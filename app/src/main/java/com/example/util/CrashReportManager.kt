package com.example.util

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * CrashReportManager
 *
 * Gathers device model, Android OS version, screen size, and stack traces into structured
 * JSON diagnostic records with ZERO Personally Identifiable Information (PII).
 *
 * Maintains a rolling history of the last 5 generated crash logs in private storage,
 * and includes an explicit, prominent privacy disclosure in reports and UI dialogs.
 */
object CrashReportManager {

    const val REPORT_FILE_NAME = "crash_report.json"
    const val HISTORY_FILE_NAME = "crash_history.json"
    private const val LEGACY_FILE_NAME = "app_crash_log.txt"
    const val MAX_STORED_CRASHES = 5

    const val PRIVACY_NOTICE_DISCLOSURE =
        "Privacy Notice & Disclosure: This diagnostic report contains ONLY anonymous technical data " +
        "(device model, Android OS version, screen size, and exception stack traces). " +
        "It does NOT contain any Personally Identifiable Information (PII) — no user names, accounts, " +
        "email addresses, phone numbers, contacts, GPS/location data, camera/audio data, IP addresses, " +
        "or personal files. These logs are stored strictly on-device and are used exclusively to diagnose " +
        "technical defects and application crashes."

    fun hasPendingCrash(context: Context): Boolean {
        val jsonFile = File(context.filesDir, REPORT_FILE_NAME)
        if (jsonFile.exists() && jsonFile.length() > 0) return true
        val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
        if (historyFile.exists() && historyFile.length() > 0) return true
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        return legacyFile.exists() && legacyFile.length() > 0
    }

    fun getLastCrashReport(context: Context): String? {
        val jsonFile = File(context.filesDir, REPORT_FILE_NAME)
        if (jsonFile.exists() && jsonFile.length() > 0) {
            return try {
                jsonFile.readText()
            } catch (_: Exception) {
                null
            }
        }
        val history = getRecentCrashReports(context)
        if (history.isNotEmpty()) {
            return history.first().toString(2)
        }
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            return try {
                legacyFile.readText()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Returns up to [MAX_STORED_CRASHES] (5) crash reports sorted from newest to oldest.
     */
    fun getRecentCrashReports(context: Context): List<JSONObject> {
        val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
        val list = mutableListOf<JSONObject>()
        if (historyFile.exists() && historyFile.length() > 0) {
            try {
                val array = JSONArray(historyFile.readText())
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
            } catch (_: Exception) {}
        }

        // Fallback: if history empty but single report exists, populate from single report
        if (list.isEmpty()) {
            val singleFile = File(context.filesDir, REPORT_FILE_NAME)
            if (singleFile.exists() && singleFile.length() > 0) {
                try {
                    list.add(JSONObject(singleFile.readText()))
                } catch (_: Exception) {}
            }
        }

        return list.take(MAX_STORED_CRASHES)
    }

    fun clearCrashReport(context: Context) {
        clearAllCrashReports(context)
    }

    fun clearAllCrashReports(context: Context) {
        try {
            val jsonFile = File(context.filesDir, REPORT_FILE_NAME)
            if (jsonFile.exists()) jsonFile.delete()
        } catch (_: Exception) {}
        try {
            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            if (historyFile.exists()) historyFile.delete()
        } catch (_: Exception) {}
        try {
            val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
            if (legacyFile.exists()) legacyFile.delete()
        } catch (_: Exception) {}
    }

    fun recordCrash(thread: Thread, throwable: Throwable, context: Context) {
        try {
            val reportJson = generateCrashReportJson(
                throwable = throwable,
                threadName = thread.name
            )
            saveCrashReportToStorage(context, reportJson)
        } catch (_: Exception) {
            // Fail-safe to avoid secondary crashes in uncaught exception handler
        }
    }

    /**
     * Records a safe, simulated diagnostic incident so the user or QA can preview
     * the 5-item crash history dashboard without forcing a fatal process crash.
     */
    fun recordSimulatedIncident(context: Context, testName: String = "Simulated Diagnostic Incident") {
        val simulatedException = IllegalStateException("Test Diagnostic: $testName at Line 142 in HeroDigitalClockCard.kt")
        val reportJson = generateCrashReportJson(
            throwable = simulatedException,
            threadName = "SimulatedThread"
        )
        saveCrashReportToStorage(context, reportJson)
    }

    private fun saveCrashReportToStorage(context: Context, reportJson: JSONObject) {
        // 1. Read existing from historyFile directly
        val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
        val existingReports = mutableListOf<JSONObject>()
        if (historyFile.exists() && historyFile.length() > 0) {
            try {
                val array = JSONArray(historyFile.readText())
                for (i in 0 until array.length()) {
                    existingReports.add(array.getJSONObject(i))
                }
            } catch (_: Exception) {}
        }

        existingReports.add(0, reportJson)
        val trimmed = existingReports.take(MAX_STORED_CRASHES)

        val array = JSONArray()
        for (item in trimmed) {
            array.put(item)
        }
        historyFile.writeText(array.toString(2))

        // 2. Save latest single report file for startup recovery dialog
        val jsonFile = File(context.filesDir, REPORT_FILE_NAME)
        jsonFile.writeText(reportJson.toString(2))
    }

    fun generateSystemDiagnostics(): String {
        return generateCrashReportJsonString(
            throwable = null,
            threadName = "UI-Diagnostics",
            indentSpaces = 2
        )
    }

    fun generateCrashReportJson(
        throwable: Throwable?,
        threadName: String = "MainThread"
    ): JSONObject {
        val isCrash = throwable != null
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val anonymousSessionId = "ANON-" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)

        val metrics = Resources.getSystem().displayMetrics
        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        val json = JSONObject()
        json.put("reportSchemaVersion", "1.0")
        json.put("reportType", if (isCrash) "CRASH_INCIDENT" else "SYSTEM_HEALTH_DIAGNOSTIC")
        json.put("timestamp", timestamp)
        json.put("anonymousSessionId", anonymousSessionId)

        // Clear Privacy Notice Disclosure
        json.put("privacyNotice", PRIVACY_NOTICE_DISCLOSURE)
        json.put("piiStatus", "NO_PII_COLLECTED_OR_STORED")
        json.put("piiExcluded", true)

        // Device & OS Technical Specs
        val deviceJson = JSONObject()
        deviceJson.put("deviceModel", Build.MODEL)
        deviceJson.put("deviceManufacturer", Build.MANUFACTURER)
        deviceJson.put("deviceBrand", Build.BRAND)
        deviceJson.put("deviceProduct", Build.PRODUCT)
        deviceJson.put("androidOsVersion", "Android ${Build.VERSION.RELEASE}")
        deviceJson.put("apiLevel", Build.VERSION.SDK_INT)
        deviceJson.put("screenSize", "${widthPx}x${heightPx} px")
        deviceJson.put("screenDensityDpi", densityDpi)
        deviceJson.put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        deviceJson.put("buildFingerprint", Build.FINGERPRINT)
        json.put("deviceEnvironment", deviceJson)

        // Execution & Thread State
        json.put("threadName", threadName)
        json.put("isCrash", isCrash)

        // Stack trace & error details
        if (throwable != null) {
            val errorJson = JSONObject()
            errorJson.put("exceptionType", throwable.javaClass.name)
            errorJson.put("exceptionMessage", throwable.message ?: "None provided")
            val rawTrace = Log.getStackTraceString(throwable)
            errorJson.put("stackTrace", sanitizeStackTrace(rawTrace))
            json.put("errorDetails", errorJson)
        } else {
            json.put("systemStatus", "Operating normally. No uncaught exceptions.")
        }

        return json
    }

    fun generateCrashReportJsonString(
        throwable: Throwable?,
        threadName: String = "MainThread",
        indentSpaces: Int = 2
    ): String {
        return generateCrashReportJson(throwable, threadName).toString(indentSpaces)
    }

    /**
     * Redacts any file system paths containing user or storage identifiers to guarantee zero PII.
     */
    fun sanitizeStackTrace(rawTrace: String): String {
        return rawTrace
            .replace(Regex("/data/user/\\d+/[a-zA-Z0-9._-]+"), "[APP_PRIVATE_DIR]")
            .replace(Regex("/data/data/[a-zA-Z0-9._-]+"), "[APP_PRIVATE_DIR]")
            .replace(Regex("/storage/emulated/\\d+/[^\\s:]*"), "[EXTERNAL_STORAGE_PATH]")
            .replace(Regex("/Users/[^/\\s]+"), "/Users/[REDACTED]")
            .replace(Regex("/home/[^/\\s]+"), "/home/[REDACTED]")
    }

    fun buildShareIntent(reportJsonText: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Meridian - Diagnostic Crash Report (JSON)")
            putExtra(Intent.EXTRA_TEXT, reportJsonText)
        }
    }

    fun buildShareAllIntent(context: Context): Intent {
        val reports = getRecentCrashReports(context)
        val array = JSONArray()
        for (r in reports) {
            array.put(r)
        }
        val text = array.toString(2)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Meridian - Last ${reports.size} Crash Reports (JSON)")
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}

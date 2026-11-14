package com.example.util

import android.content.Context
import android.content.Intent

/**
 * CrashLogManager
 *
 * Backward-compatible facade delegating directly to [CrashReportManager].
 */
object CrashLogManager {

    const val PRIVACY_ASSURANCE_NOTICE = CrashReportManager.PRIVACY_NOTICE_DISCLOSURE

    fun hasPendingCrash(context: Context): Boolean =
        CrashReportManager.hasPendingCrash(context)

    fun getLastCrashReport(context: Context): String? =
        CrashReportManager.getLastCrashReport(context)

    fun clearCrashReport(context: Context) =
        CrashReportManager.clearCrashReport(context)

    fun recordCrash(thread: Thread, throwable: Throwable, context: Context) =
        CrashReportManager.recordCrash(thread, throwable, context)

    fun generateSystemDiagnostics(): String =
        CrashReportManager.generateSystemDiagnostics()

    fun formatDiagnosticReport(throwable: Throwable?, threadName: String = "MainThread"): String =
        CrashReportManager.generateCrashReportJsonString(throwable, threadName)

    fun buildShareIntent(reportText: String): Intent =
        CrashReportManager.buildShareIntent(reportText)
}

package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Daily backup of the app's settings to a folder the user picked. The folder is outside the app, so the backups
 * survive an uninstall, which is exactly when they are needed (for example moving to a new signing key).
 * It only runs while Meridian is open, which suits a clock that stays on.
 */
object AutoBackup {
    const val KEEP = 7
    const val RUN_AFTER_HOUR = 3 // wait until 3am so the day's changes are in before the copy is taken
    private val namePattern = Regex("meridian-backup-\\d{4}-\\d{2}-\\d{2}\\.json")

    fun fileName(date: LocalDate): String = "meridian-backup-$date.json"

    fun isBackupName(name: String): Boolean = namePattern.matches(name)

    /** True when a folder is set, today's copy has not been made, and it is past 3am. */
    fun isDue(folderUri: String, lastEpochDay: Long, now: ZonedDateTime): Boolean =
        folderUri.isNotBlank() && lastEpochDay != now.toLocalDate().toEpochDay() && now.hour >= RUN_AFTER_HOUR

    /** Backup files to delete so only the newest [keep] remain. Other files in the folder are never touched. */
    fun namesToDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter(::isBackupName).sortedDescending().drop(keep)

    /** Writes today's backup into the chosen folder and removes old ones. Returns false if the folder is unusable. */
    fun write(context: Context, folder: Uri, date: LocalDate, json: String): Boolean = try {
        val resolver = context.contentResolver
        val folderDoc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val existing = children(context, folder)
        val name = fileName(date)
        // Replace a same-day file instead of letting the system rename the new one to "name (1).json".
        existing.filter { it.first == name }.forEach { DocumentsContract.deleteDocument(resolver, it.second) }
        val file = DocumentsContract.createDocument(resolver, folderDoc, "application/json", name)
        val ok = file != null && resolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) } != null
        if (ok) {
            val toDelete = namesToDelete(existing.map { it.first } + name).toSet()
            existing.filter { it.first in toDelete }.forEach { DocumentsContract.deleteDocument(resolver, it.second) }
        }
        ok
    } catch (_: Throwable) {
        false // revoked permission, unplugged card or a deleted folder: report failure, never crash
    }

    private fun children(context: Context, folder: Uri): List<Pair<String, Uri>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val result = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.getString(0) to DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(1))
            }
        }
        return result
    }
}

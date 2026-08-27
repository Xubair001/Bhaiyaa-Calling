package com.codeaza.bhaiyaaa.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * Reads the device call log.
 *
 * Failures are recorded in [lastError] rather than swallowed. An empty list and
 * a failed query look identical to the caller otherwise, which turns "the
 * provider rejected our query" into a silent "you have no calls" - the single
 * most confusing way this can break.
 */
class DeviceCallLogRepository(private val context: Context) {

    @Volatile
    var lastError: String? = null
        private set

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    fun readCalls(sinceMillis: Long = 0L, limit: Int = 500): List<CallRecordEntity> {
        lastError = null
        if (!hasPermission()) {
            lastError = "READ_CALL_LOG not granted"
            return emptyList()
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val selection = if (sinceMillis > 0) "${CallLog.Calls.DATE} > ?" else null
        val selectionArgs = if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null

        val cursor = queryCalls(projection, selection, selectionArgs, limit)
        if (cursor == null) {
            if (lastError == null) lastError = "Call log query returned no cursor"
            return emptyList()
        }

        val results = mutableListOf<CallRecordEntity>()
        cursor.use {
            val idIdx = it.getColumnIndex(CallLog.Calls._ID)
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            if (idIdx < 0 || dateIdx < 0) {
                lastError = "Call log is missing expected columns on this device"
                return emptyList()
            }

            while (it.moveToNext() && results.size < limit) {
                val rawNumber = if (numberIdx >= 0) it.getString(numberIdx) else null
                val normalized = PhoneNumbers.normalize(rawNumber)
                results.add(
                    CallRecordEntity(
                        id = it.getLong(idIdx),
                        phoneNumber = normalized,
                        matchKey = PhoneNumbers.matchKey(normalized),
                        contactName = if (nameIdx >= 0) it.getString(nameIdx) else null,
                        type = mapType(if (typeIdx >= 0) it.getInt(typeIdx) else -1).storageValue,
                        timestamp = it.getLong(dateIdx),
                        durationSeconds = if (durationIdx >= 0) it.getLong(durationIdx) else 0L
                    )
                )
            }
        }
        return results
    }

    /**
     * Runs the query, preferring the Bundle form on Android 11+.
     *
     * "ORDER BY … LIMIT n" smuggled into the sort-order string is a long-standing
     * trick, but it is not part of the contract and newer providers can reject
     * it. The Bundle form expresses the limit properly, and the plain fallback
     * truncates in Kotlin instead of relying on SQL that might not be honoured.
     */
    private fun queryCalls(
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int
    ): Cursor? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
                selectionArgs?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
            }
            runCatching {
                context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, args, null)
            }.onSuccess { if (it != null) return it }
                .onFailure { Log.w(TAG, "Bundle call-log query failed: ${it.javaClass.simpleName}") }
        }

        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )
        }.onFailure {
            // Class name and message only - never the query arguments, which
            // would put a phone number in the log.
            lastError = "${it.javaClass.simpleName}: ${it.message}"
            Log.w(TAG, "Call log query failed: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun mapType(raw: Int): CallType = when (raw) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.OTHER
    }

    private companion object {
        const val TAG = "BhaiyaaaCallLog"
    }
}

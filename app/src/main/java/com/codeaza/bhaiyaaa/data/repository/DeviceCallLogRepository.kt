package com.codeaza.bhaiyaaa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/** Reads the device call log. Returns an empty list rather than throwing when denied. */
class DeviceCallLogRepository(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Reads calls newer than [sinceMillis]. Incremental by default so a routine
     * sync scans only what has happened since the last one instead of walking
     * the whole log every time.
     */
    fun readCalls(sinceMillis: Long = 0L, limit: Int = 500): List<CallRecordEntity> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                if (sinceMillis > 0) "${CallLog.Calls.DATE} > ?" else null,
                if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )
        } catch (e: Exception) {
            null
        }

        val results = mutableListOf<CallRecordEntity>()
        cursor?.use {
            val idIdx = it.getColumnIndex(CallLog.Calls._ID)
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            if (idIdx < 0 || dateIdx < 0) return emptyList()

            while (it.moveToNext()) {
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

    private fun mapType(raw: Int): CallType = when (raw) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.OTHER
    }
}

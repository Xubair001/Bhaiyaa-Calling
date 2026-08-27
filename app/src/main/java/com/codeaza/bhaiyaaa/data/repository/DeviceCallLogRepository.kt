package com.codeaza.bhaiyaaa.data.repository

import android.content.Context
import android.provider.CallLog
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity

class DeviceCallLogRepository(private val context: Context) {

    fun readRecentCalls(limit: Int = 200): List<CallRecordEntity> {
        val results = mutableListOf<CallRecordEntity>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )
        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx) ?: "Unknown"
                val name = it.getString(nameIdx)
                val typeInt = it.getInt(typeIdx)
                val date = it.getLong(dateIdx)
                val duration = it.getLong(durationIdx)

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    else -> "OTHER"
                }

                results.add(
                    CallRecordEntity(
                        phoneNumber = number,
                        contactName = name,
                        type = type,
                        timestamp = date,
                        durationSeconds = duration
                    )
                )
            }
        }
        return results
    }
}

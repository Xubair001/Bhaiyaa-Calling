package com.codeaza.bhaiyaaa.data.repository

import android.content.Context
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.ContactEntity
import com.codeaza.bhaiyaaa.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class BhaiyaaaRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val deviceContacts = DeviceContactsRepository(context)
    private val deviceCallLog = DeviceCallLogRepository(context)

    val contacts: Flow<List<ContactEntity>> = db.contactDao().observeAll()
    val vipContacts: Flow<List<ContactEntity>> = db.contactDao().observeVipContacts()
    val calls: Flow<List<CallRecordEntity>> = db.callRecordDao().observeAll()
    val reminders: Flow<List<ReminderEntity>> = db.reminderDao().observeActive()

    suspend fun syncFromDevice() {
        val deviceContactList = deviceContacts.readDeviceContacts()
        db.contactDao().insertIfNotExists(deviceContactList)
        deviceContactList.forEach { db.contactDao().updateName(it.phoneNumber, it.name) }

        db.callRecordDao().upsertAll(deviceCallLog.readRecentCalls())
    }

    suspend fun missedCallsToday(): Int {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return db.callRecordDao().missedSince(startOfDay)
    }

    suspend fun setVipLevel(phoneNumber: String, level: String) =
        db.contactDao().setVipLevel(phoneNumber, level)

    suspend fun setTag(phoneNumber: String, tag: String?) =
        db.contactDao().setTag(phoneNumber, tag)

    suspend fun setNotes(phoneNumber: String, notes: String) =
        db.contactDao().setNotes(phoneNumber, notes)

    suspend fun addReminder(text: String, contactPhoneNumber: String? = null) {
        db.reminderDao().insert(
            ReminderEntity(
                text = text,
                contactPhoneNumber = contactPhoneNumber,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun markReminderDone(id: Long) = db.reminderDao().markDone(id)

    suspend fun clearVipAndNotes() = db.contactDao().clearAllCustomFields()

    suspend fun clearCallHistory() = db.callRecordDao().clearAll()
}

package com.codeaza.bhaiyaaa.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.codeaza.bhaiyaaa.data.db.ContactEntity

class DeviceContactsRepository(private val context: Context) {

    fun readDeviceContacts(): List<ContactEntity> {
        val results = mutableListOf<ContactEntity>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = mutableSetOf<String>()
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numberIdx) ?: continue
                val normalized = rawNumber.filter { c -> c.isDigit() || c == '+' }
                if (normalized.isBlank() || !seen.add(normalized)) continue
                results.add(ContactEntity(phoneNumber = normalized, name = name))
            }
        }
        return results
    }
}

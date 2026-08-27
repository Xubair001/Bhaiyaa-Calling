package com.codeaza.bhaiyaaa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/** Reads the device address book. Returns an empty list rather than throwing when denied. */
class DeviceContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    fun readDeviceContacts(now: Long): List<ContactEntity> {
        if (!hasPermission()) return emptyList()

        val results = mutableListOf<ContactEntity>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // A revoked permission mid-query surfaces as SecurityException; a broken
        // provider on some OEM builds throws too. Neither should crash a sync.
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || numberIdx < 0) return emptyList()

            val seen = mutableSetOf<String>()
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val raw = it.getString(numberIdx) ?: continue
                val normalized = PhoneNumbers.normalize(raw)
                if (normalized.isBlank() || !seen.add(normalized)) continue
                results.add(
                    ContactEntity(
                        phoneNumber = normalized,
                        matchKey = PhoneNumbers.matchKey(normalized),
                        name = name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
        return results
    }
}

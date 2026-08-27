package com.codeaza.bhaiyaaa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.ContactEntity
import com.codeaza.bhaiyaaa.data.db.ReminderEntity
import com.codeaza.bhaiyaaa.data.repository.BhaiyaaaRepository
import com.codeaza.bhaiyaaa.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BhaiyaaaRepository(application)

    private val _contacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    val contacts: StateFlow<List<ContactEntity>> = _contacts.asStateFlow()

    private val _vipContacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    val vipContacts: StateFlow<List<ContactEntity>> = _vipContacts.asStateFlow()

    private val _calls = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    val calls: StateFlow<List<CallRecordEntity>> = _calls.asStateFlow()

    private val _reminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = _reminders.asStateFlow()

    private val _missedToday = MutableStateFlow(0)
    val missedToday: StateFlow<Int> = _missedToday.asStateFlow()

    private val _hasPermissions = MutableStateFlow(Permissions.hasAll(application))
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        viewModelScope.launch { repository.contacts.collect { _contacts.value = it } }
        viewModelScope.launch { repository.vipContacts.collect { _vipContacts.value = it } }
        viewModelScope.launch { repository.calls.collect { _calls.value = it } }
        viewModelScope.launch { repository.reminders.collect { _reminders.value = it } }
        if (_hasPermissions.value) refresh()
    }

    fun onPermissionsResult(granted: Boolean) {
        _hasPermissions.value = granted
        if (granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.syncFromDevice()
            _missedToday.value = repository.missedCallsToday()
        }
    }

    fun setVipLevel(phoneNumber: String, level: String) {
        viewModelScope.launch { repository.setVipLevel(phoneNumber, level) }
    }

    fun setTag(phoneNumber: String, tag: String?) {
        viewModelScope.launch { repository.setTag(phoneNumber, tag) }
    }

    fun setNotes(phoneNumber: String, notes: String) {
        viewModelScope.launch { repository.setNotes(phoneNumber, notes) }
    }

    fun addReminder(text: String, contactPhoneNumber: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addReminder(text.trim(), contactPhoneNumber) }
    }

    fun markReminderDone(id: Long) {
        viewModelScope.launch { repository.markReminderDone(id) }
    }

    fun clearVipAndNotes() {
        viewModelScope.launch { repository.clearVipAndNotes() }
    }

    fun clearCallHistory() {
        viewModelScope.launch { repository.clearCallHistory() }
    }
}

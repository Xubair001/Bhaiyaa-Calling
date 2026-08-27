package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.CallRow
import com.codeaza.bhaiyaaa.ui.components.EmptyState

/** The filters offered above the call list (brief §10). */
enum class CallFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    VIP("VIP"),
    IMPORTANT("Important"),
    UNKNOWN("Unknown")
}

@Composable
fun CallsScreen(
    viewModel: BhaiyaaaViewModel,
    onOpenCall: (Long) -> Unit
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val hasPermissions by viewModel.hasCorePermissions.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(CallFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }

    // matchKey -> VIP tier, so each row can show its badge without an N+1 lookup.
    val vipByKey = remember(contacts) {
        contacts.filter { it.vipLevel != VipLevel.NONE.storageValue }
            .associate { it.matchKey to VipLevel.from(it.vipLevel) }
    }
    // matchKey -> "is this someone I know", for the Unknown filter.
    val knownKeys = remember(contacts) { contacts.map { it.matchKey }.toSet() }
    val importantKeys = remember(contacts) {
        contacts.filter { it.importance >= 2 }.map { it.matchKey }.toSet()
    }

    val visible = remember(calls, filter, query, vipByKey, knownKeys, importantKeys) {
        calls.asSequence()
            .filter { call ->
                when (filter) {
                    CallFilter.ALL -> true
                    CallFilter.MISSED -> call.type == "MISSED"
                    CallFilter.VIP -> vipByKey.containsKey(call.matchKey)
                    CallFilter.IMPORTANT -> call.isImportant || call.matchKey in importantKeys
                    CallFilter.UNKNOWN -> call.matchKey !in knownKeys
                }
            }
            .filter { call ->
                query.isBlank() ||
                    call.contactName?.contains(query, ignoreCase = true) == true ||
                    call.phoneNumber.contains(query) ||
                    call.note?.contains(query, ignoreCase = true) == true
            }
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search calls") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CallFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = if (calls.isEmpty()) "No calls yet" else "Nothing matched",
                body = when {
                    !hasPermissions ->
                        "Grant the Call log permission and your history appears here."
                    calls.isEmpty() ->
                        "Once you make or receive a call it shows up here."
                    else ->
                        "No calls match that filter or search."
                },
                actionLabel = if (calls.isEmpty() && hasPermissions) "Refresh" else null,
                onAction = if (calls.isEmpty() && hasPermissions) {
                    { viewModel.sync() }
                } else {
                    null
                }
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(visible, key = { it.id }) { call: CallRecordEntity ->
                    CallRow(
                        call = call,
                        vipLevel = vipByKey[call.matchKey] ?: VipLevel.NONE,
                        onClick = { onOpenCall(call.id) }
                    )
                }
            }
        }
    }
}

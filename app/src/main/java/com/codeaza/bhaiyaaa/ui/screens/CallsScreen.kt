package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.AppViewModel

private val filters = listOf("All", "Missed", "Incoming", "Outgoing")

@Composable
fun CallsScreen(viewModel: AppViewModel) {
    val calls by viewModel.calls.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredCalls = when (selectedFilter) {
        "Missed" -> calls.filter { it.type == "MISSED" }
        "Incoming" -> calls.filter { it.type == "INCOMING" }
        "Outgoing" -> calls.filter { it.type == "OUTGOING" }
        else -> calls
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Calls", style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
                )
            }
        }

        if (filteredCalls.isEmpty()) {
            Text("No calls to show yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredCalls) { call ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(call.contactName ?: call.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${call.type} \u00B7 ${call.durationSeconds}s",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

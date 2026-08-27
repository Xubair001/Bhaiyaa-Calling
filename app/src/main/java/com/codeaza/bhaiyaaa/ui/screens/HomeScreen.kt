package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.ui.AppViewModel
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.timeOfDayGreeting

@Composable
fun HomeScreen(viewModel: AppViewModel, onOpenInsights: () -> Unit) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val calls by viewModel.calls.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val vipContacts by viewModel.vipContacts.collectAsState()
    val missedToday by viewModel.missedToday.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionsResult(result.values.all { it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(timeOfDayGreeting(), style = MaterialTheme.typography.titleLarge)

        if (!hasPermissions) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("BHAIYAAA needs contacts and call log access to show your calls.")
                    Button(onClick = { permissionLauncher.launch(Permissions.REQUIRED) }) {
                        Text("Grant permissions")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Missed calls today", style = MaterialTheme.typography.labelSmall)
                    Text("$missedToday", style = MaterialTheme.typography.titleLarge)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Contacts synced", style = MaterialTheme.typography.labelSmall)
                    Text("${contacts.size}", style = MaterialTheme.typography.titleLarge)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VIP contacts", style = MaterialTheme.typography.labelSmall)
                    Text("${vipContacts.size}", style = MaterialTheme.typography.titleLarge)
                }
            }

            Text("Recent calls", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls.take(5)) { call -> CallRow(call) }
            }

            Button(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text("View Insights \u2192")
            }
        }
    }
}

@Composable
fun CallRow(call: CallRecordEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(call.contactName ?: call.phoneNumber, style = MaterialTheme.typography.bodyLarge)
            Text(call.type, style = MaterialTheme.typography.labelSmall)
        }
    }
}

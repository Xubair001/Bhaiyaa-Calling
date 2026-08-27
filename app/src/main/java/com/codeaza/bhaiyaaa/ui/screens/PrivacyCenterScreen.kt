package com.codeaza.bhaiyaaa.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.AppViewModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PrivacyCenterScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val vipContacts by viewModel.vipContacts.collectAsState()
    val calls by viewModel.calls.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val json = JSONObject().apply {
                put("contacts", JSONArray().apply {
                    contacts.forEach {
                        put(
                            JSONObject().apply {
                                put("name", it.name)
                                put("phoneNumber", it.phoneNumber)
                                put("vipLevel", it.vipLevel)
                                put("tag", it.tag ?: JSONObject.NULL)
                                put("notes", it.notes ?: JSONObject.NULL)
                            }
                        )
                    }
                })
                put("callCount", calls.size)
            }
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toString(2).toByteArray())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("\u2190 Back") }

        Text("Privacy Center", style = MaterialTheme.typography.titleLarge)

        Text("What BHAIYAAA can access", style = MaterialTheme.typography.titleMedium)
        Text("Contacts \u00B7 Call log \u00B7 Phone state (for VIP alerts) \u00B7 Notifications")
        Text("Everything stays on this device. Nothing is sent to a server.")

        Text("Data stored locally", style = MaterialTheme.typography.titleMedium)
        Text("${contacts.size} contacts \u00B7 ${vipContacts.size} VIP \u00B7 ${calls.size} calls cached")

        OutlinedButton(
            onClick = { exportLauncher.launch("bhaiyaaa-export.json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export data as JSON")
        }

        Button(
            onClick = { viewModel.clearVipAndNotes() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear all VIP settings & notes")
        }

        Button(
            onClick = { viewModel.clearCallHistory() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear cached call history")
        }
    }
}

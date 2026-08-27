package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.AppViewModel
import com.codeaza.bhaiyaaa.util.VipLevel

@Composable
fun VipScreen(viewModel: AppViewModel, onContactClick: (String) -> Unit) {
    val vipContacts by viewModel.vipContacts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("VIP", style = MaterialTheme.typography.titleLarge)
        Text(
            "These contacts get special alerts when they call.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
        )

        if (vipContacts.isEmpty()) {
            Text("No VIP contacts yet. Go to Contacts, tap someone, and set their VIP level.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vipContacts) { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactClick(contact.phoneNumber) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            Text(VipLevel.label(contact.vipLevel), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

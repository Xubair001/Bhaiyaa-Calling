package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * The VIP list, grouped by tier.
 *
 * Under a privacy lock this whole screen sits behind the PIN, which is the
 * "Secret VIP" behaviour from §7 - the list is never surfaced on the dashboard
 * or in a widget, only here.
 */
@Composable
fun VipScreen(
    viewModel: SukoonViewModel,
    onOpenContact: (String) -> Unit,
    onOpenAlertSettings: () -> Unit
) {
    val vips by viewModel.vipContacts.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()

    val callCountByKey = remember(calls) {
        calls.groupingBy { it.matchKey }.eachCount()
    }
    val grouped = remember(vips) {
        vips.groupBy { VipLevel.from(it.vipLevel) }
    }

    if (vips.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Star,
            title = "No VIPs yet",
            body = "Open any contact and set their VIP tier. Their calls then get a distinct " +
                "vibration, flashlight pattern and heads-up alert."
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            InfoBanner(
                text = "Alerts depend on Android delivering the ringing broadcast. Some phones " +
                    "restrict this for background apps — allow Sukoon unrestricted battery use " +
                    "if VIP alerts are unreliable.",
                actionLabel = "Alert settings",
                onAction = onOpenAlertSettings
            )
        }

        // Highest tier first - Emergency is what you scan for.
        VipLevel.assignable.reversed().forEach { level ->
            val members = grouped[level].orEmpty()
            if (members.isEmpty()) return@forEach

            item(key = "header-${level.storageValue}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${members.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(members, key = { it.phoneNumber }) { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenContact(contact.phoneNumber) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(contact.name, size = 42)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append(PhoneNumbers.forDisplay(contact.phoneNumber))
                                val count = callCountByKey[contact.matchKey] ?: 0
                                if (count > 0) append(" · ${Formatting.plural(count, "call")}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VipBadge(level)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.setVipLevel(contact.phoneNumber, VipLevel.NONE) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

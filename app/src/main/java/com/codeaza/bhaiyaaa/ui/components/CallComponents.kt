package com.codeaza.bhaiyaaa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * Direction indicator. Carries a content description rather than relying on
 * colour and arrow direction alone, so the call list is usable with TalkBack.
 */
@Composable
fun CallTypeIcon(type: CallType, modifier: Modifier = Modifier) {
    val (icon, tint, description) = when (type) {
        CallType.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.cd_call_direction_incoming)
        )
        CallType.OUTGOING -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.cd_call_direction_outgoing)
        )
        CallType.MISSED -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.cd_call_direction_missed)
        )
        CallType.REJECTED, CallType.BLOCKED -> Triple(
            Icons.Filled.Block,
            MaterialTheme.colorScheme.error,
            "Rejected call"
        )
        CallType.VOICEMAIL -> Triple(
            Icons.Filled.Voicemail,
            MaterialTheme.colorScheme.tertiary,
            "Voicemail"
        )
        CallType.OTHER -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Call"
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(18.dp)
    )
}

/** One row in the call list. */
@Composable
fun CallRow(
    call: CallRecordEntity,
    vipLevel: VipLevel = VipLevel.NONE,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val type = CallType.from(call.type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(call.contactName ?: call.phoneNumber, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = call.contactName ?: PhoneNumbers.forDisplay(call.phoneNumber),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (vipLevel.isVip) {
                    Spacer(Modifier.width(6.dp))
                    VipBadge(vipLevel)
                }
                if (call.isImportant) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.PriorityHigh,
                        contentDescription = "Marked important",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeIcon(type)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = Formatting.relativeDateTime(call.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!call.note.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = call.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (call.durationSeconds > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = Formatting.duration(call.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

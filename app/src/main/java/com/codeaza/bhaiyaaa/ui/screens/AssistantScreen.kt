package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.ai.speech.SpeechEngineKind
import com.codeaza.bhaiyaaa.ui.assistant.AssistantViewModel
import com.codeaza.bhaiyaaa.ui.assistant.ChatMessage
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.util.Permissions
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "Who called me?",
    "Any missed calls today?",
    "Show my VIP contacts",
    "Who called me most this week?",
    "Remind me to call Ali tomorrow at 5pm"
)

/**
 * The Assistant.
 *
 * Two honesty rules shape this screen:
 *  - answers carry their sources, so a claim about your calls can be checked;
 *  - the mic label states which recognizer is running, because only the Vosk
 *    path is genuinely offline.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onOpenModels: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val speech by viewModel.speech.collectAsStateWithLifecycle()
    val engineKind by viewModel.availableSpeechEngine.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (messages.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Ask BHAIYAAA",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "I answer from your own call log, contacts and saved notes — nothing " +
                        "leaves this phone, and I never guess at an answer I can't look up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                SUGGESTIONS.forEach { suggestion ->
                    AssistChip(
                        onClick = { viewModel.send(suggestion) },
                        label = { Text(suggestion) },
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message -> ChatBubble(message) }
                if (isThinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Checking your data…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        speech.error?.let { error ->
            InfoBanner(
                text = error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                actionLabel = "Models",
                onAction = onOpenModels
            )
        }

        AnimatedVisibility(visible = speech.isListening) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = speech.partialText.ifBlank { "Listening…" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = speech.engine?.label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Honest labelling of what the mic will actually use.
        if (!speech.isListening && engineKind != null) {
            Text(
                text = when (engineKind) {
                    SpeechEngineKind.VOSK_OFFLINE ->
                        "Voice input runs fully offline on this device."
                    SpeechEngineKind.ANDROID_SYSTEM ->
                        "Voice input uses your phone's recognizer. Install an offline model " +
                            "in Settings → AI Models to keep it entirely on-device."
                    null -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your calls…") },
                maxLines = 3
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (speech.isListening) {
                        viewModel.stopListening()
                    } else if (Permissions.isGranted(context, android.Manifest.permission.RECORD_AUDIO)) {
                        viewModel.startListening()
                    } else {
                        // Progressive: the mic is only ever requested right here.
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (speech.isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_mic_button),
                    tint = if (speech.isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val background = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.fromUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(14.dp)
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyMedium, color = textColor)

            // Provenance: what the answer was read from.
            if (message.sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                message.sources.forEach { source ->
                    Text(
                        "· ${source.label}: ${source.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

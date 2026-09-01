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
import androidx.compose.material3.TextButton
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

/**
 * Shown on an empty Assistant screen, grouped by what the assistant can do.
 *
 * Grouped rather than listed flat because a bare column of six examples reads
 * as "here are some magic words" - and the point to get across is the shape of
 * what it handles, so a question nobody thought to put on a chip still gets
 * asked. Each group deliberately mixes a question with a command: the first
 * thing to learn here is that it can act, not only answer.
 */
private data class SuggestionGroup(val title: String, val examples: List<String>)

private val SUGGESTIONS = listOf(
    SuggestionGroup(
        "Your calls",
        listOf("Any missed calls today?", "Who called me most this week?", "Show my VIP contacts")
    ),
    SuggestionGroup(
        "Prayer and quiet",
        listOf("When is the next prayer?", "When is Asr?", "Silence my phone for 30 minutes")
    ),
    SuggestionGroup(
        "Reminders",
        listOf("Remind me to call Ali tomorrow at 5pm", "What are my reminders?")
    )
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
            // A LazyColumn rather than a centred Column: at a large font scale
            // three groups of chips are taller than the screen, and a centred
            // column simply clipped them.
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Ask Sukoon",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "I answer from your own call log, contacts, saved notes and prayer " +
                            "times — nothing leaves this phone, and I never guess at an " +
                            "answer I can't look up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SUGGESTIONS.forEach { group ->
                    item(key = group.title) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            group.title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        group.examples.forEach { suggestion ->
                            AssistChip(
                                onClick = { viewModel.send(suggestion) },
                                label = { Text(suggestion) },
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Clearing is offered here rather than in the app bar, which is
            // shared by every screen: an action that only applies to one of
            // them belongs with it. Only shown when there is something to
            // clear, so it never sits there doing nothing.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = viewModel::clearConversation) {
                    Text("Clear")
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onRetry = viewModel::retry,
                        modifier = Modifier.animateItem()
                    )
                }
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
private fun ChatBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    // A failure gets the error container, not the ordinary answer surface -
    // otherwise "something went wrong" reads like an answer.
    val background = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.fromUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier.fillMaxWidth(), contentAlignment = alignment) {
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

            // A failure the user can act on is a hiccup; one they can only
            // read is a dead end.
            if (message.isError) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

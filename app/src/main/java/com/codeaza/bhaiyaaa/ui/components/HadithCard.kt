package com.codeaza.bhaiyaaa.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.content.HadithRepository
import com.codeaza.bhaiyaaa.data.content.HadithRotation
import com.codeaza.bhaiyaaa.domain.model.Hadith
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.prayer.PrayerPeriods
import com.codeaza.bhaiyaaa.ui.theme.CardShape
import kotlinx.coroutines.delay

/**
 * How long one narration stays before the next.
 *
 * Five minutes, from the brief. Long enough to read one twice and not feel
 * hurried; short enough that a phone left on the dashboard shows more than
 * one over a prayer period.
 */
private const val ROTATION_MILLIS = 5 * 60 * 1000L

/**
 * Enough lines reserved for the body that a shorter narration does not shrink
 * the card and a longer one does not grow it.
 *
 * This is what stops the rotation shifting everything below it on the
 * dashboard every five minutes - which would be far more intrusive than the
 * content itself.
 */
private const val BODY_MIN_LINES = 3
private const val BODY_MAX_LINES = 5

/**
 * A narration for the current prayer period, rotating quietly.
 *
 * ## Why it is built this way
 *
 * **It renders nothing rather than something generic.** No prayer period, no
 * content file, or an empty pool for that period, and the card simply is not
 * there. An informational card that appears with a placeholder is worse than
 * no card.
 *
 * **The timer only runs while it is on screen.** The rotation is a `delay` in
 * a `LaunchedEffect`, so navigating away cancels it - there is no alarm, no
 * worker and nothing polling. That is the whole of its battery cost.
 *
 * **The height does not change.** The body reserves a fixed band of lines and
 * the transition is a crossfade inside it, so nothing below the card moves.
 *
 * **One timer, not two.** The period is re-derived on the same tick that
 * rotates the narration, rather than by a second clock somewhere else. A
 * period boundary is therefore noticed within five minutes, which for a card
 * that changes its content every five minutes is exactly as precise as it
 * needs to be - and it costs nothing.
 *
 * @param anchors today's prayer instants, from
 *   [com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator.anchorsForDay]. Empty
 *   renders nothing, which is the right answer when no times are configured.
 */
@Composable
fun HadithCard(
    anchors: Map<Prayer, Long>,
    modifier: Modifier = Modifier,
    rotationMillis: Long = ROTATION_MILLIS
) {
    if (anchors.isEmpty()) return

    val context = LocalContext.current
    // Parsed once per process and cached inside the repository, so building
    // one here costs an allocation rather than a file read.
    val repository = remember(context) { HadithRepository(context) }
    val rotation = remember { HadithRotation() }

    var period by remember { mutableStateOf<Prayer?>(null) }
    var current by remember { mutableStateOf<Hadith?>(null) }
    // Most recent first. Held here rather than persisted: the rule is "not the
    // one you just saw", which is a property of this sitting, not of the
    // install.
    val recent = remember { mutableListOf<String>() }

    LaunchedEffect(anchors) {
        var pool = emptyList<Hadith>()
        var pooledFor: Prayer? = null
        while (true) {
            val now = PrayerPeriods.current(anchors, System.currentTimeMillis())
            if (now == null) {
                period = null
                current = null
                delay(rotationMillis)
                continue
            }
            // Crossing into a new period means a new pool and a clean memory:
            // a narration seen during Dhuhr is not a repeat during Asr.
            if (now != pooledFor) {
                pool = repository.forPeriod(now)
                pooledFor = now
                recent.clear()
                period = now
            }
            rotation.next(pool, recent)?.let { next ->
                current = next
                recent.add(0, next.id)
                // Trim to what the rotation will actually consult, so the list
                // cannot grow for the lifetime of the screen.
                while (recent.size > rotation.memoryFor(pool.size)) {
                    recent.removeAt(recent.lastIndex)
                }
            }
            delay(rotationMillis)
        }
    }

    val shown = current ?: return
    val prayer = period ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Says what the content is and which period it belongs to, so
                // it reads as information about this prayer rather than as an
                // instruction or a notification.
                Text(
                    text = "DURING ${prayer.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "HADITH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    // A crossfade and nothing else. Anything that moves would
                    // pull the eye to a card whose entire job is to be
                    // available without demanding attention.
                    fadeIn(tween(FADE_IN_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS))
                },
                label = "hadith"
            ) { hadith ->
                Column(
                    Modifier.semantics {
                        contentDescription = buildString {
                            append(hadith.text)
                            hadith.narrator?.let { append(" Narrated by $it.") }
                            append(" ${hadith.reference}.")
                        }
                    }
                ) {
                    Text(
                        text = hadith.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        minLines = BODY_MIN_LINES,
                        maxLines = BODY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            text = buildString {
                                hadith.narrator?.let { append("$it · ") }
                                append(hadith.reference)
                                hadith.grade?.let { append(" · ${it.label}") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private const val FADE_IN_MILLIS = 300
private const val FADE_OUT_MILLIS = 200

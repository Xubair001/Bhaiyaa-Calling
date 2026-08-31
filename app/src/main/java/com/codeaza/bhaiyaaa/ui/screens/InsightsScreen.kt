package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.Bar
import com.codeaza.bhaiyaaa.ui.components.BarChart
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.SplitBar
import com.codeaza.bhaiyaaa.ui.components.StatTile
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * Insights. Every figure is aggregated from the local call log by
 * InsightsCalculator - there is no sample data path, so an empty log shows an
 * empty state rather than a demo chart.
 */
@Composable
fun InsightsScreen(viewModel: SukoonViewModel) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()

    // Recompute on entry so figures are current after a sync elsewhere.
    LaunchedEffect(Unit) { viewModel.refreshDerived() }

    if (insights.isEmpty) {
        EmptyState(
            icon = Icons.Filled.Insights,
            title = "Nothing to chart yet",
            body = "Insights are computed from your real call log. Once there are calls to " +
                "read, your week shows up here.",
            actionLabel = "Sync now",
            onAction = { viewModel.sync() }
        )
        return
    }

    val bars = remember(insights.last7Days) {
        insights.last7Days.map { Bar(Formatting.dayLabel(it.dayStartMillis), it.callCount) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(insights.callsToday.toString(), "Calls today", Modifier.weight(1f))
                StatTile(insights.callsThisWeek.toString(), "This week", Modifier.weight(1f))
                StatTile(
                    insights.missedThisWeek.toString(),
                    "Missed this week",
                    Modifier.weight(1f),
                    accent = if (insights.missedThisWeek > 0) MaterialTheme.colorScheme.error else null
                )
            }
        }

        item {
            SectionCard(title = "Last 7 days") {
                BarChart(bars = bars)
            }
        }

        item {
            SectionCard(title = "Incoming vs outgoing") {
                SplitBar(
                    leftValue = insights.incomingThisWeek,
                    rightValue = insights.outgoingThisWeek,
                    leftColor = MaterialTheme.colorScheme.primary,
                    rightColor = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Incoming ${insights.incomingThisWeek}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Outgoing ${insights.outgoingThisWeek}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "VIP activity") {
                Text(
                    "${Formatting.plural(insights.vipCallsThisWeek, "VIP call")} this week",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (insights.mostContacted.isNotEmpty()) {
            item {
                SectionCard(title = "Most contacted this week") {
                    insights.mostContacted.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                entry.displayName?.takeIf { it.isNotBlank() }
                                    ?: PhoneNumbers.forDisplay(entry.matchKey),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Formatting.plural(entry.callCount, "call"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (insights.busiestHours.isNotEmpty()) {
            item {
                SectionCard(title = "Most active hours") {
                    insights.busiestHours.forEach { hour ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                Formatting.hourLabel(hour.hourOfDay),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                Formatting.plural(hour.callCount, "call"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (insights.longestCalls.isNotEmpty()) {
            item {
                SectionCard(title = "Longest calls") {
                    insights.longestCalls.forEach { call ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    call.contactName ?: PhoneNumbers.forDisplay(call.phoneNumber),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Formatting.relativeDateTime(call.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                Formatting.duration(call.durationSeconds),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "All figures come from your device's own call log, computed on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

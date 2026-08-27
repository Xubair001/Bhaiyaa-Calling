package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.ui.AppViewModel
import java.util.Calendar

@Composable
fun InsightsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val calls by viewModel.calls.collectAsState()
    val vipContacts by viewModel.vipContacts.collectAsState()

    val startOfToday = startOfDayMillis(0)
    val startOfWeek = startOfDayMillis(6)

    val callsToday = calls.count { it.timestamp >= startOfToday }
    val callsThisWeek = calls.count { it.timestamp >= startOfWeek }
    val missedCount = calls.count { it.type == "MISSED" }
    val incomingCount = calls.count { it.type == "INCOMING" }
    val outgoingCount = calls.count { it.type == "OUTGOING" }
    val vipNumbers = vipContacts.map { it.phoneNumber }.toSet()
    val vipCallsThisWeek = calls.count { it.timestamp >= startOfWeek && it.phoneNumber in vipNumbers }

    val mostContacted = calls
        .groupBy { it.contactName ?: it.phoneNumber }
        .maxByOrNull { it.value.size }

    val dailyCounts = lastSevenDaysCounts(calls)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("\u2190 Back") }
            Text("Insights", style = MaterialTheme.typography.titleLarge)
        }

        item { StatCard("Calls today", "$callsToday") }
        item { StatCard("Calls this week", "$callsThisWeek") }
        item { StatCard("Missed calls (all time)", "$missedCount") }
        item { StatCard("Incoming vs outgoing", "$incomingCount in / $outgoingCount out") }
        item { StatCard("VIP calls this week", "$vipCallsThisWeek") }
        item {
            StatCard(
                "Most contacted",
                mostContacted?.let { "${it.key} (${it.value.size} calls)" } ?: "No data yet"
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Calls per day (last 7 days)", style = MaterialTheme.typography.titleMedium)
                    WeekBarChart(dailyCounts, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun WeekBarChart(counts: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val maxCount = (counts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.padding(top = 12.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val barWidth = size.width / (counts.size * 2f)
            counts.forEachIndexed { index, (_, count) ->
                val barHeight = (count.toFloat() / maxCount) * size.height
                val left = index * (barWidth * 2) + barWidth / 2
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
        }
        DayLabelsRow(counts)
    }
}

@Composable
private fun DayLabelsRow(counts: List<Pair<String, Int>>) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        counts.forEach { (label, _) ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun startOfDayMillis(daysAgo: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun lastSevenDaysCounts(calls: List<CallRecordEntity>): List<Pair<String, Int>> {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val result = mutableListOf<Pair<String, Int>>()
    for (i in 6 downTo 0) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
        val dayStart = startOfDayMillis(i)
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        val count = calls.count { it.timestamp in dayStart until dayEnd }
        val weekdayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6
        result.add(dayLabels[weekdayIndex] to count)
    }
    return result
}

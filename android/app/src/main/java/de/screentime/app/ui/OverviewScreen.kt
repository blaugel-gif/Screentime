package de.screentime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.screentime.app.data.Status

@Composable
fun OverviewScreen(state: AppState) {
    val s = state.status
    if (s == null) {
        CenterMessage(
            state.error ?: if (state.loading) "Verbinde mit dem PC …"
            else "Noch keine Daten. Oben rechts auf Aktualisieren tippen."
        )
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TodayCard(s)
        QuickCard(state, s)
        BonusCard(state)
        if (s.ptEnabled) PlaytimeCard(state, s)
        UsageCard(s)
    }
}

@Composable
private fun TodayCard(s: Status) {
    val name = s.child.replaceFirstChar { it.uppercase() }
    Section("$name – ${DAY_NAMES.getOrElse(s.weekday - 1) { "heute" }}") {
        Text(fmtDur(s.leftToday), style = MaterialTheme.typography.displaySmall)
        Text("übrig heute", style = MaterialTheme.typography.bodyMedium)
        val total = s.spentToday + s.leftToday
        val frac = if (total > 0) s.spentToday.toFloat() / total else 0f
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Genutzt: ${fmtDur(s.spentToday)}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (s.limitToday > 0) "Tageslimit: ${fmtDur(s.limitToday)}" else "Heute gesperrt",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            if (s.live) "● $name ist gerade angemeldet"
            else "$name ist gerade nicht angemeldet – letzter gespeicherter Stand",
            style = MaterialTheme.typography.labelMedium,
            color = if (s.live) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BonusCard(state: AppState) {
    var custom by rememberSaveable { mutableStateOf("") }
    var confirmEnd by remember { mutableStateOf(false) }

    Section("Bonuszeit") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60).forEach { m ->
                FilledTonalButton(
                    onClick = { state.act("+$m Min gewährt") { state.api.changeTime('+', m * 60) } },
                    modifier = Modifier.weight(1f),
                ) { Text("+$m") }
            }
            OutlinedButton(
                onClick = { state.act("15 Min abgezogen") { state.api.changeTime('-', 15 * 60) } },
                modifier = Modifier.weight(1f),
            ) { Text("−15") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter { it.isDigit() }.take(3) },
                label = { Text("Minuten") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            val m = custom.toIntOrNull() ?: 0
            FilledTonalButton(
                enabled = m > 0,
                onClick = { state.act("+$m Min gewährt", onSuccess = { custom = "" }) { state.api.changeTime('+', m * 60) } },
            ) { Text("Geben") }
            OutlinedButton(
                enabled = m > 0,
                onClick = { state.act("$m Min abgezogen", onSuccess = { custom = "" }) { state.api.changeTime('-', m * 60) } },
            ) { Text("Abziehen") }
        }
        TextButton(
            onClick = { confirmEnd = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Für heute beenden") }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Für heute beenden?") },
            text = { Text("Die Restzeit wird auf 0 gesetzt. Ist er angemeldet, wird er nach einer kurzen Warnung abgemeldet. Ungespeichertes geht verloren.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    state.act("Für heute beendet") { state.api.changeTime('=', 0) }
                }) { Text("Beenden") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PlaytimeCard(state: AppState, s: Status) {
    Section("Spielzeit") {
        Text("${fmtDur(s.ptLeft)} übrig", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Genutzt: ${fmtDur(s.ptSpent)} · gilt für alle als Spiel markierten Programme",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30).forEach { m ->
                FilledTonalButton(
                    onClick = { state.act("+$m Min Spielzeit") { state.api.changeTime('+', m * 60, playtime = true) } },
                    modifier = Modifier.weight(1f),
                ) { Text("+$m Spielzeit") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageCard(s: Status) {
    var week by rememberSaveable { mutableStateOf(false) }
    val data = (if (week) s.usageWeek else s.usageToday).entries
        .filter { it.value > 0 }.sortedByDescending { it.value }
    Section("Programme") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !week, onClick = { week = false }, label = { Text("Heute") })
            FilterChip(selected = week, onClick = { week = true }, label = { Text("Letzte 7 Tage") })
        }
        if (data.isEmpty()) {
            Text(
                "Noch keine Nutzung erfasst. Gezählt werden die Programme aus dem Reiter „Programme“.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val max = data.maxOfOrNull { it.value } ?: 1
        data.forEach { (app, sec) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app, style = MaterialTheme.typography.bodyMedium)
                    Text(fmtDur(sec), style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { sec.toFloat() / max },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            "Gezählt wird, solange ein Programm in seiner Sitzung läuft – auch im Hintergrund.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickCard(state: AppState, s: Status) {
    val name = s.child.replaceFirstChar { it.uppercase() }
    var confirmLock by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }

    Section("Sofort") {
        if (s.locked) {
            Text(
                "🔒 PC ist gesperrt – $name kann sich nicht anmelden oder entsperren.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = { state.act("PC entsperrt") { state.api.unlock() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Entsperren") }
        } else {
            Button(
                onClick = { confirmLock = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("PC sofort sperren") }
        }

        Text("$name rufen", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Essen ist fertig!", "Komm bitte mal!").forEach { t ->
                FilledTonalButton(
                    onClick = { state.act("Nachricht gesendet") { state.api.callChild(t) } },
                    modifier = Modifier.weight(1f),
                ) { Text(t, maxLines = 1) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(200) },
                label = { Text("Eigene Nachricht") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                enabled = message.isNotBlank(),
                onClick = {
                    val t = message.trim()
                    state.act("Nachricht gesendet", onSuccess = { message = "" }) { state.api.callChild(t) }
                },
            ) { Text("Rufen") }
        }
        Text(
            "Die Nachricht erscheint mit Ton auf dem PC-Bildschirm, wenn $name angemeldet ist.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text("PC sofort sperren?") },
            text = { Text("Der Bildschirm wird gesperrt und $name kann sich nicht mehr anmelden, bis du entsperrst. Offene Programme bleiben erhalten, die Zeit läuft währenddessen nicht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLock = false
                    state.act("PC gesperrt") { state.api.lock() }
                }) { Text("Sperren") }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Abbrechen") } },
        )
    }
}

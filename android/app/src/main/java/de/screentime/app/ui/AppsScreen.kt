package de.screentime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.screentime.app.data.TrackedApp

private val PROC_RE = Regex("[A-Za-z0-9._+-]{1,40}")
private val CONTAINS_RE = Regex("[A-Za-z0-9._/ +-]{0,40}")

@Composable
fun AppsScreen(state: AppState) {
    val s = state.status
    if (s == null) {
        CenterMessage(state.error ?: "Noch keine Daten. Oben rechts auf Aktualisieren tippen.")
        return
    }
    var apps by remember(s) { mutableStateOf(s.apps) }
    var editIndex by remember { mutableStateOf<Int?>(null) }   // -1 = neu
    val changed = apps != s.apps

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Diese Programme werden auf dem PC erfasst. „Spiel“ heißt: Es zählt zur Spielzeit, " +
                "falls diese unter „Limits“ eingeschaltet ist.",
            style = MaterialTheme.typography.bodyMedium,
        )
        apps.forEachIndexed { i, a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { editIndex = i }) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            a.procs.joinToString(", ") + if (a.contains.isNotBlank()) "  ·  enthält „${a.contains}“" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = a.game, onCheckedChange = { on ->
                            apps = apps.toMutableList().also { it[i] = a.copy(game = on) }
                        })
                        Text("Spiel", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { apps = apps.toMutableList().also { it.removeAt(i) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Entfernen")
                    }
                }
            }
        }
        OutlinedButton(onClick = { editIndex = -1 }, enabled = apps.size < 30, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Programm hinzufügen")
        }
        Button(
            onClick = { state.act("Programme gespeichert") { state.api.saveApps(apps) } },
            enabled = changed && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Speichern") }
        Text(
            "Tipp: Den Prozessnamen eines Programms findest du am PC, während das Programm läuft, mit:\n" +
                "ps -u ${s.child} -o comm= | sort -u",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editIndex?.let { idx ->
        AppDialog(
            initial = if (idx >= 0) apps[idx] else null,
            onDismiss = { editIndex = null },
            onSave = { a ->
                apps = apps.toMutableList().also { if (idx >= 0) it[idx] = a else it.add(a) }
                editIndex = null
            },
        )
    }
}

@Composable
private fun AppDialog(initial: TrackedApp?, onDismiss: () -> Unit, onSave: (TrackedApp) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var procs by remember { mutableStateOf(initial?.procs?.joinToString(", ") ?: "") }
    var contains by remember { mutableStateOf(initial?.contains ?: "") }
    var game by remember { mutableStateOf(initial?.game ?: false) }

    val procList = procs.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
    val valid = name.isNotBlank() && name.length <= 40 &&
        procList.size in 1..8 && procList.all { PROC_RE.matches(it) } &&
        CONTAINS_RE.matches(contains)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Programm hinzufügen" else "Programm bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Anzeigename") }, singleLine = true)
                OutlinedTextField(
                    procs, { procs = it }, label = { Text("Prozessnamen (mit Komma)") },
                    supportingText = { Text("z. B. firefox-esr, discord") }, singleLine = true,
                )
                OutlinedTextField(
                    contains, { contains = it.take(40) }, label = { Text("Muss enthalten (optional)") },
                    supportingText = { Text("z. B. minecraft – unterscheidet Java-Programme") }, singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zählt als Spiel", Modifier.weight(1f))
                    Switch(checked = game, onCheckedChange = { game = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(TrackedApp(name.trim(), procList, contains.trim(), game))
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

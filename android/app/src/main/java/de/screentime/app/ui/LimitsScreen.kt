package de.screentime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.screentime.app.data.LimitsEdit

private const val STEP = 15 * 60

private val LOCKOUTS = listOf(
    "terminate" to "Abmelden (empfohlen)",
    "shutdown" to "PC herunterfahren",
    "lock" to "Bildschirm sperren – kann er mit seinem Passwort umgehen",
)

@Composable
fun LimitsScreen(state: AppState) {
    val s = state.status
    if (s == null) {
        CenterMessage(state.error ?: "Noch keine Daten. Oben rechts auf Aktualisieren tippen.")
        return
    }
    var edit by remember(s) { mutableStateOf(LimitsEdit.from(s)) }
    val changed = edit != LimitsEdit.from(s)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Zeit pro Tag") {
            edit.days.forEachIndexed { i, d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = d.allowed,
                        onCheckedChange = { on ->
                            edit = edit.copy(days = edit.days.toMutableList().also { it[i] = d.copy(allowed = on) })
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(DAY_NAMES[d.day - 1].take(2), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Stepper(
                        value = if (d.allowed) fmtHm(d.secs) else "gesperrt",
                        enabled = d.allowed,
                        onMinus = {
                            edit = edit.copy(days = edit.days.toMutableList().also {
                                it[i] = d.copy(secs = (d.secs - STEP).coerceAtLeast(STEP))
                            })
                        },
                        onPlus = {
                            edit = edit.copy(days = edit.days.toMutableList().also {
                                it[i] = d.copy(secs = (d.secs + STEP).coerceAtMost(24 * 3600))
                            })
                        },
                    )
                }
            }
            TextButton(onClick = {
                val mo = edit.days.first().secs
                edit = edit.copy(days = edit.days.map { it.copy(secs = mo) })
            }) { Text("Montags-Zeit für alle Tage übernehmen") }
        }

        Section("Erlaubte Uhrzeit") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("von", Modifier.weight(1f))
                Stepper(
                    value = "%02d:00".format(edit.fromHour),
                    onMinus = { edit = edit.copy(fromHour = (edit.fromHour - 1).coerceAtLeast(0)) },
                    onPlus = { edit = edit.copy(fromHour = (edit.fromHour + 1).coerceAtMost(edit.toHour - 1)) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("bis", Modifier.weight(1f))
                Stepper(
                    value = "%02d:00".format(edit.toHour),
                    onMinus = { edit = edit.copy(toHour = (edit.toHour - 1).coerceAtLeast(edit.fromHour + 1)) },
                    onPlus = { edit = edit.copy(toHour = (edit.toHour + 1).coerceAtMost(24)) },
                )
            }
            Text(
                "Gilt für alle Tage. Außerhalb dieser Zeit ist keine Anmeldung möglich.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Spielzeit (Zusatz-Limit)") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spielzeit begrenzen", Modifier.weight(1f))
                Switch(checked = edit.ptEnabled, onCheckedChange = { edit = edit.copy(ptEnabled = it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("pro Tag", Modifier.weight(1f))
                Stepper(
                    value = fmtHm(edit.ptSecs),
                    enabled = edit.ptEnabled,
                    onMinus = { edit = edit.copy(ptSecs = (edit.ptSecs - STEP).coerceAtLeast(STEP)) },
                    onPlus = { edit = edit.copy(ptSecs = (edit.ptSecs + STEP).coerceAtMost(24 * 3600)) },
                )
            }
            Text(
                "Gemeinsames Limit für alle Programme, die im Reiter „Programme“ als Spiel markiert sind. " +
                    "Die übrige Tageszeit bleibt für andere Programme nutzbar.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Wenn die Zeit abgelaufen ist") {
            LOCKOUTS.forEach { (key, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = edit.lockout == key,
                        onClick = { edit = edit.copy(lockout = key) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = edit.lockout == key, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }

        Button(
            onClick = { state.act("Limits gespeichert") { state.api.saveLimits(edit) } },
            enabled = changed && !state.loading && edit.days.any { it.allowed },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Speichern") }
        if (edit.days.none { it.allowed }) {
            Text("Mindestens ein Tag muss erlaubt sein.", color = MaterialTheme.colorScheme.error)
        }
    }
}

package de.screentime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.screentime.app.data.Store

@Composable
fun SettingsScreen(store: Store, state: AppState, onConnected: () -> Unit) {
    val c = remember { store.conn() }
    var host by remember { mutableStateOf(c.host) }
    var port by remember { mutableStateOf(c.port.toString()) }
    var user by remember { mutableStateOf(c.user) }
    var password by remember { mutableStateOf(c.password) }
    var showPw by remember { mutableStateOf(false) }
    var hostKey by remember { mutableStateOf(store.hostKey) }

    val portNum = port.toIntOrNull()
    val valid = host.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && portNum != null && portNum in 1..65535

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Verbindung zum PC") {
            OutlinedTextField(
                host, { host = it.trim() }, label = { Text("PC-Adresse (IP)") },
                supportingText = { Text("z. B. 192.168.178.25 – zeigt das Installationsskript an") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                user, { user = it.trim() }, label = { Text("Linux-Benutzer (Eltern)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Linux-Passwort") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Verbergen" else "Zeigen") }
                },
            )
            Button(
                enabled = valid && !state.loading,
                onClick = {
                    store.save(host, portNum ?: 22, user, password)
                    state.act("Verbindung klappt", onSuccess = {
                        hostKey = store.hostKey
                        onConnected()
                    })
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern & verbinden") }
            Text(
                "Die Zugangsdaten werden nur auf diesem Handy gespeichert, verschlüsselt im Android-Schlüsselspeicher.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Sicherheit") {
            Text(
                if (hostKey.isBlank()) "Noch kein PC-Schlüssel gespeichert. Er wird beim ersten Verbinden gemerkt."
                else "Bekannter PC-Schlüssel:\n$hostKey",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                enabled = hostKey.isNotBlank(),
                onClick = {
                    store.hostKey = ""
                    hostKey = ""
                },
            ) { Text("PC-Schlüssel zurücksetzen") }
            Text(
                "Nur nötig, wenn der PC neu installiert wurde. Die App verbindet sich sonst nur mit genau diesem PC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

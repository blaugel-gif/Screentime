package de.screentime.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import de.screentime.app.data.Api
import de.screentime.app.data.Store

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Heute", Icons.Filled.Home),
    Tab("Limits", Icons.Filled.DateRange),
    Tab("Programme", Icons.AutoMirrored.Filled.List),
    Tab("Einstellungen", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreentimeApp(store: Store) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state = remember { AppState(Api(store), scope, snackbar) }
    var tab by rememberSaveable { mutableIntStateOf(if (store.conn().complete) 0 else 3) }

    LaunchedEffect(Unit) {
        if (store.conn().complete) state.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val child = state.status?.child?.replaceFirstChar { it.uppercase() }
                    Text(if (child != null) "Screentime · $child" else "Screentime")
                },
                actions = {
                    IconButton(onClick = { state.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> OverviewScreen(state)
                1 -> LimitsScreen(state)
                2 -> AppsScreen(state)
                else -> SettingsScreen(store, state, onConnected = { tab = 0 })
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

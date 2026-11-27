package io.github.coki83.carservice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.coki83.carservice.data.Store

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val state by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    CarServiceTheme {
        BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            when (tab) {
                Tab.HOME -> HomeScreen(state, store, pad)
                Tab.HISTORY -> HistoryScreen(state, pad)
                Tab.SETTINGS -> SettingsScreen(state, store, pad)
            }
        }
        if (state.vehicle.odometerUpdated == 0L) CarSetupDialog(state, store, firstRun = true, onDismiss = {})
    }
}

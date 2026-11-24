package io.kiroku.runtime.host.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kiroku.runtime.host.ui.RuntimeHostViewModel
import io.kiroku.runtime.host.ui.components.EmptyState
import io.kiroku.runtime.host.ui.components.ScreenTitle
import io.kiroku.runtime.host.ui.theme.Accent
import io.kiroku.runtime.host.ui.theme.TextSecondary
import io.kiroku.runtime.host.ui.displayTime

@Composable
fun LogsScreen(vm: RuntimeHostViewModel) {
    val logs by vm.logs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle("Logs", "Runtime activity, newest last")
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = vm::clearLogs) { Text("Clear") }
        }
        if (logs.isEmpty()) {
            EmptyState("Nothing logged yet.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { entry ->
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            "${entry.displayTime()}  ${entry.level}  ${entry.tag}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.level == "ERROR") MaterialTheme.colorScheme.error else Accent,
                        )
                        Text(
                            entry.message,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

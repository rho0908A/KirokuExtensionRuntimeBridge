package io.kiroku.runtime.host.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kiroku.runtime.host.ui.RuntimeHostViewModel
import io.kiroku.runtime.host.ui.components.CardContainer
import io.kiroku.runtime.host.ui.components.EmptyState
import io.kiroku.runtime.host.ui.components.ScreenTitle
import io.kiroku.runtime.host.ui.components.SectionHeader
import io.kiroku.runtime.host.ui.theme.CardDarkHigh
import io.kiroku.runtime.host.ui.theme.TextSecondary
import io.kiroku.runtime.ipc.BackendType

@Composable
fun RepositoriesScreen(vm: RuntimeHostViewModel) {
    val repositories by vm.repositories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var addDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(BackendType.ANIYOMI) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ScreenTitle(
                title = "Repositories",
                subtitle = "Extension indexes the runtime downloads from",
            )
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(enabled = busy == null, onClick = vm::refreshCatalogue) { Text("Refresh all") }
                TextButton(enabled = busy == null, onClick = { addDialog = true }) { Text("Add repository") }
            }
        }
        item { SectionHeader("Configured (${repositories.size})") }
        if (repositories.isEmpty()) {
            item { EmptyState("No repositories configured.") }
        }
        items(repositories) { repository ->
            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            repository.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(repository.url, fontSize = 11.sp, color = TextSecondary)
                        Text(
                            "${repository.backend.name.lowercase()} • " +
                                if (repository.enabled) "enabled" else "disabled",
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                        repository.lastError?.let { error ->
                            Text(error.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { vm.removeRepository(repository.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = TextSecondary)
                    }
                }
                HorizontalDivider(color = CardDarkHigh)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Add repository") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("index.min.json / repo.json URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = backend == BackendType.ANIYOMI,
                            onClick = { backend = BackendType.ANIYOMI },
                            label = { Text("Aniyomi") },
                        )
                        FilterChip(
                            selected = backend == BackendType.CLOUDSTREAM,
                            onClick = { backend = BackendType.CLOUDSTREAM },
                            label = { Text("CloudStream") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.startsWith("http"),
                    onClick = {
                        vm.addRepository(url.trim(), backend, null)
                        addDialog = false
                        url = ""
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } },
        )
    }
}

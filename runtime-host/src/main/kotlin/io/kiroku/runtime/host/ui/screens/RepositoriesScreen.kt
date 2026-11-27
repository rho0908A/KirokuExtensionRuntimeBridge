package io.kiroku.runtime.host.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kiroku.runtime.host.ui.RuntimeHostViewModel
import io.kiroku.runtime.host.ui.components.CardContainer
import io.kiroku.runtime.host.ui.components.ManagerBadge
import io.kiroku.runtime.host.ui.components.managerOrder
import io.kiroku.runtime.host.ui.components.managerStyle
import io.kiroku.runtime.host.ui.theme.Accent
import io.kiroku.runtime.host.ui.theme.CardDarkHigh
import io.kiroku.runtime.host.ui.theme.TextSecondary
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ExtensionRepository


@Composable
fun RepositoriesScreen(vm: RuntimeHostViewModel) {
    val repositories by vm.repositories.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var addFor by remember { mutableStateOf<BackendType?>(null) }
    var url by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Repositories",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Extension indexes the runtime downloads from",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                TextButton(
                    enabled = busy == null,
                    onClick = vm::refreshCatalogue,
                ) { Text("Refresh") }
            }
        }

        BackendType.ANIYOMI.managerOrder.forEach { backend ->
            val style = backend.managerStyle()
            val managerRepositories = repositories.filter { it.backend == backend }
            val installedCount = installed.count { it.backend == backend }

            item(key = "header-$backend") {
                ManagerHeader(
                    backend = backend,
                    countLabel = "$installedCount installed",
                )
            }
            item(key = "card-$backend") {
                CardContainer {
                    if (managerRepositories.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 26.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.LinkOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "No repositories added yet",
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        managerRepositories.forEachIndexed { index, repository ->
                            if (index > 0) HorizontalDivider(color = CardDarkHigh)
                            RepositoryRow(
                                repository = repository,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(repository.url))
                                    vm.report("Repository URL copied")
                                },
                                onRemove = { vm.removeRepository(repository.id) },
                            )
                        }
                    }
                    HorizontalDivider(color = CardDarkHigh)
                    AddRepositoryRow(
                        title = style.title,
                        onClick = {
                            url = ""
                            addFor = backend
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    val manager = addFor
    if (manager != null) {
        AlertDialog(
            onDismissRequest = { addFor = null },
            title = { Text("Add Repository to ${manager.managerStyle().title}") },
            text = {
                Column {
                    Text(
                        text = when (manager) {
                            BackendType.CLOUDSTREAM -> "Paste a CloudStream repo.json or plugin list URL."
                            else -> "Paste an Aniyomi / Mihon index.min.json or repo.json URL."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://…/index.min.json") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.trim().startsWith("http"),
                    onClick = {
                        vm.addRepository(url.trim(), manager, null)
                        addFor = null
                        url = ""
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addFor = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
        )
    }
}

@Composable
private fun ManagerHeader(backend: BackendType, countLabel: String) {
    val style = backend.managerStyle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerBadge(icon = style.icon, tint = style.tint, background = style.badge)
        Spacer(Modifier.width(12.dp))
        Text(
            style.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Surface(color = CardDarkHigh, shape = RoundedCornerShape(10.dp)) {
            Text(
                countLabel,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun RepositoryRow(
    repository: ExtensionRepository,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerBadge(
            icon = Icons.Rounded.Description,
            tint = TextSecondary,
            background = CardDarkHigh,
            size = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repositoryPath(repository.url),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = repositoryHost(repository.url),
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            repository.lastError?.let { error ->
                Text(
                    text = error.message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy repository URL",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Remove repository",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddRepositoryRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Add Repository to $title",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Accent,
        )
    }
}

/** `https://host/a/b/index.min.json` -> `/a/b/index.min.json`, matching the manager card layout. */
private fun repositoryPath(url: String): String {
    val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
    return path.ifBlank { url }
}

private fun repositoryHost(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull().orEmpty()

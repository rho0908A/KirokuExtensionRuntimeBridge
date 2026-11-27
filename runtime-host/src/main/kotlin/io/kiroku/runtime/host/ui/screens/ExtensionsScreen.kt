package io.kiroku.runtime.host.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kiroku.runtime.host.ui.RuntimeHostViewModel
import io.kiroku.runtime.host.ui.components.CardContainer
import io.kiroku.runtime.host.ui.components.EmptyState
import io.kiroku.runtime.host.ui.components.ManagerBadge
import io.kiroku.runtime.host.ui.components.ScreenTitle
import io.kiroku.runtime.host.ui.components.SectionHeader
import io.kiroku.runtime.host.ui.components.managerOrder
import io.kiroku.runtime.host.ui.components.managerStyle
import io.kiroku.runtime.host.ui.theme.Accent
import io.kiroku.runtime.host.ui.theme.CardDarkHigh
import io.kiroku.runtime.host.ui.theme.TextSecondary
import io.kiroku.runtime.ipc.BackendType

@Composable
fun ExtensionsScreen(vm: RuntimeHostViewModel) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ScreenTitle(
                title = "Extensions",
                subtitle = info?.let { "Runtime ${it.runtimeVersion} • protocol ${it.protocolVersion}" }
                    ?: "Runtime is starting…",
            )
        }

        if (busy != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(busy.orEmpty(), fontSize = 12.sp, color = Accent)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress?.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionHeader("Installed (${installed.size})") }
        if (installed.isEmpty()) {
            item { EmptyState("No extensions installed. Install one from the repositories below.") }
        } else {
            item {
                CardContainer {
                    installed.forEachIndexed { index, extension ->
                        if (index > 0) HorizontalDivider(color = CardDarkHigh)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    extension.metadata.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${extension.metadata.versionName} • ${extension.backend.name.lowercase()} • " +
                                        "${extension.sources.size} source(s)",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                                extension.loadError?.let { error ->
                                    Text(
                                        "Load failed: ${error.message}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            TextButton(
                                enabled = busy == null,
                                onClick = { vm.uninstall(extension.id) },
                            ) { Text("Remove", color = TextSecondary) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AVAILABLE FROM REPOSITORIES (${available.size})",
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = busy == null,
                    onClick = vm::refreshCatalogue,
                ) { Text("Refresh") }
            }
        }

        if (available.isEmpty()) {
            item {
                Column {
                    EmptyState("The extension catalogue has not been loaded yet.")
                    TextButton(
                        enabled = busy == null,
                        onClick = vm::refreshCatalogue,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) { Text("Load catalogue") }
                }
            }
        } else {
            BackendType.ANIYOMI.managerOrder.forEach { backend ->
                val entries = available.filter { it.backend == backend }
                if (entries.isEmpty()) return@forEach
                val style = backend.managerStyle()

                item(key = "available-header-$backend") {
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
                                "${entries.size} available",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                item(key = "available-card-$backend") {
                    CardContainer {
                        entries.forEachIndexed { index, remote ->
                            if (index > 0) HorizontalDivider(color = CardDarkHigh)
                            val installedExtension = installed.firstOrNull { it.id == remote.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        remote.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${remote.versionName} • ${remote.backend.name.lowercase()}" +
                                            (remote.language?.let { " • $it" } ?: ""),
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                    )
                                }
                                when {
                                    installedExtension == null -> TextButton(
                                        enabled = busy == null,
                                        onClick = { vm.install(remote.id) },
                                    ) { Text("Install") }

                                    installedExtension.metadata.versionCode < remote.versionCode -> TextButton(
                                        enabled = busy == null,
                                        onClick = { vm.update(remote.id) },
                                    ) { Text("Update") }

                                    else -> Text(
                                        "Installed",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(end = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Add repositories in the Repositories tab to list more extensions",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

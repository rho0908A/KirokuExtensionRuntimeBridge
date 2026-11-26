package io.kiroku.runtime.host.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
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
import io.kiroku.runtime.host.ui.components.ActionRow
import io.kiroku.runtime.host.ui.components.CardContainer
import io.kiroku.runtime.host.ui.components.EmptyState
import io.kiroku.runtime.host.ui.components.IconTile
import io.kiroku.runtime.host.ui.components.KeyValueRow
import io.kiroku.runtime.host.ui.components.SectionHeader
import io.kiroku.runtime.host.ui.components.ToggleRow
import io.kiroku.runtime.host.ui.theme.Accent
import io.kiroku.runtime.host.ui.theme.CardDarkHigh
import io.kiroku.runtime.host.ui.theme.TextSecondary

/**
 * The Extension Manager screen: runtime status, Aniyomi settings and plugin installation/sync
 * actions. Every row maps to a real runtime operation, for anime (Anikku) and manga (Mihon) alike
 * because this runtime build executes Aniyomi anime sources only.
 */
@Composable
fun ExtensionManagerScreen(
    vm: RuntimeHostViewModel,
    installedCount: Int,
    onLoadPackage: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    val info by vm.info.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var pathDialog by remember { mutableStateOf(false) }
    var rollbackDialog by remember { mutableStateOf(false) }
    var clientDialog by remember { mutableStateOf(false) }
    var pathDraft by remember { mutableStateOf(settings.aniyomiAnimeApkPath.orEmpty()) }
    var mangaPathDialog by remember { mutableStateOf(false) }
    var mangaPathDraft by remember { mutableStateOf(settings.aniyomiMangaApkPath.orEmpty()) }
    var clientDraft by remember { mutableStateOf("") }

    val ready = info != null
    val animeReady = info?.capabilities?.backends?.any { it.name == "ANIYOMI" } == true
    val cloudstreamReady = info?.capabilities?.backends?.any { it.name == "CLOUDSTREAM" } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        CardContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Rounded.CheckCircle, tint = MaterialTheme.colorScheme.onBackground, background = Accent)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (ready) "Plugin Installed" else "Plugin Not Ready",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = listOfNotNull(
                            "Aniyomi".takeIf { animeReady },
                            "Cloudstream".takeIf { cloudstreamReady },
                        ).joinToString(" & ") + if (ready) " ready" else " unavailable",
                        fontSize = 13.sp,
                        color = Accent,
                    )
                }
            }
            HorizontalDivider(color = CardDarkHigh)
            KeyValueRow("Version", info?.runtimeVersion ?: "—")
            KeyValueRow("Release", info?.runtimeVersionCode?.let { "v1.0.$it" } ?: "—")
            KeyValueRow("Extensions", installedCount.toString())
        }

        SectionHeader("Aniyomi settings")

        CardContainer {
            ToggleRow(
                icon = Icons.Rounded.Storage,
                title = "Aniyomi Internal Anime Extensions",
                description = "Install anime extensions only into the runtime (no Package Manager)",
                checked = settings.aniyomiInternalAnimeInstalls,
                onCheckedChange = vm::setInternalAnimeInstalls,
            )
            HorizontalDivider(color = CardDarkHigh)
            ToggleRow(
                icon = Icons.Rounded.Storage,
                title = "Aniyomi Internal Manga Extensions",
                description = "Install manga extensions only into the runtime (no Package Manager)",
                checked = settings.aniyomiInternalMangaInstalls,
                onCheckedChange = vm::setInternalMangaInstalls,
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.Folder,
                title = "Custom Anime APK Path",
                description = settings.aniyomiAnimeApkPath ?: "Custom folder to import anime extension APKs from",
                onClick = {
                    pathDraft = settings.aniyomiAnimeApkPath.orEmpty()
                    pathDialog = true
                },
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.Folder,
                title = "Custom Manga APK Path",
                description = settings.aniyomiMangaApkPath ?: "Custom folder to import manga extension APKs from",
                onClick = {
                    mangaPathDraft = settings.aniyomiMangaApkPath.orEmpty()
                    mangaPathDialog = true
                },
            )
        }

        SectionHeader("Plugin installation & sync")

        CardContainer {
            ActionRow(
                icon = Icons.Rounded.CloudDownload,
                title = "Update Plugin",
                description = "Check the enabled repositories and install available extension updates",
                enabled = busy == null,
                onClick = vm::updateAll,
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.UploadFile,
                title = "Load Plugin APK from Storage",
                description = "Select a runtime package from local storage to install manually",
                enabled = busy == null,
                onClick = onLoadPackage,
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.Refresh,
                title = "Force Re-download",
                description = "Re-download and reinstall every installed extension from scratch",
                enabled = busy == null,
                onClick = vm::forceRedownload,
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.History,
                title = "Rollback Version",
                description = "Restore the package the runtime kept before the last update",
                enabled = busy == null && installed.any { vm.availableBackupCount(it.id) > 0 },
                onClick = { rollbackDialog = true },
            )
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Rounded.OpenInNew,
                title = "Runtime Host Releases",
                description = "Open the GitHub releases page for changelogs and manual downloads",
                onClick = onOpenReleases,
            )
            if (settings.aniyomiAnimeApkPath != null) {
                HorizontalDivider(color = CardDarkHigh)
                ActionRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Import From Custom Path",
                    description = "Install every .apk found in ${settings.aniyomiAnimeApkPath}",
                    enabled = busy == null,
                    onClick = vm::importFromCustomPath,
                )
            }
        }

        SectionHeader("Client access")

        CardContainer {
            if (settings.allowedClients.isEmpty()) {
                EmptyState("Only apps signed with the same certificate may bind. Add a package name to allow another app.")
            } else {
                settings.allowedClients.forEach { client ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(client, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.setClientAllowed(client, false) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = TextSecondary)
                        }
                    }
                }
            }
            HorizontalDivider(color = CardDarkHigh)
            ActionRow(
                icon = Icons.Filled.Add,
                title = "Allow Client App",
                description = "Add the package name of a host application that may bind to this runtime",
                onClick = {
                    clientDraft = ""
                    clientDialog = true
                },
            )
        }

        if (busy != null) {
            Text(
                text = busy.orEmpty(),
                fontSize = 12.sp,
                color = Accent,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    if (pathDialog) {
        AlertDialog(
            onDismissRequest = { pathDialog = false },
            title = { Text("Custom Anime APK Path") },
            text = {
                Column {
                    Text(
                        "Folder the runtime imports Aniyomi .apk files from.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pathDraft,
                        onValueChange = { pathDraft = it },
                        singleLine = true,
                        label = { Text("Absolute path") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAnimePath(pathDraft)
                    pathDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pathDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (mangaPathDialog) {
        AlertDialog(
            onDismissRequest = { mangaPathDialog = false },
            title = { Text("Custom Manga APK Path") },
            text = {
                Column {
                    Text(
                        "Folder the runtime imports Mihon manga .apk files from.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mangaPathDraft,
                        onValueChange = { mangaPathDraft = it },
                        singleLine = true,
                        label = { Text("Absolute path") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMangaPath(mangaPathDraft)
                    mangaPathDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { mangaPathDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (rollbackDialog) {
        AlertDialog(
            onDismissRequest = { rollbackDialog = false },
            title = { Text("Rollback Version") },
            text = {
                Column {
                    if (installed.isEmpty()) {
                        Text("No extensions installed.", color = TextSecondary, fontSize = 13.sp)
                    } else {
                        installed.forEach { extension ->
                            val backups = vm.availableBackupCount(extension.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(extension.metadata.name, fontSize = 13.sp)
                                TextButton(
                                    enabled = backups > 0,
                                    onClick = {
                                        vm.rollback(extension.id)
                                        rollbackDialog = false
                                    },
                                ) { Text(if (backups > 0) "Restore ($backups)" else "No backup") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rollbackDialog = false }) { Text("Close") } },
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (clientDialog) {
        AlertDialog(
            onDismissRequest = { clientDialog = false },
            title = { Text("Allow Client App") },
            text = {
                OutlinedTextField(
                    value = clientDraft,
                    onValueChange = { clientDraft = it },
                    singleLine = true,
                    label = { Text("Package name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = clientDraft.isNotBlank(),
                    onClick = {
                        vm.setClientAllowed(clientDraft.trim(), true)
                        clientDialog = false
                    },
                ) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { clientDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
        )
    }
}

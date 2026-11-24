package io.kiroku.runtime.host.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kiroku.runtime.host.ui.screens.ExtensionManagerScreen
import io.kiroku.runtime.host.ui.screens.ExtensionsScreen
import io.kiroku.runtime.host.ui.screens.LogsScreen
import io.kiroku.runtime.host.ui.screens.RepositoriesScreen
import java.io.File

private const val RELEASES_URL = "https://github.com/rho0908A/KirokuExtensionRuntimeBridge/releases"

private enum class HostTab(val label: String) {
    Extensions("Extensions"),
    Repositories("Repositories"),
    Logs("Logs"),
}

@Composable
fun RuntimeHostApp(vm: RuntimeHostViewModel) {
    var tab by rememberSaveable { mutableStateOf(HostTab.Extensions) }
    var managerOpen by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val installed by vm.installed.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val suffix = if (uri.lastPathSegment.orEmpty().endsWith(".cs3", true)) ".cs3" else ".apk"
                val target = File(context.cacheDir, "import-${System.currentTimeMillis()}$suffix")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("The selected file could not be read")
                vm.installFromFile(target.absolutePath)
            }.onFailure { vm.report("Could not load the selected package: ${it.message}") }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (managerOpen) {
                ManagerTopBar(onBack = { managerOpen = false })
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Kiroku Runtime",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { managerOpen = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Extension Manager")
                    }
                }
            }
        },
        bottomBar = {
            if (!managerOpen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == HostTab.Extensions,
                        onClick = { tab = HostTab.Extensions },
                        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                        label = { Text(HostTab.Extensions.label) },
                    )
                    NavigationBarItem(
                        selected = tab == HostTab.Repositories,
                        onClick = { tab = HostTab.Repositories },
                        icon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                        label = { Text(HostTab.Repositories.label) },
                    )
                    NavigationBarItem(
                        selected = tab == HostTab.Logs,
                        onClick = { tab = HostTab.Logs },
                        icon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                        label = { Text(HostTab.Logs.label) },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { managerOpen = true },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        label = { Text("Manager") },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (managerOpen) {
                ExtensionManagerScreen(
                    vm = vm,
                    installedCount = installed.size,
                    onLoadPackage = { packagePicker.launch(arrayOf("*/*")) },
                    onOpenReleases = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                        }
                    },
                )
            } else {
                when (tab) {
                    HostTab.Extensions -> ExtensionsScreen(vm)
                    HostTab.Repositories -> RepositoriesScreen(vm)
                    HostTab.Logs -> LogsScreen(vm)
                }
            }
        }
    }
}

/** The pill-shaped title bar with a back button, as in the Extension Manager design. */
@Composable
private fun ManagerTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier.padding(end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Extension Manager", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

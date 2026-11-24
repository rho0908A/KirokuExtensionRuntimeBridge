package io.kiroku.runtime.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kiroku.runtime.bridge.client.KirokuRuntimeBridge
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.RuntimeStatus
import io.kiroku.runtime.ipc.StreamSource
import kotlinx.coroutines.launch

/**
 * Minimal reference integration: connect, list extensions, search, open details and extract streams.
 *
 * Everything here goes through the public SDK; no runtime internals are referenced.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SampleHostScreen()
            }
        }
    }
}

@Composable
private fun SampleHostScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { KirokuRuntimeBridge(context) }

    var status by remember { mutableStateOf<RuntimeStatus?>(null) }
    var extensions by remember { mutableStateOf<List<Extension>>(emptyList()) }
    var selected by remember { mutableStateOf<Extension?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var details by remember { mutableStateOf<AnimeDetails?>(null) }
    var episode by remember { mutableStateOf<Episode?>(null) }
    var streams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }

    fun record(message: String) {
        log.add(0, message)
    }

    LaunchedEffect(bridge) {
        bridge.status.collect { status = it }
    }
    DisposableEffect(bridge) {
        onDispose { bridge.close() }
    }

    fun run(label: String, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (t: Throwable) {
                record("$label failed: ${t.message}")
            } finally {
                busy = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text("Kiroku Bridge Sample", fontSize = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = status?.let { "${it.state}${it.info?.let { info -> " • runtime ${info.runtimeVersion}" } ?: ""}" }
                    ?: "Not connected",
                fontSize = 13.sp,
            )
            Text(status?.message.orEmpty(), fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        run("Connect") {
                            val result = bridge.connect()
                            record("connect(): ${result.state}")
                            if (result.isConnected) {
                                extensions = bridge.getExtensions()
                                record("getExtensions(): ${extensions.size} extension(s)")
                                selected = extensions.firstOrNull { it.loadError == null }
                                record("Selected ${selected?.metadata?.name ?: "none"}")
                            }
                        }
                    },
                ) { Text("Connect") }

                TextButton(
                    enabled = !busy && status?.isConnected == true,
                    onClick = {
                        run("Load extensions") {
                            extensions = bridge.getExtensions()
                            selected = extensions.firstOrNull { it.loadError == null }
                            record("Loaded ${extensions.size} extension(s)")
                        }
                    },
                ) { Text("Load extensions") }
            }

            extensions.take(4).forEach { extension ->
                TextButton(
                    enabled = !busy,
                    onClick = {
                        selected = extension
                        details = null
                        results = emptyList()
                        record("Selected ${extension.metadata.name}")
                    },
                ) { Text("${extension.metadata.name} (${extension.sources.size} sources)") }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = !busy && selected != null && query.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = {
                        val extension = selected ?: return@Button
                        run("search") {
                            results = bridge.search(extension.id, query)
                            record("search('$query') -> ${results.size} result(s)")
                        }
                    },
                ) { Text("Go") }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { anime ->
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val extension = selected ?: return@TextButton
                            run("details") {
                                val loaded = bridge.getDetails(extension.id, anime.url ?: anime.id)
                                details = loaded
                                record("details('${anime.title}') -> ${loaded.episodes.size} episode(s)")
                            }
                        },
                    ) { Text(anime.title) }
                }

                details?.episodes?.take(6)?.forEach { current ->
                    item {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val extension = selected ?: return@TextButton
                                run("streams") {
                                    episode = current
                                    streams = bridge.extractStreams(extension.id, current.url ?: current.id)
                                    record("extractStreams('${current.name}') -> ${streams.size} stream(s)")
                                }
                            },
                        ) { Text("Episode: ${current.name}") }
                    }
                }

                if (streams.isNotEmpty()) {
                    item {
                        Text("Streams for ${episode?.name}", fontSize = 13.sp)
                        streams.forEach { stream ->
                            Text(
                                "${stream.quality ?: "?"} ${stream.server ?: ""} ${stream.url}",
                                fontSize = 11.sp,
                            )
                        }
                        Divider()
                    }
                }

                item {
                    Text("Log", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                items(log) { line ->
                    Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

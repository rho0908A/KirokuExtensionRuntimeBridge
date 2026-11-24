package io.kiroku.runtime.host.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kiroku.runtime.host.ui.theme.KirokuRuntimeTheme

/** Single-activity entry point of the Runtime Host management UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KirokuRuntimeTheme {
                val vm: RuntimeHostViewModel = viewModel(
                    factory = RuntimeHostViewModel.factory(application),
                )
                RuntimeHostApp(vm)
            }
        }
    }
}

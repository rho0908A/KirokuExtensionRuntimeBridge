package io.kiroku.runtime.host

import android.app.Application
import io.kiroku.runtime.host.core.RuntimeManager

/**
 * Process-wide entry point of the Runtime Host.
 *
 * The manager is created eagerly so that a client binding to [io.kiroku.runtime.host.service.RuntimeHostService]
 * always finds initialised backends, even when the service is started before the UI.
 */
class RuntimeHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeManager.get(this)
    }
}

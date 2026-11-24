package io.kiroku.runtime.bridge.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.kiroku.runtime.ipc.IRuntimeHost
import io.kiroku.runtime.ipc.RuntimeProtocol
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the Android service binding to the Runtime Host.
 *
 * Responsibilities kept here (and out of the public bridge) are exactly the Android lifecycle ones:
 * bind/unbind, connection callbacks, and reporting unexpected disconnects to the client.
 */
internal class RuntimeConnection(
    private val context: Context,
    private val onServiceConnected: (IRuntimeHost) -> Unit,
    private val onServiceDisconnected: () -> Unit,
) {

    private val bound = AtomicBoolean(false)

    @Volatile
    private var binder: IRuntimeHost? = null

    @Volatile
    private var pendingBind: CompletableDeferred<IRuntimeHost>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val host = service?.let { IRuntimeHost.Stub.asInterface(it) }
            if (host == null) {
                pendingBind?.completeExceptionally(IllegalStateException("Runtime service returned a null binder"))
                pendingBind = null
                return
            }
            binder = host
            pendingBind?.complete(host)
            pendingBind = null
            onServiceConnected(host)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            onServiceDisconnected()
        }

        override fun onBindingDied(name: ComponentName?) {
            // The runtime app was updated or its process was killed: drop state and let the client rebind.
            binder = null
            bound.set(false)
            onServiceDisconnected()
        }

        override fun onNullBinding(name: ComponentName?) {
            pendingBind?.completeExceptionally(IllegalStateException("Runtime service refused binding (null binding)"))
            pendingBind = null
            binder = null
            bound.set(false)
            onServiceDisconnected()
        }
    }

    val isBound: Boolean get() = bound.get() && binder != null

    val current: IRuntimeHost?
        get() = binder

    /** Binds to the runtime and suspends until the service is connected or [timeoutMs] elapses. */
    suspend fun bind(timeoutMs: Long): IRuntimeHost? {
        binder?.let { return it }

        val deferred = CompletableDeferred<IRuntimeHost>()
        pendingBind = deferred

        val intent = Intent(RuntimeProtocol.SERVICE_ACTION).apply {
            component = ComponentName(RuntimeProtocol.RUNTIME_PACKAGE, RuntimeProtocol.RUNTIME_SERVICE_CLASS)
        }

        val started = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        } catch (t: SecurityException) {
            pendingBind = null
            throw t
        }

        if (!started) {
            pendingBind = null
            return null
        }
        bound.set(true)

        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (result == null) {
            pendingBind = null
            unbind()
        }
        return result
    }

    /** Suspends until an unexpected disconnect is followed by a successful rebind. */
    suspend fun rebind(timeoutMs: Long): IRuntimeHost? {
        unbind()
        return bind(timeoutMs)
    }

    fun unbind() {
        if (bound.getAndSet(false)) {
            runCatching { context.unbindService(connection) }
        }
        binder = null
    }

    /** Helper for clients that want to await the binding state in a cancellable way. */
    suspend fun awaitBinder(timeoutMs: Long): IRuntimeHost = suspendCancellableCoroutine { cont ->
        val host = binder
        if (host != null) {
            cont.resume(host)
        } else {
            cont.resumeWithException(IllegalStateException("Runtime is not bound"))
        }
    }
}

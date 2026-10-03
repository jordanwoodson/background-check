package dev.bgcheck.app.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.bgcheck.app.BuildConfig
import dev.bgcheck.app.data.AccessState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Owns the permission listeners and a non-daemon Shizuku UserService binding. */
class ShizukuBridge(context: Context) {
    private val _access = MutableStateFlow(AccessState.CHECKING)
    val access: StateFlow<AccessState> = _access.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val commandMutex = Mutex()
    @Volatile private var closed = false
    @Volatile private var service: ICommandService? = null
    private var binding = false
    private var registered = false
    private var bindTimeout: Job? = null
    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.applicationContext, CommandService::class.java))
        .tag("bgcheck-command-service")
        .version(BuildConfig.VERSION_CODE)
        .processNameSuffix("bgcheck_shell")
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)

    private val serviceDeath = IBinder.DeathRecipient {
        scope.launch {
            clearService()
            update(AccessState.ERROR, "The background service disconnected. Check access to reconnect.")
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            scope.launch {
                if (closed) return@launch
                if (binder == null || !binder.pingBinder()) {
                    clearService()
                    update(AccessState.ERROR, "Shizuku returned an unavailable service.")
                    return@launch
                }
                try {
                    if (!permitted()) {
                        detach()
                        update(AccessState.NEEDS_PERMISSION, "Allow Background Check in Shizuku.")
                        return@launch
                    }
                    clearService()
                    binder.linkToDeath(serviceDeath, 0)
                    service = ICommandService.Stub.asInterface(binder)
                    update(AccessState.READY, null)
                } catch (failure: Exception) {
                    clearService()
                    update(AccessState.ERROR, failure.message ?: "Could not connect to Shizuku.")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scope.launch {
                clearService()
                if (!closed) update(AccessState.ERROR, "Shizuku service disconnected. Check access to reconnect.")
            }
        }
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { check() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        clearService()
        registered = false
        update(AccessState.NOT_RUNNING, "Start Shizuku, then check access again.")
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) check()
            else {
                detach()
                update(AccessState.NEEDS_PERMISSION, "Permission was not granted. Allow Background Check in Shizuku.")
            }
        }
    }

    init {
        Shizuku.addBinderDeadListener(binderDead, handler)
        Shizuku.addRequestPermissionResultListener(permissionResult, handler)
        Shizuku.addBinderReceivedListenerSticky(binderReceived, handler)
        check()
    }

    fun check() {
        scope.launch {
            if (closed) return@launch
            try {
                if (!Shizuku.pingBinder()) {
                    clearService()
                    registered = false
                    update(AccessState.NOT_RUNNING, "Start Shizuku, then check access again.")
                } else if (Shizuku.getVersion() < 12) {
                    detach()
                    update(AccessState.ERROR, "Update Shizuku to version 12 or newer.")
                } else if (!permitted()) {
                    detach()
                    update(AccessState.NEEDS_PERMISSION, "Allow Background Check in Shizuku.")
                } else if (service?.asBinder()?.pingBinder() == true) {
                    update(AccessState.READY, null)
                } else if (!binding) {
                    detach()
                    binding = true
                    registered = true
                    update(AccessState.CONNECTING, "Connecting to Shizuku…")
                    Shizuku.bindUserService(serviceArgs, connection)
                    bindTimeout = scope.launch {
                        delay(12_000)
                        if (binding) {
                            detach()
                            update(AccessState.ERROR, "Shizuku connection timed out. Check access to retry.")
                        }
                    }
                }
            } catch (failure: Exception) {
                detach()
                update(AccessState.ERROR, failure.message ?: "Could not check Shizuku access.")
            }
        }
    }

    fun requestPermission() {
        scope.launch {
            if (closed) return@launch
            try {
                if (!Shizuku.pingBinder()) {
                    update(AccessState.NOT_RUNNING, "Start Shizuku before granting access.")
                } else if (permitted()) {
                    check()
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    update(AccessState.NEEDS_PERMISSION, "Open Shizuku and allow Background Check under Authorized applications.")
                } else {
                    Shizuku.requestPermission(PERMISSION_REQUEST)
                }
            } catch (failure: Exception) {
                update(AccessState.ERROR, failure.message ?: "Could not request Shizuku permission.")
            }
        }
    }

    suspend fun execute(args: List<String>): String {
        val command = CommandPolicy.validate(args)
        return commandMutex.withLock {
            withContext(Dispatchers.IO) {
                check(!closed) { "Shizuku connection is closed." }
                val connected = service
                if (_access.value != AccessState.READY || connected == null || !Shizuku.pingBinder()) {
                    throw IOException("Shizuku is not connected. Check access and try again.")
                }
                if (!permitted()) {
                    scope.launch { detach(); update(AccessState.NEEDS_PERMISSION, "Shizuku permission was revoked.") }
                    throw SecurityException("Shizuku permission was revoked.")
                }
                val result = try { connected.execute(command.toTypedArray()) }
                catch (failure: DeadObjectException) { check(); throw IOException("Shizuku disconnected during the command.", failure) }
                result.getString("error")?.let { throw IOException(it) }
                if (!result.containsKey("exitCode") || !result.containsKey("stdout") || !result.containsKey("stderr")) {
                    throw IOException("The Shizuku service returned an incomplete response.")
                }
                val stdout = result.getString("stdout").orEmpty()
                val stderr = result.getString("stderr").orEmpty()
                if (result.getInt("exitCode") != 0) {
                    val detail = stderr.ifBlank { stdout }.trim().take(1000)
                    throw IOException(detail.ifBlank { "Command failed with exit code ${result.getInt("exitCode")}." })
                }
                if (stderr.isNotBlank()) throw IOException(stderr.trim().take(1000))
                if (stdout.lineSequence().any { line ->
                    val value = line.trimStart()
                    value.startsWith("Error:", ignoreCase = true) || value.startsWith("Exception occurred") ||
                        value.startsWith("Security exception:", ignoreCase = true)
                }) throw IOException(stdout.trim().take(1000))
                stdout
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        val cleanup = {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
            detach()
            scope.cancel()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else handler.post { cleanup() }
    }

    private fun permitted() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun clearService() {
        bindTimeout?.cancel()
        bindTimeout = null
        service?.asBinder()?.let { runCatching { it.unlinkToDeath(serviceDeath, 0) } }
        service = null
        binding = false
    }

    private fun detach() {
        clearService()
        if (registered) {
            // remove=true alone leaves Shizuku's local connection cache alive until binder death.
            // Detach it synchronously before removal so an immediate bind cannot reuse a dying listener.
            runCatching { Shizuku.unbindUserService(serviceArgs, connection, false) }
            runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        }
        registered = false
    }

    private fun update(state: AccessState, detail: String?) {
        if (closed) return
        _message.value = detail
        _access.value = state
    }

    private companion object { const val PERMISSION_REQUEST = 6204 }
}

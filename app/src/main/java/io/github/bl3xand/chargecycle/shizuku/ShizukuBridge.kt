package io.github.bl3xand.chargecycle.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.bl3xand.chargecycle.BuildConfig
import io.github.bl3xand.chargecycle.data.ChargeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Reading the charging-mode Settings.Secure keys back from this app's own process is blocked by
 * the OS (see `ChargeModeController`); Shizuku lets us run that read - and grant this app
 * WRITE_SECURE_SETTINGS in the first place - inside a shell-privileged process instead.
 *
 * The UserService connection is bound once and reused: repeatedly calling
 * Shizuku.bindUserService/unbindUserService back-to-back (once per call) triggers a
 * ConcurrentModificationException inside Shizuku's own ShizukuServiceConnection - it isn't meant
 * to be bound and torn down on every call.
 */
object ShizukuBridge {

    // daemon(true): one persistent process shared across app launches, since we never unbind.
    // With daemon(false), every fresh app process (each relaunch, tile click, etc.) that binds
    // and never unbinds leaks its own privileged process instead of reusing one.
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, PrivilegedUserService::class.java.name)
    )
        .daemon(true)
        .processNameSuffix("privileged")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val bindMutex = Mutex()
    @Volatile
    private var boundService: IPrivilegedService? = null

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean =
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun requestPermission(requestCode: Int) {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    suspend fun readCurrentMode(): ChargeMode? {
        if (!hasPermission()) return null
        val values = readModeValues() ?: return null
        val (optimizationMode, adaptiveEnabled) = values
        return ChargeMode.entries.find {
            it.optimizationMode == optimizationMode && it.adaptiveEnabled == adaptiveEnabled
        }
    }

    /** Grants this app WRITE_SECURE_SETTINGS without needing `adb shell pm grant` from a computer. */
    suspend fun grantWriteSecureSettings(context: Context): Boolean {
        if (!hasPermission()) return false
        val service = getOrBindService() ?: return false
        // service.grantWriteSecureSettings() is a synchronous (blocking) Binder call - keep it off
        // the caller's dispatcher (typically Main) so a slow/hung remote process can't cause an ANR.
        return withContext(Dispatchers.IO) {
            try {
                service.grantWriteSecureSettings(context.packageName)
            } catch (_: Exception) {
                boundService = null
                false
            }
        }
    }

    private suspend fun readModeValues(): Pair<Int, Int>? {
        val service = getOrBindService() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val values = service.readModeValues()
                if (values.size == 2 && values[0] >= 0 && values[1] >= 0) values[0] to values[1] else null
            } catch (_: Exception) {
                // Binder likely died without onServiceDisconnected firing yet - drop it so the next call rebinds.
                boundService = null
                null
            }
        }
    }

    private suspend fun getOrBindService(): IPrivilegedService? {
        boundService?.let { return it }
        return bindMutex.withLock {
            boundService?.let { return@withLock it }
            val connected = CompletableDeferred<IPrivilegedService?>()
            val oneShotConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    val service = IPrivilegedService.Stub.asInterface(binder)
                    boundService = service
                    connected.complete(service)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    boundService = null
                    connected.complete(null)
                }
            }
            try {
                Shizuku.bindUserService(userServiceArgs, oneShotConnection)
            } catch (_: Throwable) {
                return@withLock null
            }
            // If this never connects (timeout) or the caller is cancelled first, unbind the
            // dangling connection instead of leaving it registered with Shizuku forever - repeated
            // never-unbound connections are what caused the ConcurrentModificationException before.
            try {
                withTimeoutOrNull(5_000) { connected.await() }
            } finally {
                if (boundService == null) {
                    try {
                        Shizuku.unbindUserService(userServiceArgs, oneShotConnection, true)
                    } catch (_: Throwable) {
                        // Best effort.
                    }
                }
            }
        }
    }
}

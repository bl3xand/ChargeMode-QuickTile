package io.github.bl3xand.chargecycle.shizuku

import io.github.bl3xand.chargecycle.data.ChargeModeController
import java.util.concurrent.TimeUnit

/**
 * Instantiated by Shizuku inside a separate, privileged (shell UID) process. Reading these @hide
 * Settings.Secure keys through the Settings API is gated by the *calling package's* identity, not
 * the process's raw UID - so even here, going through Context.contentResolver still gets denied,
 * because Shizuku's Context is scoped to this app's package. Shelling out to `settings`/`pm`
 * instead spawns plain child processes with no package attribution, which behave the same way
 * `adb shell` commands do.
 */
class PrivilegedUserService : IPrivilegedService.Stub() {

    override fun readModeValues(): IntArray {
        val optimizationMode = readSetting(ChargeModeController.KEY_OPTIMIZATION_MODE)
        val adaptiveEnabled = readSetting(ChargeModeController.KEY_ADAPTIVE_ENABLED)
        return if (optimizationMode != null && adaptiveEnabled != null) {
            intArrayOf(optimizationMode, adaptiveEnabled)
        } else {
            intArrayOf(-1, -1)
        }
    }

    override fun grantWriteSecureSettings(packageName: String): Boolean {
        return try {
            val process = ProcessBuilder("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS")
                .redirectErrorStream(true)
                .start()
            awaitExit(process) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun readSetting(key: String): Int? {
        return try {
            val process = ProcessBuilder("settings", "get", "secure", key)
                .redirectErrorStream(true)
                .start()
            // Wait for exit before reading: `settings get` only ever prints one short line, so
            // there's no risk of its output pipe filling up and deadlocking the process while
            // nothing is draining it.
            if (!awaitExit(process)) return null
            process.inputStream.bufferedReader().readText().trim().toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** Bounds how long a stuck `settings`/`pm` child process can block this Binder call. */
    private fun awaitExit(process: Process): Boolean {
        val exited = process.waitFor(5, TimeUnit.SECONDS)
        if (!exited) process.destroyForcibly()
        return exited
    }
}

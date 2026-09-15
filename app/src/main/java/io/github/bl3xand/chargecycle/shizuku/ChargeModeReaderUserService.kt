package io.github.bl3xand.chargecycle.shizuku

import io.github.bl3xand.chargecycle.data.ChargeModeController

/**
 * Instantiated by Shizuku inside a separate, privileged (shell UID) process. Reading these @hide
 * Settings.Secure keys through the Settings API is gated by the *calling package's* identity, not
 * the process's raw UID - so even here, going through Context.contentResolver still gets denied,
 * because Shizuku's Context is scoped to this app's package. Shelling out to the `settings`
 * command instead spawns a plain child process with no package attribution, which reads the same
 * way `adb shell settings get` does.
 */
class ChargeModeReaderUserService : IChargeModeReaderService.Stub() {

    override fun readModeValues(): IntArray {
        val optimizationMode = readSetting(ChargeModeController.KEY_OPTIMIZATION_MODE)
        val adaptiveEnabled = readSetting(ChargeModeController.KEY_ADAPTIVE_ENABLED)
        return if (optimizationMode != null && adaptiveEnabled != null) {
            intArrayOf(optimizationMode, adaptiveEnabled)
        } else {
            intArrayOf(-1, -1)
        }
    }

    private fun readSetting(key: String): Int? {
        return try {
            val process = ProcessBuilder("settings", "get", "secure", key)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

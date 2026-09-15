package io.github.bl3xand.chargemode.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Writes the two Settings.Secure keys behind Pixel's charging optimization modes, and exposes a
 * [Flow] of change notifications for them. These keys are a Google/Pixel implementation detail,
 * not a general Android API - other manufacturers use their own (undocumented) mechanisms, so
 * [isSupportedDevice] gates all of this on Build.MANUFACTURER until a provider for another OEM
 * is added.
 *
 * Requires android.permission.WRITE_SECURE_SETTINGS, which is not grantable through the normal
 * runtime permission dialog - it must be granted once via `adb shell pm grant`. Reading these
 * keys back needs more than that permission (see `ShizukuBridge`).
 */
object ChargeModeController {

    internal const val KEY_OPTIMIZATION_MODE = "charge_optimization_mode"
    internal const val KEY_ADAPTIVE_ENABLED = "adaptive_charging_enabled"

    /** True on Pixel phones, the only devices these Settings.Secure keys are known to work on. */
    val isSupportedDevice: Boolean = Build.MANUFACTURER.equals("Google", ignoreCase = true)

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Returns true if the mode was applied. */
    fun apply(context: Context, mode: ChargeMode): Boolean {
        val resolver = context.contentResolver
        return try {
            Settings.Secure.putInt(resolver, KEY_OPTIMIZATION_MODE, mode.optimizationMode)
            Settings.Secure.putInt(resolver, KEY_ADAPTIVE_ENABLED, mode.adaptiveEnabled)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Emits whenever either underlying setting changes, no matter who changed it (this app,
     * the system Settings UI, or another adb command). Callers re-read the current mode (via
     * ShizukuBridge) on each emission.
     */
    fun observeChanges(context: Context): Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            resolver.registerContentObserver(Settings.Secure.getUriFor(KEY_OPTIMIZATION_MODE), false, observer)
            resolver.registerContentObserver(Settings.Secure.getUriFor(KEY_ADAPTIVE_ENABLED), false, observer)
        } catch (_: SecurityException) {
            // Nothing to observe without permission; the flow just stays open and idle.
        }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}

package io.github.bl3xand.chargecycle.data

import androidx.annotation.StringRes
import io.github.bl3xand.chargecycle.R

/**
 * The three charging modes exposed by Pixel's "Charging optimization" screen, mapped to the
 * Settings.Secure values that drive them (found by observing the values while switching modes
 * in the system UI).
 */
enum class ChargeMode(
    val prefKey: String,
    val optimizationMode: Int,
    val adaptiveEnabled: Int,
    @param:StringRes val labelRes: Int
) {
    OFF(prefKey = "off", optimizationMode = 0, adaptiveEnabled = 0, labelRes = R.string.mode_off),
    ADAPTIVE(prefKey = "adaptive", optimizationMode = 0, adaptiveEnabled = 1, labelRes = R.string.mode_adaptive),
    LIMIT_80(prefKey = "limit_80", optimizationMode = 1, adaptiveEnabled = 0, labelRes = R.string.mode_limit_80);

    companion object {
        fun fromPrefKey(key: String): ChargeMode? = entries.find { it.prefKey == key }
    }
}

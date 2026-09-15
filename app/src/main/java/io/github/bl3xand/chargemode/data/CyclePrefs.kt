package io.github.bl3xand.chargemode.data

import android.content.Context

/** Persists which modes participate in the cycle and the last mode that was applied. */
class CyclePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabledModes: Set<ChargeMode>
        get() {
            val keys = prefs.getStringSet(KEY_ENABLED_MODES, DEFAULT_ENABLED_KEYS) ?: DEFAULT_ENABLED_KEYS
            val modes = keys.mapNotNull { ChargeMode.fromPrefKey(it) }.toSet()
            return modes.ifEmpty { DEFAULT_ENABLED_KEYS.mapNotNull(ChargeMode::fromPrefKey).toSet() }
        }
        set(value) {
            prefs.edit()
                .putStringSet(KEY_ENABLED_MODES, value.map { it.prefKey }.toSet())
                .apply()
        }

    var lastAppliedMode: ChargeMode?
        get() = prefs.getString(KEY_LAST_MODE, null)?.let(ChargeMode::fromPrefKey)
        set(value) {
            prefs.edit()
                .putString(KEY_LAST_MODE, value?.prefKey)
                .apply()
        }

    companion object {
        private const val PREFS_NAME = "charge_cycle_prefs"
        private const val KEY_ENABLED_MODES = "enabled_modes"
        private const val KEY_LAST_MODE = "last_mode"
        private val DEFAULT_ENABLED_KEYS = ChargeMode.entries.map { it.prefKey }.toSet()
    }
}

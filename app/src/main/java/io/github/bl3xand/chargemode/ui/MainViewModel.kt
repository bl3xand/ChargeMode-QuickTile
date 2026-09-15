package io.github.bl3xand.chargemode.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bl3xand.chargemode.R
import io.github.bl3xand.chargemode.data.ChargeMode
import io.github.bl3xand.chargemode.data.ChargeModeController
import io.github.bl3xand.chargemode.data.CyclePrefs
import io.github.bl3xand.chargemode.shizuku.ShizukuBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isDeviceSupported: Boolean,
    val hasWritePermission: Boolean,
    val shizukuGranted: Boolean,
    val currentMode: ChargeMode?,
    val selectedModes: Set<ChargeMode>,
    val grantCommand: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = CyclePrefs(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDeviceSupported = ChargeModeController.isSupportedDevice,
            hasWritePermission = ChargeModeController.hasPermission(application),
            shizukuGranted = ShizukuBridge.hasPermission(),
            currentMode = null,
            selectedModes = prefs.enabledModes,
            grantCommand = application.getString(R.string.grant_command, application.packageName)
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        if (ChargeModeController.isSupportedDevice) {
            refreshDeviceState()
            // Keeps the "current mode" display live even when it changes outside this screen
            // (the system Settings UI, another adb command, or the Quick Settings tile).
            viewModelScope.launch {
                ChargeModeController.observeChanges(application).collect { refreshDeviceState() }
            }
        }
    }

    fun refreshDeviceState() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val shizukuGranted = ShizukuBridge.hasPermission()
            var hasWritePermission = ChargeModeController.hasPermission(app)
            // No adb needed: Shizuku already has shell access, so it can grant this itself.
            if (shizukuGranted && !hasWritePermission) {
                hasWritePermission = ShizukuBridge.grantWriteSecureSettings(app)
            }
            val current = if (shizukuGranted) ShizukuBridge.readCurrentMode() else null
            _uiState.update {
                it.copy(
                    hasWritePermission = hasWritePermission,
                    shizukuGranted = shizukuGranted,
                    currentMode = current
                )
            }
        }
    }

    fun setModeSelected(mode: ChargeMode, selected: Boolean) {
        _uiState.update {
            it.copy(selectedModes = if (selected) it.selectedModes + mode else it.selectedModes - mode)
        }
    }

    /** Returns false (and leaves the saved selection untouched) if nothing is selected. */
    fun applySelection(): Boolean {
        val selected = _uiState.value.selectedModes
        if (selected.isEmpty()) return false
        prefs.enabledModes = selected
        return true
    }
}

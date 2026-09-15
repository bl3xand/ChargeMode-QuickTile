package io.github.bl3xand.chargecycle.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import io.github.bl3xand.chargecycle.R
import io.github.bl3xand.chargecycle.data.ChargeMode
import io.github.bl3xand.chargecycle.data.ChargeModeController
import io.github.bl3xand.chargecycle.databinding.ActivityMainBinding
import io.github.bl3xand.chargecycle.shizuku.ShizukuBridge
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modeSwitches: Map<ChargeMode, MaterialSwitch>

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_REQUEST_CODE) return@OnRequestPermissionResultListener
            viewModel.refreshDeviceState()
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Snackbar.make(
                binding.root,
                if (granted) R.string.shizuku_granted else R.string.shizuku_denied,
                Snackbar.LENGTH_SHORT
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modeSwitches = mapOf(
            ChargeMode.OFF to binding.switchOff,
            ChargeMode.ADAPTIVE to binding.switchAdaptive,
            ChargeMode.LIMIT_80 to binding.switchLimit80
        )
        for ((mode, switch) in modeSwitches) {
            switch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setModeSelected(mode, isChecked)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        binding.buttonCopyCommand.setOnClickListener { copyGrantCommand() }

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        if (!ChargeModeController.isSupportedDevice) {
            binding.supportedContent.isVisible = false
            Snackbar.make(binding.root, R.string.unsupported_title, Snackbar.LENGTH_INDEFINITE).show()
        } else if (!ShizukuBridge.hasPermission()) {
            requestShizukuPermission()
            Snackbar.make(binding.root, R.string.shizuku_access_required, Snackbar.LENGTH_INDEFINITE).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches a permission grant/revoke done externally (e.g. adb, or the Shizuku app) while backgrounded.
        viewModel.refreshDeviceState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    private fun render(state: MainUiState) {
        if (!state.isDeviceSupported) return

        binding.permissionCard.isVisible = !state.hasWritePermission
        binding.commandText.text = state.grantCommand
        binding.shizukuCard.isVisible = !state.shizukuGranted
        binding.textCurrentMode.text = state.currentMode?.let { getString(it.labelRes) }
            ?: getString(R.string.current_mode_unknown)

        for ((mode, switch) in modeSwitches) {
            val checked = mode in state.selectedModes
            if (switch.isChecked != checked) switch.isChecked = checked
            switch.isEnabled = state.shizukuGranted
        }

        if (state.shizukuGranted) {
            binding.buttonApply.text = getString(R.string.button_apply)
            binding.buttonApply.setOnClickListener { applySelection() }
        } else {
            binding.buttonApply.text = getString(R.string.button_grant_shizuku)
            binding.buttonApply.setOnClickListener { requestShizukuPermission() }
        }
    }

    private fun requestShizukuPermission() {
        if (ShizukuBridge.isAvailable()) {
            ShizukuBridge.requestPermission(SHIZUKU_REQUEST_CODE)
        } else {
            Snackbar.make(binding.root, R.string.shizuku_access_required, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applySelection() {
        val messageRes = if (viewModel.applySelection()) {
            R.string.selection_saved
        } else {
            R.string.error_select_at_least_one
        }
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
    }

    private fun copyGrantCommand() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.app_name), viewModel.uiState.value.grantCommand)
        )
        Snackbar.make(binding.root, R.string.command_copied, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }
}

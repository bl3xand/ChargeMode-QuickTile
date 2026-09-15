package io.github.bl3xand.chargecycle.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.bl3xand.chargecycle.data.ChargeMode
import io.github.bl3xand.chargecycle.data.ChargeModeController
import io.github.bl3xand.chargecycle.data.CyclePrefs
import io.github.bl3xand.chargecycle.shizuku.ShizukuBridge

/**
 * Invisible trampoline behind the launcher-icon long-press shortcuts (Android's equivalent of
 * iOS's 3D Touch quick actions): applies the mode named in the "mode" extra and closes
 * immediately, with no UI of its own.
 */
class ApplyModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_MODE)?.let(ChargeMode::fromPrefKey)
        val ready = mode != null &&
            ChargeModeController.isSupportedDevice &&
            ChargeModeController.hasPermission(this) &&
            ShizukuBridge.hasPermission()

        if (ready) {
            if (ChargeModeController.apply(this, mode!!)) {
                CyclePrefs(this).lastAppliedMode = mode
            }
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
    }
}

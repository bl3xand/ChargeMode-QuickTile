package io.github.bl3xand.chargecycle.shizuku;

// Runs inside the privileged (shell UID) process Shizuku spawns, where reading
// @hide Settings.Secure keys is allowed.
interface IChargeModeReaderService {
    // [optimizationMode, adaptiveEnabled], or [-1, -1] if the read failed.
    int[] readModeValues();
}

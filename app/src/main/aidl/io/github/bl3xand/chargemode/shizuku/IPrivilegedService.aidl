package io.github.bl3xand.chargemode.shizuku;

// Runs inside the privileged (shell UID) process Shizuku spawns, where reading @hide
// Settings.Secure keys and granting signature permissions is allowed.
interface IPrivilegedService {
    // [optimizationMode, adaptiveEnabled], or [-1, -1] if the read failed.
    int[] readModeValues();
    // Runs `pm grant <packageName> android.permission.WRITE_SECURE_SETTINGS`.
    boolean grantWriteSecureSettings(String packageName);
}

# PrivilegedUserService is instantiated reflectively by Shizuku, in a separate process, via the
# class name baked into ComponentName(...) in ShizukuBridge. R8 has no visibility into that call
# site, so without this it would strip or rename the class (or its no-arg constructor) as
# apparently unused - the app would silently lose "current mode" reads and the auto-grant of
# WRITE_SECURE_SETTINGS in a release build, with no crash to point at why.
-keep class io.github.bl3xand.chargemode.shizuku.PrivilegedUserService { *; }

# The AIDL-generated interface/Stub/Proxy this service is addressed through, both from this app's
# own process (ShizukuBridge) and across the Binder IPC boundary from the privileged process.
-keep class io.github.bl3xand.chargemode.shizuku.IPrivilegedService { *; }
-keep class io.github.bl3xand.chargemode.shizuku.IPrivilegedService$Stub { *; }
-keep class io.github.bl3xand.chargemode.shizuku.IPrivilegedService$Stub$Proxy { *; }

# MainViewModel is constructed reflectively by androidx.lifecycle's ViewModelProvider (matched by
# constructor signature), not a `new` call R8 can trace back to this class.
-keep class io.github.bl3xand.chargemode.ui.MainViewModel {
    <init>(android.app.Application);
}

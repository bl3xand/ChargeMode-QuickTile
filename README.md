# ChargeCycle

A Quick Settings tile that cycles through your phone's charging modes with a
single tap — no need to dig into Settings every time. Currently supports Pixel
phones (Off / Adaptive Charging / Limit to 80%); see [Device support](#device-support).

## How it works

Pixel's "Charging optimization" screen (Settings → Battery → Battery health →
Charging optimization) stores its state in two `Settings.Secure` keys:

| Mode | `charge_optimization_mode` | `adaptive_charging_enabled` |
|---|---|---|
| Off | 0 | 0 |
| Adaptive Charging | 0 | 1 |
| Limit to 80% | 1 | 0 |

Tapping the tile applies the next mode from the set you selected in the app,
in order, wrapping back to the first one. The tile subtitle and the app's
"Current mode" card always reflect whatever is actually active on the device
right now — including changes made from the system Settings app itself.

Writing these keys just needs `WRITE_SECURE_SETTINGS`. Reading them back does
not: as of Android 12, `@hide` settings keys like these can only be read by
`system_server` and system apps — not by a regular installed app, even one
holding `WRITE_SECURE_SETTINGS`. ChargeCycle uses **Shizuku** to run that read
in a shell-privileged process instead (see `ChargeModeReaderUserService`).

## Setup

1. Install [Shizuku](https://shizuku.rikka.app/) and start its service (via
   wireless debugging pairing, ADB, or root — see Shizuku's own instructions).
2. Build and install ChargeCycle.
3. Grant it the `WRITE_SECURE_SETTINGS` permission — this can't be requested
   through a normal runtime dialog, so it has to be granted once from a computer:

   ```bash
   adb shell pm grant io.github.bl3xand.chargecycle android.permission.WRITE_SECURE_SETTINGS
   ```

   The app shows this exact command (with a copy button) if the permission is missing.
4. Open the app. It will prompt for Shizuku access on first launch; grant it.
5. Pick which modes should participate in the cycle, tap **Apply**.
6. Add the **Charge Cycle** tile to your Quick Settings shade (edit shade →
   drag the tile in), same as any Wi-Fi/Airplane mode tile.

Without Shizuku access, the mode switches and the tile stay disabled — the app
can still *write* a mode with just `WRITE_SECURE_SETTINGS`, but can't reliably
know what's currently active without Shizuku, so cycling is blocked rather than
risk drifting out of sync.

## Device support

- **Pixel, Android 15+ (API 35)** — fully supported. That's when this charging
  optimization feature (and the settings keys it uses) shipped.
- **Other manufacturers** — not supported yet. These are Google-specific
  settings, not a general Android API; other OEMs use their own undocumented
  mechanisms. `ChargeModeController.isSupportedDevice` gates the whole app on
  `Build.MANUFACTURER` so it fails honestly instead of pretending to work.
  A different manufacturer needs its own provider behind the same interface.

## Architecture

MVVM, deliberately minimal otherwise — no DI framework, no Compose, no database:

- `ChargeMode` — the three Pixel modes and their Settings.Secure values.
- `ChargeModeController` — writes those settings (`WRITE_SECURE_SETTINGS`) and
  exposes a `Flow` of change notifications via `ContentObserver`.
- `ShizukuBridge` / `ChargeModeReaderUserService` — the privileged read path.
- `CyclePrefs` — `SharedPreferences` wrapper for the selected cycle.
- `MainViewModel` / `MainUiState` — single source of truth for the screen,
  kept live by the two mechanisms above.
- `ChargeCycleTileService` — the Quick Settings tile; same read/write paths.
- `MainActivity` — single screen to pick which modes cycle.

Dynamic color and light/dark theming come for free from
`Theme.Material3.DynamicColors.DayNight` — no theming code required.

## License

Not yet decided.

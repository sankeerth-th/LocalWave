# LocalWave Android

Android implementation for LocalWave core engine and Jetpack Compose product UI.

LocalWave is an offline, no-signup, Bluetooth-only private messaging app for nearby warehouse/team environments. It has no account, phone number, email, backend, cloud sync, Firebase, Supabase, analytics, tracking SDK, server relay, Wi-Fi transport, or mobile-data transport. The Frequency Code is a logical private Bluetooth channel, not analog RF tuning.

## Open And Build

Open `/Users/sanks04/Desktop/Random-1/android-localwave` in Android Studio.

If Android Studio shows “Code insight unavailable” or no run configuration, open the `android-localwave` folder directly rather than the multi-root parent. This workspace keeps `local.properties` ignored; on this machine it points Gradle at `/Users/sanks04/Library/Android/sdk`.

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
```

Install on a physical Android phone:

```bash
./gradlew :app:installDebug
adb shell am start -n com.localwave/.app.MainActivity
```

The app entry point is `com.localwave.app.MainActivity`. The Android-native Compose product shell is built on top of the LocalWave engine contract.

## Compose Product UI

The app includes:

- onboarding: welcome, display name, Frequency Code, Bluetooth education, notification education
- People/Frequency screen: current Frequency Code, permission banners, active-mode status, peer list, empty state, Wake actions
- Chat screen: peer status, signal, Wake, message composer, pending/sent/delivered/failed labels, 4 KB limit, encrypted file send/export/import actions
- Settings: profile, Frequency Code, privacy explanation, identity fingerprint, permissions, redacted diagnostics

The UI depends on `LocalWaveEngine`, not concrete BLE, crypto, packet framing, Room DAO, or Android Keystore classes. `MockLocalWaveEngine` supports previews, unit tests, and emulator UI checks. Production builds use `RealLocalWaveEngine`; mock mode is exposed only in Diagnostics so sample peers/messages are not mistaken for physical-device BLE evidence.

Wake and background language is intentionally best-effort: Android notification permission, DND/Focus equivalents, Bluetooth state, background limits, and battery policy can affect reachability.

## Secure Object Transfer

Android implements the shared LW-SSOT v1 object format used by iOS:

- encrypted object manifests and encrypted pieces;
- 32 KiB default data pieces;
- XOR parity recovery, one recovery piece per 10-piece stripe;
- app-private encrypted object cache under internal storage;
- L2CAP piece batches for in-app bulk transfer when both peers expose PSM support;
- `.localwavepkg` v2 export/import through the Android Sharesheet and document picker.

GATT remains the control plane for manifest announcements, receipts, wake, text fallback, and small control frames. Large native shares are user-mediated and are not treated as app-controlled delivery.

## iPhone Interop Testing

Use the iOS reference at `/Users/sanks04/Desktop/Random-1/ios-localwave`.

1. Install iOS LocalWave on a physical iPhone.
2. Install Android LocalWave on a physical Android phone.
3. Enter the same Frequency Code, for example `DOCK-A-17`.
4. Allow Bluetooth on both devices.
5. Allow notifications if Wake alerts are being tested.
6. Verify discovery, Android to iPhone message, iPhone to Android message, Android to iPhone Wake, and iPhone to Android Wake.

Physical devices are required for real BLE validation. Emulator/simulator tests cannot prove CoreBluetooth and Android BLE interop.

# LocalWave Android Testing

## Automated

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
```

Covered unit behavior:

- Frequency Code normalization and rejection.
- iOS UUID derivation vectors.
- BLE packet framing, chunking, reassembly, CRC, and oversized rejection.
- X25519/HKDF/AES-GCM encrypt/decrypt, wrong-key failure, replay rejection.
- Room peer/message repositories.
- Redacted logger.
- Mock engine state/message behavior.
- Display-name validation and mock message pending behavior.

## Compose UI Tests

Compile the instrumentation test APK:

```bash
./gradlew assembleDebugAndroidTest
```

Run on an attached emulator/device:

```bash
./gradlew connectedAndroidTest
```

Current Compose smoke coverage verifies that `com.localwave.app.MainActivity` launches into the LocalWave shell.

## Emulator Smoke

```bash
adb devices
./gradlew :app:installDebug
adb -s <serial> shell cmd package resolve-activity --brief com.localwave
adb -s <serial> shell am start -n com.localwave/.app.MainActivity
adb -s <serial> exec-out screencap -p > /tmp/localwave-android.png
```

Manual UI path:

1. Complete onboarding with a non-empty display name and valid Frequency Code.
2. Confirm blank display names and short Frequency Codes are blocked.
3. Confirm Bluetooth and notification education copy does not imply guaranteed delivery.
4. Open People, Chat, Settings, Privacy, Identity, Permissions, and Diagnostics.
5. Confirm diagnostics omit plaintext messages, private keys, shared secrets, raw decrypted envelopes, and private channel passwords.

## Physical Android To Android

1. Install on Android A and Android B.
2. Use the same Frequency Code.
3. Allow Bluetooth permissions.
4. Verify discovery, encrypted message, Wake, channel switching, Bluetooth-disabled state, notification-denied fallback, and identity persistence.

## Android To iPhone

1. Install this Android app and `/Users/sanks04/Desktop/Random-1/ios-localwave`.
2. Use the same Frequency Code.
3. Verify Android/iPhone discovery, messages both directions, Wake both directions, and different-code invisibility.

## Troubleshooting

- Real BLE requires physical devices.
- Android 12+ requires Bluetooth scan/advertise/connect runtime permissions.
- Android 13+ requires notification permission for Wake notifications.
- OEM battery policy may throttle background BLE work.

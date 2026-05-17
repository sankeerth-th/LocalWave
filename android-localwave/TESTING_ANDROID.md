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
- LW-SSOT object package JSON round trip.
- Encrypted object package decrypt after JSON round trip.
- XOR parity recovery for one missing piece in a stripe.
- Relay cache stores opaque encrypted chunks only.
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

1. Install this Android app and the repository's iOS app from `ios-localwave`.
2. Use the same Frequency Code.
3. Verify Android/iPhone discovery, messages both directions, Wake both directions, and different-code invisibility.

## File Transfer And Native Share

1. Use the same Frequency Code on Android and iPhone.
2. From Android Chat, select Share Package and choose a small image or document.
3. Send the `.localwavepkg` through Android Sharesheet to the iPhone.
4. On iPhone, import the package.
5. Repeat iPhone to Android.

Expected: the recipient decrypts locally only after object manifest, encrypted piece hashes, Merkle root, expiry, and final payload hash verify. The sender remains exported/pending unless it receives an in-app receipt.

## L2CAP Object Transfer

1. Keep both apps foregrounded on physical devices.
2. Send 100 KB, 1 MB, 10 MB, and 25 MB files using Send File.
3. Walk one device out of range mid-transfer, then reconnect.

Expected: GATT carries manifest/control traffic, L2CAP carries piece batches, verified pieces are cached, and the app resumes missing pieces rather than claiming fake delivery.

## Troubleshooting

- Real BLE requires physical devices.
- Android/iOS L2CAP interoperability cannot be validated on an emulator alone.
- Android 12+ requires Bluetooth scan/advertise/connect runtime permissions.
- Android 13+ requires notification permission for Wake notifications.
- OEM battery policy may throttle background BLE work.

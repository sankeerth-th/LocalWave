# LocalWave Android Thread 3 Plan

## 1. iOS Reference Findings

- LocalWave is offline, no-signup, no-backend, Bluetooth-only nearby messaging. The Frequency Code is a logical private Bluetooth channel, not radio tuning.
- The iOS UI depends on `LocalWaveEngineProtocol`; Android must expose only Kotlin model/interface contracts to Thread 4 and hide BLE implementation classes.
- `ChannelCode` trims, collapses whitespace, uppercases with locale-independent behavior, preserves dots/hyphens, and rejects values shorter than 3 characters.
- Channel derivation uses `LocalWave.Channel.v1.<label>.<normalized>` as SHA-256 input. UUIDs take the first 16 digest bytes with UUID version 5 and RFC 4122 variant bits applied.
- iOS GATT profile derives service, packet, presence, and wake characteristic UUIDs. There is no separate receipt characteristic; receipt exists only as packet kind `4`.
- Packet framing is byte-compatible and fixed: version byte, packet UUID, conversation UUID, packet kind, big-endian chunk index/count, big-endian body length/checksum, then body.
- iOS crypto uses Curve25519 key agreement, HKDF-SHA256, AES-GCM, replay counters, and AAD that includes envelope fields plus the normalized channel.
- iOS stores private identity material in Keychain. Android cannot keep iOS-compatible X25519 raw keys directly in Android Keystore on API 26, so Android wraps raw identity material with an Android Keystore AES key.
- iOS currently includes presence in advertisement service data and also exposes presence over GATT. Android will interoperate with this but documents the privacy/reliability limitation.

## 2. Exact Protocol Decisions To Match iOS

- Namespace: `LocalWave.Channel.v1`.
- Channel labels: `channel-id`, `gatt-service`, `gatt-packet`, `gatt-presence`, `gatt-wake`, `discovery-tag`, `hkdf-salt`.
- Session info: `LocalWave.Session.v1|<peer ids sorted lexicographically>`.
- AAD prefix: `LocalWave.AAD.v1`.
- AAD field order: version, kind, sender ID, recipient ID, message ID or empty string, timestamp milliseconds, replay counter, normalized channel.
- String AAD fields are encoded as big-endian `UInt32` byte length followed by UTF-8 bytes.
- Envelope JSON uses ISO-8601 timestamps and Base64 binary fields for Kotlin serialization compatibility with Swift `JSONEncoder`.
- Transport packet kinds: presence `1`, message `2`, wake `3`, receipt `4`.
- Packet constants: version `1`, header `46` bytes, default frame `182` bytes, max payload `16384` bytes.
- Message delivery is marked `sent` after local BLE write succeeds and `delivered` only after a future receipt.

## 3. Android-Specific Differences

- Android project uses Kotlin, Gradle Kotlin DSL, AndroidX, Room, coroutines, and a diagnostic-only `MainActivity`.
- X25519 and Ed25519-compatible key material uses Bouncy Castle `bcprov-jdk18on`; Android platform crypto handles AES-GCM and HMAC/HKDF.
- Raw private identity material is wrapped with an Android Keystore AES-GCM key and stored in app-private preferences, not Room.
- Android active mode is a foreground service started only by explicit user action. It cannot bypass OS background, DND, Bluetooth, or OEM battery policy.
- Runtime permission reporting is more granular than iOS: scan, advertise, connect, notification, Bluetooth disabled, BLE unsupported, advertiser unsupported, foreground service unavailable, and older-device location need.

## 4. Permission Plan By SDK Version

- API 31+: request/check `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, and `BLUETOOTH_CONNECT`.
- API 31+: declare `BLUETOOTH_SCAN` with `usesPermissionFlags="neverForLocation"` because LocalWave does not derive physical location from BLE scans; document that Android may filter some beacons.
- API 33+: request/check `POST_NOTIFICATIONS` for Wake and active service notifications.
- API 34+: declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `android:foregroundServiceType="connectedDevice"` for active mode.
- API 30 and lower: declare `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`, and `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"` because BLE scan results required location permission on those versions.
- Never declare `INTERNET`.

## 5. BLE Architecture

- `AndroidBleTransport` coordinates scanning, advertising, GATT server, GATT client, connection scheduling, packet framing, and event emission.
- Each device acts as advertiser/GATT server and scanner/GATT client.
- `BleAdvertiserServer` advertises the deterministic service UUID and hosts presence, packet, and wake characteristics.
- `BleScannerClient` scans for the deterministic service UUID and delegates discovered devices to `GattClientManager`.
- `GattClientManager` connects, discovers characteristics, reads presence, negotiates MTU, and writes packet/wake frames.
- `GattServerManager` responds to presence reads and packet/wake writes.
- `ConnectionScheduler` rate-limits repeated connection attempts with exponential backoff.
- Outgoing writes are queued; failed writes leave messages honest as pending or failed.

## 6. Crypto Compatibility Plan

- Generate local identity on first launch: X25519 agreement key plus Ed25519 signing key.
- Public identity fingerprint is SHA-256 hex of `LocalWave.Identity.v1 || agreementPublicKey || signingPublicKey`.
- Derive pairwise session keys with X25519, HKDF-SHA256, channel salt, and sorted peer IDs.
- Encrypt message and wake plaintext with AES-GCM, 12-byte nonce, and iOS-compatible AAD.
- Reject wrong sender/recipient, wrong peer key, malformed nonce/tag, duplicate replay counter, and duplicate message ID.
- Do not claim Android/iOS message compatibility until fixed iOS vectors are verified. Current implementation includes deterministic test vectors and documents remaining physical-device validation.

## 7. Persistence Plan

- Room stores peers, messages, replay counters, known peer trust summaries, and redacted diagnostic events.
- Private keys, shared secrets, raw Frequency Codes, and private channel passwords are not stored in Room.
- Message text is stored plaintext locally for v1 to match iOS local-history behavior; this is documented in `SECURITY_ANDROID.md`.
- Repositories expose only the Thread 4 contract and Kotlin `Flow` streams.

## 8. Test-First Plan

- Write failing tests first for normalization, UUID derivation, packet framing, crypto, replay protection, Room repositories, mock engine, and redacted logging.
- Run `./gradlew test` before production code to verify tests fail because implementation is missing.
- Implement the smallest production core needed for tests and build.
- Add Android manifest/service foundations and diagnostic `MainActivity`.
- Rerun unit tests, debug build, lint, forbidden dependency/permission searches, and emulator smoke if an adb target is available.

## 9. Risks And Blockers

- Android/iPhone BLE compatibility cannot be fully verified without physical Android and iPhone devices.
- iOS advertises presence in service data; this helps discovery but leaks display identity to anyone who knows the service UUID and is documented as an iOS v1 limitation.
- Android BLE background behavior varies across OEMs and battery policies.
- Bouncy Castle is required for API 26 X25519/Ed25519 compatibility. If dependency policy changes, crypto compatibility must be revisited.
- Receipt handling is not active in iOS v1; delivered status remains conservative.

## 10. Verification Commands

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
rg -n "Firebase|Supabase|analytics|tracking|INTERNET|http://|https://|plaintext log|private key|shared secret" .
adb devices
./gradlew :app:installDebug
adb -s <serial> shell cmd package resolve-activity --brief com.localwave
adb -s <serial> shell am start -n com.localwave/.MainActivity
```

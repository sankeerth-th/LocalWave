# LocalWave Android Architecture

## Layers

- `core/model`: Kotlin value types shared with Thread 4.
- `core/protocol`: Thread 4 integration interfaces, channel derivation, protocol constants, JSON envelope codec, and `RealLocalWaveEngine`.
- `core/crypto`: identity generation/storage, X25519 session crypto, HKDF-SHA256, AES-GCM envelopes, replay validation.
- `core/bluetooth`: Android BLE scan/advertise/GATT roles, packet framing, permissions, active foreground service foundation.
- `core/persistence`: Room entities, DAOs, and repositories for peers/messages/replay/trust/diagnostics.
- `core/notifications`: Wake and active-mode notification channel/notification helpers.
- `mock`: `MockLocalWaveEngine` for Thread 4 Compose development.
- `app`: Thread 4 application entry, environment selection, DataStore-backed app state, and Navigation Compose shell.
- `design`: Material 3 LocalWave theme and reusable Compose components.
- `feature/onboarding`, `feature/people`, `feature/chat`, `feature/settings`: Thread 4 product screens and StateFlow view models.

## Engine Contract

Thread 4 should depend on `LocalWaveEngine`, `CryptoService`, `MessageRepository`, `PeerRepository`, and model classes only. BLE implementation details stay inside `core/bluetooth`.

## Thread 4 Navigation And State

Navigation routes:

- `onboarding/welcome`
- `people`
- `chat/{peerId}`
- `settings`
- `settings/privacy`
- `settings/identity`
- `settings/diagnostics`
- `settings/permissions`

People and Settings are bottom destinations. Chat and settings detail screens are pushed destinations. Onboarding state is stored with DataStore Preferences: onboarding complete, display name, last Frequency Code, and education flags. Crypto secrets, private keys, shared secrets, and private channel passwords are not stored in preferences.

Feature view models expose `StateFlow` UI state and call only `LocalWaveEngine` methods for peers, messages, transport state, Wake, channel switching, and identity.

## BLE Roles

Each Android device acts as both scanner/GATT client and advertiser/GATT server. The deterministic GATT service UUID comes from the normalized Frequency Code. Presence is exposed via GATT read and Android advertising keeps payloads small.

## Crypto

Android uses Bouncy Castle X25519/Ed25519-compatible key material, Android AES-GCM, HKDF-SHA256, and iOS-compatible AAD construction. Raw private identity material is wrapped by an Android Keystore AES key and stored in app-private preferences; it is not stored in Room.

## Persistence

Room stores peers, plaintext local message history for v1, delivery state, replay counters, trust summaries, and diagnostic events. Private keys and shared session secrets are not persisted in Room.

## Notifications

Wake notifications are shown only after valid decryption and replay validation. If Android 13+ notification permission is denied, the engine exposes an in-app wake fallback through transport state.

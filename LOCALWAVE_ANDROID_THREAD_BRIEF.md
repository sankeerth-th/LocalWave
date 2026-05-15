# LocalWave Android Thread Brief

This workspace now separates the completed iOS implementation under:

`/Users/sanks04/Desktop/Random-1/ios-localwave`

Use that iOS project as the product/architecture reference, not as source to copy blindly. The Android implementation should build the same product truth with Android-native APIs, Android-native UI, and Android-specific permission/background behavior.

## Product Truth

LocalWave is an offline, no-signup, Bluetooth-only private messaging app for warehouse/team environments.

Hard product claims:
- No signup.
- No phone number, email, account, cloud, server, analytics, tracking SDK, Firebase, Supabase, or backend relay.
- Bluetooth is the production transport.
- The user-facing "Frequency Code" is a logical private local Bluetooth channel, not an analog RF tuner.
- Android/iOS/Bluetooth manage the physical radio layer; LocalWave manages discovery, logical channel derivation, encrypted envelopes, persistence, and user state.
- Wake is best-effort and must respect OS notification permission, Focus/DND, background limits, Bluetooth state, and device power policy.
- LocalWave is not an emergency/public-safety communication system.

## Reference iOS Files

Product docs:
- `ios-localwave/README.md`
- `ios-localwave/ARCHITECTURE.md`
- `ios-localwave/SECURITY.md`
- `ios-localwave/TESTING.md`

Core reference:
- `ios-localwave/LocalWave/Core/Models/LocalWaveProtocols.swift`
- `ios-localwave/LocalWave/Core/Crypto/ChannelKeyDerivation.swift`
- `ios-localwave/LocalWave/Core/Crypto/IdentityStore.swift`
- `ios-localwave/LocalWave/Core/Crypto/SessionCrypto.swift`
- `ios-localwave/LocalWave/Core/Bluetooth/LocalWaveBluetoothTransport.swift`
- `ios-localwave/LocalWave/Core/Bluetooth/BLEPacketFramer.swift`

UI reference:
- `ios-localwave/LocalWave/App/AppRootView.swift`
- `ios-localwave/LocalWave/Features/Onboarding/OnboardingView.swift`
- `ios-localwave/LocalWave/Features/People/PeopleView.swift`
- `ios-localwave/LocalWave/Features/Chat/ChatView.swift`
- `ios-localwave/LocalWave/Features/Settings/SettingsView.swift`

## Skills To Use In New Android Threads

Ask each new thread to explicitly load relevant skills before coding.

Core/process skills:
- `superpowers:using-superpowers` - session discipline and skill routing.
- `superpowers:writing-plans` - create or follow an implementation plan before large changes.
- `superpowers:test-driven-development` - write behavior tests before production code where practical.
- `superpowers:systematic-debugging` - use when build/tests/device behavior fail.
- `superpowers:verification-before-completion` - require fresh build/test evidence before claiming done.
- `superpowers:subagent-driven-development` - use if a thread delegates subtasks.

Security skills:
- `codex-security:threat-model` - create/update Android repository threat model.
- `codex-security:security-scan` - final security pass over code and config.
- `codex-security:validation` - validate any suspected crypto/privacy issue.
- `codex-security:fix-finding` - fix validated security findings.

Android/device validation skills available in this session:
- `test-android-apps:android-emulator-qa` - emulator launch, UI checks, logs, screenshots.
- `test-android-apps:android-performance` - Android performance evidence when needed.

UI/product skills:
- `frontend-skill` - premium app UI, restraint, responsive layouts, empty states, accessibility, motion.
- `design-an-interface` - optional if exploring radically different Android UI directions.

General repo skills that may help:
- `security-best-practices`
- `security-threat-model`
- `tdd`
- `playwright` only if a web preview/tool is introduced; not needed for native Android.

No dedicated Android build skill is visible in this session. Use normal Android/Gradle tooling directly and the `test-android-apps` skills for emulator/device validation.

## Recommended Android Thread Split

Use two coordinated Android threads.

### Android Thread 1 - Core BLE, Crypto, Persistence

Goal: Build the offline engine.

Owned areas:
- `android-localwave/app/src/main/java/.../core/model`
- `android-localwave/app/src/main/java/.../core/crypto`
- `android-localwave/app/src/main/java/.../core/bluetooth`
- `android-localwave/app/src/main/java/.../core/persistence`
- `android-localwave/app/src/main/java/.../core/notifications`
- unit tests for crypto, channel derivation, packet framing, repositories, replay protection

Responsibilities:
- Domain models.
- Key generation and Android Keystore storage.
- Deterministic BLE service UUID/channel derivation.
- Secure envelopes using Android-native crypto APIs or carefully justified Jetpack Security/Tink if selected.
- Pairwise ECDH session establishment.
- Replay protection per peer/channel.
- BLE advertiser and scanner.
- GATT server and GATT client.
- Packet framing/chunking/reassembly.
- Message and peer repositories.
- Wake notification service.
- Mock transport for emulator/tests.
- Redacted diagnostics only.

Acceptance criteria:
- Same Frequency Code derives same UUID.
- Different Frequency Codes cannot discover each other.
- Two physical Android phones discover each other in foreground.
- Two physical Android phones exchange encrypted text.
- Wake produces notification or in-app fallback based on notification permission.
- Unit tests pass.

### Android Thread 2 - Jetpack Compose Product UI, ViewModels, Integration

Goal: Build the polished Android app experience.

Owned areas:
- `android-localwave/app/src/main/java/.../app`
- `android-localwave/app/src/main/java/.../design`
- `android-localwave/app/src/main/java/.../feature/onboarding`
- `android-localwave/app/src/main/java/.../feature/people`
- `android-localwave/app/src/main/java/.../feature/chat`
- `android-localwave/app/src/main/java/.../feature/settings`
- view-model tests and Compose UI tests

Responsibilities:
- Android-native app shell.
- Onboarding: welcome, display name, Frequency Code, Bluetooth permission explanation, notification permission explanation.
- Frequency/People screen.
- Chat screen.
- Settings, privacy explanation, diagnostics.
- Material 3 UI with dark-mode-first warehouse/night readability.
- Accessibility, font scaling, TalkBack labels, reduced-motion respect.
- ViewModels wired to protocol interfaces, not concrete BLE implementations.
- Mock engine for previews/emulator.

Acceptance criteria:
- App opens with no signup.
- Onboarding persists display name/channel.
- People list updates from engine stream.
- Chat sends pending/sent/delivered/failed states.
- Wake UI has correct unavailable/sending/sent/failed states.
- Settings exposes fingerprint, channel privacy, permissions, diagnostics.
- Compose UI does not block main thread.

## Android Integration Contract

Mirror the iOS protocol in Kotlin. Keep UI dependent only on interfaces.

```kotlin
interface LocalWaveEngine {
    suspend fun start(channel: ChannelCode, displayName: String)
    suspend fun stop()
    suspend fun updateDisplayName(displayName: String)
    suspend fun switchChannel(channel: ChannelCode)
    suspend fun sendMessage(text: String, to: PeerId): MessageId
    suspend fun sendWake(to: PeerId)
    fun observePeers(): Flow<List<PeerProfile>>
    fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>>
    fun observeTransportState(): Flow<TransportState>
    suspend fun localIdentity(): LocalIdentity
}

interface CryptoService {
    suspend fun encryptMessage(
        text: String,
        peer: PeerProfile,
        counter: Long,
        channel: ChannelCode
    ): MessageEnvelope

    suspend fun decryptMessage(
        envelope: MessageEnvelope,
        peer: PeerProfile,
        channel: ChannelCode
    ): String
}

interface MessageRepository {
    suspend fun save(message: ChatMessage)
    suspend fun updateStatus(messageId: MessageId, status: MessageStatus)
    fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>>
    suspend fun deleteAll(peerId: PeerId)
}

interface PeerRepository {
    suspend fun upsert(peer: PeerProfile)
    fun observePeers(): Flow<List<PeerProfile>>
    suspend fun peer(id: PeerId): PeerProfile?
    suspend fun clearRecentlySeen()
}
```

## Android Package Structure

Suggested root:

`/Users/sanks04/Desktop/Random-1/android-localwave`

Suggested packages:

```text
app/src/main/java/com/localwave/
  app/
    LocalWaveApplication.kt
    MainActivity.kt
    AppEnvironment.kt
    LocalWaveApp.kt
  design/
    LWTheme.kt
    LWColors.kt
    LWTypography.kt
    components/
      GlassCard.kt
      FrequencyPill.kt
      PeerAvatar.kt
      SignalStrengthView.kt
      WakeButton.kt
      MessageBubble.kt
      PermissionBanner.kt
      EmptyStateView.kt
  feature/
    onboarding/
    people/
    chat/
    settings/
  core/
    model/
    crypto/
    bluetooth/
    persistence/
    notifications/
    diagnostics/
```

## Android Platform Requirements

Minimum target recommendation:
- `minSdk` 26 or higher unless the team has a specific lower requirement.
- `targetSdk` current installed Android SDK.

Permissions:
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_CONNECT`
- `POST_NOTIFICATIONS` on Android 13+
- Location permission only if required by selected min/target behavior. Do not request it unless the app truly needs it for BLE scan behavior on the chosen Android versions.

Services:
- Use a foreground service only if needed and accurately represented to the user.
- If used, set the correct service type such as connected device where applicable.

Background limitations:
- Android may throttle BLE scanning/advertising.
- OEM battery optimizations can affect delivery.
- Wake cannot bypass notification permission, DND, background limits, or Bluetooth off state.

## BLE Architecture

Production transport should use Android BLE directly:
- `BluetoothLeAdvertiser` for advertising.
- `BluetoothLeScanner` for scanning.
- `BluetoothGattServer` for peripheral/GATT server role.
- `BluetoothGatt` client connections for central role.

GATT profile:
- Deterministic service UUID from normalized Frequency Code.
- Presence characteristic: peer intro payload after connection.
- Inbox/packet characteristic: encrypted packet writes.
- Wake characteristic: encrypted wake writes.
- Optional receipt characteristic or packet kind for delivery ACK.

Rules:
- Never depend on device Bluetooth name.
- Keep advertisements small.
- Put identity/presence in GATT after connection.
- Queue outgoing packets and retry when peers are reachable.
- Show pending/sent/delivered/failed honestly.
- Limit v1 text messages to 4 KB.

## Crypto Architecture

Required behavior:
- Generate local identity on first launch.
- Store private key material in Android Keystore when possible.
- Show public identity fingerprint for trust verification.
- Derive channel service UUID and crypto context from normalized Frequency Code.
- Support optional private channel password/invite phrase.
- Use pairwise ECDH shared secret.
- Derive session keys with HKDF-SHA256 using local/remote public keys, channel code, salt, and transcript/context.
- Encrypt envelopes with AES-GCM or ChaCha20-Poly1305 if available and justified.
- Include version, sender ID, recipient ID, timestamp, nonce, ciphertext, tag, message ID, replay counter.
- Reject duplicate replay counters per peer/channel.

Do not log:
- Plaintext message bodies.
- Private keys.
- Shared secrets.
- Full Frequency Code.
- Private channel password.
- Full ciphertext payloads unless needed for explicit redacted debug.

## Persistence Architecture

Use a local database:
- Room is appropriate for messages, peers, and trust records.
- Store message history locally.
- Keep private keys out of Room.
- Prefer encrypted-at-rest storage if practical, but do not invent fake security claims.

Tables/entities:
- `LocalIdentityMetadata`
- `PeerEntity`
- `MessageEntity`
- `ReplayCounterEntity`
- `KnownPeerTrustEntity`
- `DiagnosticEventEntity` with redacted fields only

## UI Architecture

Use Jetpack Compose and Material 3.

Screens:
- Welcome: "Private local chat. No internet. No account."
- Display name setup.
- Frequency Code join.
- Bluetooth permission explanation.
- Notification permission explanation.
- Frequency/People tab.
- Chat tab/detail.
- Settings tab.
- Privacy explanation.
- Diagnostics.

Design goals:
- Android-native, not an iOS clone.
- Dark-mode-first, premium, quiet, operational.
- Dense but readable warehouse/team workflow.
- Clear peer status and signal indicator.
- Clear Wake limitations.
- Large touch targets.
- TalkBack labels.
- Font scaling support.
- No decorative clutter.

## Android Tests To Require

Unit tests:
- same channel -> same UUID
- different channel -> different UUID
- normalization trims/uppercases/collapses whitespace
- encrypt/decrypt succeeds for intended peer
- decrypt fails with wrong key
- replay counter rejects duplicate
- packet chunk/reassemble works
- oversized message rejected
- repositories store/load peers/messages

ViewModel tests:
- onboarding state transitions
- peer list updates from Flow
- send message pending -> sent
- wake unavailable when peer unreachable or permission denied
- switch channel clears peers

Instrumented/manual tests:
- Device A and B same channel discover each other.
- Device C different channel is invisible.
- A sends B encrypted message.
- B sends A wake.
- Disable notification permission and verify in-app fallback.
- Switch channel and verify peer list clears.
- Kill/reopen app and verify identity persists.

## Prompt Template For Android Threads

Use this as the opening prompt for each Android thread, then add the thread-specific ownership section:

```text
You are Codex acting as a senior Android architect, Kotlin engineer, security reviewer, and Android-native product designer. Build the Android version of LocalWave in /Users/sanks04/Desktop/Random-1/android-localwave. First read /Users/sanks04/Desktop/Random-1/LOCALWAVE_ANDROID_THREAD_BRIEF.md and the iOS reference docs under /Users/sanks04/Desktop/Random-1/ios-localwave. Use the listed skills before coding. Preserve the product truth: no signup, no backend, no internet runtime, Bluetooth-only production transport, Frequency Code is a logical BLE channel not analog RF, encrypted envelopes, local persistence, honest permission/background states. Do not edit ios-localwave except for reading reference files. Coordinate with the other Android thread through the Kotlin interfaces in this brief.
```

Thread 1 add:

```text
Own Android core only: model, crypto, BLE, persistence, notifications, diagnostics, and core tests. Expose LocalWaveEngine/CryptoService/repository interfaces for UI. Do not edit Compose UI files except interface-driven compile fixes.
```

Thread 2 add:

```text
Own Android UI only: app shell, Compose design system, onboarding, people, chat, settings, diagnostics, view models, preview/mock engine, UI/view-model tests. Depend only on interfaces from Thread 1.
```

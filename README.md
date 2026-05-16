# LocalWave

**LocalWave is a private, offline, no-signup nearby communication system for iPhone and Android.**

LocalWave is built for teams that need to message, wake, and eventually exchange files with people physically nearby without accounts, phone numbers, email, cloud sync, Firebase, Supabase, analytics SDKs, server relay, or internet messaging paths.

It is not a social network, a cloud chat app, or a fake walkie-talkie. LocalWave is a local-first communication layer for short-range environments such as warehouses, retail teams, field teams, events, private work groups, and places where internet access is unreliable or intentionally avoided.

The long-term goal is simple:

> Nearby devices should be able to find each other, verify trust, send messages, wake teammates, and exchange files securely using only local device-to-device transport.

## Product Promise

- No internet messaging path
- No signup
- No phone number or email
- No backend account system
- No analytics or tracking SDKs
- Nearby only
- Encrypted before transport
- Honest platform limits

The **Frequency Code** is not a real radio frequency. It is a human-friendly private channel label that LocalWave uses to derive discovery and encryption context.

Example Frequency Codes:

```text
DOCK-A-17
NIGHT SHIFT
462.625
RECEIVING TEAM
```

For stronger privacy, a Frequency Code should be paired with an invite phrase or private channel password. A guessable code is a discovery namespace, not proof of trust.

## What LocalWave Is

LocalWave is:

- A private local messaging app
- A Bluetooth-first nearby communication system
- A cross-platform iOS and Android protocol
- A best-effort Wake system for nearby teammates
- A secure local file-transfer architecture
- A local encrypted object cache for resumable sharing
- A research-driven architecture for offline device-to-device communication

LocalWave is not:

- A public safety or emergency communication system
- A guaranteed always-on pager
- A real radio-frequency tuner
- A military radio replacement
- A cloud messenger
- A way to bypass iOS or Android background rules
- A generic Bluetooth replacement stack
- A promise of unlimited high-speed video transfer over Bluetooth

## Product Reality

Normal iOS and Android apps cannot rewrite the Bluetooth radio stack, force a phone onto an analog frequency, override OS Bluetooth scheduling, or turn every phone into an always-on relay node.

LocalWave builds its own secure protocol above the public platform APIs that phones actually allow:

```text
LocalWave Protocol
  above
iOS CoreBluetooth
Android BLE APIs
LE L2CAP Credit-Based Channels where available
Native encrypted share/export paths where useful
Dedicated relay nodes where serious range is needed
```

The correct product model is:

```text
Frequency Code = logical private local channel
Frequency Code != real RF tuning
```

## Repository Structure

This repository contains separate native implementations for iOS and Android:

```text
ios-localwave/
  LocalWave/
    App/
    DesignSystem/
    Features/
    Core/
      AppState/
      Bluetooth/
      Crypto/
      Models/
      Notifications/
      Persistence/
    Mocks/
  LocalWaveTests/

android-localwave/
  app/src/main/java/com/localwave/
    app/
    core/
      bluetooth/
      crypto/
      diagnostics/
      model/
      notifications/
      persistence/
      protocol/
    design/
    feature/
    mock/
```

Both platforms follow the same core rule:

> The UI never talks directly to Bluetooth. The UI talks to `LocalWaveEngine`. The engine hides transport, crypto, persistence, notifications, and diagnostics.

Mocks support previews, tests, and emulator UI work. Production app environments use real transport engines.

## Current App Flows

LocalWave currently targets these primary flows:

### Onboarding

Users enter a display name and Frequency Code, then review Bluetooth and notification permission education. The app explains that the Frequency Code is a private local Bluetooth channel, not radio tuning.

### People

Users see the current Frequency Code, Bluetooth state, permission banners, nearby peers, signal, trust/fingerprint information, and Wake actions.

### Chat

Users can send text messages, see pending/sent/delivered/failed states, retry failed sends, and send Wake pings.

### Settings

Users can inspect or update display name, Frequency Code, identity fingerprint, permissions, privacy explanation, and redacted diagnostics.

## Architecture

LocalWave is split into four major layers:

```text
Product UI
SwiftUI / Jetpack Compose
Onboarding, People, Chat, Settings
        |
App State
View models, dependency injection, UI state
        |
Core Engine
Discovery, messaging, Wake, files, crypto
        |
Domain Models and Protocols
Shared contracts and portable transfer model
```

Both platforms should expose the same conceptual engine surface:

```text
start(channel, displayName)
stop()
updateDisplayName()
switchChannel()
sendMessage()
sendWake()
sendAttachment()
exportEncryptedSharePackage()
importEncryptedSharePackage()
verifyPeer()
observePeers()
observeMessages()
observeTransfers()
observeTransportState()
localIdentity()
```

The engine owns peer discovery, Frequency Code state, Bluetooth state, secure session state, message delivery, Wake delivery, attachment transfer, encrypted package import/export, transfer records, peer trust, local identity, and redacted diagnostics.

## Transport Strategy

LocalWave uses a split transport model:

```text
Discovery Plane
  BLE advertisements
  minimal GATT discovery
  rotating discovery tokens

Control Plane
  GATT
  peer intro
  trust handshake
  capability exchange
  Wake
  receipts
  manifests
  transfer negotiation

Bulk Plane
  LE L2CAP Credit-Based Channels when available
  GATT fallback for small data
  native encrypted package export when useful
  future relay/object-cache transfer
```

GATT is best for small, deterministic control messages. LocalWave should not push large files through GATT as the default path.

The preferred bulk path is LE L2CAP Credit-Based Channels. When L2CAP is unavailable or unreliable, LocalWave can fall back to small encrypted GATT chunks, encrypted native share packages, store-and-forward object cache, or dedicated relay nodes.

## LocalWave Secure Swarm Object Transport

The long-term file-transfer architecture is called **LocalWave Secure Swarm Object Transport**, or **LW-SSOT**.

LW-SSOT turns files into encrypted, resumable, verifiable local objects:

```text
Original file
  -> encrypted object
  -> verified pieces
  -> optional recovery pieces
  -> local encrypted cache
  -> direct transfer or relay transfer
  -> receiver verifies
  -> receiver decrypts locally
```

Important rule:

> Encrypt first. Then chunk. Then cache. Then relay. Then verify. Then decrypt only on the final recipient device.

Relays never receive plaintext.

Every file becomes a LocalWave Object:

```text
LocalWave Object
  encrypted manifest
  encrypted data pieces
  optional recovery pieces
  Merkle root
  recipient policy
  relay policy
  expiry policy
  delivery receipts
```

The object ID is derived from encrypted manifest data and is safe for transfer coordination because it does not reveal plaintext file content.

## Relay And Swarm Model

LocalWave should not describe its future relay system as standard Bluetooth Mesh.

The better model is:

> Application-layer encrypted store-and-forward transfer.

Trusted phones or dedicated relay nodes can carry encrypted pieces, piece indexes, expiry data, and relay tokens. They must never receive plaintext filenames, thumbnails, messages, object keys, invite phrases, full Frequency Codes, private keys, or shared secrets.

For serious warehouse range, fixed relay nodes are the practical path:

- Plugged-in Android phone
- Android tablet in kiosk mode
- Raspberry Pi with BLE adapter
- Dedicated BLE gateway hardware

Phone relay is useful. Dedicated relay is reliable.

## Security Model

LocalWave treats Bluetooth as an untrusted pipe. Application-layer trust, identity, encryption, replay protection, and verification live above transport.

Security layers:

```text
Frequency Code
  Discovery namespace only

Invite Phrase
  Invite-authenticated proof secret

Identity Key
  Long-term device trust

Session Key
  Short-lived peer session

Object Key
  Per-file encryption key

Relay Token
  Permission to carry encrypted pieces
```

Each install creates a long-term local identity. iOS uses Keychain/Secure Enclave where suitable. Android uses Android Keystore where suitable. Users can compare identity fingerprints for high-trust environments.

Encryption rules:

- Encrypt every message and file before transport
- Never rely only on Bluetooth pairing
- Use unique nonces
- Authenticate metadata
- Bind sender, recipient, object ID, protocol version, and channel context into authenticated data
- Verify before decrypting
- Reject unknown protocol versions safely
- Never store plaintext relay pieces

## Privacy Principles

LocalWave should maintain these privacy rules:

- No account
- No phone number
- No email
- No cloud sync
- No analytics SDK
- No tracking SDK
- No server relay
- No plaintext over Bluetooth
- No plaintext relay cache
- No secrets in logs
- No full Frequency Codes in diagnostics

Identity private keys stay on device. Message and file history stay local unless the user explicitly exports or shares content.

## Diagnostics

Diagnostics should explain why delivery succeeds or fails without exposing private content.

Safe diagnostics include:

- Peer discovered/lost
- Transport selected
- GATT/L2CAP availability
- MTU
- Transfer state
- Throughput
- Retry count
- Missing piece counts
- Disconnect reason category
- Foreground/background state
- Battery state
- Permission state

Diagnostics must not log plaintext messages, plaintext filenames unless explicitly exported by the user, invite phrases, Frequency Codes, object keys, private keys, shared secrets, or full ciphertext dumps.

## Background And Wake Reality

iOS and Android do not behave the same in the background. LocalWave must represent delivery honestly.

Reliability tiers:

```text
Foreground: best reliability
Recently active: good for short sessions
Background: best effort
Screen off / battery restricted: degraded
Bluetooth off / permission denied: unavailable
```

Wake is best effort. It does not bypass Bluetooth being disabled, notification permission, Focus/DND, silent mode, lock-screen policy, battery restrictions, iOS background limits, Android process death, or physical range.

## File Size Strategy

Recommended product behavior:

```text
Text / Wake: GATT
Under 1 MB: object transfer fast path
1 MB - 25 MB: L2CAP object transfer normal mode
25 MB - 100 MB: foreground transfer with resume required
100 MB+: encrypted native share package or explicit advanced mode
```

Honest wording:

- Nearby secure file sharing
- Works offline
- Best for messages, photos, PDFs, short videos, and warehouse documents
- Large files require both phones nearby and active

Do not market unlimited Bluetooth video transfer.

## Development Roadmap

### Phase 1: Direct Secure Transport Hardening

Make iOS to Android text and Wake reliable on physical devices.

- GATT control channel
- Secure peer intro
- Redacted diagnostics
- Delivery states
- Wake best-effort behavior
- Permission handling
- Physical-device testing

### Phase 2: L2CAP Proof

Prove iOS to Android L2CAP Credit-Based Channel transfer.

- GATT PSM negotiation
- L2CAP open/close lifecycle
- 1 MB, 10 MB, and 25 MB test objects
- Throughput metrics
- Disconnect recovery

### Phase 3: Secure Object Store

Implemented in v1 as encrypted manifests, encrypted pieces, object IDs, app-private encrypted object cache, and native `.localwavepkg` packages. Continue hardening with cross-platform physical tests and richer resume UI.

- Encrypted manifest
- Object IDs and piece IDs
- Piece hashes
- Local encrypted object cache
- Missing bitmap
- Resume token
- Transfer records

### Phase 4: Relay Cache

Allow trusted devices to carry encrypted pieces.

- Relay tokens
- Expiry policy
- Relay storage quota
- Encrypted-only cache
- Piece inventory exchange
- Store-and-forward delivery

### Phase 5: Swarm Scheduler

Fetch pieces from the best available nearby peers.

- Peer scoring
- Rarest-first pieces
- Fastest-peer selection
- Duplicate suppression
- Congestion control
- Battery-aware relay limits

### Phase 6: Recovery Pieces

v1 uses XOR parity stripes that can recover one missing piece per stripe. Future work can evaluate stronger vetted erasure coding if the dependency and interoperability cost are justified.

- XOR parity v1
- Recovery piece generation
- Reconstruction
- Verification
- Adaptive recovery percentage

### Phase 7: Dedicated Relay Node

Improve warehouse range and reliability.

- Android kiosk relay
- Plugged-in relay mode
- Relay dashboard
- Cache cleanup
- Coverage diagnostics
- Fixed-node policies

## Testing

Simulator and emulator tests are useful for UI, view models, protocol tests, and persistence tests. They cannot prove physical Bluetooth behavior.

Required physical tests:

- iPhone to iPhone message
- Android to Android message
- iPhone to Android message
- Android to iPhone message
- Wake both directions
- 1 MB transfer
- 10 MB transfer
- 25 MB transfer
- Disconnect and resume
- Permission denied states
- Bluetooth off states
- Background/recently-active behavior
- Relay cache behavior

Recommended metrics:

- Time to discovery
- Time to secure session
- Time to first manifest
- Average and peak throughput
- Piece retry count
- Disconnect count
- Battery impact
- Foreground/background state
- Transport selected
- Final verification result

## Build And Run

### iOS

```bash
cd ios-localwave
xcodegen generate
xcodebuild test -project LocalWave.xcodeproj -scheme LocalWave -destination 'platform=iOS Simulator,name=iPhone 17'
open LocalWave.xcodeproj
```

Real Bluetooth testing requires physical iPhones. Configure signing in Xcode, select a physical device, and run the `LocalWave` scheme.

### Android

```bash
cd android-localwave
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:installDebug
```

Real Bluetooth testing requires physical Android devices and/or an Android device paired with a physical iPhone.

## UI Wording Rules

Use:

- Frequency Code
- Channel Frequency
- Private local Bluetooth channel
- No internet. No signup. Nearby only.
- Wake sends a local Bluetooth ping when reachable.
- Secure local transfer
- Encrypted local package
- Nearby sources

Avoid:

- real radio frequency
- emergency radio
- guaranteed wake
- military-grade encryption
- anonymous network
- always-on pager
- unlimited Bluetooth transfer

## App Store And Play Store Positioning

Describe LocalWave as:

- A nearby private Bluetooth communication app
- An offline local team messaging app
- A secure local file sharing tool

Required disclosures should be honest about Bluetooth use, notifications, local storage, file/media access, background behavior, no third-party tracking, and no cloud account.

## Design Philosophy

LocalWave should feel premium, simple, and honest.

The engine can be advanced. The UI should stay calm.

Users should see nearby people, trusted identity, clear signal state, simple send actions, clear transfer progress, honest failure reasons, and easy retry paths without needing to understand the protocol internals.

## License

See [LICENSE](LICENSE).

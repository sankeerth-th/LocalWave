# LocalWave Architecture

## Layers

LocalWave is split into four testable layers:

1. **SwiftUI Product UI**
   - `LocalWave/App`
   - `LocalWave/DesignSystem`
   - `LocalWave/Features`
   - Owns navigation, onboarding, settings, chat UI, permission education, accessibility, and preview/mock data.

2. **App State**
   - `LocalWave/Core/AppState`
   - Owns `@MainActor` view models and dependency injection through `AppEnvironment`.
   - Depends on protocols, not concrete Bluetooth or crypto implementations.

3. **Core Engine**
   - `LocalWave/Core/Bluetooth`
   - `LocalWave/Core/Crypto`
   - `LocalWave/Core/Persistence`
   - `LocalWave/Core/Notifications`
   - Owns identity, encrypted envelopes, packet framing, BLE central/peripheral roles, wake delivery, repositories, and local notifications.

4. **Domain Models and Protocols**
   - `LocalWave/Core/Models`
   - Shared value types and integration contracts.

## Protocol Boundary

The UI integrates through:

```swift
public protocol LocalWaveEngineProtocol: AnyObject, Sendable {
    func start(channel: ChannelCode, displayName: String) async throws
    func stop() async
    func updateDisplayName(_ displayName: String) async
    func switchChannel(_ channel: ChannelCode) async throws
    func sendMessage(text: String, to peerId: PeerID) async throws -> MessageID
    func sendWake(to peerId: PeerID) async throws
    func observePeers() -> AsyncStream<[PeerProfile]>
    func observeMessages(peerId: PeerID) -> AsyncStream<[ChatMessage]>
    func observeTransportState() -> AsyncStream<TransportState>
    func localIdentity() async throws -> LocalIdentity
}
```

Production uses the CoreBluetooth engine. Tests and previews use a mock engine behind the same protocol.

## Bluetooth Transport

Each device runs both roles:

- Peripheral: advertises the deterministic channel service UUID and exposes GATT characteristics.
- Central: scans for the channel service UUID, connects, reads presence, writes encrypted packets, and subscribes to notifications.

The advertisement payload stays small and never depends on BLE local name. Peer identity and presence are exchanged through GATT after connection.

## GATT Profile

- Presence characteristic: signed/encrypted peer intro payload.
- Inbox characteristic: write-with-response encrypted packet intake.
- Stream characteristic: notify subscribers that encrypted packets are available.
- Wake characteristic: write-with-response encrypted wake envelope.
- Receipt characteristic: optional encrypted delivery ACK.

## Packet Framing

BLE payloads are small, so encrypted envelopes are chunked into transport packets with:

- version
- packet ID
- conversation ID
- packet kind
- chunk index
- chunk count
- body length
- checksum

The crypto layer authenticates message integrity. Packet checksums catch malformed framing before envelope decode.

## Persistence

Local repositories persist peers and messages on device. Identity private keys are not stored in repository files; they live in Keychain. Messages are stored locally for app history, while encrypted transport envelopes protect over-the-air content.

## Background Limitations

The app declares `bluetooth-central` and `bluetooth-peripheral` background modes, but iOS still controls execution windows, scanning behavior, notification display, Focus, silent mode, and Bluetooth availability. LocalWave represents pending/sent/delivered/failed states instead of pretending delivery is guaranteed.

# LocalWave Production Mesh Architecture

## Current State

LocalWave has native iOS and Android apps with local identity keys, deterministic Frequency Code GATT UUIDs, BLE discovery, encrypted envelopes, local persistence, wake notifications, and app UIs. The current transport is direct peer-to-peer BLE: a device advertises, scans, connects to a reachable peer, reads presence, and writes encrypted packets to that peer.

That is not yet a range-expanding mesh. A direct BLE app can show nearby devices, but it cannot deliver to an out-of-range peer unless the protocol, storage, trust model, routing, duplicate suppression, and UI states all understand relay delivery.

## Product Truths

- Frequency Code is a logical private Bluetooth channel, not real analog RF tuning.
- No signup, no cloud, no server relay, no analytics, no tracking SDK.
- BLE delivery is opportunistic. iOS and Android can throttle background BLE, notifications, scanning, and advertising.
- Wake cannot bypass notification permission, Focus/DND, silent mode, app suspension, or OEM battery limits.
- LocalWave is not an emergency/public-safety reliability system.

## Required End-State

LocalWave should behave like a secure local team mesh when many devices are active on the same Frequency Code:

- Devices continuously discover reachable peers while the app is active and, where platforms allow, recently backgrounded.
- Direct messages and wake pings prefer direct BLE delivery.
- If the destination is not currently reachable, packets are stored encrypted and forwarded by trusted same-channel peers with bounded TTL.
- Devices show honest state: Direct, Relayed, Pending Relay, Delivered, Expired, Untrusted, or Blocked.
- Fingerprints become a real trust and safety feature, not decorative text.
- Attachments and sticker packs are encrypted, chunked, size-limited, and clearly represented in message status.

## Mesh Transport

### Packet Types

Add protocol-level packet types beyond the current message/wake/receipt primitives:

- `presence`: signed local identity, capabilities, app protocol version, and short-lived route hints.
- `directMessage`: end-to-end encrypted user message for one recipient.
- `wake`: end-to-end encrypted wake intent for one recipient.
- `receipt`: end-to-end encrypted delivery/read state where enabled.
- `relayOffer`: compact list of destination IDs this device has pending packets for.
- `relayPacket`: opaque encrypted payload forwarded for another destination.
- `attachmentManifest`: encrypted metadata for media/sticker payload transfer.
- `attachmentChunk`: encrypted fixed-size chunk with hash validation.

### Routing Rules

- Every packet has `originId`, `destinationId`, `packetId`, `createdAt`, `expiresAt`, `ttl`, `hopCount`, and `previousHopId`.
- Relays decrement TTL and refuse expired packets.
- Devices keep a bounded LRU cache of seen packet IDs to prevent relay loops.
- A device forwards only packets for the same Frequency Code and compatible protocol version.
- A device never decrypts relayed message content unless it is the destination.
- Relays may see metadata needed for routing: packet ID, origin/destination IDs, TTL, size, timing, and hop count.
- Relay queue limits must protect battery and storage: per-peer queue cap, per-channel queue cap, attachment cap, and age cap.

### Scale Target

For 100 nearby installs, avoid connecting to every peer continuously. Use:

- scan/advertise duty cycling;
- connection scheduler with jitter and backoff;
- RSSI-aware peer priority;
- maximum simultaneous active GATT connections;
- rotating relay windows;
- compact relay offers before transferring payloads;
- backpressure when queues grow.

## Trust And Fingerprints

The fingerprint is not random UI decoration. It is a hash of the long-lived public identity keys stored in Keychain/Android Keystore. Its purpose is to detect impersonation and key changes.

Required trust states:

- `Unverified`: discovered by BLE, not manually trusted yet.
- `Verified`: user compared fingerprint or scanned local QR code.
- `Changed`: same peer name/ID appears with a different fingerprint.
- `Blocked`: user refuses traffic from this identity.

Required UX:

- Show a short safety number in chat header.
- Add “Verify Identity” screen with full fingerprint, QR code, and scan/compare flow.
- Warn hard on changed fingerprints before sending.
- Let teams reset trust for a peer.
- Diagnostics must show redacted fingerprints only.

Required security:

- Sign presence payloads with the identity signing key.
- Verify that received public keys derive the advertised fingerprint.
- Pin verified fingerprints in local storage.
- Never log private keys, shared secrets, plaintext messages, media bytes, or full Frequency Codes.

## Encryption Model

Maintain end-to-end encryption from sender to final recipient:

- Pairwise X25519 ECDH session keys.
- HKDF-SHA256 with channel salt, protocol version, sorted peer IDs, and transcript context.
- AES-GCM or ChaCha20-Poly1305 envelopes.
- Replay counters and message IDs per sender/channel.
- Associated data must include version, type, sender, recipient, message ID, timestamp, replay counter, channel, and route context.
- Relayed packets are opaque to intermediate devices.

For mesh forwarding, add an outer relay header authenticated by the forwarding device and an inner end-to-end envelope authenticated by the origin.

## Wake

Wake should use the same delivery stack as messages:

- try direct send first;
- if direct unavailable, enqueue wake with short expiry;
- relay only while expiry is valid;
- show `Wake queued`, `Wake sent direct`, `Wake relayed`, `Wake expired`, or `Wake blocked`;
- receiver schedules a local notification only after decrypting and replay-validating the wake envelope;
- if notification permission is missing, expose an in-app wake event.

## Media And Stickers

### Images And Video

Attachments should be a separate encrypted transfer protocol, not base64 text messages.

- Limit v1 image size and transcode/compress locally.
- Limit v1 video duration/size aggressively.
- Encrypt attachment manifest and chunks.
- Hash every chunk and the whole file.
- Store encrypted attachment blobs locally.
- Transfer thumbnails first, then full media on tap or when connected long enough.
- Expire undelivered attachment chunks.

### Stickers

Offline sticker packs should be bundled with the app or side-loaded through future local import. App Store / Play Store distribution can ship new bundled packs in app updates, but runtime sticker use must not require internet.

Sticker messages should send a sticker pack ID, sticker ID, and version. If the recipient lacks the pack, show a clear missing-sticker state.

## Platform Notes

### iOS

- CoreBluetooth central/peripheral is the product transport.
- Background BLE is best-effort and can be suspended.
- Use write-with-response or a paced write queue for reliability.
- Do not depend on local name advertising.
- Use Keychain `ThisDeviceOnly` identity storage.
- Physical device testing is mandatory.

### Android

- Use BLE scanner, advertiser, GATT server, and GATT client.
- Use foreground service for active mode where appropriate.
- Handle Android 12+ Bluetooth runtime permissions and Android 13+ notification permission.
- OEM battery policies may still limit background behavior.
- Replace deprecated GATT write calls with API-level-aware writes.

## Implementation Workstreams

### Workstream 1: Protocol Compatibility

- Add cross-platform test vectors for message, wake, receipt, and relayed packets.
- Make timestamp encoding deterministic at millisecond precision.
- Replace platform-specific JSON assumptions with explicit protocol codecs.
- Add tests that encrypt, serialize, deserialize, and decrypt on both platforms.

### Workstream 2: Reliable Direct Delivery

- Add paced BLE write queues.
- Add write acknowledgements and retry windows.
- Add packet receipts.
- Expose honest send states in UI.
- Test iOS to iOS, Android to Android, iOS to Android, and Android to iOS on physical devices.

### Workstream 3: Mesh Store-And-Forward

- Add relay packet models and persistent relay queues.
- Add relay offer handshake.
- Add TTL, expiry, duplicate suppression, queue caps, and route metrics.
- Add relayed delivery receipts.
- Add UI states for direct/relayed/pending/expired.

### Workstream 4: Trust And Fingerprint UX

- Add signed presence.
- Add trust store and trust state transitions.
- Add verify identity QR/scan/compare flow.
- Add changed-key warnings.
- Add block/reset trust controls.

### Workstream 5: Wake

- Route wake through direct and relay paths.
- Add wake expiry.
- Add local notification fallback state.
- Add physical-device tests for foreground, recently backgrounded, locked, notification denied, and battery restricted states.

### Workstream 6: Media And Stickers

- Add encrypted attachment manifest/chunks.
- Add image picker and camera import using platform-native APIs.
- Add video picker with strict limits.
- Add bundled sticker pack manifest and picker.
- Add attachment progress UI and failure recovery.

### Workstream 7: Security Hardening

- Add fuzz tests for packet decoder and JSON codec.
- Add malformed BLE packet rejection tests.
- Add log redaction tests.
- Add trust downgrade tests.
- Add no-network checks in manifests, project settings, and dependency review.

## Physical Test Matrix

Minimum brutal test setup:

- 2 iPhones, 2 Android phones, then 10 mixed devices, then 25+ devices.
- Device C on different Frequency Code must stay invisible.
- A and B direct message both directions.
- A wakes B and B wakes A.
- A reaches C through B when A and C are not directly reachable.
- Relay loops do not duplicate messages.
- Weak signal and walking out of range produce pending/expired states, not fake delivered states.
- Changed fingerprint blocks sending until user resolves trust.
- Notification denied produces in-app wake fallback.
- Media transfer can be interrupted and resumed or fail cleanly.
- No plaintext messages/media/private keys appear in logs or debug bundles.

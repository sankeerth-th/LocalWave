# LocalWave Cross-Platform Protocol

LocalWave uses Bluetooth as an untrusted local carrier. The application protocol is responsible for channel discovery privacy, identity verification, encrypted envelopes, replay protection, delivery state, and attachment package integrity.

## Transport Lanes

### Direct LocalWave Lane

- Discovery: BLE advertisements for the LocalWave app service and channel-derived service UUID.
- Control: GATT characteristics for presence, trust bootstrap, wake, receipts, text fallback, and L2CAP setup.
- Bulk: BLE LE L2CAP CoC when both peers advertise support after encrypted capability negotiation.
- Fallback: GATT is limited to text, wake, receipts, and tiny control payloads.

### Native Share Lane

- The app exports an encrypted `.localwavepkg` object.
- iOS presents `UIActivityViewController`.
- Android presents `Intent.ACTION_SEND` through the Android Sharesheet.
- AirDrop, Quick Share, or another system share target is user-mediated transport only. LocalWave does not treat those flows as app-controlled delivery.

## Channel And Invite

`Frequency Code` is a logical private Bluetooth channel, not a real RF tuner. Normalize by trimming, uppercasing, and collapsing internal whitespace.

Private channels require an invite phrase before production release. The current app implementation includes invite-authenticated first-contact proof and key confirmation so peers with the wrong phrase are rejected before trust is pinned.

A standards-grade PAKE remains the production cryptography target when an audited dependency is approved. Do not describe the current HMAC proof as SPAKE2, OPAQUE, or a full PAKE:

1. Both peers derive a channel discovery context from `Frequency Code + invite phrase`.
2. Peers prove possession of the invite phrase before accepting long-term identity keys.
3. The authenticated transcript pins the identity fingerprint.
4. Weak or missing invite phrases must not be represented as a strong privacy guarantee.

## Identity And Trust

Each install owns a long-term device identity:

- Key agreement public key.
- Signing public key or future signature-capable identity key.
- Fingerprint derived from public identity material.

Peer trust states:

- `Unverified`: discovered but not explicitly verified.
- `Verified`: fingerprint or safety number accepted by the user or deployment policy.
- `Changed`: known peer now presents different identity material; block sending until reviewed.
- `Blocked`: user or policy blocked the peer.

Fingerprint is not decoration. It is the human-verifiable identity-key commitment used to detect impersonation and key changes.

## Capability Negotiation

Capabilities are exchanged only after encrypted trust bootstrap. Current cross-platform capability values:

- `gattMessaging`
- `l2capCoc`
- `nativeSharePackage`
- `fixedRelay`
- `phoneRelayBestEffort`

Capability messages must include protocol version, sender identity, supported envelope versions, maximum GATT payload, preferred attachment chunk size, route preferences, and relay policy.

## Encrypted Envelopes

All message, wake, and attachment payloads are encrypted above Bluetooth. The current AEAD envelope fields are:

- `version`
- `kind`: `message`, `wake`, or `attachment`
- `senderId`
- `recipientId`
- `timestamp`
- `messageId` or `transferId`
- `replayCounter`
- `nonce`
- `ciphertext`
- `tag`

AEAD associated data covers version, kind, sender, recipient, message or transfer id, timestamp, replay counter, and normalized channel code. Receivers reject wrong sender, wrong recipient, failed authentication, duplicate replay counters, and malformed payloads.

## Attachment Packages

Attachment plaintext before encryption:

- `fileName`
- `contentType`
- `byteCount`
- `sha256`
- `payload`

Native share package:

- File extension: `.localwavepkg`
- Versioned container with route `nativeShare`.
- Contains the encrypted attachment envelope and package metadata.
- Import verifies package version, decrypts locally, validates the payload hash, records provenance, and does not trust the system share path as proof of delivery.

Size defaults:

- GATT inline ceiling: 64 KiB.
- Native share package ceiling: 50 MiB by default.
- L2CAP target chunk size: 16-32 KiB encrypted chunks after direct transfer support is complete.

## Delivery And Transfer State

Delivery routes:

- `gatt`
- `l2cap`
- `nativeShare`
- `fixedRelay`
- `phoneRelay`

Transfer states:

- `queued`
- `negotiating`
- `sending`
- `exported`
- `importing`
- `delivered`
- `pending`
- `failed`
- `expired`

The UI must never mark native share export as delivered. Export means the encrypted package was produced for the user-mediated share lane. Delivered requires an in-app receipt or successful local import by the recipient.

## Relay Rules

Fixed powered Android or hardware relays are the reliable warehouse range-expansion path. Phone relays are best-effort only.

Relays may store and forward only opaque encrypted packets or chunks. Relay metadata must include TTL, expiry, duplicate suppression key, queue cap, route provenance, and honest pending or expired states. Relays must not receive plaintext messages, attachment names, invite phrases, private keys, or decrypted manifests.

## Logging

Allowed diagnostics:

- Redacted peer fingerprint prefix.
- Transport route.
- Packet kind.
- Byte counts.
- MTU or PSM setup state.
- Retry count.
- Error category.

Forbidden diagnostics:

- Plaintext messages.
- Attachment payloads.
- Invite phrases.
- Private keys or shared secrets.
- Full Frequency Codes.
- Full identity fingerprints unless the user explicitly opens an identity verification screen.

## Platform Limits

- iOS background Bluetooth scanning and advertising are throttled and screen-off behavior is limited.
- Wake is best effort and must respect notification permission, Focus, silent mode, lock screen policy, and OS scheduling.
- Bluetooth-only phone meshes should not be marketed as fast large-file infrastructure.
- LocalWave is not an emergency, public-safety, or guaranteed-delivery system.

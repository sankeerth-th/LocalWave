# LocalWave Security Model

## Assets

- Local identity private keys
- Pairwise session secrets
- Message plaintext
- Message history stored on device
- Peer trust/fingerprints
- Frequency Code and optional private channel password

## Threat Model

### Passive Nearby Observer

A person nearby may observe BLE traffic timing, signal strength, packet sizes, and service UUIDs.

Mitigations:
- End-to-end encrypted message envelopes
- Pairwise session keys
- No plaintext message body in BLE packets
- Redacted diagnostics

Limitations:
- Nearby metadata such as timing, rough signal, and activity patterns may still be observable.
- A weak or shared Frequency Code may reveal that a LocalWave channel exists nearby.

### Active Nearby BLE Peer

A nearby attacker may run LocalWave or compatible BLE tooling, guess a Frequency Code, advertise services, attempt malformed packets, or impersonate peers.

Mitigations:
- Deterministic service UUID requires matching normalized channel context
- Identity fingerprints shown for manual trust verification
- Public-key session establishment
- Authenticated encryption
- Strict packet parsing and size limits
- Replay counters

Limitations:
- Display names are not secret after trusted presence exchange.
- Users must verify fingerprints for high-trust environments.
- Guessable Frequency Codes or weak passwords reduce discovery privacy.

### Replay Attacker

An attacker may record encrypted BLE packets and replay them later.

Mitigations:
- Per-peer replay counters
- Authenticated encrypted envelopes
- Message IDs and timestamps

Limitations:
- Devices with cleared trust state may need to re-establish counters and trust context.

### Lost or Stolen Phone

An attacker with physical access to a phone may try to read app data or use the unlocked app.

Mitigations:
- Identity private keys stored in Keychain
- iOS Data Protection applies to app container files
- No plaintext secrets in logs
- Trust reset controls

Limitations:
- If the device is unlocked and compromised, local message history visible in the app may be exposed.
- LocalWave does not replace device passcode, MDM, or hardware security policies.

### Guessable Frequency Code

An attacker may guess common codes like `DOCK`, `462.625`, or shift names.

Mitigations:
- Optional private channel password/invite phrase
- Channel/password material included in key derivation context
- UI explains that codes are logical Bluetooth channels, not secret radio frequencies

Limitations:
- The Frequency Code alone should not be treated as a strong secret.

## Crypto Design

- Curve25519 ECDH for pairwise shared secret establishment
- CryptoKit HKDF-SHA256 for session key derivation
- AES.GCM authenticated encryption for envelopes
- P256 signing key for local identity support where needed by presence/trust flows
- Keychain storage for private identity keys
- Random 256-bit object keys for file/object transfer
- Per-recipient object-key wrapping with the pairwise session-derived wrapping key
- Encrypted object manifests and encrypted object pieces cached under app-private storage
- XOR parity recovery only, one missing piece per stripe; this is not Reed-Solomon

Every envelope includes:
- version
- sender ID
- recipient ID or group marker
- timestamp
- nonce
- ciphertext
- authentication tag
- message ID where applicable
- replay counter

## Invite Proof And PAKE Gate

The current implementation includes invite-authenticated proof and transcript binding tests. It must not be marketed or documented as SPAKE2, OPAQUE, or a complete standards PAKE. A vetted PAKE dependency and interoperability test vectors remain a production security gate before strong password-authenticated first-contact claims.

## Object Transfer Security

LocalWave object transfer encrypts metadata and content before caching or transport. Relays and native-share carriers see only encrypted manifests, encrypted pieces, object IDs, expiry, sizes, and route metadata. Receivers reject expired objects, manifest commitment mismatches, Merkle root mismatches, bad encrypted-piece hashes, wrong wrapped object keys, and final plaintext hash mismatches.

Native share packages are user-mediated transport. AirDrop, Quick Share, or any other Sharesheet target is not treated as proof of delivery. Sender-side completion requires an in-app transfer receipt.

## Logging Rules

Never log:
- plaintext messages
- private keys
- shared secrets
- full Frequency Codes
- private channel passwords
- full ciphertext payloads when avoidable

Allowed diagnostics:
- redacted fingerprints
- redacted channel hashes
- transport state
- BLE error category
- packet counts and timestamps

## No-Network Guarantee

Production LocalWave uses CoreBluetooth for local transport. It does not include Firebase, Supabase, analytics SDKs, tracking SDKs, backend clients, or internet messaging code. Any future MultipeerConnectivity or network experiment must be compile-time disabled and unused in product flow.

## Non-Goals

LocalWave is not:
- an emergency communications system
- a public-safety radio
- a guaranteed always-on paging system
- a way to bypass iOS notification, Focus, Bluetooth, or background rules

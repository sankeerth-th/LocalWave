# LocalWave Android Security

## Threat Model

### Passive Nearby BLE Observer

May observe timing, packet size, RSSI, and service UUIDs.

Mitigations: encrypted envelopes, pairwise session keys, no plaintext message bodies over BLE, redacted diagnostics.

Limitations: BLE metadata and weak Frequency Codes may still reveal channel activity.

### Malicious Nearby BLE Peer

May guess a Frequency Code, advertise compatible services, connect, send malformed packets, or impersonate a peer.

Mitigations: deterministic channel context, identity fingerprints, authenticated encryption, strict packet parsing, replay counters.

Limitations: users must verify fingerprints for high-trust environments, and weak codes remain guessable.

### Replay Attacker

May replay captured encrypted packets.

Mitigations: per-peer/channel replay counters and duplicate message ID rejection.

### Lost Or Stolen Phone

May expose app-local data if the device is unlocked or compromised.

Mitigations: Android Keystore-wrapped identity private material, no private keys in Room, no plaintext secrets in logs, Android backup exclusion rules.

Limitations: v1 local message text is stored plaintext in Room to match iOS local-history behavior.

### Android Backup And OEM Behavior

Backup, device transfer, and OEM battery policies can affect confidentiality and reliability.

Mitigations: backup exclusions for LocalWave database and identity preferences, foreground active-mode foundation.

Limitations: OEM policies may still throttle BLE and Wake.

### Notification Privacy Leak

Wake notifications could reveal that a teammate is trying to reach the user.

Mitigations: default copy avoids message content and uses only display name when allowed.

## No-Network Guarantee

The manifest does not request `INTERNET`. The project does not include Firebase, Supabase, analytics, tracking SDKs, backend clients, or server relay code.

## UI And Diagnostics Rules

- Permission copy says Bluetooth is required for nearby LocalWave users and notifications are optional for Wake alerts.
- Frequency Code copy says logical Bluetooth channel, not real RF tuning.
- Wake copy says local Bluetooth ping when reachable and never guarantees delivery.
- Diagnostics show engine mode, transport state, peer count, permission state, and redacted error categories only.
- UI diagnostics must not show plaintext messages, private keys, shared secrets, raw decrypted envelopes, sensitive notification payloads, or private channel passwords.

## Non-Goals

LocalWave is not an emergency/public-safety communication system, guaranteed wake pager, anonymous network, or real RF tuner.

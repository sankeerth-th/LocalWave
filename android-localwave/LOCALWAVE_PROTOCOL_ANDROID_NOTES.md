# LocalWave Protocol Android Notes

## Matched iOS Behavior

- Frequency Code normalization: trim, collapse internal whitespace, uppercase locale-independently, preserve dots/hyphens.
- Minimum Frequency Code length: 3.
- Channel namespace: `LocalWave.Channel.v1`.
- UUID labels: `channel-id`, `gatt-service`, `gatt-packet`, `gatt-presence`, `gatt-wake`.
- Discovery tag: first 8 bytes of SHA-256 over `LocalWave.Channel.v1.discovery-tag.<normalized>`.
- HKDF salt: SHA-256 over `LocalWave.Channel.v1.hkdf-salt.<normalized>`.
- Packet wire layout: version, packet UUID, conversation UUID, kind, chunk index, chunk count, body length, checksum, body.
- Packet integers: big-endian.
- Packet kinds: presence `1`, message `2`, wake `3`, receipt `4`.
- Crypto: X25519 ECDH, HKDF-SHA256, AES-GCM, AAD prefix `LocalWave.AAD.v1`.

## Proposed Cross-Platform V1

- Android enforces max Frequency Code length 32. This requires iOS confirmation if the iOS team wants a different limit.
- Android message history is plaintext in Room for v1, matching iOS local history. Encrypt-at-rest can be added later without changing BLE protocol.

## Requires iOS Confirmation

- iOS v1 advertises presence in service data and ignores peers without it. Android attempts iOS-compatible service-data advertising and also supports GATT presence. Some Android devices may reject oversized advertisements; if that happens, iOS must confirm a smaller presence payload or GATT-only discovery flow.
- Receipt packet kind exists but is not active in iOS. Android does not mark delivered without a receipt.
- Fixed end-to-end crypto vectors from iOS should be added before claiming fully verified Android/iPhone encrypted envelope compatibility.

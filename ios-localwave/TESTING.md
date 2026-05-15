# LocalWave Testing

## Automated Tests

Run:

```bash
xcodegen generate
xcodebuild test -project LocalWave.xcodeproj -scheme LocalWave -destination 'platform=iOS Simulator,name=iPhone 17'
```

Covered behaviors:
- Same Frequency Code derives the same service UUID
- Different Frequency Codes derive different service UUIDs
- Message encrypt/decrypt succeeds for the intended peer
- Decrypt fails with the wrong key
- Replay counter rejects duplicate envelopes
- Packet chunk/reassemble works
- Oversized messages are rejected
- Repositories store/load peers and messages
- Onboarding state transitions
- Peer list updates
- Message send moves through pending/sent
- Wake unavailable state is surfaced

Thread 2 view-model coverage:
- Onboarding validates display name and Frequency Code before completion
- App setup stores normalized Frequency Code and display name
- People view model observes peer and transport streams
- Bluetooth permission states affect People/Wake UI state
- Chat send disables for empty or unreachable peers
- Mock message sends transition from pending to sent/delivered
- Settings validates channel changes and redacts diagnostics

## Manual Physical-Device Tests

Use at least two physical iPhones. A simulator is not sufficient for real Bluetooth validation.

### Same Channel Discovery

1. Install LocalWave on Device A and Device B.
2. Open both apps.
3. Enter different display names.
4. Enter the same Frequency Code, for example `DOCK-A-17`.
5. Allow Bluetooth permission.
6. Confirm both devices appear in each other's People list.

Expected: both peers appear with display name, fingerprint, signal indicator, status, and last-seen time.

### Different Channel Isolation

1. Keep Device A on `DOCK-A-17`.
2. Switch Device B to `DOCK-B-17`.

Expected: peer lists clear after the channel switch and the devices no longer discover each other.

### Encrypted Message Delivery

1. Put Device A and Device B on the same Frequency Code.
2. Tap Device B from Device A.
3. Send a short text message.

Expected: Device A shows pending then sent/delivered when receipt is available. Device B receives the message in chat. No plaintext message appears in device logs.

### Wake Behavior

1. Allow notifications on Device A.
2. From Device B, tap Wake for Device A.

Expected: Device A receives an in-app wake banner when foregrounded or a local notification when iOS allows background handling.

### Notification Denied Fallback

1. Deny LocalWave notification permission on Device A.
2. Send Wake from Device B.
3. Open Device A.

Expected: Device A shows an in-app wake banner/fallback. No bypass of iOS notification settings occurs.

### Identity Persistence

1. Record Device A identity fingerprint in Settings.
2. Kill and reopen LocalWave.
3. Return to Settings.

Expected: identity fingerprint is unchanged.

## Background Notes

iOS may throttle or defer BLE work in the background. Test Wake and delivery with the app foregrounded, recently backgrounded, locked, and after a longer idle period. Document observed device/iOS behavior for the deployment environment.

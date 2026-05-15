# LocalWave

LocalWave is an offline, no-signup iOS app for private local team messaging over Bluetooth. It has no account, no phone number, no email, no backend, no analytics SDK, and no cloud messaging path.

## Product Reality

The **Frequency Code** is a logical private Bluetooth channel. LocalWave does not tune the iPhone to an arbitrary analog radio frequency and does not control the physical RF layer like a walkie-talkie. iOS and Bluetooth manage radio behavior; LocalWave derives a deterministic BLE service UUID and encryption context from the Frequency Code so nearby devices using the same app and same code can find each other.

Examples:
- `462.625`
- `DOCK-A-17`
- `NIGHT SHIFT`

For stronger privacy, use a private channel password/invite phrase in addition to a Frequency Code. A guessable code can make discovery easier for a nearby person running the app.

## Requirements

- Xcode 26.1 or later recommended
- iOS 17+ deployment target
- Two physical iPhones for real Bluetooth discovery/message testing
- Bluetooth permission
- Notification permission for Wake alerts

The simulator can build the app and run unit/view-model tests, but it cannot validate real CoreBluetooth central/peripheral behavior between phones. Simulator UI uses a mock engine with no seeded peers in live mode, so it must not be treated as physical-device BLE evidence.

## Build

```bash
xcodegen generate
xcodebuild test -project LocalWave.xcodeproj -scheme LocalWave -destination 'platform=iOS Simulator,name=iPhone 17'
```

Open `LocalWave.xcodeproj` in Xcode, select the `LocalWave` scheme, choose a physical iPhone, configure signing, then run.

## SwiftUI Product Layer

The app shell lives under `LocalWave/App`, UI state under `LocalWave/AppState`, reusable native components under `LocalWave/DesignSystem`, and screens under `LocalWave/Features`.

SwiftUI screens integrate only through `LocalWaveEngineProtocol`. Previews and simulator development use `MockLocalWaveEngine`; production device builds use the CoreBluetooth engine through the same `AppEnvironment` contract. Mock mode is exposed in Diagnostics, not as fake peers on the main People screen.

Main UI flows:
- Onboarding: welcome, display name, Frequency Code, Bluetooth education, notification education.
- People: current Channel Frequency, scanning state, permission banner, nearby peers, signal, last seen, Wake.
- Chat: local message bubbles, pending/sent/delivered/failed status, retry affordance, Wake.
- Settings: display name, channel switch, privacy explanation, fingerprint, permissions, redacted diagnostics.

Preview coverage is included on the major screens. In Xcode, open a SwiftUI file under `LocalWave/Features` and select the preview variants for onboarding, People, Chat, Settings, light mode, and dark mode.

## Two-iPhone Setup

1. Install LocalWave on Device A and Device B from Xcode.
2. Launch both apps.
3. Enter a display name on each device.
4. Enter the same Frequency Code on both devices.
5. Allow Bluetooth permission.
6. Allow notifications if Wake alerts should appear outside the foreground app.
7. Keep both apps recently opened and Bluetooth enabled.
8. Confirm each device appears in the People list.
9. Send a message from A to B.
10. Send Wake from B to A.

## Wake Reliability

Wake uses encrypted Bluetooth delivery plus local notifications. It does not bypass iOS notification permissions, Focus modes, silent mode, lock-screen behavior, Bluetooth state, or background execution limits. Wake works best when both users have recently opened the app and are physically nearby.

## Privacy

Messages are encrypted in LocalWave envelopes before Bluetooth transport. Identity private keys are stored in Keychain. Debug diagnostics redact sensitive values and must not include plaintext messages, private keys, shared secrets, or full Frequency Codes.

LocalWave is not an emergency/public-safety communication system and does not guarantee always-on delivery.

## UI Wording Rules

Use “Frequency Code,” “Channel Frequency,” “Private local Bluetooth channel,” “No internet. No signup. Nearby only,” and “Wake sends a local Bluetooth ping when reachable.”

Do not describe LocalWave as a real radio tuner, emergency radio, guaranteed wake system, anonymous network, military-grade encryption product, or always-on background pager.

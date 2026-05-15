# LocalWave Android Thread 4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android-native Jetpack Compose product shell, onboarding, permission UX, People, Chat, Settings, diagnostics, accessibility, and UI tests for LocalWave.

**Architecture:** Thread 4 owns only the app, design, feature, view-model, UI-test, and documentation layers. UI integrates through `LocalWaveEngine` and UI-safe permission abstractions; BLE transport, crypto, packet framing, database, wake internals, and persistence internals remain Thread 3 owned.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, AndroidX Lifecycle ViewModel, Kotlin Coroutines/Flow, DataStore Preferences, AndroidX test/JUnit.

---

## 1. Files And Contracts Found From Thread 3

Thread 3 Android files were requested at `/Users/sanks04/Desktop/Random-1/android-localwave`, but that directory did not exist when Thread 4 started. These requested files were therefore unavailable:

- `CODEX_THREAD3_PLAN.md`
- `README.md`
- `ARCHITECTURE_ANDROID.md`
- `SECURITY_ANDROID.md`
- `TESTING_ANDROID.md`
- `LOCALWAVE_PROTOCOL_ANDROID_NOTES.md`
- `app/src/main/java/com/localwave/core/protocol/LocalWaveEngine.kt`
- `app/src/main/java/com/localwave/core/model/*`
- `app/src/main/java/com/localwave/mock/MockLocalWaveEngine.kt`
- `app/src/main/java/com/localwave/core/bluetooth/BlePermissionManager.kt`

Compatibility plan:

- Create the smallest compile-ready `LocalWaveEngine` interface and domain model shims under `com.localwave.core.*` because no Thread 3 contract exists in this checkout.
- Keep these shims protocol-shaped and UI-safe so Thread 3 can replace them without changing UI code.
- Do not implement BLE scan/advertise/connect, crypto, packet framing, Room, or wake notification internals.
- Document the missing Thread 3 integration in `README.md`, `ARCHITECTURE_ANDROID.md`, and final notes.

## 2. Existing iOS Product Behavior Reviewed

Reviewed iOS reference files:

- `ios-localwave/LocalWave/App/AppRootView.swift`
- `ios-localwave/LocalWave/Features/Onboarding/OnboardingView.swift`
- `ios-localwave/LocalWave/Features/People/PeopleView.swift`
- `ios-localwave/LocalWave/Features/Chat/ChatView.swift`
- `ios-localwave/LocalWave/Features/Settings/SettingsView.swift`
- `ios-localwave/README.md`
- `ios-localwave/ARCHITECTURE.md`
- `ios-localwave/SECURITY.md`
- `ios-localwave/LocalWave/AppState/OnboardingViewModel.swift`
- `ios-localwave/LocalWave/AppState/PeopleViewModel.swift`
- `ios-localwave/LocalWave/AppState/ChatViewModel.swift`
- `ios-localwave/LocalWave/AppState/SettingsViewModel.swift`
- `ios-localwave/LocalWave/Core/Models/LocalWaveProtocols.swift`
- `ios-localwave/LocalWave/Core/Models/*`
- `ios-localwave/LocalWave/Mocks/MockLocalWaveEngine.swift`

iOS behavior to preserve in Android-native form:

- App root chooses onboarding or main shell based on persisted onboarding state.
- Main app has People/Frequency as the primary screen and Settings as the secondary screen.
- Onboarding steps are welcome, display name, Frequency Code, Bluetooth education, and notification education.
- People screen shows current Frequency Code, scanning status, permission banner, nearby peers, empty state, peer detail, and Wake.
- Chat screen shows peer header, reachable/unreachable banner, message list, composer, outgoing status, retry affordance where supported, and Wake.
- Settings exposes profile, Frequency Code, privacy claims, permissions, identity fingerprint, and redacted diagnostics.
- Diagnostics show mode, peer count, permission/transport state, and redacted errors only.

## 3. Android UI Architecture Plan

Visual thesis: LocalWave Android should feel like a dark, operational warehouse tool: high contrast, compact but readable, one teal accent, large touch targets, no decorative clutter.

Content plan:

- Onboarding: state the product truth and collect only display name and Frequency Code.
- People: make the current Frequency Code and nearby availability the primary workspace.
- Chat: focus on message reachability, Wake state, and delivery truth.
- Settings: expose profile, privacy, identity, permissions, diagnostics, and integration state.

Interaction thesis:

- Use Material 3 navigation, bottom app tabs for People and Settings, and pushed detail for Chat/settings subpages.
- Keep motion minimal and functional; do not depend on animation for state comprehension.
- Use state chips, banners, and icons with text so status is accessible without color.

Planned files:

- `app/build.gradle.kts`, root Gradle files, Android manifest.
- `com.localwave.app`: application, activity, app environment, app state holder, app root, nav graph.
- `com.localwave.design`: theme, spacing, components, preview data.
- `com.localwave.feature.onboarding`: route, view model, screens.
- `com.localwave.feature.people`: route, view model, screens, peer detail.
- `com.localwave.feature.chat`: route, view model, screens.
- `com.localwave.feature.settings`: route, view model, screens.
- `com.localwave.core.protocol`, `core.model`, `core.bluetooth`, `mock`: compatibility shims only.

## 4. Navigation Plan

Use Navigation Compose if dependency resolution succeeds.

Routes:

- `onboarding/welcome`
- `onboarding/name`
- `onboarding/frequency`
- `onboarding/bluetooth`
- `onboarding/notifications`
- `people`
- `chat/{peerId}`
- `settings`
- `settings/privacy`
- `settings/identity`
- `settings/channel`
- `settings/diagnostics`
- `settings/permissions`

Bottom navigation:

- People
- Settings

Chat and Settings detail screens are pushed destinations, not bottom tabs.

## 5. ViewModel And State Plan

Use ViewModel plus StateFlow.

App state:

- Persist onboarding completion, display name, last Frequency Code, and education flags with DataStore Preferences.
- Hold current `LocalWaveEngine`, permission controller, and engine mode in `AppEnvironment`.
- Start engine after onboarding completes.

Onboarding:

- Validate display name: trimmed non-empty, max 32 characters.
- Validate Frequency Code: trimmed/collapsed, normalized uppercase, minimum 3 characters.
- Request Bluetooth and notification permissions through UI-safe wrappers.

People:

- Observe peers and transport state from `LocalWaveEngine`.
- Sort available and connecting peers first.
- Expose permission banners, active mode state, wake state per peer, and channel display.

Chat:

- Observe messages by peer.
- Block empty/whitespace sends and enforce 4 KB message limit.
- Call `sendMessage()` and `sendWake()` on the engine.
- Present pending/sent/delivered/failed states from model.

Settings:

- Expose display name, channel, identity fingerprint, permission status, transport diagnostics, engine mode, peer count.
- Redact diagnostics before UI display.

## 6. Permission UX Plan

Create a UI-safe permission controller that can be backed by Thread 3 `BlePermissionManager` later.

States to display:

- Bluetooth permission missing
- Bluetooth disabled
- Scan permission missing
- Advertise permission missing
- Connect permission missing
- Notification permission missing
- BLE unsupported
- BLE advertiser unsupported
- Active mode unavailable
- Background/battery warning

Required copy:

- "Bluetooth is required to find nearby LocalWave users."
- "Notifications are optional, but Wake alerts need notification permission."
- "Wake works best when both phones are nearby, Bluetooth is on, and the app has permission."
- "Android battery settings may affect background discovery."

Forbidden copy checks:

- No real RF tuning implication.
- No guaranteed Wake.
- No emergency/public-safety reliability.
- No "military-grade" or impossible interception claims.

## 7. Accessibility Plan

- Minimum 48 dp touch targets for buttons, rows, and icon actions.
- TalkBack labels on Wake buttons, peer rows, signal indicator, message status, diagnostics, and permission banners.
- Support Material font scaling and avoid fixed text heights.
- Do not rely only on color for status; pair colors with text/icons.
- Use semantic headings for major screen sections where practical.
- Keep reduced-motion behavior simple by limiting non-essential animations.
- Verify contrast in dark and light modes through Material color roles.

## 8. Compose Testing Plan

Unit tests:

- Onboarding display name validation.
- Onboarding Frequency Code validation.
- People list updates from Flow.
- Wake unavailable when permission missing.
- Wake success calls engine.
- Chat empty send blocked.
- Chat valid send calls engine.
- Chat 4 KB limit blocks oversized text.
- Settings exposes redacted diagnostics and fingerprint.
- Switch channel calls engine and updates UI state.

Compose UI tests where practical:

- Onboarding welcome renders.
- Display name validation error appears.
- Frequency Code explanation appears.
- People empty state appears.
- Peer row opens Chat.
- Chat composer sends a mock message.
- Wake button state changes.
- Settings privacy screen opens.
- Diagnostics does not show forbidden sample secrets.

## 9. Emulator QA Plan

Use `test-android-apps:android-emulator-qa`.

Steps:

- `adb devices` to detect an emulator/device.
- `./gradlew :app:installDebug` when a target is available.
- Launch resolved main activity.
- Walk onboarding with mock engine.
- Verify People screen with mock peers.
- Open Chat and send a mock message.
- Open Settings, Privacy, Identity, Permissions, and Diagnostics.
- Capture screenshot(s) and crash logs where the emulator is available.

## 10. Risks And Blockers

- Android Thread 3 output is absent in this checkout; real BLE, crypto, persistence, wake internals, permission foundation, and `LocalWaveEngine` implementation cannot be verified.
- The Android project itself is absent, so Thread 4 must scaffold a compile-ready Android app before UI work can start.
- Real Android-to-Android and Android-to-iPhone behavior cannot be claimed without Thread 3 transport and physical device testing.
- Compose/UI tests may depend on local Android SDK availability and emulator stability.
- If Gradle dependencies are not cached and network resolution fails, build verification may block.

## 11. Verification Commands To Run

Run from `/Users/sanks04/Desktop/Random-1/android-localwave`:

- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew lintDebug`
- `./gradlew connectedAndroidTest` only if an emulator/device is available and stable.

Security and privacy checks:

- `rg -n "INTERNET|firebase|supabase|analytics|tracking|military-grade|guaranteed|emergency|public-safety|radio tuner|RF tuner|anonymous forever|always works" .`
- Inspect `app/src/main/AndroidManifest.xml` for no `INTERNET` permission.
- Inspect diagnostics and logs for no plaintext messages, private keys, shared secrets, full Frequency Codes, or sensitive notification payloads.

## Implementation Checklist

### Task 1: Scaffold Android Project

**Files:**
- Create root Gradle files.
- Create app Gradle file and Android manifest.
- Create `MainActivity` and `LocalWaveApplication`.

- [ ] Add minimal Android Gradle project with Compose Material 3.
- [ ] Add app namespace `com.localwave`.
- [ ] Add permissions for Bluetooth and notifications only; do not add `INTERNET`.
- [ ] Run `./gradlew tasks` to verify Gradle project loads.

### Task 2: Add Core Compatibility Shims

**Files:**
- Create `core/model/*`.
- Create `core/protocol/LocalWaveEngine.kt`.
- Create `core/bluetooth/PermissionModels.kt`.
- Create `mock/MockLocalWaveEngine.kt`.

- [ ] Add value classes and enums matching the iOS protocol shape.
- [ ] Add mock engine with Flow-backed peers, messages, transport state, wake, and message simulation.
- [ ] Add unit tests for channel and message validation before production logic.

### Task 3: Add App State And Persistence

**Files:**
- Create `app/AppEnvironment.kt`.
- Create `app/AppStateHolder.kt`.
- Create `app/LocalWaveApp.kt`.
- Create `app/LocalWaveNavGraph.kt`.

- [ ] Add DataStore-backed app state.
- [ ] Add app environment factory that defaults to mock engine until Thread 3 real engine is available.
- [ ] Add app root that chooses onboarding or main shell.

### Task 4: Add Design System And Components

**Files:**
- Create `design/*` and `design/components/*`.

- [ ] Add Material 3 dark-first theme.
- [ ] Add reusable LocalWave components.
- [ ] Add previews with mock data for major components.

### Task 5: Add Onboarding Flow

**Files:**
- Create `feature/onboarding/*`.
- Add unit tests.

- [ ] Write failing validation tests.
- [ ] Implement ViewModel and screens.
- [ ] Persist completed onboarding state.

### Task 6: Add People Flow

**Files:**
- Create `feature/people/*`.
- Add unit and UI tests.

- [ ] Write failing Flow/wake tests.
- [ ] Implement People screen, peer rows, empty state, permission banners, and peer detail sheet.
- [ ] Wire peer tap to Chat route.

### Task 7: Add Chat Flow

**Files:**
- Create `feature/chat/*`.
- Add unit and UI tests.

- [ ] Write failing send/wake/limit tests.
- [ ] Implement Chat screen, message list, bubbles, composer, Wake states, and unreachable banner.

### Task 8: Add Settings, Privacy, Identity, Permissions, Diagnostics

**Files:**
- Create `feature/settings/*`.
- Add unit and UI tests.

- [ ] Write failing diagnostics redaction tests.
- [ ] Implement settings list and detail screens.
- [ ] Verify forbidden diagnostic samples are not rendered.

### Task 9: Documentation

**Files:**
- Create or update `README.md`.
- Create or update `ARCHITECTURE_ANDROID.md`.
- Create or update `TESTING_ANDROID.md`.

- [ ] Document mock vs real engine.
- [ ] Document onboarding, permissions, and Thread 3 integration.
- [ ] Document manual Android-Android and Android-iPhone UI flow without claiming transport success.

### Task 10: Verification And Final Security Pass

- [ ] Run `./gradlew test`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Run `./gradlew lintDebug`.
- [ ] Run emulator QA if a device is available.
- [ ] Run Codex Security final pass over the diff/config/logging/copy.
- [ ] Report exact command results and remaining blockers.

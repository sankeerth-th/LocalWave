# LocalWave Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or task-by-task execution discipline. Thread 1 owns engine files under `LocalWave/Core` and core tests. Thread 2 owns SwiftUI files under `LocalWave/App`, `LocalWave/DesignSystem`, `LocalWave/Features`, app-state view models, and view-model tests. Both threads depend on protocols in `LocalWave/Core/Models/LocalWaveProtocols.swift`.

**Goal:** Build a production-quality offline iOS Bluetooth messaging app with no signup, no backend, encrypted message envelopes, local persistence, Apple-native UI, and tests.

**Architecture:** The UI depends on protocol-based services injected through `AppEnvironment`. The engine owns channel derivation, identity keys, packet framing, CoreBluetooth central/peripheral roles, repositories, and notification handling. BLE is the only production transport; mock transport exists only for simulator tests/previews.

**Tech Stack:** SwiftUI, Observation, CoreBluetooth, CryptoKit, Security/Keychain, UserNotifications, XCTest, XcodeGen-generated iOS project targeting iOS 17+.

---

## Visual Thesis

LocalWave should feel like a quiet Apple system tool for night-shift teams: dark-mode first, glassy native materials, crisp operational status, and restrained motion around scanning, wake, and delivery state.

## Content Plan

The app opens into onboarding when identity/channel setup is missing. The main shell has Frequency/People, Chat, and Settings tabs. Frequency copy is explicit that the channel is a local Bluetooth Frequency Code, not an analog RF tuner, cellular, internet, or emergency radio.

## Interaction Thesis

Peer rows appear with a spring transition as BLE discovery updates. The active channel pill has a subtle scanning ring. Wake has a short pulse and degrades to clear unavailable states when permissions or reachability prevent delivery.

## Thread Contract

Thread 1 exposes:
- `LocalWaveEngineProtocol`
- `CryptoServiceProtocol`
- `MessageRepositoryProtocol`
- `PeerRepositoryProtocol`

Thread 2 consumes only those protocols through `AppEnvironment`, using `MockLocalWaveEngine` for previews and simulator-only flows.

## Verification

Run:
- `xcodegen generate`
- `xcodebuild test -project LocalWave.xcodeproj -scheme LocalWave -destination 'platform=iOS Simulator,name=iPhone 17'`

Physical BLE verification requires two iPhones because CoreBluetooth advertising/scanning behavior cannot be fully validated in simulator.

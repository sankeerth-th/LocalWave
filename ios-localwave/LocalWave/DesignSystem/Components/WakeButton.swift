import SwiftUI

public enum WakeButtonState: Equatable, Sendable {
    case idle
    case sending
    case sent
    case unavailable
    case permissionNeeded
    case failed
}

struct WakeButton: View {
    let state: WakeButtonState
    var compact = false
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: action) {
            HStack(spacing: compact ? 0 : 8) {
                image
                if !compact {
                    Text(title)
                        .fontWeight(.semibold)
                }
            }
            .frame(minWidth: compact ? 42 : 92, minHeight: 42)
            .padding(.horizontal, compact ? 0 : 12)
        }
        .buttonStyle(.borderedProminent)
        .tint(tint)
        .disabled(isDisabled)
        .accessibilityLabel(accessibilityTitle)
        .accessibilityHint("Wake sends a local Bluetooth ping when the other phone is reachable.")
        .animation(LWAnimations.quick(reduceMotion: reduceMotion), value: state)
    }

    @ViewBuilder
    private var image: some View {
        switch state {
        case .sending:
            ProgressView()
                .controlSize(.small)
        case .sent:
            Image(systemName: "checkmark")
        case .failed:
            Image(systemName: LWSymbols.failed)
        case .permissionNeeded:
            Image(systemName: "lock.fill")
        case .unavailable:
            Image(systemName: "bell.slash.fill")
        case .idle:
            Image(systemName: LWSymbols.wake)
        }
    }

    private var title: String {
        switch state {
        case .idle: return "Wake"
        case .sending: return "Sending"
        case .sent: return "Sent"
        case .unavailable: return "Away"
        case .permissionNeeded: return "Permission"
        case .failed: return "Retry"
        }
    }

    private var accessibilityTitle: String {
        switch state {
        case .idle: return "Send Wake"
        case .sending: return "Sending Wake"
        case .sent: return "Wake sent"
        case .unavailable: return "Wake unavailable"
        case .permissionNeeded: return "Bluetooth permission needed for Wake"
        case .failed: return "Wake failed, retry"
        }
    }

    private var tint: Color {
        switch state {
        case .sent: return LWTheme.success
        case .failed: return LWTheme.danger
        case .unavailable, .permissionNeeded: return Color.secondary
        default: return LWTheme.accent
        }
    }

    private var isDisabled: Bool {
        state == .sending || state == .sent || state == .unavailable || state == .permissionNeeded
    }
}


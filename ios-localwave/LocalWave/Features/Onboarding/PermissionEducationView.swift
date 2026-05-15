import SwiftUI

enum PermissionEducationKind {
    case bluetooth
    case notifications

    var title: String {
        switch self {
        case .bluetooth: return "Bluetooth finds nearby teammates"
        case .notifications: return "Notifications make Wake useful"
        }
    }

    var message: String {
        switch self {
        case .bluetooth:
            return "Bluetooth permission is needed to discover nearby LocalWave users and exchange local messages."
        case .notifications:
            return "Notifications are optional, but needed for Wake alerts when iOS allows them. Wake is not guaranteed and depends on reachability and system rules."
        }
    }

    var symbol: String {
        switch self {
        case .bluetooth: return LWSymbols.bluetooth
        case .notifications: return LWSymbols.wake
        }
    }

    var buttonTitle: String {
        switch self {
        case .bluetooth: return "Check Bluetooth"
        case .notifications: return "Check Notifications"
        }
    }
}

struct PermissionEducationView: View {
    let kind: PermissionEducationKind
    let stateText: String
    let requestAction: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: kind.symbol)
                .font(.system(size: 62, weight: .semibold))
                .foregroundStyle(LWTheme.accent)
                .accessibilityHidden(true)

            SectionHeader(title: kind.title, subtitle: kind.message)
                .multilineTextAlignment(.center)

            Button(kind.buttonTitle, action: requestAction)
                .buttonStyle(.bordered)
                .accessibilityHint("Shows the current permission state when a permission helper is available.")

            Text("Current state: \(stateText)")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding(LWTheme.screenPadding)
    }
}

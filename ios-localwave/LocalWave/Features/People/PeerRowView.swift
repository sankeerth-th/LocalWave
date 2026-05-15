import SwiftUI

struct PeerRowView: View {
    let peer: PeerProfile
    let wakeState: WakeButtonState
    let wakeAction: () -> Void
    let detailAction: () -> Void

    var body: some View {
        GlassCard {
            HStack(spacing: 12) {
                PeerAvatar(name: peer.displayName)
                VStack(alignment: .leading, spacing: 5) {
                    Text(peer.displayName)
                        .font(.headline)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(peer.state.rawValue)
                        Text("•")
                        Text(peer.lastSeen.relativeDisplay)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                SignalStrengthView(rssi: peer.rssi)
                WakeButton(state: wakeState, compact: true, action: wakeAction)
                    .simultaneousGesture(TapGesture().onEnded { })
                Button(action: detailAction) {
                    Image(systemName: "info.circle")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Show \(peer.displayName) details")
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(peer.displayName), \(peer.state.rawValue), \(peer.lastSeen.relativeDisplay)")
    }
}

extension Date {
    var relativeDisplay: String {
        let interval = abs(timeIntervalSinceNow)
        switch interval {
        case 0..<45: return "Now"
        case 45..<3600:
            let minutes = max(1, Int(interval / 60))
            return "\(minutes)m ago"
        default:
            let hours = max(1, Int(interval / 3600))
            return "\(hours)h ago"
        }
    }
}


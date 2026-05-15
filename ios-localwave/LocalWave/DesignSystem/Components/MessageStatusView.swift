import SwiftUI

struct MessageStatusView: View {
    let status: MessageStatus

    var body: some View {
        Label(title, systemImage: symbol)
            .font(.caption2)
            .foregroundStyle(color)
            .labelStyle(.titleAndIcon)
            .accessibilityLabel("Message \(title)")
    }

    private var title: String {
        switch status {
        case .pending: return "Pending"
        case .sent: return "Sent"
        case .delivered: return "Delivered"
        case .failed: return "Failed"
        }
    }

    private var symbol: String {
        switch status {
        case .pending: return "clock"
        case .sent: return LWSymbols.sent
        case .delivered: return LWSymbols.delivered
        case .failed: return LWSymbols.failed
        }
    }

    private var color: Color {
        status == .failed ? LWTheme.danger : .secondary
    }
}


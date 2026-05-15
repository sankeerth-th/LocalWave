import SwiftUI

struct FrequencyHeaderView: View {
    let channel: ChannelCode
    let status: String
    let isScanning: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Frequency Code")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            HStack(alignment: .center, spacing: 12) {
                FrequencyPill(channel: channel, isScanning: isScanning)
                Spacer()
            }
            Label(status, systemImage: isScanning ? "dot.radiowaves.left.and.right" : "checkmark.circle")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("Logical private Bluetooth channel. Not analog RF, cellular, Wi-Fi, or internet.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

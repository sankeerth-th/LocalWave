import SwiftUI

struct FrequencyHeaderView: View {
    let channel: ChannelCode
    let status: String
    let isScanning: Bool
    var mode: LocalWaveAppMode = .real

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Channel")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    FrequencyPill(channel: channel, isScanning: isScanning)
                }
                Spacer()
            }
            Label(status, systemImage: isScanning ? "dot.radiowaves.left.and.right" : "checkmark.circle")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

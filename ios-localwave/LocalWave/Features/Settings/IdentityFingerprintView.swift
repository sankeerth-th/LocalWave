import SwiftUI

struct IdentityFingerprintView: View {
    let title: String
    let fingerprint: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Text(fingerprint.redactedDiagnostic)
                        .font(.title3.monospaced().weight(.semibold))
                        .textSelection(.enabled)
                    Text("Compare fingerprints in person for high-trust teams. LocalWave does not upload identity keys.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 6)
            }
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
            }
        }
    }
}


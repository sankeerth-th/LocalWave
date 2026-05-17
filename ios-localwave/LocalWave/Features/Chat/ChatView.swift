import SwiftUI
import UniformTypeIdentifiers
import UIKit

private extension UTType {
    static let localWavePackage = UTType(exportedAs: "com.localwave.package", conformingTo: .data)
}

struct ChatView: View {
    @StateObject private var viewModel: ChatViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showingDirectImporter = false
    @State private var showingShareImporter = false
    @State private var showingPackageImporter = false

    init(viewModel: ChatViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        VStack(spacing: 0) {
            ChatHeaderView(peer: viewModel.peer, wakeState: viewModel.wakeState) {
                Task { await viewModel.sendWake() }
            }

            if !viewModel.isPeerReachable {
                PermissionBanner(
                    text: "Messages will send when this person is nearby again.",
                    systemImage: "clock.badge.exclamationmark"
                )
                .padding(.horizontal)
                .padding(.bottom, 8)
            }

            MessageListView(messages: viewModel.messages) { message in
                Task { await viewModel.retry(message) }
            }

            if let latestTransfer = viewModel.transferRecords.first {
                TransferStatusBanner(transfer: latestTransfer)
                    .padding(.horizontal)
                    .padding(.bottom, 4)
            }

            AttachmentActionsView(
                isTransferring: viewModel.isTransferring,
                sendDirect: { showingDirectImporter = true },
                sharePackage: { showingShareImporter = true },
                importPackage: { showingPackageImporter = true }
            )

            MessageComposerView(text: $viewModel.draft, canSend: viewModel.canSend) {
                Task { await viewModel.sendDraft() }
            }
        }
        .background(LWTheme.background)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    dismiss()
                } label: {
                    Label("Back", systemImage: "chevron.left")
                }
                .accessibilityLabel("Back")
            }
        }
        .task { viewModel.start() }
        .onDisappear { viewModel.stop() }
        .fileImporter(isPresented: $showingDirectImporter, allowedContentTypes: [.data, .image, .movie], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first {
                Task { await viewModel.sendAttachment(from: url) }
            }
        }
        .fileImporter(isPresented: $showingShareImporter, allowedContentTypes: [.data, .image, .movie], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first {
                Task { await viewModel.exportEncryptedSharePackage(from: url) }
            }
        }
        .fileImporter(isPresented: $showingPackageImporter, allowedContentTypes: [.localWavePackage, .data], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first {
                Task { await viewModel.importEncryptedSharePackage(from: url) }
            }
        }
        .sheet(item: $viewModel.sharePackageURL) { item in
            ActivityView(items: [item.url])
        }
        .alert("LocalWave", isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { _ in })) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }
}

private struct TransferStatusBanner: View {
    let transfer: TransferRecord

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 2) {
                Text(statusText)
                    .font(.footnote.weight(.semibold))
                Text(routeText)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var statusText: String {
        switch transfer.status {
        case .queued, .negotiating, .announced:
            return "Preparing \(transfer.fileName)"
        case .accepted, .sessionNegotiated, .sending, .transferring:
            return "Sending \(transfer.fileName)"
        case .manifestReceived:
            return "Receiving encrypted manifest"
        case .waitingForPeer:
            return "Waiting for peer receipt"
        case .waitingForRelay:
            return "Waiting for relay"
        case .verifying, .reconstructing, .decrypting, .importing:
            return "Verifying secure transfer"
        case .exported:
            return "Encrypted package ready to share"
        case .completed, .delivered:
            return "Downloaded"
        case .pending:
            return "Pending"
        case .failed:
            return transfer.failureReason ?? "Transfer failed"
        case .expired:
            return "Transfer expired"
        case .cancelled:
            return "Transfer cancelled"
        }
    }

    private var routeText: String {
        switch transfer.route {
        case .l2cap:
            return "Route: Direct L2CAP"
        case .gatt:
            return "Route: GATT control only"
        case .nativeShare:
            return "Route: Native Share package"
        case .fixedRelay:
            return "Route: Fixed relay"
        case .phoneRelay:
            return "Route: Best-effort phone relay"
        }
    }

    private var iconName: String {
        switch transfer.status {
        case .failed, .expired:
            return "exclamationmark.triangle.fill"
        case .completed, .delivered, .exported:
            return "checkmark.circle.fill"
        default:
            return "arrow.up.arrow.down.circle.fill"
        }
    }

    private var color: Color {
        switch transfer.status {
        case .failed, .expired:
            return .orange
        case .completed, .delivered, .exported:
            return .green
        default:
            return .accentColor
        }
    }
}

private struct AttachmentActionsView: View {
    let isTransferring: Bool
    let sendDirect: () -> Void
    let sharePackage: () -> Void
    let importPackage: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button(action: sendDirect) {
                Label("Send File", systemImage: "paperclip")
            }
            Button(action: sharePackage) {
                Label("Share Package", systemImage: "square.and.arrow.up")
            }
            Button(action: importPackage) {
                Label("Import", systemImage: "square.and.arrow.down")
            }
        }
        .buttonStyle(.bordered)
        .font(.callout)
        .padding(.horizontal)
        .padding(.top, 8)
        .disabled(isTransferring)
    }
}

private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview("Chat") {
    NavigationStack {
        ChatView(viewModel: ChatViewModel(environment: .mock, peer: PreviewData.peers[0]))
    }
}

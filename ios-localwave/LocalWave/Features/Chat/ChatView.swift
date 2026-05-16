import SwiftUI
import UniformTypeIdentifiers
import UIKit

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
        .fileImporter(isPresented: $showingPackageImporter, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in
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

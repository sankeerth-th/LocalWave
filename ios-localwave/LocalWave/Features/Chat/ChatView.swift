import SwiftUI

struct ChatView: View {
    @StateObject private var viewModel: ChatViewModel
    @Environment(\.dismiss) private var dismiss

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
        .alert("LocalWave", isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { _ in })) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }
}

#Preview("Chat") {
    NavigationStack {
        ChatView(viewModel: ChatViewModel(environment: .mock, peer: PreviewData.peers[0]))
    }
}

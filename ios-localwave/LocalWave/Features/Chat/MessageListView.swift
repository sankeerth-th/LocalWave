import SwiftUI

struct MessageListView: View {
    let messages: [ChatMessage]
    let retry: (ChatMessage) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if messages.isEmpty {
                        EmptyStateView(
                            title: "No messages yet.",
                            message: "Send a local message when this person is nearby.",
                            systemImage: "message"
                        )
                    } else {
                        ForEach(messages) { message in
                            MessageBubble(message: message) {
                                retry(message)
                            }
                            .id(message.id)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 12)
            }
            .onChange(of: messages.count) { _, _ in
                guard let id = messages.last?.id else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(id, anchor: .bottom)
                }
            }
        }
    }
}


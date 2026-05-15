import Foundation

enum PreviewData {
    static let channel = ChannelCode.sample
    static let identity = LocalIdentity(
        peerId: "local-preview",
        displayName: "Maya",
        agreementPublicKey: Data([1, 2, 3, 4]),
        signingPublicKey: Data([5, 6, 7, 8]),
        fingerprint: "A7C9 14D2 8B31 5F04"
    )

    static let peers: [PeerProfile] = [
        PeerProfile(id: "peer-ana", displayName: "Ana Ruiz", fingerprint: "7B42 91FA 0C44 3E12", rssi: -42, lastSeen: Date(), state: .available),
        PeerProfile(id: "peer-dock", displayName: "Dock Lead", fingerprint: "11E9 AC07 4D88 22B1", rssi: -63, lastSeen: Date().addingTimeInterval(-86), state: .connecting),
        PeerProfile(id: "peer-sam", displayName: "Sam Patel", fingerprint: "CB10 884A 76E2 9D05", rssi: -76, lastSeen: Date().addingTimeInterval(-380), state: .recentlySeen)
    ]

    static func messages(peerId: PeerID) -> [ChatMessage] {
        [
            ChatMessage(peerId: peerId, text: "Can you check bay three?", sentAt: Date().addingTimeInterval(-300), direction: .incoming, status: .delivered),
            ChatMessage(peerId: peerId, text: "On it. I will ping when clear.", sentAt: Date().addingTimeInterval(-240), direction: .outgoing, status: .delivered),
            ChatMessage(peerId: peerId, text: "Copy.", sentAt: Date().addingTimeInterval(-210), direction: .incoming, status: .delivered)
        ]
    }
}


import Foundation

public actor ConnectionScheduler {
    private var retryDates: [PeerID: Date] = [:]

    public init() {}

    public func shouldAttempt(peerId: PeerID, now: Date = Date()) -> Bool {
        guard let retryDate = retryDates[peerId] else { return true }
        return now >= retryDate
    }

    public func scheduleRetry(peerId: PeerID, after interval: TimeInterval = 5) {
        retryDates[peerId] = Date().addingTimeInterval(interval)
    }

    public func clear(peerId: PeerID) {
        retryDates.removeValue(forKey: peerId)
    }
}

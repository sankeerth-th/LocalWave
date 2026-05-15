import CryptoKit
import Foundation

public struct GATTProfile: Equatable, Sendable {
    public var serviceUUID: UUID
    public var packetCharacteristicUUID: UUID
    public var presenceCharacteristicUUID: UUID
    public var wakeCharacteristicUUID: UUID

    public init(
        serviceUUID: UUID,
        packetCharacteristicUUID: UUID,
        presenceCharacteristicUUID: UUID,
        wakeCharacteristicUUID: UUID
    ) {
        self.serviceUUID = serviceUUID
        self.packetCharacteristicUUID = packetCharacteristicUUID
        self.presenceCharacteristicUUID = presenceCharacteristicUUID
        self.wakeCharacteristicUUID = wakeCharacteristicUUID
    }
}

public struct ChannelIdentity: Equatable, Sendable {
    public var channelId: UUID
    public var discoveryTag: Data
    public var hkdfSalt: Data
    public var gatt: GATTProfile

    public init(channelId: UUID, discoveryTag: Data, hkdfSalt: Data, gatt: GATTProfile) {
        self.channelId = channelId
        self.discoveryTag = discoveryTag
        self.hkdfSalt = hkdfSalt
        self.gatt = gatt
    }
}

public enum ChannelKeyDerivation {
    private static let namespace = "LocalWave.Channel.v1"

    public static func derive(from channel: ChannelCode) -> ChannelIdentity {
        let normalized = channel.normalized
        let channelId = uuid(label: "channel-id", normalized: normalized)
        let serviceUUID = uuid(label: "gatt-service", normalized: normalized)
        let packetUUID = uuid(label: "gatt-packet", normalized: normalized)
        let presenceUUID = uuid(label: "gatt-presence", normalized: normalized)
        let wakeUUID = uuid(label: "gatt-wake", normalized: normalized)
        let discoveryDigest = SHA256.hash(data: data("discovery-tag", normalized))
        let saltDigest = SHA256.hash(data: data("hkdf-salt", normalized))

        return ChannelIdentity(
            channelId: channelId,
            discoveryTag: Data(discoveryDigest.prefix(8)),
            hkdfSalt: Data(saltDigest),
            gatt: GATTProfile(
                serviceUUID: serviceUUID,
                packetCharacteristicUUID: packetUUID,
                presenceCharacteristicUUID: presenceUUID,
                wakeCharacteristicUUID: wakeUUID
            )
        )
    }

    private static func uuid(label: String, normalized: String) -> UUID {
        let digest = SHA256.hash(data: data(label, normalized))
        var bytes = Array(digest.prefix(16))
        bytes[6] = (bytes[6] & 0x0F) | 0x50
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return NSUUID(uuidBytes: bytes) as UUID
    }

    private static func data(_ label: String, _ normalized: String) -> Data {
        Data("\(namespace).\(label).\(normalized)".utf8)
    }
}

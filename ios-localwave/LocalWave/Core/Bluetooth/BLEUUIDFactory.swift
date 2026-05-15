import CoreBluetooth
import Foundation

public enum BLEUUIDFactory {
    public static func profile(for channel: ChannelCode) -> BLECBUUIDProfile {
        BLECBUUIDProfile(profile: ChannelKeyDerivation.derive(from: channel).gatt)
    }
}

public struct BLECBUUIDProfile: Equatable {
    public var serviceUUID: CBUUID
    public var packetCharacteristicUUID: CBUUID
    public var presenceCharacteristicUUID: CBUUID
    public var wakeCharacteristicUUID: CBUUID

    public init(profile: GATTProfile) {
        self.serviceUUID = CBUUID(nsuuid: profile.serviceUUID)
        self.packetCharacteristicUUID = CBUUID(nsuuid: profile.packetCharacteristicUUID)
        self.presenceCharacteristicUUID = CBUUID(nsuuid: profile.presenceCharacteristicUUID)
        self.wakeCharacteristicUUID = CBUUID(nsuuid: profile.wakeCharacteristicUUID)
    }
}

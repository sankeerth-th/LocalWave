import XCTest
@testable import LocalWave

final class ChannelDerivationTests: XCTestCase {
    func testDerivationIsStableForEquivalentCodes() throws {
        let first = try ChannelKeyDerivation.derive(from: ChannelCode(" dock-a-17 "))
        let second = try ChannelKeyDerivation.derive(from: ChannelCode("DOCK-A-17"))

        XCTAssertEqual(first.channelId, second.channelId)
        XCTAssertEqual(first.gatt.serviceUUID, second.gatt.serviceUUID)
        XCTAssertEqual(first.gatt.packetCharacteristicUUID, second.gatt.packetCharacteristicUUID)
        XCTAssertEqual(first.discoveryTag, second.discoveryTag)
    }

    func testDifferentCodesProduceDifferentDiscoveryMaterial() throws {
        let first = try ChannelKeyDerivation.derive(from: ChannelCode("DOCK-A-17"))
        let second = try ChannelKeyDerivation.derive(from: ChannelCode("DOCK-B-17"))

        XCTAssertNotEqual(first.channelId, second.channelId)
        XCTAssertNotEqual(first.gatt.serviceUUID, second.gatt.serviceUUID)
        XCTAssertNotEqual(first.discoveryTag, second.discoveryTag)
    }
}

import XCTest
@testable import LocalWave

final class PacketFramingTests: XCTestCase {
    func testChunksAndReassemblesPayload() throws {
        let framer = BLEPacketFramer(maxFrameBytes: 96, maxPayloadBytes: 4096)
        let reassembler = BLEPacketReassembler()
        let body = Data((0..<512).map { UInt8($0 % 251) })
        let packetId = UUID()
        let conversationId = UUID()

        let chunks = try framer.frame(body: body, kind: .message, packetId: packetId, conversationId: conversationId)

        XCTAssertGreaterThan(chunks.count, 1)
        for chunk in chunks.reversed() {
            let result = try reassembler.receive(chunk)
            if chunk.chunkIndex == 0 {
                XCTAssertEqual(result?.body, body)
                XCTAssertEqual(result?.kind, .message)
                XCTAssertEqual(result?.packetId, packetId)
            } else {
                XCTAssertNil(result)
            }
        }
    }

    func testOversizedPayloadThrows() throws {
        let framer = BLEPacketFramer(maxFrameBytes: 96, maxPayloadBytes: 128)
        let body = Data(repeating: 0x44, count: 129)

        XCTAssertThrowsError(try framer.frame(body: body, kind: .message, packetId: UUID(), conversationId: UUID())) { error in
            XCTAssertEqual(error as? LocalWaveError, .oversizedMessage(limitBytes: 128))
        }
    }
}

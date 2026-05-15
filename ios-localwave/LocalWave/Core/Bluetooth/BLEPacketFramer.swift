import Foundation

public struct ReassembledPacket: Equatable, Sendable {
    public var packetId: PacketID
    public var conversationId: ConversationID
    public var kind: TransportPacketKind
    public var body: Data
}

public struct BLEPacketFramer: Sendable {
    public static let headerBytes = 46
    public static let defaultMaxFrameBytes = 182
    public static let defaultMaxPayloadBytes = 16 * 1024

    public var maxFrameBytes: Int
    public var maxPayloadBytes: Int

    public init(maxFrameBytes: Int = Self.defaultMaxFrameBytes, maxPayloadBytes: Int = Self.defaultMaxPayloadBytes) {
        self.maxFrameBytes = maxFrameBytes
        self.maxPayloadBytes = maxPayloadBytes
    }

    public func frame(
        body: Data,
        kind: TransportPacketKind,
        packetId: PacketID = UUID(),
        conversationId: ConversationID
    ) throws -> [TransportPacket] {
        guard body.count <= maxPayloadBytes else {
            throw LocalWaveError.oversizedMessage(limitBytes: maxPayloadBytes)
        }
        let maxChunkBytes = maxFrameBytes - Self.headerBytes
        guard maxChunkBytes > 0 else { throw LocalWaveError.malformedPacket }

        let chunkCount = max(1, Int(ceil(Double(body.count) / Double(maxChunkBytes))))
        guard chunkCount <= Int(UInt16.max), body.count <= Int(UInt32.max) else {
            throw LocalWaveError.oversizedMessage(limitBytes: maxPayloadBytes)
        }

        let checksum = CRC32.checksum(body)
        return (0..<chunkCount).map { index in
            let start = index * maxChunkBytes
            let end = min(start + maxChunkBytes, body.count)
            let chunkBody = start < end ? body.subdata(in: start..<end) : Data()
            return TransportPacket(
                id: packetId,
                conversationId: conversationId,
                kind: kind,
                chunkIndex: UInt16(index),
                chunkCount: UInt16(chunkCount),
                bodyLength: UInt32(body.count),
                checksum: checksum,
                body: chunkBody
            )
        }
    }

    public func chunk(
        body: Data,
        kind: TransportPacketKind,
        packetId: PacketID = UUID(),
        conversationId: ConversationID = UUID()
    ) throws -> [TransportPacket] {
        try frame(body: body, kind: kind, packetId: packetId, conversationId: conversationId)
    }

    public func reassemble(_ packets: [TransportPacket]) throws -> Data {
        let reassembler = BLEPacketReassembler()
        for packet in packets.sorted(by: { $0.chunkIndex < $1.chunkIndex }) {
            if let result = try reassembler.receive(packet) {
                return result.body
            }
        }
        throw LocalWaveError.malformedPacket
    }

    public func encode(_ packet: TransportPacket) -> Data {
        var data = Data()
        data.append(TransportPacket.version)
        data.append(packet.id.uuidBytes)
        data.append(packet.conversationId.uuidBytes)
        data.append(packet.kind.rawValue)
        data.append(packet.chunkIndex.bigEndianData)
        data.append(packet.chunkCount.bigEndianData)
        data.append(packet.bodyLength.bigEndianData)
        data.append(packet.checksum.bigEndianData)
        data.append(packet.body)
        return data
    }

    public func decode(_ data: Data) throws -> TransportPacket {
        var cursor = DataCursor(data: data)
        let version = try cursor.readUInt8()
        guard version == TransportPacket.version else { throw LocalWaveError.malformedPacket }
        let packetId = try cursor.readUUID()
        let conversationId = try cursor.readUUID()
        guard let kind = TransportPacketKind(rawValue: try cursor.readUInt8()) else {
            throw LocalWaveError.malformedPacket
        }
        let chunkIndex = try cursor.readUInt16()
        let chunkCount = try cursor.readUInt16()
        let bodyLength = try cursor.readUInt32()
        let checksum = try cursor.readUInt32()
        let body = cursor.remainingData()

        guard chunkCount > 0, chunkIndex < chunkCount else {
            throw LocalWaveError.malformedPacket
        }

        return TransportPacket(
            id: packetId,
            conversationId: conversationId,
            kind: kind,
            chunkIndex: chunkIndex,
            chunkCount: chunkCount,
            bodyLength: bodyLength,
            checksum: checksum,
            body: body
        )
    }
}

public final class BLEPacketReassembler {
    private struct Accumulator {
        var conversationId: ConversationID
        var kind: TransportPacketKind
        var chunkCount: UInt16
        var bodyLength: UInt32
        var checksum: UInt32
        var chunks: [UInt16: Data]
    }

    private var accumulators: [PacketID: Accumulator] = [:]

    public init() {}

    public func receive(_ packet: TransportPacket) throws -> ReassembledPacket? {
        guard packet.chunkCount > 0, packet.chunkIndex < packet.chunkCount else {
            throw LocalWaveError.malformedPacket
        }

        var accumulator = accumulators[packet.id] ?? Accumulator(
            conversationId: packet.conversationId,
            kind: packet.kind,
            chunkCount: packet.chunkCount,
            bodyLength: packet.bodyLength,
            checksum: packet.checksum,
            chunks: [:]
        )

        guard accumulator.conversationId == packet.conversationId,
              accumulator.kind == packet.kind,
              accumulator.chunkCount == packet.chunkCount,
              accumulator.bodyLength == packet.bodyLength,
              accumulator.checksum == packet.checksum else {
            throw LocalWaveError.malformedPacket
        }

        accumulator.chunks[packet.chunkIndex] = packet.body
        accumulators[packet.id] = accumulator

        guard accumulator.chunks.count == Int(accumulator.chunkCount) else {
            return nil
        }

        var body = Data()
        for index in 0..<accumulator.chunkCount {
            guard let chunk = accumulator.chunks[index] else {
                return nil
            }
            body.append(chunk)
        }

        accumulators[packet.id] = nil
        guard body.count == Int(accumulator.bodyLength), CRC32.checksum(body) == accumulator.checksum else {
            throw LocalWaveError.malformedPacket
        }

        return ReassembledPacket(
            packetId: packet.id,
            conversationId: accumulator.conversationId,
            kind: accumulator.kind,
            body: body
        )
    }
}

enum CRC32 {
    private static let table: [UInt32] = (0..<256).map { index in
        var crc = UInt32(index)
        for _ in 0..<8 {
            crc = (crc & 1) == 1 ? (0xEDB88320 ^ (crc >> 1)) : (crc >> 1)
        }
        return crc
    }

    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            crc = table[Int((crc ^ UInt32(byte)) & 0xFF)] ^ (crc >> 8)
        }
        return crc ^ 0xFFFF_FFFF
    }
}

private struct DataCursor {
    private let data: Data
    private var offset = 0

    init(data: Data) {
        self.data = data
    }

    mutating func readUInt8() throws -> UInt8 {
        guard offset < data.count else { throw LocalWaveError.malformedPacket }
        defer { offset += 1 }
        return data[offset]
    }

    mutating func readUInt16() throws -> UInt16 {
        let bytes = Array(try readBytes(count: 2))
        return (UInt16(bytes[0]) << 8) | UInt16(bytes[1])
    }

    mutating func readUInt32() throws -> UInt32 {
        let bytes = Array(try readBytes(count: 4))
        return (UInt32(bytes[0]) << 24) | (UInt32(bytes[1]) << 16) | (UInt32(bytes[2]) << 8) | UInt32(bytes[3])
    }

    mutating func readUUID() throws -> UUID {
        let bytes = Array(try readBytes(count: 16))
        return bytes.withUnsafeBufferPointer { pointer in
            NSUUID(uuidBytes: pointer.baseAddress!) as UUID
        }
    }

    mutating func readBytes(count: Int) throws -> Data {
        guard offset + count <= data.count else { throw LocalWaveError.malformedPacket }
        defer { offset += count }
        return data.subdata(in: offset..<(offset + count))
    }

    func remainingData() -> Data {
        data.subdata(in: offset..<data.count)
    }
}

private extension UUID {
    var uuidBytes: Data {
        var bytes = uuid
        return withUnsafeBytes(of: &bytes) { Data($0) }
    }
}

private extension FixedWidthInteger {
    var bigEndianData: Data {
        var value = self.bigEndian
        return withUnsafeBytes(of: &value) { Data($0) }
    }
}

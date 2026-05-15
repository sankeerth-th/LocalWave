import CryptoKit
import Foundation
import Security

public struct LocalIdentityKeyPair: Codable, Equatable, Sendable {
    public var identity: LocalIdentity
    public var agreementPrivateKeyData: Data
    public var signingPrivateKeyData: Data

    public init(identity: LocalIdentity, agreementPrivateKeyData: Data, signingPrivateKeyData: Data) {
        self.identity = identity
        self.agreementPrivateKeyData = agreementPrivateKeyData
        self.signingPrivateKeyData = signingPrivateKeyData
    }
}

public protocol IdentityStoreProtocol: Sendable {
    func loadOrCreateIdentity(displayName: String) async throws -> LocalIdentity
    func currentIdentity() async throws -> LocalIdentity
    func keyPair() async throws -> LocalIdentityKeyPair
    func updateDisplayName(_ displayName: String) async throws
    func reset() async throws
}

public actor InMemoryIdentityStore: IdentityStoreProtocol {
    private var material: LocalIdentityKeyPair?

    public init(keyPair: LocalIdentityKeyPair? = nil) {
        self.material = keyPair
    }

    public func loadOrCreateIdentity(displayName: String) async throws -> LocalIdentity {
        if var material {
            material.identity.displayName = displayName
            self.material = material
            return material.identity
        }

        let agreement = Curve25519.KeyAgreement.PrivateKey()
        let signing = Curve25519.Signing.PrivateKey()
        let identity = Self.makeIdentity(displayName: displayName, agreement: agreement, signing: signing)
        let keyPair = LocalIdentityKeyPair(
            identity: identity,
            agreementPrivateKeyData: agreement.rawRepresentation,
            signingPrivateKeyData: signing.rawRepresentation
        )
        material = keyPair
        return identity
    }

    public func currentIdentity() async throws -> LocalIdentity {
        guard let material else { throw LocalWaveError.identityUnavailable }
        return material.identity
    }

    public func keyPair() async throws -> LocalIdentityKeyPair {
        guard let material else { throw LocalWaveError.identityUnavailable }
        return material
    }

    public func updateDisplayName(_ displayName: String) async throws {
        guard var material else { throw LocalWaveError.identityUnavailable }
        material.identity.displayName = displayName
        self.material = material
    }

    public func reset() async throws {
        material = nil
    }

    public static func makeKeyPair(peerId: PeerID? = nil, displayName: String) -> LocalIdentityKeyPair {
        let agreement = Curve25519.KeyAgreement.PrivateKey()
        let signing = Curve25519.Signing.PrivateKey()
        var identity = makeIdentity(displayName: displayName, agreement: agreement, signing: signing)
        if let peerId {
            identity.peerId = peerId
        }
        return LocalIdentityKeyPair(
            identity: identity,
            agreementPrivateKeyData: agreement.rawRepresentation,
            signingPrivateKeyData: signing.rawRepresentation
        )
    }

    private static func makeIdentity(
        displayName: String,
        agreement: Curve25519.KeyAgreement.PrivateKey,
        signing: Curve25519.Signing.PrivateKey
    ) -> LocalIdentity {
        let agreementPublic = agreement.publicKey.rawRepresentation
        let signingPublic = signing.publicKey.rawRepresentation
        let fingerprint = IdentityFingerprint.make(agreementPublicKey: agreementPublic, signingPublicKey: signingPublic)
        return LocalIdentity(
            peerId: String(fingerprint.prefix(16)),
            displayName: displayName,
            agreementPublicKey: agreementPublic,
            signingPublicKey: signingPublic,
            fingerprint: fingerprint
        )
    }
}

public actor KeychainIdentityStore: IdentityStoreProtocol {
    private let service: String
    private let account: String
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(service: String = "com.localwave.identity", account: String = "local-peer") {
        self.service = service
        self.account = account
    }

    public func loadOrCreateIdentity(displayName: String) async throws -> LocalIdentity {
        if var existing = try loadRecord() {
            existing.identity.displayName = displayName
            try saveRecord(existing)
            return existing.identity
        }

        let keyPair = InMemoryIdentityStore.makeKeyPair(displayName: displayName)
        try saveRecord(keyPair)
        return keyPair.identity
    }

    public func currentIdentity() async throws -> LocalIdentity {
        guard let record = try loadRecord() else { throw LocalWaveError.identityUnavailable }
        return record.identity
    }

    public func keyPair() async throws -> LocalIdentityKeyPair {
        guard let record = try loadRecord() else { throw LocalWaveError.identityUnavailable }
        return record
    }

    public func updateDisplayName(_ displayName: String) async throws {
        guard var record = try loadRecord() else { throw LocalWaveError.identityUnavailable }
        record.identity.displayName = displayName
        try saveRecord(record)
    }

    public func reset() async throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw LocalWaveError.repositoryFailure("Keychain delete failed with status \(status).")
        }
    }

    private func loadRecord() throws -> LocalIdentityKeyPair? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = result as? Data else {
            throw LocalWaveError.identityUnavailable
        }
        return try decoder.decode(LocalIdentityKeyPair.self, from: data)
    }

    private func saveRecord(_ record: LocalIdentityKeyPair) throws {
        let data = try encoder.encode(record)
        var attributes = baseQuery()
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(attributes as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updateStatus = SecItemUpdate(baseQuery() as CFDictionary, [kSecValueData as String: data] as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw LocalWaveError.identityUnavailable
            }
            return
        }

        guard status == errSecSuccess else {
            throw LocalWaveError.identityUnavailable
        }
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

public enum IdentityFingerprint {
    public static func make(agreementPublicKey: Data, signingPublicKey: Data) -> String {
        var data = Data("LocalWave.Identity.v1".utf8)
        data.append(agreementPublicKey)
        data.append(signingPublicKey)
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

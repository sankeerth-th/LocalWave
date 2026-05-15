package com.localwave.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.PeerId
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidKeystoreIdentityKeyStore(context: Context) : IdentityKeyStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("localwave_identity", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun loadOrCreateIdentity(displayName: String): LocalIdentity = mutex.withLock {
        loadRecord()?.let {
            val updated = it.copy(identity = it.identity.copy(displayName = displayName))
            saveRecord(updated)
            return@withLock updated.identity
        }
        val generated = LocalIdentityKeyPair.generate(displayName = displayName)
        saveRecord(generated)
        generated.identity
    }

    override suspend fun currentIdentity(): LocalIdentity = mutex.withLock {
        loadRecord()?.identity ?: throw CryptoException.IdentityUnavailable
    }

    override suspend fun keyPair(): LocalIdentityKeyPair = mutex.withLock {
        loadRecord() ?: throw CryptoException.IdentityUnavailable
    }

    override suspend fun updateDisplayName(displayName: String) = mutex.withLock {
        val record = loadRecord() ?: throw CryptoException.IdentityUnavailable
        saveRecord(record.copy(identity = record.identity.copy(displayName = displayName)))
    }

    override suspend fun reset() = mutex.withLock {
        prefs.edit().clear().apply()
    }

    private fun loadRecord(): LocalIdentityKeyPair? {
        val encoded = prefs.getString("record", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val plaintext = decrypt(Base64.getDecoder().decode(encoded), Base64.getDecoder().decode(iv))
        return IdentityRecordCodec.decode(plaintext)
    }

    private fun saveRecord(record: LocalIdentityKeyPair) {
        val plaintext = IdentityRecordCodec.encode(record)
        val encrypted = encrypt(plaintext)
        prefs.edit()
            .putString("record", Base64.getEncoder().encodeToString(encrypted.first))
            .putString("iv", Base64.getEncoder().encodeToString(encrypted.second))
            .apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.doFinal(plaintext) to cipher.iv
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val KEY_ALIAS = "localwave.identity.wrap.v1"
    }
}

private object IdentityRecordCodec {
    fun encode(record: LocalIdentityKeyPair): ByteArray {
        val parts = listOf(
            record.identity.peerId.value,
            record.identity.displayName,
            record.identity.fingerprint,
            Base64.getEncoder().encodeToString(record.identity.agreementPublicKey),
            Base64.getEncoder().encodeToString(record.identity.signingPublicKey),
            Base64.getEncoder().encodeToString(record.agreementPrivateKeyData),
            Base64.getEncoder().encodeToString(record.signingPrivateKeyData)
        )
        return parts.joinToString("\n").toByteArray()
    }

    fun decode(bytes: ByteArray): LocalIdentityKeyPair {
        val parts = bytes.toString(Charsets.UTF_8).split("\n")
        require(parts.size == 7) { "Malformed identity record." }
        val agreementPublic = Base64.getDecoder().decode(parts[3])
        val signingPublic = Base64.getDecoder().decode(parts[4])
        return LocalIdentityKeyPair(
            identity = com.localwave.core.model.LocalIdentity(
                peerId = PeerId(parts[0]),
                displayName = parts[1],
                agreementPublicKey = agreementPublic,
                signingPublicKey = signingPublic,
                fingerprint = parts[2]
            ),
            agreementPrivateKeyData = Base64.getDecoder().decode(parts[5]),
            signingPrivateKeyData = Base64.getDecoder().decode(parts[6])
        )
    }
}

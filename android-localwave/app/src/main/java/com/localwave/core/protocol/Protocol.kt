package com.localwave.core.protocol

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.MessageEnvelope
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.TransportState
import kotlinx.coroutines.flow.Flow

interface LocalWaveEngine {
    suspend fun start(channel: ChannelCode, displayName: String)
    suspend fun stop()
    suspend fun updateDisplayName(displayName: String)
    suspend fun switchChannel(channel: ChannelCode)
    suspend fun sendMessage(text: String, to: PeerId): MessageId
    suspend fun sendWake(to: PeerId)
    fun observePeers(): Flow<List<PeerProfile>>
    fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>>
    fun observeTransportState(): Flow<TransportState>
    suspend fun localIdentity(): LocalIdentity
}

interface CryptoService {
    suspend fun encryptMessage(text: String, peer: PeerProfile, counter: Long, channel: ChannelCode): MessageEnvelope
    suspend fun decryptMessage(envelope: MessageEnvelope, peer: PeerProfile, channel: ChannelCode): String
}

interface MessageRepository {
    suspend fun save(message: ChatMessage)
    suspend fun updateStatus(messageId: MessageId, status: MessageStatus)
    fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>>
    suspend fun deleteAll(peerId: PeerId)
}

interface PeerRepository {
    suspend fun upsert(peer: PeerProfile)
    fun observePeers(): Flow<List<PeerProfile>>
    suspend fun peer(id: PeerId): PeerProfile?
    suspend fun clearRecentlySeen()
}

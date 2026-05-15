package com.localwave.core.persistence

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.protocol.MessageRepository
import com.localwave.core.protocol.PeerRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        PeerEntity::class,
        MessageEntity::class,
        ReplayCounterEntity::class,
        KnownPeerTrustEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LocalWaveTypeConverters::class)
abstract class LocalWaveDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao
    abstract fun replayCounterDao(): ReplayCounterDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao
}

class LocalWaveTypeConverters {
    @TypeConverter fun uuidToString(value: UUID?): String? = value?.toString()
    @TypeConverter fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)
    @TypeConverter fun peerIdToString(value: PeerId?): String? = value?.value
    @TypeConverter fun stringToPeerId(value: String?): PeerId? = value?.let(::PeerId)
}

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: PeerId,
    val displayName: String,
    val fingerprint: String,
    val rssi: Int,
    val lastSeenEpochMillis: Long,
    val state: PresenceState,
    val publicKeyData: ByteArray?
) {
    fun toModel(): PeerProfile = PeerProfile(id, displayName, fingerprint, rssi, lastSeenEpochMillis, state, publicKeyData)

    companion object {
        fun from(peer: PeerProfile): PeerEntity = PeerEntity(
            id = peer.id,
            displayName = peer.displayName,
            fingerprint = peer.fingerprint,
            rssi = peer.rssi,
            lastSeenEpochMillis = peer.lastSeenEpochMillis,
            state = peer.state,
            publicKeyData = peer.publicKeyData
        )
    }
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: MessageId,
    val peerId: PeerId,
    val text: String,
    val sentAtEpochMillis: Long,
    val direction: MessageDirection,
    val status: MessageStatus
) {
    fun toModel(): ChatMessage = ChatMessage(id, peerId, text, sentAtEpochMillis, direction, status)

    companion object {
        fun from(message: ChatMessage): MessageEntity = MessageEntity(
            id = message.id,
            peerId = message.peerId,
            text = message.text,
            sentAtEpochMillis = message.sentAtEpochMillis,
            direction = message.direction,
            status = message.status
        )
    }
}

@Entity(tableName = "replay_counters", primaryKeys = ["peerId", "channelNormalized"])
data class ReplayCounterEntity(
    val peerId: PeerId,
    val channelNormalized: String,
    val highestCounter: Long
)

@Entity(tableName = "known_peer_trust")
data class KnownPeerTrustEntity(
    @PrimaryKey val peerId: PeerId,
    val fingerprintSummary: String,
    val trustedAtEpochMillis: Long?
)

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestampEpochMillis: Long,
    val category: String,
    val message: String
)

@Dao
interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY displayName COLLATE NOCASE")
    fun observePeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE id = :id")
    suspend fun peer(id: PeerId): PeerEntity?

    @Query("UPDATE peers SET state = :recentlySeen WHERE state != :permissionNeeded")
    suspend fun markRecentlySeen(recentlySeen: PresenceState = PresenceState.RECENTLY_SEEN, permissionNeeded: PresenceState = PresenceState.PERMISSION_NEEDED)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: MessageId, status: MessageStatus)

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY sentAtEpochMillis ASC")
    fun observeMessages(peerId: PeerId): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE peerId = :peerId")
    suspend fun deleteAll(peerId: PeerId)
}

@Dao
interface ReplayCounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(counter: ReplayCounterEntity)
}

@Dao
interface DiagnosticEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DiagnosticEventEntity)
}

class RoomPeerRepository(private val dao: PeerDao) : PeerRepository {
    override suspend fun upsert(peer: PeerProfile) = dao.upsert(PeerEntity.from(peer))
    override fun observePeers(): Flow<List<PeerProfile>> = dao.observePeers().map { peers -> peers.map { it.toModel() } }
    override suspend fun peer(id: PeerId): PeerProfile? = dao.peer(id)?.toModel()
    override suspend fun clearRecentlySeen() = dao.markRecentlySeen()
}

class RoomMessageRepository(private val dao: MessageDao) : MessageRepository {
    override suspend fun save(message: ChatMessage) = dao.upsert(MessageEntity.from(message))
    override suspend fun updateStatus(messageId: MessageId, status: MessageStatus) = dao.updateStatus(messageId, status)
    override fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>> = dao.observeMessages(peerId).map { messages -> messages.map { it.toModel() } }
    override suspend fun deleteAll(peerId: PeerId) = dao.deleteAll(peerId)
}

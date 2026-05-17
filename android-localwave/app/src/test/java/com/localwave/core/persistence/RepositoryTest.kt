package com.localwave.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.DeliveryRoute
import com.localwave.core.model.TransferRecord
import com.localwave.core.model.TransferStatus
import com.localwave.core.protocol.TransferRecordFileStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: LocalWaveDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalWaveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun peerRepositoryStoresAndLoadsPeers() = runTest {
        val repo = RoomPeerRepository(db.peerDao())
        val peer = PeerProfile(
            id = PeerId("alice"),
            displayName = "Alice",
            fingerprint = "aa",
            rssi = -40,
            lastSeenEpochMillis = 1,
            state = PresenceState.AVAILABLE,
            publicKeyData = byteArrayOf(1, 2, 3)
        )

        repo.upsert(peer)

        assertEquals(peer, repo.peer(PeerId("alice")))
        assertEquals(listOf(peer), repo.observePeers().first())
    }

    @Test
    fun messageRepositoryStoresAndUpdatesMessages() = runTest {
        val repo = RoomMessageRepository(db.messageDao())
        val peerId = PeerId("alice")
        val message = ChatMessage(
            id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
            peerId = peerId,
            text = "hello",
            sentAtEpochMillis = 10,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.PENDING
        )

        repo.save(message)
        repo.updateStatus(message.id, MessageStatus.SENT)

        assertEquals(MessageStatus.SENT, repo.observeMessages(peerId).first().single().status)
    }

    @Test
    fun transferRecordFileStorePersistsObservableTransferState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "transfer-${UUID.randomUUID()}.json")
        val transfer = TransferRecord(
            id = UUID.randomUUID(),
            peerId = PeerId("alice"),
            fileName = "inventory.pdf",
            byteCount = 128_000,
            route = DeliveryRoute.L2CAP,
            status = TransferStatus.WAITING_FOR_PEER,
            updatedAtEpochMillis = 1_778_900_000_000,
            failureReason = "Waiting for encrypted transfer receipt."
        )

        TransferRecordFileStore(file).save(listOf(transfer))
        val reloaded = TransferRecordFileStore(file).load()

        assertEquals(listOf(transfer), reloaded)
    }
}

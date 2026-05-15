package com.localwave

import android.content.Context
import androidx.room.Room
import com.localwave.core.bluetooth.AndroidBleTransport
import com.localwave.core.crypto.AndroidKeystoreIdentityKeyStore
import com.localwave.core.crypto.SessionCrypto
import com.localwave.core.diagnostics.RedactedLogger
import com.localwave.core.notifications.WakeNotificationManager
import com.localwave.core.persistence.LocalWaveDatabase
import com.localwave.core.persistence.RoomMessageRepository
import com.localwave.core.persistence.RoomPeerRepository
import com.localwave.core.protocol.LocalWaveEngine
import com.localwave.core.protocol.RealLocalWaveEngine

class AppEnvironment(
    val engine: LocalWaveEngine,
    val database: LocalWaveDatabase
) {
    companion object {
        fun create(context: Context): AppEnvironment {
            val database = Room.databaseBuilder(context, LocalWaveDatabase::class.java, "localwave.db").build()
            val identityStore = AndroidKeystoreIdentityKeyStore(context)
            val crypto = SessionCrypto(identityStore)
            val peerRepository = RoomPeerRepository(database.peerDao())
            val messageRepository = RoomMessageRepository(database.messageDao())
            val logger = RedactedLogger()
            val transport = AndroidBleTransport(context, logger)
            val wakeNotifications = WakeNotificationManager(context)
            val engine = RealLocalWaveEngine(
                identityStore = identityStore,
                crypto = crypto,
                peerRepository = peerRepository,
                messageRepository = messageRepository,
                transport = transport,
                wakeNotificationManager = wakeNotifications
            )
            return AppEnvironment(engine, database)
        }
    }
}

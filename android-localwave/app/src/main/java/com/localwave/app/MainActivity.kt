package com.localwave.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.localwave.design.LWTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val packageImportUris = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val environment = AppEnvironment.create(this)
        val appStateHolder = AppStateHolder(applicationContext)
        setContent {
            LWTheme {
                LocalWaveApp(environment = environment, appStateHolder = appStateHolder, activity = this, packageImportUris = packageImportUris)
            }
        }
        handlePackageIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePackageIntent(intent)
    }

    private fun handlePackageIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) packageImportUris.tryEmit(uri)
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let(packageImportUris::tryEmit)
            }
        }
    }
}

package com.localwave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.localwave.design.LWTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val environment = AppEnvironment.create(this)
        val appStateHolder = AppStateHolder(applicationContext)
        setContent {
            LWTheme {
                LocalWaveApp(environment = environment, appStateHolder = appStateHolder, activity = this)
            }
        }
    }
}

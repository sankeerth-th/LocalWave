package com.localwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DiagnosticPlaceholder() }
    }
}

@Composable
private fun DiagnosticPlaceholder() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("LocalWave Android Core", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Core BLE, crypto, persistence, permissions, and mock engine services are available for product integration.")
                Spacer(modifier = Modifier.height(12.dp))
                Text("No internet. No signup. Nearby Bluetooth only.")
            }
        }
    }
}

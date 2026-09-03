package com.abe.androidcodexbridge

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BridgeDashboard(
                    openAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}

@Composable
private fun BridgeDashboard(openAccessibility: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Android Codex Bridge", style = MaterialTheme.typography.headlineMedium)
        Text("Local Android observation and control bridge for Codex/MCP agents.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accessibility bridge", style = MaterialTheme.typography.titleMedium)
                Text("Enable this manually to inspect visible UI and perform user-authorized gestures.")
                Button(onClick = openAccessibility) { Text("Open Accessibility settings") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device", style = MaterialTheme.typography.titleMedium)
                Text("Manufacturer: ${android.os.Build.MANUFACTURER}")
                Text("Model: ${android.os.Build.MODEL}")
                Text("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { BridgeAccessibilityService.instance?.performBack() }) { Text("Back") }
            Button(onClick = { BridgeAccessibilityService.instance?.performHome() }) { Text("Home") }
        }

        Text("v0.1 foundation — host ADB/MCP bridge provides deeper diagnostics such as logcat and dumpsys.")
    }
}

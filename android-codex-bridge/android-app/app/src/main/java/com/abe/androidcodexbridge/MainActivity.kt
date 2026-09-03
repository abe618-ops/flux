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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BridgeDashboard(
                    openAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    startCameraDoctor = { CameraDoctor.start(this) },
                    finishCameraDoctor = { CameraDoctor.finish(this) },
                    shareReport = ::shareReport
                )
            }
        }
    }

    private fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share diagnostic report"))
    }
}

@Composable
private fun BridgeDashboard(
    openAccessibility: () -> Unit,
    startCameraDoctor: () -> Unit,
    finishCameraDoctor: () -> File,
    shareReport: (File) -> Unit
) {
    var doctorRunning by remember { mutableStateOf(CameraDoctor.isRunning()) }
    var lastReport by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Android Codex Bridge", style = MaterialTheme.typography.headlineMedium)
        Text("v0.2 Camera Doctor — reproduce a camera failure and export evidence.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Camera Doctor", style = MaterialTheme.typography.titleMedium)
                Text(if (doctorRunning) "Recording session. Open Camera, take a photo, reproduce the save/crash problem, then return here." else "Start a session before reproducing the camera problem.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        startCameraDoctor()
                        doctorRunning = true
                    }, enabled = !doctorRunning) { Text("Start diagnosis") }
                    Button(onClick = {
                        lastReport = finishCameraDoctor()
                        doctorRunning = false
                    }, enabled = doctorRunning) { Text("Finish") }
                }
                lastReport?.let { report ->
                    Text("Report: ${report.name}")
                    Button(onClick = { shareReport(report) }) { Text("Share report") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accessibility bridge", style = MaterialTheme.typography.titleMedium)
                Text("Enable this manually so Camera Doctor can capture the visible UI state.")
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

        Text("Deep crash/logcat/CameraService evidence still requires an authorized ADB or privileged bridge; the report marks this explicitly.")
    }
}

package com.rst.heyrst

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rst.heyrst.ui.theme.HeyRSTTheme

class MainActivity : ComponentActivity() {

    private var hasAudioPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                checkAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()

        setContent {
            HeyRSTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupDashboard(
                        hasAudio = hasAudioPermission,
                        hasOverlay = hasOverlayPermission,
                        engineStatus = AssistantOverlayService.engineStatus,
                        lastHeard = AssistantOverlayService.lastHeardSpeech,
                        onRequestPermissions = { requestAudioAndNotificationPermissions() },
                        onRequestOverlay = { openOverlaySettings() },
                        onStartService = { sendServiceAction(AssistantOverlayService.ACTION_START_LISTENING) },
                        onTriggerOverlayTest = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) },
                        onStopService = { stopService(Intent(this, AssistantOverlayService::class.java)) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        hasAudioPermission = checkAudioPermission()
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioAndNotificationPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun sendServiceAction(action: String) {
        if (!hasAudioPermission || !hasOverlayPermission) {
            Toast.makeText(
                this,
                "Grant both Microphone and Overlay permissions first!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = Intent(this, AssistantOverlayService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun SetupDashboard(
    hasAudio: Boolean,
    hasOverlay: Boolean,
    engineStatus: String,
    lastHeard: String,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onStartService: () -> Unit,
    onTriggerOverlayTest: () -> Unit,
    onStopService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hey RST (In-House Engine)",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "100% Free • Zero External APIs • com.rst.heyrst",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Live In-House Audio Debugger Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Status: $engineStatus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last Heard: \"$lastHeard\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasAudio
        ) {
            Text(if (hasAudio) "✓ 1. Microphone Permission Granted" else "1. Grant Microphone Permission")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onRequestOverlay,
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasOverlay
        ) {
            Text(if (hasOverlay) "✓ 2. Overlay Permission Granted" else "2. Enable Display Over Other Apps")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartService,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Start \"Hey RST\" Listener")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onTriggerOverlayTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Floating Overlay Card Now")
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onStopService,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Service", color = MaterialTheme.colorScheme.error)
        }
    }
}
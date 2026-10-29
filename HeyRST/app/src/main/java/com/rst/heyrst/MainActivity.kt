package com.rst.heyrst

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rst.heyrst.accessibility.RSTAccessibilityService
import com.rst.heyrst.ui.theme.HeyRSTTheme

class MainActivity : ComponentActivity() {

    private var hasAudioPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var isSystemActivated by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                checkAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()

        // Read saved active preference
        val prefs = getSharedPreferences("heyrst_prefs", Context.MODE_PRIVATE)
        isSystemActivated = prefs.getBoolean("is_system_activated", false) &&
                hasAudioPermission && hasOverlayPermission

        setContent {
            HeyRSTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupDashboard(
                        isActivated = isSystemActivated,
                        onToggleActivated = { shouldActivate ->
                            handleSystemToggle(shouldActivate)
                        },
                        hasAudio = hasAudioPermission,
                        hasOverlay = hasOverlayPermission,
                        hasAccessibility = hasAccessibilityPermission,
                        engineStatus = AssistantOverlayService.engineStatus,
                        lastHeard = AssistantOverlayService.lastHeardSpeech,
                        onRequestPermissions = { requestAudioAndNotificationPermissions() },
                        onRequestOverlay = { openOverlaySettings() },
                        onRequestAccessibility = { openAccessibilitySettings() },
                        onTriggerOverlayTest = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) },
                        onTriggerDemoForm = { sendServiceAction(AssistantOverlayService.ACTION_TEST_DEMO_FORM) }
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
        hasAccessibilityPermission = isAccessibilityServiceEnabled(this, RSTAccessibilityService::class.java)
    }

    private fun handleSystemToggle(activate: Boolean) {
        val prefs = getSharedPreferences("heyrst_prefs", Context.MODE_PRIVATE)

        if (activate) {
            if (!hasAudioPermission || !hasOverlayPermission) {
                Toast.makeText(
                    this,
                    "Please grant Microphone and Overlay permissions first!",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            isSystemActivated = true
            prefs.edit().putBoolean("is_system_activated", true).apply()
            sendServiceAction(AssistantOverlayService.ACTION_START_LISTENING)
        } else {
            isSystemActivated = false
            prefs.edit().putBoolean("is_system_activated", false).apply()
            stopService(Intent(this, AssistantOverlayService::class.java))
            AssistantOverlayService.engineStatus = "Deactivated (Mic Off)"
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        if (RSTAccessibilityService.isServiceRunning()) return true
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun sendServiceAction(action: String) {
        if (!hasAudioPermission || !hasOverlayPermission) {
            Toast.makeText(
                this,
                "Grant Microphone and Overlay permissions first!",
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
    isActivated: Boolean,
    onToggleActivated: (Boolean) -> Unit,
    hasAudio: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    engineStatus: String,
    lastHeard: String,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onTriggerOverlayTest: () -> Unit,
    onTriggerDemoForm: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Hey RST — Form Companion",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Voice-First Auto Form Filler for Chrome & Android",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Master Switch Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActivated) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hey RST Listener",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isActivated) "Active • Mic ON & listening for 'Hey RST'" else "Deactivated • Mic is OFF",
                        fontSize = 12.sp,
                        color = if (isActivated) Color(0xFF1D4ED8) else Color(0xFF64748B)
                    )
                }

                Switch(
                    checked = isActivated,
                    onCheckedChange = onToggleActivated,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF1D4ED8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Audio Debugger Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engine Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActivated) Color(0xFF10B981) else Color(0xFF64748B),
                                CircleShape
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isActivated) "ONLINE" else "OFFLINE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Status: $engineStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last Heard: \"$lastHeard\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "REQUIRED PERMISSIONS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Step 1: Microphone
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasAudio) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (hasAudio) "✓ 1. Microphone Granted" else "1. Grant Microphone Permission")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step 2: Overlay
        Button(
            onClick = onRequestOverlay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasOverlay) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (hasOverlay) "✓ 2. Display Over Other Apps Granted" else "2. Enable Display Over Other Apps")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step 3: Accessibility
        Button(
            onClick = onRequestAccessibility,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasAccessibility) Color(0xFF10B981) else Color(0xFF6366F1)
            )
        ) {
            Text(if (hasAccessibility) "✓ 3. Accessibility Service Enabled" else "3. Enable Accessibility Service (Chrome Form Sync)")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "TESTING & PRACTICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onTriggerOverlayTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Overlay on Active Chrome Form")
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onTriggerDemoForm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Practice with Registration Form Demo")
        }
    }
}

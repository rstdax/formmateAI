package com.rst.heyrst

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rst.heyrst.ui.theme.HeyRSTTheme

class AssistantOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        const val ACTION_START_LISTENING = "com.rst.heyrst.ACTION_START_LISTENING"
        const val ACTION_SHOW_OVERLAY = "com.rst.heyrst.ACTION_SHOW_OVERLAY"
        private const val CHANNEL_ID = "rst_assistant_channel"
        private const val NOTIFICATION_ID = 1001

        // Observable state shared with MainActivity for live debugging
        var engineStatus by mutableStateOf("Stopped")
        var lastHeardSpeech by mutableStateOf("Nothing heard yet")
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var wakeWordEngine: RSTWakeWordEngine? = null

    private var currentTranscript by mutableStateOf("Listening for your command...")
    private var micVolumeRms by mutableFloatStateOf(0f)

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        startInHouseEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                currentTranscript = "How can RST help you?"
                showOverlayUI()
            }
            ACTION_START_LISTENING -> {
                wakeWordEngine?.start()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RST In-House Voice Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hey RST Active (In-House Engine)")
            .setContentText("Say \"Hey RST\" anytime to launch overlay")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startInHouseEngine() {
        wakeWordEngine = RSTWakeWordEngine(
            context = applicationContext,
            onWakeWordDetected = { commandAfterWake ->
                currentTranscript = if (commandAfterWake.isNotEmpty()) {
                    "\"$commandAfterWake\""
                } else {
                    "I'm listening... Speak your command"
                }
                showOverlayUI()
                if (commandAfterWake.isNotEmpty()) {
                    handleVoiceCommand(commandAfterWake)
                }
            },
            onLiveTranscript = { text, isFinal ->
                lastHeardSpeech = text
                if (composeView != null) {
                    currentTranscript = text
                    if (isFinal) {
                        handleVoiceCommand(text.lowercase())
                    }
                }
            },
            onVolumeChanged = { rms ->
                micVolumeRms = rms
            },
            onDebugStatus = { status ->
                engineStatus = status
            }
        )
        wakeWordEngine?.start()
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("close") || command.contains("dismiss") -> {
                hideOverlayUI()
            }
            command.contains("open youtube") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                hideOverlayUI()
            }
            command.contains("open google") || command.contains("open browser") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                hideOverlayUI()
            }
        }
    }

    private fun showOverlayUI() {
        if (composeView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AssistantOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AssistantOverlayService)
            setViewTreeViewModelStoreOwner(this@AssistantOverlayService)

            setContent {
                HeyRSTTheme {
                    RSTAssistantFloatingSheet(
                        transcript = currentTranscript,
                        rmsLevel = micVolumeRms,
                        onDismiss = { hideOverlayUI() }
                    )
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
    }

    private fun hideOverlayUI() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }

    override fun onDestroy() {
        wakeWordEngine?.stop()
        engineStatus = "Stopped"
        hideOverlayUI()
        store.clear()
        super.onDestroy()
    }
}

@Composable
fun RSTAssistantFloatingSheet(
    transcript: String,
    rmsLevel: Float,
    onDismiss: () -> Unit
) {
    val pulseScale by animateFloatAsState(
        targetValue = 1f + (rmsLevel / 18f),
        label = "micPulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Voice Pulse Indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF6366F1), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("RST", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Hey RST Assistant",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF38BDF8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Try saying: \"Open YouTube\", \"Open Google\", or \"Close\"",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
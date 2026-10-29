package com.rst.heyrst

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.rst.heyrst.accessibility.RSTAccessibilityService
import com.rst.heyrst.model.FormHistoryItem
import com.rst.heyrst.model.FormHistoryStorage
import com.rst.heyrst.model.ProfileStorage
import com.rst.heyrst.model.UserProfile
import com.rst.heyrst.ui.theme.HeyRSTTheme
import java.io.File
import java.util.Calendar

/**
 * Screen destinations for multi-page architecture:
 * 1. PROFILES: Starting / Onboarding page where user creates or manages profiles.
 * 2. SETTINGS: Permissions, Listener master toggle, Language selection, Testing.
 * 3. HOME: Image 3 Dashboard with Bento grid history cards, status pill, selectors.
 */
enum class AppScreen {
    HOME,
    PROFILES,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private var hasAudioPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var isSystemActivated by mutableStateOf(false)
    private var selectedLanguage by mutableStateOf("en") // "en", "hi", "bn"
    private var currentScreen by mutableStateOf(AppScreen.HOME)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true || checkAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()

        val prefs = getSharedPreferences("heyrst_prefs", Context.MODE_PRIVATE)
        isSystemActivated = prefs.getBoolean("is_system_activated", false) &&
                hasAudioPermission && hasOverlayPermission
        selectedLanguage = prefs.getString("selected_language", "en") ?: "en"

        // Onboarding Check: If no profiles exist yet, guide user to the Profiles page first
        val profiles = ProfileStorage.getProfiles(this)
        currentScreen = if (profiles.isEmpty()) AppScreen.PROFILES else AppScreen.HOME

        setContent {
            HeyRSTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8FAFD)
                ) {
                    when (currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                isActivated = isSystemActivated,
                                engineStatus = AssistantOverlayService.engineStatus,
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { lang ->
                                    selectedLanguage = lang
                                    prefs.edit().putString("selected_language", lang).apply()
                                },
                                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                                onNavigateToProfiles = { currentScreen = AppScreen.PROFILES },
                                onStartNewForm = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) },
                                onToggleActivated = { handleSystemToggle(it) }
                            )
                        }
                        AppScreen.PROFILES -> {
                            ProfilesScreen(
                                onNavigateBack = { currentScreen = AppScreen.HOME },
                                onProceedToSettings = { currentScreen = AppScreen.SETTINGS },
                                onOpenAssistant = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                isActivated = isSystemActivated,
                                onToggleActivated = { handleSystemToggle(it) },
                                hasAudio = hasAudioPermission,
                                hasOverlay = hasOverlayPermission,
                                hasAccessibility = hasAccessibilityPermission,
                                engineStatus = AssistantOverlayService.engineStatus,
                                lastHeard = AssistantOverlayService.lastHeardSpeech,
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { lang ->
                                    selectedLanguage = lang
                                    prefs.edit().putString("selected_language", lang).apply()
                                },
                                onRequestPermissions = { requestAudioAndNotificationPermissions() },
                                onRequestOverlay = { openOverlaySettings() },
                                onRequestAccessibility = { openAccessibilitySettings() },
                                onOpenAssistant = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) },
                                onPracticeDemo = { sendServiceAction(AssistantOverlayService.ACTION_TEST_DEMO_FORM) },
                                onNavigateBack = { currentScreen = AppScreen.HOME }
                            )
                        }
                    }
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
                currentScreen = AppScreen.SETTINGS
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
            currentScreen = AppScreen.SETTINGS
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

// ==============================================================================
// 1. HOME SCREEN (Dashboard with Bento Grid & Status Pill - Image 3)
// ==============================================================================

@Composable
fun HomeScreen(
    isActivated: Boolean,
    engineStatus: String,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onStartNewForm: () -> Unit,
    onToggleActivated: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var historyItems by remember { mutableStateOf(FormHistoryStorage.getHistory(context)) }
    var activeProfile by remember { mutableStateOf(ProfileStorage.getActiveProfile(context)) }
    var allProfiles by remember { mutableStateOf(ProfileStorage.getProfiles(context)) }

    var showProfileDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var selectedHistoryItem by remember { mutableStateOf<FormHistoryItem?>(null) }

    val completedCount = historyItems.count { it.status == "Completed" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        // --- Header (Hello, User! + 4 forms filled + Settings & Profile Icons) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val greetingName = activeProfile?.profileName?.ifBlank { activeProfile?.firstName } ?: "User"
                Text(
                    text = "Hello, $greetingName!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$completedCount forms filled",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Settings Icon Button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFF1F5F9), CircleShape)
                ) {
                    Text("⚙", fontSize = 18.sp, color = Color(0xFF334155))
                }

                // Profile Icon Button
                IconButton(
                    onClick = onNavigateToProfiles,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFF1F5F9), CircleShape)
                ) {
                    Text("👤", fontSize = 18.sp, color = Color(0xFF334155))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- Engine Status Pill Banner ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(if (isActivated) Color(0xFFE8F7F0) else Color(0xFFF1F5F9))
                .border(
                    1.dp,
                    if (isActivated) Color(0xFFB8EAD2) else Color(0xFFCBD5E1),
                    RoundedCornerShape(30.dp)
                )
                .clickable(onClick = onNavigateToSettings)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isActivated) Color(0xFF10B981) else Color(0xFF94A3B8),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isActivated) "Engine Status: Active — Say \"Formate AI\" or \"Hey RST\"" else "Engine Status: Deactivated — Tap to Enable",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActivated) Color(0xFF065F46) else Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "›",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActivated) Color(0xFF059669) else Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Quick Bar: Profile & Language Selectors ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Profile Selector Chip
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .clickable { showProfileDropdown = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👤 ${activeProfile?.profileName?.ifBlank { "Personal" } ?: "Select Profile"}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("▾", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }

                DropdownMenu(
                    expanded = showProfileDropdown,
                    onDismissRequest = { showProfileDropdown = false }
                ) {
                    allProfiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.profileName.ifBlank { p.fullName }) },
                            onClick = {
                                ProfileStorage.setActiveProfile(context, p.id)
                                activeProfile = p
                                showProfileDropdown = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("+ Manage Profiles", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold) },
                        onClick = {
                            showProfileDropdown = false
                            onNavigateToProfiles()
                        }
                    )
                }
            }

            // Language Selector Chip
            Box(modifier = Modifier.weight(1f)) {
                val langLabel = when (selectedLanguage) {
                    "hi" -> "🌐 हिन्दी (Hindi)"
                    "bn" -> "🌐 বাংলা (Bengali)"
                    else -> "🌐 English"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .clickable { showLanguageDropdown = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = langLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("▾", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }

                DropdownMenu(
                    expanded = showLanguageDropdown,
                    onDismissRequest = { showLanguageDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("English (Default)") },
                        onClick = {
                            onLanguageChange("en")
                            showLanguageDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("हिन्दी (Hindi)") },
                        onClick = {
                            onLanguageChange("hi")
                            showLanguageDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("বাংলা (Bengali)") },
                        onClick = {
                            onLanguageChange("bn")
                            showLanguageDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Section Title: Form Filling History & View all ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Form Filling History",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "View all ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Real Webpage Form History Display ---
        if (historyItems.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFEFF6FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📋", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Form History Yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open any web form in Chrome and say \"Formate AI\" or \"Hey RST\" to fill forms automatically.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onStartNewForm,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text("🧭  Start Filling Now", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        } else {
            // --- 2-Column Bento Grid of Real Webpage Forms ---
            val displayedHistory = historyItems.take(6)
            for (i in displayedHistory.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val item1 = displayedHistory[i]
                    Box(modifier = Modifier.weight(1f)) {
                        BentoHistoryCard(item = item1, onClick = { selectedHistoryItem = item1 })
                    }

                    if (i + 1 < displayedHistory.size) {
                        val item2 = displayedHistory[i + 1]
                        Box(modifier = Modifier.weight(1f)) {
                            BentoHistoryCard(item = item2, onClick = { selectedHistoryItem = item2 })
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (selectedHistoryItem != null) {
        FormHistoryDetailsDialog(
            item = selectedHistoryItem!!,
            onDismiss = { selectedHistoryItem = null },
            onDelete = { id ->
                FormHistoryStorage.deleteHistoryItem(context, id)
                historyItems = FormHistoryStorage.getHistory(context)
                selectedHistoryItem = null
            }
        )
    }
}

/**
 * Modern Bento Grid card matching Image 3:
 * - Rounded 20dp container
 * - Image thumbnail (from file or dynamic procedural art)
 * - Translucent top-left capsule (time/date)
 * - Top-right subtle chevron
 * - Card title
 * - Green capsule: ✓ Status: Completed >
 */
@Composable
fun BentoHistoryCard(
    item: FormHistoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Thumbnail Background
            if (item.thumbnailPath != null && File(item.thumbnailPath).exists()) {
                val bitmap = remember(item.thumbnailPath) {
                    BitmapFactory.decodeFile(item.thumbnailPath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    ThematicCardIllustration(title = item.title)
                }
            } else {
                ThematicCardIllustration(title = item.title)
            }

            // Subtle gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // 2. Card Content Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Time Capsule & Chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.timeBadge,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    Text(
                        text = "›",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // Title Area
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Bottom Row: Status Capsule
                val isDone = item.status == "Completed"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDone) Color(0xFF059669) else Color(0xFFD97706))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDone) "✓ Status: Completed" else "⏱ Status: ${item.status}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "›",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed view dialog for a Form History Item:
 * Displays real webpage/form title, full screenshot preview, date and time,
 * fields completed count, and delete action.
 */
@Composable
fun FormHistoryDetailsDialog(
    item: FormHistoryItem,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Row: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Form Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real Screenshot Preview (if captured)
                val thumbFile = item.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0) {
                    val bitmap = remember(item.thumbnailPath) {
                        try {
                            BitmapFactory.decodeFile(item.thumbnailPath)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Webpage / Form Title
                Text(
                    text = item.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                if (item.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.url,
                        fontSize = 12.sp,
                        color = Color(0xFF1D4ED8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                // Status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", fontSize = 13.sp, color = Color(0xFF64748B))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (item.status == "Completed") Color(0xFFD1FAE5) else Color(0xFFFEF3C7))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (item.status == "Completed") "✓ Completed" else "⏱ ${item.status}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.status == "Completed") Color(0xFF059669) else Color(0xFFB45309)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date & Time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date & Time", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text(
                        text = item.dateTime.ifBlank { "Recently" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                if (item.totalFieldsCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fields Filled", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(
                            text = "${item.filledFieldsCount} / ${item.totalFieldsCount} fields",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons: Delete & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDelete(item.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                    ) {
                        Text("🗑 Delete", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                    ) {
                        Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Procedural illustrations for default sample history cards matching Image 3:
 * 1. Indian Passport Application (navy & gold)
 * 2. California Poppy Bloom (sunny sky & vibrant orange petals)
 * 3. Lake Tahoe Trip Planning (alpine cyan lake & emerald pines)
 * 4. Nighthawks by Edward Hopper (nocturnal midnight blue & warm amber diner)
 */
@Composable
fun ThematicCardIllustration(title: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when {
            title.contains("Passport", ignoreCase = true) -> {
                // Navy passport cover with gold accents
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                drawCircle(
                    color = Color(0xFFD97706).copy(alpha = 0.7f),
                    radius = w * 0.22f,
                    center = Offset(w * 0.5f, h * 0.45f)
                )
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = w * 0.16f,
                    center = Offset(w * 0.5f, h * 0.45f)
                )
            }
            title.contains("Poppy", ignoreCase = true) -> {
                // Sky & California poppy orange petals
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFBAE6FD), Color(0xFFFEF08A), Color(0xFFF97316))
                    )
                )
                drawCircle(
                    color = Color(0xFFEA580C),
                    radius = w * 0.28f,
                    center = Offset(w * 0.65f, h * 0.45f)
                )
                drawCircle(
                    color = Color(0xFFFBBF24),
                    radius = w * 0.18f,
                    center = Offset(w * 0.55f, h * 0.40f)
                )
            }
            title.contains("Tahoe", ignoreCase = true) -> {
                // Mountain sky, alpine cyan lake, evergreen tree silhouettes
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF065F46))
                    )
                )
                drawCircle(
                    color = Color(0xFF047857).copy(alpha = 0.6f),
                    radius = w * 0.35f,
                    center = Offset(w * 0.3f, h * 0.75f)
                )
                drawCircle(
                    color = Color(0xFF064E3B),
                    radius = w * 0.25f,
                    center = Offset(w * 0.75f, h * 0.8f)
                )
            }
            else -> {
                // Nighthawks nocturnal dark teal & amber warm light
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF134E4A), Color(0xFF042F2E))
                    )
                )
                drawRect(
                    color = Color(0xFFFDE68A).copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.15f, h * 0.35f),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.35f)
                )
            }
        }
    }
}

// ==============================================================================
// 2. PROFILES SCREEN (Saved Profiles & Profile Creator - Image 2)
// ==============================================================================

@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    onProceedToSettings: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var profiles by remember { mutableStateOf(ProfileStorage.getProfiles(context)) }
    var activeProfile by remember { mutableStateOf(ProfileStorage.getActiveProfile(context)) }
    var isEditingProfile by remember { mutableStateOf(profiles.isEmpty()) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var showProfileSelectorDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        // Top Back Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profiles.isNotEmpty()) {
                IconButton(onClick = onNavigateBack) {
                    Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                }
            }
            Text(
                text = "Formate AI",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1D4ED8)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Voice-first digital form assistant",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(start = if (profiles.isNotEmpty()) 48.dp else 0.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Banner Card: "Forms, made simpler."
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Forms, made simpler.",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Open Formate AI beside any webpage to detect form fields, answer naturally by voice or text, and fill forms effortlessly.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card: "Saved Profiles" (Stored on this device)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Profiles",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Stored on this device",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isEditingProfile) {
                    if (profiles.isNotEmpty()) {
                        Text(
                            text = "Choose a profile",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Active profile capsule with dropdown
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                                    .clickable { showProfileSelectorDropdown = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeProfile?.profileName?.ifBlank { activeProfile?.fullName } ?: "Personal Profile",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text("▼", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            DropdownMenu(
                                expanded = showProfileSelectorDropdown,
                                onDismissRequest = { showProfileSelectorDropdown = false }
                            ) {
                                profiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.profileName.ifBlank { p.fullName }) },
                                        onClick = {
                                            ProfileStorage.setActiveProfile(context, p.id)
                                            activeProfile = p
                                            showProfileSelectorDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3 Action Buttons: Create | Edit | Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    profileToEdit = UserProfile()
                                    isEditingProfile = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Create", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    profileToEdit = activeProfile ?: UserProfile()
                                    isEditingProfile = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Edit", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    activeProfile?.let { p ->
                                        ProfileStorage.deleteProfile(context, p.id)
                                        profiles = ProfileStorage.getProfiles(context)
                                        activeProfile = ProfileStorage.getActiveProfile(context)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Delete", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                profileToEdit = UserProfile()
                                isEditingProfile = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Text("+ Create your first profile", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "The selected profile will be offered when a form is detected. Profiles stay on this device and are not encrypted.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                }

                // Profile Editor Form (Create New / Edit Profile)
                AnimatedVisibility(visible = isEditingProfile) {
                    ProfileEditorForm(
                        initialProfile = profileToEdit ?: UserProfile(),
                        onSave = { updated ->
                            ProfileStorage.saveProfile(context, updated)
                            profiles = ProfileStorage.getProfiles(context)
                            activeProfile = ProfileStorage.getActiveProfile(context)
                            isEditingProfile = false
                            profileToEdit = null
                            Toast.makeText(context, "Profile '${updated.profileName.ifBlank { "Personal" }}' saved!", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = {
                            if (profiles.isNotEmpty()) {
                                isEditingProfile = false
                                profileToEdit = null
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Next / Open Assistant Button
        Button(
            onClick = {
                if (profiles.isNotEmpty()) {
                    onProceedToSettings()
                } else {
                    Toast.makeText(context, "Please create at least one profile first!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
        ) {
            Text(
                text = if (profiles.size <= 1) "Continue to Settings →" else "Save & Go to Dashboard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ready • No login required",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "Formate AI assists you in filling forms; you review and submit.",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ==============================================================================
// 3. SETTINGS SCREEN (Listener Toggle & Permissions - Image from Request 3)
// ==============================================================================

@Composable
fun SettingsScreen(
    isActivated: Boolean,
    onToggleActivated: (Boolean) -> Unit,
    hasAudio: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    engineStatus: String,
    lastHeard: String,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenAssistant: () -> Unit,
    onPracticeDemo: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
            }
            Column {
                Text(
                    text = "Formate AI — Form Companion",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Voice-First Auto Form Filler for Chrome & Android",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Assistant Listener Switch Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActivated) Color(0xFFEFF6FF) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Formate AI Listener",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isActivated) "Active • Mic is ON and listening for 'Formate AI' or 'Hey RST'" else "Deactivated • Mic is OFF",
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

        // Engine Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Engine Status",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Status: $engineStatus",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Last Heard: \"$lastHeard\"",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActivated) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isActivated) "ACTIVE" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActivated) Color(0xFF10B981) else Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Required Permissions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "REQUIRED PERMISSIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow("1. Microphone Access", hasAudio, onRequestPermissions)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow("2. Display Over Other Apps", hasOverlay, onRequestOverlay)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow("3. Accessibility Service Enabled", hasAccessibility, onRequestAccessibility)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Language Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "PREFERRED FILLING LANGUAGE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageChip("English", selectedLanguage == "en") { onLanguageChange("en") }
                    LanguageChip("हिन्दी", selectedLanguage == "hi") { onLanguageChange("hi") }
                    LanguageChip("বাংলা", selectedLanguage == "bn") { onLanguageChange("bn") }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Testing & Practice Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "TESTING & PRACTICE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF))
                        .clickable(onClick = onOpenAssistant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Test Overlay on Active Chrome Form", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                    Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F3FF))
                        .clickable(onClick = onPracticeDemo)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Practice with Registration Form Demo", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C3AED))
                    Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
        ) {
            Text("Go to Dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun LanguageChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1D4ED8) else Color(0xFFF1F5F9))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color(0xFF475569)
        )
    }
}

@Composable
fun PermissionRow(title: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
            .clickable(onClick = onRequest)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (granted) Color(0xFF10B981) else Color(0xFF94A3B8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (granted) "✓" else "!", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (granted) Color(0xFF065F46) else Color(0xFF334155)
            )
        }
        Text(if (granted) "Granted" else "Grant", fontSize = 12.sp, color = if (granted) Color(0xFF10B981) else Color(0xFF2563EB))
    }
}

/**
 * Profile Editor Form containing all user requested fields:
 * Profile name, Full name, First name, Last name, Email, Phone, Date of birth (with DatePickerDialog),
 * Gender, Parent / guardian name, Street address, City / town, District, State / province,
 * Country, Postal / PIN code, Age, Occupation, Course / stream, Education / qualification,
 * School / institution.
 */
@Composable
fun ProfileEditorForm(
    initialProfile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var profileName by remember { mutableStateOf(initialProfile.profileName) }
    var fullName by remember { mutableStateOf(initialProfile.fullName) }
    var firstName by remember { mutableStateOf(initialProfile.firstName) }
    var lastName by remember { mutableStateOf(initialProfile.lastName) }
    var email by remember { mutableStateOf(initialProfile.email) }
    var phone by remember { mutableStateOf(initialProfile.phone) }
    var dateOfBirth by remember { mutableStateOf(initialProfile.dateOfBirth) }
    var gender by remember { mutableStateOf(initialProfile.gender) }
    var guardianName by remember { mutableStateOf(initialProfile.guardianName) }
    var streetAddress by remember { mutableStateOf(initialProfile.streetAddress) }
    var city by remember { mutableStateOf(initialProfile.city) }
    var district by remember { mutableStateOf(initialProfile.district) }
    var state by remember { mutableStateOf(initialProfile.state) }
    var country by remember { mutableStateOf(initialProfile.country) }
    var pincode by remember { mutableStateOf(initialProfile.pincode) }
    var age by remember { mutableStateOf(initialProfile.age) }
    var occupation by remember { mutableStateOf(initialProfile.occupation) }
    var course by remember { mutableStateOf(initialProfile.course) }
    var education by remember { mutableStateOf(initialProfile.education) }
    var institution by remember { mutableStateOf(initialProfile.institution) }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val formatted = String.format("%02d/%02d/%04d", day, month + 1, year)
                dateOfBirth = formatted
            },
            cal.get(Calendar.YEAR) - 20,
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // Profile Name
        ProfileInputField("Profile name", profileName, "e.g. Personal") { profileName = it }

        Spacer(modifier = Modifier.height(8.dp))

        // Full Name | First Name
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Full name", fullName, "Rohan Ranjan Das") { fullName = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("First name", firstName, "Rohan") { firstName = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Last Name | Email
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Last name", lastName, "Das") { lastName = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Email", email, "name@example.com") { email = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Phone | Date of Birth (Clickable with Calendar)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Phone", phone, "10-digit mobile") { phone = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Date of birth",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                            .clickable { showDatePicker() }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateOfBirth.ifBlank { "dd/mm/yyyy" },
                                fontSize = 13.sp,
                                color = if (dateOfBirth.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A)
                            )
                            Text("📅", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Gender | Parent / guardian name
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Gender", gender, "Male / Female / Other") { gender = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Parent / guardian", guardianName, "Guardian name") { guardianName = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Street address
        ProfileInputField("Street address", streetAddress, "House, street, locality") { streetAddress = it }

        Spacer(modifier = Modifier.height(8.dp))

        // City | District
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("City / town", city, "e.g. Guwahati") { city = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("District", district, "e.g. Kamrup") { district = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // State | Country
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("State / province", state, "e.g. Assam") { state = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Country", country, "India") { country = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Postal / PIN code | Age
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Postal / PIN code", pincode, "6-digit PIN") { pincode = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Age", age, "e.g. 21") { age = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Occupation | Course / stream
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Occupation", occupation, "e.g. Student") { occupation = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Course / stream", course, "e.g. BTech CSE") { course = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Education | School / institution
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Education", education, "e.g. Undergraduate") { education = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("School / institution", institution, "College / University") { institution = it }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons: Cancel & Save Profile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = Color(0xFF64748B))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val updated = initialProfile.copy(
                        profileName = profileName.ifBlank { "Personal" },
                        fullName = fullName,
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        phone = phone,
                        dateOfBirth = dateOfBirth,
                        gender = gender,
                        guardianName = guardianName,
                        streetAddress = streetAddress,
                        city = city,
                        district = district,
                        state = state,
                        country = country,
                        pincode = pincode,
                        age = age,
                        occupation = occupation,
                        course = course,
                        education = education,
                        institution = institution
                    )
                    onSave(updated)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
            ) {
                Text("Save Profile", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileInputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF475569)
        )
        Spacer(modifier = Modifier.height(3.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 12.sp, color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF0F172A))
        )
    }
}

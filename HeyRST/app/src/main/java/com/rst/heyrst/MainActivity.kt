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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rst.heyrst.accessibility.RSTAccessibilityService
import com.rst.heyrst.model.ProfileStorage
import com.rst.heyrst.model.UserProfile
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

        val prefs = getSharedPreferences("heyrst_prefs", Context.MODE_PRIVATE)
        isSystemActivated = prefs.getBoolean("is_system_activated", false) &&
                hasAudioPermission && hasOverlayPermission

        setContent {
            HeyRSTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8FAFD)
                ) {
                    FormmateAppScreen(
                        isActivated = isSystemActivated,
                        onToggleActivated = { handleSystemToggle(it) },
                        hasAudio = hasAudioPermission,
                        hasOverlay = hasOverlayPermission,
                        hasAccessibility = hasAccessibilityPermission,
                        engineStatus = AssistantOverlayService.engineStatus,
                        onRequestPermissions = { requestAudioAndNotificationPermissions() },
                        onRequestOverlay = { openOverlaySettings() },
                        onRequestAccessibility = { openAccessibilitySettings() },
                        onOpenAssistant = { sendServiceAction(AssistantOverlayService.ACTION_SHOW_OVERLAY) },
                        onPracticeDemo = { sendServiceAction(AssistantOverlayService.ACTION_TEST_DEMO_FORM) }
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
fun FormmateAppScreen(
    isActivated: Boolean,
    onToggleActivated: (Boolean) -> Unit,
    hasAudio: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    engineStatus: String,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenAssistant: () -> Unit,
    onPracticeDemo: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var profiles by remember { mutableStateOf(ProfileStorage.getProfiles(context)) }
    var activeProfile by remember { mutableStateOf(ProfileStorage.getActiveProfile(context)) }
    var isEditingProfile by remember { mutableStateOf(profiles.isEmpty()) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header: Formmate Logo & Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Formmate",
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                    text = "Open Formmate beside any webpage to detect form fields, answer naturally by voice or text, and fill forms effortlessly.",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Profile Selector or Creation Trigger
                if (!isEditingProfile) {
                    if (profiles.isNotEmpty()) {
                        Text(
                            text = "Choose a profile",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Active profile capsule
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
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
                                Text(text = "▼", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Create | Edit | Delete
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
                        // Empty state: direct prompt to create first profile
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

        Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Hey RST Listener",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isActivated) "Active • Mic is ON and listening for 'Hey RST'" else "Deactivated • Mic is OFF",
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

        // Permissions Status
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
                PermissionRow("3. Accessibility Service (Chrome Form Sync)", hasAccessibility, onRequestAccessibility)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big Primary Button: "Open Assistant"
        Button(
            onClick = onOpenAssistant,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
        ) {
            Text(
                text = "Open Assistant",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(onClick = onPracticeDemo) {
            Text("Practice with Registration Demo Form", fontSize = 13.sp, color = Color(0xFF2563EB))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Ready • No login required",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Text(
            text = "Formmate assists you in filling forms; you review and submit.",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8)
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
 * Profile name, Full name, First name, Last name, Email, Phone, Date of birth, Gender,
 * Parent / guardian name, Street address, City / town, District, State / province,
 * Country, Postal / PIN code, Age, Occupation, Course / stream, Education / qualification,
 * School / institution.
 */
@Composable
fun ProfileEditorForm(
    initialProfile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onCancel: () -> Unit
) {
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

        // Phone | Date of Birth
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Phone", phone, "10-digit mobile") { phone = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Date of birth", dateOfBirth, "dd/mm/yyyy 📅") { dateOfBirth = it }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Gender | Parent / guardian name
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ProfileInputField("Gender", gender, "e.g. Male / Female") { gender = it }
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

        // PIN Code | Age
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

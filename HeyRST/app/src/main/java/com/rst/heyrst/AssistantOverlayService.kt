package com.rst.heyrst

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
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
import com.rst.heyrst.accessibility.RSTAccessibilityService
import com.rst.heyrst.engine.FormmateEngine
import com.rst.heyrst.engine.RSTSoundEffects
import com.rst.heyrst.model.ConversationState
import com.rst.heyrst.model.FieldKind
import com.rst.heyrst.model.FormField
import com.rst.heyrst.model.OptionItem
import com.rst.heyrst.model.SemanticType
import com.rst.heyrst.model.VoiceCommand
import com.rst.heyrst.ui.FormmateOverlayView
import com.rst.heyrst.ui.theme.HeyRSTTheme
import java.util.Locale

class AssistantOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        const val ACTION_START_LISTENING = "com.rst.heyrst.ACTION_START_LISTENING"
        const val ACTION_SHOW_OVERLAY = "com.rst.heyrst.ACTION_SHOW_OVERLAY"
        const val ACTION_TEST_DEMO_FORM = "com.rst.heyrst.ACTION_TEST_DEMO_FORM"
        private const val CHANNEL_ID = "rst_assistant_channel"
        private const val NOTIFICATION_ID = 1001

        var engineStatus by mutableStateOf("Stopped")
        var lastHeardSpeech by mutableStateOf("Nothing heard yet")
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var wakeWordEngine: RSTWakeWordEngine? = null

    // TTS & Voice Recognition for form questions and answers
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var answerRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Observable states for Compose UI
    private var conversationState by mutableStateOf(ConversationState.IDLE)
    private var currentPrompt by mutableStateOf("Hello! I'm your Hey RST form assistant.")
    private var userTranscript by mutableStateOf("")
    private var micVolumeRms by mutableFloatStateOf(0f)
    private var lastConfirmedValue by mutableStateOf<String?>(null)
    private var isHindi by mutableStateOf(false)

    // Form fields model
    private var formFields by mutableStateOf<List<FormField>>(emptyList())
    private var currentFieldIndex by mutableIntStateOf(0)

    // 8-Second Inactivity Watchdog
    private val inactivityRunnable = Runnable {
        if (conversationState == ConversationState.LISTENING) {
            stopAnswerListening()
            conversationState = ConversationState.WAITING_FOR_TAP
            currentPrompt = if (isHindi) "अपना समय लें। तैयार होने पर टैप करें।" else "Take your time. Tap when you're ready."
        }
    }

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

        initTextToSpeech()
        startForegroundServiceNotification()
        startWakeWordEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                activateAssistantFormmateFlow()
            }
            ACTION_TEST_DEMO_FORM -> {
                loadDemoStudentForm()
            }
            ACTION_START_LISTENING -> {
                wakeWordEngine?.start()
            }
        }
        return START_STICKY
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val loc = if (isHindi) Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-IN")
                textToSpeech?.language = loc
                textToSpeech?.setSpeechRate(0.96f)
                textToSpeech?.setPitch(1.02f)

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            conversationState = ConversationState.SPEAKING
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.postDelayed({
                            if (composeView != null && conversationState == ConversationState.SPEAKING) {
                                // Acoustic transition chime E5 -> A5
                                RSTSoundEffects.playListeningStart()
                                conversationState = ConversationState.LISTENING
                                startListeningForAnswer()
                            }
                        }, 200L)
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            conversationState = ConversationState.WAITING_FOR_TAP
                        }
                    }
                })
            }
        }
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey RST Form Companion",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hey RST Form Companion Active")
            .setContentText("Say \"Hey RST\" inside Chrome anytime to fill forms")
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

    private fun startWakeWordEngine() {
        wakeWordEngine = RSTWakeWordEngine(
            context = applicationContext,
            onWakeWordDetected = { _ ->
                mainHandler.post {
                    activateAssistantFormmateFlow()
                }
            },
            onLiveTranscript = { text, isFinal ->
                lastHeardSpeech = text
            },
            onVolumeChanged = { rms ->
                if (conversationState != ConversationState.LISTENING) {
                    micVolumeRms = rms
                }
            },
            onDebugStatus = { status ->
                engineStatus = status
            }
        )
        wakeWordEngine?.start()
    }

    /**
     * Triggered when user says "Hey RST" or clicks "Test Floating Overlay"
     */
    private fun activateAssistantFormmateFlow() {
        // 1. Pause wake word engine while assistant overlay is interacting
        wakeWordEngine?.pause()

        // 2. Scan active screen for Chrome form fields
        val scanned = RSTAccessibilityService.scanActiveWindow()
        if (scanned.isNotEmpty()) {
            formFields = scanned
            currentFieldIndex = 0
            showOverlayUI()
            startConversationalLoop()
        } else {
            // No fields detected on current screen; offer quick demo form or prompt user
            showOverlayUI()
            conversationState = ConversationState.SCANNING
            currentPrompt = if (isHindi) {
                "मैंने Chrome में कोई खुला फ़ॉर्म नहीं पाया। कृपया Chrome में अपना फ़ॉर्म खोलें, या अभ्यास के लिए डेमो फ़ॉर्म आज़माएँ।"
            } else {
                "I didn't detect an active form in Chrome yet. Open a webpage with form fields, or tap below to test with a demo form!"
            }
            speakAssistant(currentPrompt)
        }
    }

    fun loadDemoStudentForm() {
        wakeWordEngine?.pause()
        formFields = getDemoStudentForm()
        currentFieldIndex = 0
        showOverlayUI()
        startConversationalLoop()
    }

    private fun startConversationalLoop() {
        if (formFields.isEmpty()) return
        val firstField = formFields[currentFieldIndex]
        val lang = if (isHindi) "hi-IN" else "en-IN"
        val welcome = if (isHindi) {
            "नमस्ते! मैं Hey RST हूँ। चलिए मिलकर यह फ़ॉर्म भरते हैं। सबसे पहले, " + FormmateEngine.generateHumanPrompt(firstField, lang)
        } else {
            "Hello! I'm Hey RST, your form companion. Let's fill out this form together! First, " + FormmateEngine.generateHumanPrompt(firstField, lang)
        }
        currentPrompt = welcome
        speakAssistant(welcome)
    }

    private fun speakAssistant(text: String) {
        if (!ttsReady || textToSpeech == null) return
        stopInactivityWatchdog()
        stopAnswerListening()

        conversationState = ConversationState.SPEAKING
        userTranscript = ""

        val loc = if (isHindi) Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-IN")
        textToSpeech?.language = loc

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "formmate_utterance_${System.currentTimeMillis()}")
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "formmate_utterance")
    }

    private fun startListeningForAnswer() {
        stopInactivityWatchdog()
        resetAnswerRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            answerRecognizer?.startListening(intent)
            resetInactivityWatchdog()
        } catch (e: Exception) {
            e.printStackTrace()
            conversationState = ConversationState.WAITING_FOR_TAP
        }
    }

    private fun resetAnswerRecognizer() {
        try {
            answerRecognizer?.stopListening()
            answerRecognizer?.cancel()
            answerRecognizer?.destroy()
        } catch (_: Exception) {}

        answerRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    conversationState = ConversationState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    stopInactivityWatchdog()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    micVolumeRms = rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    conversationState = ConversationState.PROCESSING
                }

                override fun onError(error: Int) {
                    if (conversationState == ConversationState.LISTENING) {
                        conversationState = ConversationState.WAITING_FOR_TAP
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val rawAnswer = matches?.firstOrNull() ?: ""
                    if (rawAnswer.isNotBlank()) {
                        userTranscript = rawAnswer
                        handleUserResponse(rawAnswer)
                    } else {
                        conversationState = ConversationState.WAITING_FOR_TAP
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { userTranscript = it }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleUserResponse(rawText: String) {
        stopInactivityWatchdog()
        conversationState = ConversationState.PROCESSING

        // 1. Check for Voice Control Commands
        val command = FormmateEngine.recognizeCommand(rawText)
        if (command != null) {
            handleVoiceCommand(command)
            return
        }

        // 2. Target Field Association & Value Normalization
        val activeField = formFields.getOrNull(currentFieldIndex)
        if (activeField == null) {
            finishForm()
            return
        }

        val normalizedValue = normalizeValueForField(activeField, rawText)
        if (normalizedValue == null) {
            // Unrecognized or invalid answer
            val retryPrompt = if (isHindi) {
                "मैं “$rawText” को समझ नहीं पाया। कृपया दोबारा बोलें।"
            } else {
                "I couldn't quite catch that. Could you please say that again?"
            }
            currentPrompt = retryPrompt
            speakAssistant(retryPrompt)
            return
        }

        // 3. Populate field in Chrome via AccessibilityService
        RSTAccessibilityService.fillField(activeField.id, normalizedValue)

        // 4. Acoustic Confirmation Chime (C5 -> G5)
        RSTSoundEffects.playSuccess()

        // 5. Update local state
        lastConfirmedValue = normalizedValue
        val updatedList = formFields.toMutableList()
        updatedList[currentFieldIndex] = activeField.copy(currentValue = normalizedValue)
        formFields = updatedList

        // 6. Advance to next field or finish
        val nextIndex = currentFieldIndex + 1
        if (nextIndex < formFields.size) {
            currentFieldIndex = nextIndex
            val nextField = formFields[nextIndex]
            val lang = if (isHindi) "hi-IN" else "en-IN"
            val confirmation = FormmateEngine.generateConfirmationMessage(activeField, normalizedValue, nextField, lang)
            currentPrompt = confirmation
            speakAssistant(confirmation)
        } else {
            finishForm()
        }
    }

    private fun normalizeValueForField(field: FormField, rawInput: String): String? {
        return when (field.kind) {
            FieldKind.CHECKBOX -> {
                val bool = FormmateEngine.parseBooleanAnswer(rawInput)
                when (bool) {
                    true -> "Yes, I agree (Checked)"
                    false -> "Unchecked"
                    null -> null
                }
            }
            FieldKind.SELECT, FieldKind.RADIO -> {
                val matchRes = FormmateEngine.matchOption(field.options, rawInput, field.semanticType)
                if (matchRes.matched != null) {
                    matchRes.matched.label
                } else if (matchRes.ambiguous) {
                    null
                } else {
                    FormmateEngine.stripConversationalFraming(rawInput, field.semanticType)
                }
            }
            else -> {
                when (field.semanticType) {
                    SemanticType.DATE, SemanticType.DATE_OF_BIRTH -> {
                        FormmateEngine.parseSpokenDate(rawInput) ?: rawInput
                    }
                    SemanticType.EMAIL -> {
                        FormmateEngine.parseSpokenEmail(rawInput) ?: rawInput.replace(" ", "")
                    }
                    SemanticType.PHONE -> {
                        FormmateEngine.parseSpokenPhone(rawInput) ?: rawInput.replace(Regex("\\D"), "")
                    }
                    SemanticType.PINCODE -> {
                        val digits = rawInput.replace(Regex("\\D"), "")
                        if (digits.length == 6) digits else rawInput
                    }
                    SemanticType.FULL_NAME, SemanticType.FIRST_NAME, SemanticType.LAST_NAME -> {
                        val stripped = FormmateEngine.stripConversationalFraming(rawInput, field.semanticType)
                        stripped.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    }
                    else -> {
                        FormmateEngine.stripConversationalFraming(rawInput, field.semanticType)
                    }
                }
            }
        }
    }

    private fun handleVoiceCommand(cmd: VoiceCommand) {
        when (cmd) {
            VoiceCommand.REPEAT -> {
                val active = formFields.getOrNull(currentFieldIndex) ?: return
                val lang = if (isHindi) "hi-IN" else "en-IN"
                val p = FormmateEngine.generateHumanPrompt(active, lang)
                currentPrompt = p
                speakAssistant(p)
            }
            VoiceCommand.SKIP, VoiceCommand.NEXT_QUESTION -> {
                val nextIdx = currentFieldIndex + 1
                if (nextIdx < formFields.size) {
                    currentFieldIndex = nextIdx
                    val active = formFields[nextIdx]
                    val lang = if (isHindi) "hi-IN" else "en-IN"
                    val p = "Skipping. Next, " + FormmateEngine.generateHumanPrompt(active, lang)
                    currentPrompt = p
                    speakAssistant(p)
                } else {
                    finishForm()
                }
            }
            VoiceCommand.GO_BACK -> {
                if (currentFieldIndex > 0) {
                    currentFieldIndex--
                    val active = formFields[currentFieldIndex]
                    val lang = if (isHindi) "hi-IN" else "en-IN"
                    val p = "Returning to previous field. " + FormmateEngine.generateHumanPrompt(active, lang)
                    currentPrompt = p
                    speakAssistant(p)
                }
            }
            VoiceCommand.START_AGAIN -> {
                currentFieldIndex = 0
                val active = formFields[0]
                val lang = if (isHindi) "hi-IN" else "en-IN"
                val p = "Restarting form. First, " + FormmateEngine.generateHumanPrompt(active, lang)
                currentPrompt = p
                speakAssistant(p)
            }
            VoiceCommand.STOP_LISTENING -> {
                hideOverlayUI()
            }
            else -> {}
        }
    }

    private fun finishForm() {
        conversationState = ConversationState.COMPLETED
        RSTSoundEffects.playComplete()

        val completionMsg = if (isHindi) {
            "बहुत बढ़िया! सभी फ़ील्ड भर दिए गए हैं। कृपया पृष्ठ पर अपने विवरणों की समीक्षा करें और स्वयं सबमिट करें।"
        } else {
            "All fields have been filled! Please review your answers on the page and submit when ready."
        }
        currentPrompt = completionMsg
        speakAssistant(completionMsg)

        mainHandler.postDelayed({
            hideOverlayUI()
        }, 6000L)
    }

    private fun resetInactivityWatchdog() {
        mainHandler.removeCallbacks(inactivityRunnable)
        mainHandler.postDelayed(inactivityRunnable, 8000L)
    }

    private fun stopInactivityWatchdog() {
        mainHandler.removeCallbacks(inactivityRunnable)
    }

    private fun stopAnswerListening() {
        try {
            answerRecognizer?.stopListening()
            answerRecognizer?.cancel()
        } catch (_: Exception) {}
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
                    FormmateOverlayView(
                        state = conversationState,
                        activeField = formFields.getOrNull(currentFieldIndex),
                        fieldIndex = currentFieldIndex,
                        totalFields = formFields.size,
                        assistantPrompt = currentPrompt,
                        userTranscript = userTranscript,
                        lastConfirmedValue = lastConfirmedValue,
                        micVolumeRms = micVolumeRms,
                        isHindi = isHindi,
                        onHeroTap = {
                            if (conversationState == ConversationState.LISTENING) {
                                stopAnswerListening()
                                conversationState = ConversationState.WAITING_FOR_TAP
                            } else {
                                RSTSoundEffects.playListeningStart()
                                conversationState = ConversationState.LISTENING
                                startListeningForAnswer()
                            }
                        },
                        onSkip = { handleVoiceCommand(VoiceCommand.SKIP) },
                        onGoBack = { handleVoiceCommand(VoiceCommand.GO_BACK) },
                        onRepeat = { handleVoiceCommand(VoiceCommand.REPEAT) },
                        onToggleLanguage = {
                            isHindi = !isHindi
                            val active = formFields.getOrNull(currentFieldIndex)
                            if (active != null) {
                                val lang = if (isHindi) "hi-IN" else "en-IN"
                                val p = FormmateEngine.generateHumanPrompt(active, lang)
                                currentPrompt = p
                                speakAssistant(p)
                            }
                        },
                        onDismiss = { hideOverlayUI() }
                    )
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
    }

    private fun hideOverlayUI() {
        stopInactivityWatchdog()
        stopAnswerListening()
        textToSpeech?.stop()

        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }

        // Resume continuous wake word listening
        wakeWordEngine?.resume()
    }

    private fun getDemoStudentForm(): List<FormField> {
        return listOf(
            FormField("demo-1", "Full Name", FieldKind.INPUT, SemanticType.FULL_NAME, required = true),
            FormField("demo-2", "Email Address", FieldKind.INPUT, SemanticType.EMAIL, required = true),
            FormField("demo-3", "Mobile Number", FieldKind.INPUT, SemanticType.PHONE),
            FormField("demo-4", "Date of Birth", FieldKind.INPUT, SemanticType.DATE_OF_BIRTH),
            FormField(
                "demo-5", "State", FieldKind.SELECT, SemanticType.STATE,
                options = listOf(
                    OptionItem("Assam", "Assam"),
                    OptionItem("Meghalaya", "Meghalaya"),
                    OptionItem("Manipur", "Manipur"),
                    OptionItem("Tripura", "Tripura"),
                    OptionItem("Arunachal Pradesh", "Arunachal Pradesh"),
                    OptionItem("West Bengal", "West Bengal")
                )
            ),
            FormField(
                "demo-6", "Gender", FieldKind.RADIO, SemanticType.GENDER,
                options = listOf(
                    OptionItem("Female", "Female"),
                    OptionItem("Male", "Male"),
                    OptionItem("Prefer not to say", "Prefer not to say")
                )
            ),
            FormField("demo-7", "Residential Address", FieldKind.TEXTAREA, SemanticType.ADDRESS),
            FormField("demo-8", "PIN Code", FieldKind.INPUT, SemanticType.PINCODE),
            FormField("demo-9", "I agree to the terms and declaration", FieldKind.CHECKBOX, SemanticType.CONSENT, required = true)
        )
    }

    override fun onDestroy() {
        wakeWordEngine?.stop()
        stopInactivityWatchdog()
        stopAnswerListening()
        textToSpeech?.shutdown()
        hideOverlayUI()
        store.clear()
        super.onDestroy()
    }
}

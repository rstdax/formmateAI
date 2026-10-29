package com.rst.heyrst

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.math.min

/**
 * High-resilience Wake Word Engine for "Hey RST".
 * Features:
 * - Robust background listening in foreground service with watchdog self-healing
 * - Extensive mispronunciation and regional accent training (hair style, hairstyle, hairsty, heiaresti, etc.)
 * - Token-level and Levenshtein phonetic fuzzy matching
 */
class RSTWakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (initialCommand: String) -> Unit,
    private val onLiveTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onVolumeChanged: (rmsDb: Float) -> Unit,
    private val onDebugStatus: (status: String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isPaused = false
    private var isRestartScheduled = false
    private var consecutiveErrors = 0

    // Watchdog to prevent native SpeechRecognizer from freezing silently in background
    private val watchdogTimeoutMs = 8000L
    private val watchdogRunnable = Runnable {
        if (isRunning && !isPaused) {
            onDebugStatus("Watchdog: Refreshing listener...")
            recreateAndStartRecognizer()
        }
    }

    companion object {
        // Direct accent and phonetic mispronunciation variations observed in testing
        val KNOWN_WAKE_PATTERNS = listOf(
            // User-reported mispronounced phrases
            "hair style", "hairstyle", "hairsty", "hair stay", "hair", "hare style", "hare",
            "heiaresti", "hey aresti", "hey arest", "hey arrest", "hey artist", "hey artiste",
            "aresti", "aresty", "arest", "heia",

            // Phonetic letter-by-letter variations
            "hey rst", "hey r s t", "hey r s d", "hey r s", "hey ar es ti", "hey ar es tee",
            "hey ares", "hey arasti", "hey r st", "hey rs t", "hey rs", "heyrst",
            "a r s t", "ar es ti", "ares ti", "are st", "rs t", "r s t", "rst",

            // Rusty / Accent variations
            "hey rusty", "hey rust", "rusty", "rust", "heya rst", "heya resti", "hai rst",
            "hay rst", "he rst", "hi rst", "hi r s t", "hello rst", "hello r s t",
            "ok rst", "okay rst", "okay r s t", "ay rst", "ai rst",

            // Common phonetic distortions
            "hey std", "hey rsd", "hey reset", "hey rest", "hey recipe", "hey rashi",
            "hey stay", "hey state", "hey start"
        )

        /**
         * Determines if an utterance is intended as "Hey RST", tolerating regional accents
         * and common phonetic misrecognitions.
         */
        fun isWakePhraseMatch(rawInput: String): Pair<Boolean, String> {
            val normalized = normalizePhonetics(rawInput)
            if (normalized.isBlank()) return Pair(false, "")

            // 1. Direct match in dictionary
            for (pattern in KNOWN_WAKE_PATTERNS) {
                if (normalized == pattern || normalized.startsWith("$pattern ") || normalized.contains(" $pattern")) {
                    val command = normalized.substringAfter(pattern).trim()
                    return Pair(true, command)
                }
            }

            // 2. Token-level heuristic (e.g. "he... resti", "hair... rst")
            val words = normalized.split(Regex("\\s+"))
            if (words.size in 1..4) {
                val first = words[0]
                val isHeyLike = first in listOf("hey", "he", "hi", "hay", "ay", "hair", "hare", "heia", "ai", "a")
                val isRstLike = words.any { w ->
                    w in listOf("rst", "aresti", "areste", "style", "sty", "rusty", "rust", "arrest", "artiste", "rs", "sd") ||
                            (w.startsWith("rst") || w.startsWith("arest") || w.startsWith("styl") || w.startsWith("rust"))
                }
                if (isHeyLike && isRstLike) {
                    return Pair(true, "")
                }
            }

            // 3. Levenshtein Fuzzy Match against primary triggers
            val targets = listOf("heyrst", "hey rst", "hairstyle", "heiaresti", "aresti", "heyr s t")
            val squashed = normalized.replace(" ", "")
            for (target in targets) {
                val targetSquashed = target.replace(" ", "")
                val dist = levenshteinDistance(squashed, targetSquashed)
                val maxLen = maxOf(squashed.length, targetSquashed.length)
                if (maxLen > 0) {
                    val similarity = 1.0 - (dist.toDouble() / maxLen.toDouble())
                    if (similarity >= 0.72 || dist <= 2) {
                        return Pair(true, "")
                    }
                }
            }

            return Pair(false, "")
        }

        fun normalizePhonetics(input: String): String {
            return input.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun levenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length
            val dp = Array(m + 1) { IntArray(n + 1) }

            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j

            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = min(
                        dp[i - 1][j] + 1,
                        min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                    )
                }
            }
            return dp[m][n]
        }
    }

    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra("android.speech.extra.DICTATION_MODE", true)
        // Silence tolerances
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        isPaused = false
        consecutiveErrors = 0
        mainHandler.post { initAndStartRecognizer() }
    }

    fun pause() {
        isPaused = true
        isRestartScheduled = false
        stopWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (!isRunning) return
        isPaused = false
        consecutiveErrors = 0
        mainHandler.post { initAndStartRecognizer() }
    }

    fun stop() {
        isRunning = false
        isPaused = false
        isRestartScheduled = false
        stopWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initAndStartRecognizer() {
        if (!isRunning || isPaused) return
        resetWatchdog()

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
            }
            speechRecognizer?.startListening(recognizerIntent)
            onDebugStatus("Listening for 'Hey RST'...")
        } catch (e: Exception) {
            onDebugStatus("Init error: ${e.message}")
            scheduleRestart(800L)
        }
    }

    private fun recreateAndStartRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        initAndStartRecognizer()
    }

    private fun scheduleRestart(delayMs: Long = 200L) {
        if (!isRunning || isPaused || isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (isRunning && !isPaused) {
                if (consecutiveErrors >= 3) {
                    consecutiveErrors = 0
                    recreateAndStartRecognizer()
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {}
                    initAndStartRecognizer()
                }
            }
        }, delayMs)
    }

    private fun resetWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, watchdogTimeoutMs)
    }

    private fun stopWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            resetWatchdog()
            consecutiveErrors = 0
            if (!isPaused) {
                onDebugStatus("Active — Say 'Hey RST'")
            }
        }

        override fun onBeginningOfSpeech() {
            resetWatchdog()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (!isPaused) {
                onVolumeChanged(rmsdB.coerceIn(0f, 12f))
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            scheduleRestart(100L)
        }

        override fun onError(error: Int) {
            if (isPaused) return
            consecutiveErrors++

            // On severe client/busy errors, clean reset
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            ) {
                recreateAndStartRecognizer()
            } else {
                scheduleRestart(180L)
            }
        }

        override fun onResults(results: Bundle?) {
            resetWatchdog()
            if (isPaused) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                processSpeechCandidates(matches, isFinal = true)
            }
            scheduleRestart(100L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            resetWatchdog()
            if (isPaused) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                processSpeechCandidates(matches, isFinal = false)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processSpeechCandidates(candidates: List<String>, isFinal: Boolean) {
        val primaryText = candidates.firstOrNull() ?: return
        onLiveTranscript(primaryText, isFinal)

        for (rawPhrase in candidates) {
            val (isMatch, command) = isWakePhraseMatch(rawPhrase)
            if (isMatch) {
                onWakeWordDetected(command)
                break
            }
        }
    }
}

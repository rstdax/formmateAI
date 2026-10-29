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
    private var isRestartScheduled = false

    // Phonetic variations that STT engines produce when a user says "Hey RST"
    private val wakeWordPatterns = listOf(
        "hey rst", "hey r s t", "hey r s d", "hey r s", "hey ar es ti",
        "hey ares", "hey arasti", "hey rusty", "hey r st", "hey rs t",
        "hi rst", "hi r s t", "hello rst", "hello r s t", "ok rst",
        "okay rst", "okay r s t", "hey arrest", "hey artiste", "rst"
    )

    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        // Extended silence tolerances so the listener stays open longer per cycle
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        mainHandler.post { initAndStartRecognizer() }
    }

    fun stop() {
        isRunning = false
        isRestartScheduled = false
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
        if (!isRunning) return

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
            scheduleRestart(1000L)
        }
    }

    private fun scheduleRestart(delayMs: Long = 250L) {
        if (!isRunning || isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (isRunning) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {}
                initAndStartRecognizer()
            }
        }, delayMs)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onDebugStatus("Active — Say 'Hey RST'")
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            onVolumeChanged(rmsdB.coerceIn(0f, 12f))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            scheduleRestart(150L)
        }

        override fun onError(error: Int) {
            // Reset recognizer instance if client/busy errors occur
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT
            ) {
                try {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                } catch (_: Exception) {}
                scheduleRestart(600L)
            } else {
                // Normal silence/no-match timeout; immediately continue listening
                scheduleRestart(150L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                processSpeechCandidates(matches, isFinal = true)
            }
            scheduleRestart(150L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
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
            val normalized = normalizePhonetics(rawPhrase)
            val matchedWakeWord = wakeWordPatterns.firstOrNull { pattern ->
                normalized.contains(pattern)
            }

            if (matchedWakeWord != null) {
                // Extract any command spoken right after "Hey RST"
                val commandAfterWake = normalized.substringAfter(matchedWakeWord).trim()
                onWakeWordDetected(commandAfterWake)
                break
            }
        }
    }

    private fun normalizePhonetics(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ") // Strip punctuation like R.S.T. -> r s t
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
package com.rst.heyrst.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

/**
 * Android acoustic synthesizer matching FormmateSoundEffects (Web Audio API).
 * Synthesizes pure harmonic chimes and feedback tones directly in-memory via AudioTrack:
 * - Listening Start Chime: E5 (659.25 Hz) -> A5 (880.00 Hz)
 * - Field Auto-Fill Success: C5 (523.25 Hz) -> G5 (783.99 Hz)
 * - Tap Tone: 440 Hz -> 280 Hz droplet
 * - Form Complete Celebration: C5 -> E5 -> G5 -> C6 ascending chord
 */
object RSTSoundEffects {

    var isEnabled: Boolean = true
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Dual-tone ascending chime (E5 659.25 Hz -> A5 880.00 Hz)
     * Plays when assistant finishes speaking and microphone opens.
     */
    fun playListeningStart() {
        if (!isEnabled) return
        scope.launch {
            val durationSec = 0.38
            val numSamples = (durationSec * SAMPLE_RATE).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE

                // Note 1: E5 (659.25 Hz) from t = 0 to 0.28s
                var sample1 = 0.0
                if (t in 0.0..0.28) {
                    val env1 = if (t < 0.02) t / 0.02 else exp(-(t - 0.02) * 12.0)
                    sample1 = sin(2.0 * Math.PI * 659.25 * t) * env1 * 0.4
                }

                // Note 2: A5 (880.00 Hz) from t = 0.08 to 0.38s
                var sample2 = 0.0
                if (t >= 0.08) {
                    val dt = t - 0.08
                    val env2 = if (dt < 0.02) dt / 0.02 else exp(-(dt - 0.02) * 10.0)
                    sample2 = sin(2.0 * Math.PI * 880.00 * t) * env2 * 0.5
                }

                val mixed = (sample1 + sample2).coerceIn(-1.0, 1.0)
                buffer[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    /**
     * Double confirmation chime (C5 523.25 Hz -> G5 783.99 Hz)
     * Plays when an answer is normalized and populated into the active field.
     */
    fun playSuccess() {
        if (!isEnabled) return
        scope.launch {
            val durationSec = 0.35
            val numSamples = (durationSec * SAMPLE_RATE).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE

                var sample1 = 0.0
                if (t in 0.0..0.22) {
                    val env1 = if (t < 0.02) t / 0.02 else exp(-(t - 0.02) * 14.0)
                    sample1 = sin(2.0 * Math.PI * 523.25 * t) * env1 * 0.4
                }

                var sample2 = 0.0
                if (t >= 0.09) {
                    val dt = t - 0.09
                    val env2 = if (dt < 0.02) dt / 0.02 else exp(-(dt - 0.02) * 11.0)
                    sample2 = sin(2.0 * Math.PI * 783.99 * t) * env2 * 0.5
                }

                val mixed = (sample1 + sample2).coerceIn(-1.0, 1.0)
                buffer[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    /**
     * Subtle tap droplet tone (440 Hz -> 280 Hz)
     */
    fun playTap() {
        if (!isEnabled) return
        scope.launch {
            val durationSec = 0.06
            val numSamples = (durationSec * SAMPLE_RATE).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val freq = 440.0 - (160.0 * (t / durationSec))
                val env = exp(-t * 40.0)
                val sample = sin(2.0 * Math.PI * freq * t) * env * 0.3
                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    /**
     * Celebratory 4-note ascending chord (C5 -> E5 -> G5 -> C6)
     */
    fun playComplete() {
        if (!isEnabled) return
        scope.launch {
            val durationSec = 0.7
            val numSamples = (durationSec * SAMPLE_RATE).toInt()
            val buffer = ShortArray(numSamples)
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
            val delays = doubleArrayOf(0.0, 0.08, 0.16, 0.24)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                var mixed = 0.0
                for (n in notes.indices) {
                    val start = delays[n]
                    if (t >= start) {
                        val dt = t - start
                        val env = if (dt < 0.02) dt / 0.02 else exp(-(dt - 0.02) * 6.0)
                        mixed += sin(2.0 * Math.PI * notes[n] * t) * env * 0.25
                    }
                }
                buffer[i] = (mixed.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(buffer.size * 2, minBufSize)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            // Allow static buffer playback to complete
            val durationMs = (buffer.size.toDouble() / SAMPLE_RATE * 1000).toLong() + 50L
            Thread.sleep(durationMs)
        } catch (_: Exception) {
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }
}

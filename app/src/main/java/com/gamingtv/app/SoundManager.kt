package com.gamingtv.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.*

object SoundManager {

    // ── SON STYLE PS2 AU DÉMARRAGE ──
    fun playPS2Intro(context: Context) {
        Thread {
            try {
                val sampleRate = 44100
                val duration = 4.0 // secondes

                // Générer le son PS2-style en plusieurs phases
                val samples = generatePS2Sound(sampleRate, duration)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                Thread.sleep((duration * 1000).toLong())
                audioTrack.stop()
                audioTrack.release()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun generatePS2Sound(sampleRate: Int, duration: Double): ShortArray {
        val totalSamples = (sampleRate * duration * 2).toInt() // stéréo
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples / 2) {
            val t = i.toDouble() / sampleRate
            val sample: Double

            sample = when {
                // Phase 1 (0-0.3s): Bruit statique électronique
                t < 0.3 -> {
                    val noise = (Math.random() * 2 - 1) * 0.3
                    val hum = sin(2 * PI * 60 * t) * 0.1
                    (noise + hum) * fadeIn(t, 0.0, 0.1) * fadeOut(t, 0.2, 0.3)
                }

                // Phase 2 (0.3-0.8s): Montée grave — le "BOOM" PS2
                t < 0.8 -> {
                    val boom = sin(2 * PI * 40 * t) * exp(-(t - 0.3) * 3)
                    val sub = sin(2 * PI * 25 * t) * exp(-(t - 0.3) * 2) * 0.8
                    val mid = sin(2 * PI * 80 * t) * exp(-(t - 0.3) * 5) * 0.3
                    (boom + sub + mid) * 0.9 * fadeIn(t, 0.3, 0.5)
                }

                // Phase 3 (0.8-1.5s): Chord musical PS2 (mi + sol + si)
                t < 1.5 -> {
                    val chord1 = sin(2 * PI * 329.63 * t) // Mi4
                    val chord2 = sin(2 * PI * 392.00 * t) // Sol4
                    val chord3 = sin(2 * PI * 493.88 * t) // Si4
                    val chord4 = sin(2 * PI * 164.81 * t) // Mi3 (octave basse)
                    val env = exp(-(t - 0.8) * 1.5)
                    (chord1 + chord2 * 0.8 + chord3 * 0.6 + chord4 * 0.4) * env * 0.25
                }

                // Phase 4 (1.5-2.5s): Mélodie montante style PS2
                t < 2.5 -> {
                    val noteFreq = when {
                        t < 1.7 -> 261.63  // Do4
                        t < 1.9 -> 293.66  // Re4
                        t < 2.1 -> 329.63  // Mi4
                        t < 2.3 -> 392.00  // Sol4
                        else    -> 523.25  // Do5
                    }
                    val noteEnv = sin(PI * ((t - floor((t - 1.5) / 0.2) * 0.2 - 1.5) / 0.2))
                    val harmonic = sin(2 * PI * noteFreq * t)
                    val harmonic2 = sin(2 * PI * noteFreq * 2 * t) * 0.3
                    (harmonic + harmonic2) * noteEnv.coerceIn(0.0, 1.0) * 0.3
                }

                // Phase 5 (2.5-3.5s): Accord final + réverb
                t < 3.5 -> {
                    val env = exp(-(t - 2.5) * 0.8)
                    val finalChord =
                        sin(2 * PI * 523.25 * t) +      // Do5
                        sin(2 * PI * 659.25 * t) * 0.7 + // Mi5
                        sin(2 * PI * 783.99 * t) * 0.5 + // Sol5
                        sin(2 * PI * 261.63 * t) * 0.4   // Do4 (basse)
                    // Légère réverb simulée
                    val reverb = if (t > 2.6) sin(2 * PI * 523.25 * (t - 0.05)) * 0.15 * exp(-(t - 2.6) * 2) else 0.0
                    (finalChord + reverb) * env * 0.25
                }

                // Phase 6 (3.5-4.0s): Fade out
                else -> {
                    val env = exp(-(t - 3.5) * 5)
                    sin(2 * PI * 523.25 * t) * env * 0.1
                }
            }

            val shortVal = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            // Stéréo (left + right)
            samples[i * 2] = shortVal
            samples[i * 2 + 1] = shortVal
        }

        return samples
    }

    private fun fadeIn(t: Double, start: Double, end: Double): Double {
        if (t <= start) return 0.0
        if (t >= end) return 1.0
        return (t - start) / (end - start)
    }

    private fun fadeOut(t: Double, start: Double, end: Double): Double {
        if (t <= start) return 1.0
        if (t >= end) return 0.0
        return 1.0 - (t - start) / (end - start)
    }

    // ── SON ALERTE SESSION ──
    fun playAlert(context: Context) {
        Thread {
            try {
                val sampleRate = 44100
                val samples = ShortArray(sampleRate / 2) // 0.5s
                for (i in samples.indices) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (t < 0.25) 880.0 else 660.0
                    val env = if (t < 0.05) t / 0.05 else if (t > 0.45) (0.5 - t) / 0.05 else 1.0
                    samples[i] = (sin(2 * PI * freq * t) * env * Short.MAX_VALUE * 0.5).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(500)
                track.stop()
                track.release()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
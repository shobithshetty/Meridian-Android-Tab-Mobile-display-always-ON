package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.example.data.ClockSettings
import kotlin.math.exp
import kotlin.math.sin

object AudioSynthEngine {
    private const val SAMPLE_RATE = 44100

    val BuiltInSoundNames = listOf(
        "Zen Bell",
        "Warm Temple Gong",
        "Digital Chirp",
        "Double Marimba",
        "Soft Beep",
        "System Accent"
    )

    private val playbackLock = Any()
    private var activeAudioTrack: AudioTrack? = null
    private var activeRingtone: Ringtone? = null
    private var activeMediaPlayer: MediaPlayer? = null

    /** Loudness (0..1) applied to whatever is played next. Alarms ramp it up; every other caller leaves it at 1. */
    @Volatile
    private var outputVolume = 1f

    private val isRunningTests: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /** The same attribution-tagged context every sound here uses (Android 11+ tracks it for app-ops). */
    internal fun attributionContext(context: Context): Context = wrapAttributionContext(context)

    private fun wrapAttributionContext(context: Context): Context {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (context.attributionTag == null) {
                context.createAttributionContext("default")
            } else {
                context
            }
        } else {
            context
        }
    }

    /**
     * Stops and releases any active AudioTrack, Ringtone, or MediaPlayer so rapid sound previews
     * never overlap or leak native audio handles.
     */
    fun stopActivePlayback() {
        synchronized(playbackLock) {
            try {
                activeAudioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
            } catch (_: Throwable) {
            } finally {
                activeAudioTrack = null
            }

            try {
                activeRingtone?.let { ringtone ->
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                activeRingtone = null
            }

            try {
                activeMediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                }
            } catch (_: Throwable) {
            } finally {
                activeMediaPlayer = null
            }
        }
    }

    /**
     * Plays the user's configured alert sound (Built-in synth, System Ringtone URI, or Custom Audio File URI)
     * with automatic fallback to synthesized chime if a custom URI is revoked or unreadable.
     */
    fun playConfiguredAlert(context: Context, settings: ClockSettings, volume: Float = 1f) {
        if (isRunningTests) return
        val attrContext = wrapAttributionContext(context)
        stopActivePlayback()
        val level = volume.coerceIn(0f, 1f)
        outputVolume = level

        when (settings.soundSourceType) {
            "CUSTOM_FILE" -> {
                if (settings.customSoundUri.isNotBlank()) {
                    playCustomFileUri(attrContext, settings.customSoundUri, settings.selectedSoundName)
                } else {
                    playSound(attrContext, settings.selectedSoundName, level)
                }
            }
            "RINGTONE" -> {
                if (settings.customSoundUri.isNotBlank()) {
                    playRingtoneUri(attrContext, settings.customSoundUri, settings.selectedSoundName)
                } else {
                    playSystemNotification(attrContext)
                }
            }
            else -> {
                playSound(attrContext, settings.selectedSoundName, level)
            }
        }
    }

    fun playUriPreview(
        context: Context,
        sourceType: String,
        uriString: String,
        fallbackBuiltin: String = "Zen Bell"
    ) {
        if (isRunningTests) return
        val attrContext = wrapAttributionContext(context)
        stopActivePlayback()
        outputVolume = 1f
        if (sourceType == "CUSTOM_FILE") {
            playCustomFileUri(attrContext, uriString, fallbackBuiltin)
        } else if (sourceType == "RINGTONE") {
            playRingtoneUri(attrContext, uriString, fallbackBuiltin)
        } else {
            playSound(attrContext, fallbackBuiltin)
        }
    }

    fun playSound(context: Context, soundName: String, volume: Float = 1f) {
        if (isRunningTests) return
        val attrContext = wrapAttributionContext(context)
        stopActivePlayback()
        outputVolume = volume.coerceIn(0f, 1f)

        if (soundName == "System Accent") {
            playSystemNotification(attrContext)
            return
        }

        Thread {
            try {
                when (soundName) {
                    "Zen Bell" -> playZenBell(attrContext)
                    "Warm Temple Gong" -> playWarmGong(attrContext)
                    "Digital Chirp" -> playDigitalChirp(attrContext)
                    "Double Marimba" -> playDoubleMarimba(attrContext)
                    "Soft Beep" -> playSoftBeep(attrContext)
                    else -> playZenBell(attrContext)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playCustomFileUri(context: Context, uriString: String, fallbackName: String) {
        Thread {
            try {
                val uri = Uri.parse(uriString)
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    setOnCompletionListener { player ->
                        synchronized(playbackLock) {
                            if (activeMediaPlayer === player) {
                                activeMediaPlayer = null
                            }
                        }
                        try {
                            player.release()
                        } catch (_: Throwable) {
                        }
                    }
                    setOnErrorListener { player, _, _ ->
                        synchronized(playbackLock) {
                            if (activeMediaPlayer === player) {
                                activeMediaPlayer = null
                            }
                        }
                        try {
                            player.release()
                        } catch (_: Throwable) {
                        }
                        playZenBell(context)
                        true
                    }
                    prepare()
                    setVolume(outputVolume, outputVolume)
                }
                synchronized(playbackLock) {
                    activeMediaPlayer = mp
                }
                mp.start()
            } catch (_: Throwable) {
                playSound(context, fallbackName, outputVolume)
            }
        }.start()
    }

    private fun playRingtoneUri(context: Context, uriString: String, fallbackName: String) {
        try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                synchronized(playbackLock) {
                    activeRingtone = ringtone
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) ringtone.volume = outputVolume
                ringtone.play()
            } else {
                playSound(context, fallbackName, outputVolume)
            }
        } catch (_: Throwable) {
            playSound(context, fallbackName, outputVolume)
        }
    }

    private fun playZenBell(context: Context? = null) {
        val duration = 1.8f
        val numSamples = (duration * SAMPLE_RATE).toInt()
        val buffer = ShortArray(numSamples)
        val freqPrimary = 880.0
        val freqResonant = 1320.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-2.2 * t)
            val valPrimary = sin(2.0 * Math.PI * freqPrimary * t) * 0.7
            val valResonant = sin(2.0 * Math.PI * freqResonant * t) * 0.3
            val sampleValue = ((valPrimary + valResonant) * envelope * Short.MAX_VALUE).toInt()
            buffer[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeAndPlay(context, buffer)
    }

    private fun playWarmGong(context: Context? = null) {
        val duration = 2.5f
        val numSamples = (duration * SAMPLE_RATE).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 130.81
        val freqOvertone = 261.63

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-1.5 * t)
            val vibrato = 1.0 + 0.04 * sin(2.0 * Math.PI * 5.0 * t)
            val valPrimary = sin(2.0 * Math.PI * freq * t * vibrato) * 0.8
            val valOvertone = sin(2.0 * Math.PI * freqOvertone * t) * 0.2
            val sampleValue = ((valPrimary + valOvertone) * envelope * Short.MAX_VALUE).toInt()
            buffer[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeAndPlay(context, buffer)
    }

    private fun playDigitalChirp(context: Context? = null) {
        val durationSingle = 0.10f
        val gap = 0.08f
        val numSamplesSingle = (durationSingle * SAMPLE_RATE).toInt()
        val numSamplesGap = (gap * SAMPLE_RATE).toInt()
        val totalSamples = numSamplesSingle * 2 + numSamplesGap
        val buffer = ShortArray(totalSamples)

        for (i in 0 until numSamplesSingle) {
            val t = i.toDouble() / SAMPLE_RATE
            val currentFreq = 1500.0 + 1000.0 * (t / durationSingle)
            val phase = 2.0 * Math.PI * currentFreq * t
            buffer[i] = (sin(phase) * 0.4 * Short.MAX_VALUE).toInt().toShort()
        }
        for (i in 0 until numSamplesSingle) {
            val t = i.toDouble() / SAMPLE_RATE
            val currentFreq = 1700.0 + 1100.0 * (t / durationSingle)
            val phase = 2.0 * Math.PI * currentFreq * t
            buffer[numSamplesSingle + numSamplesGap + i] = (sin(phase) * 0.4 * Short.MAX_VALUE).toInt().toShort()
        }
        writeAndPlay(context, buffer)
    }

    private fun playDoubleMarimba(context: Context? = null) {
        val durationNote = 0.4f
        val delay = 0.12f
        val numSamplesNote = (durationNote * SAMPLE_RATE).toInt()
        val numSamplesDelay = (delay * SAMPLE_RATE).toInt()
        val totalSamples = numSamplesNote + numSamplesDelay
        val buffer = ShortArray(totalSamples)

        val freq1 = 698.46
        for (i in 0 until numSamplesNote) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-7.0 * t)
            val valPrimary = sin(2.0 * Math.PI * freq1 * t) * 0.6
            val valHar = sin(2.0 * Math.PI * freq1 * 2.1 * t) * 0.15
            val sampleValue = ((valPrimary + valHar) * envelope * Short.MAX_VALUE).toInt()
            buffer[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val freq2 = 1046.50
        for (i in 0 until numSamplesNote) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-7.0 * t)
            val valPrimary = sin(2.0 * Math.PI * freq2 * t) * 0.6
            val valHar = sin(2.0 * Math.PI * freq2 * 2.1 * t) * 0.15
            val sampleValue = ((valPrimary + valHar) * envelope * Short.MAX_VALUE).toInt()
            val destIdx = numSamplesDelay + i
            if (destIdx < totalSamples) {
                val combined = buffer[destIdx].toInt() + sampleValue
                buffer[destIdx] = combined.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        writeAndPlay(context, buffer)
    }

    private fun playSoftBeep(context: Context? = null) {
        val duration = 0.3f
        val numSamples = (duration * SAMPLE_RATE).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 523.25

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val fade = when {
                t < 0.03 -> t / 0.03
                t > duration - 0.05 -> (duration - t) / 0.05
                else -> 1.0
            }
            buffer[i] = (sin(2.0 * Math.PI * freq * t) * fade * 0.4 * Short.MAX_VALUE).toInt().toShort()
        }
        writeAndPlay(context, buffer)
    }

    private fun playSystemNotification(context: Context) {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            if (ringtone != null) {
                synchronized(playbackLock) {
                    activeRingtone = ringtone
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) ringtone.volume = outputVolume
                ringtone.play()
            } else {
                playSoftBeep(context)
            }
        } catch (_: Throwable) {
            playSoftBeep(context)
        }
    }

    private fun writeAndPlay(context: Context?, buffer: ShortArray) {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackSize = maxOf(minBufferSize, buffer.size * 2)
            val builder = AudioTrack.Builder()
            if (android.os.Build.VERSION.SDK_INT >= 34 && context != null) {
                builder.setContext(context)
            }
            val audioTrack = builder
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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
                .setBufferSizeInBytes(trackSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            synchronized(playbackLock) {
                activeAudioTrack = audioTrack
            }

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.setVolume(outputVolume)
            audioTrack.play()

            val playTimeMs = (buffer.size.toFloat() / SAMPLE_RATE * 1000).toLong() + 200
            Thread {
                try {
                    Thread.sleep(playTimeMs)
                    synchronized(playbackLock) {
                        if (activeAudioTrack === audioTrack) {
                            activeAudioTrack = null
                        }
                    }
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Throwable) {
                }
            }.start()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class AmbientKind(val id: String, val label: String, val blurb: String) {
    WHITE("WHITE", "White", "Bright, even hiss"),
    PINK("PINK", "Pink", "Softer, like steady wind"),
    BROWN("BROWN", "Brown", "Deep, like a distant roar"),
    RAIN("RAIN", "Rain", "Steady rain with drops");

    companion object {
        fun fromId(id: String): AmbientKind = entries.firstOrNull { it.id == id } ?: PINK
    }
}

/**
 * Makes endless noise one sample at a time. Seeded so tests get the same numbers every run, and kept free of any
 * Android class so it can be checked without audio hardware.
 */
class NoiseSource(private val kind: AmbientKind, seed: Long = System.nanoTime()) {
    private val rng = java.util.Random(seed)

    // Pink noise filter state (Paul Kellet's refined method).
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0
    private var brown = 0.0

    // Rain drops: a few short, decaying high-pitched blips mixed over the steady hiss.
    private val dropAge = IntArray(MAX_DROPS) { -1 }
    private val dropFreq = DoubleArray(MAX_DROPS)
    private val dropAmp = DoubleArray(MAX_DROPS)

    /** One sample in roughly -1..1. */
    fun next(): Double = when (kind) {
        AmbientKind.WHITE -> white() * 0.45
        AmbientKind.PINK -> pink()
        AmbientKind.BROWN -> brownNoise()
        AmbientKind.RAIN -> pink() * 0.8 + drops()
    }

    fun fill(buffer: ShortArray) {
        for (i in buffer.indices) {
            buffer[i] = (next() * Short.MAX_VALUE).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun white(): Double = rng.nextDouble() * 2.0 - 1.0

    private fun pink(): Double {
        val w = white()
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return out * 0.11
    }

    private fun brownNoise(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return (brown * 3.5).coerceIn(-1.0, 1.0) * 0.8
    }

    private fun drops(): Double {
        if (rng.nextDouble() < DROP_CHANCE) {
            val slot = dropAge.indexOfFirst { it < 0 }
            if (slot >= 0) {
                dropAge[slot] = 0
                dropFreq[slot] = 1800.0 + rng.nextDouble() * 2700.0
                dropAmp[slot] = 0.08 + rng.nextDouble() * 0.17
            }
        }
        var sum = 0.0
        for (i in 0 until MAX_DROPS) {
            val age = dropAge[i]
            if (age < 0) continue
            sum += dropAmp[i] * sin(2.0 * PI * dropFreq[i] * age / SAMPLE_RATE) * exp(-age / (0.012 * SAMPLE_RATE))
            dropAge[i] = if (age > 0.08 * SAMPLE_RATE) -1 else age + 1
        }
        return sum
    }

    companion object {
        const val SAMPLE_RATE = 22050
        private const val MAX_DROPS = 12
        private const val DROP_CHANCE = 0.0006
    }
}

/** Plays [NoiseSource] audio on a background thread until stopped. Only one ambient sound plays at a time. */
object AmbientEngine {
    private val lock = Any()
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var volume = 0.5f

    private val isRunningTests: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    val isPlaying: Boolean get() = running

    /** Changes loudness while playing; used by the sleep timer to fade out. */
    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
    }

    fun start(context: Context, kind: AmbientKind, level: Float) {
        if (isRunningTests) return
        stop()
        volume = level.coerceIn(0f, 1f)
        val attributed = AudioSynthEngine.attributionContext(context)
        synchronized(lock) {
            running = true
            thread = Thread { play(attributed, kind) }.apply {
                isDaemon = true
                name = "meridian-ambient"
                start()
            }
        }
    }

    fun stop() {
        val toJoin: Thread?
        synchronized(lock) {
            running = false
            toJoin = thread
            thread = null
        }
        // The loop sees `running == false` within one buffer (about 90 ms) and releases the track itself.
        try {
            toJoin?.join(500)
        } catch (_: InterruptedException) {
        }
    }

    private fun play(context: Context, kind: AmbientKind) {
        var track: AudioTrack? = null
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                NoiseSource.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val builder = AudioTrack.Builder()
            if (android.os.Build.VERSION.SDK_INT >= 34) builder.setContext(context)
            track = builder
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(NoiseSource.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, CHUNK * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val source = NoiseSource(kind)
            val chunk = ShortArray(CHUNK)
            while (running) {
                source.fill(chunk)
                track.setVolume(volume)
                track.write(chunk, 0, chunk.size) // blocks until there is room, which paces the loop
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            try {
                track?.stop()
            } catch (_: Throwable) {
            }
            try {
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private const val CHUNK = 2048
}

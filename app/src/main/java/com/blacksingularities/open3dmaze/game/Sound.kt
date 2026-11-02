package com.blacksingularities.open3dmaze.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dźwięk gry syntezowany na żywo: szum toczenia zależny od prędkości (inny na lodzie),
 * stuknięcia o ściany zależne od siły uderzenia i krótkie sygnały zdarzeń.
 * Bez plików audio – wszystko liczy osobny wątek do strumienia AudioTrack.
 */
class Sound {
    private val rate = 44100
    @Volatile var enabled = true
    @Volatile var speed = 0f
    @Volatile var onIce = false
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    private class Voice(val kind: Int, val amp: Float, val freq: Float) {
        var t = 0f
    }

    private val pending = ArrayList<Voice>()
    private val voices = ArrayList<Voice>()

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 2048 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        track = t
        running = true
        runCatching { t.play() }
        thread = Thread({ loop(t) }, "maze-sound").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    private fun add(kind: Int, amp: Float, freq: Float = 0f) {
        if (!enabled) return
        synchronized(pending) { if (pending.size < 16) pending += Voice(kind, amp, freq) }
    }

    /** Uderzenie o ścianę; [v] – prędkość normalna w jednostkach świata/s. */
    fun hit(v: Float) {
        if (v < 1.5f) return
        add(HIT, min(1f, (v / 30f).pow(0.8f)), 1400f + Random.nextFloat() * 900f)
    }

    fun gem() = add(CHIME, 0.5f, 1568f)
    fun check() = add(CHIME, 0.4f, 1046f)
    fun portal() = add(SWEEP, 0.4f, 300f)
    fun fall() = add(FALL, 0.7f, 520f)
    fun win() { add(CHIME, 0.45f, 1046f); add(CHIME, 0.4f, 1318f); add(CHIME, 0.4f, 1568f) }

    private fun loop(t: AudioTrack) {
        val n = 256
        val buf = ShortArray(n)
        val dtS = 1f / rate
        var lp1 = 0f
        var lp2 = 0f
        var brown = 0f
        var level = 0f
        var phase = 0f
        val rnd = Random(1)
        while (running) {
            synchronized(pending) { voices += pending; pending.clear() }
            val sp = if (enabled) speed else 0f
            // Głośność toczenia rośnie z prędkością, ale łagodnie, jak na prawdziwej desce.
            val target = (sp / 25f).coerceIn(0f, 1f).pow(0.7f) * (if (onIce) 0.12f else 0.32f)
            val cutoff = (if (onIce) 0.25f else 0.04f) + (sp / 60f).coerceIn(0f, 1f) * 0.12f
            for (i in 0 until n) {
                level += (target - level) * 0.002f
                val white = rnd.nextFloat() * 2f - 1f
                brown = (brown + white * 0.02f) * 0.995f
                lp1 += (white - lp1) * cutoff
                lp2 += (lp1 - lp2) * cutoff
                // Delikatne „bicie” toczenia – częstotliwość rośnie z prędkością.
                phase += (8f + sp * 3f) * dtS
                if (phase > 1f) phase -= 1f
                val wobble = 0.75f + 0.25f * sin(phase * 2f * PI.toFloat())
                var out = (lp2 * 3f + brown * 2f) * level * wobble

                var vi = 0
                while (vi < voices.size) {
                    val v = voices[vi]
                    val tt = v.t
                    val s = when (v.kind) {
                        HIT -> {
                            val env = exp(-tt * 70f)
                            (sin(2f * PI.toFloat() * v.freq * tt) * 0.6f + white * 0.5f) * env * v.amp
                        }
                        CHIME -> {
                            val env = exp(-tt * 6f) * min(1f, tt * 400f)
                            (sin(2f * PI.toFloat() * v.freq * tt) + 0.3f * sin(2f * PI.toFloat() * v.freq * 2.76f * tt)) * env * v.amp * 0.5f
                        }
                        SWEEP -> {
                            val env = exp(-tt * 5f) * min(1f, tt * 100f)
                            sin(2f * PI.toFloat() * (v.freq + 900f * tt) * tt) * env * v.amp * 0.5f
                        }
                        else -> {
                            // Upadek: opadający ton i głuche stuknięcie dna.
                            val env = exp(-tt * 3.5f)
                            val tone = sin(2f * PI.toFloat() * (v.freq - 380f * tt) * tt) * env * 0.4f
                            val thud = if (tt > 0.32f) sin(2f * PI.toFloat() * 90f * (tt - 0.32f)) * exp(-(tt - 0.32f) * 25f) else 0f
                            (tone + thud) * v.amp
                        }
                    }
                    out += s
                    v.t += dtS
                    if (v.t > 1.2f) voices.removeAt(vi) else vi++
                }
                buf[i] = (out.coerceIn(-1f, 1f) * 32000f).toInt().toShort()
            }
            if (t.write(buf, 0, n) < 0) break
        }
    }

    companion object {
        private const val HIT = 0
        private const val CHIME = 1
        private const val SWEEP = 2
        private const val FALL = 3
    }
}

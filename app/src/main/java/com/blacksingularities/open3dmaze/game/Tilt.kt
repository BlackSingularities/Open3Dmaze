package com.blacksingularities.open3dmaze.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager

/** Pochylenie telefonu w jednostkach g (oś X w prawo, Y w dół ekranu). */
class TiltSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available get() = sensor != null

    @Volatile var rawX = 0f
        private set
    @Volatile var rawY = 0f
        private set
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ax = -e.values[0] / SensorManager.GRAVITY_EARTH
        val ay = e.values[1] / SensorManager.GRAVITY_EARTH
        // Filtr dolnoprzepustowy – akcelerometr szumi, czujnik grawitacji już jest gładki.
        val a = if (e.sensor.type == Sensor.TYPE_GRAVITY) 0.6f else 0.25f
        rawX += (ax - rawX) * a
        rawY += (ay - rawY) * a
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        @Suppress("DEPRECATION")
        fun rotation(context: Context): Int =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}

/** Dźwięki i wibracje gry – krótkie, systemowe, bez plików audio. */
class Fx(context: Context) {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    var sound = true
    var haptics = true
    private var lastBump = 0L

    fun buzz(ms: Long, amp: Int) {
        val v = vib ?: return
        if (!haptics || !v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255))) }
    }

    fun beep(type: Int, ms: Int) {
        if (!sound) return
        runCatching { tone?.startTone(type, ms) }
    }

    fun bump(strength: Float) {
        val now = System.currentTimeMillis()
        if (strength < 1.2f || now - lastBump < 60) return
        lastBump = now
        buzz(12, (strength * 28).toInt())
        if (strength > 3.5f) beep(ToneGenerator.TONE_PROP_BEEP, 15)
    }

    fun gem() { beep(ToneGenerator.TONE_PROP_ACK, 60); buzz(20, 120) }
    fun fall() { beep(ToneGenerator.TONE_CDMA_PIP, 180); buzz(140, 220) }
    fun win() { beep(ToneGenerator.TONE_CDMA_CONFIRM, 300); buzz(60, 200) }
    fun portal() { beep(ToneGenerator.TONE_PROP_PROMPT, 80); buzz(30, 90) }
    fun check() { beep(ToneGenerator.TONE_PROP_BEEP2, 80); buzz(25, 100) }
    fun boost() { buzz(10, 60) }

    fun release() { runCatching { tone?.release() } }
}

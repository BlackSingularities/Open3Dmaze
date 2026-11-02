package com.blacksingularities.open3dmaze.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

/** Dźwięki (syntezowane na żywo) i wibracje gry. */
class Fx(context: Context) {
    private val synth = Sound()
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    var sound: Boolean
        get() = synth.enabled
        set(v) { synth.enabled = v }
    var haptics = true
    private var lastBump = 0L

    fun start() = synth.start()
    fun stop() = synth.stop()

    /** Co klatkę: prędkość kulki i podłoże dla dźwięku toczenia. */
    fun rolling(speed: Float, ice: Boolean) {
        synth.speed = speed
        synth.onIce = ice
    }

    fun buzz(ms: Long, amp: Int) {
        val v = vib ?: return
        if (!haptics || !v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255))) }
    }

    /** Uderzenie o ścianę; [v] – prędkość normalna w jednostkach świata/s. */
    fun bump(v: Float) {
        synth.hit(v)
        val now = System.currentTimeMillis()
        if (v < 4f || now - lastBump < 50) return
        lastBump = now
        buzz(8 + (v / 6f).toLong().coerceAtMost(20), (v * 5f).toInt())
    }

    fun gem() { synth.gem(); buzz(20, 120) }
    fun fall() { synth.fall(); buzz(140, 220) }
    fun win() { synth.win(); buzz(60, 200) }
    fun portal() { synth.portal(); buzz(30, 90) }
    fun check() { synth.check(); buzz(25, 100) }
    fun boost() { buzz(10, 60) }

    fun release() = synth.stop()
}

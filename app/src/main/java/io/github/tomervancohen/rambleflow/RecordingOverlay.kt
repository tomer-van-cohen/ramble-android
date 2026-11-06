package io.github.tomervancohen.rambleflow

import android.app.KeyguardManager
import android.content.*
import android.graphics.*
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import kotlin.math.abs
import kotlin.math.sin

/** A small, permission-gated overlay. Never appears above the keyguard or requests focus. */
internal class RecordingOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var view: Indicator? = null
    private var state = QuickRecordState()
    private var fullControlsVisible = false
    private val application = context.applicationContext as android.app.Application
    private val activityCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: android.app.Activity) {
            if(activity is MainActivity && activity.showsLiveRecording) { fullControlsVisible = true; update(state) }
        }
        override fun onActivityPaused(activity: android.app.Activity) {
            if(activity is MainActivity && activity.showsLiveRecording) { fullControlsVisible = false; update(state) }
        }
        override fun onActivityCreated(activity: android.app.Activity, bundle: android.os.Bundle?) {}
        override fun onActivityStarted(activity: android.app.Activity) {}
        override fun onActivityStopped(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, bundle: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { update(state) }
    }
    init {
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        if(android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
    }
    fun update(next: QuickRecordState) {
        state = next
        if(fullControlsVisible || next.mode != "recording" || !Settings.canDrawOverlays(context) || keyguard.isKeyguardLocked || !power.isInteractive) { remove(); return }
        if(view == null) {
            val inset = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
            val params = WindowManager.LayoutParams((116 * density).toInt(), (38 * density).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = (12 * density).toInt(); y = inset + (6 * density).toInt() }
            val indicator = Indicator()
            indicator.setOnClickListener {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .putExtra("ramble.live.recording", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            var downX = 0f; var downY = 0f; var initialX = 0; var initialY = 0; var moved = false
            indicator.setOnTouchListener { target, event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = params.x; initialY = params.y; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        if(abs(event.rawX - downX) + abs(event.rawY - downY) > 8 * density) moved = true
                        if(moved) {
                            val bounds = wm.currentWindowMetrics.bounds
                            params.x = (initialX - (event.rawX - downX)).toInt().coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
                            params.y = (initialY + (event.rawY - downY)).toInt().coerceIn(inset, (bounds.height() - params.height - inset).coerceAtLeast(inset))
                            runCatching { wm.updateViewLayout(target, params) }
                        }; true
                    }
                    MotionEvent.ACTION_UP -> { if(!moved) target.performClick(); true }
                    else -> true
                }
            }
            runCatching { wm.addView(indicator, params); view = indicator }
        }
        view?.apply {
            contentDescription = appText(R.string.ui_ramble_is_recording_tap_to_open_recording_con_7e317ce)
            invalidate()
        }
    }
    private fun remove() { view?.let { runCatching { wm.removeView(it) } }; view = null }
    fun close() { remove(); application.unregisterActivityLifecycleCallbacks(activityCallbacks); runCatching { context.unregisterReceiver(receiver) } }
    private inner class Indicator : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val d = density
            paint.color = Color.rgb(20, 23, 21); paint.style = Paint.Style.FILL
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 22*d, 22*d, paint)
            paint.color = Color.rgb(37, 211, 102); paint.strokeWidth = 2.5f*d; paint.strokeCap = Paint.Cap.ROUND
            for(i in 0..6) {
                val amplitude = (3 + 18 * state.level * abs(sin(i * .9 + state.seconds))).toFloat()*d
                val x = (13 + i*4)*d
                canvas.drawLine(x, height/2f-amplitude/2, x, height/2f+amplitude/2, paint)
            }
            paint.color = Color.WHITE; paint.textSize = 14*d; paint.typeface = Typeface.create("monospace", Typeface.BOLD)
            canvas.drawText("%02d:%02d".format(state.seconds/60, state.seconds%60), 51*d, height/2f + 5*d, paint)
        }
    }
}

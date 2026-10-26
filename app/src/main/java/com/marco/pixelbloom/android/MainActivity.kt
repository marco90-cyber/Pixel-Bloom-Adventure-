package com.marco.pixelbloom.android

import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.marco.pixelbloom.Btn
import com.marco.pixelbloom.Game

class MainActivity : Activity() {
    private lateinit var game: Game
    private lateinit var view: GameView
    private lateinit var sound: AndroidSound

    // stato dello stick/D-pad analogico
    private var ax = 0; private var ay = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sound = AndroidSound()
        game = Game(PrefsStorage(this), sound, AndroidHost(this))
        view = GameView(this, game)
        setContentView(view)
        hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi() else view.enqueue(Runnable { game.releaseAll() })
    }

    override fun onResume() {
        super.onResume()
        sound.start()
        hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        view.enqueue(Runnable { game.releaseAll() })
        sound.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.stop()
    }

    // ---------- tastiera e controller ----------
    private fun map(code: Int): Btn? = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Btn.RIGHT
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Btn.DOWN
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_DPAD_CENTER -> Btn.A
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_B -> Btn.B
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_L1 -> Btn.L
        KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_BUTTON_R1 -> Btn.R
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_START -> Btn.START
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_SELECT -> Btn.BACK
        else -> null
    }

    private fun key(e: KeyEvent, down: Boolean): Boolean {
        val b = map(e.keyCode) ?: return false
        if (down && e.repeatCount > 0) return true
        view.enqueue(Runnable { game.key(b, down) })
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(event, true) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(event, false) || super.onKeyUp(keyCode, event)

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) != 0 && e.action == MotionEvent.ACTION_MOVE) {
            var x = e.getAxisValue(MotionEvent.AXIS_X)
            var y = e.getAxisValue(MotionEvent.AXIS_Y)
            val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (Math.abs(hx) > 0.5f) x = hx
            if (Math.abs(hy) > 0.5f) y = hy
            val nx = if (x < -0.5f) -1 else if (x > 0.5f) 1 else 0
            val ny = if (y < -0.5f) -1 else if (y > 0.5f) 1 else 0
            if (nx != ax) {
                val old = ax; ax = nx
                view.enqueue(Runnable {
                    if (old < 0) game.key(Btn.LEFT, false)
                    if (old > 0) game.key(Btn.RIGHT, false)
                    if (nx < 0) game.key(Btn.LEFT, true)
                    if (nx > 0) game.key(Btn.RIGHT, true)
                })
            }
            if (ny != ay) {
                val old = ay; ay = ny
                view.enqueue(Runnable {
                    if (old < 0) game.key(Btn.UP, false)
                    if (old > 0) game.key(Btn.DOWN, false)
                    if (ny < 0) game.key(Btn.UP, true)
                    if (ny > 0) game.key(Btn.DOWN, true)
                })
            }
            return true
        }
        return super.onGenericMotionEvent(e)
    }
}

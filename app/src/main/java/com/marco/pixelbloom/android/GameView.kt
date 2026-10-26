package com.marco.pixelbloom.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.marco.pixelbloom.Game
import com.marco.pixelbloom.VH
import com.marco.pixelbloom.VW
import java.util.concurrent.ConcurrentLinkedQueue

/** Superficie di gioco: ciclo a 60 Hz fissi, rendering su bitmap 480x270 scalato senza filtro. */
class GameView(ctx: Context, private val game: Game) : SurfaceView(ctx), SurfaceHolder.Callback {
    private val frame = Bitmap.createBitmap(VW, VH, Bitmap.Config.ARGB_8888)
    private val fc = Canvas(frame)
    private val gfx = AndroidGfx(fc)
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = Rect()
    private val events = ConcurrentLinkedQueue<Runnable>()

    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var sw = 1
    @Volatile private var sh = 1
    @Volatile private var scale = 1f
    @Volatile private var offX = 0f
    @Volatile private var offY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun enqueue(r: Runnable) { events.add(r) }

    // ---------- ciclo ----------
    fun startLoop() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "pb-game").also { it.start() }
    }

    fun stopLoop() {
        running = false
        try { thread?.join(500) } catch (e: InterruptedException) {}
        thread = null
    }

    private fun loop() {
        val step = 1_000_000_000L / 60L
        var last = System.nanoTime()
        var acc = 0L
        while (running) {
            val now = System.nanoTime()
            acc += now - last
            last = now
            if (acc > step * 5) acc = step * 5
            var n = 0
            while (acc >= step) {
                while (true) { val r = events.poll() ?: break; r.run() }
                try { game.update() } catch (e: Exception) { android.util.Log.e("PixelBloom", "update", e) }
                acc -= step
                n++
            }
            if (n > 0) draw()
            else try { Thread.sleep(2) } catch (e: InterruptedException) {}
        }
    }

    private fun draw() {
        val h = holder
        val c = try { h.lockCanvas() } catch (e: Exception) { null } ?: return
        try {
            fc.drawColor(Color.BLACK)
            try { game.render(gfx) } catch (e: Exception) { android.util.Log.e("PixelBloom", "render", e) }
            val w = c.width; val hh = c.height
            sw = w; sh = hh
            val s = minOf(w / VW.toFloat(), hh / VH.toFloat())
            val dw = (VW * s).toInt(); val dh = (VH * s).toInt()
            val ox = (w - dw) / 2; val oy = (hh - dh) / 2
            scale = s; offX = ox.toFloat(); offY = oy.toFloat()
            c.drawColor(Color.BLACK)
            dst.set(ox, oy, ox + dw, oy + dh)
            c.drawBitmap(frame, null, dst, blit)
        } finally {
            try { h.unlockCanvasAndPost(c) } catch (e: Exception) {}
        }
    }

    // ---------- superficie ----------
    override fun surfaceCreated(holder: SurfaceHolder) { startLoop() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopLoop() }

    // ---------- tocco ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = scale
        val ox = offX; val oy = offY
        val act = e.actionMasked
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                send(0, e.getPointerId(i), (e.getX(i) - ox) / s, (e.getY(i) - oy) / s)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) send(1, e.getPointerId(i), (e.getX(i) - ox) / s, (e.getY(i) - oy) / s)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                send(2, e.getPointerId(i), (e.getX(i) - ox) / s, (e.getY(i) - oy) / s)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until e.pointerCount) send(3, e.getPointerId(i), (e.getX(i) - ox) / s, (e.getY(i) - oy) / s)
            }
        }
        return true
    }

    private fun send(action: Int, id: Int, x: Float, y: Float) {
        events.add(Runnable { game.touch(action, id, x, y) })
    }
}

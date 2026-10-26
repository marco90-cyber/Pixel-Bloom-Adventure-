package com.marco.pixelbloom.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.marco.pixelbloom.Gfx
import com.marco.pixelbloom.Img

/** Disegna sul bitmap 480x270 (1:1); la scala sullo schermo la fa GameView in un solo passaggio. */
class AndroidGfx(val canvas: Canvas) : Gfx {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false; isDither = false }
    private val src = Rect()
    private val dst = Rect()

    override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        if (w <= 0 || h <= 0) return
        paint.color = argb
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    override fun img(i: Img, x: Int, y: Int, flipX: Boolean, flipY: Boolean, dw: Int, dh: Int) {
        var bmp = i.handle as? Bitmap
        if (bmp == null) {
            bmp = Bitmap.createBitmap(i.px, i.w, i.h, Bitmap.Config.ARGB_8888)
            i.handle = bmp
        }
        paint.color = -1
        if (!flipX && !flipY) {
            dst.set(x, y, x + dw, y + dh)
            canvas.drawBitmap(bmp, null, dst, paint)
        } else {
            canvas.save()
            canvas.scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, x + dw / 2f, y + dh / 2f)
            dst.set(x, y, x + dw, y + dh)
            canvas.drawBitmap(bmp, null, dst, paint)
            canvas.restore()
        }
    }
}

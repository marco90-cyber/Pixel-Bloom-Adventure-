package com.marco.pixelbloom

import kotlin.math.sqrt

object Ui {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val GOLD = 0xFFFFD23C.toInt()
    const val GRAY = 0xFF9AA0B0.toInt()
    const val DIM = 0xFF5A5F70.toInt()
    const val RED = 0xFFE8334A.toInt()
    const val GREEN = 0xFF4CD964.toInt()
    const val PINK = 0xFFFF7BAC.toInt()

    fun panel(g: Gfx, x: Int, y: Int, w: Int, h: Int, fill: Int = 0xE61A1530.toInt(), border: Int = GOLD) {
        g.fill(x + 2, y, w - 4, h, fill)
        g.fill(x, y + 2, w, h - 4, fill)
        g.fill(x + 2, y, w - 4, 1, border)
        g.fill(x + 2, y + h - 1, w - 4, 1, border)
        g.fill(x, y + 2, 1, h - 4, border)
        g.fill(x + w - 1, y + 2, 1, h - 4, border)
        g.fill(x + 1, y + 1, 1, 1, border); g.fill(x + w - 2, y + 1, 1, 1, border)
        g.fill(x + 1, y + h - 2, 1, 1, border); g.fill(x + w - 2, y + h - 2, 1, 1, border)
    }

    /** Disco pieno disegnato a righe orizzontali. */
    fun disc(g: Gfx, cx: Int, cy: Int, r: Int, color: Int) {
        for (dy in -r..r) {
            val dx = sqrt((r * r - dy * dy).toFloat()).toInt()
            g.fill(cx - dx, cy + dy, dx * 2 + 1, 1, color)
        }
    }

    fun ring(g: Gfx, cx: Int, cy: Int, r: Int, color: Int) {
        for (dy in -r..r) {
            val dx = sqrt((r * r - dy * dy).toFloat()).toInt()
            g.fill(cx - dx, cy + dy, 1, 1, color)
            g.fill(cx + dx, cy + dy, 1, 1, color)
        }
        for (dx in -r..r) {
            val dy = sqrt((r * r - dx * dx).toFloat()).toInt()
            g.fill(cx + dx, cy - dy, 1, 1, color)
            g.fill(cx + dx, cy + dy, 1, 1, color)
        }
    }

    fun frame(g: Gfx, x: Int, y: Int, w: Int, h: Int, color: Int) {
        g.fill(x, y, w, 1, color); g.fill(x, y + h - 1, w, 1, color)
        g.fill(x, y, 1, h, color); g.fill(x + w - 1, y, 1, h, color)
    }
}

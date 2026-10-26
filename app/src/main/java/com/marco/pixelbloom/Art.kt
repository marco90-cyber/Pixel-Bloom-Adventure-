package com.marco.pixelbloom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ThemeDef(
    val skyTop: Int, val skyBot: Int,
    val topA: Int, val topB: Int, val dirtA: Int, val dirtB: Int,
    val brickA: Int, val brickB: Int,
    val far: Int, val mid: Int,
    val enemyA: Int, val enemyB: Int, val pillar: Int
)

class ThemeArt {
    lateinit var groundTop: Array<Img>
    lateinit var groundFill: Array<Img>
    lateinit var brick: Img
    lateinit var qblock: Array<Img>
    lateinit var used: Img
    lateinit var stone: Img
    lateinit var spike: Img
    lateinit var pillarCap: Img
    lateinit var pillarBody: Img
    lateinit var platform: Img
    lateinit var ice: Img
    lateinit var spring: Array<Img>
    lateinit var lavaTop: Array<Img>
    lateinit var lavaFill: Img
    lateinit var far: Img
    lateinit var mid: Img
    lateinit var clouds: Array<Img>
    lateinit var slime: Array<Img>      // 0,1 camminata, 2 schiacciato
    lateinit var snail: Array<Img>      // 0,1 camminata, 2 guscio
    lateinit var frog: Array<Img>       // 0 fermo, 1 salto
    lateinit var boss: Array<Img>       // 2 frame
}

object Art {
    private fun c(v: Int) = rgb(v)

    val themes = arrayOf(
        // Prato
        ThemeDef(c(0x5BB8F5), c(0xCDEFFF), c(0x58C33C), c(0x3C9A2A), c(0x9A6B3E), c(0x7A4F2A), c(0xC9794A), c(0x8E4F2C),
            c(0x86D9A8), c(0x4FB874), c(0x4FA8F0), c(0x6BD36B), c(0x39B58A)),
        // Deserto
        ThemeDef(c(0xF4A64F), c(0xFFE9B8), c(0xF2D16B), c(0xD9B04A), c(0xD9A45A), c(0xB9833A), c(0xC98B4A), c(0x9B6430),
            c(0xEBB765), c(0xD39B4B), c(0xE8B04A), c(0xB5C94A), c(0xC9A24A)),
        // Ghiaccio
        ThemeDef(c(0x8FBFEF), c(0xEAF6FF), c(0xF4FBFF), c(0xBFE3F7), c(0x7FB5D8), c(0x5E92B8), c(0x8FB4D9), c(0x6A8DB8),
            c(0xC5E2F5), c(0xA3CBE6), c(0x9FE3F5), c(0x4DD0E1), c(0x6FB7E6)),
        // Vulcano
        ThemeDef(c(0x2E1A2E), c(0xB0502E), c(0x6A5560), c(0x453640), c(0x6B3A32), c(0x4A2622), c(0x8A4A3A), c(0x5E2E26),
            c(0x6A2A2A), c(0x4A1E22), c(0xF0603A), c(0xFFB02E), c(0x9A5A4A))
    )

    private val built = arrayOfNulls<ThemeArt>(4)

    fun theme(i: Int): ThemeArt {
        val k = i.coerceIn(0, 3)
        var t = built[k]
        if (t == null) { t = buildTheme(k); built[k] = t }
        return t
    }

    // ---------- comuni ----------
    val coin: Array<Img> by lazy { Array(4) { makeCoin(it) } }
    val seed: Array<Img> by lazy { Array(2) { makeSeed(it) } }
    val bulb: Img by lazy { makeBulb() }
    val flower: Img by lazy { makeFlower() }
    val shot: Img by lazy { makeShot() }
    val heart: Img by lazy { spriteFrom(listOf(".rr..rr.", "rwrrrrrr", "rrrrrrrr", ".rrrrrr.", "..rrrr..", "...rr..."), mapOf('r' to c(0xE8334A), 'w' to c(0xFFB3BE))) }
    val heartEmpty: Img by lazy { spriteFrom(listOf(".rr..rr.", "r..rr..r", "r......r", ".r....r.", "..r..r..", "...rr..."), mapOf('r' to c(0x7A5A66))) }
    val coinIcon: Img by lazy { val p = Pix(8, 8); p.ellipseO(4f, 4f, 2.6f, 3f, c(0xFFD23C), c(0x9A6A10)); p.set(3, 2, c(0xFFF3A0)); p.toImg() }
    val seedIcon: Img by lazy { makeSeedIcon() }
    val gem: Array<Img> by lazy { Array(2) { makeGem(it) } }
    val flagCheckOff: Img by lazy { makeCheckFlag(false) }
    val flagCheckOn: Img by lazy { makeCheckFlag(true) }
    val leaf: Img by lazy { makeLeaf() }

    private val heroes = HashMap<Int, Img>()
    /** Frame: 0 fermo, 1 corsa A, 2 corsa B, 3 salto, 4 colpito. */
    fun hero(id: Int, frame: Int): Img {
        val key = id * 10 + frame
        var im = heroes[key]
        if (im == null) { im = if (id == 0) makeRina(frame) else makeToki(frame); heroes[key] = im }
        return im
    }

    // ---------- eroi ----------
    private fun legs(p: Pix, frame: Int, leg: Int, boot: Int, ol: Int) {
        val lx: Int; val ly: Int; val rx: Int; val ry: Int
        when (frame) {
            0 -> { lx = 3; ly = 21; rx = 8; ry = 21 }
            1 -> { lx = 1; ly = 21; rx = 9; ry = 19 }
            2 -> { lx = 5; ly = 19; rx = 10; ry = 21 }
            3 -> { lx = 2; ly = 20; rx = 9; ry = 19 }
            else -> { lx = 1; ly = 20; rx = 10; ry = 20 }
        }
        p.rect(lx + 1, 19, 3, maxOf(1, ly - 19), leg)
        p.rect(rx + 1, 19, 3, maxOf(1, ry - 19), leg)
        p.rectO(lx, ly, 5, 3, boot, ol)
        p.rectO(rx, ry, 5, 3, boot, ol)
    }

    private fun makeRina(frame: Int): Img {
        val p = Pix(16, 24)
        val ol = c(0x2A1633); val fur = c(0xF08A3C); val furS = c(0xC85F1E); val wh = c(0xFFF4E0)
        val sc = c(0xE23B5C); val tun = c(0x2FA8A0); val tunS = c(0x1F7F7A); val boot = c(0x5A3A2A)
        val eye = c(0x1F1730); val pink = c(0xFF9EB0)
        // coda (dietro)
        val ty = if (frame == 3) 9 else if (frame == 1) 12 else 13
        p.ellipseO(2.5f, ty + 4f, 2.2f, 4.2f, fur, ol)
        p.ellipse(2.5f, ty + 6.6f, 1.5f, 1.3f, wh)
        legs(p, frame, furS, boot, ol)
        p.rectO(4, 13, 8, 7, tun, ol)
        p.rect(5, 14, 2, 5, tunS)
        p.rect(9, 17, 2, 1, c(0xFFD23C))
        // braccia
        when (frame) {
            3, 4 -> { p.rectO(0, 11, 3, 4, fur, ol); p.rectO(13, 11, 3, 4, fur, ol) }
            1 -> { p.rectO(2, 15, 3, 4, fur, ol); p.rectO(11, 13, 4, 3, fur, ol) }
            2 -> { p.rectO(2, 13, 3, 3, fur, ol); p.rectO(11, 15, 3, 4, fur, ol) }
            else -> { p.rectO(2, 14, 3, 5, fur, ol); p.rectO(11, 14, 3, 5, fur, ol) }
        }
        // orecchie
        p.tri(1, 3, 2, 0, 5, 3, ol); p.tri(2, 3, 3, 1, 4, 3, fur); p.set(3, 2, pink)
        p.tri(14, 3, 13, 0, 10, 3, ol); p.tri(13, 3, 12, 1, 11, 3, fur); p.set(12, 2, pink)
        // testa
        p.rectO(1, 3, 14, 9, fur, ol)
        p.set(1, 3, 0); p.set(14, 3, 0)
        p.rect(5, 8, 6, 3, wh)
        p.rect(7, 8, 2, 1, ol)
        p.set(3, 8, pink); p.set(12, 8, pink)
        if (frame == 4) {
            p.line(4, 5, 5, 7, eye); p.line(5, 5, 4, 7, eye); p.line(10, 5, 11, 7, eye); p.line(11, 5, 10, 7, eye)
        } else {
            p.rect(4, 5, 2, 3, eye); p.rect(10, 5, 2, 3, eye)
            p.set(4, 5, wh); p.set(10, 5, wh)
        }
        // sciarpa
        p.rectO(3, 11, 10, 3, sc, ol)
        p.rect(4, 12, 8, 1, c(0xFF7A93))
        val fl = if (frame == 3) -1 else if (frame == 0) 0 else 1
        p.rectO(11, 13 + fl, 3, 4, sc, ol)
        return p.toImg()
    }

    private fun makeToki(frame: Int): Img {
        val p = Pix(16, 24)
        val ol = c(0x1A1A33); val body = c(0x5C8DD6); val shade = c(0x3C63A8); val light = c(0xDCEBFF)
        val yel = c(0xFFD23C); val scr = c(0x16213A); val cy = c(0x6BF0FF); val boot = c(0x3A3A52)
        legs(p, frame, shade, boot, ol)
        p.rectO(3, 12, 10, 8, body, ol)
        p.rectO(5, 14, 6, 4, light, ol)
        p.rect(7, 15, 2, 2, yel); p.set(8, 14, yel)
        when (frame) {
            3, 4 -> { p.rectO(0, 9, 3, 5, shade, ol); p.rectO(13, 9, 3, 5, shade, ol) }
            1 -> { p.rectO(0, 14, 3, 5, shade, ol); p.rectO(13, 12, 3, 4, shade, ol) }
            2 -> { p.rectO(0, 12, 3, 4, shade, ol); p.rectO(13, 14, 3, 5, shade, ol) }
            else -> { p.rectO(0, 13, 3, 6, shade, ol); p.rectO(13, 13, 3, 6, shade, ol) }
        }
        // antenna
        p.rect(7, 2, 2, 2, c(0x8890A8))
        p.ellipseO(8f, 1.2f, 1.6f, 1.4f, yel, ol)
        // orecchie
        p.ellipseO(2.5f, 5.5f, 2f, 2f, body, ol)
        p.ellipseO(13.5f, 5.5f, 2f, 2f, body, ol)
        // testa
        p.rectO(2, 3, 12, 9, body, ol)
        p.rect(3, 4, 4, 1, lighter(body, 0.4f))
        p.rectO(4, 5, 8, 5, scr, ol)
        if (frame == 4) {
            p.line(5, 6, 6, 8, cy); p.line(6, 6, 5, 8, cy); p.line(9, 6, 10, 8, cy); p.line(10, 6, 9, 8, cy)
        } else {
            p.rect(5, 6, 2, 2, cy); p.rect(9, 6, 2, 2, cy)
            p.rect(7, 8, 2, 1, cy)
        }
        return p.toImg()
    }

    // ---------- oggetti ----------
    private fun makeCoin(f: Int): Img {
        val p = Pix(16, 16)
        val rx = when (f) { 0 -> 5.5f; 1 -> 4f; 2 -> 1.6f; else -> 4f }
        p.ellipseO(8f, 8f, rx, 6.2f, c(0xFFD23C), c(0x9A6A10))
        if (f != 2) {
            p.ellipse(8f, 8f, rx * 0.5f, 3.4f, c(0xFFEE90))
            p.set(6, 5, c(0xFFFFFF))
        }
        return p.toImg()
    }

    private fun makeSeed(f: Int): Img {
        val p = Pix(16, 16)
        val glow = withAlpha(90, 0xFF80FF)
        p.ellipse(8f, 9f, 7.5f, 7.5f, glow)
        p.ellipseO(8f, 9f, 4.5f, 5f, c(0xE040FB), c(0x4A148C))
        p.ellipse(8f, 9.5f, 2.6f, 3f, c(0xFF9CFF))
        p.set(6, 7, c(0xFFFFFF)); p.set(6, 8, c(0xFFFFFF))
        // germoglio
        p.rect(8, 2, 1, 3, c(0x43A047))
        p.ellipseO(10.5f, 2.5f, 2f, 1.2f, c(0x66BB6A), c(0x1B5E20))
        p.ellipseO(5.5f, 3f, 1.8f, 1.1f, c(0x66BB6A), c(0x1B5E20))
        if (f == 0) { p.set(1, 3, c(0xFFFFFF)); p.set(14, 12, c(0xFFFFFF)); p.set(13, 4, c(0xFFF59D)) }
        else { p.set(2, 12, c(0xFFFFFF)); p.set(14, 6, c(0xFFFFFF)); p.set(3, 4, c(0xFFF59D)) }
        return p.toImg()
    }

    private fun makeSeedIcon(): Img {
        val p = Pix(8, 8)
        p.ellipseO(4f, 4.5f, 2.5f, 2.8f, c(0xE040FB), c(0x4A148C))
        p.set(3, 3, c(0xFFFFFF))
        p.set(4, 0, c(0x66BB6A)); p.set(4, 1, c(0x66BB6A))
        return p.toImg()
    }

    private fun makeBulb(): Img {
        val p = Pix(16, 16)
        val ol = c(0x8A5A00)
        p.ellipseO(8f, 7.5f, 5.2f, 5.5f, c(0xFFE25A), ol)
        p.ellipse(8f, 8f, 3f, 3.4f, c(0xFFF7B0))
        p.set(6, 5, c(0xFFFFFF)); p.set(6, 6, c(0xFFFFFF))
        p.rectO(5, 12, 6, 4, c(0x9AA0B0), c(0x3A3F55))
        p.rect(6, 13, 4, 1, c(0xD0D4E0))
        p.ellipseO(8f, 1.6f, 2.2f, 1.2f, c(0x66BB6A), c(0x1B5E20))
        return p.toImg()
    }

    private fun makeFlower(): Img {
        val p = Pix(16, 16)
        val ol = c(0x6A1030)
        p.rect(7, 8, 2, 8, c(0x43A047))
        p.ellipseO(11.5f, 12.5f, 2.2f, 1.2f, c(0x66BB6A), c(0x1B5E20))
        val cols = intArrayOf(5, 8, 11, 8)
        // petali
        p.ellipseO(8f, 3f, 3f, 2.6f, c(0xFF5C8A), ol)
        p.ellipseO(3.5f, 6f, 2.8f, 2.6f, c(0xFF5C8A), ol)
        p.ellipseO(12.5f, 6f, 2.8f, 2.6f, c(0xFF5C8A), ol)
        p.ellipseO(8f, 8f, 3f, 2.8f, c(0xFFD23C), c(0x9A6A10))
        p.set(7, 7, c(0xFFF3A0))
        if (cols.isEmpty()) p.set(0, 0, 0)
        return p.toImg()
    }

    private fun makeShot(): Img {
        val p = Pix(6, 6)
        p.ellipseO(3f, 3f, 1.8f, 1.8f, c(0xFFB02E), c(0xB3361A))
        p.set(2, 2, c(0xFFF3A0))
        return p.toImg()
    }

    private fun makeGem(f: Int): Img {
        val p = Pix(16, 16)
        val ol = c(0x0B4F6C)
        p.tri(1, 6, 8, 0, 15, 6, ol)
        p.tri(1, 6, 8, 15, 15, 6, ol)
        p.tri(3, 6, 8, 2, 13, 6, c(0x7FEFFF))
        p.tri(3, 7, 8, 13, 13, 7, c(0x26C6DA))
        p.tri(8, 2, 6, 6, 10, 6, c(0xE0FBFF))
        if (f == 0) { p.set(2, 1, c(0xFFFFFF)); p.set(14, 12, c(0xFFFFFF)) } else { p.set(13, 2, c(0xFFFFFF)); p.set(2, 12, c(0xFFFFFF)) }
        return p.toImg()
    }

    private fun makeCheckFlag(on: Boolean): Img {
        val p = Pix(16, 32)
        p.rect(7, 4, 2, 28, c(0xC8CCD8))
        p.rect(7, 4, 1, 28, c(0xFFFFFF))
        p.ellipseO(8f, 3f, 2.2f, 2.2f, c(0xFFD23C), c(0x8A5A00))
        val col = if (on) c(0x43D17A) else c(0xB0B4C0)
        p.tri(9, 6, 9, 16, 15, 11, darker(col, 0.5f))
        p.tri(9, 7, 9, 15, 14, 11, col)
        p.rect(5, 30, 6, 2, c(0x6A6F85))
        return p.toImg()
    }

    private fun makeLeaf(): Img {
        val p = Pix(20, 9)
        p.ellipseO(10f, 5f, 9f, 3.4f, c(0x66BB6A), c(0x1B5E20))
        p.line(2, 5, 17, 5, c(0x2E7D32))
        p.ellipse(7f, 4f, 3f, 1f, c(0xA5D6A7))
        return p.toImg()
    }

    // ---------- tile per tema ----------
    private fun buildTheme(k: Int): ThemeArt {
        val d = themes[k]
        val t = ThemeArt()
        t.groundTop = Array(2) { groundTile(d, true, it, k) }
        t.groundFill = Array(2) { groundTile(d, false, it, k) }
        t.brick = brickTile(d)
        t.qblock = Array(3) { qTile(it) }
        t.used = usedTile()
        t.stone = stoneTile()
        t.spike = spikeTile()
        t.pillarCap = pillarTile(d, true)
        t.pillarBody = pillarTile(d, false)
        t.platform = platformTile(d, k)
        t.ice = iceTile()
        t.spring = Array(2) { springTile(it) }
        t.lavaTop = Array(2) { lavaTile(true, it) }
        t.lavaFill = lavaTile(false, 0)
        t.far = farLayer(k, true)
        t.mid = farLayer(k, false)
        t.clouds = Array(3) { cloudImg(it, lighter(d.skyBot, 0.5f), k) }
        t.slime = Array(3) { slimeImg(d, it) }
        t.snail = Array(3) { snailImg(d, it) }
        t.frog = Array(2) { frogImg(d, it) }
        t.boss = Array(2) { bossImg(k, it) }
        return t
    }

    private fun groundTile(d: ThemeDef, top: Boolean, v: Int, k: Int): Img {
        val p = Pix(16, 16)
        val dirtDark = darker(d.dirtB, 0.25f)
        for (y in 0 until 16) for (x in 0 until 16) {
            val h = hash2(x, y, v + 3)
            p.set(x, y, if (h % 11 == 0) d.dirtB else if (h % 29 == 0) lighter(d.dirtA, 0.15f) else d.dirtA)
        }
        // sassolini con ombra
        for (i in 0 until 2) {
            val sx = 2 + (hash2(i, v, 31) % 11); val sy = 6 + (hash2(i, v, 37) % 8)
            p.rect(sx, sy, 3, 2, lighter(d.dirtA, 0.3f)); p.rect(sx, sy + 2, 3, 1, dirtDark); p.set(sx, sy, lighter(d.dirtA, 0.5f))
        }
        if (top) {
            val g = d.topA; val gd = d.topB; val gl = lighter(d.topA, 0.35f); val go = darker(d.topB, 0.4f)
            for (x in 0 until 16) {
                // ciuffi irregolari sul bordo inferiore dell'erba
                val drop = 4 + (hash2(x / 2, 3, v + 5) % 3)
                for (y in 0 until drop) p.set(x, y, if (y == 0) gl else if (y < drop - 1) g else gd)
                p.set(x, drop, go)
                if (hash2(x, 8, v) % 5 == 0) p.set(x, 1, lighter(g, 0.2f))
            }
            if (k == 2) for (x in 0 until 16) { p.set(x, 0, c(0xFFFFFF)); if (hash2(x, 7, 2) % 3 == 0) p.set(x, 1, c(0xFFFFFF)) }
            if (k == 3) for (x in 0 until 16) if (hash2(x, 5, 9) % 6 == 0) p.set(x, 2, c(0xFF8A3A))
        } else {
            for (x in 0 until 16) if (hash2(x, 0, v) % 3 == 0) p.set(x, 0, dirtDark)
        }
        return p.toImg()
    }

    private fun brickTile(d: ThemeDef): Img {
        val p = Pix(16, 16)
        p.rect(0, 0, 16, 16, d.brickA)
        val mortar = d.brickB
        for (r in 0 until 4) {
            val y = r * 4
            p.rect(0, y + 3, 16, 1, mortar)
            val off = if (r % 2 == 0) 7 else 3
            p.rect(off, y, 1, 4, mortar)
            if (r % 2 == 1) p.rect(11, y, 1, 4, mortar)
            for (bx in 0 until 16) if (bx % 8 == (off + 1) % 8) p.set(bx, y, lighter(d.brickA, 0.25f))
        }
        p.rect(0, 0, 16, 1, lighter(d.brickA, 0.2f))
        p.rect(15, 0, 1, 16, darker(d.brickB, 0.3f))
        p.rect(0, 15, 16, 1, darker(d.brickB, 0.3f))
        return p.toImg()
    }

    private fun qTile(f: Int): Img {
        val p = Pix(16, 16)
        val gold = c(0xF2B21A); val dk = c(0x9A5A08); val lt = c(0xFFE27A)
        p.rect(0, 0, 16, 16, dk)
        p.rect(1, 1, 14, 14, gold)
        p.rect(1, 1, 14, 1, lt); p.rect(1, 1, 1, 14, lt)
        p.set(2, 2, dk); p.set(13, 2, dk); p.set(2, 13, dk); p.set(13, 13, dk)
        // fiore
        p.rect(7, 4, 2, 2, c(0xFFFFFF)); p.rect(7, 10, 2, 2, c(0xFFFFFF))
        p.rect(4, 7, 2, 2, c(0xFFFFFF)); p.rect(10, 7, 2, 2, c(0xFFFFFF))
        p.rect(7, 7, 2, 2, c(0xE8334A))
        p.rect(5, 5, 1, 1, c(0xFFF3B0)); p.rect(10, 5, 1, 1, c(0xFFF3B0)); p.rect(5, 10, 1, 1, c(0xFFF3B0)); p.rect(10, 10, 1, 1, c(0xFFF3B0))
        if (f > 0) {
            val s = f * 5
            for (i in 0 until 16) { val x = s - i; if (x in 1..14 && i in 1..14) p.set(x, i, c(0xFFFBD0)) }
        }
        return p.toImg()
    }

    private fun usedTile(): Img {
        val p = Pix(16, 16)
        p.rect(0, 0, 16, 16, c(0x5E5048))
        p.rect(1, 1, 14, 14, c(0x8A786A))
        p.rect(1, 1, 14, 1, c(0xA8968A)); p.rect(1, 1, 1, 14, c(0xA8968A))
        p.set(2, 2, c(0x5E5048)); p.set(13, 2, c(0x5E5048)); p.set(2, 13, c(0x5E5048)); p.set(13, 13, c(0x5E5048))
        return p.toImg()
    }

    private fun stoneTile(): Img {
        val p = Pix(16, 16)
        p.rect(0, 0, 16, 16, c(0x555B6E))
        p.rect(1, 1, 14, 14, c(0x9AA0B4))
        p.rect(1, 1, 14, 2, c(0xC0C6D8)); p.rect(1, 1, 2, 14, c(0xC0C6D8))
        p.rect(13, 3, 2, 12, c(0x7A8096)); p.rect(3, 13, 12, 2, c(0x7A8096))
        p.line(5, 6, 8, 9, c(0x70768C)); p.line(8, 9, 7, 12, c(0x70768C))
        return p.toImg()
    }

    private fun spikeTile(): Img {
        val p = Pix(16, 16)
        for (i in 0 until 3) {
            val x = i * 5 + 1
            p.tri(x, 15, x + 2, 5, x + 4, 15, c(0x40465A))
            p.tri(x + 1, 15, x + 2, 7, x + 3, 15, c(0xC8CEDC))
            p.set(x + 2, 6, c(0xFFFFFF))
        }
        p.rect(0, 14, 16, 2, c(0x40465A))
        return p.toImg()
    }

    private fun pillarTile(d: ThemeDef, cap: Boolean): Img {
        val p = Pix(16, 16)
        val body = d.pillar; val dk = darker(body, 0.35f); val lt = lighter(body, 0.35f)
        if (cap) {
            p.rectO(0, 0, 16, 8, body, dk)
            p.rect(2, 1, 3, 6, lt); p.rect(12, 1, 2, 6, darker(body, 0.15f))
            p.rect(1, 8, 14, 8, dk)
            p.rect(2, 8, 12, 8, body)
            p.rect(4, 8, 2, 8, lt); p.rect(12, 8, 2, 8, darker(body, 0.15f))
            p.rect(2, 8, 12, 1, dk)
        } else {
            p.rect(1, 0, 14, 16, dk)
            p.rect(2, 0, 12, 16, body)
            p.rect(4, 0, 2, 16, lt); p.rect(12, 0, 2, 16, darker(body, 0.15f))
        }
        return p.toImg()
    }

    private fun platformTile(d: ThemeDef, k: Int): Img {
        val p = Pix(16, 16)
        val base = when (k) { 0 -> c(0xB9814C); 1 -> c(0xE0C07A); 2 -> c(0xCFEAFB); else -> c(0x7A6670) }
        val dk = darker(base, 0.35f)
        p.rect(0, 0, 16, 7, dk)
        p.rect(0, 1, 16, 5, base)
        p.rect(0, 1, 16, 1, lighter(base, 0.4f))
        p.rect(4, 2, 1, 4, dk); p.rect(11, 2, 1, 4, dk)
        p.rect(0, 6, 16, 1, dk)
        return p.toImg()
    }

    private fun iceTile(): Img {
        val p = Pix(16, 16)
        p.rect(0, 0, 16, 16, c(0x6FB5E0))
        p.rect(1, 1, 14, 14, c(0xBEE8FF))
        p.rect(1, 1, 14, 1, c(0xFFFFFF)); p.rect(1, 1, 1, 14, c(0xFFFFFF))
        for (i in 0 until 5) { p.set(3 + i, 11 - i, c(0xF0FBFF)); p.set(8 + i, 14 - i, c(0xF0FBFF)) }
        return p.toImg()
    }

    private fun springTile(f: Int): Img {
        val p = Pix(16, 16)
        val red = c(0xE24A5C); val dk = c(0x7A1F2C); val gr = c(0x9AA0B0)
        val padY = if (f == 0) 4 else 9
        val padH = 3
        p.rectO(1, padY, 14, padH + 1, red, dk)
        p.rect(2, padY + 1, 12, 1, c(0xFF8A99))
        val coilTop = padY + padH + 1
        var y = coilTop
        var left = true
        while (y < 14) {
            if (left) p.line(3, y, 12, y + 1, gr) else p.line(12, y, 3, y + 1, gr)
            left = !left
            y += 2
        }
        p.rectO(2, 14, 12, 2, c(0x5A6075), c(0x2A2F44))
        return p.toImg()
    }

    private fun lavaTile(top: Boolean, f: Int): Img {
        val p = Pix(16, 16)
        for (y in 0 until 16) for (x in 0 until 16) {
            val h = hash2(x, y, 5)
            val base = mixColor(c(0xFF8A1E), c(0xE2331A), y / 16f)
            p.set(x, y, if (h % 11 == 0) c(0xFFD23C) else base)
        }
        if (top) {
            for (x in 0 until 16) {
                val wy = (3f + sin((x + f * 4) * 0.5f) * 1.4f).toInt()
                for (y in 0 until wy) p.set(x, y, 0)
                p.set(x, wy, c(0xFFE066))
                p.set(x, wy + 1, c(0xFFB02E))
            }
        }
        return p.toImg()
    }

    private fun hillTop(k: Int, far: Boolean, x: Int): Int {
        val a = 2.0 * PI / VW
        val xd = x.toDouble()
        val v: Double = when (k) {
            0 -> if (far) 150 + 24 * sin(a * 2 * xd + 1) + 10 * sin(a * 5 * xd) else 192 + 14 * sin(a * 3 * xd + 0.5) + 6 * sin(a * 8 * xd)
            1 -> if (far) {
                // mesa a gradini
                val s = sin(a * 3 * xd + 0.4)
                val base = 158 + 28 * s
                (Math.round(base / 10.0) * 10).toDouble()
            } else 205 + 10 * sin(a * 2 * xd + 1) + 5 * sin(a * 6 * xd)
            2 -> if (far) 200 - 100 * (1 - abs(sin(PI * 3 * xd / VW + 0.5))) else 218 - 40 * (1 - abs(sin(PI * 5 * xd / VW + 1.1)))
            else -> if (far) 205 - 105 * (1 - abs(sin(PI * 2 * xd / VW + 1))) else 218 - 48 * (1 - abs(sin(PI * 4 * xd / VW + 0.3)))
        }
        return v.toInt().coerceIn(8, VH - 1)
    }

    private fun wrapSet(p: Pix, x: Int, y: Int, col: Int) { p.set(((x % VW) + VW) % VW, y, col) }

    private fun tree(p: Pix, x: Int, by: Int, h: Int, leaf: Int, trunk: Int) {
        val ol = darker(leaf, 0.55f)
        for (y in by - h / 2 until by) for (dx in 0..1) wrapSet(p, x + dx, y, trunk)
        val r = h / 3 + 3
        val cy = by - h / 2 - r / 2
        for (yy in -r..r) for (xx in -r..r) {
            val d = xx * xx + yy * yy
            if (d <= r * r) {
                val edge = d > (r - 1) * (r - 1)
                val col = if (edge) ol else if (xx + yy < -r / 2) lighter(leaf, 0.3f) else if (xx + yy > r / 2) darker(leaf, 0.2f) else leaf
                wrapSet(p, x + xx, cy + yy, col)
            }
        }
    }

    private fun pine(p: Pix, x: Int, by: Int, h: Int, leaf: Int, snow: Boolean) {
        val ol = darker(leaf, 0.55f)
        for (y in by - 4 until by) wrapSet(p, x, y, c(0x5A3A2A))
        val tiers = 3
        for (t in 0 until tiers) {
            val top = by - h + t * (h / 4)
            val bot = top + h / 2
            val half = 3 + t * 2 + h / 8
            for (y in top..bot) {
                val w = (half * (y - top)) / (bot - top)
                for (xx in -w..w) {
                    val edge = xx == -w || xx == w || y == bot
                    var col = if (edge) ol else if (xx < 0) lighter(leaf, 0.15f) else leaf
                    if (snow && !edge && y - top < (bot - top) / 3 + 1) col = c(0xF4FBFF)
                    wrapSet(p, x + xx, y, col)
                }
            }
        }
    }

    private fun cactus(p: Pix, x: Int, by: Int, h: Int) {
        val g = c(0x4FA84A); val ol = c(0x2A5E2A); val lt = c(0x7ED36A)
        for (y in by - h until by) { wrapSet(p, x - 2, y, ol); wrapSet(p, x + 2, y, ol); wrapSet(p, x - 1, y, lt); wrapSet(p, x, y, g); wrapSet(p, x + 1, y, g) }
        for (xx in -1..1) wrapSet(p, x + xx, by - h - 1, ol)
        val ay = by - h / 2
        for (xx in 3..6) { wrapSet(p, x + xx, ay, g); wrapSet(p, x + xx, ay - 1, ol); wrapSet(p, x + xx, ay + 1, ol) }
        for (y in ay - 6..ay) { wrapSet(p, x + 6, y, g); wrapSet(p, x + 5, y, ol); wrapSet(p, x + 7, y, ol) }
        for (xx in -6..-3) { wrapSet(p, x + xx, ay + 3, g); wrapSet(p, x + xx, ay + 2, ol); wrapSet(p, x + xx, ay + 4, ol) }
        for (y in ay - 3..ay + 3) { wrapSet(p, x - 6, y, g); wrapSet(p, x - 5, y, ol); wrapSet(p, x - 7, y, ol) }
    }

    private fun farLayer(k: Int, far: Boolean): Img {
        val d = themes[k]
        val p = Pix(VW, VH)
        val col = if (far) d.far else d.mid
        val tops = IntArray(VW) { hillTop(k, far, it) }
        val dark = darker(col, 0.5f)
        for (x in 0 until VW) {
            val t0 = tops[x]
            for (y in t0 until VH) {
                val rel = y - t0
                val band = rel / 26
                var shade = if (band == 0) col else mixColor(col, dark, minOf(0.65f, band * 0.22f))
                // dithering al confine tra le fasce
                if (rel % 26 < 2 && band > 0 && (x + y) % 2 == 0) shade = mixColor(col, dark, minOf(0.65f, (band - 1) * 0.22f))
                p.px[y * VW + x] = shade
            }
            // bordo: luce sopra, contorno scuro
            if (t0 > 0) p.px[(t0 - 1) * VW + x] = dark
            if (t0 + 1 < VH) p.px[t0 * VW + x] = lighter(col, 0.28f)
            // pendio illuminato verso sinistra
            val prev = tops[(x + VW - 1) % VW]
            if (prev > t0 && t0 + 2 < VH) p.px[(t0 + 1) * VW + x] = lighter(col, 0.12f)
        }
        // dettagli per tema
        var seed = 11 + k * 7 + (if (far) 0 else 100)
        fun rnd(n: Int): Int { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) % n }
        when (k) {
            0 -> if (far) {
                for (i in 0 until 16) { val x = 14 + i * 30 + rnd(10); tree(p, x, tops[x % VW] + 2, 12, lighter(d.far, 0.0f).let { darker(it, 0.15f) }, c(0x6A4A2A)) }
            } else {
                for (i in 0 until 9) { val x = 20 + i * 52 + rnd(16); tree(p, x, tops[x % VW] + 3, 26 + rnd(10), c(0x2FA85A), c(0x7A4F2A)) }
                for (i in 0 until 24) { val x = 6 + i * 20 + rnd(12); val y = tops[x % VW] + 4 + rnd(10); wrapSet(p, x, y, c(0xFFE066)); wrapSet(p, x + 1, y, c(0xFF7BAC)); wrapSet(p, x, y + 1, c(0xFFFFFF)) }
            }
            1 -> if (far) {
                // strati di roccia sulle mesa
                for (x in 0 until VW) { val t0 = tops[x]; for (y in t0 + 8 until VH step 9) p.px[y * VW + x] = darker(col, 0.25f) }
            } else {
                for (i in 0 until 8) { val x = 24 + i * 58 + rnd(20); cactus(p, x, tops[x % VW] + 2, 18 + rnd(14)) }
            }
            2 -> if (far) {
                // neve sulle cime e facce in ombra
                for (x in 0 until VW) { val t0 = tops[x]; for (y in t0 until minOf(VH, t0 + 12)) p.px[y * VW + x] = if ((x + y) % 7 == 0) c(0xDDEEFA) else c(0xFFFFFF) ; for (y in t0 + 12 until minOf(VH, t0 + 14)) if ((x + y) % 2 == 0) p.px[y * VW + x] = c(0xFFFFFF) }
            } else {
                for (i in 0 until 12) { val x = 12 + i * 40 + rnd(14); pine(p, x, tops[x % VW] + 3, 26 + rnd(12), c(0x2E8A6A), true) }
            }
            else -> if (far) {
                for (x in 0 until VW) { val t0 = tops[x]; if (t0 < 130) { for (y in t0 until minOf(VH, t0 + 3)) p.px[y * VW + x] = if (y == t0) c(0xFFD84A) else c(0xFF7A2A) } }
                for (i in 0 until 7) { val x = 30 + i * 66 + rnd(20); val t0 = tops[x % VW]; for (y in t0 + 4 until minOf(VH, t0 + 40 + rnd(30))) wrapSet(p, x + (y - t0) / 6 % 3, y, c(0xFF7A2A)) }
            } else {
                for (i in 0 until 10) { val x = 18 + i * 46 + rnd(16); val t0 = tops[x % VW]; for (y in t0 + 1 until minOf(VH, t0 + 5)) wrapSet(p, x, y, c(0xFF6A2A)); wrapSet(p, x + 1, t0 + 2, c(0xFFD84A)) }
            }
        }
        return p.toImg()
    }

    private fun cloudImg(v: Int, col: Int, k: Int): Img {
        val w = 44 + v * 8
        val p = Pix(w, 20)
        val sh = darker(col, 0.12f)
        val colr = if (k == 3) c(0x7A5A5A) else col
        val shr = if (k == 3) c(0x5A3E42) else sh
        p.ellipse(w * 0.3f, 12f, w * 0.22f, 6f, shr)
        p.ellipse(w * 0.7f, 12f, w * 0.24f, 6f, shr)
        p.ellipse(w * 0.5f, 13f, w * 0.38f, 5f, shr)
        p.ellipse(w * 0.3f, 10f, w * 0.2f, 6f, colr)
        p.ellipse(w * 0.55f, 7f, w * 0.2f, 6.5f, colr)
        p.ellipse(w * 0.72f, 10f, w * 0.2f, 5.5f, colr)
        p.ellipse(w * 0.5f, 11f, w * 0.36f, 4.5f, colr)
        return p.toImg()
    }

    private fun slimeImg(d: ThemeDef, f: Int): Img {
        val p = Pix(16, 16)
        val ol = darker(d.enemyA, 0.6f)
        if (f == 2) {
            p.ellipseO(8f, 13.5f, 7f, 2f, d.enemyA, ol)
            p.rect(4, 13, 2, 1, c(0xFFFFFF)); p.rect(10, 13, 2, 1, c(0xFFFFFF))
            return p.toImg()
        }
        if (f == 0) p.ellipseO(8f, 10.4f, 6.4f, 4.8f, d.enemyA, ol) else p.ellipseO(8f, 11.5f, 6.9f, 3.8f, d.enemyA, ol)
        val ey = if (f == 0) 8 else 9
        p.rect(4, ey, 3, 3, c(0xFFFFFF)); p.rect(9, ey, 3, 3, c(0xFFFFFF))
        p.rect(5, ey + 1, 1, 2, c(0x1A1A2E)); p.rect(10, ey + 1, 1, 2, c(0x1A1A2E))
        p.set(4, ey - 2 + if (f == 0) 0 else 1, lighter(d.enemyA, 0.5f)); p.set(5, ey - 2 + if (f == 0) 0 else 1, lighter(d.enemyA, 0.5f))
        return p.toImg()
    }

    private fun snailImg(d: ThemeDef, f: Int): Img {
        val p = Pix(16, 16)
        val ol = c(0x3A2A20)
        val skin = c(0xE8C79A)
        val shell = d.enemyB
        if (f == 2) {
            p.ellipseO(8f, 9.5f, 6f, 5.6f, shell, ol)
            p.ellipse(8f, 9.5f, 3.6f, 3.4f, darker(shell, 0.2f))
            p.ellipse(8f, 9.5f, 1.6f, 1.4f, shell)
            p.rect(3, 13, 3, 2, c(0x1A1A2E))
            return p.toImg()
        }
        p.ellipseO(7f, 12.5f, 6.2f, 2.6f, skin, ol)
        p.ellipseO(13f, 9.5f, 2f, 3.2f, skin, ol)
        p.line(12, 6, 11, 3 + f, ol); p.line(14, 6, 15, 3 + f, ol)
        p.set(13, 8, c(0x1A1A2E))
        p.ellipseO(6f, 8f, 5f, 5f, shell, ol)
        p.ellipse(6f, 8f, 3f, 3f, darker(shell, 0.2f))
        p.ellipse(6f, 8f, 1.3f, 1.3f, shell)
        p.set(3, 5, lighter(shell, 0.5f))
        return p.toImg()
    }

    private fun frogImg(d: ThemeDef, f: Int): Img {
        val p = Pix(16, 16)
        val ol = darker(d.enemyB, 0.65f)
        val belly = lighter(d.enemyB, 0.55f)
        if (f == 0) {
            p.ellipseO(8f, 10.5f, 6.2f, 4.4f, d.enemyB, ol)
            p.ellipse(8f, 12f, 3.6f, 2.2f, belly)
            p.ellipseO(4.5f, 6.2f, 2.3f, 2.3f, d.enemyB, ol)
            p.ellipseO(11.5f, 6.2f, 2.3f, 2.3f, d.enemyB, ol)
            p.rect(4, 5, 2, 2, c(0xFFFFFF)); p.rect(11, 5, 2, 2, c(0xFFFFFF))
            p.set(5, 6, c(0x1A1A2E)); p.set(12, 6, c(0x1A1A2E))
            p.rect(4, 11, 8, 1, ol)
        } else {
            p.ellipseO(8f, 8.5f, 4.6f, 6f, d.enemyB, ol)
            p.ellipse(8f, 10f, 2.6f, 3.6f, belly)
            p.ellipseO(5.5f, 3.4f, 2f, 2f, d.enemyB, ol)
            p.ellipseO(10.5f, 3.4f, 2f, 2f, d.enemyB, ol)
            p.rect(5, 3, 1, 2, c(0xFFFFFF)); p.rect(10, 3, 1, 2, c(0xFFFFFF))
            p.line(4, 12, 1, 15, ol); p.line(12, 12, 15, 15, ol)
            p.line(6, 14, 6, 15, ol); p.line(10, 14, 10, 15, ol)
        }
        return p.toImg()
    }

    val hedge: Array<Img> by lazy { Array(2) { makeHedge(it) } }
    val bat: Array<Img> by lazy { Array(2) { makeBat(it) } }

    private fun makeHedge(f: Int): Img {
        val p = Pix(16, 16)
        val ol = c(0x2E1E14); val body = c(0x8A5A3A); val spike = c(0x4A3A5A); val tip = c(0xD8D0F0)
        val tips = intArrayOf(3, 5, 8, 4, 11, 5, 14, 8)
        var i = 0
        while (i < tips.size) {
            val x = tips[i]; val ty = tips[i + 1]
            p.tri(x - 2, 10, x, ty - 2, x + 2, 10, ol)
            p.tri(x - 1, 10, x, ty, x + 1, 10, spike)
            p.set(x, ty - 1, tip)
            i += 2
        }
        p.ellipseO(7.5f, 10.5f, 6f, 4.4f, body, ol)
        p.ellipseO(13f, 11.5f, 2.2f, 1.8f, c(0xE8C79A), ol)
        p.set(12, 9, c(0xFFFFFF)); p.set(12, 10, c(0x1A1A2E))
        p.set(15, 11, c(0x1A1A2E))
        if (f == 0) { p.rect(4, 14, 2, 2, ol); p.rect(10, 14, 2, 2, ol) } else { p.rect(5, 14, 2, 2, ol); p.rect(9, 14, 2, 2, ol) }
        return p.toImg()
    }

    private fun makeBat(f: Int): Img {
        val p = Pix(16, 16)
        val ol = c(0x1E1030); val body = c(0x6A4AA0); val wing = c(0x4A3280)
        if (f == 0) {
            p.tri(5, 8, 0, 2, 1, 10, ol); p.tri(5, 8, 1, 3, 2, 9, wing)
            p.tri(11, 8, 15, 2, 14, 10, ol); p.tri(11, 8, 14, 3, 13, 9, wing)
        } else {
            p.tri(5, 7, 0, 14, 1, 7, ol); p.tri(5, 7, 1, 13, 2, 8, wing)
            p.tri(11, 7, 15, 14, 14, 7, ol); p.tri(11, 7, 14, 13, 13, 8, wing)
        }
        p.ellipseO(8f, 8f, 3.4f, 3.8f, body, ol)
        p.tri(5, 5, 5, 1, 7, 5, ol); p.tri(11, 5, 11, 1, 9, 5, ol)
        p.rect(6, 7, 1, 2, c(0xFF5A5A)); p.rect(9, 7, 1, 2, c(0xFF5A5A))
        p.set(7, 11, c(0xFFFFFF)); p.set(9, 11, c(0xFFFFFF))
        return p.toImg()
    }

    private fun bossImg(world: Int, f: Int): Img {
        val p = Pix(40, 36)
        val sq = if (f == 1) 2 else 0
        val ol = c(0x24122E)
        when (world) {
            0 -> { // Re Gelatina
                val body = c(0xFF7BAC)
                p.ellipseO(20f, 23f + sq / 2f, 17f, 12f - sq / 2f, body, ol)
                p.ellipse(14f, 17f, 5f, 3f, lighter(body, 0.4f))
                // corona
                p.rectO(11, 3 + sq, 18, 6, c(0xFFD23C), c(0x8A5A00))
                p.tri(11, 4 + sq, 13, -1 + sq, 15, 4 + sq, c(0xFFD23C)); p.tri(18, 4 + sq, 20, -1 + sq, 22, 4 + sq, c(0xFFD23C)); p.tri(25, 4 + sq, 27, -1 + sq, 29, 4 + sq, c(0xFFD23C))
                p.rect(15, 5 + sq, 2, 2, c(0xE8334A)); p.rect(23, 5 + sq, 2, 2, c(0x26C6DA))
            }
            1 -> { // Scorpione
                val body = c(0xD9A441)
                p.ellipseO(19f, 25f + sq / 2f, 15f, 9f - sq / 2f, body, ol)
                p.ellipseO(5f, 22f, 4f, 3.5f, body, ol)       // chele
                p.ellipseO(35f, 22f, 4f, 3.5f, body, ol)
                p.ellipseO(33f, 16f, 2.6f, 2.6f, body, ol); p.ellipseO(36f, 10f, 2.6f, 2.6f, body, ol)
                p.ellipseO(33f, 4.5f, 2.6f, 2.6f, body, ol); p.ellipseO(27f, 3f, 2.6f, 2.6f, body, ol)
                p.tri(22, 6, 25, 1, 25, 8, c(0xE8334A))
                p.ellipse(14f, 21f, 4f, 2.4f, lighter(body, 0.4f))
            }
            2 -> { // Yeti
                val body = c(0xEAF4FF)
                p.ellipseO(20f, 23f + sq / 2f, 17f, 12f - sq / 2f, body, ol)
                p.ellipseO(7f, 30f, 5f, 4f, body, ol); p.ellipseO(33f, 30f, 5f, 4f, body, ol)
                for (i in 0 until 4) p.tri(8 + i * 8, 11 + sq, 11 + i * 8, 3 + sq, 14 + i * 8, 11 + sq, lighter(c(0x9AC8F0), 0.3f))
                p.ellipse(20f, 20f + sq / 2f, 9f, 6.5f, c(0x4A6FA5))
                p.ellipse(20f, 16f, 7f, 2f, lighter(body, 0.2f))
            }
            else -> { // Drago
                val body = c(0xE24A3A)
                p.tri(2, 8, 12, 20, 6, 28, ol); p.tri(38, 8, 28, 20, 34, 28, ol)
                p.tri(4, 11, 12, 21, 8, 26, c(0x8A2A3A)); p.tri(36, 11, 28, 21, 32, 26, c(0x8A2A3A))
                p.ellipseO(20f, 23f + sq / 2f, 15f, 12f - sq / 2f, body, ol)
                p.ellipse(20f, 27f, 9f, 6f, c(0xFFB06A))
                p.tri(10, 11 + sq, 12, 2 + sq, 16, 11 + sq, c(0xFFF3E0)); p.tri(30, 11 + sq, 28, 2 + sq, 24, 11 + sq, c(0xFFF3E0))
            }
        }
        // occhi cattivi
        val ey = 16 + sq
        p.ellipseO(13f, ey.toFloat(), 3f, 3.2f, c(0xFFFFFF), ol)
        p.ellipseO(27f, ey.toFloat(), 3f, 3.2f, c(0xFFFFFF), ol)
        p.rect(14, ey, 2, 3, c(0xE8334A)); p.rect(24, ey, 2, 3, c(0xE8334A))
        p.line(9, ey - 5, 16, ey - 2, ol); p.line(31, ey - 5, 24, ey - 2, ol)
        // bocca
        p.rect(13, 27 + sq / 2, 14, 3, ol)
        for (i in 0 until 4) p.rect(14 + i * 3, 27 + sq / 2, 2, 1, c(0xFFFFFF))
        return p.toImg()
    }
}

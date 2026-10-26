package com.marco.pixelbloom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object WorldRender {

    /** Immagine di un tile (usata anche dall'editor). `above` e' il tile sopra, per scegliere la versione "con bordo". */
    fun tileImage(t: ThemeArt, id: Int, above: Int, tx: Int, ty: Int, tick: Int, springDown: Boolean = false): Img? {
        return when (id) {
            T.GROUND -> {
                val v = hash2(tx, ty, 0) and 1
                if (above == T.GROUND || above == T.ICE) t.groundFill[v] else t.groundTop[v]
            }
            T.BRICK -> t.brick
            T.QCOIN, T.QPOWER -> {
                val f = (tick / 5) % 14
                t.qblock[if (f < 3) f else 0]
            }
            T.USED -> t.used
            T.STONE -> t.stone
            T.SPIKE -> t.spike
            T.PIPE -> if (above == T.PIPE) t.pillarBody else t.pillarCap
            T.PLATFORM -> t.platform
            T.COIN -> Art.coin[((tick / 6) + tx) % 4]
            T.SEED -> Art.seed[(tick / 14) % 2]
            T.ICE -> t.ice
            T.SPRING -> t.spring[if (springDown) 1 else 0]
            T.LAVA -> if (above == T.LAVA) t.lavaFill else t.lavaTop[(tick / 14) % 2]
            else -> null
        }
    }

    fun drawSky(g: Gfx, theme: Int, camX: Float, tick: Int, wonder: Int, wonderOn: Boolean) {
        val d = Art.themes[theme.coerceIn(0, 3)]
        val a = Art.theme(theme)
        var top = d.skyTop
        var bot = d.skyBot
        if (wonderOn) {
            val tint = when (wonder) { 1 -> 0x2A1A6A; 2 -> 0x1F8F4F; 3 -> 0x1A8FB0; else -> 0xB0508A }
            top = mixColor(top, tint, 0.55f); bot = mixColor(bot, tint, 0.35f)
        }
        for (i in 0 until 18) g.fill(0, i * 15, VW, 15, mixColor(top, bot, i / 17f))
        if (wonderOn && wonder == 1) {
            for (i in 0 until 40) {
                val x = hash2(i, 1, 5) % VW
                val y = hash2(i, 2, 5) % 170
                if (((tick / 8) + i) % 5 != 0) g.fill(x, y, if (i % 7 == 0) 2 else 1, if (i % 7 == 0) 2 else 1, 0xFFFFFFFF.toInt())
            }
        }
        // nuvole
        val span = VW + 90
        for (i in 0 until 5) {
            val im = a.clouds[i % 3]
            var x = ((i * 113 + 30) - (camX * 0.12f).toInt() - (tick / 24)) % span
            if (x < 0) x += span
            g.img(im, x - 60, 18 + (i * 29) % 70)
        }
        // colline / montagne in parallasse
        var off = ((camX * 0.2f).toInt()) % VW
        if (off < 0) off += VW
        g.img(a.far, -off, 0)
        g.img(a.far, VW - off, 0)
        off = ((camX * 0.45f).toInt()) % VW
        if (off < 0) off += VW
        g.img(a.mid, -off, 0)
        g.img(a.mid, VW - off, 0)
    }

    fun draw(g: Gfx, w: World, levelLabel: String = "") {
        val tick = w.frame
        val lv = w.level
        val th = lv.theme.coerceIn(0, 3)
        val ta = Art.theme(th)
        var shx = 0; var shy = 0
        if (w.shake > 0) { shx = if (tick % 2 == 0) 1 else -1; shy = if (tick % 3 == 0) 1 else -1; shx *= (w.shake / 4 + 1); shy *= (w.shake / 5 + 1) }
        val cx = w.camX.toInt() + shx
        val cy = shy
        val wonderOn = w.wonderT > 0

        drawSky(g, th, w.camX, tick, lv.wonder, wonderOn)

        // checkpoint e traguardo (dietro)
        for (i in w.checks.indices) {
            val c = w.checks[i]
            g.img(if (w.checkOn[i]) Art.flagCheckOn else Art.flagCheckOff, c[0] * TS - cx, (c[1] - 1) * TS - cy)
        }
        if (w.goalTx >= 0) drawGoal(g, w, cx, cy, ta)

        // entita' (dietro ai tile cosi' gli oggetti escono "dal" blocco)
        drawEnts(g, w, cx, cy, ta)

        // tile
        val tx0 = max(0, cx / TS)
        val tx1 = min(w.W - 1, (cx + VW) / TS + 1)
        for (ty in 0 until w.H) {
            for (tx in tx0..tx1) {
                val id = w.tiles[ty * w.W + tx]
                if (id == T.EMPTY) continue
                val idx = ty * w.W + tx
                val above = if (ty > 0) w.tiles[idx - w.W] else T.EMPTY
                val im = tileImage(ta, id, above, tx, ty, tick, w.springT.containsKey(idx)) ?: continue
                var yy = ty * TS - cy
                val b = w.bump[idx]
                if (b != null) yy -= (sin(b / 8f * PI) * 5f).toInt()
                if (id == T.SEED) yy += (sin(tick * 0.12f) * 2f).toInt()
                g.img(im, tx * TS - cx, yy)
            }
        }

        drawPlayer(g, w, cx, cy)

        // particelle
        for (q in w.parts) {
            val x = q.x.toInt() - cx
            val y = q.y.toInt() - cy
            when (q.kind) {
                1 -> g.img(Art.coin[(q.life / 3) % 4], x - 8, y - 8)
                3 -> g.fill(x, y, q.size, q.size, q.color)
                else -> {
                    val a = min(255, q.life * 18)
                    g.fill(x, y, q.size, q.size, withAlpha(a, q.color))
                }
            }
        }

        // effetti Meraviglia
        if (wonderOn) {
            val pulse = (sin(tick * 0.12f) * 0.5f + 0.5f)
            val tint = when (lv.wonder) { 1 -> 0x3A1A9A; 2 -> 0x20C060; 3 -> 0x20D0FF; else -> 0xFFC0E0 }
            g.fill(0, 0, VW, VH, withAlpha(22 + (pulse * 22).toInt(), tint))
            if (lv.wonder == 3) {
                for (i in 0 until 8) {
                    val y = hash2(i, tick / 3, 9) % VH
                    val x = (hash2(i, tick / 3, 4) % VW)
                    g.fill(x, y, 40, 1, withAlpha(120, 0xFFFFFF))
                }
            }
            // anello attorno al giocatore
            val rr = 18 + (pulse * 6).toInt()
            val pxc = (w.p.x + w.p.w / 2f).toInt() - cx
            val pyc = (w.p.y + w.p.h / 2f).toInt() - cy
            for (i in 0 until 16) {
                val ang = i / 16f * 2f * PI.toFloat() + tick * 0.08f
                g.fill(pxc + (kotlin.math.cos(ang) * rr).toInt(), pyc + (sin(ang) * rr).toInt(), 2, 2, withAlpha(200, 0xFFF59D))
            }
        }
        if (w.flash > 0) g.fill(0, 0, VW, VH, withAlpha(min(200, w.flash * 9), 0xFFFFFF))
        drawHud(g, w, levelLabel)
    }

    private fun drawGoal(g: Gfx, w: World, cx: Int, cy: Int, ta: ThemeArt) {
        val px = w.goalTx * TS + 7 - cx
        val base = (w.goalTy + 1) * TS - cy
        val top = base - POLE_H * TS
        g.fill(px, top, 2, base - top, 0xFFE8ECF4.toInt())
        g.fill(px, top, 1, base - top, 0xFFFFFFFF.toInt())
        g.fill(px - 3, base - 6, 8, 6, 0xFF6A6F85.toInt())
        g.fill(px - 2, top - 5, 6, 6, 0xFFFFD23C.toInt())
        g.fill(px - 1, top - 4, 2, 2, 0xFFFFF59D.toInt())
        var fy = top + 6
        if (w.state >= 2) fy = max(top + 6, min(base - 26, w.p.y.toInt() - cy - 6))
        val col = 0xFFE8334A.toInt()
        for (r in 0 until 12) {
            val width = if (r < 6) 4 + r * 2 else 4 + (11 - r) * 2
            g.fill(px + 2, fy + r, width, 1, if (r == 0) 0xFFFF8FA0.toInt() else col)
        }
    }

    private fun drawEnts(g: Gfx, w: World, cx: Int, cy: Int, ta: ThemeArt) {
        val tick = w.frame
        for (e in w.ents) {
            val sx = e.x.toInt() - cx
            if (sx < -64 || sx > VW + 64) continue
            val sy = e.y.toInt() - cy
            when (e.type) {
                K.SLIME -> {
                    val im = if (e.state == 9) ta.slime[2] else ta.slime[(e.t / 12) % 2]
                    g.img(im, sx - 1, sy + e.h.toInt() - 16, false, e.state == 8, 16, 16)
                }
                K.SNAIL -> {
                    val im = if (e.state == 1 || e.state == 2) ta.snail[2] else ta.snail[(e.t / 14) % 2]
                    val flip = if (e.state == 2) (e.t / 3) % 2 == 0 else e.dir < 0
                    g.img(im, sx - 1, sy + e.h.toInt() - 16, flip, e.state == 8, 16, 16)
                }
                K.HEDGE -> g.img(Art.hedge[(e.t / 10) % 2], sx - 1, sy + e.h.toInt() - 16, e.dir < 0, e.state == 8, 16, 16)
                K.BAT -> g.img(Art.bat[(e.t / 6) % 2], sx - 2, sy + e.h.toInt() - 14, false, e.state == 8, 16, 16)
                K.FROG -> g.img(ta.frog[if (e.onGround) 0 else 1], sx - 1, sy + e.h.toInt() - 16, e.dir > 0, e.state == 8, 16, 16)
                K.BOSS -> {
                    val blink = e.inv > 0 && (tick / 3) % 2 == 0
                    if (!blink || e.state == 10) {
                        val im = ta.boss[if (e.state == 10) (tick / 3) % 2 else (e.t / 18) % 2]
                        g.img(im, sx - 4, sy + e.h.toInt() - 36, e.dir > 0, false, 40, 36)
                    }
                }
                En.BULB -> g.img(Art.bulb, sx - 1, sy - 1)
                En.FLOWER -> g.img(Art.flower, sx - 1, sy - 1)
                En.SHOT -> g.img(Art.shot, sx, sy)
                En.COINFALL -> g.img(Art.coin[(e.t / 5) % 4], sx - 3, sy - 3)
                En.GEM -> g.img(Art.gem[(tick / 10) % 2], sx - 1, sy - 1 + (sin(tick * 0.1f) * 2f).toInt())
            }
        }
    }

    fun heroFrame(w: World): Int {
        val p = w.p
        return when {
            w.state == 1 -> 4
            w.state == 2 -> 3
            !w.onGround && w.state != 3 -> 3
            w.skid -> 1
            abs(p.vx) > 0.2f -> 1 + (w.walkAnim.toInt() % 2)
            else -> 0
        }
    }

    private fun drawPlayer(g: Gfx, w: World, cx: Int, cy: Int) {
        val p = w.p
        if (w.state == 0 && w.inv > 0 && (w.frame / 3) % 2 == 0) return
        val im = Art.hero(w.hero, heroFrame(w))
        val flip = w.facing < 0
        val scale = if (w.giant) 1.7f else 1f
        val dw = (16 * scale).toInt()
        val dh = (24 * scale).toInt()
        val x = (p.x + p.w / 2f).toInt() - dw / 2 - cx
        val y = (p.y + p.h).toInt() + 2 - dh - cy
        if (w.hp >= 2 && w.state == 0) {
            // alone luminoso quando e' "potenziato"
            val a = 40 + ((sin(w.frame * 0.15f) * 0.5f + 0.5f) * 40).toInt()
            g.fill(x + dw / 2 - 1, y - 3, 2, 2, withAlpha(a + 120, 0xFFF59D))
        }
        g.img(im, x, y, flip, false, dw, dh)
        if (w.gliding) g.img(Art.leaf, x + dw / 2 - 10, y - 8)
        if (w.pounding) {
            g.fill(x + 2, y - 8, 1, 6, withAlpha(200, 0xFFFFFF)); g.fill(x + dw - 3, y - 8, 1, 6, withAlpha(200, 0xFFFFFF))
        }
        if (w.hasFlower && w.state == 0) g.img(Art.shot, x + (if (flip) -2 else dw - 4), y + dh / 2)
    }

    fun drawHud(g: Gfx, w: World, levelLabel: String) {
        // cuori
        for (i in 0 until 2) g.img(if (i < w.hp) Art.heart else Art.heartEmpty, 6 + i * 10, 6)
        g.img(Art.coinIcon, 6, 18)
        Font.text(g, "x" + w.coins, 16, 19, 0xFFFFFFFF.toInt(), 1, 0xFF000000.toInt())
        g.img(Art.seedIcon, 6, 29)
        if (w.seedGot) Font.text(g, "OK", 16, 30, 0xFFFFD23C.toInt(), 1, 0xFF000000.toInt())
        else Font.text(g, "-", 16, 30, 0xFFAAAAAA.toInt(), 1, 0xFF000000.toInt())
        if (levelLabel.isNotEmpty()) Font.center(g, levelLabel, VW / 2, 6, 0xFFFFFFFF.toInt(), 1, 0xFF000000.toInt())
        val sec = max(0, w.timeLeft / 60)
        val ts = "T " + sec
        Font.text(g, ts, VW - 130 - Font.width(ts), 6, if (sec < 30) 0xFFFF6A6A.toInt() else 0xFFFFFFFF.toInt(), 1, 0xFF000000.toInt())
        if (w.wonderT > 0) {
            val bw = 80
            val f = w.wonderT / 720f
            g.fill(VW / 2 - bw / 2 - 1, 17, bw + 2, 6, 0xFF000000.toInt())
            g.fill(VW / 2 - bw / 2, 18, (bw * f).toInt(), 4, 0xFFFF80FF.toInt())
        }
        val boss = w.bossEnt
        if (boss != null && boss.state < 8 && abs(boss.x - w.camX - VW / 2) < VW) {
            val bw = 120
            val f = max(0, boss.hp).toFloat() / max(1, w.bossMax)
            g.fill(VW / 2 - bw / 2 - 1, 30, bw + 2, 8, 0xFF000000.toInt())
            g.fill(VW / 2 - bw / 2, 31, (bw * f).toInt(), 6, 0xFFE8334A.toInt())
        }
        if (w.bannerT > 0 && w.banner.isNotEmpty()) {
            val a = min(255, w.bannerT * 12)
            Font.center(g, w.banner, VW / 2, 60, withAlpha(a, 0xFFFFFF), 2, withAlpha(a, 0x000000))
        }
    }
}

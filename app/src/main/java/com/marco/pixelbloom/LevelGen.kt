package com.marco.pixelbloom

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Genera i 20 livelli del gioco (4 mondi x 5): ogni livello e' costruito a "pezzi" con salti sempre fattibili. */
object LevelGen {
    private val cache = arrayOfNulls<Level>(20)

    fun level(index: Int): Level {
        val i = index.coerceIn(0, 19)
        var l = cache[i]
        if (l == null) { l = build(i); cache[i] = l }
        return l.copy()
    }

    private class B(val lv: Level, val rnd: Random, val theme: Int, val d: Float) {
        val gy = 13
        val H = lv.h

        fun ground(x0: Int, x1: Int, top: Int = gy) {
            for (x in x0 until minOf(x1, lv.w)) for (y in top until H) lv.set(x, y, T.GROUND)
        }

        fun flat(x: Int, len: Int): Int {
            ground(x, x + len)
            if (theme == 2 && len >= 5 && rnd.nextInt(3) == 0) for (cx in x + 1 until x + len - 1) lv.set(cx, gy, T.ICE)
            return x + len
        }

        fun coin(x: Int, y: Int) { if (lv.get(x, y) == T.EMPTY) lv.set(x, y, T.COIN) }

        fun coinArc(x0: Int, n: Int, baseRow: Int) {
            for (i in 0 until n) {
                val f = if (n <= 1) 0f else i.toFloat() / (n - 1)
                coin(x0 + i, baseRow - (sin(f * PI) * 2f).roundToInt())
            }
        }

        fun plat(x: Int, w: Int, row: Int) { for (i in 0 until w) lv.set(x + i, row, T.PLATFORM) }

        fun spawn(type: Int, x: Int, y: Int) { lv.ents.add(Spawn(type, x, y)) }

        fun gap(x: Int, n: Int): Int {
            if (theme == 3) for (cx in x until x + n) { lv.set(cx, 15, T.LAVA); lv.set(cx, 16, T.LAVA) }
            coinArc(x, n, gy - 3)
            return x + n
        }

        fun platformPit(x: Int, n: Int): Int {
            gap(x, n)
            when (n) {
                5 -> { plat(x + 1, 3, gy - 1); coin(x + 2, gy - 2) }
                6 -> { plat(x + 1, 2, gy - 1); plat(x + 4, 2, gy - 2); coin(x + 1, gy - 2); coin(x + 4, gy - 3) }
                else -> { plat(x + 1, 2, gy - 1); plat(x + 4, 2, gy - 1); coin(x + 1, gy - 2); coin(x + 4, gy - 2) }
            }
            return x + n
        }

        fun stairs(x: Int, h: Int): Int {
            for (i in 0 until h) for (dx in 0..1) for (y in gy - 1 - i until gy) lv.set(x + 2 * i + dx, y, T.GROUND)
            for (dx in 0..1) for (y in gy - h until gy) lv.set(x + 2 * h + dx, y, T.GROUND)
            for (i in h - 1 downTo 0) for (dx in 0..1) for (y in gy - 1 - i until gy) lv.set(x + 2 * h + 2 + 2 * (h - 1 - i) + dx, y, T.GROUND)
            ground(x, x + 4 * h + 2)
            coinArc(x + 2 * h - 1, 4, gy - h - 2)
            return x + 4 * h + 2
        }

        fun pipe(x: Int, h: Int): Int {
            for (dx in 0..1) for (y in gy - h until gy) lv.set(x + dx, y, T.PIPE)
            return x + 2
        }

        fun blocks(x: Int, len: Int): Int {
            ground(x, x + len)
            val power = 1 + 2 * rnd.nextInt((len - 1) / 2)
            for (i in 0 until len) {
                val id = if (i == power) T.QPOWER else if (i % 2 == 0) T.BRICK else T.QCOIN
                lv.set(x + i, gy - 4, id)
                if (i % 2 == 1) coin(x + i, gy - 6)
            }
            return x + len
        }

        fun spikes(x: Int, n: Int): Int {
            flat(x, n + 6)
            for (i in 0 until n) lv.set(x + 3 + i, gy - 1, T.SPIKE)
            coinArc(x + 2, n + 2, gy - 3)
            return x + n + 6
        }

        fun spring(x: Int, withSeed: Boolean): Int {
            flat(x, 13)
            lv.set(x + 3, gy - 1, T.SPRING)
            plat(x + 5, 6, gy - 6)
            for (i in 0 until 4) coin(x + 6 + i, gy - 7)
            if (withSeed) lv.set(x + 8, gy - 8, T.SEED)
            return x + 13
        }

        fun seedNook(x: Int): Int {
            flat(x, 14)
            plat(x + 1, 3, gy - 2)
            plat(x + 5, 3, gy - 4)
            plat(x + 9, 3, gy - 6)
            coin(x + 2, gy - 3); coin(x + 6, gy - 5)
            lv.set(x + 10, gy - 7, T.SEED)
            return x + 14
        }

        fun checkpoint(x: Int): Int {
            flat(x, 6)
            spawn(K.CHECK, x + 3, gy - 1)
            return x + 6
        }

        private fun pickKind(): Int {
            val bag = when (theme) {
                0 -> intArrayOf(K.SLIME, K.SLIME, K.SNAIL, K.FROG)
                1 -> intArrayOf(K.SLIME, K.HEDGE, K.SNAIL, K.BAT)
                2 -> intArrayOf(K.FROG, K.SNAIL, K.HEDGE, K.BAT)
                else -> intArrayOf(K.HEDGE, K.BAT, K.FROG, K.SLIME)
            }
            return bag[rnd.nextInt(bag.size)]
        }

        fun enemies(x: Int): Int {
            val n = minOf(4, 1 + (d * 2.5f).toInt() + rnd.nextInt(2))
            val len = 8 + n * 4
            flat(x, len)
            for (i in 0 until n) {
                val kind = pickKind()
                val ex = x + 5 + i * 4
                if (kind == K.BAT) spawn(kind, ex, gy - 5) else spawn(kind, ex, gy - 1)
            }
            return x + len
        }

        fun chunk(x: Int): Int {
            val w = when (theme) {
                0 -> intArrayOf(2, 3, 2, 4, 3, 2, if (d > 0.1f) 1 else 0, 5, 1)
                1 -> intArrayOf(1, 3, 2, 3, 2, 2, 3, 5, 1)
                2 -> intArrayOf(1, 3, 3, 3, 2, 2, 2, 5, 2)
                else -> intArrayOf(1, 2, 4, 2, 2, 2, 3, 5, 1)
            }
            var total = 0
            for (v in w) total += v
            var r = rnd.nextInt(total)
            var k = 0
            while (r >= w[k]) { r -= w[k]; k++ }
            var nx = x
            when (k) {
                0 -> nx = flat(nx, 4 + rnd.nextInt(5))
                1 -> {
                    nx = flat(nx, 3)
                    nx = gap(nx, 2 + rnd.nextInt(2 + (d * 2f).toInt()))
                    nx = flat(nx, 3)
                }
                2 -> {
                    nx = flat(nx, 3)
                    nx = platformPit(nx, 5 + rnd.nextInt(if (d > 0.5f) 3 else 2))
                    nx = flat(nx, 3)
                }
                3 -> { nx = blocks(nx, 5 + rnd.nextInt(5)); nx = flat(nx, 2) }
                4 -> {
                    nx = flat(nx, 4)
                    nx = pipe(nx, 2 + rnd.nextInt(2))
                    nx = flat(nx, 3 + rnd.nextInt(3))
                    if (d > 0.3f && rnd.nextBoolean()) { nx = pipe(nx, 3); nx = flat(nx, 4) }
                }
                5 -> { nx = flat(nx, 2); nx = stairs(nx, 2 + rnd.nextInt(2)); nx = flat(nx, 3) }
                6 -> {
                    nx = flat(nx, 2)
                    nx = spikes(nx, 1 + rnd.nextInt(2 + (d * 2f).toInt()))
                    nx = flat(nx, 2)
                }
                7 -> { nx = flat(nx, 2); nx = enemies(nx); nx = flat(nx, 2) }
                else -> { nx = flat(nx, 2); nx = spring(nx, false); nx = flat(nx, 2) }
            }
            return nx
        }
    }

    private fun build(index: Int): Level {
        val wo = index / 5
        val k = index % 5
        val boss = k == 4
        val theme = wo
        val d = index / 19f
        val rnd = Random(1000 + index * 77)
        val target = if (boss) 80 + wo * 6 else 120 + k * 14 + wo * 12
        val lv = Level(target + 70)
        val b = B(lv, rnd, theme, d)
        lv.name = Worlds.levelNames[index]
        lv.theme = theme
        lv.wonder = (index + wo) % 4 + 1
        lv.time = if (boss) 240 else 300 + wo * 30
        lv.bossHp = if (boss) intArrayOf(3, 3, 4, 5)[wo] else 0

        var x = b.flat(0, 12)
        b.spawn(K.START, 3, b.gy - 1)
        val endX = if (boss) target - 40 else target
        val seedAt = (endX * (0.30f + rnd.nextFloat() * 0.25f)).toInt()
        val cpAt = (endX * 0.6f).toInt()
        var seedDone = false
        var cpDone = false
        val springSeed = rnd.nextInt(3) == 0
        while (x < endX) {
            if (!seedDone && x >= seedAt) {
                x = if (springSeed) b.spring(x, true) else b.seedNook(x)
                x = b.flat(x, 4)
                seedDone = true
                continue
            }
            if (!cpDone && x >= cpAt) { x = b.checkpoint(x); cpDone = true; continue }
            x = b.chunk(x)
        }
        if (!seedDone) { x = b.seedNook(x); x = b.flat(x, 3) }

        val finalW: Int
        if (boss) {
            x = b.checkpoint(x)
            x = b.flat(x, 6)
            val a = x
            b.ground(a, a + 34)
            for (dx in 0..1) for (y in 2 until b.gy) lv.set(a + 32 + dx, y, T.STONE)
            b.plat(a + 6, 3, b.gy - 4)
            b.plat(a + 18, 3, b.gy - 4)
            b.spawn(K.BOSS, a + 24, b.gy - 1)
            finalW = a + 34
        } else {
            x = b.flat(x, 4)
            for (i in 0 until 4) for (y in b.gy - 1 - i until b.gy) lv.set(x + i, y, T.STONE)
            b.ground(x, x + 16)
            b.spawn(K.GOAL, x + 10, b.gy - 1)
            finalW = x + 16
        }
        lv.resize(finalW)
        return lv
    }
}

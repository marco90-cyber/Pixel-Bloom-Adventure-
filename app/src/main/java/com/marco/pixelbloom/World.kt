package com.marco.pixelbloom

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Tipi di entita' dinamiche oltre ai nemici (K.*). */
object En {
    const val BULB = 30
    const val FLOWER = 31
    const val SHOT = 32
    const val COINFALL = 33
    const val GEM = 34
}

class Ent(@JvmField var type: Int, @JvmField var x: Float, @JvmField var y: Float, @JvmField var w: Float, @JvmField var h: Float) {
    @JvmField var vx = 0f
    @JvmField var vy = 0f
    @JvmField var dir = -1
    @JvmField var state = 0
    @JvmField var t = 0
    @JvmField var hp = 1
    @JvmField var onGround = false
    @JvmField var ox = 0f
    @JvmField var oy = 0f
    @JvmField var inv = 0
    @JvmField var phase = 0
}

class Part(
    @JvmField var x: Float, @JvmField var y: Float, @JvmField var vx: Float, @JvmField var vy: Float,
    @JvmField var life: Int, @JvmField var color: Int, @JvmField var size: Int,
    @JvmField var grav: Float = 0.2f, @JvmField var kind: Int = 0
)

class World(
    val level: Level,
    val hero: Int,
    val snd: Sound?,
    startPos: FloatArray? = null,
    seedKeep: Boolean = false
) {
    val W = level.w
    val H = level.h
    val tiles: IntArray = level.t.copyOf()
    val ents = ArrayList<Ent>()
    val parts = ArrayList<Part>()
    val bump = HashMap<Int, Int>()
    val springT = HashMap<Int, Int>()
    private val rnd = Random(12345)

    // giocatore
    val p = Ent(0, 0f, 0f, 12f, 22f)
    var facing = 1
    var onGround = false
    private var coyote = 0
    private var jumpBuf = 0
    private var usedDouble = false
    var gliding = false
    var pounding = false
    var hp = 1
    var hasFlower = false
    var inv = 0
    var state = 0          // 0 gioco, 1 morto, 2 scivola sul palo, 3 festeggia, 4 finito
    var stateT = 0
    var walkAnim = 0f
    private var dropT = 0
    var skid = false
    var fell = false

    // stato del livello
    var camX = 0f
    var coins = 0
    var seedGot = false
    var wonderT = 0
    var frame = 0
    var timeLeft = level.time * 60
    var shake = 0
    var flash = 0
    var banner = ""
    var bannerT = 0
    var finished = false
    var failed = false
    var bossMax = 0
    var cpX = -1f
    var cpY = -1f
    val checks = ArrayList<IntArray>()
    val checkOn = ArrayList<Boolean>()
    var goalTx = -1
    var goalTy = -1
    val levelMusic: Int

    init {
        var sx = 3; var sy = 12
        var hasBoss = false
        for (s in level.ents) {
            when (s.type) {
                K.START -> { sx = s.x; sy = s.y }
                K.GOAL -> { goalTx = s.x; goalTy = s.y }
                K.CHECK -> { checks.add(intArrayOf(s.x, s.y)); checkOn.add(false) }
                K.SLIME -> addEnemy(s, 14f, 12f)
                K.SNAIL -> addEnemy(s, 14f, 13f)
                K.HEDGE -> addEnemy(s, 14f, 12f)
                K.BAT -> { val e = addEnemy(s, 12f, 10f); e.ox = e.x; e.oy = e.y - 8f; e.t = (s.x * 13) % 100 }
                K.FROG -> { val e = addEnemy(s, 14f, 13f); e.t = (s.x * 7) % 60 }
                K.BOSS -> {
                    hasBoss = true
                    val e = addEnemy(s, 32f, 30f)
                    e.hp = if (level.bossHp > 0) level.bossHp else 3
                    bossMax = e.hp
                }
            }
        }
        levelMusic = if (hasBoss) Mus.BOSS else Mus.W1 + level.theme.coerceIn(0, 3)
        if (seedKeep) {
            seedGot = true
            for (i in tiles.indices) if (tiles[i] == T.SEED) tiles[i] = T.EMPTY
        }
        if (startPos != null) { p.x = startPos[0]; p.y = startPos[1] }
        else { p.x = sx * TS + 2f; p.y = (sy + 1) * TS - p.h }
        camX = (p.x - VW * 0.4f).coerceIn(0f, max(0f, W * TS - VW.toFloat()))
        snd?.music(levelMusic)
    }

    private fun addEnemy(s: Spawn, w: Float, h: Float): Ent {
        val e = Ent(s.type, s.x * TS + (TS - w) / 2f, (s.y + 1) * TS - h, w, h)
        e.dir = -1
        ents.add(e)
        return e
    }

    // ---------- tile e collisioni ----------
    fun tile(tx: Int, ty: Int): Int {
        if (tx < 0 || tx >= W) return T.STONE
        if (ty < 0 || ty >= H) return T.EMPTY
        return tiles[ty * W + tx]
    }

    private fun solidAt(tx: Int, ty: Int) = T.solid(tile(tx, ty))

    private fun moveX(e: Ent, dx: Float): Boolean {
        if (dx == 0f) return false
        e.x += dx
        val top = floor(e.y / TS).toInt()
        val bot = floor((e.y + e.h - 0.01f) / TS).toInt()
        if (dx > 0f) {
            val tx = floor((e.x + e.w - 0.001f) / TS).toInt()
            for (ty in top..bot) if (solidAt(tx, ty)) { e.x = tx * TS - e.w; return true }
        } else {
            val tx = floor(e.x / TS).toInt()
            for (ty in top..bot) if (solidAt(tx, ty)) { e.x = (tx + 1) * TS.toFloat(); return true }
        }
        return false
    }

    private var bumpTx = 0
    private var bumpTy = 0

    /** Ritorna 1 se atterra, -1 se batte la testa, 0 altrimenti. */
    private fun moveY(e: Ent, dy: Float, drop: Boolean): Int {
        val prevBottom = e.y + e.h
        e.y += dy
        val l = floor(e.x / TS).toInt()
        val r = floor((e.x + e.w - 0.01f) / TS).toInt()
        if (dy > 0f) {
            val ty = floor((e.y + e.h - 0.01f) / TS).toInt()
            for (tx in l..r) {
                val id = tile(tx, ty)
                if (T.solid(id) || (id == T.PLATFORM && !drop && prevBottom <= ty * TS + 0.5f)) {
                    e.y = ty * TS - e.h
                    return 1
                }
            }
        } else if (dy < 0f) {
            val ty = floor(e.y / TS).toInt()
            val cx = floor((e.x + e.w / 2f) / TS).toInt()
            var hitX = -1
            if (solidAt(cx, ty)) hitX = cx else for (tx in l..r) if (solidAt(tx, ty)) { hitX = tx; break }
            if (hitX >= 0) { e.y = (ty + 1) * TS.toFloat(); bumpTx = hitX; bumpTy = ty; return -1 }
        }
        return 0
    }

    private fun standingOn(id: Int): Boolean =
        tile(floor((p.x + p.w / 2f) / TS).toInt(), floor((p.y + p.h + 0.5f) / TS).toInt()) == id

    private fun overlap(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float) =
        ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by

    val giant get() = wonderT > 0 && level.wonder == 2
    val luna get() = wonderT > 0 && level.wonder == 1
    val turbo get() = wonderT > 0 && level.wonder == 3
    val rain get() = wonderT > 0 && level.wonder == 4

    // ---------- aggiornamento ----------
    fun update(inp: Input) {
        frame++
        if (shake > 0) shake--
        if (flash > 0) flash--
        if (bannerT > 0) bannerT--
        when (state) {
            0 -> {
                updatePlayer(inp)
                updateEnts()
                updateWonder()
                if (state == 0) {
                    timeLeft--
                    if (timeLeft <= 0) { banner = "TEMPO SCADUTO"; bannerT = 90; die(false) }
                }
            }
            1 -> {
                stateT++
                if (fell) { if (stateT > 60) failed = true }
                else {
                    p.vy += 0.3f; if (p.vy > 6f) p.vy = 6f
                    p.y += p.vy
                    if (stateT > 110) failed = true
                }
            }
            2 -> { // scivola sul palo
                stateT++
                p.vx = 0f
                val r = moveY(p, 2f, true)
                if (r == 1) { state = 3; stateT = 0; facing = 1 }
            }
            3 -> { // festeggia e cammina
                stateT++
                facing = 1
                p.vx = 1.2f
                if (moveX(p, p.vx)) p.vx = 0f
                p.vy += 0.26f; if (p.vy > 5f) p.vy = 5f
                val r = moveY(p, p.vy, true)
                if (r == 1) p.vy = 0f
                walkAnim += 0.2f
                if (stateT > 80) { state = 4; finished = true }
            }
        }
        updateParts()
        updateCam()
    }

    private fun updateCam() {
        val maxCam = W * TS - VW.toFloat()
        if (maxCam <= 0f) { camX = maxCam / 2f; return }
        val target = p.x + p.w / 2f - VW * 0.45f + facing * 28f
        camX += (target - camX) * 0.12f
        camX = camX.coerceIn(0f, maxCam)
    }

    private fun updateWonder() {
        if (wonderT > 0) {
            wonderT--
            if (rain && frame % 5 == 0) spawnCoinFall()
            if (wonderT == 0) { snd?.music(levelMusic); banner = ""; bannerT = 0 }
        }
    }

    private fun startWonder() {
        seedGot = true
        wonderT = 720
        flash = 24
        banner = Worlds.wonderNames[level.wonder.coerceIn(1, 4)]
        bannerT = 140
        snd?.sfx(Sfx.WONDER)
        snd?.music(Mus.WONDER)
        puff(p.x + p.w / 2f, p.y + 8f, 24, 0xFFFF80FF.toInt(), 2.4f)
    }

    private fun spawnCoinFall() {
        val e = Ent(En.COINFALL, camX + rnd.nextFloat() * (VW - 12f), -10f, 10f, 10f)
        e.vy = 0.5f
        ents.add(e)
    }

    // ---------- giocatore ----------
    private fun updatePlayer(inp: Input) {
        val e = p
        val dirIn = (if (inp.h(Btn.RIGHT)) 1 else 0) - (if (inp.h(Btn.LEFT)) 1 else 0)
        val run = inp.h(Btn.B)
        var maxS = if (run) (if (hero == 0) 2.7f else 2.4f) else 1.5f
        if (turbo) maxS *= 1.4f
        if (giant) maxS *= 1.1f
        val onIce = onGround && standingOn(T.ICE)
        val acc = if (onGround) (if (onIce) 0.04f else 0.14f) else 0.10f
        val fr = if (onGround) (if (onIce) 0.02f else 0.16f) else 0.02f

        skid = false
        if (pounding) {
            e.vx = 0f
        } else if (dirIn != 0) {
            facing = dirIn
            val opposing = e.vx * dirIn < 0f
            val a = if (opposing) acc * (if (onIce) 1.5f else 2.5f) else acc
            if (opposing && onGround && abs(e.vx) > 1.4f) skid = true
            if (opposing) {
                e.vx += dirIn * a
            } else if (abs(e.vx) < maxS) {
                e.vx += dirIn * a
                if (abs(e.vx) > maxS) e.vx = dirIn * maxS
            } else if (abs(e.vx) > maxS) {
                // ha rilasciato la corsa: rallenta fino alla velocita' di camminata
                val slow = if (onGround) fr else 0.03f
                e.vx = if (e.vx > 0f) max(maxS, e.vx - slow) else min(-maxS, e.vx + slow)
            }
        } else {
            if (e.vx > 0f) e.vx = max(0f, e.vx - fr) else if (e.vx < 0f) e.vx = min(0f, e.vx + fr)
        }

        // salto
        if (inp.p(Btn.A)) jumpBuf = 7 else if (jumpBuf > 0) jumpBuf--
        if (onGround) { coyote = 6; usedDouble = false } else if (coyote > 0) coyote--
        if (jumpBuf > 0 && coyote > 0) {
            e.vy = when {
                luna -> -4.1f
                turbo || giant -> -5.7f
                else -> -5.3f - 0.35f * min(1f, abs(e.vx) / 2.7f)
            }
            onGround = false; coyote = 0; jumpBuf = 0
            puff(e.x + e.w / 2f, e.y + e.h, 4, 0xFFFFFFFF.toInt(), 0.8f)
            snd?.sfx(Sfx.JUMP)
        } else if (jumpBuf > 0 && !onGround && hero == 0 && !usedDouble) {
            e.vy = if (luna) -3.6f else -4.7f
            usedDouble = true; jumpBuf = 0
            puff(e.x + e.w / 2f, e.y + e.h, 8, 0xFFFFE08A.toInt(), 1.2f)
            snd?.sfx(Sfx.DJUMP)
        }
        val cut = if (luna) -1.6f else -2.2f
        if (!inp.h(Btn.A) && e.vy < cut && !pounding) e.vy = cut

        // abilita' di Toki
        gliding = false
        if (hero == 1 && !onGround) {
            if (inp.p(Btn.DOWN) && !pounding && e.vy > -3f) { pounding = true; e.vy = 7.5f; snd?.sfx(Sfx.POUND) }
        }

        // gravita'
        val g = if (luna) 0.11f else 0.26f
        val maxFall = if (luna) 2.2f else 5.4f
        if (pounding) {
            e.vy = 7.5f
        } else {
            e.vy += g
            if (hero == 1 && !onGround && e.vy > 0f && inp.h(Btn.A)) { gliding = true; if (e.vy > 0.9f) e.vy = max(0.9f, e.vy - 0.6f) }
            if (e.vy > maxFall) e.vy = maxFall
        }

        // discesa dalle piattaforme
        if (dropT > 0) dropT--
        if (onGround && inp.h(Btn.DOWN) && tile(floor((e.x + e.w / 2f) / TS).toInt(), floor((e.y + e.h + 0.5f) / TS).toInt()) == T.PLATFORM) dropT = 10

        // movimento
        if (moveX(e, e.vx)) e.vx = 0f
        val r = moveY(e, e.vy, dropT > 0)
        if (r == 1) {
            val wasAir = !onGround
            onGround = true
            if (pounding) poundImpact()
            pounding = false
            e.vy = 0f
            if (wasAir) puff(e.x + e.w / 2f, e.y + e.h, 3, 0xFFFFFFFF.toInt(), 0.6f)
            val tl = tile(floor((e.x + e.w / 2f) / TS).toInt(), floor((e.y + e.h + 0.5f) / TS).toInt())
            if (tl == T.SPRING) {
                e.vy = if (inp.h(Btn.A)) -8.8f else -7f
                onGround = false
                usedDouble = false
                springT[floor((e.y + e.h + 0.5f) / TS).toInt() * W + floor((e.x + e.w / 2f) / TS).toInt()] = 8
                snd?.sfx(Sfx.SPRING)
            }
        } else {
            onGround = false
            if (r == -1) {
                e.vy = 0.6f
                bumpBlock(bumpTx, bumpTy, false)
            }
        }
        if (e.y > H * TS + 24f) die(true)

        // animazione
        if (onGround && abs(e.vx) > 0.2f) walkAnim += abs(e.vx) * 0.09f else if (onGround) walkAnim = 0f

        // seed (Fiore)
        if (inp.p(Btn.B) && hasFlower && countShots() < 2) {
            val s = Ent(En.SHOT, e.x + e.w / 2f + facing * 6f - 3f, e.y + 8f, 6f, 6f)
            s.vx = facing * 3.4f; s.vy = -0.4f; s.dir = facing
            ents.add(s)
            snd?.sfx(Sfx.SHOOT)
        }

        if (inv > 0) inv--
        tileInteractions()
        checkpoints()
        checkGoal()
    }

    private fun countShots(): Int { var n = 0; for (e in ents) if (e.type == En.SHOT) n++; return n }

    private fun tileInteractions() {
        if (state != 0) return
        val x0 = floor((p.x + 1f) / TS).toInt(); val x1 = floor((p.x + p.w - 1.01f) / TS).toInt()
        val y0 = floor((p.y + 1f) / TS).toInt(); val y1 = floor((p.y + p.h - 1.01f) / TS).toInt()
        for (ty in y0..y1) for (tx in x0..x1) {
            if (tx < 0 || tx >= W || ty < 0 || ty >= H) continue
            val idx = ty * W + tx
            when (tiles[idx]) {
                T.COIN -> { tiles[idx] = T.EMPTY; coins++; snd?.sfx(Sfx.COIN); sparkle(tx * TS + 8f, ty * TS + 8f) }
                T.SEED -> { tiles[idx] = T.EMPTY; startWonder() }
                T.SPIKE -> if (!giant && overlap(p.x, p.y, p.w, p.h, tx * TS + 2f, ty * TS + 8f, 12f, 8f)) hurt()
                T.LAVA -> if (p.y + p.h > ty * TS + 5f) { fell = false; die(false) }
            }
        }
    }

    private fun checkpoints() {
        for (i in checks.indices) {
            if (checkOn[i]) continue
            val c = checks[i]
            if (overlap(p.x, p.y, p.w, p.h, c[0] * TS.toFloat(), (c[1] - 1) * TS.toFloat(), 16f, 32f)) {
                checkOn[i] = true
                cpX = c[0] * TS + 2f
                cpY = (c[1] + 1) * TS - p.h
                banner = "PUNTO DI CONTROLLO"; bannerT = 70
                snd?.sfx(Sfx.CHECK)
                sparkle(c[0] * TS + 8f, c[1] * TS.toFloat())
            }
        }
    }

    private fun checkGoal() {
        if (goalTx < 0 || state != 0) return
        val base = (goalTy + 1) * TS.toFloat()
        val top = base - POLE_H * TS
        if (overlap(p.x, p.y, p.w, p.h, goalTx * TS + 5f, top, 6f, base - top)) {
            state = 2; stateT = 0
            p.x = goalTx * TS + 8f - p.w / 2f
            p.vx = 0f; p.vy = 0f
            facing = 1
            snd?.sfx(Sfx.WIN); snd?.music(Mus.NONE)
        }
    }

    fun hurt() {
        if (inv > 0 || giant || state != 0) return
        if (hp > 1) {
            hp = 1; hasFlower = false; inv = 110
            p.vy = -3.2f; onGround = false
            snd?.sfx(Sfx.HURT)
            puff(p.x + p.w / 2f, p.y + 10f, 10, 0xFFFFD060.toInt(), 1.6f)
        } else die(false)
    }

    fun die(fellDown: Boolean) {
        if (state != 0) return
        state = 1; stateT = 0; fell = fellDown
        p.vy = -6f; p.vx = 0f
        pounding = false
        snd?.sfx(Sfx.DIE); snd?.music(Mus.NONE)
    }

    private fun poundImpact() {
        shake = 10
        snd?.sfx(Sfx.BUMP)
        puff(p.x + p.w / 2f, p.y + p.h, 12, 0xFFE8E8E8.toInt(), 1.8f)
        val ty = floor((p.y + p.h + 1f) / TS).toInt()
        val l = floor(p.x / TS).toInt(); val r = floor((p.x + p.w - 0.01f) / TS).toInt()
        for (tx in l..r) bumpBlock(tx, ty, true)
        for (e in ents) {
            if (e.type < 10 || e.type > 15 || e.state >= 8) continue
            if (abs((e.x + e.w / 2f) - (p.x + p.w / 2f)) < 34f && abs((e.y + e.h) - (p.y + p.h)) < 20f) {
                if (e.type == K.BOSS) bossHit(e, 1) else killEnemy(e)
            }
        }
    }

    // ---------- blocchi ----------
    private fun bumpBlock(tx: Int, ty: Int, fromAbove: Boolean) {
        if (tx < 0 || tx >= W || ty < 0 || ty >= H) return
        val idx = ty * W + tx
        when (tiles[idx]) {
            T.QCOIN -> {
                tiles[idx] = T.USED; coins++; bump[idx] = 8
                snd?.sfx(Sfx.COIN)
                parts.add(Part(tx * TS + 8f, ty * TS.toFloat(), 0f, -4.2f, 26, 0xFFFFD23C.toInt(), 5, 0.3f, 1))
                if (!fromAbove) knockOnTop(tx, ty)
            }
            T.QPOWER -> {
                tiles[idx] = T.USED; bump[idx] = 8
                snd?.sfx(Sfx.ITEM)
                val bulb = hp < 2
                val e = Ent(if (bulb) En.BULB else En.FLOWER, tx * TS + 1f, ty * TS + 2f, 14f, 14f)
                e.state = 0; e.t = 0
                ents.add(e)
                if (!fromAbove) knockOnTop(tx, ty)
            }
            T.BRICK -> {
                if (fromAbove || hp >= 2 || hero == 1) breakBrick(tx, ty)
                else { bump[idx] = 8; snd?.sfx(Sfx.BUMP); knockOnTop(tx, ty) }
            }
        }
    }

    private fun breakBrick(tx: Int, ty: Int) {
        tiles[ty * W + tx] = T.EMPTY
        snd?.sfx(Sfx.BREAK)
        val d = Art.themes[level.theme.coerceIn(0, 3)]
        for (i in 0 until 4) {
            val vx = (if (i % 2 == 0) -1.4f else 1.4f) * (0.7f + rnd.nextFloat() * 0.6f)
            val vy = (if (i < 2) -4.4f else -3f) * (0.8f + rnd.nextFloat() * 0.4f)
            parts.add(Part(tx * TS + 4f + (i % 2) * 8f, ty * TS + 4f + (i / 2) * 8f, vx, vy, 60, d.brickA, 5, 0.3f, 3))
        }
        knockOnTop(tx, ty)
    }

    private fun knockOnTop(tx: Int, ty: Int) {
        for (e in ents) {
            if (e.type < 10 || e.type > 14 || e.state >= 8) continue
            if (abs((e.y + e.h) - ty * TS) < 4f && e.x + e.w > tx * TS - 2f && e.x < tx * TS + 18f) killEnemy(e)
        }
    }

    // ---------- nemici ----------
    fun killEnemy(e: Ent) {
        if (e.type == K.BOSS) return
        e.state = 8; e.t = 0
        e.vy = -3.2f
        e.vx = if (p.x < e.x) 1f else -1f
        snd?.sfx(Sfx.STOMP)
        puff(e.x + e.w / 2f, e.y + e.h / 2f, 5, 0xFFFFFFFF.toInt(), 1f)
    }

    private fun stomp(e: Ent) {
        val bounceHold = false
        when (e.type) {
            K.SNAIL -> {
                if (e.state == 0) { e.state = 1; e.t = 0; e.vx = 0f }
                else if (e.state == 2) { e.state = 1; e.t = 0; e.vx = 0f }
                else kickShell(e)
            }
            K.BOSS -> bossHit(e, 1)
            K.HEDGE -> killEnemy(e)
            else -> { e.state = 9; e.t = 0; e.vx = 0f }
        }
        if (e.type != K.BOSS) snd?.sfx(Sfx.STOMP)
        p.vy = -4.4f
        if (bounceHold) p.vy = -5.6f
        usedDouble = false
        pounding = false
        puff(e.x + e.w / 2f, e.y, 4, 0xFFFFFFFF.toInt(), 1f)
    }

    private fun kickShell(e: Ent) {
        e.state = 2
        e.vx = (if (p.x + p.w / 2f < e.x + e.w / 2f) 1f else -1f) * 3.6f
        e.inv = 12
        snd?.sfx(Sfx.STOMP)
    }

    fun bossHit(e: Ent, n: Int) {
        if (e.inv > 0 || e.state >= 8) return
        e.hp -= n
        e.inv = 80
        flash = 8
        shake = 12
        snd?.sfx(Sfx.BOSSHIT)
        puff(e.x + e.w / 2f, e.y + e.h / 2f, 14, 0xFFFFE066.toInt(), 2f)
        if (e.hp <= 0) {
            e.state = 10; e.t = 0; e.vx = 0f; e.vy = 0f
            snd?.music(Mus.NONE)
        } else {
            e.phase = 0; e.t = 0; e.vx = 0f
        }
    }

    private fun contact(e: Ent) {
        if (state != 0) return
        val gi = giant
        val m = if (gi) 5f else 0f
        if (!overlap(p.x - m, p.y - m, p.w + 2 * m, p.h + 2 * m, e.x, e.y, e.w, e.h)) return
        if (e.type == K.SNAIL && e.state == 2 && e.inv > 0) return
        if (e.type == K.SNAIL && e.state == 1) { kickShell(e); return }
        val above = p.vy > 0.5f && (p.y + p.h) - e.y < (if (e.type == K.BOSS) 14f else 11f)
        val stompable = e.type != K.HEDGE || pounding
        if (above && stompable) { stomp(e); return }
        if (gi) { if (e.type == K.BOSS) bossHit(e, 1) else killEnemy(e); return }
        hurt()
    }

    private fun updateEnemy(e: Ent, slow: Boolean, camL: Float, camR: Float): Boolean {
        if (e.state == 8) {
            e.vy += 0.28f; e.y += e.vy; e.x += e.vx; e.t++
            return e.y > H * TS + 40f || e.t > 140
        }
        if (e.state == 9) { e.t++; return e.t > 24 }
        if (e.type == K.BOSS && e.state == 10) return updateBossDeath(e)
        if (e.x < camL || e.x > camR) return false
        if (rain) return false
        if (slow) return false
        e.t++
        if (e.inv > 0) e.inv--
        when (e.type) {
            K.SLIME -> { e.vx = e.dir * 0.45f; walker(e, false) }
            K.HEDGE -> { e.vx = e.dir * 0.55f; walker(e, true) }
            K.SNAIL -> {
                when (e.state) {
                    0 -> { e.vx = e.dir * 0.4f; walker(e, false) }
                    1 -> { e.vx = 0f; walker(e, false); if (e.t > 380) { e.state = 0; e.t = 0 } }
                    2 -> {
                        if (walkerRaw(e)) { e.vx = -e.vx; e.dir = if (e.vx > 0) 1 else -1; snd?.sfx(Sfx.BUMP) }
                        for (o in ents) {
                            if (o === e || o.type < 10 || o.type > 14 || o.state >= 8) continue
                            if (overlap(e.x, e.y, e.w, e.h, o.x, o.y, o.w, o.h)) killEnemy(o)
                        }
                    }
                }
            }
            K.FROG -> {
                e.vx = if (e.onGround) 0f else e.vx
                e.vy += 0.26f; if (e.vy > 5f) e.vy = 5f
                if (moveX(e, e.vx)) e.vx = 0f
                val r = moveY(e, e.vy, false)
                if (r == 1) { e.vy = 0f; e.onGround = true } else e.onGround = false
                if (e.onGround && e.t > 75) {
                    e.t = 0
                    e.dir = if (p.x < e.x) -1 else 1
                    e.vy = -4.4f; e.vx = e.dir * 1.15f; e.onGround = false
                }
            }
            K.BAT -> {
                val tt = e.t * 0.022f
                val nx = e.ox + sin(tt) * 70f
                e.dir = if (cos(tt) > 0f) 1 else -1
                e.x = nx
                e.y = e.oy + sin(e.t * 0.07f) * 14f
            }
            K.BOSS -> updateBoss(e)
        }
        if (e.y > H * TS + 40f) return true
        contact(e)
        return false
    }

    /** Camminata semplice con inversione contro i muri (e sui bordi se `ledge`). Ritorna true se ha urtato. */
    private fun walker(e: Ent, ledge: Boolean) {
        e.vy += 0.26f; if (e.vy > 5f) e.vy = 5f
        if (moveX(e, e.vx)) { e.dir = -e.dir; e.vx = -e.vx }
        val r = moveY(e, e.vy, false)
        if (r == 1) { e.vy = 0f; e.onGround = true } else e.onGround = false
        if (ledge && e.onGround) {
            val ax = if (e.dir > 0) e.x + e.w + 1f else e.x - 1f
            val id = tile(floor(ax / TS).toInt(), floor((e.y + e.h + 2f) / TS).toInt())
            if (!T.solid(id) && id != T.PLATFORM) { e.dir = -e.dir; e.vx = -e.vx; e.x += e.dir * 1.5f }
        }
    }

    private fun walkerRaw(e: Ent): Boolean {
        e.vy += 0.26f; if (e.vy > 5f) e.vy = 5f
        val blocked = moveX(e, e.vx)
        val r = moveY(e, e.vy, false)
        if (r == 1) { e.vy = 0f; e.onGround = true } else e.onGround = false
        return blocked
    }

    private fun updateBoss(e: Ent) {
        val angry = e.hp * 2 <= bossMax
        val toward = if (p.x + p.w / 2f < e.x + e.w / 2f) -1 else 1
        e.vy += 0.28f; if (e.vy > 6f) e.vy = 6f
        var blocked = false
        when (e.phase) {
            0 -> {
                e.dir = toward
                e.vx = e.dir * (if (angry) 0.85f else 0.55f)
                if (e.t > (if (angry) 60 else 90)) { e.phase = 1; e.t = 0 }
            }
            1 -> {
                e.vx = 0f
                if (e.t > 25) {
                    if (rnd.nextInt(2) == 0) { e.phase = 2; e.t = 0; e.vy = -6.6f; e.vx = toward * (if (angry) 2.1f else 1.6f); e.onGround = false }
                    else { e.phase = 3; e.t = 0; e.dir = toward }
                }
            }
            2 -> { /* in volo, atterra sotto */ }
            3 -> {
                e.vx = e.dir * (if (angry) 3.0f else 2.3f)
                if (e.t > 55) { e.phase = 0; e.t = 0 }
            }
        }
        if (moveX(e, e.vx)) {
            blocked = true
            if (e.phase == 3) { shake = 8; snd?.sfx(Sfx.BUMP); e.phase = 0; e.t = -30; e.vx = 0f }
            else e.vx = -e.vx
        }
        val r = moveY(e, e.vy, false)
        if (r == 1) {
            val wasAir = !e.onGround
            e.vy = 0f; e.onGround = true
            if (e.phase == 2 && e.t > 8) { e.phase = 0; e.t = 0; e.vx = 0f; shake = 10; snd?.sfx(Sfx.POUND); puff(e.x + e.w / 2f, e.y + e.h, 10, 0xFFE8E8E8.toInt(), 1.8f) }
            if (wasAir && e.phase != 2) e.vx = e.vx
        } else e.onGround = false
        if (blocked && e.phase == 0 && e.vx == 0f) e.vx = 0f
    }

    private fun updateBossDeath(e: Ent): Boolean {
        e.t++
        shake = 4
        if (e.t % 4 == 0) puff(e.x + rnd.nextFloat() * e.w, e.y + rnd.nextFloat() * e.h, 3, 0xFFFFE066.toInt(), 1.6f)
        if (e.t == 1 || e.t % 12 == 0) snd?.sfx(Sfx.BOSSHIT)
        if (e.t > 75) {
            val g = Ent(En.GEM, e.x + e.w / 2f - 7f, e.y + e.h - 14f, 14f, 14f)
            ents.add(g)
            puff(e.x + e.w / 2f, e.y + e.h / 2f, 30, 0xFFFFFFFF.toInt(), 3f)
            return true
        }
        return false
    }

    // ---------- altre entita' ----------
    private fun updateEnts() {
        val slow = turbo && (frame and 1) == 1
        val camL = camX - 96f
        val camR = camX + VW + 96f
        var i = 0
        while (i < ents.size) {
            val e = ents[i]
            val remove = when (e.type) {
                En.BULB, En.FLOWER -> updateItem(e)
                En.SHOT -> updateShot(e)
                En.COINFALL -> updateCoinFall(e)
                En.GEM -> updateGem(e)
                else -> updateEnemy(e, slow, camL, camR)
            }
            if (remove) ents.removeAt(i) else i++
            if (state != 0) break
        }
    }

    private fun updateItem(e: Ent): Boolean {
        if (e.state == 0) {
            e.t++
            e.y -= 0.5f
            if (e.t >= 32) { e.state = 1; e.vx = if (e.type == En.BULB) 0.7f else 0f }
            return false
        }
        if (e.type == En.BULB) {
            e.vy += 0.26f; if (e.vy > 5f) e.vy = 5f
            if (moveX(e, e.vx)) e.vx = -e.vx
            val r = moveY(e, e.vy, false)
            if (r == 1) { e.vy = 0f; e.onGround = true }
            if (e.y > H * TS + 30f) return true
        }
        if (state == 0 && overlap(p.x, p.y, p.w, p.h, e.x, e.y, e.w, e.h)) {
            if (e.type == En.FLOWER) hasFlower = true
            if (hp < 2) hp = 2 else coins += 5
            snd?.sfx(Sfx.POWER)
            flash = 8
            puff(e.x + 7f, e.y + 7f, 10, 0xFFFFF59D.toInt(), 1.6f)
            return true
        }
        return false
    }

    private fun updateShot(e: Ent): Boolean {
        e.t++
        e.vy += 0.25f
        if (moveX(e, e.vx)) { puff(e.x, e.y, 3, 0xFFFFB02E.toInt(), 1f); return true }
        val r = moveY(e, e.vy, false)
        if (r == 1) e.vy = -2.8f
        if (r == -1) return true
        if (e.t > 120 || e.y > H * TS + 20f) return true
        for (o in ents) {
            if (o.type < 10 || o.type > 15 || o.state >= 8) continue
            if (overlap(e.x, e.y, e.w, e.h, o.x, o.y, o.w, o.h)) {
                if (o.type == K.BOSS) bossHit(o, 1) else killEnemy(o)
                puff(e.x, e.y, 5, 0xFFFFB02E.toInt(), 1.4f)
                return true
            }
        }
        return false
    }

    private fun updateCoinFall(e: Ent): Boolean {
        e.vy = min(e.vy + 0.05f, 1.5f)
        e.y += e.vy
        e.t++
        if (state == 0 && overlap(p.x, p.y, p.w, p.h, e.x, e.y, e.w, e.h)) {
            coins++; snd?.sfx(Sfx.COIN); sparkle(e.x + 5f, e.y + 5f)
            return true
        }
        if (e.y > 0f && solidAt(floor((e.x + 5f) / TS).toInt(), floor((e.y + 10f) / TS).toInt())) return true
        return e.y > H * TS + 20f
    }

    private fun updateGem(e: Ent): Boolean {
        e.t++
        if (state == 0 && overlap(p.x, p.y, p.w, p.h, e.x, e.y, e.w, e.h)) {
            state = 3; stateT = 0
            p.vx = 0f
            snd?.sfx(Sfx.WIN)
            sparkle(e.x + 7f, e.y + 7f)
            return true
        }
        return false
    }

    // ---------- particelle ----------
    private fun updateParts() {
        var i = 0
        while (i < parts.size) {
            val q = parts[i]
            q.vy += q.grav
            q.x += q.vx; q.y += q.vy
            q.life--
            if (q.life <= 0) parts.removeAt(i) else i++
        }
        val it = bump.entries.iterator()
        while (it.hasNext()) { val en = it.next(); if (en.value <= 1) it.remove() else en.setValue(en.value - 1) }
        val it2 = springT.entries.iterator()
        while (it2.hasNext()) { val en = it2.next(); if (en.value <= 1) it2.remove() else en.setValue(en.value - 1) }
    }

    fun puff(x: Float, y: Float, n: Int, color: Int, speed: Float) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val s = speed * (0.4f + rnd.nextFloat() * 0.8f)
            parts.add(Part(x, y, cos(a) * s, sin(a) * s - 0.3f, 14 + rnd.nextInt(14), color, 2, 0.04f, 0))
        }
    }

    private fun sparkle(x: Float, y: Float) = puff(x, y, 6, 0xFFFFF59D.toInt(), 1.2f)

    /** Piazza il giocatore al checkpoint (se attivato) o all'inizio: usato dal Game quando si rigioca. */
    fun respawnPos(): FloatArray? = if (cpX >= 0f) floatArrayOf(cpX, cpY) else null

    val bossEnt: Ent? get() { for (e in ents) if (e.type == K.BOSS) return e; return null }
}

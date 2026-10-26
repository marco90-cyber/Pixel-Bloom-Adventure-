package com.marco.pixelbloom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Editor di livelli in stile "maker": matita, gomma, mano, annulla, prova. Tocco + tastiera/controller. */
class Editor(val game: Game, val lv: Level, var id: String?) {
    private val TB_H = 22
    private val PAL_Y = VH - 32
    private val CELL = 28

    // palette: 0..99 = tile, 100+ = entita'
    private val pal = intArrayOf(
        T.GROUND, T.BRICK, T.QCOIN, T.QPOWER, T.STONE, T.PIPE, T.PLATFORM, T.ICE, T.SPRING, T.SPIKE, T.LAVA, T.COIN, T.SEED,
        100 + K.START, 100 + K.GOAL, 100 + K.CHECK, 100 + K.SLIME, 100 + K.SNAIL, 100 + K.HEDGE, 100 + K.BAT, 100 + K.FROG, 100 + K.BOSS
    )
    private var sel = 0
    private var palScroll = 0

    private var tool = 0           // 0 matita, 1 gomma, 2 mano
    var testHero = 0
    private var camX = 0f
    private var tick = 0
    private var dirty = false

    // cursore (tastiera/controller)
    private var cx = 6
    private var cy = 11
    private var cursorMode = false
    private var holdL = 0; private var holdR = 0; private var holdU = 0; private var holdD = 0
    private var lastKX = -1; private var lastKY = -1

    // tocco
    private var ptr = -1
    private var ptrMode = 0        // 1 disegno, 2 pan, 3 palette, 4 scrollbar
    private var lastTx = -1
    private var lastTy = -1
    private var lastPx = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    // undo
    private class Snap(val t: IntArray, val ents: List<Spawn>)
    private val undo = ArrayList<Snap>()

    // menu
    private var menuOpen = false
    private var msel = 0
    private var confirmClear = false
    private var confirmExit = false

    // nome
    private var nameMode = false
    private var ksel = 0
    private val KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .-!?'"
    private val KCOLS = 11

    private val toolbar = arrayOf("PENNA", "GOMMA", "MANO", "UNDO", "PROVA", "MENU")
    private fun tbX(i: Int) = if (i == 5) VW - 44 else 4 + i * 40
    private fun tbW(i: Int) = if (i == 5) 40 else 36

    private fun maxCam() = max(0f, lv.w * TS - VW.toFloat())

    // ---------- modifica ----------
    private fun snap() {
        undo.add(Snap(lv.t.copyOf(), lv.ents.map { Spawn(it.type, it.x, it.y) }))
        if (undo.size > 40) undo.removeAt(0)
    }

    private fun doUndo() {
        if (undo.isEmpty()) { game.snd.sfx(Sfx.BUMP); return }
        val s = undo.removeAt(undo.size - 1)
        System.arraycopy(s.t, 0, lv.t, 0, lv.t.size)
        lv.ents.clear(); lv.ents.addAll(s.ents)
        dirty = true
        game.snd.sfx(Sfx.BACK)
    }

    private fun place(tx: Int, ty: Int, first: Boolean) {
        if (tx < 0 || ty < 0 || tx >= lv.w || ty >= lv.h) return
        val c = pal[sel]
        if (c >= 100) {
            if (!first) return
            val type = c - 100
            if (type == K.START || type == K.GOAL || type == K.BOSS) lv.ents.removeAll { it.type == type }
            lv.removeEntAt(tx, ty)
            lv.set(tx, ty, T.EMPTY)
            lv.ents.add(Spawn(type, tx, ty))
        } else {
            if (lv.get(tx, ty) == c && lv.entAt(tx, ty) == null) return
            if (T.solid(c) || c == T.SPIKE || c == T.LAVA) lv.removeEntAt(tx, ty)
            lv.set(tx, ty, c)
        }
        dirty = true
    }

    private fun erase(tx: Int, ty: Int) {
        if (tx < 0 || ty < 0 || tx >= lv.w || ty >= lv.h) return
        if (lv.entAt(tx, ty) != null) { lv.removeEntAt(tx, ty); dirty = true }
        else if (lv.get(tx, ty) != T.EMPTY) { lv.set(tx, ty, T.EMPTY); dirty = true }
    }

    private fun apply(tx: Int, ty: Int, first: Boolean, erasing: Boolean) {
        if (erasing) erase(tx, ty) else place(tx, ty, first)
    }

    private fun line(x0: Int, y0: Int, x1: Int, y1: Int, erasing: Boolean) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var guard = 0
        while (guard++ < 400) {
            apply(x, y, false, erasing)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
        }
    }

    // ---------- validazione, test, salvataggio ----------
    private fun validate(): String? {
        if (lv.findEnt(K.START) == null) return "MANCA IL PUNTO DI PARTENZA"
        if (lv.findEnt(K.GOAL) == null && lv.findEnt(K.BOSS) == null) return "MANCA IL TRAGUARDO"
        return null
    }

    private fun startTest() {
        val err = validate()
        if (err != null) { game.toast(err); game.snd.sfx(Sfx.BUMP); return }
        menuOpen = false
        if (lv.findEnt(K.BOSS) != null && lv.bossHp == 0) lv.bossHp = 3
        game.startTest(lv)
    }

    private fun save(): Boolean {
        val cur = id ?: game.newLevelId().also { id = it }
        game.saveLevel(cur, lv)
        dirty = false
        game.toast("LIVELLO SALVATO")
        game.snd.sfx(Sfx.CONFIRM)
        return true
    }

    private fun leave() { game.go(Scr.EDMENU) }

    fun resumeFromTest() {
        menuOpen = false; nameMode = false
        ptr = -1; ptrMode = 0
        game.input.releaseAll()
        game.snd.music(Mus.EDIT)
    }

    // ---------- tocco ----------
    fun touch(action: Int, id: Int, x: Float, y: Float) {
        cursorMode = false
        if (nameMode) { if (action == 2) nameTap(x, y); return }
        if (menuOpen) { if (action == 2) menuTap(x, y); return }
        when (action) {
            0 -> {
                if (ptr >= 0) return
                ptr = id; moved = false; downX = x; downY = y; lastPx = x
                if (y < TB_H + 2) { ptrMode = 0; return }          // il bottone agisce al rilascio
                if (y < TB_H + 10) { ptrMode = 4; scrollTo(x); return }
                if (y >= PAL_Y) { ptrMode = 3; return }
                if (tool == 2) { ptrMode = 2; return }
                ptrMode = 1
                snap()
                val tx = ((x + camX) / TS).toInt(); val ty = (y / TS).toInt()
                apply(tx, ty, true, tool == 1)
                lastTx = tx; lastTy = ty
            }
            1 -> {
                if (id != ptr) return
                if (abs(x - downX) + abs(y - downY) > 6f) moved = true
                when (ptrMode) {
                    1 -> {
                        val tx = ((x + camX) / TS).toInt(); val ty = (y / TS).toInt()
                        if (tx != lastTx || ty != lastTy) { line(lastTx, lastTy, tx, ty, tool == 1); lastTx = tx; lastTy = ty }
                    }
                    2 -> { camX = (camX - (x - lastPx)).coerceIn(0f, maxCam()); lastPx = x }
                    3 -> {
                        val d = ((x - lastPx) / CELL).toInt()
                        if (d != 0) { palScroll = (palScroll - d).coerceIn(0, max(0, pal.size - 15)); lastPx = x }
                    }
                    4 -> scrollTo(x)
                }
            }
            else -> {
                if (id != ptr) return
                if (ptrMode == 0 && downY < TB_H + 2) toolbarTap(x)
                if (ptrMode == 3 && !moved) paletteTap(x)
                ptr = -1; ptrMode = 0
            }
        }
    }

    private fun scrollTo(x: Float) {
        camX = ((x / VW) * lv.w * TS - VW / 2f).coerceIn(0f, maxCam())
    }

    private fun toolbarTap(x: Float) {
        for (i in toolbar.indices) {
            if (x >= tbX(i) && x < tbX(i) + tbW(i)) {
                game.snd.sfx(Sfx.SELECT)
                when (i) {
                    0 -> tool = 0
                    1 -> tool = 1
                    2 -> tool = 2
                    3 -> doUndo()
                    4 -> startTest()
                    5 -> { menuOpen = true; msel = 0; confirmClear = false; confirmExit = false }
                }
                return
            }
        }
    }

    private fun paletteTap(x: Float) {
        val i = palScroll + ((x - 4) / CELL).toInt()
        if (x >= 4 && i in pal.indices) { sel = i; if (tool != 0) tool = 0; game.snd.sfx(Sfx.SELECT) }
    }

    // ---------- menu ----------
    private fun menuItems(): List<String> {
        val sizes = if (lv.findEnt(K.BOSS) != null) (if (lv.bossHp > 0) lv.bossHp else 3) else 0
        return listOf(
            "PROVA IL LIVELLO",
            "SALVA",
            "NOME: " + lv.name.uppercase(),
            "TEMA: " + Worlds.names[lv.theme.coerceIn(0, 3)],
            "MERAVIGLIA: " + Worlds.wonderNames[lv.wonder.coerceIn(1, 4)],
            "TEMPO: " + lv.time,
            "LUNGHEZZA: " + lv.w,
            "PV DEL BOSS: " + (if (sizes == 0) "-" else "" + sizes),
            "EROE PER LA PROVA: " + (if (testHero == 0) "RINA" else "TOKI"),
            if (confirmClear) "SICURO? SVUOTA TUTTO" else "SVUOTA LIVELLO",
            "SALVA ED ESCI",
            if (confirmExit) "SICURO? ESCI SENZA SALVARE" else "ESCI SENZA SALVARE",
            "CHIUDI IL MENU"
        )
    }

    private fun menuAct(i: Int, d: Int) {
        if (i != 9) confirmClear = false
        if (i != 11) confirmExit = false
        when (i) {
            0 -> startTest()
            1 -> save()
            2 -> { nameMode = true; ksel = 0 }
            3 -> { lv.theme = (lv.theme + d + 4) % 4; dirty = true }
            4 -> { lv.wonder = ((lv.wonder - 1 + d + 4) % 4) + 1; dirty = true }
            5 -> { lv.time = (lv.time + d * 50).coerceIn(100, 900); dirty = true }
            6 -> {
                val nw = (lv.w + d * 20).coerceIn(40, 300)
                if (nw != lv.w) { lv.resize(nw); undo.clear(); camX = camX.coerceIn(0f, maxCam()); dirty = true }
            }
            7 -> if (lv.findEnt(K.BOSS) != null) { val cur = if (lv.bossHp > 0) lv.bossHp else 3; lv.bossHp = ((cur - 1 + d + 9) % 9) + 1; dirty = true }
            8 -> testHero = 1 - testHero
            9 -> {
                if (!confirmClear) { confirmClear = true; game.toast("PREMI ANCORA PER CONFERMARE") }
                else {
                    snap()
                    for (k in lv.t.indices) lv.t[k] = if (k / lv.w >= 13) T.GROUND else T.EMPTY
                    lv.ents.clear()
                    lv.ents.add(Spawn(K.START, 3, 12)); lv.ents.add(Spawn(K.GOAL, lv.w - 8, 12))
                    confirmClear = false; dirty = true; menuOpen = false
                }
            }
            10 -> { save(); leave() }
            11 -> {
                if (dirty && !confirmExit) { confirmExit = true; game.toast("MODIFICHE NON SALVATE!") }
                else leave()
            }
            12 -> menuOpen = false
        }
        if (i in 3..8) game.snd.sfx(Sfx.SELECT)
    }

    private fun menuTap(x: Float, y: Float) {
        val items = menuItems()
        for (i in items.indices) {
            val ry = 18 + i * 19
            if (x >= VW / 2 - 170 && x < VW / 2 + 170 && y >= ry && y < ry + 18) {
                msel = i
                menuAct(i, if (x < VW / 2 - 100 && i in 3..8) -1 else 1)
                return
            }
        }
        menuOpen = false
    }

    // ---------- nome ----------
    private fun nameTap(x: Float, y: Float) {
        val gx = (x - 20f) / 40f
        val gy = (y - 76f) / 30f
        if (gx < 0 || gy < 0) return
        val k = gy.toInt() * KCOLS + gx.toInt()
        if (gx.toInt() < KCOLS && k < KEYS.length + 2) { ksel = k; nameKey(k) }
    }

    private fun nameKey(k: Int) {
        game.snd.sfx(Sfx.SELECT)
        if (k < KEYS.length) {
            if (lv.name.length < 20) { lv.name = (if (lv.name == "SENZA NOME") "" else lv.name) + KEYS[k]; dirty = true }
        } else if (k == KEYS.length) {
            if (lv.name.isNotEmpty()) { lv.name = lv.name.substring(0, lv.name.length - 1); dirty = true }
        } else {
            if (lv.name.isBlank()) lv.name = "SENZA NOME"
            nameMode = false
        }
    }

    // ---------- aggiornamento ----------
    fun update() {
        tick++
        val i = game.input
        val anyKey = i.p(Btn.A) || i.p(Btn.B) || i.p(Btn.UP) || i.p(Btn.DOWN) || i.p(Btn.LEFT) || i.p(Btn.RIGHT) || i.p(Btn.L) || i.p(Btn.R) || i.p(Btn.START)
        if (anyKey) cursorMode = true
        if (nameMode) { updateName(); return }
        if (menuOpen) { updateMenu(); return }

        if (i.p(Btn.START)) { menuOpen = true; msel = 0; confirmClear = false; confirmExit = false; game.snd.sfx(Sfx.SELECT); return }
        if (i.p(Btn.BACK)) { menuOpen = true; msel = 0; return }
        if (i.p(Btn.L)) { sel = (sel + pal.size - 1) % pal.size; syncPal(); game.snd.sfx(Sfx.SELECT) }
        if (i.p(Btn.R)) { sel = (sel + 1) % pal.size; syncPal(); game.snd.sfx(Sfx.SELECT) }

        // autoscroll ai bordi con il dito
        if (ptr >= 0 && ptrMode == 1) {
            if (lastPx < 0) lastPx = 0f
        }

        // cursore con ripetizione
        var dx = 0; var dy = 0
        dx += rep(i.h(Btn.RIGHT), 0) - rep(i.h(Btn.LEFT), 1)
        dy += rep(i.h(Btn.DOWN), 2) - rep(i.h(Btn.UP), 3)
        if (dx != 0 || dy != 0) {
            cx = (cx + dx).coerceIn(0, lv.w - 1)
            cy = (cy + dy).coerceIn(0, lv.h - 1)
        }
        if (cursorMode) {
            val sx = cx * TS - camX
            if (sx < 48) camX = (cx * TS - 48f).coerceIn(0f, maxCam())
            if (sx > VW - 64) camX = (cx * TS - (VW - 64f)).coerceIn(0f, maxCam())
        }
        val moved2 = cx != lastKX || cy != lastKY
        if (i.p(Btn.A) || i.p(Btn.B)) { snap(); apply(cx, cy, true, i.p(Btn.B)) }
        else if ((i.h(Btn.A) || i.h(Btn.B)) && moved2 && lastKX >= 0) {
            val er = i.h(Btn.B) && !i.h(Btn.A)
            line(lastKX, lastKY, cx, cy, er)
        }
        lastKX = cx; lastKY = cy
        if (!i.h(Btn.A) && !i.h(Btn.B)) { /* niente */ }
    }

    private fun rep(h: Boolean, k: Int): Int {
        val v = when (k) { 0 -> holdR; 1 -> holdL; 2 -> holdD; else -> holdU }
        val nv = if (h) v + 1 else 0
        when (k) { 0 -> holdR = nv; 1 -> holdL = nv; 2 -> holdD = nv; else -> holdU = nv }
        if (nv == 1) return 1
        if (nv > 14 && nv % 3 == 0) return 1
        return 0
    }

    private fun syncPal() {
        if (sel < palScroll) palScroll = sel
        if (sel >= palScroll + 15) palScroll = sel - 14
        palScroll = palScroll.coerceIn(0, max(0, pal.size - 15))
    }

    private fun updateMenu() {
        val i = game.input
        val n = menuItems().size
        if (i.p(Btn.UP)) { msel = (msel + n - 1) % n; game.snd.sfx(Sfx.SELECT) }
        if (i.p(Btn.DOWN)) { msel = (msel + 1) % n; game.snd.sfx(Sfx.SELECT) }
        if (i.p(Btn.LEFT)) menuAct(msel, -1) else if (i.p(Btn.RIGHT)) menuAct(msel, 1)
        else if (i.p(Btn.A)) menuAct(msel, 1)
        if (i.p(Btn.B) || i.p(Btn.BACK) || i.p(Btn.START)) { menuOpen = false; game.snd.sfx(Sfx.BACK) }
    }

    private fun updateName() {
        val i = game.input
        val n = KEYS.length + 2
        if (i.p(Btn.LEFT)) ksel = (ksel + n - 1) % n
        if (i.p(Btn.RIGHT)) ksel = (ksel + 1) % n
        if (i.p(Btn.UP)) ksel = if (ksel >= KCOLS) ksel - KCOLS else ksel
        if (i.p(Btn.DOWN)) ksel = if (ksel + KCOLS < n) ksel + KCOLS else ksel
        if (i.p(Btn.A)) nameKey(ksel)
        if (i.p(Btn.B)) nameKey(KEYS.length)
        if (i.p(Btn.START)) nameKey(KEYS.length + 1)
    }

    // ---------- disegno ----------
    fun render(g: Gfx) {
        val th = lv.theme.coerceIn(0, 3)
        val ta = Art.theme(th)
        val cam = camX.toInt()
        WorldRender.drawSky(g, th, camX, tick, lv.wonder, false)

        // griglia leggera
        val t0 = cam / TS
        val t1 = min(lv.w - 1, (cam + VW) / TS + 1)
        for (tx in t0..t1) g.fill(tx * TS - cam, 0, 1, VH, withAlpha(18, 0xFFFFFF))
        for (ty in 0..lv.h) g.fill(0, ty * TS, VW, 1, withAlpha(18, 0xFFFFFF))

        // entita' dietro ai tile
        for (e in lv.ents) {
            val sx = e.x * TS - cam
            if (sx < -48 || sx > VW + 48) continue
            drawEnt(g, e.type, sx, e.y * TS, ta, 1f)
        }
        // tile
        for (ty in 0 until lv.h) for (tx in t0..t1) {
            val id = lv.get(tx, ty)
            if (id == T.EMPTY) continue
            val above = if (ty > 0) lv.get(tx, ty - 1) else T.EMPTY
            val im = WorldRender.tileImage(ta, id, above, tx, ty, tick) ?: continue
            g.img(im, tx * TS - cam, ty * TS)
        }
        // limiti del livello
        if (lv.w * TS - cam < VW) g.fill(lv.w * TS - cam, 0, VW, VH, 0xFF000000.toInt())

        // cursore / anteprima
        if (cursorMode && !menuOpen && !nameMode) {
            val blink = (tick / 15) % 2 == 0
            Ui.frame(g, cx * TS - cam - 1, cy * TS - 1, TS + 2, TS + 2, if (blink) Ui.GOLD else Ui.WHITE)
        }

        // barra strumenti
        g.fill(0, 0, VW, TB_H, withAlpha(210, 0x14102A))
        g.fill(0, TB_H, VW, 1, Ui.GOLD)
        for (i in toolbar.indices) {
            val on = i == tool
            val x = tbX(i); val w = tbW(i)
            g.fill(x, 3, w, 16, if (on) withAlpha(230, 0xFFD23C) else withAlpha(200, 0x3A3260))
            Ui.frame(g, x, 3, w, 16, if (on) Ui.WHITE else Ui.DIM)
            Font.center(g, toolbar[i], x + w / 2, 8, if (on) 0xFF1A1530.toInt() else Ui.WHITE)
        }
        Font.text(g, (if (dirty) "*" else "") + lv.name.uppercase(), 4 + 6 * 40 - 8, 8, Ui.GRAY, 1)
        // barra di scorrimento
        val sbw = VW
        g.fill(0, TB_H + 2, sbw, 4, withAlpha(150, 0x000000))
        val thumbW = max(12, (VW.toFloat() / (lv.w * TS) * sbw).toInt())
        val thumbX = if (maxCam() <= 0f) 0 else (camX / maxCam() * (sbw - thumbW)).toInt()
        g.fill(thumbX, TB_H + 2, thumbW, 4, withAlpha(220, 0xFFD23C))

        // palette
        g.fill(0, PAL_Y - 2, VW, VH - PAL_Y + 2, withAlpha(220, 0x14102A))
        g.fill(0, PAL_Y - 2, VW, 1, Ui.GOLD)
        for (k in 0 until 15) {
            val idx = palScroll + k
            if (idx >= pal.size) break
            val x = 4 + k * CELL
            val on = idx == sel
            g.fill(x, PAL_Y, CELL - 2, CELL - 2, if (on) withAlpha(230, 0xFFD23C) else withAlpha(200, 0x2A2450))
            Ui.frame(g, x, PAL_Y, CELL - 2, CELL - 2, if (on) Ui.WHITE else Ui.DIM)
            drawPalItem(g, pal[idx], x + 2, PAL_Y + 2, ta)
        }
        if (palScroll > 0) Font.text(g, "<", 0, PAL_Y + 8, Ui.WHITE, 1, Ui.BLACK)
        if (palScroll + 15 < pal.size) Font.text(g, ">", VW - 6, PAL_Y + 8, Ui.WHITE, 1, Ui.BLACK)

        if (menuOpen) drawMenu(g)
        if (nameMode) drawName(g)
    }

    private fun drawPalItem(g: Gfx, c: Int, x: Int, y: Int, ta: ThemeArt) {
        if (c < 100) {
            val im = WorldRender.tileImage(ta, c, T.EMPTY, 0, 0, 0)
            if (im != null) g.img(im, x + 4, y + 4, false, false, 16, 16)
        } else {
            drawEnt(g, c - 100, x, y - 6, ta, 0.75f, true)
        }
    }

    /** Disegna un'entita' dell'editor nella cella (px, py) = angolo in alto a sinistra del tile. */
    private fun drawEnt(g: Gfx, type: Int, px: Int, py: Int, ta: ThemeArt, scale: Float, icon: Boolean = false) {
        fun put(im: Img) {
            val w = (im.w * scale).toInt(); val h = (im.h * scale).toInt()
            g.img(im, px + (TS - w) / 2 + (if (icon) 4 else 0), py + TS - h + (if (icon) 6 else 0), false, false, w, h)
        }
        val f = (tick / 12) % 2
        when (type) {
            K.START -> { put(Art.hero(testHero, 0)); Font.text(g, "S", px + 4 + (if (icon) 4 else 0), py + 2 + (if (icon) 6 else 0), Ui.WHITE, 1, Ui.BLACK) }
            K.GOAL -> {
                val bx = px + 7 + (if (icon) 4 else 0); val by = py + TS + (if (icon) 6 else 0)
                val top = if (icon) by - 16 else by - 5 * TS
                g.fill(bx, top, 2, by - top, 0xFFE8ECF4.toInt())
                g.fill(bx - 3, by - 5, 8, 5, 0xFF6A6F85.toInt())
                g.fill(bx - 2, top - 4, 6, 5, 0xFFFFD23C.toInt())
                g.fill(bx + 2, top + 3, 8, 7, 0xFFE8334A.toInt())
            }
            K.CHECK -> {
                val im = Art.flagCheckOff
                val w = (im.w * scale).toInt(); val h = (im.h * scale).toInt()
                g.img(im, px + (TS - w) / 2 + (if (icon) 4 else 0), py + TS - h + (if (icon) 6 else 0), false, false, w, h)
            }
            K.SLIME -> put(ta.slime[f])
            K.SNAIL -> put(ta.snail[f])
            K.HEDGE -> put(Art.hedge[f])
            K.BAT -> put(Art.bat[f])
            K.FROG -> put(ta.frog[0])
            K.BOSS -> {
                val im = ta.boss[f]
                val w = (32 * scale).toInt(); val h = (29 * scale).toInt()
                g.img(im, px + (TS - w) / 2 + (if (icon) 4 else 0), py + TS - h + (if (icon) 6 else 0), false, false, w, h)
            }
        }
    }

    private fun drawMenu(g: Gfx) {
        g.fill(0, 0, VW, VH, withAlpha(170, 0x000000))
        val items = menuItems()
        Ui.panel(g, VW / 2 - 176, 6, 352, 18 + items.size * 19 - 6 + 6)
        for (i in items.indices) {
            val y = 18 + i * 19
            val s = i == msel
            if (s) { g.fill(VW / 2 - 168, y, 336, 18, withAlpha(90, 0xFFD23C)); Ui.frame(g, VW / 2 - 168, y, 336, 18, Ui.GOLD) }
            val col = if (s) Ui.GOLD else Ui.WHITE
            Font.center(g, items[i], VW / 2, y + 5, col, 1, Ui.BLACK)
            if (i in 3..8) {
                Font.text(g, "<", VW / 2 - 160, y + 5, Ui.GRAY, 1)
                Font.text(g, ">", VW / 2 + 155, y + 5, Ui.GRAY, 1)
            }
        }
    }

    private fun drawName(g: Gfx) {
        g.fill(0, 0, VW, VH, withAlpha(200, 0x000000))
        Font.center(g, "NOME DEL LIVELLO", VW / 2, 16, Ui.GOLD, 2, Ui.BLACK)
        Ui.panel(g, 60, 42, VW - 120, 24)
        val cur = if ((tick / 20) % 2 == 0) "_" else " "
        Font.center(g, lv.name.uppercase() + cur, VW / 2, 50, Ui.WHITE, 1, Ui.BLACK)
        val n = KEYS.length + 2
        for (k in 0 until n) {
            val x = 20 + (k % KCOLS) * 40
            val y = 76 + (k / KCOLS) * 30
            val s = k == ksel
            g.fill(x + 1, y + 1, 38, 28, if (s) withAlpha(230, 0xFFD23C) else withAlpha(220, 0x2A2450))
            Ui.frame(g, x + 1, y + 1, 38, 28, if (s) Ui.WHITE else Ui.DIM)
            val lab = if (k < KEYS.length) "" + KEYS[k] else if (k == KEYS.length) "CANC" else "OK"
            Font.center(g, lab, x + 20, y + 11, if (s) 0xFF1A1530.toInt() else Ui.WHITE, 1)
        }
        Font.center(g, "A: SCRIVI   B: CANCELLA   START: FINE", VW / 2, 226, Ui.GRAY, 1, Ui.BLACK)
    }
}

package com.marco.pixelbloom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Scr {
    const val TITLE = 0
    const val PROFILES = 1
    const val CHAR = 2
    const val MAP = 3
    const val PLAY = 4
    const val RESULT = 5
    const val OPTIONS = 6
    const val EDMENU = 7
    const val MYLEVELS = 8
    const val EDIT = 9
    const val ENDING = 10
}

object Mode {
    const val STORY = 0
    const val CUSTOM = 1
    const val TEST = 2
}

/** Tutto il gioco: schermate, menu, mappa, partita ed editor. Indipendente da Android. */
class Game(val store: Storage, val snd: Sound, val host: Host) {
    val input = Input()
    var opts: Options = Options.parse(store.get("opts"))
    val profiles = Array(3) { Saves.loadProfile(store, it) }
    var slot = 0
    var screen = Scr.TITLE
    var tick = 0
    var menuSel = 0
    private val menuRects = ArrayList<IntArray>()
    private var tapIndex = -1
    private var tapX = 0f
    private var tapY = 0f
    private var tapPending = false
    private var fade = 255
    private var toastText = ""
    private var toastT = 0

    // partita
    var world: World? = null
    var mode = Mode.STORY
    private var playLevel = 0
    private var playBase: Level? = null
    private var paused = false
    private var pauseSel = 0

    // risultati
    private var resCoins = 0
    private var resSec = 0
    private var resSeed = false
    private var resNewWorld = -1
    private var resT = 0

    // profili / personaggio
    private var profMode = 0
    private var confirmOverwrite = false
    private var charSel = 0

    // editor e livelli personali
    var editor: Editor? = null
    private var myKeys: List<String> = emptyList()
    private var mySub = false
    private var myConfirmDelete = false
    private var myScroll = 0
    private var endScroll = 0

    // comandi a schermo
    private class TBtn(val btn: Btn, var cx: Int, var cy: Int, var r: Int, val label: String)
    private val tBtns = ArrayList<TBtn>()
    private val pointerBtn = HashMap<Int, Int>()
    private val pressedBtn = HashSet<Int>()

    init {
        applyVolumes()
        layoutControls()
        go(Scr.TITLE)
    }

    // ---------- utilita' ----------
    fun toast(s: String) { toastText = s; toastT = 150 }

    private fun applyVolumes() = snd.setVolumes(opts.music / 10f, opts.sfx / 10f)

    fun saveOptions() = store.put("opts", opts.serialize())

    private fun anyProfile(): Boolean { for (p in profiles) if (p.used) return true; return false }

    fun go(s: Int) {
        screen = s
        menuSel = 0
        fade = 255
        tapPending = false
        tapIndex = -1
        when (s) {
            Scr.TITLE -> snd.music(Mus.TITLE)
            Scr.MAP -> snd.music(Mus.MAP)
            Scr.EDIT -> snd.music(Mus.EDIT)
            Scr.ENDING -> snd.music(Mus.ENDING)
            Scr.RESULT -> snd.music(Mus.NONE)
        }
    }

    private fun layoutControls() {
        tBtns.clear()
        val s = when (opts.size) { 0 -> 0.85f; 1 -> 1f; else -> 1.2f }
        fun px(v: Float) = (v * s).toInt()
        tBtns.add(TBtn(Btn.LEFT, px(34f), VH - px(38f), px(23f), "<"))
        tBtns.add(TBtn(Btn.RIGHT, px(90f), VH - px(38f), px(23f), ">"))
        tBtns.add(TBtn(Btn.DOWN, px(62f), VH - px(10f), px(13f), "V"))
        tBtns.add(TBtn(Btn.A, VW - px(40f), VH - px(42f), px(27f), "A"))
        tBtns.add(TBtn(Btn.B, VW - px(100f), VH - px(30f), px(22f), "B"))
        tBtns.add(TBtn(Btn.START, VW - 18, 16, 12, "II"))
    }

    // ---------- ingresso da tastiera / controller / tocco ----------
    fun key(b: Btn, down: Boolean) { input.set(b, down, 1) }

    fun touch(action: Int, id: Int, x: Float, y: Float) {
        if (screen == Scr.EDIT) { editor?.touch(action, id, x, y); return }
        if (screen == Scr.PLAY && !paused && world?.state == 0) { touchControls(action, id, x, y); if (action != 2 && action != 3) return }
        if (action == 2) {
            tapX = x; tapY = y; tapPending = true
            tapIndex = -1
            for (i in menuRects.indices) {
                val r = menuRects[i]
                if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) { tapIndex = i; break }
            }
        }
    }

    private fun touchControls(action: Int, id: Int, x: Float, y: Float) {
        if (!opts.controls && action == 0) {
            // anche senza comandi visibili, il tasto pausa resta disponibile
        }
        when (action) {
            0, 1 -> {
                var best = -1
                var bd = 1e9f
                for (i in tBtns.indices) {
                    val b = tBtns[i]
                    if (!opts.controls && b.btn != Btn.START) continue
                    val dx = x - b.cx; val dy = y - b.cy
                    val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (d < b.r * 1.35f && d < bd) { bd = d; best = i }
                }
                val old = pointerBtn[id]
                if (old != null && old != best) { input.set(tBtns[old].btn, false, 2); pressedBtn.remove(old); pointerBtn.remove(id) }
                if (best >= 0 && old != best) {
                    input.set(tBtns[best].btn, true, 2)
                    pressedBtn.add(best)
                    pointerBtn[id] = best
                    if (opts.vibrate) host.vibrate(12)
                }
            }
            else -> {
                val old = pointerBtn[id]
                if (old != null) { input.set(tBtns[old].btn, false, 2); pressedBtn.remove(old); pointerBtn.remove(id) }
            }
        }
    }

    /** Rilascia tutti i tasti (es. quando l'app va in secondo piano). */
    fun releaseAll() {
        input.releaseAll()
        pointerBtn.clear(); pressedBtn.clear()
        if (screen == Scr.PLAY && world?.state == 0 && !paused) { paused = true; pauseSel = 0 }
    }

    // ---------- aggiornamento (60 Hz) ----------
    fun update() {
        tick++
        if (fade > 0) fade = max(0, fade - 16)
        if (toastT > 0) toastT--
        when (screen) {
            Scr.TITLE -> updateTitle()
            Scr.PROFILES -> updateProfiles()
            Scr.CHAR -> updateChar()
            Scr.MAP -> updateMap()
            Scr.PLAY -> updatePlay()
            Scr.RESULT -> updateResult()
            Scr.OPTIONS -> updateOptions()
            Scr.EDMENU -> updateEdMenu()
            Scr.MYLEVELS -> updateMyLevels()
            Scr.EDIT -> editor?.update()
            Scr.ENDING -> updateEnding()
        }
        tapPending = false
        tapIndex = -1
        input.endFrame()
    }

    /** Navigazione verticale in un menu: ritorna l'indice confermato, -2 per "indietro", -1 per niente. */
    private fun nav(n: Int, enabled: BooleanArray? = null): Int {
        val i = input
        if (n <= 0) return if (i.p(Btn.B) || i.p(Btn.BACK)) -2 else -1
        if (i.p(Btn.UP)) { menuSel = step(menuSel, -1, n, enabled); snd.sfx(Sfx.SELECT) }
        if (i.p(Btn.DOWN)) { menuSel = step(menuSel, 1, n, enabled); snd.sfx(Sfx.SELECT) }
        if (tapIndex in 0 until n) {
            menuSel = tapIndex
            if (enabled == null || enabled[menuSel]) return menuSel
            snd.sfx(Sfx.BUMP)
            return -1
        }
        if (i.p(Btn.A) || i.p(Btn.START)) {
            if (enabled == null || enabled[menuSel]) return menuSel
            snd.sfx(Sfx.BUMP)
            return -1
        }
        if (i.p(Btn.B) || i.p(Btn.BACK)) return -2
        return -1
    }

    private fun step(sel: Int, d: Int, n: Int, enabled: BooleanArray?): Int {
        var s = sel
        for (k in 0 until n) {
            s = (s + d + n) % n
            if (enabled == null || enabled[s]) return s
        }
        return sel
    }

    // ---------- titolo ----------
    private fun updateTitle() {
        val en = booleanArrayOf(true, anyProfile(), true, true, true)
        if (menuSel == 1 && !en[1]) menuSel = 0
        val r = nav(5, en)
        if (r < 0) return
        snd.sfx(Sfx.CONFIRM)
        when (r) {
            0 -> { profMode = 0; confirmOverwrite = false; go(Scr.PROFILES) }
            1 -> { profMode = 1; confirmOverwrite = false; go(Scr.PROFILES) }
            2 -> go(Scr.EDMENU)
            3 -> go(Scr.OPTIONS)
            4 -> host.exit()
        }
    }

    // ---------- profili ----------
    private fun updateProfiles() {
        if (confirmOverwrite) {
            val r = nav(2)
            if (r == -2 || r == 1) { confirmOverwrite = false; menuSel = slot; snd.sfx(Sfx.BACK) }
            else if (r == 0) { confirmOverwrite = false; charSel = 0; go(Scr.CHAR) }
            return
        }
        val en = BooleanArray(4) { it == 3 || profMode == 0 || profiles[it].used }
        val r = nav(4, en)
        if (r == -2 || r == 3) { snd.sfx(Sfx.BACK); go(Scr.TITLE); return }
        if (r in 0..2) {
            slot = r
            snd.sfx(Sfx.CONFIRM)
            if (profMode == 0) {
                if (profiles[slot].used) { confirmOverwrite = true; menuSel = 1 } else { charSel = 0; go(Scr.CHAR) }
            } else {
                openMap()
            }
        }
    }

    // ---------- scelta personaggio ----------
    private fun updateChar() {
        if (input.p(Btn.LEFT) || input.p(Btn.RIGHT)) { charSel = 1 - charSel; snd.sfx(Sfx.SELECT) }
        if (tapPending) {
            if (tapY in 40f..220f) {
                val c = if (tapX < VW / 2f) 0 else 1
                if (c == charSel) confirmChar() else { charSel = c; snd.sfx(Sfx.SELECT) }
            } else if (tapY > 230f && tapX < 100f) { snd.sfx(Sfx.BACK); go(Scr.PROFILES) }
        }
        if (input.p(Btn.A) || input.p(Btn.START)) confirmChar()
        if (input.p(Btn.B) || input.p(Btn.BACK)) { snd.sfx(Sfx.BACK); go(Scr.PROFILES) }
    }

    private fun confirmChar() {
        snd.sfx(Sfx.CONFIRM)
        val p = Profile()
        p.used = true; p.hero = charSel
        profiles[slot] = p
        Saves.saveProfile(store, slot, p)
        openMap()
    }

    // ---------- mappa ----------
    private fun prof(): Profile = profiles[slot]

    private fun openMap() {
        val p = prof()
        if (!p.unlocked(p.cur)) p.cur = p.firstOpen()
        go(Scr.MAP)
    }

    private fun nodeX(k: Int) = 62 + k * 89
    private fun nodeY(w: Int, k: Int) = 132 + (sin((k + w * 0.9) * 1.3) * 40).toInt()

    private fun updateMap() {
        val p = prof()
        var moved = false
        if (input.p(Btn.LEFT) && p.cur > 0) { p.cur--; moved = true }
        if (input.p(Btn.RIGHT)) { if (p.cur < 19 && p.unlocked(p.cur + 1)) { p.cur++; moved = true } else snd.sfx(Sfx.BUMP) }
        if (tapPending) {
            val w = p.cur / 5
            var hit = -1
            for (k in 0 until 5) {
                val dx = tapX - nodeX(k); val dy = tapY - nodeY(w, k)
                if (dx * dx + dy * dy < 22 * 22) hit = w * 5 + k
            }
            if (hit >= 0) {
                if (!p.unlocked(hit)) snd.sfx(Sfx.BUMP)
                else if (hit == p.cur) { startStory(hit); return }
                else { p.cur = hit; moved = true }
            } else if (tapX < 28f && tapY in 100f..170f && p.cur > 0) { p.cur = max(0, (p.cur / 5) * 5 - 1); moved = true }
            else if (tapX > VW - 28f && tapY in 100f..170f) {
                val nw = p.cur / 5 + 1
                if (nw < 4 && p.unlocked(nw * 5)) { p.cur = nw * 5; moved = true } else snd.sfx(Sfx.BUMP)
            } else if (tapX < 60f && tapY < 30f) { snd.sfx(Sfx.BACK); Saves.saveProfile(store, slot, p); go(Scr.TITLE); return }
        }
        if (moved) snd.sfx(Sfx.SELECT)
        if (input.p(Btn.A) || input.p(Btn.START)) { startStory(p.cur); return }
        if (input.p(Btn.B) || input.p(Btn.BACK)) { snd.sfx(Sfx.BACK); Saves.saveProfile(store, slot, p); go(Scr.TITLE) }
    }

    // ---------- partita ----------
    private fun startStory(i: Int) {
        mode = Mode.STORY
        playLevel = i
        playBase = LevelGen.level(i)
        beginPlay(null, false)
    }

    fun startCustom(lv: Level) {
        mode = Mode.CUSTOM
        playBase = lv
        beginPlay(null, false)
    }

    fun startTest(lv: Level) {
        mode = Mode.TEST
        playBase = lv.copy()
        beginPlay(null, false)
    }

    private fun beginPlay(pos: FloatArray?, seedKeep: Boolean) {
        val base = playBase ?: return
        val hero = if (mode == Mode.STORY) prof().hero else if (mode == Mode.TEST) (editor?.testHero ?: 0) else prof().hero
        val w = World(base, hero, snd, pos, seedKeep)
        world = w
        paused = false
        pauseSel = 0
        screen = Scr.PLAY
        fade = 255
        input.releaseAll()
        pointerBtn.clear()
    }

    private fun levelLabel(): String = when (mode) {
        Mode.STORY -> "" + (playLevel / 5 + 1) + "-" + (playLevel % 5 + 1) + " " + Worlds.levelNames[playLevel].uppercase()
        else -> (playBase?.name ?: "").uppercase()
    }

    private fun updatePlay() {
        val w = world ?: return
        if (paused) {
            val r = nav(3)
            if (r == -2 || r == 0) { paused = false; snd.sfx(Sfx.BACK) }
            else if (r == 1) { snd.sfx(Sfx.CONFIRM); beginPlay(null, false) }
            else if (r == 2) { snd.sfx(Sfx.CONFIRM); exitPlay() }
            return
        }
        if (input.p(Btn.START) || input.p(Btn.BACK)) { paused = true; menuSel = 0; snd.sfx(Sfx.SELECT); return }
        w.update(input)
        if (w.failed) {
            beginPlay(w.respawnPos(), w.seedGot)
            return
        }
        if (w.finished) finishPlay(w)
    }

    private fun exitPlay() {
        world = null
        snd.music(Mus.NONE)
        when (mode) {
            Mode.STORY -> openMap()
            Mode.CUSTOM -> { loadMyLevels(); go(Scr.MYLEVELS) }
            else -> { go(Scr.EDIT); editor?.resumeFromTest() }
        }
    }

    private fun finishPlay(w: World) {
        resCoins = w.coins
        resSec = max(0, w.timeLeft / 60)
        resSeed = w.seedGot
        resNewWorld = -1
        resT = 0
        if (mode == Mode.STORY) {
            val p = prof()
            val wasDone = p.done[playLevel]
            p.done[playLevel] = true
            if (w.seedGot) p.seed[playLevel] = true
            p.coins += w.coins
            p.cur = min(19, playLevel + 1)
            if (!wasDone && playLevel % 5 == 4 && playLevel < 19) resNewWorld = playLevel / 5 + 1
            Saves.saveProfile(store, slot, p)
        }
        world = null
        go(Scr.RESULT)
    }

    private fun updateResult() {
        resT++
        if (resT < 40) return
        if (input.p(Btn.A) || input.p(Btn.START) || input.p(Btn.B) || tapPending) {
            snd.sfx(Sfx.CONFIRM)
            when (mode) {
                Mode.STORY -> if (playLevel >= 19) { endScroll = 0; go(Scr.ENDING) } else openMap()
                Mode.CUSTOM -> { loadMyLevels(); go(Scr.MYLEVELS) }
                else -> { go(Scr.EDIT); editor?.resumeFromTest() }
            }
        }
    }

    private fun updateEnding() {
        endScroll++
        if (endScroll > 60 && (input.p(Btn.A) || input.p(Btn.START) || input.p(Btn.B) || tapPending)) {
            snd.sfx(Sfx.CONFIRM); go(Scr.TITLE)
        }
    }

    // ---------- opzioni ----------
    private fun updateOptions() {
        val n = 7
        val i = input
        if (i.p(Btn.UP)) { menuSel = (menuSel + n - 1) % n; snd.sfx(Sfx.SELECT) }
        if (i.p(Btn.DOWN)) { menuSel = (menuSel + 1) % n; snd.sfx(Sfx.SELECT) }
        var d = 0
        if (i.p(Btn.LEFT)) d = -1
        if (i.p(Btn.RIGHT) || i.p(Btn.A) || i.p(Btn.START)) d = 1
        if (tapIndex in 0 until n) { menuSel = tapIndex; d = 1 }
        if (i.p(Btn.B) || i.p(Btn.BACK)) { saveOptions(); snd.sfx(Sfx.BACK); go(Scr.TITLE); return }
        if (d == 0) return
        when (menuSel) {
            0 -> { opts.music = (opts.music + d + 11) % 11; applyVolumes() }
            1 -> { opts.sfx = (opts.sfx + d + 11) % 11; applyVolumes(); snd.sfx(Sfx.COIN) }
            2 -> opts.controls = !opts.controls
            3 -> opts.opacity = ((opts.opacity - 1 + d + 10) % 10) + 1
            4 -> { opts.size = (opts.size + d + 3) % 3; layoutControls() }
            5 -> { opts.vibrate = !opts.vibrate; if (opts.vibrate) host.vibrate(30) }
            6 -> { saveOptions(); snd.sfx(Sfx.BACK); go(Scr.TITLE) }
        }
        if (menuSel != 1) snd.sfx(Sfx.SELECT)
    }

    // ---------- menu editor e livelli personali ----------
    private fun updateEdMenu() {
        val r = nav(4)
        if (r == -2 || r == 3) { snd.sfx(Sfx.BACK); go(Scr.TITLE); return }
        if (r < 0) return
        snd.sfx(Sfx.CONFIRM)
        when (r) {
            0 -> openEditor(Level.blank(100), null)
            1 -> { loadMyLevels(); go(Scr.MYLEVELS) }
            2 -> {
                val txt = host.clipboard()
                val lv = if (txt != null) Level.parse(txt) else null
                if (lv == null) toast("NIENTE DI VALIDO NEGLI APPUNTI")
                else { val id = saveNewLevel(lv); toast("LIVELLO IMPORTATO!"); openEditor(lv, id) }
            }
        }
    }

    fun openEditor(lv: Level, id: String?) {
        editor = Editor(this, lv, id)
        go(Scr.EDIT)
    }

    fun newLevelId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36)

    fun saveNewLevel(lv: Level): String {
        val id = newLevelId()
        store.put("lvl_$id", lv.serialize())
        return id
    }

    fun saveLevel(id: String, lv: Level) = store.put("lvl_$id", lv.serialize())

    private fun loadMyLevels() {
        myKeys = store.keys("lvl_")
        menuSel = 0
        myScroll = 0
        mySub = false
        myConfirmDelete = false
    }

    private fun levelName(key: String): String = Level.parse(store.get(key) ?: "")?.name ?: "???"

    private fun updateMyLevels() {
        val n = myKeys.size
        if (mySub) {
            val r = nav(5)
            if (r == -2 || r == 4) { mySub = false; myConfirmDelete = false; menuSel = subReturn; snd.sfx(Sfx.BACK); return }
            if (r < 0) return
            val key = myKeys[subReturn]
            val lv = Level.parse(store.get(key) ?: "")
            snd.sfx(Sfx.CONFIRM)
            when (r) {
                0 -> if (lv != null) { mySub = false; startCustom(lv) }
                1 -> if (lv != null) { mySub = false; openEditor(lv, key.substring(4)) }
                2 -> if (lv != null) { host.share(lv.serialize()); toast("CODICE LIVELLO CONDIVISO") }
                3 -> {
                    if (!myConfirmDelete) { myConfirmDelete = true; toast("PREMI ANCORA PER ELIMINARE") }
                    else { store.remove(key); loadMyLevels(); toast("LIVELLO ELIMINATO") }
                }
            }
            if (r != 3) myConfirmDelete = false
            return
        }
        if (n == 0) {
            if (input.p(Btn.A) || input.p(Btn.B) || input.p(Btn.BACK) || tapPending) { snd.sfx(Sfx.BACK); go(Scr.EDMENU) }
            return
        }
        if (tapIndex >= 0) tapIndex += myScroll
        val r = nav(n)
        if (menuSel < myScroll) myScroll = menuSel
        if (menuSel >= myScroll + 7) myScroll = menuSel - 6
        if (r == -2) { snd.sfx(Sfx.BACK); go(Scr.EDMENU); return }
        if (r >= 0) { subReturn = r; mySub = true; menuSel = 0; snd.sfx(Sfx.CONFIRM) }
    }

    private var subReturn = 0

    // ---------- disegno ----------
    fun render(g: Gfx) {
        menuRects.clear()
        when (screen) {
            Scr.TITLE -> drawTitle(g)
            Scr.PROFILES -> drawProfiles(g)
            Scr.CHAR -> drawChar(g)
            Scr.MAP -> drawMap(g)
            Scr.PLAY -> drawPlay(g)
            Scr.RESULT -> drawResult(g)
            Scr.OPTIONS -> drawOptions(g)
            Scr.EDMENU -> drawEdMenu(g)
            Scr.MYLEVELS -> drawMyLevels(g)
            Scr.EDIT -> editor?.render(g)
            Scr.ENDING -> drawEnding(g)
        }
        if (toastT > 0) {
            val w = Font.width(toastText) + 16
            Ui.panel(g, VW / 2 - w / 2, 44, w, 18)
            Font.center(g, toastText, VW / 2, 49, Ui.WHITE)
        }
        if (fade > 0) g.fill(0, 0, VW, VH, withAlpha(fade, 0x000000))
    }

    private fun drawMenu(g: Gfx, items: List<String>, sel: Int, cx: Int, y0: Int, stepY: Int, scale: Int = 2,
                         enabled: BooleanArray? = null, w: Int = 230, showSel: Boolean = true) {
        for (i in items.indices) {
            val y = y0 + i * stepY
            val x = cx - w / 2
            menuRects.add(intArrayOf(x, y - 4, w, stepY - 2))
            val en = enabled == null || enabled[i]
            val s = showSel && i == sel
            if (s) {
                g.fill(x, y - 4, w, stepY - 2, withAlpha(90, 0xFFD23C))
                Ui.frame(g, x, y - 4, w, stepY - 2, Ui.GOLD)
                val bob = ((tick / 8) % 2)
                Font.text(g, ">", x + 6 + bob, y + (if (scale == 2) 0 else 0), Ui.GOLD, scale, Ui.BLACK)
            }
            val col = if (!en) Ui.DIM else if (s) Ui.GOLD else Ui.WHITE
            Font.center(g, items[i], cx, y, col, scale, if (en) Ui.BLACK else 0)
        }
    }

    private fun groundStrip(g: Gfx, theme: Int, y: Int, scroll: Int) {
        val ta = Art.theme(theme)
        var x = -(scroll % 32)
        if (x > 0) x -= 32
        var i = 0
        while (x < VW) {
            g.img(ta.groundTop[i % 2], x, y)
            g.img(ta.groundTop[i % 2], x + 16, y)
            var yy = y + 16
            while (yy < VH) { g.img(ta.groundFill[(i + yy / 16) % 2], x, yy); g.img(ta.groundFill[(i + yy / 16 + 1) % 2], x + 16, yy); yy += 16 }
            x += 32; i++
        }
    }

    private fun drawTitle(g: Gfx) {
        WorldRender.drawSky(g, 0, tick * 0.6f, tick, 1, false)
        groundStrip(g, 0, 226, tick / 2)
        // personaggi
        val f0 = if ((tick / 8) % 2 == 0) 1 else 2
        g.img(Art.hero(0, f0), 48, 154, false, false, 48, 72)
        g.img(Art.hero(1, f0), VW - 96, 154, true, false, 48, 72)
        // logo
        val bob = (sin(tick * 0.06f) * 3f).toInt()
        g.img(Art.flower, 52, 22 + bob, false, false, 40, 40)
        g.img(Art.flower, VW - 92, 22 - bob, true, false, 40, 40)
        Font.center(g, "PIXEL", VW / 2, 18 + bob, Ui.PINK, 6, 0xFF7A1E4A.toInt())
        Font.center(g, "BLOOM", VW / 2, 66 + bob, Ui.GOLD, 6, 0xFF8A5A00.toInt())
        Font.center(g, "UN'AVVENTURA PIXEL ART", VW / 2, 114, Ui.WHITE, 1, Ui.BLACK)
        val en = booleanArrayOf(true, anyProfile(), true, true, true)
        drawMenu(g, listOf("INIZIA", "CONTINUA", "EDITOR", "OPZIONI", "ESCI"), menuSel, VW / 2, 132, 18, 2, en, 200)
    }

    private fun drawProfiles(g: Gfx) {
        WorldRender.drawSky(g, 0, tick * 0.3f, tick, 1, false)
        groundStrip(g, 0, 232, 0)
        Ui.panel(g, 40, 20, VW - 80, VH - 60)
        Font.center(g, if (profMode == 0) "NUOVA PARTITA" else "CONTINUA", VW / 2, 34, Ui.GOLD, 2, Ui.BLACK)
        if (confirmOverwrite) {
            Font.center(g, "SOVRASCRIVERE IL PROFILO " + (slot + 1) + "?", VW / 2, 90, Ui.WHITE, 1, Ui.BLACK)
            Font.center(g, "I PROGRESSI ANDRANNO PERSI", VW / 2, 106, Ui.GRAY, 1, Ui.BLACK)
            drawMenu(g, listOf("SI", "NO"), menuSel, VW / 2, 130, 26, 2, null, 160)
            return
        }
        val items = ArrayList<String>()
        val en = BooleanArray(4)
        for (i in 0 until 3) {
            val p = profiles[i]
            items.add(if (p.used) "PROFILO " + (i + 1) + "  " + p.doneCount() + "/20  SEMI " + p.seedCount() else "PROFILO " + (i + 1) + "  VUOTO")
            en[i] = profMode == 0 || p.used
        }
        items.add("INDIETRO"); en[3] = true
        drawMenu(g, items, menuSel, VW / 2, 70, 30, 2, en, 380)
        for (i in 0 until 3) {
            val p = profiles[i]
            if (p.used) g.img(Art.hero(p.hero, 0), 62, 70 + i * 30 - 12, false, false, 16, 24)
        }
    }

    private fun drawChar(g: Gfx) {
        WorldRender.drawSky(g, 0, tick * 0.3f, tick, 1, false)
        groundStrip(g, 0, 232, 0)
        Font.center(g, "SCEGLI IL PERSONAGGIO", VW / 2, 14, Ui.GOLD, 2, Ui.BLACK)
        val names = arrayOf("RINA", "TOKI")
        val subs = arrayOf("LA VOLPINA", "L'ORSETTO ROBOT")
        val l1 = arrayOf("DOPPIO SALTO IN ARIA", "PLANA CON LA FOGLIA")
        val l2 = arrayOf("CORRE PIU' VELOCE", "PESTONE: GIU' IN ARIA")
        val l3 = arrayOf("(PREMI SALTO DI NUOVO)", "ROMPE BLOCCHI E NEMICI")
        for (c in 0 until 2) {
            val x = 24 + c * 220
            val sel = c == charSel
            Ui.panel(g, x, 38, 212, 184, if (sel) 0xF02A2050.toInt() else 0xB01A1530.toInt(), if (sel) Ui.GOLD else Ui.DIM)
            val f = if (sel) 1 + (tick / 7) % 2 else 0
            g.img(Art.hero(c, f), x + 74, 52, false, false, 64, 96)
            Font.center(g, names[c], x + 106, 156, if (sel) Ui.GOLD else Ui.WHITE, 2, Ui.BLACK)
            Font.center(g, subs[c], x + 106, 174, Ui.GRAY, 1, Ui.BLACK)
            Font.center(g, l1[c], x + 106, 188, Ui.WHITE, 1, Ui.BLACK)
            Font.center(g, l2[c], x + 106, 199, Ui.WHITE, 1, Ui.BLACK)
            Font.center(g, l3[c], x + 106, 210, Ui.GRAY, 1, Ui.BLACK)
        }
        Font.center(g, "SINISTRA/DESTRA: SCEGLI   A: CONFERMA", VW / 2, 246, Ui.WHITE, 1, Ui.BLACK)
    }

    private fun drawMap(g: Gfx) {
        val p = prof()
        val w = p.cur / 5
        val d = Art.themes[w]
        WorldRender.drawSky(g, w, tick * 0.4f, tick, 1, false)
        groundStrip(g, w, 236, 0)
        // percorso
        for (k in 0 until 4) {
            val x0 = nodeX(k); val y0 = nodeY(w, k); val x1 = nodeX(k + 1); val y1 = nodeY(w, k + 1)
            val n = 14
            val open = p.unlocked(w * 5 + k + 1)
            for (s in 1 until n) {
                val x = x0 + (x1 - x0) * s / n; val y = y0 + (y1 - y0) * s / n
                g.fill(x - 1, y - 1, 3, 3, if (open) lighter(d.topB, 0.5f) else withAlpha(160, 0x444444))
            }
        }
        // nodi
        for (k in 0 until 5) {
            val idx = w * 5 + k
            val x = nodeX(k); val y = nodeY(w, k)
            val boss = Worlds.isBoss(idx)
            val un = p.unlocked(idx)
            val col = if (p.done[idx]) Ui.GREEN else if (!un) 0xFF7A7F90.toInt() else if (boss) Ui.RED else Ui.GOLD
            val r = if (boss) 14 else 11
            Ui.disc(g, x, y, r + 2, 0xFF1A1530.toInt())
            Ui.disc(g, x, y, r, col)
            Font.center(g, "" + (k + 1), x, y - 3, if (un) 0xFF1A1530.toInt() else Ui.DIM)
            if (boss) g.img(Art.theme(w).boss[(tick / 20) % 2], x - 16, y - 40, false, false, 32, 29)
            if (p.seed[idx]) g.img(Art.seedIcon, x - 4, y + r + 3)
            if (idx == p.cur) {
                val bob = (sin(tick * 0.15f) * 2f).toInt()
                val fr = if ((tick / 10) % 2 == 0) 0 else 3
                g.img(Art.hero(p.hero, fr), x - 8, y - 38 + bob - (if (boss) 30 else 0), false, false, 16, 24)
                Ui.ring(g, x, y, r + 4 + (tick / 6) % 3, Ui.WHITE)
            }
        }
        // frecce per cambiare mondo
        if (w > 0) Font.text(g, "<", 8, 126, Ui.WHITE, 3, Ui.BLACK)
        if (w < 3 && p.unlocked((w + 1) * 5)) Font.text(g, ">", VW - 22, 126, Ui.WHITE, 3, Ui.BLACK)
        // intestazione
        Ui.panel(g, VW / 2 - 130, 6, 260, 24)
        Font.center(g, "MONDO " + (w + 1) + ": " + Worlds.names[w], VW / 2, 14, Ui.GOLD, 1, Ui.BLACK)
        Font.text(g, "MENU", 6, 8, Ui.WHITE, 1, Ui.BLACK)
        // pannello livello
        Ui.panel(g, 30, 200, VW - 60, 54)
        val idx = p.cur
        Font.center(g, "" + (w + 1) + "-" + (idx % 5 + 1) + " " + Worlds.levelNames[idx].uppercase(), VW / 2, 208, Ui.WHITE, 2, Ui.BLACK)
        Font.text(g, "SEMI " + p.seedCount() + "/20", 42, 232, Ui.GOLD, 1, Ui.BLACK)
        g.img(Art.coinIcon, 150, 230)
        Font.text(g, "x" + p.coins, 162, 232, Ui.WHITE, 1, Ui.BLACK)
        Font.text(g, "A: GIOCA   B: MENU", VW - 42 - Font.width("A: GIOCA   B: MENU"), 232, Ui.GRAY, 1, Ui.BLACK)
        if (p.done[idx]) Font.text(g, "COMPLETATO", VW - 42 - Font.width("COMPLETATO"), 218, Ui.GREEN, 1, Ui.BLACK)
    }

    private fun drawPlay(g: Gfx) {
        val w = world ?: return
        WorldRender.draw(g, w, levelLabel())
        if (paused) {
            g.fill(0, 0, VW, VH, withAlpha(150, 0x000000))
            Ui.panel(g, VW / 2 - 110, 60, 220, 140)
            Font.center(g, "PAUSA", VW / 2, 74, Ui.GOLD, 2, Ui.BLACK)
            val last = when (mode) { Mode.STORY -> "ESCI SULLA MAPPA"; Mode.CUSTOM -> "ESCI"; else -> "TORNA ALL'EDITOR" }
            drawMenu(g, listOf("RIPRENDI", "RICOMINCIA", last), menuSel, VW / 2, 104, 26, 2, null, 190)
        } else drawControls(g)
    }

    private fun drawControls(g: Gfx) {
        val w = world
        for (i in tBtns.indices) {
            val b = tBtns[i]
            if (!opts.controls && b.btn != Btn.START) continue
            val pressed = pressedBtn.contains(i)
            val base = opts.opacity * 16 + 16
            if (b.btn == Btn.START) {
                Ui.disc(g, b.cx, b.cy, b.r, withAlpha(min(255, base + (if (pressed) 60 else 0)), 0x202040))
                g.fill(b.cx - 4, b.cy - 5, 3, 10, withAlpha(220, 0xFFFFFF)); g.fill(b.cx + 1, b.cy - 5, 3, 10, withAlpha(220, 0xFFFFFF))
                continue
            }
            val col = when (b.btn) { Btn.A -> 0x3CCB6A; Btn.B -> 0xE8A33C; else -> 0xFFFFFF }
            Ui.disc(g, b.cx, b.cy, b.r + 1, withAlpha(min(255, base / 2 + 20), 0x000000))
            Ui.disc(g, b.cx, b.cy, b.r - 1, withAlpha(min(255, base + (if (pressed) 70 else 0)), col))
            val lab = if (b.btn == Btn.B && w != null && w.hasFlower) "*" else b.label
            Font.center(g, lab, b.cx, b.cy - 3, withAlpha(min(255, base + 90), 0xFFFFFF), if (b.r > 20) 2 else 1)
        }
    }

    private fun drawResult(g: Gfx) {
        WorldRender.drawSky(g, if (mode == Mode.STORY) playLevel / 5 else (playBase?.theme ?: 0), tick * 0.5f, tick, 1, false)
        groundStrip(g, if (mode == Mode.STORY) playLevel / 5 else (playBase?.theme ?: 0), 232, 0)
        Ui.panel(g, 60, 24, VW - 120, 206)
        Font.center(g, "LIVELLO COMPLETATO!", VW / 2, 38, Ui.GOLD, 2, Ui.BLACK)
        Font.center(g, levelLabel(), VW / 2, 62, Ui.WHITE, 1, Ui.BLACK)
        val hero = if (mode == Mode.STORY) prof().hero else 0
        g.img(Art.hero(hero, 3), VW / 2 - 24, 80 - ((resT % 40) / 4), false, false, 48, 72)
        Font.center(g, "MONETE: " + resCoins, VW / 2, 164, Ui.WHITE, 1, Ui.BLACK)
        Font.center(g, "TEMPO RIMASTO: " + resSec, VW / 2, 178, Ui.WHITE, 1, Ui.BLACK)
        Font.center(g, if (resSeed) "SEME MERAVIGLIA: TROVATO!" else "SEME MERAVIGLIA: NON TROVATO", VW / 2, 192, if (resSeed) Ui.PINK else Ui.GRAY, 1, Ui.BLACK)
        if (resNewWorld >= 0) Font.center(g, "MONDO " + (resNewWorld + 1) + " SBLOCCATO!", VW / 2, 206, Ui.GREEN, 1, Ui.BLACK)
        if (resT > 40 && (resT / 20) % 2 == 0) Font.center(g, "PREMI A PER CONTINUARE", VW / 2, 218, Ui.GOLD, 1, Ui.BLACK)
    }

    private fun drawOptions(g: Gfx) {
        WorldRender.drawSky(g, 0, tick * 0.3f, tick, 1, false)
        groundStrip(g, 0, 232, 0)
        Ui.panel(g, 50, 14, VW - 100, 222)
        Font.center(g, "OPZIONI", VW / 2, 24, Ui.GOLD, 2, Ui.BLACK)
        val sizes = arrayOf("PICCOLI", "MEDI", "GRANDI")
        val items = listOf(
            "MUSICA  " + bar(opts.music),
            "EFFETTI  " + bar(opts.sfx),
            "COMANDI A SCHERMO  " + (if (opts.controls) "SI" else "NO"),
            "OPACITA COMANDI  " + bar(opts.opacity),
            "COMANDI  " + sizes[opts.size],
            "VIBRAZIONE  " + (if (opts.vibrate) "SI" else "NO"),
            "INDIETRO"
        )
        drawMenu(g, items, menuSel, VW / 2, 50, 26, 1, null, 360)
        Font.center(g, "TASTIERA: FRECCE/WASD  Z=SALTA  X=CORRI/SPARA", VW / 2, 218, Ui.GRAY, 1, Ui.BLACK)
    }

    private fun bar(v: Int): String {
        val sb = StringBuilder()
        for (i in 1..10) sb.append(if (i <= v) '#' else '_')
        return sb.toString()
    }

    private fun drawEdMenu(g: Gfx) {
        WorldRender.drawSky(g, 2, tick * 0.3f, tick, 1, false)
        groundStrip(g, 2, 232, 0)
        Ui.panel(g, 60, 24, VW - 120, 200)
        Font.center(g, "EDITOR DI LIVELLI", VW / 2, 38, Ui.GOLD, 2, Ui.BLACK)
        Font.center(g, "CREA I TUOI LIVELLI E GIOCALI!", VW / 2, 62, Ui.WHITE, 1, Ui.BLACK)
        drawMenu(g, listOf("NUOVO LIVELLO", "I MIEI LIVELLI", "IMPORTA DAGLI APPUNTI", "INDIETRO"), menuSel, VW / 2, 92, 28, 2, null, 330)
    }

    private fun drawMyLevels(g: Gfx) {
        WorldRender.drawSky(g, 2, tick * 0.3f, tick, 1, false)
        groundStrip(g, 2, 232, 0)
        Ui.panel(g, 40, 14, VW - 80, 226)
        Font.center(g, "I MIEI LIVELLI", VW / 2, 24, Ui.GOLD, 2, Ui.BLACK)
        if (myKeys.isEmpty()) {
            Font.center(g, "NESSUN LIVELLO SALVATO", VW / 2, 110, Ui.WHITE, 1, Ui.BLACK)
            Font.center(g, "CREANE UNO CON L'EDITOR!", VW / 2, 126, Ui.GRAY, 1, Ui.BLACK)
            return
        }
        if (mySub) {
            Font.center(g, levelName(myKeys[subReturn]).uppercase(), VW / 2, 56, Ui.WHITE, 2, Ui.BLACK)
            drawMenu(g, listOf("GIOCA", "MODIFICA", "CONDIVIDI", if (myConfirmDelete) "SICURO? ELIMINA" else "ELIMINA", "INDIETRO"), menuSel, VW / 2, 90, 26, 2, null, 260)
            return
        }
        val rows = ArrayList<String>()
        val last = min(myKeys.size, myScroll + 7)
        for (i in myScroll until last) rows.add(levelName(myKeys[i]).uppercase())
        drawMenu(g, rows, menuSel - myScroll, VW / 2, 54, 26, 1, null, 380)
        // la lista disegnata e' una finestra: gli indici dei tocchi vanno traslati
        Font.center(g, "" + (menuSel + 1) + "/" + myKeys.size, VW / 2, 228, Ui.GRAY, 1, Ui.BLACK)
    }

    private fun drawEnding(g: Gfx) {
        WorldRender.drawSky(g, 0, tick * 0.8f, tick, 1, false)
        groundStrip(g, 0, 226, tick)
        val p = prof()
        val y = 250 - endScroll / 2
        val lines = listOf(
            "COMPLIMENTI!", "", "HAI COMPLETATO", "PIXEL BLOOM", "",
            "SEMI MERAVIGLIA: " + p.seedCount() + "/20", "MONETE: " + p.coins, "",
            "UN GIOCO FATTO CON CLAUDE", "", "GRAZIE PER AVER GIOCATO!"
        )
        for (i in lines.indices) {
            val yy = y + i * 18
            if (yy > -20 && yy < VH) Font.center(g, lines[i], VW / 2, yy, if (i < 2) Ui.GOLD else Ui.WHITE, if (i < 2) 2 else 1, Ui.BLACK)
        }
        g.img(Art.hero(p.hero, 1 + (tick / 8) % 2), 100, 156, false, false, 48, 72)
        g.img(Art.hero(1 - p.hero, 1 + (tick / 8) % 2), VW - 148, 156, true, false, 48, 72)
        if (endScroll > 120 && (tick / 25) % 2 == 0) Font.center(g, "PREMI A", VW / 2, 252, Ui.GOLD, 1, Ui.BLACK)
    }
}

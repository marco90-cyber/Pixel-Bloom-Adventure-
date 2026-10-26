package com.marco.pixelbloom

/** Pulsanti logici (tastiera, controller e comandi a schermo finiscono tutti qui). */
enum class Btn { LEFT, RIGHT, UP, DOWN, A, B, L, R, START, BACK }

class Input {
    private val mask = IntArray(Btn.values().size)
    private val held = BooleanArray(Btn.values().size)
    private val pressed = BooleanArray(Btn.values().size)

    /** `src` identifica la sorgente (1 tastiera/controller, 2 tocco) cosi' piu' sorgenti non si pestano i piedi. */
    fun set(b: Btn, down: Boolean, src: Int = 1) {
        val i = b.ordinal
        mask[i] = if (down) mask[i] or src else mask[i] and src.inv()
        val nh = mask[i] != 0
        if (nh && !held[i]) pressed[i] = true
        held[i] = nh
    }

    fun h(b: Btn) = held[b.ordinal]
    fun p(b: Btn) = pressed[b.ordinal]
    fun endFrame() { java.util.Arrays.fill(pressed, false) }
    fun releaseAll() { java.util.Arrays.fill(mask, 0); java.util.Arrays.fill(held, false); java.util.Arrays.fill(pressed, false) }
}

object Sfx {
    const val JUMP = 1; const val COIN = 2; const val STOMP = 3; const val BUMP = 4; const val BREAK = 5
    const val POWER = 6; const val HURT = 7; const val DIE = 8; const val WONDER = 9; const val WIN = 10
    const val SHOOT = 11; const val SELECT = 12; const val CONFIRM = 13; const val SPRING = 14; const val POUND = 15
    const val BOSSHIT = 16; const val DJUMP = 17; const val CHECK = 18; const val ITEM = 19; const val BACK = 20
}

object Mus {
    const val NONE = 0; const val TITLE = 1; const val MAP = 2; const val W1 = 3; const val W2 = 4
    const val W3 = 5; const val W4 = 6; const val BOSS = 7; const val WONDER = 8; const val EDIT = 9; const val ENDING = 10
}

interface Sound {
    fun sfx(id: Int)
    fun music(id: Int)
    fun setVolumes(music: Float, sfx: Float)
}

interface Storage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(prefix: String): List<String>
}

interface Host {
    fun exit()
    fun share(text: String)
    fun clipboard(): String?
    fun vibrate(ms: Int)
}

/** Implementazioni "vuote" utili per i test. */
class NullSound : Sound {
    override fun sfx(id: Int) {}
    override fun music(id: Int) {}
    override fun setVolumes(music: Float, sfx: Float) {}
}

class MemStorage : Storage {
    val m = HashMap<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String) { m[key] = value }
    override fun remove(key: String) { m.remove(key) }
    override fun keys(prefix: String) = m.keys.filter { it.startsWith(prefix) }.sorted()
}

class NullHost : Host {
    var exited = false
    override fun exit() { exited = true }
    override fun share(text: String) {}
    override fun clipboard(): String? = null
    override fun vibrate(ms: Int) {}
}

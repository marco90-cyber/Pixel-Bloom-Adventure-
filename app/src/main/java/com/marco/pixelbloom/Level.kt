package com.marco.pixelbloom

/** Tipi di tile. */
object T {
    const val EMPTY = 0
    const val GROUND = 1
    const val BRICK = 2
    const val QCOIN = 3     // blocco fiore con moneta
    const val QPOWER = 4    // blocco fiore con potenziamento
    const val USED = 5
    const val STONE = 6
    const val SPIKE = 7
    const val PIPE = 8      // pilastro
    const val PLATFORM = 9  // piattaforma semisolida
    const val COIN = 10
    const val SEED = 11     // Seme Meraviglia
    const val ICE = 12
    const val SPRING = 13
    const val LAVA = 14
    const val COUNT = 15

    fun solid(id: Int): Boolean = when (id) {
        GROUND, BRICK, QCOIN, QPOWER, USED, STONE, PIPE, ICE, SPRING -> true
        else -> false
    }
}

/** Tipi di oggetti piazzabili (nemici e speciali). */
object K {
    const val START = 1
    const val GOAL = 2
    const val CHECK = 3
    const val SLIME = 10
    const val SNAIL = 11
    const val HEDGE = 12
    const val BAT = 13
    const val FROG = 14
    const val BOSS = 15
}

/** Altezza dell'asta del traguardo, in tile. */
const val POLE_H = 11

class Spawn(var type: Int, var x: Int, var y: Int)

/** Livello: griglia 17 righe x w colonne. */
class Level(var w: Int) {
    val h = 17
    var t = IntArray(w * h)
    val ents = ArrayList<Spawn>()
    var name = "SENZA NOME"
    var theme = 0
    var wonder = 1
    var bossHp = 0
    var time = 400

    fun get(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else t[y * w + x]
    fun set(x: Int, y: Int, v: Int) { if (x >= 0 && y >= 0 && x < w && y < h) t[y * w + x] = v }

    fun findEnt(type: Int): Spawn? { for (e in ents) if (e.type == type) return e; return null }

    fun entAt(x: Int, y: Int): Spawn? { for (e in ents) if (e.x == x && e.y == y) return e; return null }

    fun removeEntAt(x: Int, y: Int) { ents.removeAll { it.x == x && it.y == y } }

    fun copy(): Level {
        val l = Level(w)
        System.arraycopy(t, 0, l.t, 0, t.size)
        for (e in ents) l.ents.add(Spawn(e.type, e.x, e.y))
        l.name = name; l.theme = theme; l.wonder = wonder; l.bossHp = bossHp; l.time = time
        return l
    }

    fun resize(nw: Int) {
        val nt = IntArray(nw * h)
        val m = minOf(nw, w)
        for (y in 0 until h) for (x in 0 until m) nt[y * nw + x] = t[y * w + x]
        // se si allarga, estendi il terreno dell'ultima colonna
        if (nw > w && w > 0) for (y in 0 until h) for (x in w until nw) nt[y * nw + x] = if (t[y * w + (w - 1)] == T.GROUND) T.GROUND else 0
        t = nt; w = nw
        ents.removeAll { it.x >= nw }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("PBL1\n")
        sb.append(name.replace('\n', ' ')).append('\n')
        sb.append(theme).append(' ').append(wonder).append(' ').append(w).append(' ').append(time).append(' ').append(bossHp).append('\n')
        var i = 0
        var first = true
        while (i < t.size) {
            var j = i
            while (j < t.size && t[j] == t[i]) j++
            if (!first) sb.append(',')
            sb.append(t[i]).append('*').append(j - i)
            first = false
            i = j
        }
        sb.append('\n')
        for ((k, e) in ents.withIndex()) {
            if (k > 0) sb.append(';')
            sb.append(e.type).append(',').append(e.x).append(',').append(e.y)
        }
        sb.append('\n')
        return sb.toString()
    }

    companion object {
        fun parse(s: String): Level? {
            try {
                val lines = s.trim().split("\n")
                if (lines.size < 4 || lines[0].trim() != "PBL1") return null
                val head = lines[2].trim().split(" ")
                val w = head[2].toInt()
                if (w < 20 || w > 600) return null
                val l = Level(w)
                l.name = lines[1].trim()
                l.theme = head[0].toInt().coerceIn(0, 3)
                l.wonder = head[1].toInt().coerceIn(1, 4)
                if (head.size > 3) l.time = head[3].toInt().coerceIn(60, 999)
                if (head.size > 4) l.bossHp = head[4].toInt().coerceIn(0, 9)
                var pos = 0
                for (tok in lines[3].trim().split(",")) {
                    if (tok.isEmpty()) continue
                    val p = tok.split("*")
                    val id = p[0].toInt()
                    val n = p[1].toInt()
                    for (k in 0 until n) { if (pos < l.t.size) l.t[pos] = if (id in 0 until T.COUNT) id else 0; pos++ }
                }
                if (lines.size > 4 && lines[4].isNotBlank()) {
                    for (tok in lines[4].trim().split(";")) {
                        val p = tok.split(",")
                        if (p.size < 3) continue
                        l.ents.add(Spawn(p[0].toInt(), p[1].toInt(), p[2].toInt()))
                    }
                }
                return l
            } catch (e: Exception) {
                return null
            }
        }

        /** Livello vuoto con terreno e start/goal, per l'editor. */
        fun blank(w: Int = 100): Level {
            val l = Level(w)
            for (x in 0 until w) for (y in 13 until l.h) l.set(x, y, T.GROUND)
            l.ents.add(Spawn(K.START, 3, 12))
            l.ents.add(Spawn(K.GOAL, w - 8, 12))
            return l
        }
    }
}

/** Informazioni sui 4 mondi. */
object Worlds {
    val names = arrayOf("PRATO", "DESERTO", "GHIACCIO", "VULCANO")
    val levelNames = arrayOf(
        "Prato Fiorito", "Collina Sorridente", "Bosco Verde", "Ponte delle Libellule", "Castello di Gelatina",
        "Dune Dorate", "Oasi Misteriosa", "Canyon Rovente", "Tempesta di Sabbia", "Piramide dello Scorpione",
        "Pista di Cristallo", "Grotta Gelata", "Vetta Nevosa", "Tormenta Polare", "Fortezza dello Yeti",
        "Crateri Fumanti", "Fiume di Lava", "Rocce Infuocate", "Tunnel del Magma", "Covo del Drago"
    )
    val wonderNames = arrayOf("", "GRAVITA LUNARE", "GIGANTE", "TURBO", "PIOGGIA DI MONETE")
    fun worldOf(level: Int) = level / 5
    fun isBoss(level: Int) = level % 5 == 4
}

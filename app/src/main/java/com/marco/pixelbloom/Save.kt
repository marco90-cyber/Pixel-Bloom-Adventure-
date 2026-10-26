package com.marco.pixelbloom

/** Profilo di gioco (3 slot). */
class Profile {
    var used = false
    var hero = 0
    val done = BooleanArray(20)
    val seed = BooleanArray(20)
    var coins = 0
    var cur = 0

    fun doneCount(): Int { var n = 0; for (b in done) if (b) n++; return n }
    fun seedCount(): Int { var n = 0; for (b in seed) if (b) n++; return n }
    fun unlocked(i: Int): Boolean = i == 0 || (i in 1..19 && done[i - 1])
    fun firstOpen(): Int { for (i in 0 until 20) if (!done[i]) return i; return 19 }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("P1;").append(if (used) 1 else 0).append(';').append(hero).append(';').append(coins).append(';').append(cur).append(';')
        for (b in done) sb.append(if (b) '1' else '0')
        sb.append(';')
        for (b in seed) sb.append(if (b) '1' else '0')
        return sb.toString()
    }

    companion object {
        fun parse(s: String?): Profile {
            val p = Profile()
            if (s == null) return p
            try {
                val a = s.split(";")
                if (a.size < 7 || a[0] != "P1") return p
                p.used = a[1] == "1"
                p.hero = a[2].toInt().coerceIn(0, 1)
                p.coins = a[3].toInt().coerceAtLeast(0)
                p.cur = a[4].toInt().coerceIn(0, 19)
                for (i in 0 until 20) {
                    p.done[i] = i < a[5].length && a[5][i] == '1'
                    p.seed[i] = i < a[6].length && a[6][i] == '1'
                }
            } catch (e: Exception) {
                return Profile()
            }
            return p
        }
    }
}

class Options {
    var music = 7
    var sfx = 8
    var controls = true
    var opacity = 6
    var size = 1
    var vibrate = true

    fun serialize(): String = "O1;$music;$sfx;${if (controls) 1 else 0};$opacity;$size;${if (vibrate) 1 else 0}"

    companion object {
        fun parse(s: String?): Options {
            val o = Options()
            if (s == null) return o
            try {
                val a = s.split(";")
                if (a.size < 7 || a[0] != "O1") return o
                o.music = a[1].toInt().coerceIn(0, 10)
                o.sfx = a[2].toInt().coerceIn(0, 10)
                o.controls = a[3] == "1"
                o.opacity = a[4].toInt().coerceIn(1, 10)
                o.size = a[5].toInt().coerceIn(0, 2)
                o.vibrate = a[6] == "1"
            } catch (e: Exception) {
                return Options()
            }
            return o
        }
    }
}

object Saves {
    fun loadProfile(st: Storage, slot: Int): Profile = Profile.parse(st.get("profile$slot"))
    fun saveProfile(st: Storage, slot: Int, p: Profile) = st.put("profile$slot", p.serialize())
}

package com.marco.pixelbloom

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Nota: `midi` < 0 e' una pausa. */
class Note(val midi: Int, val steps: Int)

class Song(
    val sps: Float,              // passi (sedicesimi) al secondo
    val lead: List<Note>,
    val bass: List<Note>,
    val harm: List<Note>?,
    val drums: String,           // k = cassa, s = rullante, h = charleston, . = niente (16 passi per battuta, ripetuti)
    val leadDuty: Float = 0.25f
)

object Songs {
    private val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    fun midiOf(name: String): Int {
        if (name.startsWith("R")) return -1
        var i = 0
        val letter = name[i++]
        var m = base[letter] ?: 0
        if (i < name.length && name[i] == '#') { m += 1; i++ } else if (i < name.length && name[i] == 'b') { m -= 1; i++ }
        val oct = name.substring(i).toInt()
        return (oct + 1) * 12 + m
    }

    fun parse(s: String): List<Note> {
        val out = ArrayList<Note>()
        for (tok in s.trim().split(Regex("\\s+"))) {
            if (tok.isEmpty() || tok == "|") continue
            val p = tok.split(":")
            out.add(Note(midiOf(p[0]), p[1].toInt()))
        }
        return out
    }

    private fun rep(note: String, steps: Int, n: Int): String {
        val sb = StringBuilder()
        for (i in 0 until n) sb.append(note).append(':').append(steps).append(' ')
        return sb.toString()
    }

    /** Arpeggio veloce: per ogni accordo (note in semitoni da `root`) ripete uno schema. */
    private fun arp(roots: List<String>, shape: IntArray): String {
        val sb = StringBuilder()
        for (r in roots) {
            val m = midiOf(r)
            for (rep in 0 until 2) for (s in shape) {
                val mm = m + s
                sb.append(noteName(mm)).append(":1 ")
            }
        }
        return sb.toString()
    }

    private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private fun noteName(m: Int) = names[m % 12] + (m / 12 - 1)

    private val cache = HashMap<Int, Song>()

    fun get(id: Int): Song? {
        if (id == Mus.NONE) return null
        var s = cache[id]
        if (s == null) { s = build(id); cache[id] = s }
        return s
    }

    private fun build(id: Int): Song = when (id) {
        Mus.TITLE -> Song(9f,
            parse("E5:2 G5:2 C6:4 B5:2 G5:2 E5:4 D5:2 F5:2 A5:4 G5:2 E5:2 C5:4 E5:2 G5:2 C6:4 D6:2 E6:2 G5:4 F5:2 E5:2 D5:2 C5:2 D5:4 R:4"),
            parse("C3:4 G3:4 C3:4 G3:4 F3:4 C3:4 F3:4 C3:4 C3:4 G3:4 C3:4 G3:4 G3:4 G3:4 G3:2 A3:2 B3:4"),
            parse("G4:2 R:2 G4:2 R:2 G4:2 R:2 G4:2 R:2 A4:2 R:2 A4:2 R:2 A4:2 R:2 A4:2 R:2 G4:2 R:2 G4:2 R:2 G4:2 R:2 G4:2 R:2 B4:2 R:2 B4:2 R:2 B4:2 R:2 B4:2 R:2"),
            "k...h...s...h...")
        Mus.W1 -> Song(8.5f,
            parse("D5:2 G5:2 B5:2 D6:2 B5:4 G5:4 E5:2 A5:2 C6:2 E6:2 C6:4 A5:4 D5:2 G5:2 B5:2 D6:2 C6:2 B5:2 A5:2 F#5:2 G5:4 D5:4 G5:8"),
            parse("G3:4 D3:4 G3:4 D3:4 A3:4 E3:4 A3:4 E3:4 G3:4 D3:4 G3:4 D3:4 D3:4 D3:4 G3:8"),
            parse("B4:2 R:2 B4:2 R:2 B4:2 R:2 B4:2 R:2 C5:2 R:2 C5:2 R:2 C5:2 R:2 C5:2 R:2 B4:2 R:2 B4:2 R:2 B4:2 R:2 B4:2 R:2 A4:2 R:2 A4:2 R:2 B4:4 R:4"),
            "k.h.s.h.k.h.s.hh")
        Mus.W2 -> Song(7f,
            parse("D5:3 Eb5:1 F#5:4 G5:4 F#5:2 Eb5:2 D5:3 Eb5:1 F#5:4 A5:4 G5:4 A5:3 Bb5:1 A5:4 G5:4 F#5:2 Eb5:2 D5:8 R:4 A4:2 D5:2"),
            parse("D3:2 R:2 D3:2 R:2 D3:2 R:2 A2:2 R:2 D3:2 R:2 D3:2 R:2 D3:2 R:2 A2:2 R:2 G3:2 R:2 G3:2 R:2 A2:2 R:2 A2:2 R:2 D3:4 D3:4 A2:4 D3:4"),
            null,
            "k..hs..hk.h.s..h",
            0.5f)
        Mus.W3 -> Song(6.5f,
            parse("E6:2 B5:2 G#5:2 B5:2 E6:2 B5:2 G#5:2 B5:2 C#6:2 G#5:2 E5:2 G#5:2 C#6:2 G#5:2 E5:2 G#5:2 A5:2 E5:2 C#5:2 E5:2 B5:2 F#5:2 D#5:2 F#5:2 B5:4 G#5:4 E5:8"),
            parse("E3:8 E3:8 C#3:8 C#3:8 A2:8 A2:8 B2:8 E3:8"),
            parse("G#4:4 R:4 G#4:4 R:4 E4:4 R:4 E4:4 R:4 C#4:4 R:4 C#4:4 R:4 D#4:4 R:4 G#4:4 R:4"),
            "k.......h.......",
            0.125f)
        Mus.W4 -> Song(9f,
            parse("A4:2 A4:2 C5:2 A4:2 E5:4 D5:2 C5:2 A4:2 A4:2 C5:2 A4:2 G5:4 F5:2 E5:2 F5:2 F5:2 A5:2 F5:2 E5:4 D5:2 C5:2 B4:2 B4:2 D5:2 B4:2 E5:8"),
            parse(rep("A2", 2, 8) + rep("A2", 2, 8) + rep("F2", 2, 8) + rep("E2", 2, 8)),
            null,
            "k.hks.h.k.hks.hs",
            0.5f)
        Mus.BOSS -> Song(10f,
            parse("E5:2 E5:1 E5:1 G5:2 E5:2 F5:2 E5:2 D5:2 R:2 C5:2 C5:1 C5:1 E5:2 C5:2 D5:2 C5:2 B4:2 R:2 E5:2 E5:1 E5:1 G5:2 E5:2 A5:2 G5:2 F5:2 R:2 E5:4 D5:4 C5:4 B4:2 E4:2"),
            parse(rep("A2", 2, 8) + rep("A2", 2, 8) + rep("F2", 2, 8) + rep("E2", 2, 8)),
            null,
            "k.hks.hkk.hks.hs",
            0.5f)
        Mus.WONDER -> Song(14f,
            parse(arp(listOf("C5", "F5", "G5", "A5"), intArrayOf(0, 4, 7, 12, 16, 12, 7, 4))),
            parse(rep("C3", 4, 4) + rep("F3", 4, 4) + rep("G3", 4, 4) + rep("A3", 4, 4)),
            parse(arp(listOf("C4", "F4", "G4", "A4"), intArrayOf(7, 12, 16, 19, 16, 12, 7, 4))),
            "k.h.s.h.k.h.s.hh",
            0.125f)
        Mus.EDIT -> Song(6f,
            parse("G5:4 R:4 E5:4 R:4 F5:4 R:4 D5:4 R:4 E5:4 C5:4 D5:4 G4:4 C5:8 R:8"),
            parse("C3:8 C3:8 G2:8 G2:8 A2:8 F2:8 C3:16"),
            null,
            "k.......h.......",
            0.5f)
        Mus.ENDING -> Song(8f,
            parse("C5:2 E5:2 G5:4 E5:2 G5:2 C6:4 D5:2 F5:2 A5:4 F5:2 A5:2 D6:4 E5:2 G5:2 B5:4 G5:2 B5:2 E6:4 C6:4 G5:4 E5:4 C5:4"),
            parse("C3:8 C3:8 D3:8 D3:8 E3:8 E3:8 C3:8 G2:8"),
            parse("E4:4 G4:4 E4:4 G4:4 F4:4 A4:4 F4:4 A4:4 G4:4 B4:4 G4:4 B4:4 E4:4 G4:4 E4:4 G4:4"),
            "k...h...s...h...")
        else -> Song(6f, // MAP
            parse("E5:4 G5:4 A5:4 G5:4 F5:4 A5:4 C6:4 A5:4 G5:4 B5:4 D6:4 B5:4 C6:8 G5:4 E5:4"),
            parse("C3:8 G3:8 F3:8 C3:8 G3:8 D3:8 C3:16"),
            parse("C4:4 R:4 C4:4 R:4 A3:4 R:4 A3:4 R:4 B3:4 R:4 B3:4 R:4 G3:4 R:4 G3:4 R:4"),
            "k.......h.......",
            0.5f)
    }
}

/** Un singolo "tono" di un effetto sonoro. wave: 0 quadra, 1 triangolare, 2 rumore. */
class Tone(val wave: Int, val f0: Float, val f1: Float, val dur: Float, val vol: Float, val delay: Float = 0f, val duty: Float = 0.5f)

object SfxDefs {
    private fun seq(wave: Int, notes: List<Float>, each: Float, vol: Float, duty: Float = 0.5f): List<Tone> =
        notes.mapIndexed { i, f -> Tone(wave, f, f, each, vol, i * each, duty) }

    fun get(id: Int): List<Tone> = when (id) {
        Sfx.JUMP -> listOf(Tone(0, 300f, 700f, 0.14f, 0.5f, 0f, 0.25f))
        Sfx.DJUMP -> listOf(Tone(0, 500f, 1000f, 0.13f, 0.5f, 0f, 0.125f))
        Sfx.COIN -> listOf(Tone(0, 988f, 988f, 0.07f, 0.45f), Tone(0, 1319f, 1319f, 0.22f, 0.45f, 0.07f))
        Sfx.STOMP -> listOf(Tone(0, 420f, 110f, 0.12f, 0.55f), Tone(2, 0f, 0f, 0.08f, 0.3f))
        Sfx.BUMP -> listOf(Tone(0, 130f, 80f, 0.09f, 0.6f))
        Sfx.BREAK -> listOf(Tone(2, 0f, 0f, 0.22f, 0.55f), Tone(0, 200f, 60f, 0.12f, 0.3f))
        Sfx.POWER -> seq(0, listOf(523f, 659f, 784f, 1047f, 1319f), 0.07f, 0.45f, 0.25f)
        Sfx.ITEM -> listOf(Tone(0, 330f, 800f, 0.3f, 0.45f, 0f, 0.25f))
        Sfx.HURT -> listOf(Tone(0, 700f, 140f, 0.4f, 0.55f, 0f, 0.5f))
        Sfx.DIE -> seq(0, listOf(523f, 392f, 330f, 262f, 196f, 131f), 0.15f, 0.5f)
        Sfx.WONDER -> seq(0, listOf(262f, 330f, 392f, 523f, 659f, 784f, 1047f, 784f, 659f, 523f, 659f, 784f, 1047f, 1319f, 1568f), 0.05f, 0.45f, 0.125f)
        Sfx.WIN -> seq(0, listOf(523f, 659f, 784f, 1047f), 0.12f, 0.5f, 0.25f) + listOf(Tone(0, 784f, 784f, 0.15f, 0.5f, 0.48f), Tone(0, 1047f, 1047f, 0.5f, 0.5f, 0.63f))
        Sfx.SHOOT -> listOf(Tone(0, 900f, 300f, 0.12f, 0.45f, 0f, 0.125f))
        Sfx.SELECT -> listOf(Tone(0, 700f, 700f, 0.04f, 0.35f))
        Sfx.CONFIRM -> listOf(Tone(0, 700f, 700f, 0.06f, 0.4f), Tone(0, 1000f, 1000f, 0.12f, 0.4f, 0.06f))
        Sfx.BACK -> listOf(Tone(0, 500f, 300f, 0.09f, 0.4f))
        Sfx.SPRING -> listOf(Tone(0, 200f, 1000f, 0.25f, 0.5f, 0f, 0.25f))
        Sfx.POUND -> listOf(Tone(2, 0f, 0f, 0.1f, 0.5f), Tone(1, 160f, 50f, 0.2f, 0.7f))
        Sfx.BOSSHIT -> listOf(Tone(2, 0f, 0f, 0.25f, 0.55f), Tone(0, 220f, 70f, 0.25f, 0.5f))
        Sfx.CHECK -> seq(0, listOf(880f, 1175f, 1568f), 0.07f, 0.4f)
        else -> emptyList()
    }
}

/** Sintetizzatore: genera audio PCM 16 bit mono. Tutto il codice e' indipendente da Android. */
class Synth(val rate: Int = 22050) {
    @Volatile var musicVol = 0.6f
    @Volatile var sfxVol = 0.8f

    private class Voice(val notes: List<Note>) {
        var idx = 0
        var left = 0
        var freq = 0f
        var phase = 0f
        var nt = 0
    }

    private class ActiveSfx(val tones: List<Tone>) {
        var t = 0
        val phases = FloatArray(tones.size)
        var total = 0
        init { for (x in tones) total = maxOf(total, ((x.delay + x.dur) * 44100f).toInt()) }
    }

    private var song: Song? = null
    private var vLead: Voice? = null
    private var vBass: Voice? = null
    private var vHarm: Voice? = null
    private var stepSamples = 1
    private var stepPos = 0
    private var stepIdx = 0
    private var drumT = 100000
    private var drumKind = '.'
    private var noise = 0x1234
    private val sfxList = ArrayList<ActiveSfx>()
    private val lock = Any()

    fun music(id: Int) {
        synchronized(lock) {
            val s = Songs.get(id)
            song = s
            if (s == null) { vLead = null; vBass = null; vHarm = null; return }
            vLead = Voice(s.lead); vBass = Voice(s.bass); vHarm = s.harm?.let { Voice(it) }
            stepSamples = (rate / s.sps).toInt()
            stepPos = stepSamples
            stepIdx = -1
        }
    }

    fun sfx(id: Int) {
        val tones = SfxDefs.get(id)
        if (tones.isEmpty()) return
        synchronized(lock) {
            if (sfxList.size > 8) sfxList.removeAt(0)
            sfxList.add(ActiveSfx(tones))
        }
    }

    private fun nextNoise(): Float {
        var x = noise
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        noise = x
        return ((x and 0xFFFF) / 32768f) - 1f
    }

    private fun freqOf(m: Int) = (440.0 * 2.0.pow((m - 69) / 12.0)).toFloat()

    private fun advance(v: Voice?) {
        if (v == null) return
        if (v.left <= 0) {
            val n = v.notes[v.idx % v.notes.size]
            v.idx++
            v.left = n.steps
            v.freq = if (n.midi < 0) 0f else freqOf(n.midi)
            v.nt = 0
        }
        v.left--
    }

    private fun osc(v: Voice, wave: Int, duty: Float): Float {
        if (v.freq <= 0f) return 0f
        v.phase += v.freq / rate
        if (v.phase >= 1f) v.phase -= 1f
        return when (wave) {
            0 -> if (v.phase < duty) 1f else -1f
            else -> { val x = v.phase; (if (x < 0.5f) x * 4f - 1f else 3f - x * 4f) }
        }
    }

    private fun envOf(v: Voice): Float {
        val a = v.nt / (0.3f * rate)
        return 0.45f + 0.55f * (1f - minOf(1f, a))
    }

    fun render(out: ShortArray, n: Int) {
        synchronized(lock) {
            val s = song
            for (i in 0 until n) {
                var mix = 0f
                if (s != null) {
                    if (stepPos >= stepSamples) {
                        stepPos = 0
                        stepIdx++
                        advance(vLead); advance(vBass); advance(vHarm)
                        val ch = s.drums[stepIdx % s.drums.length]
                        if (ch != '.') { drumKind = ch; drumT = 0 }
                    }
                    stepPos++
                    var m = 0f
                    val l = vLead; val b = vBass; val h = vHarm
                    if (l != null) { m += osc(l, 0, s.leadDuty) * 0.16f * envOf(l); l.nt++ }
                    if (b != null) { m += osc(b, 1, 0.5f) * 0.26f; b.nt++ }
                    if (h != null) { m += osc(h, 0, 0.125f) * 0.07f * envOf(h); h.nt++ }
                    // batteria
                    if (drumT < rate / 4) {
                        val t = drumT / rate.toFloat()
                        when (drumKind) {
                            'k' -> m += sin(2.0 * PI * (50.0 + 110.0 * exp(-t * 28.0)) * t).toFloat() * 0.3f * exp(-t * 14f)
                            's' -> m += nextNoise() * 0.14f * exp(-t * 22f)
                            'h' -> m += nextNoise() * 0.06f * exp(-t * 70f)
                        }
                        drumT++
                    }
                    mix += m * musicVol
                }
                // effetti
                var sm = 0f
                var k = 0
                while (k < sfxList.size) {
                    val a = sfxList[k]
                    val tt = a.t / rate.toFloat()
                    for (j in a.tones.indices) {
                        val x = a.tones[j]
                        val lt = tt - x.delay
                        if (lt < 0f || lt > x.dur) continue
                        val p = lt / x.dur
                        val env = (1f - p) * minOf(1f, lt * 400f)
                        val v: Float
                        if (x.wave == 2) v = nextNoise()
                        else {
                            val f = x.f0 + (x.f1 - x.f0) * p
                            a.phases[j] += f / rate
                            if (a.phases[j] >= 1f) a.phases[j] -= 1f
                            val ph = a.phases[j]
                            v = if (x.wave == 0) (if (ph < x.duty) 1f else -1f) else (if (ph < 0.5f) ph * 4f - 1f else 3f - ph * 4f)
                        }
                        sm += v * x.vol * env * 0.35f
                    }
                    a.t++
                    if (a.t.toFloat() / rate > a.total / 44100f + 0.02f) sfxList.removeAt(k) else k++
                }
                mix += sm * sfxVol
                val v = (mix * 30000f).toInt().coerceIn(-32768, 32767)
                out[i] = v.toShort()
            }
        }
    }
}

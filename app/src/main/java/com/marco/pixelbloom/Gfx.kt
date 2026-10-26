package com.marco.pixelbloom

/** Risoluzione virtuale del gioco (alta definizione pixel art, 16:9). */
const val VW = 480
const val VH = 270
const val TS = 16

/** Immagine in pixel ARGB. `handle` serve al backend grafico per tenere in cache la bitmap. */
class Img(@JvmField val w: Int, @JvmField val h: Int, @JvmField val px: IntArray) {
    @JvmField var handle: Any? = null
}

/** Interfaccia di disegno minimale: l'implementazione Android disegna su una bitmap 480x270. */
interface Gfx {
    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int)
    fun img(i: Img, x: Int, y: Int, flipX: Boolean, flipY: Boolean, dw: Int, dh: Int)
}

fun Gfx.img(i: Img, x: Int, y: Int) = img(i, x, y, false, false, i.w, i.h)
fun Gfx.img(i: Img, x: Int, y: Int, flipX: Boolean) = img(i, x, y, flipX, false, i.w, i.h)

fun rgb(c: Int): Int = c or 0xFF000000.toInt()
fun withAlpha(a: Int, c: Int): Int = ((a and 0xFF) shl 24) or (c and 0xFFFFFF)

fun mixColor(a: Int, b: Int, t: Float): Int {
    val tt = if (t < 0f) 0f else if (t > 1f) 1f else t
    val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
    val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
    val r = (ar + (br - ar) * tt).toInt()
    val g = (ag + (bg - ag) * tt).toInt()
    val bl = (ab + (bb - ab) * tt).toInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
}

fun darker(c: Int, t: Float = 0.25f) = mixColor(c, 0x000000, t)
fun lighter(c: Int, t: Float = 0.25f) = mixColor(c, 0xFFFFFF, t)

/** Piccolo pennello per costruire sprite in codice. */
class Pix(val w: Int, val h: Int) {
    val px = IntArray(w * h)
    fun set(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = c }
    fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) px[y * w + x] else 0
    fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
        for (j in y until y + rh) for (i in x until x + rw) set(i, j, c)
    }
    /** Rettangolo con contorno di 1 pixel. */
    fun rectO(x: Int, y: Int, rw: Int, rh: Int, fill: Int, outline: Int) {
        rect(x, y, rw, rh, outline)
        if (rw > 2 && rh > 2) rect(x + 1, y + 1, rw - 2, rh - 2, fill)
    }
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int) {
        val x0 = (cx - rx - 1).toInt(); val x1 = (cx + rx + 1).toInt()
        val y0 = (cy - ry - 1).toInt(); val y1 = (cy + ry + 1).toInt()
        for (y in y0..y1) for (x in x0..x1) {
            val dx = (x + 0.5f - cx) / rx
            val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) set(x, y, c)
        }
    }
    /** Ellisse piena con contorno. */
    fun ellipseO(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int, outline: Int) {
        ellipse(cx, cy, rx + 1f, ry + 1f, outline)
        ellipse(cx, cy, rx, ry, fill)
    }
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = Math.abs(x1 - x0); val dy = -Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }
    fun tri(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, c: Int) {
        val minX = minOf(x0, x1, x2); val maxX = maxOf(x0, x1, x2)
        val minY = minOf(y0, y1, y2); val maxY = maxOf(y0, y1, y2)
        for (y in minY..maxY) for (x in minX..maxX) {
            val d1 = (x - x1) * (y0 - y1) - (x0 - x1) * (y - y1)
            val d2 = (x - x2) * (y1 - y2) - (x1 - x2) * (y - y2)
            val d3 = (x - x0) * (y2 - y0) - (x2 - x0) * (y - y0)
            val neg = d1 < 0 || d2 < 0 || d3 < 0
            val pos = d1 > 0 || d2 > 0 || d3 > 0
            if (!(neg && pos)) set(x, y, c)
        }
    }
    fun toImg() = Img(w, h, px)
}

/** Costruisce uno sprite da righe di testo e una tavolozza (il carattere '.' e' trasparente). */
fun spriteFrom(rows: List<String>, pal: Map<Char, Int>): Img {
    val h = rows.size
    var w = 0
    for (r in rows) if (r.length > w) w = r.length
    val p = Pix(w, h)
    for (y in 0 until h) {
        val r = rows[y]
        for (x in 0 until w) {
            val ch = if (x < r.length) r[x] else '.'
            val c = pal[ch]
            if (c != null) p.px[y * w + x] = c
        }
    }
    return p.toImg()
}

/** Numero pseudo-casuale deterministico (per texture). */
fun hash2(x: Int, y: Int, s: Int): Int {
    var h = x * 374761393 + y * 668265263 + s * 982451653
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)) and 0x7FFFFFFF
}

/** Font pixel 5x7 (solo maiuscolo, con le lettere accentate italiane). */
object Font {
    private const val SRC = """A 01110/10001/10001/11111/10001/10001/10001
B 11110/10001/10001/11110/10001/10001/11110
C 01110/10001/10000/10000/10000/10001/01110
D 11110/10001/10001/10001/10001/10001/11110
E 11111/10000/10000/11110/10000/10000/11111
F 11111/10000/10000/11110/10000/10000/10000
G 01110/10001/10000/10111/10001/10001/01111
H 10001/10001/10001/11111/10001/10001/10001
I 01110/00100/00100/00100/00100/00100/01110
J 00111/00010/00010/00010/00010/10010/01100
K 10001/10010/10100/11000/10100/10010/10001
L 10000/10000/10000/10000/10000/10000/11111
M 10001/11011/10101/10101/10001/10001/10001
N 10001/11001/10101/10011/10001/10001/10001
O 01110/10001/10001/10001/10001/10001/01110
P 11110/10001/10001/11110/10000/10000/10000
Q 01110/10001/10001/10001/10101/10010/01101
R 11110/10001/10001/11110/10100/10010/10001
S 01111/10000/10000/01110/00001/00001/11110
T 11111/00100/00100/00100/00100/00100/00100
U 10001/10001/10001/10001/10001/10001/01110
V 10001/10001/10001/10001/10001/01010/00100
W 10001/10001/10001/10101/10101/11011/10001
X 10001/10001/01010/00100/01010/10001/10001
Y 10001/10001/01010/00100/00100/00100/00100
Z 11111/00001/00010/00100/01000/10000/11111
0 01110/10001/10011/10101/11001/10001/01110
1 00100/01100/00100/00100/00100/00100/01110
2 01110/10001/00001/00010/00100/01000/11111
3 11110/00001/00001/01110/00001/00001/11110
4 00010/00110/01010/10010/11111/00010/00010
5 11111/10000/11110/00001/00001/10001/01110
6 00110/01000/10000/11110/10001/10001/01110
7 11111/00001/00010/00100/01000/01000/01000
8 01110/10001/10001/01110/10001/10001/01110
9 01110/10001/10001/01111/00001/00010/01100
. 00000/00000/00000/00000/00000/01100/01100
, 00000/00000/00000/00000/01100/00100/01000
: 00000/01100/01100/00000/01100/01100/00000
! 00100/00100/00100/00100/00100/00000/00100
? 01110/10001/00001/00010/00100/00000/00100
- 00000/00000/00000/11111/00000/00000/00000
+ 00000/00100/00100/11111/00100/00100/00000
/ 00001/00010/00010/00100/01000/01000/10000
( 00010/00100/01000/01000/01000/00100/00010
) 01000/00100/00010/00010/00010/00100/01000
% 11001/11010/00010/00100/01000/01011/10011
' 00100/00100/01000/00000/00000/00000/00000
* 00000/10101/01110/11111/01110/10101/00000
= 00000/00000/11111/00000/11111/00000/00000
< 00010/00100/01000/10000/01000/00100/00010
> 01000/00100/00010/00001/00010/00100/01000
_ 00000/00000/00000/00000/00000/00000/11111
^ 01010/11111/11111/11111/01110/00100/00000
# 00000/01110/11111/11111/11111/01110/00000"""

    private val glyphs = HashMap<Char, List<String>>()
    private val cache = HashMap<Long, Img>()

    init {
        for (line in SRC.split("\n")) {
            if (line.length < 3) continue
            glyphs[line[0]] = line.substring(2).trim().split("/")
        }
    }

    private fun glyph(ch: Char, color: Int): Img? {
        var c = ch.uppercaseChar()
        var acc = 0
        when (c) {
            'À' -> { c = 'A'; acc = 1 }
            'È' -> { c = 'E'; acc = 1 }
            'É' -> { c = 'E'; acc = 2 }
            'Ì' -> { c = 'I'; acc = 1 }
            'Ò' -> { c = 'O'; acc = 1 }
            'Ù' -> { c = 'U'; acc = 1 }
        }
        val rows = glyphs[c] ?: return null
        val key = (c.code.toLong() shl 40) or (acc.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
        val hit = cache[key]
        if (hit != null) return hit
        val p = Pix(5, 9)
        for (y in 0 until 7) for (x in 0 until 5) if (rows[y][x] == '1') p.set(x, y + 2, color)
        if (acc == 1) { p.set(1, 0, color); p.set(2, 1, color) }
        if (acc == 2) { p.set(3, 0, color); p.set(2, 1, color) }
        val im = p.toImg()
        cache[key] = im
        return im
    }

    fun width(s: String, scale: Int = 1): Int = if (s.isEmpty()) 0 else (s.length * 6 - 1) * scale

    fun text(g: Gfx, s: String, x: Int, y: Int, color: Int, scale: Int = 1, shadow: Int = 0) {
        if (shadow != 0) draw(g, s, x + scale, y + scale, shadow, scale)
        draw(g, s, x, y, color, scale)
    }

    fun center(g: Gfx, s: String, cx: Int, y: Int, color: Int, scale: Int = 1, shadow: Int = 0) {
        text(g, s, cx - width(s, scale) / 2, y, color, scale, shadow)
    }

    private fun draw(g: Gfx, s: String, x: Int, y: Int, color: Int, scale: Int) {
        var cx = x
        for (ch in s) {
            if (ch != ' ') {
                val gi = glyph(ch, color)
                if (gi != null) g.img(gi, cx, y - 2 * scale, false, false, 5 * scale, 9 * scale)
            }
            cx += 6 * scale
        }
    }
}

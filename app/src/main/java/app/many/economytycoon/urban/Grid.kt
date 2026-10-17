package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.mulDiv

/** A grid position; x grows eastward and y southward. */
data class Cell(val x: Int, val y: Int) : Comparable<Cell> {
    override fun compareTo(other: Cell): Int {
        val c = y.compareTo(other.y)
        return if (c != 0) c else x.compareTo(other.x)
    }

    fun manhattan(other: Cell): Int = Math.abs(x - other.x) + Math.abs(y - other.y)
}

/**
 * A dense integer grid in row-major order. All urban surfaces (zoning, pollution, land value ...) are grids of Long so
 * that every computation is exact and independent of platform. Values mean whatever the owner says; most surfaces use
 * permille scores (0..1000) or raw money per square metre.
 */
class Grid(val width: Int, val height: Int, private val cells: LongArray) {
    init {
        require(width > 0 && height > 0) { "grid must be non-empty" }
        require(cells.size == width * height) { "grid data does not match its size" }
    }

    constructor(width: Int, height: Int, fill: Long = 0L) : this(width, height, LongArray(width * height) { fill })

    val size: Int get() = cells.size

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun index(x: Int, y: Int): Int {
        require(inBounds(x, y)) { "cell $x,$y outside ${width}x$height" }
        return y * width + x
    }

    operator fun get(x: Int, y: Int): Long = cells[index(x, y)]
    operator fun get(c: Cell): Long = get(c.x, c.y)
    fun at(i: Int): Long = cells[i]
    fun cellAt(i: Int): Cell = Cell(i % width, i / width)

    fun getOrDefault(x: Int, y: Int, default: Long): Long = if (inBounds(x, y)) cells[y * width + x] else default

    operator fun set(x: Int, y: Int, v: Long) { cells[index(x, y)] = v }
    operator fun set(c: Cell, v: Long) = set(c.x, c.y, v)

    fun copy() = Grid(width, height, cells.copyOf())
    fun toLongArray(): LongArray = cells.copyOf()

    fun sum(): Long { var s = 0L; for (v in cells) s = Math.addExact(s, v); return s }
    fun max(): Long = cells.max()
    fun min(): Long = cells.min()
    fun mean(): Long = Math.floorDiv(sum(), cells.size.toLong())

    inline fun mapIndexed(transform: (Int, Long) -> Long): Grid {
        val out = LongArray(size)
        for (i in 0 until size) out[i] = transform(i, at(i))
        return Grid(width, height, out)
    }

    fun map(transform: (Long) -> Long): Grid = mapIndexed { _, v -> transform(v) }

    fun combine(other: Grid, op: (Long, Long) -> Long): Grid {
        require(width == other.width && height == other.height) { "grid sizes differ" }
        return mapIndexed { i, v -> op(v, other.at(i)) }
    }

    fun coerceIn(lo: Long, hi: Long): Grid = map { it.coerceIn(lo, hi) }

    /** Rescales so that the lowest value becomes 0 and the highest [top]; a flat grid becomes all zero. */
    fun normalized(top: Long = 1000L): Grid {
        val lo = min()
        val hi = max()
        if (hi == lo) return map { 0L }
        return map { mulDiv(it - lo, top, hi - lo) }
    }

    /**
     * Sums every value weighted by a kernel that depends on the Manhattan distance to the cell:
     * `result[c] = sum over d of kernel[d] * (values at distance d)`, divided by [divisor]. Distances beyond the kernel
     * contribute nothing, so the reach of a source is the kernel length.
     */
    fun spread(kernel: LongArray, divisor: Long = Scale.PPM): Grid {
        require(kernel.isNotEmpty()) { "kernel must not be empty" }
        require(divisor != 0L) { "divisor must not be zero" }
        val reach = kernel.size - 1
        val out = LongArray(size)
        for (y in 0 until height) for (x in 0 until width) {
            var acc = 0L
            for (dy in -reach..reach) {
                val yy = y + dy
                if (yy < 0 || yy >= height) continue
                val rest = reach - Math.abs(dy)
                for (dx in -rest..rest) {
                    val xx = x + dx
                    if (xx < 0 || xx >= width) continue
                    val v = cells[yy * width + xx]
                    if (v != 0L) acc += mulDiv(v, kernel[Math.abs(dx) + Math.abs(dy)], divisor)
                }
            }
            out[y * width + x] = acc
        }
        return Grid(width, height, out)
    }

    /** Average of a cell and its four neighbours (edges count only the neighbours that exist). */
    fun smoothed(): Grid {
        val out = LongArray(size)
        for (y in 0 until height) for (x in 0 until width) {
            var acc = cells[y * width + x]
            var n = 1L
            for ((dx, dy) in NEIGHBOURS4) {
                if (inBounds(x + dx, y + dy)) { acc += cells[(y + dy) * width + x + dx]; n++ }
            }
            out[y * width + x] = Math.floorDiv(acc, n)
        }
        return Grid(width, height, out)
    }

    /** Sum of the values inside the rectangle [x0, x1) x [y0, y1). */
    fun sumRect(x0: Int, y0: Int, x1: Int, y1: Int): Long {
        var s = 0L
        for (y in y0 until y1) for (x in x0 until x1) s += get(x, y)
        return s
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(width).putInt(height)
        for (v in cells) h.putLong(v)
    }

    override fun equals(other: Any?) = other is Grid && width == other.width && height == other.height && cells.contentEquals(other.cells)
    override fun hashCode() = 31 * (31 * width + height) + cells.contentHashCode()

    companion object {
        val NEIGHBOURS4: List<Pair<Int, Int>> = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

        /** A kernel that falls by [decayPpm] (ppm of the previous step kept) at each step, over [reach] steps. */
        fun decayKernel(decayPpm: Long, reach: Int): LongArray {
            require(reach >= 0) { "reach must not be negative" }
            val k = LongArray(reach + 1)
            k[0] = Scale.PPM
            for (d in 1..reach) k[d] = mulDiv(k[d - 1], decayPpm, Scale.PPM)
            return k
        }
    }
}

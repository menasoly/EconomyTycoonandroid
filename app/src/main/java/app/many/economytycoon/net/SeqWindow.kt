package app.many.economytycoon.net

/** How a sequence number relates to what has been seen. */
enum class SeqVerdict { FRESH, DUPLICATE, STALE, AHEAD }

/**
 * Anti-replay window over a 64-number span, the same scheme secure transports use. A number is fresh the first
 * time it is seen inside the window, a duplicate if it was seen, stale if it fell off the back, and ahead if it
 * jumps further than [maxJump] past the newest, which a well-behaved sender cannot do.
 */
class SeqWindow(private val maxJump: Long = 256L) {
    var highest: Long = 0L
        private set
    private var mask: Long = 0L

    fun check(seq: Long): SeqVerdict {
        if (seq <= 0L) return SeqVerdict.STALE
        if (seq > highest) return if (seq - highest > maxJump) SeqVerdict.AHEAD else SeqVerdict.FRESH
        val back = highest - seq
        if (back >= SIZE) return SeqVerdict.STALE
        return if ((mask ushr back.toInt()) and 1L == 1L) SeqVerdict.DUPLICATE else SeqVerdict.FRESH
    }

    /** Records [seq] if it is fresh and returns the verdict either way. */
    fun accept(seq: Long): SeqVerdict {
        val v = check(seq)
        if (v != SeqVerdict.FRESH) return v
        if (seq > highest) {
            val shift = seq - highest
            mask = if (shift >= SIZE) 0L else mask shl shift.toInt()
            mask = mask or 1L
            highest = seq
        } else {
            mask = mask or (1L shl (highest - seq).toInt())
        }
        return v
    }

    fun restore(highest: Long, mask: Long) { this.highest = highest; this.mask = mask }
    fun maskBits(): Long = mask

    companion object { const val SIZE = 64 }
}

/**
 * Token bucket in thousandths of a token so refills are exact integers. [ratePerSecond] tokens accrue each
 * second of network time up to [capacity]; taking a token fails when the bucket is empty.
 */
class RateLimiter(private val capacity: Int, private val ratePerSecond: Int) {
    init { require(capacity >= 1 && ratePerSecond >= 0) { "capacity must be positive" } }

    private var milli: Long = capacity * 1000L
    private var last: Long = Long.MIN_VALUE

    private fun refill(now: Long) {
        if (last == Long.MIN_VALUE) { last = now; return }
        if (now > last) {
            milli = minOf(capacity * 1000L, milli + (now - last) * ratePerSecond)
            last = now
        }
    }

    fun tryTake(now: Long, cost: Int = 1): Boolean {
        refill(now)
        val need = cost * 1000L
        if (milli < need) return false
        milli -= need
        return true
    }

    fun available(now: Long): Int { refill(now); return (milli / 1000L).toInt() }
}

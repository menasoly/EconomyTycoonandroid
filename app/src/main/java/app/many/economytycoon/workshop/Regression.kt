package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.session.Formatting
import java.util.zip.CRC32

/** The outcome of one run, boiled down to what a later run is compared with. */
data class BaselineEntry(
    val policy: String,
    val seed: Long,
    val days: Int,
    val end: RunEnd,
    /** Day on which every required goal was met, or -1. */
    val wonOn: Int,
    val cash: Long,
    val netWorth: Long,
    val facilities: Int,
    val employees: Long,
    val goalPpm: List<Long>,
    val softlock: Boolean,
    val stateHash: String,
) {
    val key: String get() = "$policy/$seed"

    companion object {
        fun of(m: RunMetrics) = BaselineEntry(
            m.policy, m.seed, m.days, m.end, m.allDoneDay ?: -1, m.last.cash, m.last.netWorth, m.last.facilities, m.last.employees,
            m.last.goalPpm, m.softlockDay != null, m.stateHash,
        )
    }
}

/** A recorded set of runs of one draft: the yardstick later runs are held to. */
class Baseline(val name: String, val draftHash: String, entries: List<BaselineEntry>) {
    val entries: List<BaselineEntry> = entries.sortedBy { it.key }
    fun entry(key: String): BaselineEntry? = entries.firstOrNull { it.key == key }
    override fun equals(other: Any?) = other is Baseline && other.name == name && other.draftHash == draftHash && other.entries == entries
    override fun hashCode() = name.hashCode() * 31 + entries.hashCode()
}

/** Writes baselines as bytes with a header and checksum, so they can be kept beside a scenario and checked in. */
object BaselineCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf(0x45, 0x54, 0x57, 0x42) // "ETWB"

    fun toBytes(b: Baseline): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeString(b.name); w.writeString(b.draftHash)
        w.writeVarInt(b.entries.size)
        for (e in b.entries) {
            w.writeString(e.policy); w.writeVarLong(e.seed); w.writeVarInt(e.days); w.writeVarInt(e.end.ordinal); w.writeVarInt(e.wonOn)
            w.writeVarLong(e.cash); w.writeVarLong(e.netWorth); w.writeVarInt(e.facilities); w.writeVarLong(e.employees)
            w.writeVarInt(e.goalPpm.size); for (p in e.goalPpm) w.writeVarLong(p)
            w.writeBoolean(e.softlock); w.writeString(e.stateHash)
        }
        val payload = w.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(MAGIC)
        out.writeBytes(payload)
        out.writeFixedLong(CRC32().also { it.update(payload) }.value)
        return out.toByteArray()
    }

    fun fromBytes(bytes: ByteArray): Baseline {
        if (bytes.size < MAGIC.size + 9) throw ByteReader.CorruptDataException("baseline is too short")
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw ByteReader.CorruptDataException("not a baseline file")
        val r = ByteReader(bytes, MAGIC.size)
        val payload = r.readBytes()
        if (r.readFixedLong() != CRC32().also { it.update(payload) }.value) throw ByteReader.CorruptDataException("baseline checksum does not match")
        val p = ByteReader(payload)
        val version = p.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported baseline version $version")
        val name = p.readString(); val hash = p.readString()
        val n = p.readVarInt()
        if (n < 0 || n > MAX_ENTRIES) throw ByteReader.CorruptDataException("bad entry count $n")
        val entries = List(n) {
            val policy = p.readString(); val seed = p.readVarLong(); val days = p.readVarInt()
            val end = RunEnd.values().getOrNull(p.readVarInt()) ?: throw ByteReader.CorruptDataException("unknown run end")
            val wonOn = p.readVarInt(); val cash = p.readVarLong(); val worth = p.readVarLong(); val fac = p.readVarInt(); val emp = p.readVarLong()
            val goals = p.readVarInt()
            if (goals < 0 || goals > DraftLimits.MAX_GOALS) throw ByteReader.CorruptDataException("bad goal count $goals")
            val ppm = List(goals) { p.readVarLong() }
            BaselineEntry(policy, seed, days, end, wonOn, cash, worth, fac, emp, ppm, p.readBoolean(), p.readString())
        }
        return Baseline(name, hash, entries)
    }

    private const val MAX_ENTRIES = 10_000
}

enum class DiffKind(val label: String) {
    IDENTICAL("identical"),
    /** The state hash differs but every recorded number is the same: something outside the numbers moved. */
    HASH_ONLY("hash differs, numbers agree"),
    WITHIN_TOLERANCE("within tolerance"),
    CHANGED("changed"),
    MISSING("missing from the new runs"),
    ADDED("not in the baseline"),
}

data class MetricChange(val metric: String, val before: String, val after: String, val changePpm: Long?) {
    override fun toString() = "$metric $before -> $after" + (changePpm?.let { " (${signedPercent(it)})" } ?: "")

    private fun signedPercent(ppm: Long): String {
        val abs = Math.abs(ppm)
        val whole = abs / 10_000
        val frac = abs % 10_000 / 100
        return (if (ppm < 0) "-" else "+") + "$whole." + frac.toString().padStart(2, '0') + "%"
    }
}

data class RunDiff(val key: String, val kind: DiffKind, val changes: List<MetricChange>) {
    fun lines(): List<String> = listOf("$key: ${kind.label}") + changes.map { "    $it" }
}

class RegressionReport(val baselineName: String, val draftChanged: Boolean, val diffs: List<RunDiff>) {
    val identical: Boolean get() = !draftChanged && diffs.all { it.kind == DiffKind.IDENTICAL }
    /** True when nothing changed beyond the tolerance and nothing is missing. */
    val passed: Boolean get() = diffs.all { it.kind == DiffKind.IDENTICAL || it.kind == DiffKind.HASH_ONLY || it.kind == DiffKind.WITHIN_TOLERANCE || it.kind == DiffKind.ADDED }
    fun of(kind: DiffKind) = diffs.filter { it.kind == kind }

    fun lines(): List<String> {
        val out = ArrayList<String>()
        out += "baseline '$baselineName': " + if (identical) "all ${diffs.size} runs identical" else summary()
        if (draftChanged) out += "the draft has changed since the baseline was recorded"
        for (d in diffs) if (d.kind != DiffKind.IDENTICAL) out += d.lines()
        return out
    }

    fun summary(): String = DiffKind.values().filter { k -> diffs.any { it.kind == k } }.joinToString(", ") { k -> "${diffs.count { it.kind == k }} ${k.label}" }.ifEmpty { "no runs" }
}

object Regression {
    fun capture(name: String, draft: ScenarioDraft, runs: List<RunMetrics>): Baseline =
        Baseline(name, draft.stateHash(), runs.map { BaselineEntry.of(it) })

    /**
     * Compares new runs with a baseline. Numbers that moved by no more than [tolerancePpm] of their old value count as
     * unchanged; the state hash is compared only to tell identical runs from ones that merely agree.
     */
    fun compare(baseline: Baseline, draft: ScenarioDraft, runs: List<RunMetrics>, tolerancePpm: Long = 0L): RegressionReport {
        val now = runs.map { BaselineEntry.of(it) }.associateBy { it.key }
        val diffs = ArrayList<RunDiff>()
        for (old in baseline.entries) {
            val cur = now[old.key]
            if (cur == null) { diffs += RunDiff(old.key, DiffKind.MISSING, emptyList()); continue }
            diffs += diff(old, cur, tolerancePpm)
        }
        for (cur in now.values.sortedBy { it.key }) if (baseline.entry(cur.key) == null) diffs += RunDiff(cur.key, DiffKind.ADDED, emptyList())
        return RegressionReport(baseline.name, baseline.draftHash != draft.stateHash(), diffs)
    }

    private fun diff(a: BaselineEntry, b: BaselineEntry, tol: Long): RunDiff {
        val changes = ArrayList<MetricChange>()
        var withinOnly = true
        fun number(name: String, x: Long, y: Long) {
            if (x == y) return
            val ppm = if (x == 0L) null else (y - x) * 1_000_000L / Math.abs(x)
            changes += MetricChange(name, Formatting.grouped(x), Formatting.grouped(y), ppm)
            if (ppm == null || Math.abs(ppm) > tol) withinOnly = false
        }
        fun exact(name: String, x: String, y: String) {
            if (x != y) { changes += MetricChange(name, x, y, null); withinOnly = false }
        }
        exact("outcome", a.end.label, b.end.label)
        number("days played", a.days.toLong(), b.days.toLong())
        number("won on day", a.wonOn.toLong(), b.wonOn.toLong())
        number("cash", a.cash, b.cash)
        number("net worth", a.netWorth, b.netWorth)
        number("facilities", a.facilities.toLong(), b.facilities.toLong())
        number("employees", a.employees, b.employees)
        for (i in 0 until maxOf(a.goalPpm.size, b.goalPpm.size)) {
            val x = a.goalPpm.getOrNull(i); val y = b.goalPpm.getOrNull(i)
            if (x == null || y == null) exact("goal ${i + 1}", x?.toString() ?: "none", y?.toString() ?: "none") else number("goal ${i + 1} progress", x, y)
        }
        exact("stuck", a.softlock.toString(), b.softlock.toString())
        val kind = when {
            changes.isNotEmpty() -> if (withinOnly) DiffKind.WITHIN_TOLERANCE else DiffKind.CHANGED
            a.stateHash == b.stateHash -> DiffKind.IDENTICAL
            else -> DiffKind.HASH_ONLY
        }
        return RunDiff(a.key, kind, changes)
    }
}

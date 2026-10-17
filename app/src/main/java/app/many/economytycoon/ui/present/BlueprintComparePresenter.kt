package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.BlueprintAnalysis
import app.many.economytycoon.blueprint.BlueprintDiffer
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

data class CompareRow(val label: String, val left: String, val right: String, val delta: String?, val tone: Tone)

data class CompareModel(val leftName: String, val rightName: String, val rows: List<CompareRow>, val changes: List<DiffRow>, val summary: String, val identical: Boolean)

/** Two blueprints side by side: the numbers that matter and every setting that differs. */
object BlueprintComparePresenter {
    private fun moneyRow(label: String, a: Money?, b: Money?, money: (Money) -> String, higherIsBetter: Boolean): CompareRow {
        if (a == null || b == null) return CompareRow(label, a?.let(money) ?: "-", b?.let(money) ?: "-", null, Tone.NEUTRAL)
        val d = b - a
        val good = if (higherIsBetter) d.raw > 0 else d.raw < 0
        return CompareRow(label, money(a), money(b), if (d.raw == 0L) null else (if (d.raw > 0) "+" else "-") + money(d.abs()),
            when { d.raw == 0L -> Tone.NEUTRAL; good -> Tone.GOOD; else -> Tone.WARN })
    }

    private val DEFAULT: (Money) -> String = { Formatting.money(it) }

    fun model(left: BlueprintAnalysis, right: BlueprintAnalysis, content: ContentRepository, money: (Money) -> String = DEFAULT): CompareModel {
        val diff = BlueprintDiffer(content).diff(left.effective.spec, right.effective.spec)
        val a = left.cost; val b = right.cost
        val rows = listOf(
            moneyRow("Capital", a?.capital, b?.capital, money, higherIsBetter = false),
            moneyRow("Net a day", a?.netPerDay, b?.netPerDay, money, higherIsBetter = true),
            CompareRow("Pays back", BlueprintDetailPresenter.payback(a?.paybackDays), BlueprintDetailPresenter.payback(b?.paybackDays), paybackDelta(a?.paybackDays, b?.paybackDays), paybackTone(a?.paybackDays, b?.paybackDays)),
            CompareRow("Lines", left.effective.spec.lines.size.toString(), right.effective.spec.lines.size.toString(), null, Tone.NEUTRAL),
            CompareRow("Machines", left.effective.spec.machineCount.toString(), right.effective.spec.machineCount.toString(), null, Tone.NEUTRAL),
        )
        return CompareModel(left.blueprint.name, right.blueprint.name, rows, BlueprintApplyPresenter.diffRows(diff), diff.summary, left.effective.hash == right.effective.hash)
    }

    private fun paybackDelta(a: Long?, b: Long?): String? = when {
        a == null || b == null || a == b -> null
        else -> (if (b > a) "+" else "-") + Formatting.duration(Math.abs(b - a))
    }

    private fun paybackTone(a: Long?, b: Long?): Tone = when {
        a == b -> Tone.NEUTRAL
        a == null -> Tone.GOOD
        b == null -> Tone.WARN
        b < a -> Tone.GOOD
        else -> Tone.WARN
    }
}

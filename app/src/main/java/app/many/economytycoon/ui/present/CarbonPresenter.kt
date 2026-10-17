package app.many.economytycoon.ui.present

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.carbon.AbatementKind
import app.many.economytycoon.carbon.AbatementOption
import app.many.economytycoon.carbon.AbatementStatus
import app.many.economytycoon.carbon.AccountView
import app.many.economytycoon.carbon.CarbonAdvice
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.CarbonGrade
import app.many.economytycoon.carbon.CompanyCarbonView
import app.many.economytycoon.carbon.ComplianceStatus
import app.many.economytycoon.carbon.MarketView
import app.many.economytycoon.carbon.OffsetKind
import app.many.economytycoon.carbon.OffsetStatus
import app.many.economytycoon.carbon.PendingView
import app.many.economytycoon.carbon.RegimeKind
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting

/**
 * What the carbon screens need from the running game. The session implements it once the package is wired in; tests
 * implement it with plain values, which keeps the presenters free of the session.
 */
interface CarbonDesk {
    val tick: Tick
    val company: CompanyId
    fun money(m: Money): String
    fun countryName(country: CountryId): String
    fun facilityName(facility: FacilityId): String
    fun view(): CompanyCarbonView?
    fun market(country: CountryId): MarketView?
    fun revenue(): Money

    /** Runs a command for the player's company; null on success, the reason it was refused otherwise. */
    fun submit(command: CarbonCommand): String?
}

data class CarbonAccountRow(
    val country: CountryId,
    val title: String,
    val regime: String,
    val tone: Tone,
    val facts: List<Fact>,
    val deadline: String?,
    val canBuy: Boolean,
)

data class CarbonOptionRow(
    val facility: FacilityId,
    val kind: AbatementKind,
    val title: String,
    val detail: String,
    val cost: String,
    val tone: Tone,
)

data class CarbonProjectRow(val title: String, val status: String, val detail: String, val tone: Tone)

data class CarbonOffsetRow(val kind: OffsetKind, val title: String, val held: String, val blurb: String)

data class CarbonStatementRow(val title: String, val result: String, val detail: String, val tone: Tone)

data class CarbonInsightRow(val title: String, val detail: String, val tone: Tone)

data class CarbonModel(
    val tiles: List<Tile>,
    val accounts: List<CarbonAccountRow>,
    val rating: List<Fact>,
    val options: List<CarbonOptionRow>,
    val projects: List<CarbonProjectRow>,
    val offsets: List<CarbonOffsetRow>,
    val statements: List<CarbonStatementRow>,
    val insights: List<CarbonInsightRow>,
    val empty: String?,
)

/** The company's carbon position: what it emits, what it owes, what it holds and what it could do about it. */
object CarbonPresenter {
    const val EMPTY = "No carbon policy touches your company yet. When a government prices emissions, your position will appear here."
    const val NO_VIEW = "Choose a company first."

    fun gradeTone(g: CarbonGrade): Tone = when {
        g.minScore >= 700 -> Tone.GOOD
        g.minScore >= 400 -> Tone.WARN
        else -> Tone.BAD
    }

    fun statusTone(s: ComplianceStatus): Tone = when (s) {
        ComplianceStatus.COMPLIANT, ComplianceStatus.TAXED, ComplianceStatus.LEVIED -> Tone.GOOD
        ComplianceStatus.SHORTFALL -> Tone.BAD
        ComplianceStatus.EXEMPT, ComplianceStatus.UNREGULATED -> Tone.MUTED
    }

    fun tonnes(t: Tonnes): String = Formatting.grouped(t.wholeTonnes()) + " t"

    fun percent(ppm: Long): String = Formatting.percentPpm(ppm)

    fun deadline(p: PendingView): String = when {
        p.outstanding.raw <= 0L -> "${p.year} is settled"
        p.daysLeft <= 0 -> "${p.year} falls due today"
        else -> "${p.year} falls due in ${Formatting.plural(p.daysLeft.toLong(), "day")}"
    }

    fun account(a: AccountView, desk: CarbonDesk): CarbonAccountRow {
        val facts = ArrayList<Fact>()
        facts += Fact("Emissions so far", tonnes(a.footprint), hint = "${a.daysObserved} days observed")
        if (a.kind != RegimeKind.NONE) {
            facts += Fact("Covered this year", tonnes(a.projectedCovered), hint = "Projected to the year end")
            facts += Fact("Bill at today's price", desk.money(a.projectedBill), if (a.projectedBill.raw > 0L) Tone.WARN else Tone.NEUTRAL, hint = "${desk.money(a.price)} a tonne")
        }
        if (a.kind == RegimeKind.CAP_AND_TRADE) {
            facts += Fact("Permits held", tonnes(a.permitsHeld), hint = "Cost ${desk.money(a.permitsCost)}")
            if (a.expiringNext.isPositive) facts += Fact("Lapsing next year", tonnes(a.expiringNext), Tone.WARN)
            if (a.freeThisYear.isPositive) facts += Fact("Free allocation", tonnes(a.freeThisYear), Tone.GOOD)
        }
        if (a.carry.isPositive) facts += Fact("Shortfall carried in", tonnes(a.carry), Tone.BAD)
        if (a.violations > 0) facts += Fact("Years in breach", a.violations.toString(), Tone.BAD)
        a.pending?.let { p ->
            facts += Fact("Owed for ${p.year}", tonnes(p.outstanding), if (p.outstanding.isPositive) Tone.WARN else Tone.GOOD)
            if (p.projectedShortfall.isPositive) facts += Fact("Short by", tonnes(p.projectedShortfall), Tone.BAD, "After permits and credits you hold")
        }
        val tone = when {
            a.pending?.projectedShortfall?.isPositive == true -> Tone.BAD
            a.violations > 0 -> Tone.WARN
            a.kind == RegimeKind.NONE -> Tone.MUTED
            else -> Tone.NEUTRAL
        }
        return CarbonAccountRow(a.country, desk.countryName(a.country), a.kind.label, tone, facts, a.pending?.let { deadline(it) }, a.kind == RegimeKind.CAP_AND_TRADE)
    }

    fun option(o: AbatementOption, price: Money, desk: CarbonDesk): CarbonOptionRow {
        val payback = o.paybackYears(price)
        val worth = o.worthwhileAt(price)
        val detail = buildString {
            append("Saves ${tonnes(o.annualAbatement)} a year for ${desk.money(o.capex)}; ready in ${Formatting.plural(o.buildDays.toLong(), "day")}.")
            append(if (payback == null) " The carbon price alone does not repay it." else " Repaid by the carbon price in ${Formatting.plural(payback.toLong(), "year")}.")
        }
        return CarbonOptionRow(o.facility, o.kind, "${o.kind.label} at ${desk.facilityName(o.facility)}", detail, "${desk.money(o.costPerTonne)} a tonne", if (worth) Tone.GOOD else Tone.NEUTRAL)
    }

    fun abatementTone(s: AbatementStatus): Tone = when (s) {
        AbatementStatus.BUILDING -> Tone.INFO
        AbatementStatus.OPERATING -> Tone.GOOD
        AbatementStatus.CANCELLED, AbatementStatus.RETIRED -> Tone.MUTED
    }

    fun offsetTone(s: OffsetStatus): Tone = when (s) {
        OffsetStatus.ISSUING -> Tone.GOOD
        OffsetStatus.DEVELOPMENT, OffsetStatus.VALIDATION -> Tone.INFO
        OffsetStatus.COMPLETED -> Tone.MUTED
        OffsetStatus.REJECTED -> Tone.BAD
    }

    fun projects(view: CompanyCarbonView, desk: CarbonDesk): List<CarbonProjectRow> {
        val rows = ArrayList<CarbonProjectRow>()
        for (a in view.abatement) {
            val detail = if (a.status == AbatementStatus.BUILDING) "Ready on day ${a.completesAt.value}" else "Saves ${tonnes(a.annualAbatement)} a year"
            rows += CarbonProjectRow("${a.kind.label} at ${desk.facilityName(a.facility)}", a.status.label, detail, abatementTone(a.status))
        }
        for (p in view.offsetProjects) {
            rows += CarbonProjectRow("${p.kind.label} project", p.status.label, "${tonnes(p.issued)} issued of ${tonnes(p.annual)} a year", offsetTone(p.status))
        }
        return rows
    }

    fun statement(s: app.many.economytycoon.carbon.AnnualStatement, desk: CarbonDesk): CarbonStatementRow {
        val change = s.changePpm?.let { c -> (if (c > 0) "up " else "down ") + percent(Math.abs(c)) + " on the year before" } ?: "first statement"
        val charges = if (s.charges.raw > 0L) ", charges ${desk.money(s.charges)}" else ""
        return CarbonStatementRow("${s.year} in ${desk.countryName(s.country)}", s.status.label, "${tonnes(s.footprint)} released, $change$charges", statusTone(s.status))
    }

    fun insight(i: Insight): CarbonInsightRow =
        CarbonInsightRow(i.title, i.detail, if (i.priority >= 80) Tone.BAD else if (i.priority >= 55) Tone.WARN else Tone.INFO)

    fun tiles(view: CompanyCarbonView, desk: CarbonDesk): List<Tile> {
        val shortfall = view.accounts.sumOf { it.pending?.projectedShortfall?.raw ?: 0L }
        return listOf(
            Tile("Emissions", tonnes(view.footprint), note = "This year so far"),
            Tile("Carbon bill", desk.money(view.projectedBill), if (view.projectedBill.raw > 0L) Tone.WARN else Tone.NEUTRAL, "At today's prices"),
            Tile("Rating", view.rating?.grade?.label ?: "None yet", view.rating?.let { gradeTone(it.grade) } ?: Tone.MUTED),
            Tile("Short", tonnes(Tonnes(shortfall)), if (shortfall > 0L) Tone.BAD else Tone.GOOD, if (view.hasPending) "Before the deadline" else "Nothing due"),
        )
    }

    fun build(desk: CarbonDesk): CarbonModel {
        val view = desk.view() ?: return CarbonModel(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), NO_VIEW)
        val priced = view.accounts.filter { it.kind != RegimeKind.NONE }
        val price = priced.firstOrNull()?.price ?: Money.ZERO
        val rating = view.rating?.let { r ->
            listOf(Fact("Score", "${r.score} of 1000", gradeTone(r.grade))) + r.factors.map { Fact(it.label, "${it.points} of ${it.maximum}", hint = it.note) }
        }.orEmpty()
        return CarbonModel(
            tiles(view, desk),
            view.accounts.map { account(it, desk) },
            rating,
            view.options.filter { it.annualAbatement.isPositive }.take(8).map { option(it, price, desk) },
            projects(view, desk),
            OffsetKind.values().map { k -> CarbonOffsetRow(k, k.label, tonnes(view.offsetsHeld[k] ?: Tonnes.ZERO), k.blurb) },
            view.statements.sortedByDescending { it.year }.take(6).map { statement(it, desk) },
            CarbonAdvice.insights(view, desk.revenue()).map { insight(it) },
            if (priced.isEmpty() && view.footprint.raw <= 0L) EMPTY else null,
        )
    }

    // Actions. Each parses what the player typed and returns the reason it could not go ahead, or null.

    fun wholeTonnes(text: String): Tonnes? = text.trim().toLongOrNull()?.takeIf { it > 0L }?.let { Tonnes.of(it) }

    fun buyOffsets(desk: CarbonDesk, kind: OffsetKind, text: String): String? {
        val q = wholeTonnes(text) ?: return "Enter a number of tonnes above zero."
        return desk.submit(CarbonCommand.BuyOffsets(desk.company, kind, q))
    }

    fun retireOffsets(desk: CarbonDesk, country: CountryId, kind: OffsetKind, text: String, forCompliance: Boolean): String? {
        val q = wholeTonnes(text) ?: return "Enter a number of tonnes above zero."
        return desk.submit(CarbonCommand.RetireOffsets(desk.company, country, kind, q, forCompliance))
    }

    fun startAbatement(desk: CarbonDesk, row: CarbonOptionRow): String? =
        desk.submit(CarbonCommand.StartAbatement(desk.company, row.facility, row.kind))

    fun setPolicy(desk: CarbonDesk, country: CountryId, autoOffsets: Boolean, autoBorrow: Boolean): String? =
        desk.submit(CarbonCommand.SetPolicy(desk.company, country, autoOffsets, autoBorrow))

    fun surrender(desk: CarbonDesk, country: CountryId, text: String): String? {
        val q = wholeTonnes(text) ?: return "Enter a number of tonnes above zero."
        return desk.submit(CarbonCommand.Surrender(desk.company, country, q))
    }
}

package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.risk.Claim
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Peril
import app.many.economytycoon.risk.Policy
import app.many.economytycoon.risk.PolicyStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class PolicyCard(
    val id: PolicyId,
    val title: String,
    val cover: String,
    val premium: String,
    val insured: String,
    val renews: String,
    val status: String,
    val statusTone: Tone,
    val claims: String,
    val active: Boolean,
)

data class UninsuredRow(val id: FacilityId, val name: String, val value: String, val from: String, val risk: String, val tone: Tone)

data class ClaimRow(val title: String, val when_: String, val loss: String, val paid: String, val note: String, val tone: Tone)

data class PerilChance(val peril: String, val chance: String)

data class InsuranceModel(
    val tiles: List<Tile>,
    val policies: List<PolicyCard>,
    val uninsured: List<UninsuredRow>,
    val claims: List<ClaimRow>,
    val note: String,
)

/** A priced offer shown while the player chooses cover. */
data class CoverQuote(
    val premium: String,
    val perMonthNote: String,
    val insured: String,
    val excess: String,
    val perils: String,
    val chances: List<PerilChance>,
    val problem: String?,
    val premiumMoney: Money,
)

object InsurancePresenter {
    private fun facilityName(session: GameSession, f: Facility): String =
        f.name.ifBlank { session.economy.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }

    private fun perilNames(level: CoverLevel) = level.perils.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }

    fun statusLabel(s: PolicyStatus): String = when (s) {
        PolicyStatus.ACTIVE -> "Covered"
        PolicyStatus.LAPSED -> "Lapsed"
        PolicyStatus.CANCELLED -> "Cancelled"
    }

    fun statusTone(s: PolicyStatus): Tone = when (s) {
        PolicyStatus.ACTIVE -> Tone.GOOD
        PolicyStatus.LAPSED -> Tone.BAD
        PolicyStatus.CANCELLED -> Tone.MUTED
    }

    fun chances(session: GameSession, facility: Facility, level: CoverLevel): List<PerilChance> {
        val region = session.economy.world.region(facility.region)
        val risk = session.economy.risk
        return level.perils.sortedBy { it.ordinal }.map { p ->
            var yearly = p.annualPpm * risk.regionFactorPpm(region, p) / 1_000_000L
            if (p == Peril.FIRE) yearly = yearly * risk.fireMultiplePpm(facility.conditionPpm, facility.pollutionPpm) / 1_000_000L
            PerilChance(p.label, Formatting.percentPpm(yearly) + " a year")
        }
    }

    private fun riskTone(session: GameSession, f: Facility): Pair<String, Tone> {
        val region = session.economy.world.region(f.region)
        val risk = session.economy.risk
        val worst = Peril.values().maxByOrNull { risk.regionFactorPpm(region, it) * it.annualPpm / 1_000_000L }!!
        val yearly = worst.annualPpm * risk.regionFactorPpm(region, worst) / 1_000_000L
        val tone = when {
            yearly >= 150_000L -> Tone.BAD
            yearly >= 60_000L -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        return "${worst.label.lowercase()} ${Formatting.percentPpm(yearly)} a year" to tone
    }

    fun card(session: GameSession, p: Policy): PolicyCard {
        val money = session.settings::money
        val f = session.economy.production.facility(p.facility)
        val name = f?.let { facilityName(session, it) } ?: "Facility ${p.facility.value}"
        val claims = if (p.claimCount == 0) "No claims" else "${Formatting.plural(p.claimCount.toLong(), "claim")}, ${money(p.claimsPaid)} paid"
        val renews = if (p.isActive) "Next premium ${Formatting.date(p.paidThrough)}" else p.endedAt?.let { "Ended ${Formatting.date(it)}" } ?: ""
        return PolicyCard(
            id = p.id, title = name, cover = "${p.level.label} cover, ${Formatting.percentPpm(p.coveragePpm)} repaid, ${p.deductible.label.lowercase()} excess",
            premium = money(p.premium) + " / month", insured = "Insured for ${money(p.insuredValue)}", renews = renews,
            status = statusLabel(p.status), statusTone = statusTone(p.status), claims = claims, active = p.isActive,
        )
    }

    fun build(session: GameSession): InsuranceModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val money = session.settings::money
        val all = e.risk.policiesOf(me).sortedByDescending { it.id.value }
        val active = all.filter { it.isActive }
        val facilities = e.production.facilitiesOf(me).filter { it.status != FacilityStatus.DEMOLISHED }
        val uninsured = facilities.filter { e.risk.activeFor(it.id) == null }
            .map { it to e.riskRunner.insuredValueOf(it) }
            .sortedWith(compareByDescending<Pair<Facility, Money>> { it.second }.thenBy { it.first.id })
        val rows = uninsured.map { (f, value) ->
            val (risk, tone) = riskTone(session, f)
            val q = e.riskRunner.quote(f, CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM)
            UninsuredRow(f.id, facilityName(session, f), money(value), "from ${money(q.premium)} / month", risk, tone)
        }
        val claims = e.risk.claimsOf(me).sortedByDescending { it.tick.value }.take(30).map { claimRow(session, it) }
        val monthly = active.fold(Money.ZERO) { a, p -> a + p.premium }
        val paid = all.fold(Money.ZERO) { a, p -> a + p.claimsPaid }
        val bare = uninsured.fold(Money.ZERO) { a, u -> a + u.second }
        val tiles = listOf(
            Tile("Covered", "${active.size} of ${facilities.size}", if (facilities.isNotEmpty() && active.size == facilities.size) Tone.GOOD else Tone.NEUTRAL, "facilities"),
            Tile("Premiums", money(monthly), note = "a month"),
            Tile("Claims paid", money(paid), Tones.bySign(paid.raw), "so far"),
            Tile("Uninsured", money(bare), if (bare.raw > 0L) Tone.WARN else Tone.NEUTRAL, "at risk"),
        )
        val note = "Fires, storms, floods and earthquakes damage buildings and destroy stock whether or not you are insured. Cover repays part of the loss after your excess."
        return InsuranceModel(tiles, all.map { card(session, it) }, rows, claims, note)
    }

    fun claimRow(session: GameSession, c: Claim): ClaimRow {
        val money = session.settings::money
        val f = session.economy.production.facility(c.facility)
        val name = f?.let { facilityName(session, it) } ?: "Facility ${c.facility.value}"
        val (paid, note, tone) = when {
            !c.insured -> Triple("Nothing", "Not insured", Tone.BAD)
            c.shortfall.raw > 0L -> Triple(money(c.payout), "The insurer could not pay ${money(c.shortfall)}", Tone.WARN)
            c.payout.raw == 0L -> Triple("Nothing", "Below your excess", Tone.NEUTRAL)
            else -> Triple(money(c.payout), "Repaid", Tone.GOOD)
        }
        return ClaimRow("${c.peril.label} at $name", Formatting.date(c.tick), money(c.loss), paid, note, tone)
    }

    /** Prices cover on a facility; the problem field says why it cannot be bought, if it cannot. */
    fun quote(session: GameSession, facility: FacilityId?, level: CoverLevel, deductible: Deductible, coveragePpm: Long): CoverQuote? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val money = session.settings::money
        val f = facility?.let { e.production.facility(it) } ?: return null
        val q = e.riskRunner.quote(f, level, coveragePpm.coerceIn(e.risk.params.minCoveragePpm, e.risk.params.maxCoveragePpm), deductible)
        val problem = e.riskRunner.problemWithCover(me, f) ?: if (e.finance.ledger.cash(LedgerOwner.company(me)) < q.premium) "You need ${money(q.premium)} in cash for the first premium." else null
        return CoverQuote(
            premium = money(q.premium), perMonthNote = "${Formatting.percentPpm(q.annualRatePpm)} of the insured value a year", insured = money(q.insuredValue),
            excess = money(q.insuredValue.scale(deductible.sharePpm, 1_000_000L)), perils = perilNames(level), chances = chances(session, f, level), problem = problem,
            premiumMoney = q.premium,
        )
    }

    /** Facilities that can still be insured, for the choice list. */
    fun insurable(session: GameSession): List<Pair<FacilityId, String>> {
        val me = session.selectedCompany ?: return emptyList()
        val e = session.economy
        return e.production.facilitiesOf(me).filter { e.risk.activeFor(it.id) == null && it.status != FacilityStatus.DEMOLISHED }
            .sortedBy { it.id }.map { it.id to facilityName(session, it) }
    }
}

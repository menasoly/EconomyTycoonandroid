package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.RegulationId
import app.many.economytycoon.regulation.Fine
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.LawStatus
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.regulation.Stance
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class RuleRow(val lever: Lever, val label: String, val value: String, val note: String?)

data class LawCard(
    val id: RegulationId,
    val title: String,
    val proposedBy: String,
    val status: String,
    val statusTone: Tone,
    val timing: String,
    val outlook: String,
    val outlookTone: Tone,
    val backing: String,
    val opposing: String,
    val yours: String?,
    val canLobby: Boolean,
    val affectsYou: Boolean,
)

data class FineRow(val facility: String, val date: String, val amount: String, val detail: String, val closed: Boolean)

data class GovernmentModel(
    val country: String,
    val stance: String,
    val stanceBlurb: String,
    val tiles: List<Tile>,
    val rules: List<RuleRow>,
    val open: List<LawCard>,
    val coming: List<LawCard>,
    val history: List<LawCard>,
    val fines: List<FineRow>,
    val standing: String?,
)

object GovernmentPresenter {
    fun statusLabel(s: LawStatus): String = when (s) {
        LawStatus.PROPOSED -> "Debate"
        LawStatus.PASSED -> "Passed"
        LawStatus.ENACTED -> "In force"
        LawStatus.REJECTED -> "Rejected"
    }

    fun statusTone(s: LawStatus): Tone = when (s) {
        LawStatus.PROPOSED -> Tone.INFO
        LawStatus.PASSED -> Tone.WARN
        LawStatus.ENACTED -> Tone.GOOD
        LawStatus.REJECTED -> Tone.MUTED
    }

    /** Words for how likely a vote is to go the government's way. */
    fun outlook(leaningPpm: Long): Pair<String, Tone> = when {
        leaningPpm >= 650_000L -> "Likely to pass" to Tone.GOOD
        leaningPpm >= 500_000L -> "Leaning to pass" to Tone.INFO
        leaningPpm >= 400_000L -> "Too close to call" to Tone.WARN
        else -> "Likely to fail" to Tone.BAD
    }

    fun card(session: GameSession, law: Law, me: CompanyId): LawCard {
        val e = session.economy
        val money = session.settings::money
        val now = session.tick.value
        val (words, tone) = outlook(e.regulation.leaning(law))
        val timing = when (law.status) {
            LawStatus.PROPOSED -> "Vote in ${Formatting.plural((law.voteAt.value - now).coerceAtLeast(0L), "day")}"
            LawStatus.PASSED -> "In force in ${Formatting.plural(((law.effectiveAt?.value ?: now) - now).coerceAtLeast(0L), "day")}"
            else -> law.endedAt?.let { Formatting.date(it) } ?: ""
        }
        val backed = law.backedBy(me); val opposed = law.opposedBy(me)
        val yours = when {
            backed.raw > 0L && opposed.raw > 0L -> "You back it with ${money(backed)} and oppose it with ${money(opposed)}"
            backed.raw > 0L -> "You back it with ${money(backed)}"
            opposed.raw > 0L -> "You oppose it with ${money(opposed)}"
            else -> null
        }
        val regions = e.production.facilitiesOf(me).map { it.region }.toSet()
        val affects = regions.any { e.world.region(it).country == law.country }
        return LawCard(
            law.id, Formatting.lawSummary(law).replaceFirstChar { it.uppercase() }, law.proposedBy.label, statusLabel(law.status), statusTone(law.status), timing,
            if (law.isOpen) words else if (law.status == LawStatus.REJECTED) "Voted down" else "Voted through", if (law.isOpen) tone else statusTone(law.status),
            money(law.supportPool), money(law.opposePool), yours, law.isOpen, affects,
        )
    }

    private fun countryOf(session: GameSession, me: CompanyId) = session.economy.companies.company(me)?.country

    fun build(session: GameSession): GovernmentModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val countryId = countryOf(session, me) ?: return null
        val country = e.world.countries().firstOrNull { it.id == countryId } ?: return null
        e.regulationRunner.ensureGovernments(session.tick)
        val gov = e.regulation.government(countryId) ?: return null
        val laws = e.regulation.lawsOf(countryId)
        val open = laws.filter { it.isOpen }.sortedBy { it.voteAt }
        val coming = laws.filter { it.status == LawStatus.PASSED }.sortedBy { it.effectiveAt }
        val history = laws.filter { it.status == LawStatus.ENACTED || it.status == LawStatus.REJECTED }.sortedByDescending { it.endedAt?.value ?: 0L }.take(15)
        val fines = e.regulation.finesOf(me).sortedByDescending { it.tick.value }.take(10)
        val untilElection = (gov.nextElection.value - session.tick.value).coerceAtLeast(0L)
        val tiles = listOf(
            Tile("Approval", Formatting.percentPpm(gov.approvalPpm), Tones.health(gov.approvalPpm)),
            Tile("Election", Formatting.duration(untilElection), note = "term ${gov.terms}"),
            Tile("Before parliament", open.size.toString(), if (open.isNotEmpty()) Tone.INFO else Tone.NEUTRAL),
            Tile("Your fines", fines.size.toString(), if (fines.isEmpty()) Tone.NEUTRAL else Tone.WARN, note = "recent"),
        )
        val rules = Lever.values().map { l ->
            val pending = laws.firstOrNull { it.lever == l && (it.isOpen || it.status == LawStatus.PASSED) }
            RuleRow(l, l.label, Formatting.leverValue(l, l.valueIn(country)), pending?.let { "${statusLabel(it.status)}: ${Formatting.leverValue(l, it.to)}" })
        }
        val limit = e.regulationRunner.limitFor(country)
        val worst = e.production.facilitiesOf(me).filter { e.world.region(it.region).country == countryId }.maxOfOrNull { it.pollutionPpm } ?: 0L
        val standing = when {
            worst > limit -> "A facility of yours is above the pollution limit of ${Formatting.percentPpm(limit)}. Inspectors fine it every month; the third fine in six months closes it."
            else -> null
        }
        return GovernmentModel(
            country.name, gov.stance.label, gov.stance.blurb, tiles, rules,
            open.map { card(session, it, me) }, coming.map { card(session, it, me) }, history.map { card(session, it, me) },
            fines.map { fineRow(session, it) }, standing,
        )
    }

    fun fineRow(session: GameSession, f: Fine): FineRow {
        val name = session.economy.production.facility(f.facility)?.let { it.name.ifBlank { null } } ?: "Facility ${f.facility.value}"
        return FineRow(
            name, Formatting.date(f.tick), session.settings.money(f.amount),
            "Pollution ${Formatting.percentPpm(f.pollutionPpm)} against a limit of ${Formatting.percentPpm(f.limitPpm)}", f.closed,
        )
    }

    /** The cash a company could put into lobbying right now, and the least the law allows. */
    fun lobbyBudget(session: GameSession): app.many.economytycoon.core.Money =
        session.selectedCompany?.let { session.economy.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(it)) } ?: app.many.economytycoon.core.Money.ZERO

    fun stanceLabel(s: Stance): String = s.label
}

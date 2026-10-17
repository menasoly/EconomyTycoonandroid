package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.CampaignLine
import app.many.economytycoon.talent.CampaignStatus
import app.many.economytycoon.talent.CovenantLine
import app.many.economytycoon.talent.CovenantStance
import app.many.economytycoon.talent.OfferLine
import app.many.economytycoon.talent.OfferStatus
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentView

data class OfferRow(val title: String, val terms: String, val status: String, val tone: Tone, val timing: String, val odds: String?)

data class CovenantRow(val title: String, val detail: String, val tone: Tone, val release: TalentCommand?)

data class RecruitCampaignRow(val title: String, val detail: String, val status: String, val tone: Tone, val cancel: TalentCommand?)

data class CampaignSuggestion(val title: String, val detail: String, val launch: TalentCommand)

data class RecruitBrandRow(val where: String, val known: String, val appeal: String, val tone: Tone)

data class RecruitingModel(
    val tiles: List<Tile>,
    val againstUs: List<OfferRow>,
    val hunting: List<OfferRow>,
    val covenants: List<CovenantRow>,
    val retention: List<Fact>,
    val counterPolicy: Fact,
    val toggleCounter: TalentCommand,
    val campaigns: List<RecruitCampaignRow>,
    val suggestions: List<CampaignSuggestion>,
    val brands: List<RecruitBrandRow>,
    val note: String,
)

/** Poaching, non-competes, retention, employer branding and graduate recruiting. */
object RecruitingPresenter {
    fun statusLabel(s: OfferStatus): String = when (s) {
        OfferStatus.PENDING -> "Waiting for an answer"
        OfferStatus.SUCCEEDED -> "People moved"
        OfferStatus.COUNTERED -> "Kept by a counter-offer"
        OfferStatus.REJECTED -> "Turned down"
        OfferStatus.BLOCKED -> "Stopped by a non-compete"
    }

    fun statusTone(s: OfferStatus, ours: Boolean): Tone = when (s) {
        OfferStatus.PENDING -> Tone.INFO
        OfferStatus.SUCCEEDED -> if (ours) Tone.GOOD else Tone.BAD
        OfferStatus.COUNTERED -> if (ours) Tone.WARN else Tone.GOOD
        OfferStatus.REJECTED -> if (ours) Tone.BAD else Tone.GOOD
        OfferStatus.BLOCKED -> Tone.MUTED
    }

    fun stanceLabel(s: CovenantStance): String = when (s) {
        CovenantStance.RESPECT -> "respecting non-competes"
        CovenantStance.BUY_OUT -> "buying out non-competes"
        CovenantStance.IGNORE -> "ignoring non-competes"
    }

    fun offer(o: OfferLine, ours: Boolean, now: Tick, money: (Money) -> String): OfferRow {
        val who = if (ours) "Approach to ${o.victimName}" else "Approach by ${o.hunterName}"
        val result = when (o.status) {
            OfferStatus.PENDING -> ""
            else -> "; ${TalentWords.people(o.moved)} moved, ${o.kept} kept"
        }
        return OfferRow(
            title = "$who for ${o.role.lowercase()}",
            terms = "${TalentWords.people(o.heads)} at ${money(o.wage)} a day" + (if (o.bonus.raw > 0L) " plus ${money(o.bonus)} signing" else "") + ", ${stanceLabel(o.stance)}$result",
            status = statusLabel(o.status), tone = statusTone(o.status, ours),
            timing = if (o.status == OfferStatus.PENDING) "Answer in ${Formatting.plural((o.resolveAt.value - now.value).coerceAtLeast(0L), "day")}" else "Settled",
            odds = if (o.oddsPpm > 0L) "${Formatting.percentPpm(o.oddsPpm)} chance each says yes" else null,
        )
    }

    fun covenant(c: CovenantLine, company: CompanyId, money: (Money) -> String): CovenantRow = CovenantRow(
        title = "Non-compete: ${c.person ?: c.role.lowercase()}",
        detail = "${TalentWords.people(c.heads)} until ${Formatting.date(c.until)}, ${money(c.penaltyPerHead)} each if breached" + (if (c.breaches > 0) ", breached ${c.breaches} time(s)" else ""),
        tone = if (c.active) Tone.INFO else Tone.MUTED, release = if (c.active) TalentCommand.ReleaseNonCompete(company, c.id) else null,
    )

    fun campaign(c: CampaignLine, company: CompanyId, money: (Money) -> String): RecruitCampaignRow = RecruitCampaignRow(
        title = "Recruiting ${c.role.lowercase()} in ${c.regionName}",
        detail = "${money(c.budget)} budget, ${Formatting.plural(c.hired, "hire")} so far, until ${Formatting.date(c.end)}",
        status = when (c.status) { CampaignStatus.ACTIVE -> "Running"; CampaignStatus.DONE -> "Finished"; CampaignStatus.CANCELLED -> "Cancelled" },
        tone = when (c.status) { CampaignStatus.ACTIVE -> Tone.INFO; CampaignStatus.DONE -> Tone.GOOD; CampaignStatus.CANCELLED -> Tone.MUTED },
        cancel = if (c.status == CampaignStatus.ACTIVE) TalentCommand.CancelCampaign(company, c.id) else null,
    )

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): RecruitingModel {
        val pendingAgainst = v.offersAgainstUs.count { it.status == OfferStatus.PENDING }
        val active = v.campaigns.filter { it.status == CampaignStatus.ACTIVE }
        val tiles = listOf(
            Tile("Approaches against you", pendingAgainst.toString(), if (pendingAgainst > 0) Tone.BAD else Tone.NEUTRAL),
            Tile("Your approaches", v.huntingOffers.count { it.status == OfferStatus.PENDING }.toString()),
            Tile("Non-competes", v.covenants.count { it.active }.toString()),
            Tile("Campaigns", active.size.toString()),
        )
        val suggestions = v.cohorts.filter { it.openings > 0L && active.none { c -> c.region == it.key.region && c.role == it.role } }
            .sortedByDescending { it.openings }.take(3).map { c ->
                val budget = Money.units(2_000L * c.openings.coerceAtMost(20L))
                CampaignSuggestion(
                    title = "Recruit ${c.role.lowercase()}", detail = "${Formatting.plural(c.openings, "post")} open; ${money(budget)} for 90 days",
                    launch = TalentCommand.LaunchCampaign(v.company, c.key.region, RoleId(c.key.role.key), budget, 90),
                )
            }
        return RecruitingModel(
            tiles = tiles,
            againstUs = v.offersAgainstUs.take(8).map { offer(it, false, v.tick, money) },
            hunting = v.huntingOffers.take(8).map { offer(it, true, v.tick, money) },
            covenants = v.covenants.map { covenant(it, v.company, money) },
            retention = v.retention.map { Fact(it.role, "Bonus pool at ${Formatting.percentPpm(it.strengthPpm)} until ${Formatting.date(it.until)}") },
            counterPolicy = Fact("Counter-offers", if (v.counterEnabled) "On, up to ${Formatting.percentPpm(v.counterMaxMatchPpm)} over current pay" else "Off", if (v.counterEnabled) Tone.GOOD else Tone.WARN),
            toggleCounter = TalentCommand.SetCounterPolicy(v.company, !v.counterEnabled, if (v.counterMaxMatchPpm > 0L) v.counterMaxMatchPpm else 150_000L),
            campaigns = v.campaigns.take(8).map { campaign(it, v.company, money) },
            suggestions = suggestions,
            brands = v.brands.map { RecruitBrandRow(it.regionName, Formatting.percentPpm(it.awarenessPpm) + " know the name", Formatting.percentPpm(it.appealPpm) + " appeal", Tones.health(it.brandPpm)) },
            note = if (pendingAgainst > 0) "A rival is after your people. A counter-offer policy and retention bonuses make them harder to take." else "Good pay and happy staff are the best defence against poaching.",
        )
    }
}

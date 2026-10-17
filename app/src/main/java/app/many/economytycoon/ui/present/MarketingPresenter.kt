package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.AbOutcome
import app.many.economytycoon.marketing.BrandView
import app.many.economytycoon.marketing.CampaignId
import app.many.economytycoon.marketing.CampaignStatus
import app.many.economytycoon.marketing.CampaignView
import app.many.economytycoon.marketing.MarketingOverview
import app.many.economytycoon.marketing.PromotionStatus
import app.many.economytycoon.marketing.PromotionView
import app.many.economytycoon.marketing.RoiVerdict
import app.many.economytycoon.session.Formatting

/** What a player can do to a campaign from its screen. The host turns each into the matching command. */
sealed class CampaignAction {
    abstract val campaign: CampaignId
    data class Pause(override val campaign: CampaignId) : CampaignAction()
    data class Resume(override val campaign: CampaignId) : CampaignAction()
    data class Extend(override val campaign: CampaignId, val days: Int) : CampaignAction()
    data class Cancel(override val campaign: CampaignId) : CampaignAction()
    data class RefreshCreative(override val campaign: CampaignId) : CampaignAction()
}

data class CampaignRow(val id: CampaignId, val title: String, val place: String, val status: String, val tone: Tone, val summary: String)

data class BrandRow(val title: String, val facts: List<Fact>, val tone: Tone, val note: String?)

data class PromotionRow(val title: String, val status: String, val tone: Tone, val summary: String)

data class CampaignListModel(
    val tiles: List<Tile>,
    val live: List<CampaignRow>,
    val ended: List<CampaignRow>,
    val brands: List<BrandRow>,
    val promotions: List<PromotionRow>,
    val damages: List<Fact>,
    val note: String,
)

data class CampaignActions(
    val canPause: Boolean,
    val canResume: Boolean,
    val canExtend: Boolean,
    val canCancel: Boolean,
    val canRefresh: Boolean,
    val cancelNote: String?,
)

data class CampaignDetailModel(
    val id: CampaignId,
    val title: String,
    val place: String,
    val status: String,
    val tone: Tone,
    val headline: String,
    val booking: List<Fact>,
    val effect: List<Fact>,
    val creative: List<Fact>,
    val test: String?,
    val roi: List<Fact>,
    val warnings: List<String>,
    val actions: CampaignActions,
)

/** The campaign list and the page for one campaign, over the marketing read models. */
object MarketingPresenter {
    const val EMPTY = "No campaigns yet. Pick a product and a place, choose a channel and set a daily budget."

    fun statusTone(s: CampaignStatus): Tone = when (s) {
        CampaignStatus.ACTIVE -> Tone.GOOD
        CampaignStatus.SCHEDULED -> Tone.INFO
        CampaignStatus.PAUSED -> Tone.WARN
        CampaignStatus.SUSPENDED -> Tone.BAD
        CampaignStatus.COMPLETED, CampaignStatus.CANCELLED -> Tone.MUTED
    }

    fun qualityLabel(qualityPpm: Long): String = when {
        qualityPpm >= 700_000L -> "Excellent"
        qualityPpm >= 550_000L -> "Good"
        qualityPpm >= 420_000L -> "Average"
        else -> "Weak"
    }

    fun freshnessLabel(freshnessPpm: Long): String = when {
        freshnessPpm >= 750_000L -> "Fresh"
        freshnessPpm >= 450_000L -> "Familiar"
        freshnessPpm >= 250_000L -> "Tired"
        else -> "Worn out"
    }

    private fun summary(c: CampaignView, money: (Money) -> String): String = when (c.status) {
        CampaignStatus.SCHEDULED -> "${money(c.dailyBudget)} a day, starts soon"
        CampaignStatus.ACTIVE -> "${money(c.dailyBudget)} a day, ${Formatting.plural(c.daysRemaining.toLong(), "day")} left, reaches ${Formatting.percentPpm(c.reachPpm)}"
        CampaignStatus.PAUSED -> "Paused with ${Formatting.plural(c.daysRemaining.toLong(), "day")} booked"
        CampaignStatus.SUSPENDED -> "Stopped: bills went unpaid"
        CampaignStatus.COMPLETED -> "Ran ${Formatting.plural(c.daysRun.toLong(), "day")}, cost ${money(c.netSpend)}"
        CampaignStatus.CANCELLED -> "Cancelled after ${Formatting.plural(c.daysRun.toLong(), "day")}, cost ${money(c.netSpend)}"
    }

    fun row(c: CampaignView, money: (Money) -> String): CampaignRow =
        CampaignRow(c.id, c.title, c.place, c.status.label, statusTone(c.status), summary(c, money))

    fun brandRow(b: BrandView, money: (Money) -> String): BrandRow {
        val note = when {
            b.taintPpm >= 200_000L -> "Still marked by bad news; advertising works less well until it fades."
            b.liveCampaigns == 0 && b.awarenessPpm > 0L -> "Not advertised; awareness is fading."
            else -> null
        }
        return BrandRow(
            b.title,
            listOf(
                Fact("Known by", Formatting.percentPpm(b.awarenessPpm), Tones.health(b.awarenessPpm)),
                Fact("Loyal buyers", Formatting.percentPpm(b.loyaltyPpm), Tones.health(b.loyaltyPpm)),
                Fact("Demand lift", Formatting.signedPercentPpm(b.appealPpm - 1_000_000L)),
                Fact("Daily spend", if (b.liveCampaigns == 0) "None" else money(b.dailySpend)),
            ),
            if (b.taintPpm >= 200_000L) Tone.WARN else Tones.health(b.awarenessPpm), note,
        )
    }

    fun promotionRow(p: PromotionView, money: (Money) -> String): PromotionRow {
        val tone = when {
            p.belowCost -> Tone.BAD
            p.status == PromotionStatus.RUNNING -> Tone.GOOD
            p.status == PromotionStatus.SCHEDULED -> Tone.INFO
            else -> Tone.MUTED
        }
        val text = "${Formatting.percentPpm(p.discountPpm)} off: ${money(p.listPrice)} down to ${money(p.salePrice)}, margin ${Formatting.percentPpm(p.marginAfterPpm)}" +
            if (p.daysLeft > 0) ", ${Formatting.plural(p.daysLeft.toLong(), "day")} left" else ""
        return PromotionRow(p.title, p.status.label, tone, text)
    }

    fun list(ov: MarketingOverview, money: (Money) -> String): CampaignListModel {
        val running = ov.campaigns.count { it.status == CampaignStatus.ACTIVE }
        val aware = if (ov.brands.isEmpty()) 0L else ov.brands.sumOf { it.awarenessPpm } / ov.brands.size
        val tiles = listOf(
            Tile("Running", running.toString(), if (running > 0) Tone.GOOD else Tone.MUTED),
            Tile("Daily bill", money(ov.dailyCommitment)),
            Tile("Spent so far", money(ov.totalNetSpend)),
            Tile("Average awareness", Formatting.percentPpm(aware), Tones.health(aware)),
        )
        return CampaignListModel(
            tiles = tiles,
            live = ov.campaigns.filter { it.isLive }.map { row(it, money) },
            ended = ov.campaigns.filter { !it.isLive }.map { row(it, money) },
            brands = ov.brands.map { brandRow(it, money) },
            promotions = ov.promotions.filter { it.status == PromotionStatus.RUNNING || it.status == PromotionStatus.SCHEDULED }.map { promotionRow(it, money) },
            damages = ov.damages.map { Fact(it.title, it.kind.label, Tone.BAD, "Day ${it.tick.value}: lost ${Formatting.percentPpm(it.awarenessLostPpm)} awareness") },
            note = if (ov.campaigns.isEmpty()) EMPTY else "Awareness builds with steady spending and fades when it stops. Loyalty follows awareness and quality.",
        )
    }

    fun detail(c: CampaignView, money: (Money) -> String): CampaignDetailModel {
        val warnings = ArrayList<String>()
        if (c.isLive && c.freshnessPpm < 350_000L) warnings += "The creative is worn out. A new one restores its full strength."
        if (c.missedPayments > 0) warnings += "${Formatting.plural(c.missedPayments.toLong(), "bill")} went unpaid. Campaigns are suspended after repeated misses."
        if (c.status == CampaignStatus.ACTIVE && c.daysRemaining in 1..3) warnings += "Ends in ${Formatting.plural(c.daysRemaining.toLong(), "day")}. Awareness fades once the money stops."
        if (c.status == CampaignStatus.SUSPENDED) warnings += "Suspended for non-payment. Book it again once cash allows."
        val booking = listOf(
            Fact("Budget", "${money(c.dailyBudget)} a day"),
            Fact("Billed", "${money(c.billPerDay)} a day", hint = if (c.billPerDay < c.dailyBudget) "Discount for a long booking" else null),
            Fact("Booked", Formatting.plural(c.daysBooked.toLong(), "day")),
            Fact("Run so far", Formatting.plural(c.daysRun.toLong(), "day")),
            Fact("Remaining", Formatting.plural(c.daysRemaining.toLong(), "day")),
            Fact("Paid out", money(c.spentTotal)),
            Fact("Paid in advance, unused", money(c.prepaid)),
            Fact("Refunded", money(c.refundedTotal)),
        )
        val effect = listOf(
            Fact("Reach", Formatting.percentPpm(c.reachPpm), Tones.health(c.reachPpm * 2L), "Share of the audience that sees it each day"),
            Fact("People reached", Formatting.grouped(c.impressions)),
            Fact("Brand awareness", Formatting.percentPpm(c.awarenessPpm), Tones.health(c.awarenessPpm)),
            Fact("Brand loyalty", Formatting.percentPpm(c.loyaltyPpm), Tones.health(c.loyaltyPpm)),
        )
        val creative = listOf(
            Fact("Quality", qualityLabel(c.creativeQualityPpm)),
            Fact("Freshness", "${freshnessLabel(c.freshnessPpm)} (${Formatting.percentPpm(c.freshnessPpm)})", Tones.health(c.freshnessPpm)),
            Fact("Version", c.creativeVersion.toString()),
        )
        val test = c.test?.let {
            when (it.outcome) {
                AbOutcome.RUNNING -> "Split test running, ${Formatting.plural(it.daysLeft.toLong(), "day")} to go."
                else -> "Split test finished: ${it.outcome.label}."
            }
        }
        val roi = c.roi?.let { r ->
            listOf(
                Fact("Verdict", r.verdict.label, roiTone(r.verdict)),
                Fact("Lift in sales", Formatting.signedPercentPpm(r.liftPpm), Tones.bySign(r.liftPpm)),
                Fact("Extra revenue", Formatting.signedMoney(r.incrementalRevenue)),
                Fact("Extra profit", Formatting.signedMoney(r.incrementalProfit)),
                Fact("Return on spend", Formatting.signedPercentPpm(r.roiPpm), Tones.bySign(r.roiPpm)),
                Fact("Paid back", r.paybackDay?.let { "on day $it" } ?: "not yet"),
            )
        } ?: emptyList()
        val live = c.isLive
        val actions = CampaignActions(
            canPause = c.status == CampaignStatus.ACTIVE,
            canResume = c.status == CampaignStatus.PAUSED,
            canExtend = live,
            canCancel = live,
            canRefresh = c.canRefresh && c.test?.outcome != AbOutcome.RUNNING,
            cancelNote = if (live && (c.cancelRefund.raw > 0L || c.cancelFee.raw > 0L)) "Cancelling returns ${money(c.cancelRefund)} of the advance and keeps ${money(c.cancelFee)} as a fee." else null,
        )
        return CampaignDetailModel(
            c.id, c.title, c.place, c.status.label, statusTone(c.status), summary(c, money), booking, effect, creative, test, roi, warnings, actions,
        )
    }

    fun roiTone(v: RoiVerdict): Tone = when (v) {
        RoiVerdict.PAID_BACK -> Tone.GOOD
        RoiVerdict.PAYING_BACK -> Tone.INFO
        RoiVerdict.LOSING -> Tone.BAD
        RoiVerdict.TOO_EARLY -> Tone.MUTED
    }
}

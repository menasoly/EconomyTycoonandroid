package app.many.economytycoon.marketing

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

/** How good a creative the agency is asked to make, and what it charges in days of the campaign's daily budget. */
enum class CreativeTier(val label: String, val qualityPpm: Long, val feeDays: Int) {
    BASIC("In-house", 420_000L, 0),
    PROFESSIONAL("Agency", 600_000L, 2),
    STUDIO("Creative studio", 780_000L, 5);

    companion object { fun byOrdinal(i: Int): CreativeTier? = values().getOrNull(i) }
}

/** What a player or computer company asks for when booking a campaign. */
data class CampaignRequest(
    val company: CompanyId,
    val good: GoodId,
    val region: RegionId,
    val city: CityId? = null,
    val channel: Channel,
    val segment: TargetSegment = TargetSegment.EVERYONE,
    val dailyBudget: Money,
    val days: Int,
    val tier: CreativeTier = CreativeTier.PROFESSIONAL,
    /** Day the campaign starts; null means today. */
    val startTick: Tick? = null,
)

/** The result of a change that costs money: the thing changed and the payments the finance layer should post. */
data class Booking(val campaign: Campaign, val intents: List<SettlementIntent>) {
    val total: Money get() = Money.sum(intents.map { it.total })
}

data class CancelResult(val campaign: Campaign, val refund: Money, val fee: Money, val intents: List<SettlementIntent>)

/**
 * The register of campaigns and the rules for booking, pausing, extending, cancelling and refreshing them. Money is
 * never moved here: every operation that costs or returns money gives back settlement intents, and the caller passes
 * in the cash it has available so a company cannot book more than it can pay for.
 */
class CampaignService(
    val state: MarketingState,
    val params: MarketingParams = MarketingParams(),
    val ledger: MarketingLedger = MarketingLedger(),
) {
    fun get(id: CampaignId): Campaign? = state.campaign(id)

    /** What a campaign is billed each day: its budget, less the discount for a long booking. */
    fun billFor(c: Campaign): Money =
        if (c.daysBooked >= params.longBookingDays) c.dailyBudget.times(Rate(Ppm.ONE - params.longBookingDiscountPpm)) else c.dailyBudget

    /** Money that has to be paid up front to hold [days] days of [spec] at [bill] a day. */
    fun depositFor(spec: ChannelSpec, bill: Money, days: Int): Money = bill * minOf(days, spec.depositDays).toLong()

    private fun reject(rule: String, message: String) = Outcome.reject(rule, message)

    fun create(request: CampaignRequest, tick: Tick, available: Money, audiences: AudienceSource): Outcome<Booking> {
        val spec = request.channel.spec
        val start = request.startTick ?: tick
        if (start < tick) return reject(Rules.INVALID_ARGUMENT, "a campaign cannot start in the past")
        if (request.days < params.minCampaignDays || request.days > params.maxCampaignDays) {
            return reject(Rules.INVALID_ARGUMENT, "a campaign runs between ${params.minCampaignDays} and ${params.maxCampaignDays} days")
        }
        if (request.dailyBudget < spec.minDailySpend) {
            return reject(Rules.INVALID_ARGUMENT, "${request.channel.label} needs at least ${spec.minDailySpend} a day to be worth booking")
        }
        if (request.dailyBudget > spec.maxDailySpend) {
            return reject(Rules.LIMIT_EXCEEDED, "${request.channel.label} cannot take more than ${spec.maxDailySpend} a day")
        }
        val audience = audiences.audience(request.region, request.city) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown place to advertise in")
        if (audience.segmentSize(request.segment) <= 0L) return reject(Rules.INVALID_ARGUMENT, "nobody lives there to reach")
        if (state.liveCampaignsOf(request.company).size >= params.maxCampaignsPerCompany) {
            return reject(Rules.LIMIT_EXCEEDED, "too many campaigns running at once")
        }
        val clash = state.campaignMap.values.any {
            it.isLive && it.company == request.company && it.good == request.good && it.region == request.region &&
                it.city == request.city && it.channel == request.channel
        }
        if (clash) return reject(Rules.DUPLICATE, "that channel already carries a campaign for this product there")

        val creativeFee = request.dailyBudget * request.tier.feeDays.toLong()
        val probe = Campaign(
            CampaignId(0L), request.company, request.good, request.region, request.city, request.channel, request.segment,
            tick, start, request.dailyBudget, request.days, Creative(request.tier.qualityPpm),
        )
        val deposit = depositFor(spec, billFor(probe), request.days)
        val upfront = deposit + creativeFee
        if (upfront > available) return reject(Rules.INSUFFICIENT_FUNDS, "booking needs $upfront up front")

        val id = state.ids.campaign()
        val c = Campaign(
            id, request.company, request.good, request.region, request.city, request.channel, request.segment,
            tick, start, request.dailyBudget, request.days, Creative(request.tier.qualityPpm),
        )
        c.prepaid = deposit
        c.feesTotal = Money.ZERO
        c.spentTotal = deposit + creativeFee
        state.campaignMap[id] = c
        state.stockFor(c.key)
        val intents = listOfNotNull(ledger.deposit(c, deposit, tick), ledger.creative(c, creativeFee, tick))
        return Outcome.ok(Booking(c, intents))
    }

    /** Takes back a booking whose up-front payment could not be made. Only a campaign that has not run yet can be voided. */
    fun voidBooking(id: CampaignId): Outcome<Unit> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.daysRun > 0 || !c.isLive) return reject(Rules.WRONG_STATE, "that campaign has already run")
        state.campaignMap.remove(id)
        return Outcome.ok(Unit)
    }

    fun pause(id: CampaignId): Outcome<Campaign> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.status != CampaignStatus.ACTIVE) return reject(Rules.WRONG_STATE, "only a running campaign can be paused")
        c.status = CampaignStatus.PAUSED
        return Outcome.ok(c)
    }

    fun resume(id: CampaignId, tick: Tick): Outcome<Campaign> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.status != CampaignStatus.PAUSED) return reject(Rules.WRONG_STATE, "that campaign is not paused")
        c.status = if (c.startTick > tick) CampaignStatus.SCHEDULED else CampaignStatus.ACTIVE
        c.pausedDays = 0
        return Outcome.ok(c)
    }

    /** Adds days to a live campaign. A channel that is booked ahead asks for the extra deposit now. */
    fun extend(id: CampaignId, extraDays: Int, tick: Tick, available: Money): Outcome<Booking> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (!c.isLive) return reject(Rules.WRONG_STATE, "that campaign has ended")
        if (extraDays <= 0) return reject(Rules.INVALID_ARGUMENT, "extend by at least a day")
        if (c.daysBooked + extraDays > params.maxCampaignDays) return reject(Rules.LIMIT_EXCEEDED, "campaigns cannot be booked beyond ${params.maxCampaignDays} days")
        val before = c.daysBooked
        c.daysBooked += extraDays
        c.daysRemaining += extraDays
        val wanted = depositFor(c.channel.spec, billFor(c), c.daysRemaining)
        val topUp = (wanted - c.prepaid).coerceAtLeast(Money.ZERO)
        if (topUp > available) {
            c.daysBooked = before
            c.daysRemaining -= extraDays
            return reject(Rules.INSUFFICIENT_FUNDS, "extending needs $topUp up front")
        }
        c.prepaid = c.prepaid + topUp
        c.spentTotal = c.spentTotal + topUp
        return Outcome.ok(Booking(c, listOfNotNull(ledger.deposit(c, topUp, tick))))
    }

    /** Changes the daily budget of a live campaign, within the channel's limits. */
    fun setBudget(id: CampaignId, dailyBudget: Money, tick: Tick, available: Money): Outcome<Booking> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (!c.isLive) return reject(Rules.WRONG_STATE, "that campaign has ended")
        val spec = c.channel.spec
        if (dailyBudget < spec.minDailySpend) return reject(Rules.INVALID_ARGUMENT, "${c.channel.label} needs at least ${spec.minDailySpend} a day")
        if (dailyBudget > spec.maxDailySpend) return reject(Rules.LIMIT_EXCEEDED, "${c.channel.label} cannot take more than ${spec.maxDailySpend} a day")
        val old = c.dailyBudget
        c.dailyBudget = dailyBudget
        val wanted = depositFor(spec, billFor(c), c.daysRemaining)
        val topUp = (wanted - c.prepaid).coerceAtLeast(Money.ZERO)
        if (topUp > available) {
            c.dailyBudget = old
            return reject(Rules.INSUFFICIENT_FUNDS, "that budget needs $topUp more up front")
        }
        c.prepaid = c.prepaid + topUp
        c.spentTotal = c.spentTotal + topUp
        return Outcome.ok(Booking(c, listOfNotNull(ledger.deposit(c, topUp, tick))))
    }

    /** The fee the seller would keep if [c] were cancelled today, and the refund of what is left of the deposit. */
    fun cancellationTerms(c: Campaign): Pair<Money, Money> {
        val rate = if (c.daysRun < params.earlyCancelDays) (c.channel.spec.cancelFeePpm * 2L).coerceAtMost(Ppm.ONE) else c.channel.spec.cancelFeePpm
        val fee = c.prepaid.times(Rate(rate))
        return (c.prepaid - fee) to fee
    }

    fun cancel(id: CampaignId, tick: Tick): Outcome<CancelResult> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (!c.isLive) return reject(Rules.WRONG_STATE, "that campaign has already ended")
        val (refund, fee) = cancellationTerms(c)
        close(c, CampaignStatus.CANCELLED, tick, refund, fee)
        return Outcome.ok(CancelResult(c, refund, fee, listOfNotNull(ledger.refund(c, refund, tick))))
    }

    /** Commissions a new creative: the wear starts over and the quality is that of the tier chosen. */
    fun refreshCreative(id: CampaignId, tier: CreativeTier, tick: Tick, available: Money): Outcome<Booking> {
        val c = state.campaign(id) ?: return reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (!c.isLive) return reject(Rules.WRONG_STATE, "that campaign has ended")
        val last = c.creative.lastRefreshed
        if (last != null && tick - last < params.creativeRefreshCooldown.toLong()) {
            return reject(Rules.RATE_LIMITED, "the creative was refreshed ${tick - last} days ago; wait ${params.creativeRefreshCooldown - (tick - last)} more")
        }
        if (state.tests().any { it.campaign == id && it.isRunning }) return reject(Rules.WRONG_STATE, "a split test is running on this campaign")
        val fee = c.dailyBudget * (tier.feeDays + params.creativeRefreshDays).toLong()
        if (fee > available) return reject(Rules.INSUFFICIENT_FUNDS, "a new creative costs $fee")
        c.creative.qualityPpm = tier.qualityPpm
        c.creative.freshnessPpm = Ppm.ONE
        c.creative.version += 1
        c.creative.lastRefreshed = tick
        c.spentTotal = c.spentTotal + fee
        return Outcome.ok(Booking(c, listOfNotNull(ledger.creative(c, fee, tick))))
    }

    /** Ends a campaign, handing back a deposit less the fee, or in full when the fee is zero. */
    internal fun close(c: Campaign, status: CampaignStatus, tick: Tick, refund: Money, fee: Money) {
        c.status = status
        c.endedAt = tick
        c.refundedTotal = c.refundedTotal + refund
        c.feesTotal = c.feesTotal + fee
        c.prepaid = Money.ZERO
    }

    /** Ends the campaigns of a company that has closed, with no refund. */
    fun endAllOf(company: CompanyId, tick: Tick) {
        for (c in state.campaignMap.values) if (c.company == company && c.isLive) close(c, CampaignStatus.CANCELLED, tick, Money.ZERO, c.prepaid)
    }
}

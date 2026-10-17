package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money

/** Consistency checks over the marketing state. An empty list means all is well; each entry names one broken rule. */
object MarketingInvariants {
    fun check(state: MarketingState): List<String> {
        val problems = ArrayList<String>()
        for (c in state.campaigns()) {
            val tag = "campaign ${c.id.value}"
            if (c.prepaid.raw < 0L) problems += "$tag has a negative deposit"
            if (c.spentTotal.raw < 0L || c.refundedTotal.raw < 0L || c.feesTotal.raw < 0L) problems += "$tag has a negative money total"
            if (c.daysRemaining < 0 || c.daysRun < 0 || c.pausedDays < 0) problems += "$tag has a negative day count"
            if (c.daysRun + c.daysRemaining > c.daysBooked) problems += "$tag has run more days than were booked"
            if (c.status.isEnded && c.endedAt == null) problems += "$tag ended without an end day"
            if (c.status.isEnded && c.prepaid.raw != 0L) problems += "$tag ended holding a deposit"
            if (c.isLive && c.endedAt != null) problems += "$tag is live but has an end day"
            if (c.dailyBudget < c.channel.spec.minDailySpend || c.dailyBudget > c.channel.spec.maxDailySpend) problems += "$tag budget is outside the channel limits"
            if (c.creative.freshnessPpm !in 0..Ppm.ONE || c.creative.qualityPpm !in 0..Ppm.ONE) problems += "$tag creative out of range"
            if (c.refundedTotal > c.spentTotal) problems += "$tag refunded more than it spent"
        }
        val seen = HashSet<String>()
        for (c in state.campaigns().filter { it.isLive }) {
            val slot = "${c.company.value}/${c.good.key}/${c.region.value}/${c.city?.value}/${c.channel.name}"
            if (!seen.add(slot)) problems += "two live campaigns share the slot $slot"
        }
        for ((k, s) in state.stocks()) {
            if (s.awarenessPpm !in 0..Ppm.ONE || s.loyaltyPpm !in 0..Ppm.ONE || s.taintPpm !in 0..Ppm.ONE) problems += "brand $k out of range"
            if (s.loyaltyPpm > s.awarenessPpm) problems += "brand $k is more loved than known"
            for (ch in Channel.ordered) if (s.adstock(ch) !in 0..Ppm.ONE) problems += "brand $k has bad exposure for ${ch.label}"
        }
        for (p in state.promotions()) {
            if (p.discountPpm !in 1..Ppm.ONE) problems += "promotion ${p.id.value} discount out of range"
            if (p.days <= 0) problems += "promotion ${p.id.value} has no length"
        }
        for (t in state.tests()) {
            if (state.campaign(t.campaign) == null && t.isRunning) problems += "test ${t.id.value} runs on a campaign that is gone"
        }
        return problems
    }

    /** Money the company has committed to marketing and not yet received back: used by tests that follow the books. */
    fun outlay(state: MarketingState): Money = Money.sum(state.campaigns().map { it.netSpend })
}

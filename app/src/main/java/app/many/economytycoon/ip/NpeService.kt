package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.mulDiv

/**
 * The scripted patent holders that make no products. They wait on the sidelines, buy patents when they are cheap, and
 * pursue anyone using them without a licence in the way their strategy dictates. Everything they do is a plain function
 * of the state and the dice, so the same save always behaves the same way.
 */
class NpeService(
    private val state: IpState,
    private val disputes: DisputeService,
    private val market: PatentMarket,
    val params: IpParams = IpParams(),
) {
    private val np get() = params.npe

    /** Creates the standard roster once; calling it again changes nothing. */
    fun seed() {
        if (state.npeMap.isNotEmpty()) return
        val roster = listOf(
            Triple("Harbor Licensing Group", NpeStrategy.DEMAND_LETTERS, 650_000L),
            Triple("Meridian Patent Holdings", NpeStrategy.PORTFOLIO_BUYER, 400_000L),
            Triple("Blackwater IP Partners", NpeStrategy.LITIGATOR, 900_000L),
        )
        for ((name, strategy, aggression) in roster) {
            val n = Npe(state.ids.npe(), name, strategy, aggression, np.settleFloorPpm)
            n.chest = Money.units(np.startingChestUnits)
            state.npeMap[n.id] = n
        }
    }

    /** What a demand letter asks: priced just under what a defence would cost the target. */
    fun demandFor(d: Dispute, run: IpRun): Money {
        val e = disputes.estimate(run, d)
        val defence = e.defendantCostToGo
        val byCost = Money(mulDiv(defence.raw, np.demandShareOfDefenceCostPpm, 1_000_000L))
        return Money.max(byCost, Money.units(params.litigation.minDamagesUnits))
    }

    /** Monthly round for every scripted holder. */
    fun step(run: IpRun) {
        if (!Calendar.isMonthStart(run.tick)) return
        val month = run.tick.value / Calendar.DAYS_PER_MONTH
        for (n in state.npeMap.values.toList()) {
            n.lastActiveMonth = month
            buyPatents(run, n)
            when (n.strategy) {
                NpeStrategy.DEMAND_LETTERS -> sendDemands(run, n)
                NpeStrategy.LITIGATOR -> litigate(run, n)
                NpeStrategy.PORTFOLIO_BUYER -> sendDemands(run, n)
            }
            escalate(run, n)
            answerOffers(run, n)
        }
    }

    private fun mine(n: Npe): List<Dispute> = state.disputeMap.values.filter { it.claimant == n.owner && it.isOpen }

    private fun buyPatents(run: IpRun, n: Npe) {
        if (state.patentsOf(n.owner).size >= np.maxPatents) return
        val share = if (n.strategy == NpeStrategy.PORTFOLIO_BUYER) np.bidShareOfValuePpm else np.bidShareOfValuePpm / 2
        for (p in market.listed().sortedBy { it.askingPrice?.raw ?: Long.MAX_VALUE }) {
            val ask = p.askingPrice ?: continue
            if (p.owner == n.owner || ask > n.chest) continue
            if (ask <= market.bid(p, run, share)) {
                if (market.buy(run, p.id, n.owner) is Outcome.Ok) break
            }
        }
    }

    private fun sendDemands(run: IpRun, n: Npe) {
        var sent = 0
        for (d in mine(n)) {
            if (sent >= np.maxDemandsPerMonth) break
            if (d.stage != DisputeStage.DETECTED) continue
            val demand = demandFor(d, run)
            if (disputes.sendNotice(run, d.id, n.owner, demand) is Outcome.Ok) {
                n.demandsSent += 1
                sent++
                val target = d.defendant.companyOrNull() ?: continue
                run.out.event(IpEvent.DemandReceived(d.id, n.id, target, demand, run.tick))
            }
        }
    }

    private fun litigate(run: IpRun, n: Npe) {
        for (d in mine(n)) {
            if (d.stage != DisputeStage.DETECTED && d.stage != DisputeStage.NOTICE) continue
            val e = disputes.estimate(run, d)
            if (e.claimantWinPpm < 500_000L && n.aggressionPpm < 800_000L) continue
            disputes.file(run, d.id, n.owner)
        }
    }

    /** Anyone whose demand has sat unanswered long enough is taken to court, if the holder is aggressive enough and can pay. */
    private fun escalate(run: IpRun, n: Npe) {
        for (d in mine(n)) {
            if (d.stage != DisputeStage.NOTICE) continue
            val waited = run.tick.value - (d.noticeAt?.value ?: run.tick.value)
            if (waited < np.escalateAfterDays || n.aggressionPpm < 500_000L) continue
            val e = disputes.estimate(run, d)
            if (e.claimantWinPpm >= 400_000L) disputes.file(run, d.id, n.owner)
        }
    }

    /** Takes an offer when it clears the holder's floor against what a win would bring. */
    private fun answerOffers(run: IpRun, n: Npe) {
        for (d in mine(n)) {
            if (d.offerBy == null || d.offerBy == n.owner) continue
            val e = disputes.estimate(run, d)
            val floor = Money(mulDiv(e.awardIfWon.raw, n.settleFloorPpm, 1_000_000L))
            if (d.offerAmount >= floor || d.offerAmount >= e.claimantReserve) disputes.acceptSettlement(run, d.id, n.owner)
        }
    }
}

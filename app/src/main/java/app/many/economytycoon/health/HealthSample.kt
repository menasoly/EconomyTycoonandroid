package app.many.economytycoon.health

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.simulation.AiReserve
import app.many.economytycoon.simulation.Economy

/** A reading of how the whole economy is doing at one tick, in plain numbers a rule can compare. */
data class HealthSample(
    val tick: Long,
    /** All cash in circulation, in whole units. */
    val moneySupply: Long,
    val householdCash: Long,
    val companyCash: Long,
    val governmentCash: Long,
    val aiCompanies: Int,
    /** Computer-run companies holding less cash than a week of running costs. */
    val aiStrained: Int,
    val aiWithoutCash: Int,
    val aiFacilities: Int,
    val aiOperating: Int,
    val playerCompanies: Int,
    /** Markets that have actually traded; idle ones drift on their own and say nothing about the economy. */
    val markets: Int,
    /** Markets trading at less than half, or more than double, their reference price. */
    val marketsCheap: Int,
    val marketsDear: Int,
    val medianPriceRatioPpm: Long,
    val inflationPpm: Long?,
    val unemploymentPpm: Long?,
) {
    val aiStrainedShare: Long get() = if (aiCompanies == 0) 0L else aiStrained * 1_000_000L / aiCompanies
    val aiOperatingShare: Long get() = if (aiFacilities == 0) 1_000_000L else aiOperating * 1_000_000L / aiFacilities
    val cheapShare: Long get() = if (markets == 0) 0L else marketsCheap * 1_000_000L / markets
    val dearShare: Long get() = if (markets == 0) 0L else marketsDear * 1_000_000L / markets

    companion object {
        /** Days of running costs a company needs on hand to count as comfortable. */
        const val STRAIN_DAYS = 7

        fun take(e: Economy): HealthSample {
            val ledger = e.finance.ledger
            val reserve = AiReserve(e)
            val ai = e.companies.ofKind(CompanyKind.AI)
            var strained = 0
            var broke = 0
            var facilities = 0
            var operating = 0
            for (c in ai) {
                val cash = ledger.cash(c.owner)
                if (cash.raw <= 0L) broke++
                if (cash < reserve.dailyCost(c.id) * STRAIN_DAYS.toLong()) strained++
                val list = e.production.facilitiesOf(c.id)
                facilities += list.size
                operating += list.count { it.status == app.many.economytycoon.production.FacilityStatus.OPERATING }
            }
            val ratios = ArrayList<Long>()
            var cheap = 0
            var dear = 0
            for (s in e.markets.states()) {
                if (s.volumeAverage <= 0L) continue
                val ref = e.content.goodOrNull(s.key.good)?.referencePrice?.perUnit?.raw ?: continue
                if (ref <= 0L) continue
                val ratio = s.last.perUnit.raw * 1_000_000L / ref
                ratios += ratio
                if (ratio < 500_000L) cheap++
                if (ratio > 2_000_000L) dear++
            }
            val macro = e.world.countries().firstOrNull()?.let { e.macro.latest(it.id) }
            return HealthSample(
                tick = e.tick.value,
                moneySupply = ledger.moneySupply().wholeUnits(),
                householdCash = ledger.cashHeldBy(OwnerKind.HOUSEHOLDS).wholeUnits(),
                companyCash = ledger.cashHeldBy(OwnerKind.COMPANY).wholeUnits(),
                governmentCash = ledger.cashHeldBy(OwnerKind.GOVERNMENT).wholeUnits(),
                aiCompanies = ai.size, aiStrained = strained, aiWithoutCash = broke, aiFacilities = facilities, aiOperating = operating,
                playerCompanies = e.companies.ofKind(CompanyKind.PLAYER).size,
                markets = ratios.size, marketsCheap = cheap, marketsDear = dear,
                medianPriceRatioPpm = if (ratios.isEmpty()) 1_000_000L else ratios.sorted()[ratios.size / 2],
                inflationPpm = macro?.inflationPpm, unemploymentPpm = macro?.unemploymentPpm,
            )
        }
    }
}

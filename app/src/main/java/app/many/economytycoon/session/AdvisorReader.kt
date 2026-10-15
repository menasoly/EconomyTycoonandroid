package app.many.economytycoon.session

import app.many.economytycoon.advisor.AdvisorFacts
import app.many.economytycoon.advisor.FacilityFact
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Economy

/** Reads the live game into the plain numbers the advisor works from. */
class AdvisorReader(private val e: Economy, private val reports: SessionReports) {
    fun read(company: CompanyId): AdvisorFacts? {
        val head = reports.company.headline(company) ?: return null
        val fin = reports.company.finance(company) ?: return null
        val rows = reports.company.facilities(company)
        val work = reports.people.workforce(company)
        val cohorts = reports.people.cohorts(company)
        val research = reports.people.research(company)
        val facilities = rows.map {
            FacilityFact(it.id.value, it.name, it.status == FacilityStatus.OPERATING, it.utilizationPpm, it.conditionPpm, it.blockedReason, it.headcount, it.stockValue.wholeUnits())
        }
        val idleParcels = e.world.land.ownedBy(company).count { !it.developed && !e.property.encumbered(it.id) }
        return AdvisorFacts(
            tick = e.tick.value,
            cashUnits = head.cash.wholeUnits(),
            netWorthUnits = head.netWorth.wholeUnits(),
            debtUnits = head.debt.wholeUnits(),
            monthRevenueUnits = head.revenue.wholeUnits(),
            monthNetIncomeUnits = head.netIncome.wholeUnits(),
            dailyPayrollUnits = work.dailyPayroll.wholeUnits(),
            facilities = facilities,
            vacancies = work.vacancies,
            averageMoralePpm = work.averageMoralePpm,
            cohorts = cohorts.size,
            underpaidCohorts = cohorts.count { it.wage < it.marketWage },
            industryCount = head.industries.size,
            researchPoints = research.points,
            researchIdle = research.points > 0L && research.active.isEmpty() && research.queued.isEmpty() && research.unlocked < research.total,
            techsUnlocked = research.unlocked,
            techsTotal = research.total,
            idleParcels = idleParcels,
            loanRatePpm = fin.loans.maxOfOrNull { it.annualRate.ppm },
            investedUnits = (e.bondRunner.worth(company, e.tick) + e.futuresRunner.worth(company)).wholeUnits(),
            listed = e.exchange.isListed(company),
            contractOffers = e.contracts.proposalsFor(company).size,
            netMarginPpm = fin.ratios.netMargin.ppm,
            currentRatioPpm = fin.ratios.currentRatio.ppm,
        )
    }
}

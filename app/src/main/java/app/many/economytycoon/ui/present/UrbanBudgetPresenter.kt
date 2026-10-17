package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.urban.BudgetView
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.ConcessionStatus
import app.many.economytycoon.urban.ConcessionView
import app.many.economytycoon.urban.ProjectStatus
import app.many.economytycoon.urban.ProjectView
import app.many.economytycoon.urban.Service
import app.many.economytycoon.urban.TaxRates
import app.many.economytycoon.urban.UrbanCommand

data class BudgetLine(val label: String, val amount: String, val tone: Tone)

data class ServiceRow(val service: Service, val label: String, val level: Int, val quality: Int, val cost: String, val tone: Tone, val canRaise: Boolean, val canCut: Boolean)

data class ProjectRow(val id: Long, val name: String, val kind: String, val phase: String, val status: String, val tone: Tone, val progress: String, val budget: String, val note: String?)

data class ConcessionRow(val id: Long, val name: String, val kind: String, val terms: String, val status: String, val tone: Tone, val facts: List<Fact>, val canTerminate: Boolean)

data class UrbanBudgetModel(
    val tiles: List<Tile>,
    val revenue: List<BudgetLine>,
    val expenses: List<BudgetLine>,
    val services: List<ServiceRow>,
    val projects: List<ProjectRow>,
    val concessions: List<ConcessionRow>,
    val taxFacts: List<Fact>,
)

/** The city budget: what comes in, what goes out, what is being built and who has been contracted. */
object UrbanBudgetPresenter {
    const val STEP = 50
    const val TAX_STEP_PPM = 1_000L

    fun ratingLabel(r: Int): String = when {
        r >= 800 -> "Excellent"
        r >= 600 -> "Good"
        r >= 400 -> "Fair"
        r >= 200 -> "Weak"
        else -> "Distressed"
    }

    fun projectRow(p: ProjectView, money: MoneyText): ProjectRow {
        val note = when {
            p.status == ProjectStatus.STALLED -> "Stalled; waiting for funds or a fix"
            p.overrunPpm > 0L -> "${Formatting.percentPpm(p.overrunPpm)} over the first estimate"
            p.delayDays > 0 -> "${p.delayDays} days late"
            else -> null
        }
        val tone = when (p.status) {
            ProjectStatus.ACTIVE -> if (p.overrunPpm > 200_000L) Tone.WARN else Tone.INFO
            ProjectStatus.STALLED -> Tone.BAD
            ProjectStatus.COMPLETED -> Tone.GOOD
            ProjectStatus.CANCELLED -> Tone.MUTED
        }
        return ProjectRow(
            p.id.value, p.name, p.kind.label, p.phase.label, p.status.name.lowercase().replaceFirstChar { it.uppercase() }, tone,
            Formatting.percentPpm(p.progressPpm), "${money(p.spent)} of ${money(p.budget)}" + if (p.fixedPrice) " (fixed price)" else "", note,
        )
    }

    fun concessionRow(c: ConcessionView, money: MoneyText): ConcessionRow {
        val tone = when (c.status) {
            ConcessionStatus.TENDER -> Tone.INFO
            ConcessionStatus.CONSTRUCTION, ConcessionStatus.OPERATING -> Tone.GOOD
            ConcessionStatus.NO_BIDS -> Tone.WARN
            ConcessionStatus.TERMINATED -> Tone.BAD
            ConcessionStatus.ENDED -> Tone.MUTED
        }
        val terms = buildString {
            append(c.model.label).append(", ${c.termMonths / 12} years")
            if (c.toll.isPositive) append(", charge ").append(money(c.toll))
            if (c.availabilityMonthly.isPositive) append(", ").append(money(c.availabilityMonthly)).append(" a month")
        }
        val facts = listOf(
            Fact("Bids", c.bids.toString(), Tone.NEUTRAL, if (c.bids > 0) "Best offer ${Formatting.percentPpm(c.bestDiscountPpm)} below the ask" else null),
            Fact("User charges collected", money(c.userChargesTotal)),
            Fact("Paid to the city", money(c.cityShareTotal)),
            Fact("Availability payments", money(c.availabilityPaidTotal)),
            Fact("Deductions for poor service", money(c.deductionsTotal), if (c.deductionsTotal.isPositive) Tone.WARN else Tone.NEUTRAL),
        )
        return ConcessionRow(c.id.value, c.name, c.kind.label, terms, c.status.label, tone, facts, c.status == ConcessionStatus.OPERATING || c.status == ConcessionStatus.CONSTRUCTION)
    }

    fun serviceRows(b: BudgetView, money: MoneyText): List<ServiceRow> = b.services.map {
        ServiceRow(it.service, it.service.label, it.level, it.quality, money(it.monthlyCost), Tones.health(it.quality * 1000L), it.level < 1000, it.level > 0)
    }

    fun build(c: CityView, money: MoneyText = { Formatting.compactMoney(it) }): UrbanBudgetModel {
        val b = c.budget
        val income = b.revenue.fold(Money.ZERO) { a, r -> a + r.second }
        val outgo = b.expenses.fold(Money.ZERO) { a, r -> a + r.second }
        val net = income - outgo
        val tiles = listOf(
            Tile("Monthly balance", if (b.hasReport) money(net) else "No month closed yet", if (!b.hasReport) Tone.MUTED else Tones.bySign(net.raw)),
            Tile("Cash", money(b.cash), if (b.cash.isNegative) Tone.BAD else Tone.NEUTRAL),
            Tile("Debt", money(b.debt), if (b.debt.raw * 10L > b.debtLimit.raw * 8L) Tone.BAD else Tone.NEUTRAL, "Limit ${money(b.debtLimit)}"),
            Tile("Credit rating", ratingLabel(b.rating), Tones.health(b.rating * 1000L), "Borrows at ${Formatting.percentPpm(b.interestRatePpm)} a year"),
            Tile("Services funded", Formatting.percentPpm(b.fundingRatioPpm), if (b.fundingRatioPpm < 950_000L) Tone.BAD else Tone.GOOD, if (b.deficitMonths > 0) "${b.deficitMonths} months in deficit" else null),
        )
        val taxFacts = listOf(
            Fact("Property tax a year", Formatting.percentPpm(b.rates.propertyPerYearPpm)),
            Fact("Income tax", Formatting.percentPpm(b.rates.incomePpm)),
            Fact("Business tax per sqm a month", money(b.rates.businessPerSqm)),
        )
        return UrbanBudgetModel(
            tiles,
            b.revenue.map { BudgetLine(it.first.label, money(it.second), Tone.NEUTRAL) },
            b.expenses.map { BudgetLine(it.first.label, money(it.second), Tone.NEUTRAL) },
            serviceRows(b, money),
            c.projects.map { projectRow(it, money) },
            c.concessions.map { concessionRow(it, money) },
            taxFacts,
        )
    }

    fun raiseService(city: CityId, r: ServiceRow): UrbanCommand = UrbanCommand.SetServiceLevel(city, r.service, (r.level + STEP).coerceAtMost(1000))
    fun cutService(city: CityId, r: ServiceRow): UrbanCommand = UrbanCommand.SetServiceLevel(city, r.service, (r.level - STEP).coerceAtLeast(0))

    fun incomeTax(city: CityId, rates: TaxRates, up: Boolean): UrbanCommand {
        val next = (rates.incomePpm + if (up) TAX_STEP_PPM else -TAX_STEP_PPM).coerceIn(0L, TaxRates.MAX_INCOME_PPM)
        return UrbanCommand.SetTaxRates(city, rates.copy(incomePpm = next))
    }

    fun propertyTax(city: CityId, rates: TaxRates, up: Boolean): UrbanCommand {
        val next = (rates.propertyPerYearPpm + if (up) TAX_STEP_PPM else -TAX_STEP_PPM).coerceIn(0L, TaxRates.MAX_PROPERTY_PPM)
        return UrbanCommand.SetTaxRates(city, rates.copy(propertyPerYearPpm = next))
    }

    fun borrow(city: CityId, amount: Money): UrbanCommand = UrbanCommand.Borrow(city, amount)
    fun repay(city: CityId, amount: Money): UrbanCommand = UrbanCommand.Repay(city, amount)
}

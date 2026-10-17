package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.SettlementStatus
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.macro.GdpKind
import app.many.economytycoon.production.TickContext

/** The named phases of a tick, in the order they run. */
enum class Phase {
    BEGIN_TICK, SCHEDULED_EVENTS, DECISIONS, COMMAND_APPLICATION, RESOURCE_AVAILABILITY, PROCUREMENT_AND_SHIPMENTS,
    PRODUCTION, DEMAND, MARKET_CLEARING, FULFILLMENT, FINANCIAL_SETTLEMENT, WORKFORCE_UPDATE, MACRO_UPDATE,
    INVARIANT_CHECKS, PUBLISH,
}

/** What happened in one tick, for the UI, logs and tests. */
class TickSummary(val tick: Tick) {
    var eventsApplied: Int = 0
    var aiActionsApplied: Int = 0
    var playerCommandsApplied: Int = 0
    var playerCommandsRejected: Int = 0
    var ordersPlaced: Int = 0
    var tradesExecuted: Int = 0
    var tradedValue: Money = Money.ZERO
    var consumerSpend: Money = Money.ZERO
    var batches: Long = 0
    var intentsSettled: Int = 0
    var intentsUnpaid: Int = 0
    var taxCollected: Money = Money.ZERO
    var dividendsPaid: Money = Money.ZERO
    var liquidations: Int = 0
    var takeovers: Int = 0
    var researchCompleted: Int = 0
    var contractsDelivered: Int = 0
    var contractsMissed: Int = 0
    var exchangeEvents: List<app.many.economytycoon.exchange.ExchangeEvent> = emptyList()
    var sharesTraded: Long = 0
    var tenderEvents: List<app.many.economytycoon.tenders.TenderEvent> = emptyList()
    var grantEvents: List<app.many.economytycoon.grants.GrantEvent> = emptyList()
    var marketingEvents: List<app.many.economytycoon.marketing.MarketingEvent> = emptyList()
    /** What the electricity grids did today; null in a game without grids. */
    var gridReport: app.many.economytycoon.energygrid.DayReport? = null
    var scenarioEvents: List<app.many.economytycoon.scenarios.ScenarioEvent> = emptyList()
    var propertyEvents: List<app.many.economytycoon.property.PropertyEvent> = emptyList()
    var futuresEvents: List<app.many.economytycoon.futures.FuturesEvent> = emptyList()
    var unionEvents: List<app.many.economytycoon.unions.UnionEvent> = emptyList()
    var bondEvents: List<app.many.economytycoon.bonds.BondEvent> = emptyList()
    var regulationEvents: List<app.many.economytycoon.regulation.RegulationEvent> = emptyList()
    var carbonEvents: List<app.many.economytycoon.carbon.CarbonEvent> = emptyList()
    var bankingEvents: List<app.many.economytycoon.banking.BankEvent> = emptyList()
    var ipEvents: List<app.many.economytycoon.ip.IpEvent> = emptyList()
    /** What the tourist destinations did today, and the month's tourism bills on the last day of a month; null in a game without tourism. */
    var tourismReport: app.many.economytycoon.tourism.DayReport? = null
    var tourismSettlement: app.many.economytycoon.tourism.SettlementRun? = null
    /** What happened in the cities' councils, works and concessions today; empty in a game without city plans. */
    var urbanNotices: List<app.many.economytycoon.urban.UrbanNotice> = emptyList()
    var riskEvents: List<app.many.economytycoon.risk.RiskEvent> = emptyList()
    var contractEvents: List<app.many.economytycoon.contracts.ContractEvent> = emptyList()
    var closedBooks: Boolean = false
    var inventoryAdjustment: Money = Money.ZERO
    var problems: List<String> = emptyList()
    val phasesRun = ArrayList<Phase>()
    /** What became of each command that was waiting for this tick, in the order they were submitted. */
    val commandOutcomes = ArrayList<CommandOutcome>()
}

/** The fate of one submitted command: applied, or refused with the rule it broke. */
data class CommandOutcome(val sequence: Long, val actor: Actor, val command: Command, val accepted: Boolean, val rule: String = "", val message: String = "")

/** A command waiting for its tick, with who asked for it. */
class QueuedCommand(val sequence: Long, val actor: Actor, val command: Command)

/**
 * Runs the economy forward one tick at a time through the fixed phase order. Each phase touches the
 * engines that own it and nothing else, so the order is the only coordination there is.
 */
class TickPipeline(
    private val e: Economy,
    private val executor: CommandExecutor,
    val events: EventQueue = EventQueue(),
) {
    private val fulfillment = Fulfillment(e)
    private val rules = StandingRules(e)
    private val supplier = ConstructionSupplier(e)
    private val ai = AiDriver(e, executor)
    private val aiContracts = AiContracts(e, executor)
    private val aiInvestors = AiInvestors(e)
    private val aiInsurance = AiInsurance(e, executor)
    private val aiTenders = AiTenders(e, executor)
    private val aiGrants = AiGrants(e, executor)
    private val aiMarketing = AiMarketing(e, executor)
    private val aiCarbon = AiCarbon(e, executor)
    private val aiIp = AiIp(e, executor)
    private val aiUtilities = AiUtilities(e)
    private val aiLobbying = AiLobbying(e, executor)
    private val aiBonds = AiBonds(e, executor)
    private val aiBargaining = AiBargaining(e, executor)
    private val aiHedging = AiHedging(e, executor)
    private val aiLandlords = AiLandlords(e, executor)
    private val aiHoteliers = AiHoteliers(e)
    private val aiUrban = AiUrban(e, executor)
    private val liquidator = Liquidator(e)
    private val generator = EventGenerator(e)

    /** Commands applied this tick, in order, for the command log. */
    val applied = ArrayList<LoggedCommand>()
    var checkInvariants: Boolean = false

    fun run(queue: List<QueuedCommand>): TickSummary {
        val tick = e.tick
        val s = TickSummary(tick)
        applied.clear()
        fun phase(p: Phase, body: () -> Unit) { s.phasesRun += p; body() }

        phase(Phase.BEGIN_TICK) {
            e.grid.beginTick()
            e.workforce.beginTick()
            e.grid.clearBills()
        }
        phase(Phase.SCHEDULED_EVENTS) {
            generator.generate(tick, events)
            for (ev in events.due(tick)) { apply(ev.event); s.eventsApplied++ }
        }
        phase(Phase.DECISIONS) {
            s.aiActionsApplied = ai.run(tick).actionsApplied
            aiContracts.run(tick)
            aiInvestors.run(tick)
            aiInsurance.run(tick)
            aiTenders.run(tick)
            aiGrants.run(tick)
            aiMarketing.run(tick)
            aiCarbon.run(tick)
            aiUtilities.run(tick)
            aiLobbying.run(tick)
            aiBonds.run(tick)
            aiBargaining.run(tick)
            aiHedging.run(tick)
            aiLandlords.run(tick)
            aiHoteliers.run(tick)
            aiUrban.run(tick)
            e.insolvency.step(tick)
        }
        phase(Phase.COMMAND_APPLICATION) {
            for ((i, q) in queue.withIndex()) {
                if (i >= e.config.maxCommandsPerTick) {
                    s.playerCommandsRejected++
                    s.commandOutcomes += CommandOutcome(q.sequence, q.actor, q.command, false, app.many.economytycoon.core.Rules.LIMIT_EXCEEDED, "too many commands in one tick")
                    continue
                }
                val r = executor.execute(q.actor, q.command)
                if (r is Outcome.Ok) {
                    s.playerCommandsApplied++
                    applied += LoggedCommand(q.sequence, tick.value, q.actor, q.command)
                    s.commandOutcomes += CommandOutcome(q.sequence, q.actor, q.command, true, message = r.value.message)
                } else {
                    s.playerCommandsRejected++
                    val why = (r as Outcome.Rejected).rejection
                    s.commandOutcomes += CommandOutcome(q.sequence, q.actor, q.command, false, why.rule, why.message)
                }
            }
        }
        phase(Phase.RESOURCE_AVAILABILITY) {
            e.laborSupply.update()
            e.tradeBoard.replenish(tick)
        }
        phase(Phase.PROCUREMENT_AND_SHIPMENTS) {
            val r = rules.plan(tick)
            s.ordersPlaced = r.sellOrders + r.buyOrders
            supplier.run(tick)
            s.tenderEvents = e.tenderRunner.run(tick)
            s.grantEvents = e.grantRunner.run(tick)
            e.energyGridRunner.advance(tick, s)
            s.regulationEvents = e.regulationRunner.run(tick)
            s.bondEvents = e.bondRunner.run(tick)
            s.unionEvents = e.unionRunner.run(tick)
            s.futuresEvents = e.futuresRunner.run(tick)
            s.propertyEvents = e.propertyRunner.run(tick)
            s.scenarioEvents = e.scenarioRunner.run(tick)
            val contractEvents = e.contractRunner.run(tick)
            s.contractEvents = contractEvents
            s.contractsDelivered = contractEvents.count { it is app.many.economytycoon.contracts.ContractEvent.Delivered || it is app.many.economytycoon.contracts.ContractEvent.Fulfilled }
            s.contractsMissed = contractEvents.count { it is app.many.economytycoon.contracts.ContractEvent.Missed }
            e.logistics.step(tick, e.random)
            e.tourismRunner.advance(tick, s)
            e.urbanRunner.advance(tick, s)
        }
        phase(Phase.PRODUCTION) {
            val ctx = TickContext(tick, e.random, e.labor, e.grid, e.resources, e.modifiers, e.prices)
            val report = e.production.step(ctx)
            s.batches = report.totalBatches
            for (f in report.facilities) {
                e.facilityStats.record(f)
                val produced = f.produced[e.electricity]
                if (produced != null && produced.raw > 0L) {
                    val facility = e.production.facility(f.facility)
                    if (facility != null) {
                        facility.inventory.remove(e.electricity, app.many.economytycoon.core.Quantity.min(produced, facility.inventory.available(e.electricity)))
                        // A region with a grid of its own takes power from the grid's units, not from this pool.
                        if (!e.energyGridRunner.covers(facility.region)) e.grid.recordProduction(facility.region, facility.owner, produced)
                    }
                }
            }
            s.carbonEvents = e.carbonRunner.run(tick, report)
            for ((company, amount) in e.payer.drainCapital()) {
                val country = e.companies.company(company)?.country ?: continue
                e.macro.record(country, GdpKind.INVESTMENT, amount)
            }
        }
        phase(Phase.DEMAND) {
            // Advertising is brought up to date first, so the market sees today's brand stocks. Its bills are settled with the day's other payments.
            val blows = e.marketingRunner.recordDamage(tick, s.unionEvents, s.regulationEvents)
            s.marketingEvents = blows + e.marketingRunner.advance(tick)
            for (c in e.world.countries()) {
                val shift = e.macro.demandShiftPpm(c.id)
                for (r in e.world.regionsOf(c.id)) for (p in e.content.allDemandProfiles) {
                    val base = e.tourismRunner.liftDemand(mulDiv(shift, events.demandShift(p.good), 1_000_000L), r.id)
                    e.markets.setDemandShift(app.many.economytycoon.markets.MarketKey(r.id, p.good), e.marketingRunner.scaleDemand(base, p.good, r.id))
                }
            }
        }
        var market: app.many.economytycoon.markets.MarketReport? = null
        phase(Phase.MARKET_CLEARING) {
            val report = e.markets.step(tick, e.random) { region -> householdBudget(region) }
            market = report
            s.tradesExecuted = report.trades.size
            s.tradedValue = report.tradedValue
        }
        var fulfilled: FulfillmentReport? = null
        phase(Phase.FULFILLMENT) {
            val m = market!!
            fulfilled = fulfillment.run(m)
            e.carbonRunner.assessTrade(tick, fulfilled!!.crossBorder)
            // The patent module takes its turn once the day's sales are on record; its payments join today's settlement.
            s.ipEvents = e.ipRunner.step(tick)
            s.consumerSpend = fulfilled!!.consumerSpend
            for (o in m.expired) e.releaseReservation(o)
        }
        phase(Phase.FINANCIAL_SETTLEMENT) { settle(tick, s, fulfilled!!) }
        phase(Phase.WORKFORCE_UPDATE) {
            e.workforce.step(tick, e.random)
            e.laborSupply.recordEmployment()
        }
        phase(Phase.MACRO_UPDATE) {
            e.world.updateTick(tick, e.random)
            val events = e.research.step(tick)
            s.researchCompleted = events.count { it is app.many.economytycoon.research.ResearchEvent.Completed }
            aiIp.onResearch(tick, events)
            e.companies.advanceDepartments { id -> Money.max(Money.units(1_000), wagesPerTick(id) * 30L) }
            e.companies.advanceBrands(
                { id -> e.companies.progress(id).efficiencyPpm.coerceAtLeast(0L) },
                { _, _ -> 700_000L },
                { _, _ -> 1_000_000L },
            )
            s.exchangeEvents = e.exchangeRunner.closeDay(tick)
            s.riskEvents = e.riskRunner.run(tick)
            val snaps = e.macro.step(tick, e.random)
            if (snaps.isNotEmpty()) {
                for (snap in snaps) {
                    e.finance.baseRate = Rate(snap.policyRatePpm)
                }
            }
        }
        phase(Phase.INVARIANT_CHECKS) {
            s.inventoryAdjustment = Reconciler.run(e, tick)
            if (checkInvariants) s.problems = Invariants.check(e)
        }
        phase(Phase.PUBLISH) {
            e.tick = Tick(tick.value + 1)
        }
        return s
    }

    private fun wagesPerTick(id: CompanyId): Money =
        Money(e.workforce.cohortsOf(id).sumOf { it.wage.raw * it.headcount })

    private fun apply(ev: EconomicEvent) {
        when (ev) {
            is EconomicEvent.SupplyShock -> e.markets.applyShockEverywhere(ev.good, ev.ppm)
            is EconomicEvent.DemandShift -> events.setDemandShift(ev.good, ev.ppm)
            is EconomicEvent.DemandRestored -> events.setDemandShift(ev.good, 1_000_000L)
            is EconomicEvent.ConfidenceShock -> e.macro.shock(ev.country, ev.ppm)
        }
    }

    /** Spendable household money in a region this tick: a slice of the pool in proportion to its people. */
    private fun householdBudget(region: RegionId): Money? {
        val total = e.world.totalPopulation()
        if (total <= 0L) return Money.ZERO
        val here = e.world.citiesIn(region).sumOf { e.world.population(it.id).total }
        val pool = e.finance.ledger.cash(e.finance.households).coerceAtLeast(Money.ZERO)
        val spendable = mulDiv(pool.raw, e.config.spendingPropensityPpm, 1_000_000L)
        return Money(mulDiv(spendable, here, total))
    }

    // ---- settlement ----------------------------------------------------------------------------

    private fun settle(tick: Tick, s: TickSummary, f: FulfillmentReport) {
        val households = e.finance.households
        val intents = ArrayList<SettlementIntent>()
        intents += f.taxIntents
        intents += e.workforce.payrollIntents(tick, households) { e.governmentOfCompany(it) }
        intents += gridIntents(tick)
        intents += e.energyGridRunner.settlementIntents(tick)
        intents += e.marketingRunner.settlementIntents(e.companies.departmentIntents(tick, households))
        val carbon = e.carbonRunner.drain()
        intents += carbon.payments
        // The banks' day: interest, fees, wages, tax and dividends join the same batch, after every other group so existing order is unchanged.
        val bank = e.bankingRunner.step(tick)
        intents += bank.second
        s.bankingEvents = bank.first
        // Royalties, patent fees, court awards and research credits: after every other group, so existing order is unchanged.
        intents += e.ipRunner.drain()
        // The month's tourism bills close the batch, after every other group, so existing order is unchanged.
        intents += e.tourismRunner.settlementIntents(tick, s)
        // The cities' concession payments and the mayors' compensation follow, again after every other group.
        intents += e.urbanRunner.drain()

        val results = e.finance.settleTick(tick, intents)
        for (draft in carbon.adjustments) e.finance.ledger.post(draft)
        val marketingEvents = e.marketingRunner.reportPayments(tick, results)
        if (marketingEvents.isNotEmpty()) s.marketingEvents = s.marketingEvents + marketingEvents
        for (r in results) {
            when (r.status) {
                SettlementStatus.PAID -> s.intentsSettled++
                SettlementStatus.DEFERRED, SettlementStatus.UNPAID -> s.intentsUnpaid++
                else -> Unit
            }
            if (r.paid.raw > 0L && r.intent.payee.kind == OwnerKind.GOVERNMENT) {
                val country = CountryId(r.intent.payee.id)
                e.macro.recordRevenue(country, r.paid)
                s.taxCollected += r.paid
            }
        }
        governmentSpending(tick)
        s.closedBooks = e.finance.closeIfDue(tick) { owner -> e.companies.byOwner(owner)?.let { e.governmentOf(it.country) } ?: e.finance.defaultGovernment() }
        if (s.closedBooks) e.insolvency.scanForInsolvency(tick)
        s.dividendsPaid = rules.payDividends(tick)
        val liquidated = liquidator.run(tick)
        s.liquidations = liquidated.size
        for (r in e.acquisitions.step(tick)) { e.afterTakeover(r); s.takeovers++ }
        e.grid.clearBills()
    }

    private fun gridIntents(tick: Tick): List<SettlementIntent> {
        val out = ArrayList<SettlementIntent>()
        for ((key, amount) in e.grid.billsByRegion()) {
            val (region, company) = key
            if (e.energyGridRunner.covers(region)) continue
            val payer = LedgerOwner.company(company)
            for ((payee, share) in e.grid.payees(region, amount)) {
                if (payee == company || share.raw <= 0L) continue
                val to = payee?.let { LedgerOwner.company(it) } ?: e.finance.households
                val credit = if (payee == null) Chart.REVENUE_OTHER else Chart.REVENUE_SERVICES
                out += SettlementIntent(
                    "energy:${company.value}:${region.value}:${payee?.value ?: 0}:${tick.value}", payer, to,
                    SettlementPriority.SUPPLIERS, ReasonCode.ENERGY,
                    listOf(PaymentLine(Chart.ENERGY_EXPENSE, credit, share)), deferrable = true, memo = "electricity",
                )
            }
        }
        return out
    }

    /** Governments pay benefits and run public services; a short treasury is topped up by the central bank. */
    private fun governmentSpending(tick: Tick) {
        for (c in e.world.countries().sortedBy { it.id }) {
            val cities = e.world.citiesOf(c.id)
            val unemployed = cities.sumOf { e.world.population(it.id).unemployed }
            val people = cities.sumOf { e.world.population(it.id).total }
            val amount = Money(e.config.benefitPerTick.raw * unemployed + e.config.publicSpendingPerCapita.raw * people)
            if (amount.raw <= 0L) continue
            val gov = LedgerOwner.government(c.id)
            val cash = e.finance.ledger.cash(gov)
            if (cash < amount) e.finance.mint(tick, gov, amount - cash, "gov:${c.id.value}:${tick.value}")
            e.finance.ledger.post(
                TransactionDraft(
                    "govspend:${c.id.value}:${tick.value}", tick, ReasonCode.TRANSFER,
                    listOf(
                        Entry(AccountRef(gov, Chart.OTHER_EXPENSE), amount), Entry(AccountRef(gov, Chart.CASH), -amount),
                        Entry(AccountRef(e.finance.households, Chart.CASH), amount), Entry(AccountRef(e.finance.households, Chart.REVENUE_OTHER), -amount),
                    ),
                    "public spending",
                ),
            )
            e.macro.record(c.id, GdpKind.GOVERNMENT, amount)
            e.macro.recordSpending(c.id, amount)
        }
    }
}

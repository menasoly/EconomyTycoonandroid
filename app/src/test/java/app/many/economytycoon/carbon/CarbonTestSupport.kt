package app.many.economytycoon.carbon

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.TransactionDraft

/** Shared fixtures for the carbon tests: a service on a fake environment and helpers to feed it days of activity. */
internal object CarbonTestSupport {
    val coal = GoodId("coal")
    val uk = CountryId(1)
    val de = CountryId(2)
    val acme = CompanyId(1)
    val borea = CompanyId(2)
    val cyrus = CompanyId(3)

    fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value
    fun Outcome<*>.rule(): String? = rejectionOrNull()?.rule

    /** A facility burning [coalUnits] of coal a day, making steel. */
    fun emitter(
        facility: Long, company: CompanyId, country: CountryId = uk, coalUnits: Long = 100, family: IndustryFamily = IndustryFamily.METALS,
        output: Money = Money.units(4_000), electricity: Quantity = Quantity.ZERO, produced: Quantity = Quantity.ZERO,
    ) = FacilityActivity(
        FacilityId(facility), company, country, FacilityTypeId("steel_mill"), family,
        consumed = if (coalUnits > 0) mapOf(coal to Quantity.units(coalUnits)) else emptyMap(),
        batchesByRecipe = emptyMap(), electricityUsed = electricity, electricityProduced = produced, outputValue = output,
        energySpend = Money.units(coalUnits * 20),
    )

    class Fixture(seed: Long = 7L, val params: CarbonParams = CarbonParams()) {
        val cash = HashMap<CompanyId, Money>()
        val treasury = HashMap<CountryId, Money>()
        val owners = HashMap<FacilityId, CompanyId>()
        val inactive = HashSet<CompanyId>()
        val state = CarbonState()
        val env = FixedEnvironment(
            cashOf = { cash[it] ?: Money.units(50_000_000) }, treasury = { treasury[it] ?: Money.units(500_000_000) },
            owners = { owners[it] }, active = { it !in inactive },
        )
        val service = CarbonService(state, env, EmissionCalculator(StandardFactors.table), RandomProvider(seed), params)
        val views = CarbonViews(service)
        val events = ArrayList<CarbonEvent>()
        val payments = ArrayList<SettlementIntent>()
        val adjustments = ArrayList<TransactionDraft>()

        fun own(vararg activities: FacilityActivity) { for (a in activities) owners[a.facility] = a.company }

        fun step(day: Long, activities: List<FacilityActivity>): CarbonStepResult {
            own(*activities.toTypedArray())
            val r = service.advance(Tick(day), activities)
            events += r.events
            payments += r.settlement.payments
            adjustments += r.settlement.adjustments
            return r
        }

        /** Runs days [from]..[to] inclusive with the same activities every day. */
        fun run(from: Long, to: Long, activities: List<FacilityActivity>) { for (d in from..to) step(d, activities) }

        fun exec(c: CarbonCommand, day: Long = 0): Outcome<CarbonEffect> {
            val o = service.execute(c, Tick(day))
            if (o is Outcome.Ok) { payments += o.value.settlement.payments; adjustments += o.value.settlement.adjustments }
            return o
        }

        inline fun <reified E : CarbonEvent> eventsOf(): List<E> = events.filterIsInstance<E>()
    }

    /**
     * A busy few hundred days: a cap-and-trade market with bids and orders, offset purchases and a project, an abatement
     * measure, a border policy, and a second country under a carbon tax.
     */
    fun busy(fx: Fixture, days: Long = 470) {
        val plants = listOf(emitter(1, acme), emitter(2, borea), emitter(3, cyrus, coalUnits = 40), emitter(4, acme, country = de, coalUnits = 60))
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.service.installRegime(RegimePresets.carbonTax(de, 2000, Money.units(12), threshold = Tonnes.of(100)), Tick(0)).value()
        fx.service.setBorderPolicy(BorderPolicy(uk, startYear = 2000)).value()
        fx.run(0, 20, plants)
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(300), Money.units(22)), 20)
        fx.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(200), Money.units(18)), 20)
        fx.exec(CarbonCommand.PlaceOrder(cyrus, uk, PermitSide.SELL, Tonnes.of(100), Money.units(21)), 20)
        fx.exec(CarbonCommand.PlaceOrder(acme, uk, PermitSide.BUY, Tonnes.of(60), Money.units(23)), 20)
        fx.exec(CarbonCommand.BuyOffsets(borea, OffsetKind.METHANE_CAPTURE, Tonnes.of(400)), 20)
        fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.CLEAN_COOKING, de, Tonnes.of(2_000)), 20)
        fx.exec(CarbonCommand.StartAbatement(acme, FacilityId(1), AbatementKind.FUEL_SWITCH), 20)
        fx.exec(CarbonCommand.SetPolicy(borea, uk, autoOffsets = true, autoBorrow = true), 20)
        fx.run(21, days, plants)
    }

    fun capAndTrade(cap: Long = 12_000, free: Long = 500_000L, from: Int = 2000, country: CountryId = uk) =
        RegimePresets.capAndTrade(country, from, Tonnes.of(cap), freeSharePpm = free, threshold = Tonnes.of(100))

    /** A year of ticks. */
    const val YEAR = CarbonCalendar.DAYS_PER_YEAR.toLong()
}

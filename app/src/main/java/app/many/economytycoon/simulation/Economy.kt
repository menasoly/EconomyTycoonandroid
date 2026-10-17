package app.many.economytycoon.simulation

import app.many.economytycoon.companies.AcquisitionService
import app.many.economytycoon.companies.CompanyService
import app.many.economytycoon.companies.InsolvencyService
import app.many.economytycoon.competitors.CompetitorService
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.contracts.ContractService
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.exchange.ExchangeService
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.TaxPolicy
import app.many.economytycoon.logistics.LogisticsService
import app.many.economytycoon.macro.MacroService
import app.many.economytycoon.markets.MarketService
import app.many.economytycoon.markets.WorldPopulationView
import app.many.economytycoon.production.NoModifiers
import app.many.economytycoon.production.ProductionService
import app.many.economytycoon.research.ResearchModifiers
import app.many.economytycoon.research.ResearchService
import app.many.economytycoon.research.TechEffects
import app.many.economytycoon.workforce.LaborMarkets
import app.many.economytycoon.workforce.WorkforceService
import app.many.economytycoon.world.WorldGenerator
import app.many.economytycoon.world.WorldParams
import app.many.economytycoon.world.WorldSize
import app.many.economytycoon.world.WorldState

data class SimConfig(
    val seed: Long,
    val worldSize: WorldSize = WorldSize.SMALL,
    val aiCompanies: Int = 6,
    /** Cash each inhabitant starts with, in whole currency units. */
    val householdCashPerCapita: Long = 150,
    /** Capital of the central bank's lending arm, as a multiple of household cash. */
    val bankCapitalMultiple: Long = 2,
    val governmentTreasury: Money = Money.units(2_000_000),
    /** Share of household cash households are willing to spend on goods per tick, ppm. */
    val spendingPropensityPpm: Long = 18_000,
    val aiStartingCapital: Money = Money.units(1_500_000),
    val playerStartingCapital: Money = Money.units(500_000),
    val maxCommandsPerTick: Int = 200,
    val tradeBoardCapital: Money = Money.units(5_000_000),
    /** Goods the trade board imports even though some recipe makes them. */
    val importAlso: List<String> = listOf("diesel", "fertilizer", "seeds", "spare_parts"),
    val importMarkupPpm: Long = 100_000,
    val consumerImportMarkupPpm: Long = 250_000,
    val benefitPerTick: Money = Money.units(1),
    val publicSpendingPerCapita: Money = Money(500),
    /** One unit of each imported good is stocked per this many inhabitants of a region. */
    val importPeoplePerUnit: Long = 4,
)

/** The parts of a saved game that other engines are built around, read before the economy is assembled. */
class RestoredCore(val ids: IdAllocators, val world: WorldState, val finance: FinanceService, val tick: Tick)

/**
 * The whole simulated economy: every engine, built in dependency order and sharing one clock, one random
 * source and one set of id allocators. The tick pipeline and command executor operate on this aggregate.
 */
class Economy(val config: SimConfig, val content: ContentRepository, restored: RestoredCore? = null) {
    var tick: Tick = restored?.tick ?: Tick.ZERO
        internal set

    val ids = restored?.ids ?: IdAllocators()
    val random = RandomProvider(config.seed)
    val world: WorldState = restored?.world ?: WorldGenerator(content, ids).generate(WorldParams(config.seed, config.worldSize))

    val finance = restored?.finance ?: FinanceService(ids)
    val companies = CompanyService(ids, finance)
    val insolvency = InsolvencyService(finance, companies)
    val acquisitions = AcquisitionService(finance, companies)

    val catalog = RoleCatalogs.from(content)
    val laborMarkets = LaborMarkets(catalog)
    val workforce = WorkforceService(ids, catalog, laborMarkets)
    val laborSupply = LaborSupplyUpdater(world, content, workforce, laborMarkets)

    val production = ProductionService(ids, content)
    val logistics = LogisticsService(ids, content, world, production)
    val markets = MarketService(ids, content, WorldPopulationView(world))

    val electricity: GoodId = GoodId("electricity")
    val grid = Grid(electricity, content.good(electricity).referencePrice.perUnit)

    val payer = LedgerPayer(finance, { LedgerOwner.government(it) }, { tick })
    val research = ResearchService(content, DepartmentFunding(companies), licensePayer = LedgerLicenses(finance, { tick }))
    val techEffects = TechEffects(research)
    val carbonState = app.many.economytycoon.carbon.CarbonState()
    val modifiers: app.many.economytycoon.production.ProductionModifiers =
        IpModifiers(CarbonModifiers(ResearchModifiers(techEffects), carbonState, app.many.economytycoon.carbon.StandardFactors.table, content), this)
    val macro = MacroService(view = WorldMacroView(world, markets, { tick }))
    val outlook = WorldOutlook(content, markets, laborMarkets, world, grid)
    val competitors = CompetitorService(content, outlook)

    val contracts = ContractService(ids)
    val exchange = ExchangeService()
    val risk = app.many.economytycoon.risk.RiskService(ids)
    val tenders = app.many.economytycoon.tenders.TenderService(ids)
    val tenderRunner = TenderRunner(this)
    val grants = app.many.economytycoon.grants.GrantService(ids)
    val grantRunner = GrantRunner(this)
    val marketing = app.many.economytycoon.marketing.MarketingSystem(worldSeed = config.seed)
    val marketingRunner = MarketingRunner(this)
    val energyGrids = app.many.economytycoon.energygrid.EnergyGridState()
    val energyGridService = app.many.economytycoon.energygrid.EnergyGridService(energyGrids)
    val energyGridExecutor = app.many.economytycoon.energygrid.GridCommandExecutor(energyGridService)
    val energyGridRunner = EnergyGridRunner(this)
    val carbon = app.many.economytycoon.carbon.CarbonService(
        carbonState, CarbonLedgerEnvironment(this),
        app.many.economytycoon.carbon.EmissionCalculator(app.many.economytycoon.carbon.StandardFactors.table, content), random,
        app.many.economytycoon.carbon.CarbonParams(efficiencyRealisedInProduction = true),
    )
    val carbonViews = app.many.economytycoon.carbon.CarbonViews(carbon)
    val carbonRunner = CarbonRunner(this)
    /** Patents, licences, disputes and secrets. The engine is rebuilt around a loaded state; it holds nothing before [openIp]. */
    var ip: app.many.economytycoon.ip.IpEngine = newIpEngine(app.many.economytycoon.ip.IpState(config.seed))
        private set
    /** True once the patent office has been opened (or a saved one loaded); before that nothing is saved, hashed or run. */
    var ipActive: Boolean = false
        private set
    val ipSales = IpSalesLog()
    val ipContext = EconomyIpContext(this)
    val ipRunner = IpRunner(this)
    /** Destinations, hotels, attractions, operators and booking channels; empty (and not saved, hashed or run) until [Command.EstablishTourism]. */
    val tourism = app.many.economytycoon.tourism.TourismState()
    val tourismService = app.many.economytycoon.tourism.TourismService(tourism)
    val tourismExecutor = app.many.economytycoon.tourism.TourismCommandExecutor(tourismService)
    val tourismRunner = TourismRunner(this)
    /** City plans (districts, land value, housing, transit, budget, council, concessions); empty (and not saved, hashed or run) until [Command.EstablishUrban]. */
    val urban = app.many.economytycoon.urban.UrbanState(app.many.economytycoon.urban.UrbanParams(), config.seed)
    val urbanService = app.many.economytycoon.urban.UrbanService(urban)
    val urbanRunner = UrbanRunner(this)
    /** Skills, education, training, migration and poaching; empty (and not saved, hashed or run) until [Command.EstablishTalent]. */
    val talent = app.many.economytycoon.talent.TalentSystem(
        app.many.economytycoon.talent.TalentState(), app.many.economytycoon.talent.DefaultTaxonomy.forRoles(content.allRoles), config.seed,
    )
    /** True once the talent module has been switched on (or a saved one loaded); before that nothing is saved, hashed or run. */
    var talentActive: Boolean = false
        private set
    val talentRunner = TalentRunner(this)
    val banking = app.many.economytycoon.banking.BankingService()
    val bankingInputs = MacroBankingInputs(this)
    val bankingRunner = BankingRunner(this)
    val regulation = app.many.economytycoon.regulation.RegulationService(ids)
    val regulationRunner = RegulationRunner(this)
    val bonds = app.many.economytycoon.bonds.BondService(ids)
    val bondRunner = BondRunner(this)
    val unions = app.many.economytycoon.unions.UnionService(ids)
    val unionRunner = UnionRunner(this)
    val futures = app.many.economytycoon.futures.FuturesService(ids)
    val futuresRunner = FuturesRunner(this)
    val property = app.many.economytycoon.property.PropertyService()
    val propertyRunner = PropertyRunner(this)
    val scenario = app.many.economytycoon.scenarios.ScenarioService()
    val scenarioRunner = ScenarioRunner(this)
    val riskRunner = RiskRunner(this)
    val exchangeRunner = ExchangeRunner(this)
    val contractRunner = ContractRunner(this)
    val tradeBoard = TradeBoard(this)
    val aiReserve = AiReserve(this)
    val facilityStats = FacilityStats()
    val labor = WorkforceLabor(workforce)
    val resources = WorldResources(world)
    val prices = MarketPrices(markets)

    init {
        production.payer = payer
        production.hasTech = { c, t -> research.isUnlocked(c, t) }
        logistics.payer = payer
        for (c in world.countries()) {
            val gov = LedgerOwner.government(c.id)
            // A restored finance service already carries the policies in force when the game was saved.
            if (restored == null) {
                finance.setTaxPolicy(
                    gov,
                    TaxPolicy(corporateRate = Rate(c.corporateTaxPpm), salesRate = Rate(c.salesTaxPpm), payrollRate = Rate(c.payrollTaxPpm)),
                )
            }
            for (r in world.regionsOf(c.id)) laborMarkets.setMinimumWage(r.id, Money(c.minimumWagePerTick * Money.SCALE))
        }
        for (r in world.regions()) grid.setBaseline(r.id, world.citiesIn(r.id).sumOf { world.population(it.id).total })
    }

    private fun newIpEngine(state: app.many.economytycoon.ip.IpState) = app.many.economytycoon.ip.IpEngine(
        config.seed, content, app.many.economytycoon.ip.TechHolding { company, tech -> research.isUnlocked(company, tech) },
        app.many.economytycoon.ip.IpParams(), state,
    )

    /** Opens the patent office on a new game: the scripted patent holders appear. */
    internal fun openIp() {
        ip.start()
        ipActive = true
    }

    /**
     * Switches the talent module on. From here the skill term of productivity comes from the module for every cohort it
     * knows (a factor of exactly the plain skill number's for any other), and the qualified share of a region's people
     * decides how its job seekers split over the skill tiers.
     */
    internal fun openTalent() {
        talentActive = true
        workforce.skillSource = { key -> talent.skills.skillPpm(key) }
        laborSupply.educationSource = { region ->
            talent.education.educationIndex(region, talentRunner.world.workingAge(region)).takeIf { talent.education.qualifications(region).byKind.any { n -> n > 0L } }
        }
    }

    /** Puts a loaded patent state in place of the empty one. */
    internal fun restoreIp(state: app.many.economytycoon.ip.IpState) {
        ip = newIpEngine(state)
        ipActive = true
    }

    fun governmentOf(country: CountryId): LedgerOwner = LedgerOwner.government(country)

    fun governmentOfCompany(company: CompanyId): LedgerOwner =
        companies.company(company)?.let { LedgerOwner.government(it.country) } ?: finance.defaultGovernment()

    /** Founding capital is created with the share register entry; nothing further to move. */
    fun fundNewCompany(company: app.many.economytycoon.companies.Company, capital: Money) {
        companies.progress(company.id)
        companies.budgets(company.id)
    }

    /** Frees the inventory reservation behind a SELL order that is no longer on the book. */
    fun releaseReservation(order: app.many.economytycoon.markets.Order) {
        if (order.side != app.many.economytycoon.markets.Side.SELL) return
        val remaining = order.remaining
        if (remaining.raw <= 0L) return
        val f = production.facility(app.many.economytycoon.core.FacilityId(order.source)) ?: return
        f.inventory.release(order.key.good, remaining)
    }

    /** Removes every live commitment of a company that has ceased to exist. */
    fun retire(id: CompanyId) {
        for (o in markets.cancelAll(app.many.economytycoon.markets.Party.company(id))) releaseReservation(o)
        workforce.dissolve(id)
        for (f in production.facilitiesOf(id)) production.shutdown(id, f.id)
        contracts.endAllOf(id, tick)
        exchangeRunner.forget(id)
        ipRunner.forget(id, tick)
        riskRunner.forget(id, tick)
        tenderRunner.forget(id, tick)
        tourismRunner.forget(id, tick)
        urbanRunner.forget(id, tick)
        grantRunner.forget(id)
        talentRunner.forget(id, tick)
        marketingRunner.forget(id, tick)
        energyGridRunner.forget(id, tick)
        bondRunner.forget(id)
        unionRunner.forget(id, tick)
        futuresRunner.forget(id, tick)
        propertyRunner.forget(id, tick)
        research.remove(id)
        competitors.remove(id)
        companies.retire(id)
    }

    /** After a takeover that gives control, the target's research joins the buyer and groups are rebuilt. */
    fun afterTakeover(result: app.many.economytycoon.companies.AcquisitionResult) {
        if (!result.controlGained) return
        val offer = result.offer
        research.absorb(offer.acquirer, offer.target, tick)
        companies.groups.rebuild(finance.equity, companies.all())
    }

    fun hash(): String {
        val h = CanonicalHasher()
        h.putLong(tick.value)
        world.hashInto(h)
        finance.hashInto(h)
        companies.hashInto(h)
        insolvency.hashInto(h)
        acquisitions.hashInto(h)
        workforce.hashInto(h)
        production.hashInto(h)
        logistics.hashInto(h)
        markets.hashInto(h)
        grid.hashInto(h)
        facilityStats.hashInto(h)
        research.hashInto(h)
        contracts.hashInto(h)
        exchange.hashInto(h)
        risk.hashInto(h)
        tenders.hashInto(h)
        grants.hashInto(h)
        if (!marketing.state.isPristine()) marketing.state.hashInto(h)
        if (!energyGrids.isEmpty) energyGrids.hashInto(h)
        if (!carbonState.isPristine) carbonState.hashInto(h)
        if (!banking.isPristine) banking.hashInto(h)
        if (ipActive) { ip.state.hashInto(h); ipSales.hashInto(h) }
        if (!tourism.isEmpty) tourism.hashInto(h)
        if (urban.size > 0) urban.hashInto(h)
        if (talentActive) talent.state.hashInto(h)
        regulation.hashInto(h)
        bonds.hashInto(h)
        unions.hashInto(h)
        futures.hashInto(h)
        property.hashInto(h)
        scenario.hashInto(h)
        macro.hashInto(h)
        competitors.hashInto(h)
        for ((name, state) in random.snapshot()) h.putString(name).putLong(state)
        return h.finish().toHex()
    }
}

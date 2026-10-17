package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** A company's year waiting to be settled. */
data class PendingView(
    val year: Int,
    val verified: Tonnes,
    val obligation: Tonnes,
    val surrendered: Tonnes,
    val offsets: Tonnes,
    val borrowed: Tonnes,
    val outstanding: Tonnes,
    val deadline: Tick,
    val daysLeft: Int,
    /** Permits the company holds that can be handed in for this year. */
    val usablePermits: Tonnes,
    /** How much more could be met with credits. */
    val offsetAllowance: Tonnes,
    val offsetsHeld: Tonnes,
) {
    /** What would still be owed if the deadline were today and the company let the registry take what it holds and retire credits. */
    val projectedShortfall: Tonnes
        get() = (outstanding - usablePermits - Tonnes.min(offsetAllowance, offsetsHeld)).coerceAtLeast(Tonnes.ZERO)
}

/** One company's position in one country. */
data class AccountView(
    val country: CountryId,
    val kind: RegimeKind,
    val year: Int,
    val covered: Tonnes,
    val projectedCovered: Tonnes,
    val footprint: Tonnes,
    val daysObserved: Int,
    val price: Money,
    val permitsHeld: Tonnes,
    val permitsCost: Money,
    /** Permits that lapse at the start of next year if they are not used. */
    val expiringNext: Tonnes,
    val freeThisYear: Tonnes,
    val violations: Int,
    val carry: Tonnes,
    val policy: CompliancePolicy,
    val pending: PendingView?,
    val last: ComplianceRecord?,
) {
    /** Cost of the year's projected emissions at today's price, before free permits and permits already held. */
    val projectedBill: Money get() = projectedCovered.cost(price)
}

data class AbatementView(
    val id: AbatementId,
    val facility: FacilityId,
    val kind: AbatementKind,
    val status: AbatementStatus,
    val capex: Money,
    val annualAbatement: Tonnes,
    val completesAt: Tick,
    val subsidy: Money,
)

data class OffsetProjectView(
    val id: OffsetProjectId,
    val kind: OffsetKind,
    val status: OffsetStatus,
    val annual: Tonnes,
    val issued: Tonnes,
    val buffer: Tonnes,
    val nextIssuance: Tick,
)

/** Everything the company screen and the AI need about one company. */
data class CompanyCarbonView(
    val company: CompanyId,
    val year: Int,
    val accounts: List<AccountView>,
    val rating: CarbonRating?,
    val offsetsHeld: Map<OffsetKind, Tonnes>,
    val offsetProjects: List<OffsetProjectView>,
    val abatement: List<AbatementView>,
    val options: List<AbatementOption>,
    val statements: List<AnnualStatement>,
) {
    val footprint: Tonnes get() = Tonnes.sum(accounts.map { it.footprint })
    val projectedBill: Money get() = Money.sum(accounts.map { it.projectedBill })
    val offsetsTotal: Tonnes get() = Tonnes.sum(offsetsHeld.values)
    fun account(country: CountryId): AccountView? = accounts.firstOrNull { it.country == country }
    val hasPending: Boolean get() = accounts.any { it.pending != null }
}

data class SupplyView(
    val year: Int,
    val cap: Tonnes,
    val free: Tonnes,
    val auctionPlanned: Tonnes,
    val withheld: Tonnes,
    val released: Tonnes,
    val offered: Tonnes,
    val sold: Tonnes,
    val remaining: Tonnes,
    val auctionsHeld: Int,
)

/** A permit market as the market screen shows it. */
data class MarketView(
    val country: CountryId,
    val kind: RegimeKind,
    val year: Int,
    val price: Money,
    val floor: Money,
    val ceiling: Money,
    val nextAuction: Tick?,
    val supply: SupplyView?,
    val reserve: Tonnes,
    val circulation: Tonnes,
    val bestBid: Money?,
    val bestAsk: Money?,
    val bids: List<BookLevel>,
    val asks: List<BookLevel>,
    val auctions: List<AuctionResult>,
    val trades: List<PermitTrade>,
    val prices: List<Money>,
    val cancelled: Tonnes,
    val expired: Tonnes,
    val greenFund: Money,
    val receipts: Money,
)

/** A row of the national league table. */
data class LeagueRow(val company: CompanyId, val footprint: Tonnes, val intensityGrams: Long, val covered: Tonnes, val grade: CarbonGrade?)

/** Builds read models from the service. Nothing here changes any state. */
class CarbonViews(private val service: CarbonService) {
    private val state get() = service.state
    private val env get() = service.env

    /** Options open to each of a company's facilities, cheapest per tonne first. */
    fun options(company: CompanyId): List<AbatementOption> {
        val out = ArrayList<AbatementOption>()
        for (record in state.inventory.facilitiesOf(company)) {
            if (env.ownerOf(record.facility) != company) continue
            out += service.catalog.optionsFor(record, env.buildCost(record.facility))
        }
        return out.sortedWith(compareBy<AbatementOption>({ it.costPerTonne }, { it.facility.value }, { it.kind.ordinal }))
    }

    /** The marginal abatement cost curve of the company's facilities in [country]. */
    fun macCurve(company: CompanyId, country: CountryId): MacCurve {
        val here = state.inventory.facilitiesOf(company).filter { it.country == country }.map { it.facility }.toSet()
        return MacCurve(options(company).filter { it.facility in here })
    }

    fun company(company: CompanyId, tick: Tick): CompanyCarbonView {
        val year = CarbonCalendar.yearOf(tick)
        val accounts = ArrayList<AccountView>()
        val countries = java.util.TreeSet<CountryId>()
        state.inventory.companyYearsOf(company).filter { it.year == year }.forEach { countries += it.country }
        state.compliance.accountsOf(company).forEach { countries += it.key.country }
        for (country in countries) accounts += accountView(company, country, year, tick)
        return CompanyCarbonView(
            company, year, accounts, state.reports.rating(company),
            OffsetKind.values().filter { state.offsets.held(company, it).isPositive }.associateWith { state.offsets.held(company, it) },
            state.offsets.projectsOf(company).map { OffsetProjectView(it.id, it.kind, it.status, it.annual, it.issued, it.buffer, it.nextIssuance) },
            state.abatement.projectsOf(company).map { AbatementView(it.id, it.facility, it.kind, it.status, it.capex, it.annualAbatement, it.completesAt, it.subsidy) },
            options(company), state.reports.statementsOf(company),
        )
    }

    private fun accountView(company: CompanyId, country: CountryId, year: Int, tick: Tick): AccountView {
        val regime = state.regime(country)
        val step = regime?.stepFor(year)
        val cy = state.inventory.company(company, country, year)
        val account = state.compliance.account(company, country)
        val market = state.market(country)
        val covered = if (cy == null || step == null) Tonnes.ZERO else coveredOf(cy, step)
        val days = cy?.days ?: 0
        val projected = if (days <= 0) covered else Tonnes(mulDiv(covered.raw, CarbonCalendar.DAYS_PER_YEAR.toLong(), days.toLong().coerceAtLeast(1L)))
        val pending = account?.pending?.let { p ->
            val usable = if (regime == null || market == null) Tonnes.ZERO else market.holdings.held(company, service.firstValidVintage(regime, p.year), p.year)
            val eligible = regime?.eligibleOffsets.orEmpty()
            PendingView(
                p.year, p.verified, p.obligation, p.surrendered, p.offsets, p.borrowed, p.outstanding, p.deadline,
                (p.deadline.value - tick.value).toInt().coerceAtLeast(0), usable,
                if (regime == null) Tonnes.ZERO else service.offsetAllowance(regime, p),
                Tonnes.sum(eligible.map { state.offsets.held(company, it) }),
            )
        }
        return AccountView(
            country, regime?.kind ?: RegimeKind.NONE, year, covered, projected, cy?.footprint ?: Tonnes.ZERO, days, service.carbonPrice(country, year),
            market?.holdings?.total(company) ?: Tonnes.ZERO, market?.holdings?.totalCost(company) ?: Money.ZERO,
            expiring(market, regime, company, year), account?.freeThisYear ?: Tonnes.ZERO,
            account?.violations ?: 0, account?.carry ?: Tonnes.ZERO, account?.policy ?: CompliancePolicy(), pending, account?.lastRecord(),
        )
    }

    private fun expiring(market: PermitMarket?, regime: CarbonRegime?, company: CompanyId, year: Int): Tonnes {
        if (market == null || regime == null) return Tonnes.ZERO
        val lapsing = if (regime.bankingAllowed) year - regime.vintageLifeYears else year - 1
        return market.holdings.lotsOf(company).filter { it.vintage == lapsing }.fold(Tonnes.ZERO) { acc, l -> acc + l.tonnes }
    }

    fun market(country: CountryId, tick: Tick): MarketView? {
        val regime = state.regime(country) ?: return null
        val year = CarbonCalendar.yearOf(tick)
        val step = regime.stepFor(year)
        val market = state.market(country)
        val supply = market?.supply(year)
        return MarketView(
            country, regime.kind, year, service.carbonPrice(country, year), step?.floor ?: Money.ZERO, step?.ceiling ?: Money.ZERO,
            if (market == null || !regime.tradesPermits) null else nextAuction(tick, supply),
            supply?.let { SupplyView(it.year, it.cap, it.free, it.auctionPlanned, it.withheld, it.released, it.offered, it.sold, it.remaining, it.auctionsHeld) },
            market?.reserve ?: Tonnes.ZERO, market?.holdings?.totalAll() ?: Tonnes.ZERO, market?.bestBid(), market?.bestAsk(),
            market?.let { OrderMatcher.depth(it.orders(), PermitSide.BUY) }.orEmpty(), market?.let { OrderMatcher.depth(it.orders(), PermitSide.SELL) }.orEmpty(),
            market?.auctions().orEmpty(), market?.trades().orEmpty(), market?.prices().orEmpty(), market?.cancelled ?: Tonnes.ZERO, market?.expired ?: Tonnes.ZERO,
            state.greenFund(country), state.receipts(country),
        )
    }

    /** The day of the next auction on or after [tick], or null if this year's auctions are done. */
    private fun nextAuction(tick: Tick, supply: YearSupply?): Tick? {
        val day = CarbonCalendar.dayOfYear(tick)
        val start = CarbonCalendar.startOfYear(CarbonCalendar.yearOf(tick))
        for (q in 0 until AUCTIONS_PER_YEAR) {
            val d = q * DAYS_PER_AUCTION_QUARTER + service.params.auctionDayOfQuarter
            if (d >= day && (supply == null || supply.auctionsHeld <= q || d > day)) return start + d.toLong()
        }
        return null
    }

    fun national(country: CountryId, year: Int): NationalSummary? = NationalReports.summarise(state.inventory, country, year)

    /** Companies in a country ranked from the cleanest per unit of output. */
    fun league(country: CountryId, year: Int): List<LeagueRow> =
        state.inventory.companiesIn(country, year)
            .filter { it.outputValue.isPositive }
            .map { LeagueRow(it.company, it.footprint, it.intensityGramsPerUnit, it.covered, state.reports.rating(it.company)?.grade) }
            .sortedWith(compareBy<LeagueRow>({ it.intensityGrams }, { it.company.value }))

    /** Where a company's intensity sits against its industry peers in [country], in ppm of the median, or null without peers. */
    fun intensityVersusPeers(company: CompanyId, country: CountryId, year: Int): Long? {
        val years = state.inventory.companiesIn(country, year)
        val mine = years.firstOrNull { it.company == company } ?: return null
        val bench = Benchmarks.forFamily(Benchmarks.compute(years), mine.dominantFamily()) ?: return null
        return bench.ratioPpm(mine.intensityGramsPerUnit)
    }

    /** Share of a country's direct emissions that its policy prices, in ppm. */
    fun coveragePpm(country: CountryId, year: Int): Long {
        val n = state.inventory.national(country, year) ?: return 0L
        return if (n.direct.raw <= 0L) 0L else mulDiv(n.covered.raw, Rate.ONE_PPM, n.direct.raw, RoundingMode.HALF_EVEN)
    }
}

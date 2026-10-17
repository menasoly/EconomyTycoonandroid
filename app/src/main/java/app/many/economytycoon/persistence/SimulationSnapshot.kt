package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyCodec
import app.many.economytycoon.competitors.CompetitorCodec
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.FinanceCodec
import app.many.economytycoon.finance.FinanceConfig
import app.many.economytycoon.logistics.LogisticsCodec
import app.many.economytycoon.macro.MacroCodec
import app.many.economytycoon.markets.MarketCodec
import app.many.economytycoon.production.ProductionCodec
import app.many.economytycoon.contracts.ContractCodec
import app.many.economytycoon.exchange.ExchangeCodec
import app.many.economytycoon.risk.RiskCodec
import app.many.economytycoon.bonds.BondCodec
import app.many.economytycoon.futures.FuturesCodec
import app.many.economytycoon.property.PropertyCodec
import app.many.economytycoon.scenarios.ScenarioCodec
import app.many.economytycoon.unions.UnionCodec
import app.many.economytycoon.regulation.RegulationCodec
import app.many.economytycoon.tenders.TenderCodec
import app.many.economytycoon.grants.GrantCodec
import app.many.economytycoon.marketing.MarketingCodec
import app.many.economytycoon.energygrid.EnergyGridCodec
import app.many.economytycoon.research.ResearchCodec
import app.many.economytycoon.simulation.RestoredCore
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.workforce.WorkforceCodec
import app.many.economytycoon.world.WorldCodec

/** What a save file says about itself, readable without rebuilding the game. */
data class SnapshotInfo(
    val seed: Long,
    val tick: Long,
    val stateHash: String,
    val contentVersions: List<String>,
    val commandCount: Int,
)

/** The saved game was made with different content than the one supplied. */
class IncompatibleContentException(message: String) : RuntimeException(message)

/**
 * Saves a whole simulation to bytes and builds one back. Every engine writes its own section; the file also
 * carries the state hash taken at save time, and loading recomputes it, so a snapshot that restores to anything
 * other than the game that was saved is refused rather than played on.
 */
object SimulationSnapshot {
    const val VERSION = 1

    private const val META = "meta"
    private const val CONFIG = "config"
    private const val CORE = "core"
    private const val WORLD = "world"
    private const val FINANCE = "finance"
    private const val COMPANIES = "companies"
    private const val WORKFORCE = "workforce"
    private const val PRODUCTION = "production"
    private const val LOGISTICS = "logistics"
    private const val MARKETS = "markets"
    private const val RESEARCH = "research"
    private const val CONTRACTS = "contracts"
    private const val EXCHANGE = "exchange"
    private const val RISK = "risk"
    private const val TENDERS = "tenders"
    private const val GRANTS = "grants"
    private const val MARKETING = "marketing"
    private const val ENERGY = "energygrid"
    private const val CARBON = "carbon"
    private const val BANKING = "banking"
    private const val IP = "ip"
    private const val IP_SALES = "ipsales"
    private const val TOURISM = "tourism"
    private const val REGULATION = "regulation"
    private const val BONDS = "bonds"
    private const val UNIONS = "unions"
    private const val FUTURES = "futures"
    private const val PROPERTY = "property"
    private const val SCENARIO = "scenario"
    private const val MACRO = "macro"
    private const val COMPETITORS = "competitors"
    private const val GRID = "grid"
    private const val STATS = "stats"
    private const val EVENTS = "events"
    private const val LOG = "log"
    private const val QUEUE = "queue"

    fun save(sim: Simulation): ByteArray {
        val e = sim.economy
        val sections = ArrayList<Section>()
        fun add(name: String, body: (ByteWriter) -> Unit) { sections += Section(name, ByteWriter().also(body).toByteArray()) }

        val log = sim.commandLog()
        add(META) { w ->
            w.writeVarInt(VERSION)
            w.writeFixedLong(sim.config.seed)
            w.writeVarLong(sim.tick.value)
            w.writeString(sim.hash())
            w.writeVarInt(sim.content.versions.size)
            for (v in sim.content.versions) w.writeString(versionKey(v.packId, v.major, v.minor))
            w.writeVarInt(log.size)
        }
        add(CONFIG) { SimStateCodec.writeConfig(sim.config, it) }
        add(CORE) { w ->
            w.writeVarLong(e.tick.value)
            SimStateCodec.writeCounters(e.ids.snapshot(), w)
            SimStateCodec.writeCounters(e.random.snapshot(), w)
            w.writeRate(e.finance.baseRate)
        }
        add(WORLD) { WorldCodec.write(e.world, it) }
        add(FINANCE) { FinanceCodec.write(e.finance, it) }
        add(COMPANIES) { CompanyCodec.write(e.companies, e.insolvency, e.acquisitions, it) }
        add(WORKFORCE) { WorkforceCodec.write(e.workforce, it) }
        add(PRODUCTION) { ProductionCodec.write(e.production, it) }
        add(LOGISTICS) { LogisticsCodec.write(e.logistics, it) }
        add(MARKETS) { MarketCodec.write(e.markets, it) }
        add(RESEARCH) { ResearchCodec.write(e.research, it) }
        add(CONTRACTS) { ContractCodec.write(e.contracts, it) }
        add(EXCHANGE) { ExchangeCodec.write(e.exchange, it) }
        add(RISK) { RiskCodec.write(e.risk, it) }
        add(TENDERS) { TenderCodec.write(e.tenders, it) }
        add(GRANTS) { GrantCodec.write(e.grants, it) }
        // Written only once something has been booked, so a game that never advertises saves exactly as before.
        if (!e.marketing.state.isPristine()) add(MARKETING) { MarketingCodec.write(e.marketing.state, it) }
        // Likewise written only once grids have been set up, so a game on the simple electricity pool saves exactly as before.
        if (!e.energyGrids.isEmpty) add(ENERGY) { EnergyGridCodec.write(e.energyGrids, it) }
        // And again only once a government has a carbon policy.
        if (!e.carbonState.isPristine) add(CARBON) { app.many.economytycoon.carbon.CarbonCodec.write(e.carbonState, it) }
        // And once the commercial banks have been opened.
        if (!e.banking.isPristine) add(BANKING) { app.many.economytycoon.banking.BankingCodec.write(e.banking, it) }
        // And once the patent office has been opened; the sales history it prices royalties from is saved beside it.
        if (e.ipActive) {
            add(IP) { app.many.economytycoon.ip.IpCodec.write(e.ip.state, it) }
            add(IP_SALES) { e.ipSales.write(it) }
        }
        // And once tourism has been set up.
        if (!e.tourism.isEmpty) add(TOURISM) { app.many.economytycoon.tourism.TourismCodec.write(e.tourism, it) }
        add(REGULATION) { RegulationCodec.write(e.regulation, it) }
        add(BONDS) { BondCodec.write(e.bonds, it) }
        add(UNIONS) { UnionCodec.write(e.unions, it) }
        add(FUTURES) { FuturesCodec.write(e.futures, it) }
        add(PROPERTY) { PropertyCodec.write(e.property, it) }
        add(SCENARIO) { ScenarioCodec.write(e.scenario, it) }
        add(MACRO) { MacroCodec.write(e.macro, it) }
        add(COMPETITORS) { CompetitorCodec.write(e.competitors, it) }
        add(GRID) { SimStateCodec.writeGrid(e.grid, it) }
        add(STATS) { SimStateCodec.writeFacilityStats(e.facilityStats, it) }
        add(EVENTS) { SimStateCodec.writeEvents(sim.pipeline.events, it) }
        add(LOG) { CommandCodec.writeLog(log, it) }
        add(QUEUE) { w ->
            w.writeVarLong(sim.sequenceCounter())
            SimStateCodec.writeQueue(sim.queuedCommands(), w)
        }
        return SectionedFile.write(sections)
    }

    /** Reads only the header of a save, after checking the whole file is intact. */
    fun peek(bytes: ByteArray): SnapshotInfo = readInfo(index(bytes))

    fun load(bytes: ByteArray, content: ContentRepository, migrations: Migrations = Migrations()): Simulation {
        val sections = index(bytes, migrations)
        val info = readInfo(sections)
        val expected = content.versions.map { versionKey(it.packId, it.major, it.minor) }
        if (expected != info.contentVersions) {
            throw IncompatibleContentException("save was made with content ${info.contentVersions}, but the game has $expected")
        }
        fun reader(name: String) = ByteReader(sections[name]?.payload ?: throw ByteReader.CorruptDataException("save is missing section '$name'"))

        val config = SimStateCodec.readConfig(reader(CONFIG))
        val core = reader(CORE)
        val tick = Tick(core.readVarLong())
        val counters = SimStateCodec.readCounters(core)
        val streams = SimStateCodec.readCounters(core)
        val baseRate = core.readRate()

        val ids = app.many.economytycoon.core.IdAllocators().also { it.restore(counters) }
        val world = WorldCodec.read(reader(WORLD))
        val finance = FinanceCodec.read(reader(FINANCE), ids, FinanceConfig())
        finance.baseRate = baseRate
        val sim = Simulation(config, content, bootstrap = false, restored = RestoredCore(ids, world, finance, tick))
        val e = sim.economy
        e.random.restore(streams)

        CompanyCodec.read(reader(COMPANIES), e.companies, e.insolvency, e.acquisitions)
        WorkforceCodec.read(reader(WORKFORCE), e.workforce)
        ProductionCodec.read(reader(PRODUCTION), e.production)
        LogisticsCodec.read(reader(LOGISTICS), e.logistics)
        MarketCodec.read(reader(MARKETS), e.markets)
        ResearchCodec.read(reader(RESEARCH), e.research)
        ContractCodec.read(reader(CONTRACTS), e.contracts)
        ExchangeCodec.read(reader(EXCHANGE), e.exchange)
        RiskCodec.read(reader(RISK), e.risk)
        TenderCodec.read(reader(TENDERS), e.tenders)
        GrantCodec.read(reader(GRANTS), e.grants)
        sections[MARKETING]?.let { MarketingCodec.read(ByteReader(it.payload), e.marketing.state) }
        sections[ENERGY]?.let { EnergyGridCodec.read(ByteReader(it.payload), e.energyGrids) }
        sections[CARBON]?.let { app.many.economytycoon.carbon.CarbonCodec.read(ByteReader(it.payload), e.carbonState) }
        sections[BANKING]?.let { app.many.economytycoon.banking.BankingCodec.read(ByteReader(it.payload), e.banking) }
        sections[IP]?.let { e.restoreIp(app.many.economytycoon.ip.IpCodec.read(ByteReader(it.payload))) }
        sections[IP_SALES]?.let { e.ipSales.read(ByteReader(it.payload)) }
        sections[TOURISM]?.let { app.many.economytycoon.tourism.TourismCodec.read(ByteReader(it.payload), e.tourism) }
        RegulationCodec.read(reader(REGULATION), e.regulation)
        BondCodec.read(reader(BONDS), e.bonds)
        UnionCodec.read(reader(UNIONS), e.unions)
        FuturesCodec.read(reader(FUTURES), e.futures)
        PropertyCodec.read(reader(PROPERTY), e.property)
        ScenarioCodec.read(reader(SCENARIO), e.scenario)
        MacroCodec.read(reader(MACRO), e.macro)
        CompetitorCodec.read(reader(COMPETITORS), e.competitors)
        SimStateCodec.readGrid(reader(GRID), e.grid)
        SimStateCodec.readFacilityStats(reader(STATS), e.facilityStats)
        SimStateCodec.readEvents(reader(EVENTS), sim.pipeline.events)
        val log = CommandCodec.readLog(reader(LOG))
        val q = reader(QUEUE)
        val sequence = q.readVarLong()
        sim.restoreRuntime(log, SimStateCodec.readQueue(q), sequence)
        e.ids.restore(counters)

        if (sim.hash() != info.stateHash) throw ByteReader.CorruptDataException("restored state does not match the saved state hash")
        return sim
    }

    private fun index(bytes: ByteArray, migrations: Migrations = Migrations()): Map<String, Section> {
        val raw = SectionedFile.read(bytes)
        val meta = raw.firstOrNull { it.name == META } ?: throw ByteReader.CorruptDataException("save has no header")
        val version = ByteReader(meta.payload).readVarInt()
        val sections = if (version == VERSION) raw else migrations.upgrade(raw, version, VERSION)
        return sections.associateBy { it.name }
    }

    private fun readInfo(sections: Map<String, Section>): SnapshotInfo {
        val r = ByteReader(sections[META]?.payload ?: throw ByteReader.CorruptDataException("save has no header"))
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported snapshot version $version")
        val seed = r.readFixedLong()
        val tick = r.readVarLong()
        val hash = r.readString()
        val n = r.readVarInt()
        if (n < 0 || n > 1_000) throw ByteReader.CorruptDataException("implausible content list")
        val versions = List(n) { r.readString() }
        return SnapshotInfo(seed, tick, hash, versions, r.readVarInt())
    }

    private fun versionKey(pack: String, major: Int, minor: Int) = "$pack:$major.$minor"
}

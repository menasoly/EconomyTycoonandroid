package app.many.economytycoon.net

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.persistence.IncompatibleContentException
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.replay.TraceRecorder
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Simulation

/**
 * Drives the real economy. Cash trades go through the ledger as ordinary transfers between company accounts and
 * a single escrow account; goods are not tradable between players until the economy has a command for it.
 */
class RealSimulationPort(private var sim: Simulation, private val content: ContentRepository) : SimulationPort {
    override val tick: Long get() = sim.tick.value

    override val fingerprint: String =
        Hex.encode(Hmac.sha256(TraceRecorder.packs(content).joinToString(",").toByteArray(Charsets.UTF_8))).take(16)

    /** The underlying simulation, for read models. */
    val simulation: Simulation get() = sim

    private var transferCounter = 0L

    override fun stateHash(): ByteArray = Hmac.sha256(sim.hash().toByteArray(Charsets.UTF_8))

    override fun enqueue(player: PlayerId, command: Command) { sim.submit(Actor.Player(player.value), command) }

    override fun advance(): List<AppliedCommand> {
        val summary = sim.advance()
        return summary.commandOutcomes.mapNotNull { o ->
            val actor = o.actor
            if (actor is Actor.Player) AppliedCommand(PlayerId(actor.id), o.accepted, o.rule) else null
        }
    }

    override fun seat(player: PlayerId, name: String): Outcome<Long> {
        val country = sim.economy.world.countries().minByOrNull { it.id } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "the world has no country")
        val cmd = Command.FoundCompany(name, country.id, null, sim.config.playerStartingCapital)
        return sim.execute(Actor.Player(player.value), cmd).flatMap { Outcome.ok(it.created ?: -1L) }
    }

    override fun companiesOf(player: PlayerId): List<Long> =
        sim.economy.companies.ownedBy(Holder.player(player.value)).map { it.id.value }.sorted()

    override fun supports(kind: AssetKind): Boolean = kind == AssetKind.CASH

    override fun available(company: Long, asset: TradeAsset): Long {
        if (asset.kind != AssetKind.CASH) return 0L
        val owner = ownerOf(company) ?: return 0L
        return sim.economy.finance.ledger.cash(owner).raw.coerceAtLeast(0L)
    }

    private fun ownerOf(company: Long): LedgerOwner? =
        if (company == ESCROW) ESCROW_OWNER
        else sim.economy.companies.company(CompanyId(company))?.takeIf { it.isActive }?.owner

    override fun applyTransfers(transfers: List<Transfer>): List<Boolean> = transfers.map { t ->
        if (t.asset.kind != AssetKind.CASH || t.asset.amount <= 0L) return@map false
        val from = ownerOf(t.from) ?: return@map false
        val to = ownerOf(t.to) ?: return@map false
        val ledger = sim.economy.finance.ledger
        if (ledger.cash(from).raw < t.asset.amount) return@map false
        val key = "net:${t.trade.value}:${sim.tick.value}:${transferCounter++}"
        val draft = TransactionDraft(
            key, Tick(sim.tick.value), ReasonCode.TRANSFER,
            listOf(Entry(AccountRef(from, Chart.CASH), Money(-t.asset.amount)), Entry(AccountRef(to, Chart.CASH), Money(t.asset.amount))),
            "player trade ${t.trade.value}",
        )
        ledger.post(draft) is Outcome.Ok
    }

    override fun snapshot(): ByteArray = SimulationSnapshot.save(sim)

    override fun restore(bytes: ByteArray): Outcome<Unit> = try {
        sim = SimulationSnapshot.load(bytes, content)
        transferCounter = 0L
        Outcome.ok(Unit)
    } catch (e: ByteReader.CorruptDataException) {
        Outcome.reject(Rules.INVALID_ARGUMENT, "snapshot is damaged: ${e.message}")
    } catch (e: IncompatibleContentException) {
        Outcome.reject(Rules.INVALID_ARGUMENT, "snapshot needs other content: ${e.message}")
    }

    override fun statements(fromTick: Long): List<CompanyStatement> {
        val e = sim.economy
        val now = e.tick
        val from = Tick(fromTick.coerceIn(0L, now.value))
        val st = e.finance.statements
        return e.companies.all().filter { it.kind != CompanyKind.STATE }.sortedBy { it.id }.map { c ->
            val income = st.income(c.owner, from, now)
            val sheet = st.balance(c.owner, now)
            val person = if (c.founder.kind == "player") PlayerId(c.founder.id) else null
            CompanyStatement(c.id.value, c.name, person, income.revenue, income.netIncome, sheet.netWorth, sheet.cash, sheet.totalLiabilities, sheet.totalAssets, c.isActive)
        }
    }

    companion object {
        /** Company number that stands for the escrow account in transfers. */
        const val ESCROW = 0L
        private val ESCROW_OWNER = LedgerOwner.bank(900_001L)
    }
}

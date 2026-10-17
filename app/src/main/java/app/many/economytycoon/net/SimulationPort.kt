package app.many.economytycoon.net

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command

/** What happened to one command when its tick ran. Used to spot players who keep sending commands that cannot work. */
data class AppliedCommand(val player: PlayerId, val accepted: Boolean, val rule: String)

/** A company's figures over a window, the input to scoring. Money is in ledger units. */
data class CompanyStatement(
    val company: Long,
    val name: String,
    /** The person running it, or null for a computer-controlled company. */
    val player: PlayerId?,
    val revenue: Money,
    val netIncome: Money,
    val netWorth: Money,
    val cash: Money,
    val liabilities: Money,
    val assets: Money,
    val active: Boolean,
)

/**
 * The simulation as the multiplayer layer drives it. The server and every client each own one; because the
 * game is deterministic, feeding all of them the same commits in the same order keeps their states identical.
 * Nothing here is networked: it is the narrow seam between the protocol and the game.
 */
interface SimulationPort {
    /** Ticks completed so far. The next commit to apply is the one for this tick. */
    val tick: Long

    /** Identifies the game content; peers with different content cannot play together. */
    val fingerprint: String

    /** 32-byte digest of the full game state. */
    fun stateHash(): ByteArray

    /** Queues a player's command for the next [advance]. */
    fun enqueue(player: PlayerId, command: Command)

    /** Moves assets between companies (0 is the escrow account) before the next tick; one result per transfer, in order. */
    fun applyTransfers(transfers: List<Transfer>): List<Boolean>

    /** Runs one tick with the queued commands and reports what became of each. */
    fun advance(): List<AppliedCommand>

    /** Founds the company a player will run and returns its id. */
    fun seat(player: PlayerId, name: String): Outcome<Long>

    fun companiesOf(player: PlayerId): List<Long>

    /** How much of [asset] the company can give away right now (the asset's own amount field is ignored). */
    fun available(company: Long, asset: TradeAsset): Long

    fun supports(kind: AssetKind): Boolean

    /** The whole state as bytes, taken between ticks. */
    fun snapshot(): ByteArray

    /** Replaces the state with a snapshot taken by [snapshot]; refused, with nothing changed, if it cannot be read. */
    fun restore(bytes: ByteArray): Outcome<Unit>

    /** Company figures from [fromTick] to now. */
    fun statements(fromTick: Long): List<CompanyStatement>
}

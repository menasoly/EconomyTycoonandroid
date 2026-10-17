package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.BankOperator
import app.many.economytycoon.banking.BankSnapshot
import app.many.economytycoon.banking.BankStatus
import app.many.economytycoon.banking.BankView
import app.many.economytycoon.banking.BankingInputs
import app.many.economytycoon.banking.BankingService
import app.many.economytycoon.banking.RunStage
import app.many.economytycoon.banking.SupervisoryRung
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.session.Formatting

/** One bank in a list: who it is, how it stands and the figures a player scans first. */
data class BankRow(
    val id: BankId,
    val name: String,
    val subtitle: String,
    val status: String,
    val tone: Tone,
    val tiles: List<Tile>,
    val alerts: List<String>,
    val yours: Boolean,
)

/** The banking sector of a country as the overview screen shows it. */
data class BankingOverview(
    val tiles: List<Tile>,
    val mine: List<BankRow>,
    val rivals: List<BankRow>,
    val market: List<Fact>,
    val canFound: Boolean,
    val foundingNote: String,
    val note: String,
)

/** Plain words and colours for the states of the banking model, shared by every banking presenter. */
object BankingLabels {
    fun statusTone(s: BankStatus): Tone = when (s) {
        BankStatus.ACTIVE -> Tone.GOOD
        BankStatus.SUPERVISED -> Tone.WARN
        BankStatus.IN_RESOLUTION -> Tone.BAD
        BankStatus.MERGED, BankStatus.LIQUIDATED -> Tone.MUTED
    }

    fun rungTone(r: SupervisoryRung): Tone = when (r) {
        SupervisoryRung.NORMAL -> Tone.GOOD
        SupervisoryRung.BUFFER -> Tone.WARN
        else -> Tone.BAD
    }

    fun runTone(r: RunStage): Tone = when (r) {
        RunStage.CALM -> Tone.GOOD
        RunStage.WATCHFUL -> Tone.WARN
        RunStage.RUN, RunStage.PANIC -> Tone.BAD
    }

    fun ratingTone(r: CreditRating): Tone = when {
        r.ordinal <= CreditRating.A.ordinal -> Tone.GOOD
        r.ordinal <= CreditRating.BBB.ordinal -> Tone.NEUTRAL
        r.ordinal <= CreditRating.BB.ordinal -> Tone.WARN
        else -> Tone.BAD
    }

    fun confidenceTone(ppm: Long): Tone = Tones.health(ppm)

    fun confidence(ppm: Long): String = Formatting.percentPpm(ppm.coerceIn(0L, Rate.ONE_PPM))

    /** The coverage ratios read best as a percentage of the requirement; the model never goes beyond ten times. */
    fun coverage(r: Rate): String = if (r.ppm >= 10 * Rate.ONE_PPM) "10x+" else Formatting.ratioTimes(r.ppm)

    fun ratioTone(value: Rate, floor: Rate, comfortable: Rate): Tone = when {
        value < floor -> Tone.BAD
        value < comfortable -> Tone.WARN
        else -> Tone.GOOD
    }

    fun operatorLabel(o: BankOperator): String = if (o == BankOperator.PLAYER) "Run by you" else "Computer-run"
}

object BankingPresenter {
    private const val MAX_ALERTS = 3

    /** Short warnings for a bank's row; the detail screen has the full list of advice. */
    fun alerts(service: BankingService, view: BankView): List<String> {
        val b = view.bank
        val out = ArrayList<String>()
        if (b.status == BankStatus.SUPERVISED) out += "Under supervision"
        if (b.rung.isBreach) out += "Capital: ${b.rung.label.lowercase()}"
        else if (b.rung == SupervisoryRung.BUFFER) out += "Using its capital buffer"
        if (b.runStage >= RunStage.RUN) out += "Depositors are leaving"
        else if (b.runStage == RunStage.WATCHFUL) out += "Depositors are nervous"
        if (b.centralBankDebt.isPositive) out += "Borrowing from the central bank"
        if (view.liquidity.lcr < Rate.ONE) out += "Liquidity below the minimum"
        return out.take(MAX_ALERTS)
    }

    fun row(service: BankingService, snapshot: BankSnapshot, inputs: BankingInputs, money: (Money) -> String, yours: Boolean): BankRow {
        val view = BankView(snapshot, inputs, service.params)
        val last = snapshot.lastResult
        val tiles = listOf(
            Tile("Assets", money(snapshot.totalAssets)),
            Tile("CET1 ratio", Formatting.percent(view.capital.cet1Ratio), BankingLabels.rungTone(view.capital.rung)),
            Tile("Liquidity cover", BankingLabels.coverage(view.liquidity.lcr), BankingLabels.ratioTone(view.liquidity.lcr, Rate.ONE, Rate.percent(130))),
            Tile("Return on equity", if (last == null) "n/a" else Formatting.percent(last.returnOnEquity), if (last == null) Tone.MUTED else Tones.bySign(last.returnOnEquity.ppm)),
        )
        return BankRow(
            snapshot.id, snapshot.name, "${snapshot.rating.label} · ${BankingLabels.operatorLabel(snapshot.operator)}",
            snapshot.status.label, BankingLabels.statusTone(snapshot.status), tiles, alerts(service, view), yours,
        )
    }

    /** The sector of [country] from the point of view of [owner]. Banks that no longer operate are left out. */
    fun overview(
        service: BankingService, inputs: BankingInputs, country: CountryId, owner: CompanyId?, money: (Money) -> String, maxPlayerBanks: Int = service.params.maxPlayerBanks,
    ): BankingOverview {
        val live = service.operatingIn(country)
        val mine = live.filter { it.operator == BankOperator.PLAYER && it.owner == owner && owner != null }
        val rivals = live.filter { it !in mine }.sortedByDescending { it.totalAssets }
        val system = Money.sum(live.map { it.totalAssets })
        val deposits = Money.sum(live.map { it.depositsTotal })
        val loans = Money.sum(live.map { it.loansGross })
        val troubled = live.count { it.rung.isBreach || it.runStage >= RunStage.RUN }
        val fund = service.fund(country)
        val coverage = service.fundCoverage(country)
        val market = service.market(country)
        val tiles = listOf(
            Tile("Banks", live.size.toString(), note = if (troubled == 0) "None in trouble" else "$troubled in trouble", tone = if (troubled == 0) Tone.GOOD else Tone.BAD),
            Tile("Total assets", money(system)),
            Tile("Deposits", money(deposits)),
            Tile("Loans", money(loans)),
        )
        val facts = ArrayList<Fact>()
        facts += Fact("Policy rate", Formatting.percent(inputs.policyRate(country)))
        if (market != null) {
            facts += Fact("Overnight rate", Formatting.percent(market.rate), if (market.frozen) Tone.BAD else Tone.NEUTRAL, if (market.frozen) "Banks are not lending to each other" else null)
            facts += Fact("Interbank lending today", money(market.volume))
        }
        facts += Fact("Guarantee fund", if (fund == null) "Not yet funded" else money(fund.balance), Tones.health(coverage.ppm))
        facts += Fact("Fund cover of insured deposits", Formatting.percent(coverage), Tones.health(coverage.ppm))
        val stress = service.systemicStress(country)
        facts += Fact("Stress in the system", Formatting.percentPpm(stress), if (stress > 250_000L) Tone.BAD else if (stress > 50_000L) Tone.WARN else Tone.GOOD)
        val owned = if (owner == null) 0 else service.banksOf(owner).count { it.status.isOperating }
        val room = owned < maxPlayerBanks
        val minimum = money(Money.units(service.params.minFoundingCapitalUnits))
        return BankingOverview(
            tiles = tiles,
            mine = mine.map { row(service, it, inputs, money, true) },
            rivals = rivals.map { row(service, it, inputs, money, false) },
            market = facts,
            canFound = owner != null && room && live.size < service.params.maxBanksPerCountry,
            foundingNote = when {
                owner == null -> "Banks belong to a company."
                !room -> "You already run ${Formatting.plural(owned.toLong(), "bank")}, the most allowed."
                live.size >= service.params.maxBanksPerCountry -> "The market is full."
                else -> "A new bank needs at least $minimum of capital."
            },
            note = "Banks take deposits and lend them on. They earn the gap, and lose money when borrowers default or depositors lose faith.",
        )
    }
}

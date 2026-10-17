package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.ip.BreadthChoice
import app.many.economytycoon.ip.DesignAroundStatus
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpModel
import app.many.economytycoon.ip.IpOwner
import app.many.economytycoon.ip.PatentRow
import app.many.economytycoon.ip.PatentStanding
import app.many.economytycoon.ip.SecretRow
import app.many.economytycoon.ip.SecretStatus
import app.many.economytycoon.session.Formatting

/** What the intellectual property screens need from the running game. The host implements it over its session. */
interface IpSessionView {
    /** The model for the selected company, or null when there is none. */
    fun ipModel(): IpModel?
    fun money(m: Money): String
    fun companyName(id: CompanyId): String
    fun countryName(id: CountryId): String
    fun npeName(owner: IpOwner): String? = null

    /** Carries out a command and returns the message to show. */
    fun issue(command: IpCommand): String
}

/** Names and number formats shared by the presenters of this module. */
class IpFormat(private val view: IpSessionView) {
    fun money(m: Money): String = view.money(m)
    fun company(id: CompanyId): String = view.companyName(id)
    fun country(id: CountryId): String = view.countryName(id)
    fun owner(o: IpOwner): String = o.companyOrNull()?.let { view.companyName(it) } ?: view.npeName(o) ?: "A patent holder"
    fun percent(ppm: Long): String = Formatting.percentPpm(ppm)
    fun duration(days: Long): String = Formatting.duration(days)
}

/** A button on a card; [command] is ready to issue. */
data class IpAction(val label: String, val command: IpCommand, val primary: Boolean = false)

data class IpCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val tone: Tone,
    val facts: List<Fact>,
    val text: String?,
    val actions: List<IpAction>,
)

data class IpSection(val heading: String, val empty: String?, val cards: List<IpCard>)

enum class IpTab(val label: String) {
    OVERVIEW("Overview"), PATENTS("Patents"), SECRETS("Secrets"), LICENCES("Licences"), DISPUTES("Disputes"), MARKET("Market"), PROGRAMMES("Programmes"),
}

data class IpTabChip(val tab: IpTab, val label: String, val count: Int)

data class IpInsightLine(val title: String, val detail: String, val tone: Tone)

data class IpScreenModel(
    val tabs: List<IpTabChip>,
    val tab: IpTab,
    val tiles: List<Tile>,
    val insights: List<IpInsightLine>,
    val sections: List<IpSection>,
    val note: String,
)

object IpPresenter {
    private const val SECRET_SPEND_UNITS = 10_000L

    fun build(view: IpSessionView, tab: IpTab): IpScreenModel? {
        val m = view.ipModel() ?: return null
        val f = IpFormat(view)
        val sections = when (tab) {
            IpTab.OVERVIEW -> overview(m, f)
            IpTab.PATENTS -> patents(m, f)
            IpTab.SECRETS -> secrets(m, f)
            IpTab.LICENCES -> IpDealsPresenter.licences(m, f)
            IpTab.DISPUTES -> IpDisputePresenter.disputes(m, f)
            IpTab.MARKET -> market(m, f)
            IpTab.PROGRAMMES -> IpDealsPresenter.programmes(m, f)
        }
        return IpScreenModel(tabs(m), tab, tiles(m, f), if (tab == IpTab.OVERVIEW) insights(m) else emptyList(), sections, note(tab))
    }

    fun tabs(m: IpModel): List<IpTabChip> = IpTab.values().map { t ->
        val n = when (t) {
            IpTab.OVERVIEW -> m.insights.size
            IpTab.PATENTS -> m.patents.size
            IpTab.SECRETS -> m.secrets.count { it.status.live }
            IpTab.LICENCES -> m.licensesIn.size + m.licensesOut.size + m.offersToMe.size
            IpTab.DISPUTES -> m.disputes.count { it.stage.open }
            IpTab.MARKET -> m.market.size
            IpTab.PROGRAMMES -> m.designs.count { it.status.live } + m.compulsory.count { it.status.open } + m.pools.size
        }
        IpTabChip(t, t.label, n)
    }

    fun tiles(m: IpModel, f: IpFormat): List<Tile> {
        val inForce = m.patents.count { it.standing == PatentStanding.ACTIVE }
        val open = m.disputes.count { it.stage.open }
        return listOf(
            Tile("Portfolio worth", f.money(m.portfolioValue), Tone.INFO, "${m.patents.size} patents"),
            Tile("In force", inForce.toString(), if (inForce > 0) Tone.GOOD else Tone.MUTED),
            Tile("Licence income a year", f.money(m.licensingIncomePerYear), if (m.licensingIncomePerYear.raw > 0L) Tone.GOOD else Tone.MUTED),
            Tile("Royalties paid", f.money(m.royaltiesPaid), if (m.royaltiesPaid.raw > 0L) Tone.WARN else Tone.MUTED),
            Tile("Open disputes", open.toString(), if (open > 0) Tone.WARN else Tone.MUTED),
        )
    }

    fun insights(m: IpModel): List<IpInsightLine> = m.insights.take(5).map { i ->
        IpInsightLine(i.title, i.detail, when { i.priority >= 80 -> Tone.BAD; i.priority >= 55 -> Tone.WARN; else -> Tone.INFO })
    }

    fun note(tab: IpTab): String = when (tab) {
        IpTab.OVERVIEW -> "Protect what you know, license what rivals use, and settle fights that cost more than they are worth."
        IpTab.PATENTS -> "A patent lasts only while its fees are paid, country by country."
        IpTab.SECRETS -> "A secret costs nothing to hold and lasts until it leaks, is worked out or is found by someone else."
        IpTab.LICENCES -> "Licences let others use your patents for royalties, or let you use theirs."
        IpTab.DISPUTES -> "Courts weigh the strength of the patent against the evidence. Both sides pay, whoever wins."
        IpTab.MARKET -> "Patents change hands here. Scripted holders buy cheaply and press claims."
        IpTab.PROGRAMMES -> "Redesigns, compulsory licences, pools and your standing instructions."
    }

    // ---- overview ----

    private fun overview(m: IpModel, f: IpFormat): List<IpSection> {
        val me = m.company
        val protect = m.unprotected.map { u ->
            val reasons = u.reasons.joinToString(" ").ifBlank { null }
            IpCard(
                "protect-${u.tech.key}", u.name, "Patent worth ${f.money(u.patentValue)}, secret worth ${f.money(u.secretValue)}",
                when (u.choice) { app.many.economytycoon.ip.Protection.PATENT -> "Patent"; app.many.economytycoon.ip.Protection.SECRET -> "Secret"; else -> "Neither" },
                when (u.choice) { app.many.economytycoon.ip.Protection.NEITHER -> Tone.MUTED; else -> Tone.INFO },
                listOf(
                    Fact("Filing cost", f.money(u.fees)), Fact("Chance of a grant", f.percent(u.grantChancePpm)),
                    Fact("A secret lasts about", f.duration(u.halfLifeDays.coerceAtMost(36_000L))),
                ),
                reasons,
                listOf(
                    IpAction("File a patent", IpCommand.FilePatent(me, u.tech, u.countries, u.breadth), u.choice == app.many.economytycoon.ip.Protection.PATENT),
                    IpAction("Keep it secret", IpCommand.KeepSecret(me, u.tech), u.choice == app.many.economytycoon.ip.Protection.SECRET),
                ),
            )
        }
        val offers = m.offersToMe.map { IpDealsPresenter.offerCard(m, it, f) }
        val users = m.unlicensedUse.map { u ->
            IpCard(
                "use-${u.patent.value}-${u.user.value}-${u.country.value}", u.title, "${f.company(u.user)} in ${f.country(u.country)}", "Unlicensed", Tone.WARN,
                listOf(Fact("Their sales", f.money(u.sales)), Fact("Fair rate", f.percent(u.suggested.percent.ppm))),
                "They are using this patent without a licence.",
                listOf(
                    IpAction(
                        "Offer a licence",
                        IpCommand.OfferLicense(me, app.many.economytycoon.ip.OfferRequest(IpOwner.company(me), u.user, listOf(u.patent), u.suggested, scope = app.many.economytycoon.ip.LicenseScope(emptyList(), listOf(u.country)))), true,
                    ),
                    IpAction("Accuse them", IpCommand.ReportInfringement(me, u.patent, u.user, u.country)),
                ),
            )
        }
        return listOf(
            IpSection("Worth protecting", "Everything you sell is already protected or too small to bother with.", protect),
            IpSection("Licence offers for you", null.takeIf { offers.isNotEmpty() } ?: "No offers waiting.", offers),
            IpSection("Using your patents without a licence", "Nobody is using your patents without permission.", users),
        )
    }

    // ---- patents ----

    private fun standingTone(s: PatentStanding): Tone = when (s) { PatentStanding.ACTIVE -> Tone.GOOD; PatentStanding.PENDING -> Tone.INFO; PatentStanding.ENDED -> Tone.MUTED }

    fun patentCard(m: IpModel, p: PatentRow, f: IpFormat): IpCard {
        val me = m.company
        val mine = p.owner == IpOwner.company(me)
        val nextFee = p.filings.filter { it.nextFee != null }.minByOrNull { it.nextFeeDue?.value ?: Long.MAX_VALUE }
        val facts = ArrayList<Fact>()
        facts += Fact("Worth", f.money(p.value))
        facts += Fact("Novelty", f.percent(p.noveltyPpm)); facts += Fact("Claims reach", f.percent(p.breadthPpm))
        facts += Fact("Chance it survives a challenge", f.percent(p.validityPpm), Tones.health(p.validityPpm))
        facts += Fact("Cited by", p.citations.toString())
        facts += Fact("Time left", f.duration(p.remainingDays))
        val fee = nextFee?.nextFee
        if (nextFee != null && fee != null) facts += Fact("Next renewal", "${f.money(fee)} in ${f.country(nextFee.country)}")
        if (p.licensedOut > 0) facts += Fact("Licensed to", p.licensedOut.toString())
        facts += Fact("Fees paid so far", f.money(p.fees))
        val countries = p.filings.joinToString { "${f.country(it.country)} (${it.status.label.lowercase()})" }
        val flags = listOfNotNull(if (p.essential) "essential to a standard" else null, if (p.frandPledged) "FRAND pledged" else null, p.askingPrice?.let { "for sale at ${f.money(it)}" }).joinToString(", ")
        val actions = ArrayList<IpAction>()
        if (mine && p.standing != PatentStanding.ENDED) {
            actions += IpAction(if (p.autoMaintain) "Stop auto-renewal" else "Renew automatically", IpCommand.SetAutoMaintain(me, p.id, !p.autoMaintain))
            if (p.askingPrice == null) actions += IpAction("Put up for sale", IpCommand.ListPatent(me, p.id, p.value.coerceAtLeast(Money.units(100)))) else actions += IpAction("Take off the market", IpCommand.UnlistPatent(me, p.id))
            if (!p.essential && p.breadthPpm >= 500_000L && p.standing == PatentStanding.ACTIVE) actions += IpAction("Declare essential with a FRAND pledge", IpCommand.DeclareEssential(me, p.id, true))
            actions += IpAction("Abandon", IpCommand.AbandonPatent(me, p.id, null))
        }
        return IpCard("patent-${p.id.value}", p.title, countries, p.standing.label, standingTone(p.standing), facts, flags.ifBlank { null }, actions)
    }

    private fun patents(m: IpModel, f: IpFormat): List<IpSection> {
        val live = m.patents.filter { it.standing != PatentStanding.ENDED }
        val ended = m.patents.filter { it.standing == PatentStanding.ENDED }
        return listOf(
            IpSection("Your patents", "You hold no patents. Research something and file for it from the overview.", live.map { patentCard(m, it, f) }),
            IpSection("Ended", null, ended.map { patentCard(m, it, f) }),
        )
    }

    // ---- secrets ----

    private fun secretTone(s: SecretRow): Tone = when {
        !s.status.live -> Tone.MUTED
        s.halfLifeDays < 360L -> Tone.BAD
        s.halfLifeDays < 1_080L -> Tone.WARN
        else -> Tone.GOOD
    }

    fun secretCard(m: IpModel, s: SecretRow, f: IpFormat): IpCard {
        val me = m.company
        val facts = listOf(
            Fact("Protection", f.percent(s.protectionPpm), Tones.health(s.protectionPpm)),
            Fact("People who know it", s.knowers.toString()),
            Fact("Likely to last about", if (s.status.live) f.duration(s.halfLifeDays.coerceAtMost(36_000L)) else "-"),
            Fact("Spent on security", f.money(s.securitySpent)),
        )
        val text = when {
            s.stolenBy.isNotEmpty() -> "Taken by ${s.stolenBy.joinToString { f.company(it) }}."
            s.status == SecretStatus.PATENTED -> "Now covered by a patent."
            else -> null
        }
        val actions = if (s.status.live) {
            listOf(IpAction("Spend ${f.money(Money.units(SECRET_SPEND_UNITS))} on security", IpCommand.InvestInSecret(me, s.id, Money.units(SECRET_SPEND_UNITS)), s.halfLifeDays < 360L), IpAction("Drop it", IpCommand.DropSecret(me, s.id)))
        } else emptyList()
        return IpCard("secret-${s.id.value}", s.name, s.status.label, s.status.label, secretTone(s), facts, text, actions)
    }

    private fun secrets(m: IpModel, f: IpFormat): List<IpSection> = listOf(
        IpSection("Trade secrets", "You keep no trade secrets.", m.secrets.filter { it.status.live }.map { secretCard(m, it, f) }),
        IpSection("Gone", null, m.secrets.filter { !it.status.live }.map { secretCard(m, it, f) }),
    )

    // ---- market ----

    private fun market(m: IpModel, f: IpFormat): List<IpSection> {
        val me = m.company
        val sale = m.market.map { r ->
            IpCard(
                "sale-${r.patent.value}", r.title, "Sold by ${f.owner(r.seller)}", f.money(r.price), if (r.price < r.value) Tone.GOOD else Tone.NEUTRAL,
                listOf(Fact("Our estimate of its worth", f.money(r.value)), Fact("Time left", f.duration(r.remainingDays))), null,
                listOf(IpAction("Buy for ${f.money(r.price)}", IpCommand.BuyPatent(me, r.patent), r.price < r.value)),
            )
        }
        val holders = m.npes.map { n ->
            IpCard(
                "npe-${n.id.value}", n.name, n.strategy.label, "Makes nothing", Tone.MUTED,
                listOf(Fact("Patents", n.patents.toString()), Fact("Funds", f.money(n.chest)), Fact("Demands sent", n.demandsSent.toString()), Fact("Aggression", f.percent(n.aggressionPpm))),
                n.strategy.blurb, emptyList(),
            )
        }
        return listOf(IpSection("For sale", "Nothing is listed.", sale), IpSection("Patent holders that make nothing", null, holders))
    }

    fun designStatusTone(s: DesignAroundStatus): Tone = when (s) { DesignAroundStatus.RUNNING -> Tone.INFO; DesignAroundStatus.SUCCEEDED -> Tone.GOOD; DesignAroundStatus.FAILED -> Tone.BAD; DesignAroundStatus.CANCELLED -> Tone.MUTED }

    fun breadthLabel(b: BreadthChoice): String = b.label
}

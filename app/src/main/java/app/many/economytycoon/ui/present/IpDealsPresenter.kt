package app.many.economytycoon.ui.present

import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpModel
import app.many.economytycoon.ip.LicenseRow
import app.many.economytycoon.ip.LicenseSide
import app.many.economytycoon.ip.LicenseStatus
import app.many.economytycoon.ip.PoolRow

/** Cards for licences, pools and the programmes a company runs next to its patents. */
object IpDealsPresenter {
    private fun statusTone(s: LicenseStatus): Tone = when (s) {
        LicenseStatus.ACTIVE -> Tone.GOOD
        LicenseStatus.OFFERED -> Tone.INFO
        LicenseStatus.BREACHED -> Tone.BAD
        else -> Tone.MUTED
    }

    fun offerCard(m: IpModel, r: LicenseRow, f: IpFormat): IpCard {
        val me = m.company
        return IpCard(
            "offer-${r.id.value}", r.patents.joinToString(), "From ${f.owner(r.counterparty)}", "Offer", Tone.INFO,
            termsFacts(r, f) + Fact("Lasts", r.daysLeft?.let { f.duration(it) } ?: "Until the patents end"),
            if (r.frand) "Offered on fair, reasonable and non-discriminatory terms." else null,
            listOf(IpAction("Accept", IpCommand.AcceptLicense(me, r.id), true), IpAction("Decline", IpCommand.DeclineLicense(me, r.id))),
        )
    }

    private fun termsFacts(r: LicenseRow, f: IpFormat): List<Fact> {
        val out = ArrayList<Fact>()
        out += Fact("Kind", r.kind.label)
        out += Fact("Royalty", r.terms.basis.label)
        if (r.ratePpm > 0L) out += Fact("Rate", f.percent(r.ratePpm))
        if (r.terms.upfront.raw > 0L) out += Fact("Paid up front", f.money(r.terms.upfront))
        return out
    }

    fun licenceCard(m: IpModel, r: LicenseRow, f: IpFormat): IpCard {
        val me = m.company
        val mineToEnd = r.side == LicenseSide.LICENSOR
        val facts = ArrayList(termsFacts(r, f))
        facts += Fact(if (mineToEnd) "Received so far" else "Paid so far", f.money(r.paidTotal))
        if (r.arrears.raw > 0L) facts += Fact("Overdue", f.money(r.arrears), Tone.BAD)
        r.daysLeft?.let { facts += Fact("Time left", f.duration(it)) }
        val tags = listOfNotNull(
            if (r.frand) "FRAND" else null, if (r.compulsory) "compulsory" else null,
            if (r.crossLicensed) "cross-licence" else null, if (r.inPool) "through a pool" else null,
        ).joinToString(", ")
        val actions = ArrayList<IpAction>()
        if (r.status == LicenseStatus.ACTIVE) {
            if (mineToEnd) actions += IpAction("Audit the books", IpCommand.AuditLicense(me, r.id))
            actions += IpAction("End the licence", IpCommand.TerminateLicense(me, r.id))
        }
        if (r.status == LicenseStatus.OFFERED && mineToEnd) actions += IpAction("Withdraw offer", IpCommand.WithdrawOffer(me, r.id))
        return IpCard(
            "licence-${r.id.value}", r.patents.joinToString(), (if (mineToEnd) "To " else "From ") + f.owner(r.counterparty),
            r.status.label, statusTone(r.status), facts, tags.ifBlank { null }, actions,
        )
    }

    fun licences(m: IpModel, f: IpFormat): List<IpSection> = listOf(
        IpSection("Offers waiting for you", "No offers waiting.", m.offersToMe.map { offerCard(m, it, f) }),
        IpSection("Licences you hold", "You license nothing from anyone.", m.licensesIn.map { licenceCard(m, it, f) }),
        IpSection("Licences you grant", "You license nothing out.", m.licensesOut.map { licenceCard(m, it, f) }),
    )

    fun poolCard(m: IpModel, p: PoolRow, f: IpFormat): IpCard {
        val me = m.company
        val facts = listOf(
            Fact("Members", p.members.toString()), Fact("Patents", p.patents.toString()), Fact("Collected", f.money(p.collected)),
            Fact("Your share of income", f.percent(p.myShareOfIncomePpm)), Fact("You earned", f.money(p.myEarnings)),
        )
        val actions = when {
            p.administrator == me -> listOf(IpAction("Dissolve", IpCommand.DissolvePool(me, p.id)))
            p.iAmMember -> listOf(IpAction("Leave", IpCommand.LeavePool(me, p.id)))
            else -> emptyList()
        }
        return IpCard("pool-${p.id.value}", p.name, p.field.label, if (p.iAmMember) "Member" else "Open", Tone.INFO, facts, null, actions)
    }

    fun programmes(m: IpModel, f: IpFormat): List<IpSection> {
        val me = m.company
        val designs = m.designs.map { d ->
            IpCard(
                "design-${d.id.value}", d.patent, "Engineering around it", d.status.label, IpPresenter.designStatusTone(d.status),
                listOf(Fact("Cost", f.money(d.cost)), Fact("Chance it works", f.percent(d.successPpm), Tones.health(d.successPpm)), Fact("Time left", f.duration(d.daysLeft))),
                null, if (d.status.live) listOf(IpAction("Stop", IpCommand.CancelDesignAround(me, d.id))) else emptyList(),
            )
        }
        val compulsory = m.compulsory.map { c ->
            IpCard(
                "compulsory-${c.id.value}", c.patent, "In ${f.country(c.country)}", c.status.label, if (c.status.open) Tone.INFO else Tone.MUTED,
                listOf(Fact("Ground", c.ground.label), Fact("Decision in", f.duration(c.daysToDecision))), c.reason.ifBlank { null },
                if (c.status.open) listOf(IpAction("Withdraw", IpCommand.WithdrawCompulsory(me, c.id))) else emptyList(),
            )
        }
        val policy = m.policy
        val stance = IpCard(
            "policy", "Standing instructions", policy.stance.label, null, Tone.NEUTRAL,
            listOf(Fact("Watching for copies", f.percent(policy.monitoringPpm)), Fact("Default filing", policy.defaultBreadth.label), Fact("Renew fees automatically", if (policy.autoMaintain) "Yes" else "No")),
            null,
            app.many.economytycoon.ip.EnforcementStance.values().filter { it != policy.stance }.map { IpAction(it.label, IpCommand.SetPolicy(me, policy.copy(stance = it))) },
        )
        return listOf(
            IpSection("Policy", null, listOf(stance)),
            IpSection("Redesigns", "No redesign is running.", designs),
            IpSection("Compulsory licence requests", "None requested.", compulsory),
            IpSection("Patent pools", "You are in no pool.", m.pools.map { poolCard(m, it, f) }),
        )
    }
}

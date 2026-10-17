package app.many.economytycoon.ui.present

import app.many.economytycoon.ip.DisputeResult
import app.many.economytycoon.ip.DisputeRow
import app.many.economytycoon.ip.DisputeStage
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpModel

/** Cards for the lawsuits and demands a company is part of. */
object IpDisputePresenter {
    fun stageTone(d: DisputeRow): Tone = when {
        d.stage == DisputeStage.CLOSED -> when (d.result) {
            DisputeResult.CLAIMANT_WON -> if (d.iAmClaimant) Tone.GOOD else Tone.BAD
            DisputeResult.DEFENDANT_WON -> if (d.iAmClaimant) Tone.BAD else Tone.GOOD
            else -> Tone.MUTED
        }
        d.stage == DisputeStage.DETECTED || d.stage == DisputeStage.NOTICE -> Tone.WARN
        else -> Tone.INFO
    }

    fun disputeCard(m: IpModel, d: DisputeRow, f: IpFormat): IpCard {
        val me = m.company
        val other = if (d.iAmClaimant) d.defendant else d.claimant
        val facts = ArrayList<Fact>()
        facts += Fact("Your costs", f.money(d.myCosts)); facts += Fact("Their costs", f.money(d.theirCosts))
        if (d.demand.raw > 0L) facts += Fact("Demand", f.money(d.demand))
        d.estimate?.let { e ->
            val mine = if (d.iAmClaimant) e.claimantWinPpm else 1_000_000L - e.claimantWinPpm
            facts += Fact("Your chance of winning", f.percent(mine), Tones.health(mine))
            facts += Fact("At stake", f.money(e.awardIfWon))
            facts += Fact("A deal is possible", if (e.zoneOfAgreement) "Yes" else "No", if (e.zoneOfAgreement) Tone.GOOD else Tone.MUTED)
        }
        if (d.award.raw > 0L) facts += Fact("Awarded", f.money(d.award))
        facts += Fact("In this stage", f.duration(d.daysInStage))
        val notes = ArrayList<String>()
        if (d.willful) notes += "Knowing infringement."
        if (d.injunctionUntil != null) notes += "A court order bars the use."
        if (d.offerBy != null && d.offerAmount.raw > 0L) notes += "${f.owner(d.offerBy)} offers ${f.money(d.offerAmount)} to settle."
        val actions = ArrayList<IpAction>()
        if (d.stage.open) {
            val theirOffer = d.offerBy != null && d.offerBy != app.many.economytycoon.ip.IpOwner.company(me) && d.offerAmount.raw > 0L
            if (theirOffer) actions += IpAction("Accept ${f.money(d.offerAmount)}", IpCommand.AcceptSettlement(me, d.id), true)
            d.estimate?.let { e ->
                val fair = if (d.iAmClaimant) e.claimantReserve else e.defendantReserve
                actions += IpAction("Offer ${f.money(fair)} to settle", IpCommand.OfferSettlement(me, d.id, fair))
            }
            if (d.iAmClaimant) {
                if (d.stage == DisputeStage.DETECTED) actions += IpAction("Send a demand", IpCommand.SendNotice(me, d.id), true)
                if (d.stage == DisputeStage.DETECTED || d.stage == DisputeStage.NOTICE) actions += IpAction("File suit", IpCommand.FileSuit(me, d.id))
                actions += IpAction("Withdraw", IpCommand.WithdrawSuit(me, d.id))
            }
            if (d.stage == DisputeStage.JUDGMENT) actions += IpAction("Appeal", IpCommand.Appeal(me, d.id))
            if (!d.iAmClaimant && d.patentId != null && d.kind != app.many.economytycoon.ip.DisputeKind.MISAPPROPRIATION) actions += IpAction("Engineer around the patent", IpCommand.StartDesignAround(me, d.patentId, d.id))
        }
        return IpCard(
            "dispute-${d.id.value}", d.patent ?: d.kind.label, (if (d.iAmClaimant) "Against " else "Brought by ") + "${f.owner(other)} in ${f.country(d.country)}",
            if (d.stage == DisputeStage.CLOSED) d.result.label else d.stage.label, stageTone(d), facts, notes.joinToString(" ").ifBlank { null }, actions,
        )
    }

    fun disputes(m: IpModel, f: IpFormat): List<IpSection> = listOf(
        IpSection("Open", "No open disputes.", m.disputes.filter { it.stage.open }.map { disputeCard(m, it, f) }),
        IpSection("Closed", null, m.disputes.filter { !it.stage.open }.take(10).map { disputeCard(m, it, f) }),
    )
}

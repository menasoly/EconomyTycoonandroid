package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money

/** How much attention a headline deserves. */
enum class NewsWeight { MINOR, NOTABLE, MAJOR }

data class IpHeadline(val tick: Long, val text: String, val weight: NewsWeight, val companies: List<CompanyId>)

/** How the host names things when it turns events into text. */
data class IpNames(
    val company: (CompanyId) -> String,
    val money: (Money) -> String,
    val patent: (PatentId) -> String,
)

/**
 * Turns events into headlines for the news feed and into alerts for the company concerned. Routine events (a
 * renewal paid, a single licence signed) give no headline; the ones that change who may sell what do.
 */
object IpNews {
    fun headline(e: IpEvent, state: IpState, n: IpNames): IpHeadline? {
        val t = e.tick.value
        fun ownerName(o: IpOwner): String = o.companyOrNull()?.let(n.company) ?: o.npeOrNull()?.let { state.npeMap[it]?.name } ?: "A patent holder"
        return when (e) {
            is IpEvent.PatentGranted ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "${n.company(p.inventor)} is granted a patent on ${p.title}${if (e.narrowed) ", on narrower claims than it asked for" else ""}", NewsWeight.MINOR, listOf(p.inventor)) }
            is IpEvent.PatentRefused ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "The patent office refuses ${n.company(p.inventor)}'s application on ${p.title}", NewsWeight.MINOR, listOf(p.inventor)) }
            is IpEvent.PatentRevoked ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "A court strikes down the patent on ${p.title}", NewsWeight.MAJOR, listOfNotNull(p.owner.companyOrNull())) }
            is IpEvent.PatentSold ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "${ownerName(e.to)} buys the patent on ${p.title} from ${ownerName(e.from)} for ${n.money(e.price)}", NewsWeight.NOTABLE, listOfNotNull(e.to.companyOrNull(), e.from.companyOrNull())) }
            is IpEvent.PatentLapsed ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "The patent on ${p.title} lapses for unpaid fees", NewsWeight.MINOR, listOfNotNull(p.owner.companyOrNull())) }
            is IpEvent.SecretLost ->
                IpHeadline(t, "${n.company(e.company)} loses a trade secret: ${e.how.label.lowercase()}", NewsWeight.NOTABLE, listOf(e.company))
            is IpEvent.EspionageAttempt ->
                if (e.detected) IpHeadline(t, "${n.company(e.attacker)} is caught ${if (e.success) "stealing" else "trying to steal"} a rival's trade secret", NewsWeight.MAJOR, listOf(e.attacker)) else null
            is IpEvent.PoolFormed -> state.poolMap[e.pool]?.let { IpHeadline(t, "${n.company(it.administrator)} forms the ${it.name} patent pool", NewsWeight.NOTABLE, listOf(it.administrator)) }
            is IpEvent.InfringementDetected ->
                state.patentMap[e.patent]?.let { p -> IpHeadline(t, "${ownerName(p.owner)} accuses ${ownerName(e.defendant)} of infringing the patent on ${p.title}", NewsWeight.NOTABLE, listOfNotNull(p.owner.companyOrNull(), e.defendant.companyOrNull())) }
            is IpEvent.DemandReceived -> IpHeadline(t, "${state.npeMap[e.npe]?.name ?: "A patent holder"} demands ${n.money(e.demand)} from ${n.company(e.target)}", NewsWeight.NOTABLE, listOf(e.target))
            is IpEvent.JudgmentGiven -> state.disputeMap[e.dispute]?.let { d ->
                val who = if (e.forClaimant) d.claimant else d.defendant
                IpHeadline(t, "${ownerName(who)} wins in court${if (e.award.raw > 0L) ", with ${n.money(e.award)} awarded" else ""}${if (e.injunction) " and an order barring sales" else ""}", NewsWeight.MAJOR, listOfNotNull(d.claimant.companyOrNull(), d.defendant.companyOrNull()))
            }
            is IpEvent.DisputeClosed -> state.disputeMap[e.dispute]?.let { d ->
                if (e.result == DisputeResult.SETTLED) IpHeadline(t, "${ownerName(d.claimant)} and ${ownerName(d.defendant)} settle their patent dispute for ${n.money(e.paid)}", NewsWeight.NOTABLE, listOfNotNull(d.claimant.companyOrNull(), d.defendant.companyOrNull())) else null
            }
            is IpEvent.CompulsoryDecided -> IpHeadline(t, if (e.granted) "A government orders a patent to be licensed" else "A government refuses to force a patent licence", NewsWeight.NOTABLE, emptyList())
            is IpEvent.DesignAroundFinished -> if (e.success) IpHeadline(t, "${n.company(e.company)} engineers around a rival's patent", NewsWeight.NOTABLE, listOf(e.company)) else null
            else -> null
        }
    }

    fun headlines(events: List<IpEvent>, state: IpState, n: IpNames): List<IpHeadline> = events.mapNotNull { headline(it, state, n) }

    /** A short alert for the company an event concerns, or null when it concerns it little. */
    fun alertFor(e: IpEvent, company: CompanyId, state: IpState, n: IpNames): String? = when (e) {
        is IpEvent.InfringementDetected -> if (e.defendant == IpOwner.company(company)) "You have been accused of infringing ${n.patent(e.patent)}." else null
        is IpEvent.DemandReceived -> if (e.target == company) "A patent holder wants ${n.money(e.demand)} from you." else null
        is IpEvent.JudgmentGiven -> state.disputeMap[e.dispute]?.let { d ->
            if (!d.involves(IpOwner.company(company))) null
            else if ((d.claimant == IpOwner.company(company)) == e.forClaimant) "You won your patent case." else "You lost your patent case."
        }
        is IpEvent.PatentLapsed -> state.patentMap[e.patent]?.takeIf { it.owner == IpOwner.company(company) }?.let { "Your patent on ${it.title} lapsed." }
        is IpEvent.PatentGranted -> state.patentMap[e.patent]?.takeIf { it.inventor == company }?.let { "Patent granted on ${it.title}." }
        is IpEvent.PatentRefused -> state.patentMap[e.patent]?.takeIf { it.inventor == company }?.let { "The patent office refused ${it.title}." }
        is IpEvent.PatentRevoked -> state.patentMap[e.patent]?.takeIf { it.owner == IpOwner.company(company) }?.let { "A court revoked your patent on ${it.title}." }
        is IpEvent.SecretLost -> if (e.company == company) "A trade secret is gone: ${e.how.label.lowercase()}." else null
        is IpEvent.EspionageAttempt -> state.secretMap[e.secret]?.takeIf { it.owner == company && e.detected }?.let { "Someone tried to steal one of your secrets." }
        is IpEvent.LicenseOffered -> state.licenseMap[e.license]?.takeIf { it.licensee == company }?.let { "You have been offered a licence." }
        is IpEvent.CompulsoryDecided -> state.compulsoryMap[e.request]?.takeIf { it.applicant == company }?.let { if (e.granted) "Your compulsory licence request was granted." else "Your compulsory licence request was refused." }
        is IpEvent.DesignAroundFinished -> if (e.company == company) (if (e.success) "The redesign worked." else "The redesign did not get around the patent.") else null
        is IpEvent.TaxCreditPaid -> if (e.company == company) "Research tax credit of ${n.money(e.amount)} received." else null
        is IpEvent.AuditCompleted -> state.licenseMap[e.license]?.takeIf { it.licensor.companyOrNull() == company && e.underpaid.raw > 0L }?.let { "The audit found ${n.money(e.underpaid)} unpaid." }
        else -> null
    }
}

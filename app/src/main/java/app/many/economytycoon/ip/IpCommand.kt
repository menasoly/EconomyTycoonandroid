package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TechId

/**
 * Everything a company can ask this module to do. The same set serves the player's screens and the computer-controlled
 * companies, so both go through the same checks. [actor] is the company acting.
 */
sealed class IpCommand {
    abstract val actor: CompanyId

    // patents
    data class FilePatent(override val actor: CompanyId, val tech: TechId, val countries: List<CountryId>, val breadth: BreadthChoice = BreadthChoice.STANDARD, val claims: Int = 10, val industry: IndustryFamily? = null) : IpCommand()
    data class AbandonPatent(override val actor: CompanyId, val patent: PatentId, val country: CountryId? = null) : IpCommand()
    data class SetAutoMaintain(override val actor: CompanyId, val patent: PatentId, val on: Boolean) : IpCommand()
    data class PayMaintenance(override val actor: CompanyId, val patent: PatentId, val country: CountryId) : IpCommand()
    data class DeclareEssential(override val actor: CompanyId, val patent: PatentId, val pledgeFrand: Boolean) : IpCommand()

    // trade secrets
    data class KeepSecret(override val actor: CompanyId, val tech: TechId) : IpCommand()
    data class InvestInSecret(override val actor: CompanyId, val secret: SecretId, val amount: Money) : IpCommand()
    data class DropSecret(override val actor: CompanyId, val secret: SecretId) : IpCommand()
    data class StealSecret(override val actor: CompanyId, val secret: SecretId) : IpCommand()

    // licences
    data class OfferLicense(override val actor: CompanyId, val request: OfferRequest) : IpCommand()
    data class AcceptLicense(override val actor: CompanyId, val license: LicenseId) : IpCommand()
    data class DeclineLicense(override val actor: CompanyId, val license: LicenseId) : IpCommand()
    data class WithdrawOffer(override val actor: CompanyId, val license: LicenseId) : IpCommand()
    data class TerminateLicense(override val actor: CompanyId, val license: LicenseId) : IpCommand()
    data class RequestFrandLicense(override val actor: CompanyId, val patent: PatentId) : IpCommand()
    data class AuditLicense(override val actor: CompanyId, val license: LicenseId) : IpCommand()
    data class ProposeCrossLicense(override val actor: CompanyId, val partner: CompanyId, val mine: List<PatentId>, val theirs: List<PatentId>, val termDays: Int = 5 * 360) : IpCommand()

    // pools
    data class FormPool(override val actor: CompanyId, val name: String, val field: TechField, val terms: RoyaltyTerms, val adminFeePpm: Long) : IpCommand()
    data class JoinPool(override val actor: CompanyId, val pool: PoolId, val patents: List<PatentId>) : IpCommand()
    data class LeavePool(override val actor: CompanyId, val pool: PoolId) : IpCommand()
    data class DissolvePool(override val actor: CompanyId, val pool: PoolId) : IpCommand()
    data class OfferPool(override val actor: CompanyId, val pool: PoolId, val licensee: CompanyId) : IpCommand()

    // disputes
    data class ReportInfringement(override val actor: CompanyId, val patent: PatentId, val defendant: CompanyId, val country: CountryId) : IpCommand()
    data class SendNotice(override val actor: CompanyId, val dispute: DisputeId, val demand: Money? = null) : IpCommand()
    data class FileSuit(override val actor: CompanyId, val dispute: DisputeId) : IpCommand()
    data class Appeal(override val actor: CompanyId, val dispute: DisputeId) : IpCommand()
    data class WithdrawSuit(override val actor: CompanyId, val dispute: DisputeId) : IpCommand()
    data class OfferSettlement(override val actor: CompanyId, val dispute: DisputeId, val amount: Money) : IpCommand()
    data class AcceptSettlement(override val actor: CompanyId, val dispute: DisputeId) : IpCommand()
    data class ChallengePatent(override val actor: CompanyId, val patent: PatentId, val country: CountryId) : IpCommand()

    // programmes
    data class StartDesignAround(override val actor: CompanyId, val patent: PatentId, val dispute: DisputeId? = null) : IpCommand()
    data class CancelDesignAround(override val actor: CompanyId, val id: DesignAroundId) : IpCommand()
    data class RequestCompulsory(override val actor: CompanyId, val patent: PatentId, val country: CountryId, val ground: CompulsoryGround) : IpCommand()
    data class WithdrawCompulsory(override val actor: CompanyId, val id: CompulsoryId) : IpCommand()

    // market
    data class ListPatent(override val actor: CompanyId, val patent: PatentId, val price: Money) : IpCommand()
    data class UnlistPatent(override val actor: CompanyId, val patent: PatentId) : IpCommand()
    data class BuyPatent(override val actor: CompanyId, val patent: PatentId) : IpCommand()

    // standing instructions
    data class SetPolicy(override val actor: CompanyId, val policy: IpPolicy) : IpCommand()
}

/** One-line descriptions of commands, for the command log and for confirmations. */
object IpCommandDescriber {
    fun describe(c: IpCommand): String = when (c) {
        is IpCommand.FilePatent -> "File a patent on ${c.tech.key} in ${c.countries.size} country(ies)"
        is IpCommand.AbandonPatent -> "Abandon patent ${c.patent.value}" + (c.country?.let { " in country ${it.value}" } ?: "")
        is IpCommand.SetAutoMaintain -> "Set automatic renewal of patent ${c.patent.value} ${if (c.on) "on" else "off"}"
        is IpCommand.PayMaintenance -> "Pay the renewal fee of patent ${c.patent.value} in country ${c.country.value}"
        is IpCommand.DeclareEssential -> "Declare patent ${c.patent.value} essential to its standard" + if (c.pledgeFrand) " with a FRAND pledge" else ""
        is IpCommand.KeepSecret -> "Keep ${c.tech.key} as a trade secret"
        is IpCommand.InvestInSecret -> "Spend ${c.amount.format()} guarding secret ${c.secret.value}"
        is IpCommand.DropSecret -> "Drop secret ${c.secret.value}"
        is IpCommand.StealSecret -> "Try to obtain secret ${c.secret.value}"
        is IpCommand.OfferLicense -> "Offer a licence on ${c.request.patents.size} patent(s) to company ${c.request.licensee.value}"
        is IpCommand.AcceptLicense -> "Accept licence ${c.license.value}"
        is IpCommand.DeclineLicense -> "Decline licence ${c.license.value}"
        is IpCommand.WithdrawOffer -> "Withdraw licence offer ${c.license.value}"
        is IpCommand.TerminateLicense -> "End licence ${c.license.value}"
        is IpCommand.RequestFrandLicense -> "Ask for a FRAND licence on patent ${c.patent.value}"
        is IpCommand.AuditLicense -> "Audit licence ${c.license.value}"
        is IpCommand.ProposeCrossLicense -> "Propose a cross-licence with company ${c.partner.value}"
        is IpCommand.FormPool -> "Form the patent pool ${c.name}"
        is IpCommand.JoinPool -> "Join pool ${c.pool.value} with ${c.patents.size} patent(s)"
        is IpCommand.LeavePool -> "Leave pool ${c.pool.value}"
        is IpCommand.DissolvePool -> "Dissolve pool ${c.pool.value}"
        is IpCommand.OfferPool -> "Offer pool ${c.pool.value} to company ${c.licensee.value}"
        is IpCommand.ReportInfringement -> "Accuse company ${c.defendant.value} of infringing patent ${c.patent.value}"
        is IpCommand.SendNotice -> "Send a demand in dispute ${c.dispute.value}"
        is IpCommand.FileSuit -> "File suit in dispute ${c.dispute.value}"
        is IpCommand.Appeal -> "Appeal dispute ${c.dispute.value}"
        is IpCommand.WithdrawSuit -> "Withdraw dispute ${c.dispute.value}"
        is IpCommand.OfferSettlement -> "Offer ${c.amount.format()} to settle dispute ${c.dispute.value}"
        is IpCommand.AcceptSettlement -> "Accept the settlement in dispute ${c.dispute.value}"
        is IpCommand.ChallengePatent -> "Challenge the validity of patent ${c.patent.value}"
        is IpCommand.StartDesignAround -> "Design around patent ${c.patent.value}"
        is IpCommand.CancelDesignAround -> "Stop redesign ${c.id.value}"
        is IpCommand.RequestCompulsory -> "Ask for a compulsory licence on patent ${c.patent.value} (${c.ground.label})"
        is IpCommand.WithdrawCompulsory -> "Withdraw compulsory licence request ${c.id.value}"
        is IpCommand.ListPatent -> "List patent ${c.patent.value} for ${c.price.format()}"
        is IpCommand.UnlistPatent -> "Take patent ${c.patent.value} off the market"
        is IpCommand.BuyPatent -> "Buy patent ${c.patent.value}"
        is IpCommand.SetPolicy -> "Set the intellectual property policy"
    }
}

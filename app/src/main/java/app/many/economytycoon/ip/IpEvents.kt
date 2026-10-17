package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

/** Things that happened in the module. The host turns them into news, alerts and the activity log. */
sealed class IpEvent {
    abstract val tick: Tick

    data class PatentFiled(val patent: PatentId, val company: CompanyId, val countries: List<CountryId>, val fees: Money, override val tick: Tick) : IpEvent()
    data class PatentGranted(val patent: PatentId, val country: CountryId, val narrowed: Boolean, override val tick: Tick) : IpEvent()
    data class PatentRefused(val patent: PatentId, val country: CountryId, override val tick: Tick) : IpEvent()
    data class MaintenancePaid(val patent: PatentId, val country: CountryId, val fee: Money, override val tick: Tick) : IpEvent()
    data class PatentLapsed(val patent: PatentId, val country: CountryId, override val tick: Tick) : IpEvent()
    data class PatentExpired(val patent: PatentId, val country: CountryId, override val tick: Tick) : IpEvent()
    data class PatentAbandoned(val patent: PatentId, override val tick: Tick) : IpEvent()
    data class PatentRevoked(val patent: PatentId, val country: CountryId, val dispute: DisputeId?, override val tick: Tick) : IpEvent()
    data class PatentSold(val patent: PatentId, val from: IpOwner, val to: IpOwner, val price: Money, override val tick: Tick) : IpEvent()

    data class SecretKept(val secret: SecretId, val company: CompanyId, override val tick: Tick) : IpEvent()
    data class SecretLost(val secret: SecretId, val company: CompanyId, val how: SecretStatus, override val tick: Tick) : IpEvent()
    data class EspionageAttempt(val secret: SecretId, val attacker: CompanyId, val success: Boolean, val detected: Boolean, override val tick: Tick) : IpEvent()

    data class LicenseOffered(val license: LicenseId, override val tick: Tick) : IpEvent()
    data class LicenseSigned(val license: LicenseId, override val tick: Tick) : IpEvent()
    data class LicenseDeclined(val license: LicenseId, override val tick: Tick) : IpEvent()
    data class LicenseEnded(val license: LicenseId, val status: LicenseStatus, override val tick: Tick) : IpEvent()
    data class RoyaltySettled(val license: LicenseId, val due: Money, val shortfall: Money, override val tick: Tick) : IpEvent()
    data class AuditCompleted(val license: LicenseId, val underpaid: Money, val penalty: Money, override val tick: Tick) : IpEvent()
    data class PoolFormed(val pool: PoolId, override val tick: Tick) : IpEvent()
    data class PoolChanged(val pool: PoolId, val company: CompanyId, val joined: Boolean, override val tick: Tick) : IpEvent()

    data class InfringementDetected(val dispute: DisputeId, val patent: PatentId, val defendant: IpOwner, override val tick: Tick) : IpEvent()
    data class DisputeAdvanced(val dispute: DisputeId, val stage: DisputeStage, override val tick: Tick) : IpEvent()
    data class JudgmentGiven(val dispute: DisputeId, val forClaimant: Boolean, val award: Money, val injunction: Boolean, override val tick: Tick) : IpEvent()
    data class DisputeClosed(val dispute: DisputeId, val result: DisputeResult, val paid: Money, override val tick: Tick) : IpEvent()
    data class DemandReceived(val dispute: DisputeId, val npe: NpeId, val target: CompanyId, val demand: Money, override val tick: Tick) : IpEvent()

    data class DesignAroundFinished(val id: DesignAroundId, val company: CompanyId, val patent: PatentId, val success: Boolean, override val tick: Tick) : IpEvent()
    data class CompulsoryDecided(val request: CompulsoryId, val granted: Boolean, override val tick: Tick) : IpEvent()
    data class TaxCreditPaid(val company: CompanyId, val amount: Money, override val tick: Tick) : IpEvent()
}

/** What a payment is for decides how it hits the books. */
enum class ChargeKind(val reason: ReasonCode, val payerDebit: Chart, val payeeCredit: Chart, val priority: SettlementPriority, val deferrable: Boolean) {
    FILING_FEE(ReasonCode.FEE, Chart.RESEARCH_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.STATUTORY, false),
    MAINTENANCE_FEE(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.STATUTORY, false),
    COURT_FEE(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.STATUTORY, false),
    ROYALTY(ReasonCode.FEE, Chart.COGS, Chart.REVENUE_OTHER, SettlementPriority.SUPPLIERS, true),
    LUMP_SUM(ReasonCode.PURCHASE, Chart.INTANGIBLES, Chart.REVENUE_OTHER, SettlementPriority.SUPPLIERS, true),
    AUDIT_COST(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, false),
    DAMAGES(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, true),
    SETTLEMENT(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, true),
    LEGAL_FEES(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.SUPPLIERS, true),
    PATENT_PURCHASE(ReasonCode.PURCHASE, Chart.INTANGIBLES, Chart.REVENUE_OTHER, SettlementPriority.OTHER, false),
    SECURITY_SPEND(ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, false),
    DESIGN_AROUND_COST(ReasonCode.FEE, Chart.RESEARCH_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, false),
    POOL_DISTRIBUTION(ReasonCode.TRANSFER, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.SUPPLIERS, true),
    TAX_CREDIT(ReasonCode.SUBSIDY, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, SettlementPriority.OTHER, false),
}

/** A payment the module wants made. The host settles it; nothing here touches a ledger. */
data class IpCharge(
    val key: String,
    val kind: ChargeKind,
    val payer: LedgerOwner,
    val payee: LedgerOwner,
    val amount: Money,
    val memo: String,
) {
    init { require(amount.raw > 0L) { "a charge must be positive" } }

    fun toIntent(): SettlementIntent =
        SettlementIntent.simple(key, payer, payee, amount, kind.priority, kind.reason, kind.payerDebit, kind.payeeCredit, kind.deferrable, memo)
}

/** What one step produced. */
data class IpStepResult(val events: List<IpEvent>, val charges: List<IpCharge>) {
    fun intents(): List<SettlementIntent> = charges.map { it.toIntent() }

    companion object {
        val EMPTY = IpStepResult(emptyList(), emptyList())
    }
}

/** Collects events and charges while a step runs and names charges so they can never collide. */
class IpOutput(private val ids: IpIds) {
    val events = ArrayList<IpEvent>()
    val charges = ArrayList<IpCharge>()

    fun event(e: IpEvent) { events += e }

    fun charge(tick: Tick, kind: ChargeKind, payer: LedgerOwner, payee: LedgerOwner, amount: Money, memo: String, ref: String = "") {
        if (amount.raw <= 0L || payer == payee) return
        charges += IpCharge("ip:${kind.name.lowercase()}:$ref:${tick.value}:${ids.charge()}", kind, payer, payee, amount, memo)
    }

    fun result() = IpStepResult(events.toList(), charges.toList())

    fun addAll(other: IpOutput) { events += other.events; charges += other.charges }
}

/** Maps the module's owners onto ledger owners. Non-practising entities stand in for outside investors. */
object IpLedger {
    fun of(owner: IpOwner): LedgerOwner = when (owner.kind) {
        IpOwnerKind.COMPANY -> LedgerOwner.company(CompanyId(owner.id))
        IpOwnerKind.NPE -> LedgerOwner.households()
    }

    fun of(company: CompanyId): LedgerOwner = LedgerOwner.company(company)
    fun government(country: CountryId): LedgerOwner = LedgerOwner.government(country)
    val professionals: LedgerOwner get() = LedgerOwner.households()
    fun households(): LedgerOwner = LedgerOwner.households()
}

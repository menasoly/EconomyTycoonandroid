package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.SettlementIntent

/**
 * Orders that change an urban plan. Some are given by the city government (its player or its computer-controlled
 * mayor), some by companies dealing with it. The executor checks that a company can afford [UrbanService.companyCost]
 * before applying a command.
 */
sealed class UrbanCommand {
    abstract val city: CityId

    // ----- city government ---------------------------------------------------------------------------------------
    data class SetTaxRates(override val city: CityId, val rates: TaxRates) : UrbanCommand()
    data class SetServiceLevel(override val city: CityId, val service: Service, val level: Int) : UrbanCommand()
    data class SetDensityRules(override val city: CityId, val district: Int, val rules: DensityRules) : UrbanCommand()
    data class SetTenantProtection(override val city: CityId, val district: Int, val permille: Int) : UrbanCommand()
    data class AddRoad(override val city: CityId, val a: Int, val b: Int, val roadClass: RoadClass, val lanes: Int) : UrbanCommand()
    data class PlanLine(
        override val city: CityId, val name: String, val mode: TransitMode, val stops: List<Int>, val headwaySeconds: Int, val fare: Money,
        val dedicatedLane: Boolean,
    ) : UrbanCommand()
    data class SetLineHeadway(override val city: CityId, val line: TransitLineId, val headwaySeconds: Int) : UrbanCommand()
    data class SetLineFare(override val city: CityId, val line: TransitLineId, val fare: Money) : UrbanCommand()
    /** Suspends an active line or puts a suspended one back into service. */
    data class ToggleLine(override val city: CityId, val line: TransitLineId) : UrbanCommand()
    data class StartProject(
        override val city: CityId, val kind: ProjectKind, val name: String, val district: Int?, val line: TransitLineId?, val road: LinkId?,
        val size: Long, val fixedPrice: Boolean,
    ) : UrbanCommand()
    data class CancelProject(override val city: CityId, val project: CapitalProjectId) : UrbanCommand()
    data class Borrow(override val city: CityId, val amount: Money) : UrbanCommand()
    data class Repay(override val city: CityId, val amount: Money) : UrbanCommand()
    data class TenderConcession(
        override val city: CityId, val kind: ConcessionKind, val name: String, val road: LinkId?, val line: TransitLineId?, val model: PaymentModel,
        val termMonths: Int, val constructionMonths: Int, val capex: Money, val toll: Money, val availabilityMonthly: Money,
        val cityShareBasePpm: Long, val trafficGuarantee: Long,
    ) : UrbanCommand()
    data class TerminateConcession(override val city: CityId, val concession: ConcessionId) : UrbanCommand()

    // ----- petitions: city or company ---------------------------------------------------------------------------
    /** [applicant] is null when the city itself proposes the change. */
    data class FilePetition(
        override val city: CityId, val district: Int, val kind: PetitionKind, val toZone: ZoneUse, val farMilli: Int, val floors: Int,
        val applicant: CompanyId?,
    ) : UrbanCommand()
    data class WithdrawPetition(override val city: CityId, val petition: PetitionId, val applicant: CompanyId?) : UrbanCommand()

    // ----- companies --------------------------------------------------------------------------------------------
    data class LobbyPetition(override val city: CityId, val petition: PetitionId, val company: CompanyId, val seat: Int?, val amount: Money) : UrbanCommand()
    data class BidConcession(override val city: CityId, val concession: ConcessionId, val company: CompanyId, val discountPpm: Long) : UrbanCommand()
}

/** The result of an accepted command. [intents] are money movements for the ledger to settle; the service never posts. */
data class UrbanReceipt(
    val message: String,
    /** Id of whatever was created (petition, project, line, concession, road), or null. */
    val created: Long? = null,
    val intents: List<SettlementIntent> = emptyList(),
)

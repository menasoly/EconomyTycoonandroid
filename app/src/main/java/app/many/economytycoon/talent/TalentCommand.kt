package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.workforce.CohortKey

/**
 * Everything a player or a rival can ask the talent module to do. The simulation's command layer wraps these; the
 * module itself only needs the plain data.
 */
sealed class TalentCommand {
    /** The company acting, or null for a country-level decision. */
    abstract val actor: CompanyId?

    // ----- education -------------------------------------------------------------------------------------
    data class OpenInstitution(override val actor: CompanyId, val country: CountryId, val region: RegionId, val kind: InstitutionKind, val name: String, val capacity: Long, val tuition: Money, val spend: Money, val fields: List<SkillId>) : TalentCommand()
    data class SetInstitutionTuition(override val actor: CompanyId, val institution: InstitutionId, val tuition: Money) : TalentCommand()
    data class SetInstitutionSpend(override val actor: CompanyId, val institution: InstitutionId, val spend: Money) : TalentCommand()
    data class SetInstitutionFields(override val actor: CompanyId, val institution: InstitutionId, val fields: List<SkillId>) : TalentCommand()
    data class ExpandInstitution(override val actor: CompanyId, val institution: InstitutionId, val seats: Long) : TalentCommand()
    data class CloseInstitution(override val actor: CompanyId, val institution: InstitutionId) : TalentCommand()

    // ----- employer training ----------------------------------------------------------------------------
    data class StartTraining(override val actor: CompanyId, val cohort: CohortKey, val skill: SkillId, val kind: ProgrammeKind, val seats: Long, val days: Int, val clause: RetentionClause? = null) : TalentCommand()
    data class CancelTraining(override val actor: CompanyId, val programme: ProgrammeId) : TalentCommand()

    // ----- training providers -----------------------------------------------------------------------------
    data class OpenProvider(override val actor: CompanyId, val region: RegionId, val name: String) : TalentCommand()
    data class AddCourse(override val actor: CompanyId, val provider: ProviderId, val name: String, val skill: SkillId, val kind: CourseKind, val seats: Long, val tuition: Money) : TalentCommand()
    data class SetCourseTuition(override val actor: CompanyId, val course: CourseId, val tuition: Money) : TalentCommand()
    data class SetCourseSeats(override val actor: CompanyId, val course: CourseId, val seats: Long) : TalentCommand()
    data class CloseCourse(override val actor: CompanyId, val course: CourseId) : TalentCommand()
    data class SetTeaching(override val actor: CompanyId, val provider: ProviderId, val levelPpm: Long) : TalentCommand()
    data class AccreditProvider(override val actor: CompanyId, val provider: ProviderId, val level: Accreditation) : TalentCommand()
    data class CloseProvider(override val actor: CompanyId, val provider: ProviderId) : TalentCommand()
    data class BuyCourseSeats(override val actor: CompanyId, val course: CourseId, val cohort: CohortKey, val seats: Long, val clause: RetentionClause? = null) : TalentCommand()

    // ----- poaching ------------------------------------------------------------------------------------------
    data class SubmitOffer(override val actor: CompanyId, val destination: CohortKey, val target: PoachTarget, val offeredWage: Money, val signingBonus: Money, val stance: CovenantStance) : TalentCommand()
    data class SetCounterPolicy(override val actor: CompanyId, val enabled: Boolean, val maxMatchPpm: Long) : TalentCommand()
    data class SignNonCompete(override val actor: CompanyId, val cohort: CohortKey?, val person: PersonId?, val months: Int, val penaltyPerHead: Money) : TalentCommand()
    data class ReleaseNonCompete(override val actor: CompanyId, val covenant: CovenantId) : TalentCommand()
    data class GrantRetention(override val actor: CompanyId, val cohort: CohortKey, val strengthPpm: Long, val days: Int) : TalentCommand()

    // ----- recruiting and careers ---------------------------------------------------------------------------------
    data class LaunchCampaign(override val actor: CompanyId, val region: RegionId, val role: RoleId, val budget: Money, val days: Int) : TalentCommand()
    data class CancelCampaign(override val actor: CompanyId, val campaign: RecruitCampaignId) : TalentCommand()
    data class CreateLadder(override val actor: CompanyId, val name: String, val rungs: List<Rung>) : TalentCommand()
    data class SetLadderEnabled(override val actor: CompanyId, val ladder: LadderId, val enabled: Boolean) : TalentCommand()
    data class RemoveLadder(override val actor: CompanyId, val ladder: LadderId) : TalentCommand()

    // ----- key people and succession ---------------------------------------------------------------------------------
    data class HirePerson(override val actor: CompanyId, val name: String, val role: RoleId, val region: RegionId, val wage: Money, val age: Int) : TalentCommand()
    data class OpenPosition(override val actor: CompanyId, val role: RoleId, val region: RegionId, val title: String) : TalentCommand()
    data class AppointPerson(override val actor: CompanyId, val position: PositionId, val person: PersonId) : TalentCommand()
    data class NameSuccessor(override val actor: CompanyId, val position: PositionId, val person: PersonId, val development: DevelopmentKind) : TalentCommand()
    data class RemoveSuccessor(override val actor: CompanyId, val position: PositionId, val person: PersonId) : TalentCommand()
    data class SetDevelopment(override val actor: CompanyId, val position: PositionId, val person: PersonId, val development: DevelopmentKind) : TalentCommand()

    // ----- country policy -------------------------------------------------------------------------------------------------
    data class SetImmigrationPolicy(val country: CountryId, val opennessPpm: Long, val quotaPpm: Long, val skilledOnly: Boolean) : TalentCommand() {
        override val actor: CompanyId? get() = null
    }
}

/** What a command produced, for the caller to post, apply and publish. */
data class TalentResult(
    val intents: List<app.many.economytycoon.finance.SettlementIntent> = emptyList(),
    val effects: List<WorkforceEffect> = emptyList(),
    val events: List<TalentEvent> = emptyList(),
    /** A short line for the player: the new thing's name or the outcome. */
    val note: String = "",
) {
    companion object { val DONE = TalentResult() }
}

package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Things that happened in the talent system, for the news feed, the activity log and alerts. */
sealed class TalentEvent {
    abstract val tick: Tick

    // education
    data class InstitutionOpened(val institution: InstitutionId, val owner: CompanyId?, val region: RegionId, val kind: InstitutionKind, override val tick: Tick) : TalentEvent()
    data class InstitutionClosed(val institution: InstitutionId, override val tick: Tick) : TalentEvent()
    data class CapacityReady(val institution: InstitutionId, val seats: Long, override val tick: Tick) : TalentEvent()
    data class ClassGraduated(val institution: InstitutionId, val skill: SkillId, val count: Long, val levelPpm: Long, override val tick: Tick) : TalentEvent()
    data class PlacesShort(val region: RegionId, val kind: InstitutionKind, val unmet: Long, override val tick: Tick) : TalentEvent()

    // skills market
    data class ShortageRaised(val region: RegionId, val skill: SkillId, val kind: ShortageKind, override val tick: Tick) : TalentEvent()
    data class ShortageEased(val region: RegionId, val skill: SkillId, override val tick: Tick) : TalentEvent()

    // training
    data class ProgrammeStarted(val programme: ProgrammeId, val company: CompanyId, override val tick: Tick) : TalentEvent()
    data class ProgrammeCompleted(val programme: ProgrammeId, val company: CompanyId, val trainees: Long, override val tick: Tick) : TalentEvent()
    data class ProgrammeCancelled(val programme: ProgrammeId, val company: CompanyId, override val tick: Tick) : TalentEvent()
    data class ClawbackCharged(val programme: ProgrammeId, val company: CompanyId, val leavers: Long, override val tick: Tick) : TalentEvent()

    // poaching and covenants
    data class PoachOffered(val offer: OfferId, val hunter: CompanyId, val victim: CompanyId, override val tick: Tick) : TalentEvent()
    data class PoachSucceeded(val offer: OfferId, val hunter: CompanyId, val victim: CompanyId, val heads: Long, override val tick: Tick) : TalentEvent()
    data class PoachCountered(val offer: OfferId, val hunter: CompanyId, val victim: CompanyId, val kept: Long, override val tick: Tick) : TalentEvent()
    data class PoachFailed(val offer: OfferId, val hunter: CompanyId, val victim: CompanyId, override val tick: Tick) : TalentEvent()
    data class CovenantSigned(val covenant: CovenantId, val company: CompanyId, override val tick: Tick) : TalentEvent()
    data class CovenantBreached(val covenant: CovenantId, val company: CompanyId, val hunter: CompanyId, override val tick: Tick) : TalentEvent()

    // employer brand
    data class CampaignStarted(val campaign: RecruitCampaignId, val company: CompanyId, override val tick: Tick) : TalentEvent()
    data class CampaignEnded(val campaign: RecruitCampaignId, val company: CompanyId, val hired: Long, override val tick: Tick) : TalentEvent()
    data class GraduatesRecruited(val campaign: RecruitCampaignId, val company: CompanyId, val count: Long, override val tick: Tick) : TalentEvent()

    // careers and succession
    data class Promoted(val ladder: LadderId, val company: CompanyId, val heads: Long, override val tick: Tick) : TalentEvent()
    data class PositionVacated(val position: PositionId, val company: CompanyId, val coveredBySuccessor: Boolean, override val tick: Tick) : TalentEvent()
    data class PositionFilled(val position: PositionId, val company: CompanyId, val person: PersonId, override val tick: Tick) : TalentEvent()

    // providers
    data class ProviderOpened(val provider: ProviderId, val owner: CompanyId, override val tick: Tick) : TalentEvent()
    data class AccreditationChanged(val provider: ProviderId, val level: Accreditation, override val tick: Tick) : TalentEvent()
    data class CourseCompleted(val provider: ProviderId, val course: CourseId, val graduates: Long, override val tick: Tick) : TalentEvent()

    // migration
    data class MigrationWave(val from: CountryId, val to: CountryId, val skill: SkillId, val heads: Long, override val tick: Tick) : TalentEvent()
}

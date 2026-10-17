package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.talent.Accreditation
import app.many.economytycoon.talent.CourseId
import app.many.economytycoon.talent.CourseKind
import app.many.economytycoon.talent.CovenantId
import app.many.economytycoon.talent.CovenantStance
import app.many.economytycoon.talent.DevelopmentKind
import app.many.economytycoon.talent.InstitutionId
import app.many.economytycoon.talent.InstitutionKind
import app.many.economytycoon.talent.LadderId
import app.many.economytycoon.talent.PersonId
import app.many.economytycoon.talent.PoachTarget
import app.many.economytycoon.talent.PositionId
import app.many.economytycoon.talent.ProgrammeId
import app.many.economytycoon.talent.ProgrammeKind
import app.many.economytycoon.talent.ProviderId
import app.many.economytycoon.talent.RecruitCampaignId
import app.many.economytycoon.talent.RetentionClause
import app.many.economytycoon.talent.Rung
import app.many.economytycoon.talent.SkillId
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.workforce.CohortKey

/**
 * Binary form of a [TalentCommand] for the command log and replays. The company is written by the caller and is the
 * actor on reading; each command here is a sub-tag followed by its other fields (money as raw varints, enums by
 * ordinal, optional parts behind a presence byte). Sub-tags never change meaning, and an unknown one makes the log
 * unreadable. The game's own immigration orders (a command with no company) have a top-level tag of their own.
 */
object TalentCommandCodec {
    fun write(c: TalentCommand, w: ByteWriter) {
        when (c) {
            is TalentCommand.OpenInstitution -> {
                w.writeVarInt(1); w.writeVarLong(c.country.value); w.writeVarLong(c.region.value); w.writeVarInt(c.kind.ordinal); w.writeString(c.name)
                w.writeVarLong(c.capacity); w.writeMoney(c.tuition); w.writeMoney(c.spend); skills(w, c.fields)
            }
            is TalentCommand.SetInstitutionTuition -> { w.writeVarInt(2); w.writeVarLong(c.institution.value); w.writeMoney(c.tuition) }
            is TalentCommand.SetInstitutionSpend -> { w.writeVarInt(3); w.writeVarLong(c.institution.value); w.writeMoney(c.spend) }
            is TalentCommand.SetInstitutionFields -> { w.writeVarInt(4); w.writeVarLong(c.institution.value); skills(w, c.fields) }
            is TalentCommand.ExpandInstitution -> { w.writeVarInt(5); w.writeVarLong(c.institution.value); w.writeVarLong(c.seats) }
            is TalentCommand.CloseInstitution -> { w.writeVarInt(6); w.writeVarLong(c.institution.value) }
            is TalentCommand.StartTraining -> {
                w.writeVarInt(7); key(w, c.cohort); w.writeString(c.skill.key); w.writeVarInt(c.kind.ordinal); w.writeVarLong(c.seats); w.writeVarInt(c.days); clause(w, c.clause)
            }
            is TalentCommand.CancelTraining -> { w.writeVarInt(8); w.writeVarLong(c.programme.value) }
            is TalentCommand.OpenProvider -> { w.writeVarInt(9); w.writeVarLong(c.region.value); w.writeString(c.name) }
            is TalentCommand.AddCourse -> {
                w.writeVarInt(10); w.writeVarLong(c.provider.value); w.writeString(c.name); w.writeString(c.skill.key); w.writeVarInt(c.kind.ordinal)
                w.writeVarLong(c.seats); w.writeMoney(c.tuition)
            }
            is TalentCommand.SetCourseTuition -> { w.writeVarInt(11); w.writeVarLong(c.course.value); w.writeMoney(c.tuition) }
            is TalentCommand.SetCourseSeats -> { w.writeVarInt(12); w.writeVarLong(c.course.value); w.writeVarLong(c.seats) }
            is TalentCommand.CloseCourse -> { w.writeVarInt(13); w.writeVarLong(c.course.value) }
            is TalentCommand.SetTeaching -> { w.writeVarInt(14); w.writeVarLong(c.provider.value); w.writeVarLong(c.levelPpm) }
            is TalentCommand.AccreditProvider -> { w.writeVarInt(15); w.writeVarLong(c.provider.value); w.writeVarInt(c.level.ordinal) }
            is TalentCommand.CloseProvider -> { w.writeVarInt(16); w.writeVarLong(c.provider.value) }
            is TalentCommand.BuyCourseSeats -> { w.writeVarInt(17); w.writeVarLong(c.course.value); key(w, c.cohort); w.writeVarLong(c.seats); clause(w, c.clause) }
            is TalentCommand.SubmitOffer -> {
                w.writeVarInt(18); key(w, c.destination); target(w, c.target); w.writeMoney(c.offeredWage); w.writeMoney(c.signingBonus); w.writeVarInt(c.stance.ordinal)
            }
            is TalentCommand.SetCounterPolicy -> { w.writeVarInt(19); w.writeBoolean(c.enabled); w.writeVarLong(c.maxMatchPpm) }
            is TalentCommand.SignNonCompete -> {
                w.writeVarInt(20); w.writeBoolean(c.cohort != null); c.cohort?.let { key(w, it) }
                w.writeBoolean(c.person != null); c.person?.let { w.writeVarLong(it.value) }; w.writeVarInt(c.months); w.writeMoney(c.penaltyPerHead)
            }
            is TalentCommand.ReleaseNonCompete -> { w.writeVarInt(21); w.writeVarLong(c.covenant.value) }
            is TalentCommand.GrantRetention -> { w.writeVarInt(22); key(w, c.cohort); w.writeVarLong(c.strengthPpm); w.writeVarInt(c.days) }
            is TalentCommand.LaunchCampaign -> { w.writeVarInt(23); w.writeVarLong(c.region.value); w.writeString(c.role.key); w.writeMoney(c.budget); w.writeVarInt(c.days) }
            is TalentCommand.CancelCampaign -> { w.writeVarInt(24); w.writeVarLong(c.campaign.value) }
            is TalentCommand.CreateLadder -> {
                w.writeVarInt(25); w.writeString(c.name); w.writeVarInt(c.rungs.size)
                for (r in c.rungs) { key(w, r.cohort); w.writeVarLong(r.minTenureTicks); w.writeVarLong(r.minLevelPpm); w.writeVarLong(r.raisePpm) }
            }
            is TalentCommand.SetLadderEnabled -> { w.writeVarInt(26); w.writeVarLong(c.ladder.value); w.writeBoolean(c.enabled) }
            is TalentCommand.RemoveLadder -> { w.writeVarInt(27); w.writeVarLong(c.ladder.value) }
            is TalentCommand.HirePerson -> {
                w.writeVarInt(28); w.writeString(c.name); w.writeString(c.role.key); w.writeVarLong(c.region.value); w.writeMoney(c.wage); w.writeVarInt(c.age)
            }
            is TalentCommand.OpenPosition -> { w.writeVarInt(29); w.writeString(c.role.key); w.writeVarLong(c.region.value); w.writeString(c.title) }
            is TalentCommand.AppointPerson -> { w.writeVarInt(30); w.writeVarLong(c.position.value); w.writeVarLong(c.person.value) }
            is TalentCommand.NameSuccessor -> { w.writeVarInt(31); w.writeVarLong(c.position.value); w.writeVarLong(c.person.value); w.writeVarInt(c.development.ordinal) }
            is TalentCommand.RemoveSuccessor -> { w.writeVarInt(32); w.writeVarLong(c.position.value); w.writeVarLong(c.person.value) }
            is TalentCommand.SetDevelopment -> { w.writeVarInt(33); w.writeVarLong(c.position.value); w.writeVarLong(c.person.value); w.writeVarInt(c.development.ordinal) }
            // The executor refuses a company's policy order, but a refused order can still be waiting in a saved queue, so it must be writable.
            is TalentCommand.SetImmigrationPolicy -> { w.writeVarInt(34); w.writeVarLong(c.country.value); w.writeVarLong(c.opennessPpm); w.writeVarLong(c.quotaPpm); w.writeBoolean(c.skilledOnly) }
        }
    }

    fun read(r: ByteReader, actor: CompanyId): TalentCommand {
        val t = r.readVarInt()
        return when (t) {
            1 -> TalentCommand.OpenInstitution(
                actor, CountryId(r.readVarLong()), RegionId(r.readVarLong()), at(InstitutionKind.values(), r.readVarInt(), "institution kind"), r.readString(),
                r.readVarLong(), r.readMoney(), r.readMoney(), skills(r),
            )
            2 -> TalentCommand.SetInstitutionTuition(actor, InstitutionId(r.readVarLong()), r.readMoney())
            3 -> TalentCommand.SetInstitutionSpend(actor, InstitutionId(r.readVarLong()), r.readMoney())
            4 -> TalentCommand.SetInstitutionFields(actor, InstitutionId(r.readVarLong()), skills(r))
            5 -> TalentCommand.ExpandInstitution(actor, InstitutionId(r.readVarLong()), r.readVarLong())
            6 -> TalentCommand.CloseInstitution(actor, InstitutionId(r.readVarLong()))
            7 -> TalentCommand.StartTraining(actor, key(r), SkillId(r.readString()), at(ProgrammeKind.values(), r.readVarInt(), "programme kind"), r.readVarLong(), r.readVarInt(), clause(r))
            8 -> TalentCommand.CancelTraining(actor, ProgrammeId(r.readVarLong()))
            9 -> TalentCommand.OpenProvider(actor, RegionId(r.readVarLong()), r.readString())
            10 -> TalentCommand.AddCourse(
                actor, ProviderId(r.readVarLong()), r.readString(), SkillId(r.readString()), at(CourseKind.values(), r.readVarInt(), "course kind"), r.readVarLong(), r.readMoney(),
            )
            11 -> TalentCommand.SetCourseTuition(actor, CourseId(r.readVarLong()), r.readMoney())
            12 -> TalentCommand.SetCourseSeats(actor, CourseId(r.readVarLong()), r.readVarLong())
            13 -> TalentCommand.CloseCourse(actor, CourseId(r.readVarLong()))
            14 -> TalentCommand.SetTeaching(actor, ProviderId(r.readVarLong()), r.readVarLong())
            15 -> TalentCommand.AccreditProvider(actor, ProviderId(r.readVarLong()), at(Accreditation.values(), r.readVarInt(), "accreditation"))
            16 -> TalentCommand.CloseProvider(actor, ProviderId(r.readVarLong()))
            17 -> TalentCommand.BuyCourseSeats(actor, CourseId(r.readVarLong()), key(r), r.readVarLong(), clause(r))
            18 -> TalentCommand.SubmitOffer(actor, key(r), target(r), r.readMoney(), r.readMoney(), at(CovenantStance.values(), r.readVarInt(), "covenant stance"))
            19 -> TalentCommand.SetCounterPolicy(actor, r.readBoolean(), r.readVarLong())
            20 -> {
                val cohort = if (r.readBoolean()) key(r) else null
                val person = if (r.readBoolean()) PersonId(r.readVarLong()) else null
                TalentCommand.SignNonCompete(actor, cohort, person, r.readVarInt(), r.readMoney())
            }
            21 -> TalentCommand.ReleaseNonCompete(actor, CovenantId(r.readVarLong()))
            22 -> TalentCommand.GrantRetention(actor, key(r), r.readVarLong(), r.readVarInt())
            23 -> TalentCommand.LaunchCampaign(actor, RegionId(r.readVarLong()), RoleId(r.readString()), r.readMoney(), r.readVarInt())
            24 -> TalentCommand.CancelCampaign(actor, RecruitCampaignId(r.readVarLong()))
            25 -> {
                val name = r.readString(); val n = r.readVarInt()
                if (n < 0 || n > MAX_RUNGS) throw ByteReader.CorruptDataException("implausible number of rungs $n")
                TalentCommand.CreateLadder(actor, name, List(n) { Rung(key(r), r.readVarLong(), r.readVarLong(), r.readVarLong()) })
            }
            26 -> TalentCommand.SetLadderEnabled(actor, LadderId(r.readVarLong()), r.readBoolean())
            27 -> TalentCommand.RemoveLadder(actor, LadderId(r.readVarLong()))
            28 -> TalentCommand.HirePerson(actor, r.readString(), RoleId(r.readString()), RegionId(r.readVarLong()), r.readMoney(), r.readVarInt())
            29 -> TalentCommand.OpenPosition(actor, RoleId(r.readString()), RegionId(r.readVarLong()), r.readString())
            30 -> TalentCommand.AppointPerson(actor, PositionId(r.readVarLong()), PersonId(r.readVarLong()))
            31 -> TalentCommand.NameSuccessor(actor, PositionId(r.readVarLong()), PersonId(r.readVarLong()), at(DevelopmentKind.values(), r.readVarInt(), "development"))
            32 -> TalentCommand.RemoveSuccessor(actor, PositionId(r.readVarLong()), PersonId(r.readVarLong()))
            33 -> TalentCommand.SetDevelopment(actor, PositionId(r.readVarLong()), PersonId(r.readVarLong()), at(DevelopmentKind.values(), r.readVarInt(), "development"))
            34 -> TalentCommand.SetImmigrationPolicy(CountryId(r.readVarLong()), r.readVarLong(), r.readVarLong(), r.readBoolean())
            else -> throw ByteReader.CorruptDataException("unknown talent command tag $t")
        }
    }

    private const val MAX_RUNGS = 32
    private const val MAX_SKILLS = 64

    private fun key(w: ByteWriter, k: CohortKey) { w.writeVarLong(k.company.value); w.writeVarLong(k.facility.value); w.writeString(k.role.key); w.writeVarLong(k.region.value) }
    private fun key(r: ByteReader) = CohortKey(CompanyId(r.readVarLong()), FacilityId(r.readVarLong()), RoleId(r.readString()), RegionId(r.readVarLong()))

    private fun skills(w: ByteWriter, list: List<SkillId>) { w.writeVarInt(list.size); for (s in list) w.writeString(s.key) }
    private fun skills(r: ByteReader): List<SkillId> {
        val n = r.readVarInt()
        if (n < 0 || n > MAX_SKILLS) throw ByteReader.CorruptDataException("implausible number of subjects $n")
        return List(n) { SkillId(r.readString()) }
    }

    private fun clause(w: ByteWriter, c: RetentionClause?) { w.writeBoolean(c != null); if (c != null) { w.writeVarInt(c.bondDays); w.writeVarLong(c.clawbackPpm) } }
    private fun clause(r: ByteReader): RetentionClause? = if (r.readBoolean()) RetentionClause(r.readVarInt(), r.readVarLong()) else null

    private fun target(w: ByteWriter, t: PoachTarget) {
        when (t) {
            is PoachTarget.Group -> { w.writeVarInt(0); key(w, t.cohort); w.writeVarLong(t.count); w.writeVarLong(t.minLevelPpm) }
            is PoachTarget.Individual -> { w.writeVarInt(1); w.writeVarLong(t.person.value) }
        }
    }
    private fun target(r: ByteReader): PoachTarget = when (val t = r.readVarInt()) {
        0 -> PoachTarget.Group(key(r), r.readVarLong(), r.readVarLong())
        1 -> PoachTarget.Individual(PersonId(r.readVarLong()))
        else -> throw ByteReader.CorruptDataException("unknown poach target $t")
    }

    private fun <T> at(values: Array<T>, i: Int, what: String): T = values.getOrNull(i) ?: throw ByteReader.CorruptDataException("bad $what ordinal $i")
}

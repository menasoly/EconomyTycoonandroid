package app.many.economytycoon.activity

import app.many.economytycoon.talent.PoachTarget
import app.many.economytycoon.talent.TalentCommand

/** Writes an order of the talent module as a sentence a player would recognise. */
object TalentCommandDescriber {
    private fun pct(ppm: Long): String = if (ppm % 10_000L == 0L) "${ppm / 10_000L}%" else "%.1f%%".format(ppm / 10_000.0)
    private fun words(key: String) = key.replace('_', ' ')

    fun describe(cmd: TalentCommand, n: DescribeNames = DescribeNames.PLAIN): String = when (cmd) {
        is TalentCommand.OpenInstitution -> "Open ${cmd.kind.label.lowercase()} ${cmd.name} (${cmd.capacity} seats) in ${n.region(cmd.region)}"
        is TalentCommand.SetInstitutionTuition -> "Set the fee at institution ${cmd.institution.value} to ${n.money(cmd.tuition)} a year"
        is TalentCommand.SetInstitutionSpend -> "Set the teaching spend at institution ${cmd.institution.value} to ${n.money(cmd.spend)} a student a year"
        is TalentCommand.SetInstitutionFields -> "Changed the subjects taught at institution ${cmd.institution.value}"
        is TalentCommand.ExpandInstitution -> "Build ${cmd.seats} more seats at institution ${cmd.institution.value}"
        is TalentCommand.CloseInstitution -> "Closed institution ${cmd.institution.value}"
        is TalentCommand.StartTraining -> "Start ${cmd.kind.label.lowercase()} training in ${words(cmd.skill.key)} for ${cmd.seats} staff over ${cmd.days} days"
        is TalentCommand.CancelTraining -> "Cancelled training programme ${cmd.programme.value}"
        is TalentCommand.OpenProvider -> "Open the training business ${cmd.name} in ${n.region(cmd.region)}"
        is TalentCommand.AddCourse -> "Add the ${cmd.kind.label.lowercase()} ${cmd.name} (${words(cmd.skill.key)}, ${cmd.seats} seats) at ${n.money(cmd.tuition)}"
        is TalentCommand.SetCourseTuition -> "Set the price of course ${cmd.course.value} to ${n.money(cmd.tuition)}"
        is TalentCommand.SetCourseSeats -> "Set the seats of course ${cmd.course.value} to ${cmd.seats}"
        is TalentCommand.CloseCourse -> "Closed course ${cmd.course.value}"
        is TalentCommand.SetTeaching -> "Set the teaching level of provider ${cmd.provider.value} to ${pct(cmd.levelPpm)}"
        is TalentCommand.AccreditProvider -> "Apply for ${cmd.level.label.lowercase()} accreditation for provider ${cmd.provider.value}"
        is TalentCommand.CloseProvider -> "Closed training provider ${cmd.provider.value}"
        is TalentCommand.BuyCourseSeats -> "Buy ${cmd.seats} seats on course ${cmd.course.value} for ${words(cmd.cohort.role.key)} staff"
        is TalentCommand.SubmitOffer -> when (val t = cmd.target) {
            is PoachTarget.Group -> "Offer ${n.money(cmd.offeredWage)} a day to ${t.count} ${words(t.cohort.role.key)} staff of ${n.company(t.cohort.company)}"
            is PoachTarget.Individual -> "Offer ${n.money(cmd.offeredWage)} a day to person ${t.person.value}"
        }
        is TalentCommand.SetCounterPolicy -> if (cmd.enabled) "Match rival offers up to ${pct(cmd.maxMatchPpm)} more pay" else "Stopped matching rival offers"
        is TalentCommand.SignNonCompete -> "Sign a ${cmd.months}-month non-compete with ${cmd.cohort?.let { words(it.role.key) + " staff" } ?: "a key person"}"
        is TalentCommand.ReleaseNonCompete -> "Released non-compete ${cmd.covenant.value}"
        is TalentCommand.GrantRetention -> "Grant a retention bonus to ${words(cmd.cohort.role.key)} staff for ${cmd.days} days"
        is TalentCommand.LaunchCampaign -> "Run a recruiting campaign for ${words(cmd.role.key)} in ${n.region(cmd.region)} with ${n.money(cmd.budget)} over ${cmd.days} days"
        is TalentCommand.CancelCampaign -> "Cancelled recruiting campaign ${cmd.campaign.value}"
        is TalentCommand.CreateLadder -> "Create the career ladder ${cmd.name} (${cmd.rungs.size} rungs)"
        is TalentCommand.SetLadderEnabled -> if (cmd.enabled) "Resumed career ladder ${cmd.ladder.value}" else "Paused career ladder ${cmd.ladder.value}"
        is TalentCommand.RemoveLadder -> "Removed career ladder ${cmd.ladder.value}"
        is TalentCommand.HirePerson -> "Hire ${cmd.name} as ${words(cmd.role.key)} at ${n.money(cmd.wage)} a day"
        is TalentCommand.OpenPosition -> "Create the key position ${cmd.title}"
        is TalentCommand.AppointPerson -> "Appoint person ${cmd.person.value} to position ${cmd.position.value}"
        is TalentCommand.NameSuccessor -> "Name person ${cmd.person.value} as successor to position ${cmd.position.value} (${cmd.development.label.lowercase()})"
        is TalentCommand.RemoveSuccessor -> "Removed person ${cmd.person.value} as successor to position ${cmd.position.value}"
        is TalentCommand.SetDevelopment -> "Set the development of person ${cmd.person.value} to ${cmd.development.label.lowercase()}"
        is TalentCommand.SetImmigrationPolicy -> "Set the immigration rules of country ${cmd.country.value}"
    }

    /** True for orders that spend money straight away. */
    fun movesMoney(cmd: TalentCommand): Boolean = when (cmd) {
        is TalentCommand.OpenInstitution, is TalentCommand.ExpandInstitution, is TalentCommand.StartTraining, is TalentCommand.OpenProvider,
        is TalentCommand.AccreditProvider, is TalentCommand.BuyCourseSeats, is TalentCommand.SubmitOffer, is TalentCommand.GrantRetention,
        is TalentCommand.LaunchCampaign -> true
        else -> false
    }
}

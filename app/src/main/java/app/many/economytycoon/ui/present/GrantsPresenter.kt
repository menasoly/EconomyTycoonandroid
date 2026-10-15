package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GrantId
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import app.many.economytycoon.grants.GrantStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class GrantRow(
    val id: GrantId,
    val title: String,
    val country: String,
    val blurb: String,
    val terms: List<Fact>,
    val status: String,
    val tone: Tone,
    /** Where the programme stands in time: how long applications stay open, or how long growth is counted. */
    val timing: String,
    /** What the selected company has done about it. */
    val standing: String?,
    val canApply: Boolean,
    val canWithdraw: Boolean,
)

data class GrantsModel(
    val received: String,
    val open: List<GrantRow>,
    val running: List<GrantRow>,
    val finished: List<GrantRow>,
    val note: String,
)

object GrantsPresenter {
    fun title(g: GrantProgram): String = when (g.kind) {
        GrantKind.JOBS -> "New jobs"
        GrantKind.FACILITIES -> "New ${g.family?.displayName?.lowercase() ?: "industrial"} facilities"
    }

    fun statusLabel(s: GrantStatus): String = when (s) {
        GrantStatus.OPEN -> "Taking applications"
        GrantStatus.ACTIVE -> "Counting growth"
        GrantStatus.PAID -> "Paid out"
        GrantStatus.NO_APPLICANTS -> "No applicants"
    }

    fun statusTone(s: GrantStatus): Tone = when (s) {
        GrantStatus.OPEN -> Tone.INFO
        GrantStatus.ACTIVE -> Tone.WARN
        GrantStatus.PAID -> Tone.GOOD
        GrantStatus.NO_APPLICANTS -> Tone.MUTED
    }

    private fun timing(g: GrantProgram, now: Long): String = when (g.status) {
        GrantStatus.OPEN -> "Applications close in ${Formatting.plural((g.applyUntil.value - now).coerceAtLeast(0L), "day")}"
        GrantStatus.ACTIVE -> "Ends in ${Formatting.plural((g.endsAt.value - now).coerceAtLeast(0L), "day")}"
        else -> "Finished ${Formatting.date(g.endedAt ?: g.endsAt)}"
    }

    private fun standing(session: GameSession, g: GrantProgram, company: CompanyId?): String? {
        company ?: return null
        val a = g.applicationOf(company)
        val unit = g.kind.unit
        if (a == null) return if (g.isOpen) "You have not applied." else null
        if (a.settled) {
            return if (a.paid.raw > 0L) "Paid ${session.settings.money(a.paid)} for growth of ${Formatting.plural(a.gain, unit)}." else "Nothing paid: ${if (a.gain == 0L) "no growth" else "the treasury was empty"}."
        }
        if (g.isOpen) return "Applied, starting from ${a.baseline}."
        val gain = (session.economy.grantRunner.countFor(g, company) - a.baseline).coerceAtLeast(0L)
        val would = Formatting.plural(gain, unit)
        return "Up ${would} so far, worth ${session.settings.money(app.many.economytycoon.core.Money.min(g.cap, g.rate * gain))} before any scaling."
    }

    fun row(session: GameSession, g: GrantProgram, company: CompanyId?): GrantRow {
        val money = session.settings::money
        val country = session.economy.world.countries().firstOrNull { it.id == g.country }?.name ?: "A government"
        val mine = company?.let { g.applicationOf(it) }
        return GrantRow(
            id = g.id, title = title(g), country = country, blurb = g.kind.blurb,
            terms = listOf(
                Fact("Pays", "${money(g.rate)} per ${g.kind.unit}"),
                Fact("Most per company", money(g.cap)),
                Fact("Programme budget", money(g.budget)),
                Fact("Applicants", g.applications.size.toString()),
            ),
            status = statusLabel(g.status), tone = statusTone(g.status), timing = timing(g, session.tick.value),
            standing = standing(session, g, company),
            canApply = company != null && g.isOpen && mine == null,
            canWithdraw = company != null && g.isRunning && mine != null,
        )
    }

    fun build(session: GameSession): GrantsModel? {
        val company = session.selectedCompany ?: return null
        val e = session.economy
        fun rows(list: List<GrantProgram>) = list.map { row(session, it, company) }
        val running = e.grants.active() + e.grants.open().filter { it.applicationOf(company) != null }
        return GrantsModel(
            received = session.settings.money(e.grants.receivedBy(company)),
            open = rows(e.grants.open().filter { it.applicationOf(company) == null }),
            running = rows(running.sortedBy { it.endsAt }),
            finished = rows(e.grants.history().sortedByDescending { it.endedAt?.value ?: 0L }.take(10)),
            note = "Governments pay for growth. Apply while a programme is open; what you add afterwards is counted and paid at the end, as far as the treasury has the money.",
        )
    }
}

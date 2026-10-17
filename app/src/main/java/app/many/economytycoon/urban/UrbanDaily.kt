package app.many.economytycoon.urban

import app.many.economytycoon.core.Tick

/** The daily cycle of a city: projects draw their money, petitions move through council, concessions change state. */
internal object UrbanDaily {
    fun run(state: UrbanState, plan: CityPlan, hooks: List<LobbyingHook>, now: Tick): List<UrbanNotice> {
        val notices = ArrayList<UrbanNotice>()
        projects(state, plan, now, notices)
        petitions(state, plan, hooks, now, notices)
        concessions(plan, now, notices)
        return notices
    }

    private fun projects(state: UrbanState, plan: CityPlan, now: Tick, notices: MutableList<UrbanNotice>) {
        val spend = { m: app.many.economytycoon.core.Money ->
            val ok = plan.budget.spendCapital(m)
            if (ok) plan.capitalThisMonth += m
            ok
        }
        for (e in plan.projects.advanceDay(now, spend, state.stream("projects", plan.city))) {
            val p = plan.projects.get(e.project) ?: continue
            when (e) {
                is ProjectEvent.PhaseFinished -> if (e.overrunPpm > 50_000L || e.delayDays > 20) {
                    notices += UrbanNotice(plan.city, now, NoticeKind.PROJECT_PHASE, "${p.name}: ${e.phase.label.lowercase()} finished ${e.overrunPpm / 10_000L}% over on cost and ${e.delayDays} days late.", p.id.value)
                }
                is ProjectEvent.Stalled -> notices += UrbanNotice(plan.city, now, NoticeKind.PROJECT_STALLED, "${p.name} has stopped: the city cannot pay for the work.", p.id.value)
                is ProjectEvent.Resumed -> notices += UrbanNotice(plan.city, now, NoticeKind.PROJECT_RESUMED, "${p.name} is under way again after ${e.stalledDays} days.", p.id.value)
                is ProjectEvent.Completed -> {
                    UrbanEffects.projectCompleted(plan, p, state.params)
                    notices += UrbanNotice(plan.city, now, NoticeKind.PROJECT_DONE, "${p.name} is finished, ${e.overrunPpm / 10_000L}% over its original budget.", p.id.value)
                }
                is ProjectEvent.Cancelled -> {}
            }
        }
    }

    private fun petitions(state: UrbanState, plan: CityPlan, hooks: List<LobbyingHook>, now: Tick, notices: MutableList<UrbanNotice>) {
        val ctx = PetitionContext(plan.geometry, plan.districts, plan.council, state.params, state.stream("votes", plan.city), hooks)
        for (e in plan.petitions.advanceDay(now, ctx)) {
            val p = plan.petitions.get(e.petition) ?: continue
            val where = plan.districts[p.district].name
            when (e) {
                is PetitionEvent.Filed -> {}
                is PetitionEvent.Heard -> notices += UrbanNotice(plan.city, now, NoticeKind.PETITION_HEARD, "The council heard the petition for $where.", p.id.value)
                is PetitionEvent.Voted -> notices += UrbanNotice(
                    plan.city, now, if (e.approved) NoticeKind.PETITION_APPROVED else NoticeKind.PETITION_DENIED,
                    "The council voted ${e.yes} to ${e.no} on the petition for $where (${e.needed} needed): ${if (e.approved) "approved" else "denied"}.", p.id.value,
                )
                is PetitionEvent.Scandal -> notices += UrbanNotice(plan.city, now, NoticeKind.LOBBYING_SCANDAL, "Lobbying for the $where petition came to light and the petition was thrown out.", p.id.value)
                is PetitionEvent.Enacted -> {
                    UrbanEffects.petitionEnacted(plan, p, now)
                    notices += UrbanNotice(plan.city, now, NoticeKind.PETITION_ENACTED, "$where is now zoned ${plan.districts[p.district].zone.label.lowercase()}.", p.id.value)
                }
                is PetitionEvent.Withdrawn -> {}
            }
        }
    }

    private fun concessions(plan: CityPlan, now: Tick, notices: MutableList<UrbanNotice>) {
        for (e in plan.concessions.advanceDay(now)) {
            val c = plan.concessions.get(e.concession) ?: continue
            when (e) {
                is ConcessionEvent.Awarded -> notices += UrbanNotice(plan.city, now, NoticeKind.CONCESSION_AWARDED, "${c.name} was awarded.", c.id.value)
                is ConcessionEvent.NoBids -> notices += UrbanNotice(plan.city, now, NoticeKind.CONCESSION_NO_BIDS, "Nobody bid for ${c.name}.", c.id.value)
                is ConcessionEvent.Opened -> {
                    c.road?.let { plan.network.setToll(it, if (c.model == PaymentModel.AVAILABILITY) app.many.economytycoon.core.Money.ZERO else c.toll) }
                    c.line?.let { plan.network.line(it)?.status = LineStatus.ACTIVE }
                    notices += UrbanNotice(plan.city, now, NoticeKind.CONCESSION_OPENED, "${c.name} is open.", c.id.value)
                }
                is ConcessionEvent.HandedBack -> {
                    c.road?.let { plan.network.setToll(it, app.many.economytycoon.core.Money.ZERO) }
                    notices += UrbanNotice(plan.city, now, NoticeKind.CONCESSION_ENDED, "${c.name} has been handed back to the city.", c.id.value)
                }
            }
        }
    }
}

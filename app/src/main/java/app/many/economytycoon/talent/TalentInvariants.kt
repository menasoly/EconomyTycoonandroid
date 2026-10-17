package app.many.economytycoon.talent

import app.many.economytycoon.core.Tick

/**
 * Consistency rules the talent state must always satisfy. Used by tests after every step, and by the integration's
 * debug checks; an empty list means the state is sound.
 */
object TalentInvariants {

    fun violations(state: TalentState, taxonomy: SkillTaxonomy, cohorts: List<CohortFact>? = null, tick: Tick? = null): List<String> {
        val out = ArrayList<String>()
        checkEducation(state, out)
        checkSkills(state, taxonomy, cohorts, out)
        checkPeople(state, out)
        checkPoaching(state, tick, out)
        checkBusiness(state, out)
        checkIds(state, out)
        return out
    }

    private fun checkEducation(s: TalentState, out: MutableList<String>) {
        for (i in s.institutions.values) {
            if (i.enrolled > i.capacity) out += "institution ${i.id} has ${i.enrolled} students for ${i.capacity} places"
            if (i.qualityPpm !in 0L..Ppm.ONE) out += "institution ${i.id} quality out of range"
            if (i.capacity < 0L || i.tuition.isNegative || i.spend.isNegative) out += "institution ${i.id} has a negative figure"
            for (n in i.intakes) if (n.enrolled < 0L || n.enrolled > n.initial) out += "institution ${i.id} intake from ${n.startTick} has ${n.enrolled} of ${n.initial}"
        }
        for ((r, q) in s.qualifications) if (q.byKind.any { it < 0L }) out += "region $r holds a negative qualification count"
    }

    private fun checkSkills(s: TalentState, t: SkillTaxonomy, cohorts: List<CohortFact>?, out: MutableList<String>) {
        for ((k, d) in s.pools) {
            if (d.counts().any { it < 0L }) out += "pool $k has a negative count"
        }
        for ((k, m) in s.markets) {
            if (m.premiumPpm <= 0L || m.tightnessPpm < 0L) out += "market $k has a bad price signal"
        }
        val facts = cohorts?.associateBy { it.key }
        for (set in s.cohortSkills.values) {
            val profile = t.profile(set.key.role)
            if (profile == null) { out += "cohort ${set.key} has a skill picture but no profile"; continue }
            val heads = set.heads(profile.primary)
            for (r in profile.requirements) {
                val d = set.dist(r.skill)
                if (d == null) out += "cohort ${set.key} lacks a ${r.skill.key} picture"
                else if (d.total != heads) out += "cohort ${set.key}: ${r.skill.key} counts ${d.total} but primary skill counts $heads"
            }
            val fact = facts?.get(set.key)
            if (fact != null && fact.headcount != heads) out += "cohort ${set.key} has ${fact.headcount} people but its skill picture counts $heads"
            for ((sk, v) in set.certified) if (v !in 0L..Ppm.ONE) out += "cohort ${set.key} certified share of ${sk.key} is $v"
        }
        for (p in s.programmes.values) if (p.seats <= 0L || p.endTick < p.startTick) out += "programme ${p.id} is malformed"
    }

    private fun checkPeople(s: TalentState, out: MutableList<String>) {
        for (pos in s.positions.values) {
            val holder = pos.holder
            if (holder != null) {
                val p = s.people[holder]
                if (p == null) out += "position ${pos.id} is held by unknown person $holder"
                else {
                    if (!p.isActive) out += "position ${pos.id} is held by a retired person"
                    if (p.position != pos.id) out += "position ${pos.id} and its holder disagree"
                    if (p.company != pos.company) out += "position ${pos.id} is held by someone from another company"
                }
            }
        }
        for (p in s.people.values) {
            val pos = p.position
            if (pos != null) {
                val position = s.positions[pos]
                if (position == null || position.holder != p.id) out += "person ${p.id} claims position $pos"
            }
            if (p.loyaltyPpm !in 0L..Ppm.ONE) out += "person ${p.id} loyalty out of range"
            if (p.levels.values.any { it !in 0L..Ppm.ONE }) out += "person ${p.id} has a level out of range"
        }
        for ((pos, plan) in s.successors) {
            if (pos !in s.positions) out += "successor plan for unknown position $pos"
            for (c in plan.values) {
                if (c.person !in s.people) out += "successor ${c.person} of $pos is unknown"
                if (c.readinessPpm !in 0L..Ppm.ONE) out += "successor ${c.person} readiness out of range"
            }
        }
    }

    private fun checkPoaching(s: TalentState, tick: Tick?, out: MutableList<String>) {
        for (o in s.offers.values) {
            if (o.hunter == o.victim) out += "offer ${o.id} is to the hunter's own people"
            if (o.movedHeads < 0L || o.keptHeads < 0L || o.movedHeads + o.keptHeads > o.heads) out += "offer ${o.id} moved ${o.movedHeads} and kept ${o.keptHeads} of ${o.heads}"
            if (o.isPending && o.resolvedAt != null) out += "offer ${o.id} is pending but resolved"
            if (!o.isPending && o.resolvedAt == null) out += "offer ${o.id} is resolved without a date"
            if (tick != null && o.isPending && o.resolveAt.value + 1L < tick.value) out += "offer ${o.id} is overdue"
        }
        for (c in s.covenants.values) if (c.until < c.signedAt || c.penaltyPerHead.raw <= 0L) out += "covenant ${c.id} is malformed"
    }

    private fun checkBusiness(s: TalentState, out: MutableList<String>) {
        for (p in s.providers.values) {
            if (p.qualityPpm !in 0L..Ppm.ONE || p.reputationPpm !in 0L..Ppm.ONE) out += "provider ${p.id} quality or reputation out of range"
            for (c in p.courses.values) if (c.students > c.seats) out += "course ${c.id} has ${c.students} students for ${c.seats} places"
        }
        for (b in s.brands.values) if (b.awarenessPpm !in 0L..Ppm.ONE || b.appealPpm !in 0L..Ppm.ONE) out += "a brand is out of range"
        for (c in s.campaigns.values) if (c.hired < 0L) out += "campaign ${c.id} hired a negative number"
        for (l in s.ladders.values) if (l.rungs.size < 2) out += "ladder ${l.id} has fewer than two rungs"
    }

    private fun checkIds(s: TalentState, out: MutableList<String>) {
        fun below(kind: String, ids: Collection<Long>) { val next = s.counters.peek(kind); for (v in ids) if (v >= next) out += "$kind id $v is not below the counter $next" }
        below("institution", s.institutions.keys.map { it.value })
        below("programme", s.programmes.keys.map { it.value })
        below("person", s.people.keys.map { it.value })
        below("offer", s.offers.keys.map { it.value })
        below("covenant", s.covenants.keys.map { it.value })
        below("campaign", s.campaigns.keys.map { it.value })
        below("ladder", s.ladders.keys.map { it.value })
        below("position", s.positions.keys.map { it.value })
        below("provider", s.providers.keys.map { it.value })
        below("course", s.providers.values.flatMap { p -> p.courses.keys.map { it.value } })
    }
}

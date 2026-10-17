package app.many.economytycoon.talent

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

data class WeakCohort(val key: CohortKey, val multiplierPpm: Long, val weakSkill: SkillId, val training: Boolean)
data class CheapCohort(val key: CohortKey, val wageRatioPpm: Long, val moralePpm: Long, val retained: Boolean)
data class OpenPost(val title: String, val vacantDays: Long, val searching: Boolean)
data class UnreadyPost(val title: String, val named: Int, val bestReadinessPpm: Long)

/** What the people advisor looks at, as plain numbers, so any situation can be written down and tried. */
data class TalentAdvisorFacts(
    val company: CompanyId,
    val tick: Long,
    val shortages: List<ShortageRow> = emptyList(),
    val weakCohorts: List<WeakCohort> = emptyList(),
    val cheapCohorts: List<CheapCohort> = emptyList(),
    val vacantPosts: List<OpenPost> = emptyList(),
    val unreadyPosts: List<UnreadyPost> = emptyList(),
    val offersAgainstUs: Int = 0,
    val hasCounterPolicy: Boolean = false,
    val openings: Long = 0L,
    val headcount: Long = 0L,
    val brandAppealPpm: Long = 500_000L,
    val recruitingCampaigns: Int = 0,
    val brainDrainPpm: Long = 0L,
    val providerLapsingDays: Long? = null,
    val bondedHeadsExpiringSoon: Long = 0L,
)

fun interface TalentRule { fun check(f: TalentAdvisorFacts): List<Insight> }

object TalentRules {
    private fun one(i: Insight?): List<Insight> = if (i == null) emptyList() else listOf(i)

    val shortage = TalentRule { f ->
        val worst = f.shortages.firstOrNull() ?: return@TalentRule emptyList()
        one(Insight("talent-shortage", Topic.PEOPLE, 60 + (f.shortages.size * 3).coerceAtMost(15), "Skilled people are scarce",
            "${worst.skill.key.replace('_', ' ')} is short in region ${worst.region.value} (${worst.kind.label.lowercase()}); pay will keep rising. Train your own, run a recruiting campaign or open a college."))
    }

    val skillGap = TalentRule { f ->
        val gaps = f.weakCohorts.filter { !it.training }
        val worst = gaps.minByOrNull { it.multiplierPpm } ?: return@TalentRule emptyList()
        one(Insight("talent-skill-gap", Topic.PEOPLE, 55 + ((1_000_000L - worst.multiplierPpm) / 20_000L).toInt().coerceIn(0, 20), "Some teams lack the skills the job asks for",
            "${worst.key.role.key} staff produce ${worst.multiplierPpm / 10_000}% of what the role needs, mostly for want of ${worst.weakSkill.key.replace('_', ' ')}. A training programme repays itself when people stay."))
    }

    val flightRisk = TalentRule { f ->
        val risky = f.cheapCohorts.filter { !it.retained }
        val worst = risky.minByOrNull { it.wageRatioPpm } ?: return@TalentRule emptyList()
        one(Insight("talent-flight-risk", Topic.PEOPLE, 58, "People are easy to hire away",
            "${risky.size} team(s) are paid below the going rate and morale is low; ${worst.key.role.key} pay is at ${worst.wageRatioPpm / 10_000}% of market. Raise pay, grant a retention bonus or sign non-competes before a rival calls."))
    }

    val underOffer = TalentRule { f ->
        one(if (f.offersAgainstUs > 0) Insight("talent-under-offer", Topic.PEOPLE, 80, "A rival is approaching your people",
            "${f.offersAgainstUs} approach(es) are under way. Matching offers cost less than replacing people, if a counter-offer policy is set.") else null)
    }

    val noCounter = TalentRule { f ->
        one(if (!f.hasCounterPolicy && f.offersAgainstUs > 0) Insight("talent-no-counter", Topic.PEOPLE, 65, "You have no counter-offer policy",
            "Without one, people who are approached simply leave. Set a ceiling for how far you will match.") else null)
    }

    val vacantPost = TalentRule { f ->
        val p = f.vacantPosts.maxByOrNull { it.vacantDays } ?: return@TalentRule emptyList()
        one(Insight("talent-vacant-post", Topic.PEOPLE, 85, "A key post is empty",
            "${p.title} has been vacant ${p.vacantDays} days${if (p.searching) "; a search is under way" else ""}. Output suffers until it is filled."))
    }

    val noSuccessor = TalentRule { f ->
        val p = f.unreadyPosts.minByOrNull { it.bestReadinessPpm } ?: return@TalentRule emptyList()
        one(Insight("talent-no-successor", Topic.PEOPLE, 62, "No one is ready to take over a key post",
            "${p.title} has ${if (p.named == 0) "no named successor" else "no successor who is ready"}. Name one and start development so the post never goes empty."))
    }

    val weakBrand = TalentRule { f ->
        one(if (f.openings >= 5L && f.brandAppealPpm < 420_000L && f.recruitingCampaigns == 0)
            Insight("talent-weak-brand", Topic.PEOPLE, 48, "Few people want to work for you",
                "You have ${f.openings} open posts and a poor name as an employer. Better pay and morale lift it over time; a recruiting campaign makes you known.") else null)
    }

    val unfilled = TalentRule { f ->
        one(if (f.headcount > 0L && f.openings * 5L > f.headcount && f.recruitingCampaigns == 0)
            Insight("talent-unfilled", Topic.PEOPLE, 50, "Many posts are open",
                "${f.openings} of your posts are empty. A graduate recruiting campaign fills them with new talent.") else null)
    }

    val brainDrain = TalentRule { f ->
        one(if (f.brainDrainPpm >= 5_000L) Insight("talent-brain-drain", Topic.PEOPLE, 40, "Skilled people are leaving the country",
            "${f.brainDrainPpm / 100} in ten thousand of the working population left for better pay abroad last year. Expect shortages to deepen.") else null)
    }

    val accreditation = TalentRule { f ->
        val d = f.providerLapsingDays
        one(if (d != null && d in 0L..60L) Insight("talent-accreditation", Topic.PEOPLE, 52, "A provider's accreditation is about to lapse",
            "Renewal falls due in $d days; keep quality and results above the level's bar or students will go elsewhere.") else null)
    }

    val bonds = TalentRule { f ->
        one(if (f.bondedHeadsExpiringSoon > 0L) Insight("talent-bond-ending", Topic.PEOPLE, 35, "Trainees are about to be free to leave",
            "${f.bondedHeadsExpiringSoon} bonded trainee(s) finish their retention period within two months. A raise or a retention bonus keeps them.") else null)
    }

    val all: List<TalentRule> = listOf(shortage, skillGap, flightRisk, underOffer, noCounter, vacantPost, noSuccessor, weakBrand, unfilled, brainDrain, accreditation, bonds)
}

/** Gathers the facts for a company and runs the rules. */
class TalentAdvisor(private val system: TalentSystem, private val rules: List<TalentRule> = TalentRules.all) {

    fun facts(company: CompanyId, tick: Tick, inputs: TalentInputs, world: TalentWorld): TalentAdvisorFacts {
        val own = inputs.cohorts.filter { it.key.company == company }
        val regions = own.map { it.key.region }.toSet()
        val roles = own.map { it.key.role }.toSet()
        val shortages = system.markets.shortages().filter { row -> row.region in regions && system.taxonomy.rolesWithPrimary(row.skill).any { it.role in roles } }
        val weak = own.mapNotNull { f ->
            val prod = system.skills.productivity(f.key) ?: return@mapNotNull null
            val weakest = prod.weakest ?: return@mapNotNull null
            if (prod.multiplierPpm >= 920_000L) null
            else WeakCohort(f.key, prod.multiplierPpm, weakest.skill, system.training.activeOf(company).any { it.cohort == f.key })
        }
        val cheap = own.filter { it.wageRatioPpm < 900_000L && it.moralePpm < 500_000L }
            .map { CheapCohort(it.key, it.wageRatioPpm, it.moralePpm, system.poaching.retentionOf(it.key, tick) > 0L) }
        val vacant = system.succession.positionsOf(company).filter { it.isVacant }
            .map { OpenPost(it.title, tick.value - (it.vacantSince?.value ?: tick.value), it.searchUntil != null) }
        val unready = system.succession.positionsOf(company).filter { !it.isVacant }.mapNotNull { pos ->
            val named = system.succession.successorsOf(pos.id)
            val best = named.maxOfOrNull { it.readinessPpm } ?: 0L
            if (best >= system.params.succession.readyPpm) null else UnreadyPost(pos.title, named.size, best)
        }
        val against = system.poaching.offers().count { it.victim == company && it.isPending }
        val country: CountryId? = regions.minOrNull()?.let { world.countryOf(it) }
        val lapsing = system.providers.providersOf(company).filter { it.accreditation != Accreditation.NONE }.minOfOrNull { it.accreditedUntil.value - tick.value }
        val expiring = system.training.all().filter { p -> p.company == company && p.bondedHeads > 0L && p.bondUntil?.let { it.value - tick.value in 0L..60L } == true }.sumOf { it.bondedHeads }
        val appeal = regions.minOrNull()?.let { system.brand.appeal(company, it) } ?: 500_000L
        return TalentAdvisorFacts(
            company = company, tick = tick.value, shortages = shortages, weakCohorts = weak, cheapCohorts = cheap, vacantPosts = vacant, unreadyPosts = unready,
            offersAgainstUs = against, hasCounterPolicy = system.poaching.counterPolicy(company)?.enabled == true,
            openings = own.sumOf { it.openings }, headcount = own.sumOf { it.headcount }, brandAppealPpm = appeal,
            recruitingCampaigns = system.brand.activeCampaigns(company).size,
            brainDrainPpm = if (country == null) 0L else system.migration.brainDrainPpm(world, country),
            providerLapsingDays = lapsing, bondedHeadsExpiringSoon = expiring,
        )
    }

    /** Suggestions that apply to [f], most pressing first. */
    fun advise(f: TalentAdvisorFacts, limit: Int = Int.MAX_VALUE): List<Insight> =
        rules.flatMap { it.check(f) }.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code }).take(limit)

    fun advise(company: CompanyId, tick: Tick, inputs: TalentInputs, world: TalentWorld, limit: Int = Int.MAX_VALUE): List<Insight> =
        advise(facts(company, tick, inputs, world), limit)
}

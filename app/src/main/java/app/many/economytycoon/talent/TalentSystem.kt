package app.many.economytycoon.talent

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

/**
 * The talent module as the rest of the game sees it: [execute] applies one command, [advance] runs one day.
 * Services are exposed for reading; changes go through the commands.
 */
class TalentSystem(
    val state: TalentState,
    val taxonomy: SkillTaxonomy,
    private val seed: Long,
    val params: TalentParams = TalentParams(),
) {
    val markets = SkillMarketService(state, taxonomy, params.market, params.dynamics)
    val brand = BrandService(state, taxonomy, markets, seed, params.brand)
    val skills = CohortSkillService(state, taxonomy, markets, params.dynamics) { k -> brand.hireBoostPpm(k.company, k.region, k.role) }
    val education = EducationService(state, taxonomy, params.education)
    val training = TrainingService(state, taxonomy, skills, params.training)
    val migration = MigrationService(state, taxonomy, markets, skills, params.migration)
    val succession = SuccessionService(state, taxonomy, seed, params.succession)
    val poaching = PoachingService(state, taxonomy, skills, training, succession, seed, params.poaching) { c, r -> brand.brandOf(c, r) }
    val careers = CareerService(state, taxonomy, skills, params.careers)
    val providers = ProviderService(state, taxonomy, markets, training, seed, params.providers, params.education)

    fun stateHash() = state.stateHash()

    // ----- commands -----------------------------------------------------------------------------------------------------

    fun execute(cmd: TalentCommand, tick: Tick, inputs: TalentInputs, world: TalentWorld): Outcome<TalentResult> {
        val cash = cmd.actor?.let { inputs.cashOf(it) } ?: app.many.economytycoon.core.Money.ZERO
        fun <T> Outcome<T>.done(note: (T) -> String = { "" }): Outcome<TalentResult> = map { TalentResult(note = note(it)) }
        fun <T> Outcome<T>.paid(intents: (T) -> List<SettlementIntent>, note: (T) -> String = { "" }): Outcome<TalentResult> = map { TalentResult(intents(it), note = note(it)) }
        return when (cmd) {
            is TalentCommand.OpenInstitution -> education.open(cmd.actor, cmd.country, cmd.region, cmd.kind, cmd.name, cmd.capacity, cmd.tuition, cmd.spend, cmd.fields, tick, world.averageWage(cmd.region))
                .paid({ it.intents }, { it.institution.name })
            is TalentCommand.SetInstitutionTuition -> education.setTuition(cmd.institution, cmd.actor, cmd.tuition).done()
            is TalentCommand.SetInstitutionSpend -> education.setSpend(cmd.institution, cmd.actor, cmd.spend).done()
            is TalentCommand.SetInstitutionFields -> education.setFields(cmd.institution, cmd.actor, cmd.fields).done()
            is TalentCommand.ExpandInstitution -> {
                val region = education.get(cmd.institution)?.region ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown institution")
                education.expand(cmd.institution, cmd.actor, cmd.seats, tick, world.averageWage(region)).paid({ it.intents })
            }
            is TalentCommand.CloseInstitution -> education.close(cmd.institution, cmd.actor, tick).done()
            is TalentCommand.StartTraining -> {
                val fact = inputs.cohort(cmd.cohort) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown cohort")
                training.start(cmd.actor, fact, cmd.skill, cmd.kind, cmd.seats, cmd.days, cmd.clause, cash, tick).paid({ it.intents })
            }
            is TalentCommand.CancelTraining -> training.cancel(cmd.programme, cmd.actor, tick).done()
            is TalentCommand.OpenProvider -> providers.open(cmd.actor, cmd.region, cmd.name, tick, cash).paid({ it.second }, { it.first.name })
            is TalentCommand.AddCourse -> providers.addCourse(cmd.actor, cmd.provider, cmd.name, cmd.skill, cmd.kind, cmd.seats, cmd.tuition, world).done { it.name }
            is TalentCommand.SetCourseTuition -> providers.setTuition(cmd.actor, cmd.course, cmd.tuition, world).done()
            is TalentCommand.SetCourseSeats -> providers.setSeats(cmd.actor, cmd.course, cmd.seats).done()
            is TalentCommand.CloseCourse -> providers.closeCourse(cmd.actor, cmd.course).done()
            is TalentCommand.SetTeaching -> providers.setTeaching(cmd.actor, cmd.provider, cmd.levelPpm).done()
            is TalentCommand.AccreditProvider -> providers.accredit(cmd.actor, cmd.provider, cmd.level, tick, cash).paid({ it.second }, { it.first.accreditation.label })
            is TalentCommand.CloseProvider -> providers.close(cmd.actor, cmd.provider, tick).done()
            is TalentCommand.BuyCourseSeats -> {
                val fact = inputs.cohort(cmd.cohort) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown cohort")
                providers.buySeats(cmd.actor, cmd.course, fact, cmd.seats, cmd.clause, cash, tick).paid({ it.intents })
            }
            is TalentCommand.SubmitOffer -> poaching.submit(cmd.actor, cmd.destination, cmd.target, cmd.offeredWage, cmd.signingBonus, cmd.stance, tick, inputs)
                .map { TalentResult(it.intents, events = listOf(TalentEvent.PoachOffered(it.offer.id, it.offer.hunter, it.offer.victim, tick))) }
            is TalentCommand.SetCounterPolicy -> poaching.setCounterPolicy(cmd.actor, cmd.enabled, cmd.maxMatchPpm).done()
            is TalentCommand.SignNonCompete -> poaching.signNonCompete(cmd.actor, cmd.cohort, cmd.person, cmd.months, cmd.penaltyPerHead, tick, inputs)
                .map { TalentResult(events = listOf(TalentEvent.CovenantSigned(it.id, it.company, tick))) }
            is TalentCommand.ReleaseNonCompete -> poaching.releaseCovenant(cmd.covenant, cmd.actor).done()
            is TalentCommand.GrantRetention -> poaching.grantRetention(cmd.actor, cmd.cohort, cmd.strengthPpm, cmd.days, tick, inputs).paid({ it })
            is TalentCommand.LaunchCampaign -> brand.launch(cmd.actor, cmd.region, cmd.role, cmd.budget, cmd.days, tick, inputs)
                .map { TalentResult(it.second, events = listOf(TalentEvent.CampaignStarted(it.first.id, it.first.company, tick))) }
            is TalentCommand.CancelCampaign -> brand.cancel(cmd.campaign, cmd.actor).done()
            is TalentCommand.CreateLadder -> careers.create(cmd.actor, cmd.name, cmd.rungs).done { it.name }
            is TalentCommand.SetLadderEnabled -> careers.setEnabled(cmd.ladder, cmd.actor, cmd.enabled).done()
            is TalentCommand.RemoveLadder -> careers.remove(cmd.ladder, cmd.actor).done()
            is TalentCommand.HirePerson -> succession.addPerson(cmd.actor, cmd.name, cmd.role, cmd.region, cmd.wage, cmd.age, tick).done { it.name }
            is TalentCommand.OpenPosition -> succession.openPosition(cmd.actor, cmd.role, cmd.region, cmd.title).done { it.title }
            is TalentCommand.AppointPerson -> succession.appoint(cmd.position, cmd.person, tick).done()
            is TalentCommand.NameSuccessor -> succession.nameSuccessor(cmd.position, cmd.person, cmd.development, tick).done()
            is TalentCommand.RemoveSuccessor -> succession.removeSuccessor(cmd.position, cmd.person).done()
            is TalentCommand.SetDevelopment -> succession.setDevelopment(cmd.position, cmd.person, cmd.development).done()
            is TalentCommand.SetImmigrationPolicy -> {
                if (world.countries().none { it == cmd.country }) Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown country")
                else if (cmd.opennessPpm !in 0L..Ppm.ONE || cmd.quotaPpm !in 0L..Ppm.ONE) Outcome.reject(Rules.INVALID_ARGUMENT, "openness and quota are shares")
                else { migration.setPolicy(cmd.country, cmd.opennessPpm, cmd.quotaPpm, cmd.skilledOnly); Outcome.ok(TalentResult.DONE) }
            }
        }
    }

    // ----- a day -------------------------------------------------------------------------------------------------------------

    /**
     * Runs one day. The order matters: the cohorts are brought in line with the workforce first, so every later step
     * sees today's people; education and providers refill the pools before the markets are priced; training and
     * migration change who is where; poaching, careers and succession then act on the result.
     */
    fun advance(tick: Tick, world: TalentWorld, inputs: TalentInputs): TalentReport {
        val intents = ArrayList<SettlementIntent>(); val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()

        val active = inputs.cohorts.map { it.key }.toSet()
        for (f in inputs.cohorts) {
            val change = skills.reconcile(f, world)
            if (change.left > 0L) {
                val r = training.onDepartures(f.key, change.left, f.headcount + change.left, tick)
                intents += r.intents; effects += r.effects; events += r.events
            }
        }
        for (k in skills.all().map { it.key }) skills.retire(k, active)

        val edu = education.advance(tick, world) { region, skill -> markets.premium(region, skill) }
        for (g in edu.graduations) markets.deposit(g.region, g.skill, SkillDistribution.around(g.levelPpm, params.education.graduateSpreadPpm, g.count))
        intents += edu.intents; events += edu.events

        val prov = providers.advance(tick, world)
        intents += prov.intents; events += prov.events

        if (tick.value > 0L && tick.value % 30L == 0L) {
            val openings = java.util.TreeMap<PoolKey, Long>(); val employed = java.util.TreeMap<PoolKey, Long>()
            for (f in inputs.cohorts) {
                val primary = taxonomy.profile(f.key.role)?.primary ?: continue
                val k = PoolKey(f.key.region, primary)
                openings[k] = (openings[k] ?: 0L) + f.openings
                employed[k] = (employed[k] ?: 0L) + f.headcount
            }
            markets.monthly(tick, openings, employed, events)
            for (f in inputs.cohorts) skills.learnMonthly(f)
        }

        val tr = training.advance(tick, inputs)
        intents += tr.intents; effects += tr.effects; events += tr.events

        val mig = migration.review(tick, world, inputs)
        effects += mig.effects; events += mig.events
        for (e in mig.effects) if (e is WorkforceEffect.Emigrate) {
            val before = inputs.cohort(e.cohort)?.headcount ?: continue
            val r = training.onDepartures(e.cohort, e.count, before, tick)
            intents += r.intents; effects += r.effects; events += r.events
        }

        val po = poaching.advance(tick, world, inputs)
        intents += po.intents; effects += po.effects; events += po.events

        val ca = careers.advance(tick, world, inputs)
        effects += ca.effects; events += ca.events

        if (tick.value > 0L && tick.value % 30L == 0L) {
            val su = succession.monthly(tick) { p -> inputs.cohort(p.cohort)?.wageRatioPpm ?: Ppm.ONE }
            intents += su.intents; effects += su.effects; events += su.events
            val br = brand.monthly(tick, inputs) { company -> trainingShareOf(company, inputs) }
            effects += br.effects; events += br.events
        }

        if (tick.value > 0L && tick.value % 360L == 0L) {
            markets.rollYear(); migration.rollYear(); succession.rollYear()
        }
        if (tick.value % 30L == 0L) { training.prune(tick); poaching.prune(tick) }
        return TalentReport(tick, events, intents, effects)
    }

    private fun trainingShareOf(company: app.many.economytycoon.core.CompanyId, inputs: TalentInputs): Long {
        val heads = inputs.cohorts.filter { it.key.company == company }.sumOf { it.headcount }
        if (heads <= 0L) return 0L
        val away = training.activeOf(company).sumOf { it.seats }
        return Ppm.clamp(Ppm.ratio(away, heads) * 4L)
    }
}

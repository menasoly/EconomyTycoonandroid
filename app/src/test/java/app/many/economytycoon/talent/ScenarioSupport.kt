package app.many.economytycoon.talent

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

/** A small world with schools, two competing firms and a workforce stand-in, for tests that run for years. */
class Scenario(val seed: Long = 7L, val world: FixedTalentWorld = FixedTalentWorld.standard()) {
    val taxonomy = TalentTestSupport.taxonomy
    val system = TalentSystem(TalentState(), taxonomy, seed)
    val workforce = MiniWorkforce()
    val events = ArrayList<TalentEvent>()
    val intents = ArrayList<app.many.economytycoon.finance.SettlementIntent>()
    var day = 0L

    val machinists1 = TalentTestSupport.key(company = 1L, role = "machinist")
    val machinists2 = TalentTestSupport.key(company = 2L, role = "machinist")
    val engineers1 = TalentTestSupport.key(company = 1L, role = "engineer")
    val engineers2 = TalentTestSupport.key(company = 2L, role = "engineer")

    init {
        for ((region, country) in listOf(1L to 1L, 2L to 1L, 3L to 2L)) {
            for (kind in InstitutionKind.values()) {
                val fields = if (kind == InstitutionKind.SCHOOL) listOf(DefaultTaxonomy.GENERAL) else taxonomy.skillsOf(kind.pathway).map { it.id }
                system.education.registerPublic(CountryId(country), RegionId(region), kind, "${kind.label} $region", 4_000L, Money.units(2_000), fields, Tick(0))
            }
        }
        workforce.add(machinists1, 120, target = 140, wage = Money.units(95))
            .add(machinists2, 100, target = 100, wage = Money.units(90))
            .add(engineers1, 30, target = 36, wage = Money.units(150))
            .add(engineers2, 24, target = 24, wage = Money.units(160))
            .fund(1, 5_000_000).fund(2, 5_000_000).fund(3, 5_000_000)
        for (f in workforce.inputs(0).cohorts) system.skills.reconcile(f, world)
    }

    fun inputs(): TalentInputs = workforce.inputs(day)

    fun step(): TalentReport {
        val report = system.advance(Tick(day), world, workforce.inputs(day))
        workforce.apply(report.effects)
        events += report.events; intents += report.intents
        workforce.day()
        day += 1L
        return report
    }

    /** Runs [days] days, checking the invariants every [checkEvery] days. Returns the problems found. */
    fun run(days: Int, checkEvery: Int = 30): List<String> {
        val problems = ArrayList<String>()
        repeat(days) {
            step()
            if (day % checkEvery == 0L) problems += TalentInvariants.violations(system.state, taxonomy, null, Tick(day)).map { "day $day: $it" }
        }
        return problems
    }

    /** Issues one of every kind of command so every partition of the state holds something. */
    fun populate() {
        fun go(cmd: TalentCommand) { system.execute(cmd, Tick(day), workforce.inputs(day), world) }
        val c1 = app.many.economytycoon.core.CompanyId(1); val c2 = app.many.economytycoon.core.CompanyId(2)
        val r1 = RegionId(1)
        system.markets.release(r1, DefaultTaxonomy.MACHINING, SkillDistribution.around(300_000L, 90_000L, 400L))
        system.markets.release(r1, DefaultTaxonomy.ENGINEERING, SkillDistribution.around(450_000L, 90_000L, 60L))
        go(TalentCommand.OpenInstitution(c1, CountryId(1), r1, InstitutionKind.VOCATIONAL, "Tech College", 300L, Money.units(900), Money.units(1_500), listOf(DefaultTaxonomy.MACHINING)))
        go(TalentCommand.StartTraining(c1, machinists1, DefaultTaxonomy.MACHINING, ProgrammeKind.APPRENTICESHIP, 10L, 180, RetentionClause(360, 500_000L)))
        go(TalentCommand.OpenProvider(c1, r1, "Night School"))
        system.providers.providersOf(c1).firstOrNull()?.let { p ->
            go(TalentCommand.AddCourse(c1, p.id, "Lathe basics", DefaultTaxonomy.MACHINING, CourseKind.BOOTCAMP, 60L, Money.units(300)))
        }
        go(TalentCommand.SetCounterPolicy(c2, true, 200_000L))
        go(TalentCommand.SignNonCompete(c2, engineers2, null, 18, Money.units(4_000)))
        go(TalentCommand.GrantRetention(c2, machinists2, 400_000L, 120))
        go(TalentCommand.SubmitOffer(c1, machinists1, PoachTarget.Group(machinists2, 15L), Money.units(135), Money.units(10), CovenantStance.RESPECT))
        go(TalentCommand.LaunchCampaign(c1, r1, RoleId("machinist"), Money.units(20_000), 120))
        go(TalentCommand.CreateLadder(c1, "Shop to design", listOf(Rung(machinists1, 0L, 0L, 0L), Rung(engineers1, 100L, 300_000L, 100_000L))))
        go(TalentCommand.OpenPosition(c1, RoleId("executive"), r1, "CEO"))
        go(TalentCommand.HirePerson(c1, "Ada", RoleId("executive"), r1, Money.units(400), 66))
        go(TalentCommand.HirePerson(c1, "Bo", RoleId("manager"), r1, Money.units(250), 40))
        val pos = system.succession.positionsOf(c1).firstOrNull()
        val ada = system.succession.peopleOf(c1).firstOrNull { it.name == "Ada" }
        val bo = system.succession.peopleOf(c1).firstOrNull { it.name == "Bo" }
        if (pos != null && ada != null && bo != null) {
            go(TalentCommand.AppointPerson(c1, pos.id, ada.id))
            go(TalentCommand.NameSuccessor(c1, pos.id, bo.id, DevelopmentKind.MENTORING))
        }
        go(TalentCommand.SetImmigrationPolicy(CountryId(2), 600_000L, 6_000L, true))
    }
}

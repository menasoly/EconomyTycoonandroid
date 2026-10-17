package app.many.economytycoon.talent

import app.many.economytycoon.content.RoleDef
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId

enum class SkillCategory(val label: String) {
    GENERAL("General"), MANUAL("Manual"), OPERATIONAL("Operations"), TECHNICAL("Technical"), ANALYTICAL("Analytical"),
    CREATIVE("Creative"), SOCIAL("Social"), MANAGERIAL("Management"), SCIENTIFIC("Science"), MEDICAL("Medical"),
}

/** How people normally gain a skill before their first job. */
enum class Pathway { EXPERIENCE, SCHOOL, VOCATIONAL, UNIVERSITY }

/** Named bands of a skill level, for people to read. Levels are ppm of mastery, 0..1,000,000. */
enum class SkillTier(val label: String, val floorPpm: Long) {
    NOVICE("Novice", 0L), APPRENTICE("Apprentice", 200_000L), COMPETENT("Competent", 400_000L),
    PROFICIENT("Proficient", 600_000L), EXPERT("Expert", 800_000L);

    companion object {
        fun of(levelPpm: Long): SkillTier = values().last { levelPpm >= it.floorPpm }
    }
}

/**
 * One skill. [decayPerYearPpm] is what an unused skill loses in a year, down to [floorPpm]; [learnPerYearPpm] is
 * what a person starting from nothing gains in a year of full practice at work, and the gain shrinks as they near
 * the ceiling their job asks of them.
 */
data class Skill(
    val id: SkillId,
    val name: String,
    val category: SkillCategory,
    val pathway: Pathway,
    val decayPerYearPpm: Long,
    val learnPerYearPpm: Long,
    val floorPpm: Long,
) {
    init {
        require(decayPerYearPpm in 0..Ppm.ONE && learnPerYearPpm in 0..Ppm.ONE && floorPpm in 0..Ppm.ONE) { "skill ${id.key} has a rate out of range" }
    }
}

/** What a role asks of one skill: the level it wants and how much that skill weighs in doing the job. */
data class SkillRequirement(val skill: SkillId, val requiredPpm: Long, val weightPpm: Long) {
    init {
        require(requiredPpm in 0..Ppm.ONE) { "required level out of range" }
        require(weightPpm in 1..Ppm.ONE) { "weight must be positive" }
    }
}

/** The talent view of a job: its band, pay and the skills it needs. The first requirement is the primary trade. */
data class RoleProfile(
    val role: RoleId,
    val name: String,
    val band: SkillBand,
    val baseWage: Money,
    val trainingTicks: Int,
    val requirements: List<SkillRequirement>,
    /** Roles that are tracked one person at a time (executives and the like). */
    val keyRole: Boolean = false,
) {
    init { require(requirements.isNotEmpty()) { "role ${role.key} needs at least one skill" } }

    val primary: SkillId get() = requirements.first().skill
    val totalWeight: Long get() = requirements.sumOf { it.weightPpm }
    fun requirementFor(skill: SkillId): SkillRequirement? = requirements.firstOrNull { it.skill == skill }

    /** The level a worker is expected to reach, averaged over the skills by weight. */
    fun standardLevelPpm(): Long = requirements.sumOf { it.requiredPpm * it.weightPpm } / totalWeight
}

/** Skills and role profiles together. Immutable once built. */
class SkillTaxonomy(skills: List<Skill>, profiles: List<RoleProfile>) {
    private val skillMap = java.util.TreeMap<SkillId, Skill>().also { m -> skills.forEach { require(m.put(it.id, it) == null) { "duplicate skill ${it.id.key}" } } }
    private val profileMap = java.util.TreeMap<RoleId, RoleProfile>().also { m -> profiles.forEach { require(m.put(it.role, it) == null) { "duplicate role ${it.role.key}" } } }

    init {
        for (p in profileMap.values) for (r in p.requirements) require(skillMap.containsKey(r.skill)) { "role ${p.role.key} needs unknown skill ${r.skill.key}" }
    }

    fun skills(): List<Skill> = skillMap.values.toList()
    fun profiles(): List<RoleProfile> = profileMap.values.toList()
    fun skill(id: SkillId): Skill = skillMap[id] ?: error("unknown skill ${id.key}")
    fun skillOrNull(id: SkillId): Skill? = skillMap[id]
    fun profile(role: RoleId): RoleProfile? = profileMap[role]
    fun hasSkill(id: SkillId) = skillMap.containsKey(id)

    fun rolesRequiring(skill: SkillId): List<RoleProfile> = profileMap.values.filter { p -> p.requirements.any { it.skill == skill } }
    fun rolesWithPrimary(skill: SkillId): List<RoleProfile> = profileMap.values.filter { it.primary == skill }
    fun skillsOf(pathway: Pathway): List<Skill> = skillMap.values.filter { it.pathway == pathway }

    /** Skills nobody can hold as a main trade: they are only ever picked up alongside another one. */
    fun secondarySkills(): List<SkillId> = skillMap.keys.filter { id -> profileMap.values.none { it.primary == id } }

    /** Adds profiles for roles content defines that this taxonomy does not know, deriving one from the skill band. */
    fun withRolesFrom(defs: List<RoleDef>): SkillTaxonomy {
        val extra = defs.filter { profileMap[it.id] == null }.map { DefaultTaxonomy.fallbackProfile(it) }
        if (extra.isEmpty()) return this
        val skillsPlus = if (skillMap.containsKey(DefaultTaxonomy.GENERAL)) skills() else skills() + DefaultTaxonomy.skills.first { it.id == DefaultTaxonomy.GENERAL }
        return SkillTaxonomy(skillsPlus, profiles() + extra)
    }
}

/** The shipped skills and the profile of each shipped role. */
object DefaultTaxonomy {
    val GENERAL = SkillId("general")
    val FARMING = SkillId("farming")
    val DRIVING = SkillId("driving")
    val MACHINING = SkillId("machining")
    val MAINTENANCE = SkillId("maintenance")
    val ELECTRICAL = SkillId("electrical")
    val MINING = SkillId("mining")
    val PROCESS_OPS = SkillId("process_ops")
    val COOKING = SkillId("cooking")
    val SAFETY = SkillId("safety")
    val SELLING = SkillId("selling")
    val CLERICAL = SkillId("clerical")
    val COMMUNICATION = SkillId("communication")
    val ENGINEERING = SkillId("engineering")
    val PROGRAMMING = SkillId("programming")
    val DESIGN = SkillId("design")
    val RESEARCH = SkillId("research")
    val MEDICINE = SkillId("medicine")
    val NURSING = SkillId("nursing")
    val AVIATION = SkillId("aviation")
    val ANALYSIS = SkillId("analysis")
    val MANAGEMENT = SkillId("management")
    val LEADERSHIP = SkillId("leadership")

    private fun s(id: SkillId, name: String, cat: SkillCategory, path: Pathway, decay: Long, learn: Long, floor: Long) =
        Skill(id, name, cat, path, decay, learn, floor)

    val skills: List<Skill> = listOf(
        s(GENERAL, "General work", SkillCategory.GENERAL, Pathway.SCHOOL, 20_000, 250_000, 250_000),
        s(FARMING, "Farming", SkillCategory.MANUAL, Pathway.VOCATIONAL, 40_000, 220_000, 120_000),
        s(DRIVING, "Driving", SkillCategory.OPERATIONAL, Pathway.VOCATIONAL, 50_000, 240_000, 150_000),
        s(MACHINING, "Machining", SkillCategory.TECHNICAL, Pathway.VOCATIONAL, 70_000, 200_000, 100_000),
        s(MAINTENANCE, "Maintenance", SkillCategory.TECHNICAL, Pathway.VOCATIONAL, 70_000, 200_000, 100_000),
        s(ELECTRICAL, "Electrical work", SkillCategory.TECHNICAL, Pathway.VOCATIONAL, 80_000, 190_000, 80_000),
        s(MINING, "Mining", SkillCategory.MANUAL, Pathway.VOCATIONAL, 50_000, 210_000, 100_000),
        s(PROCESS_OPS, "Process operation", SkillCategory.OPERATIONAL, Pathway.VOCATIONAL, 70_000, 190_000, 100_000),
        s(COOKING, "Cooking", SkillCategory.MANUAL, Pathway.VOCATIONAL, 40_000, 230_000, 120_000),
        s(SAFETY, "Safety practice", SkillCategory.OPERATIONAL, Pathway.EXPERIENCE, 90_000, 160_000, 50_000),
        s(SELLING, "Selling", SkillCategory.SOCIAL, Pathway.VOCATIONAL, 50_000, 230_000, 150_000),
        s(CLERICAL, "Clerical work", SkillCategory.ANALYTICAL, Pathway.VOCATIONAL, 60_000, 220_000, 150_000),
        s(COMMUNICATION, "Communication", SkillCategory.SOCIAL, Pathway.EXPERIENCE, 30_000, 150_000, 200_000),
        s(ENGINEERING, "Engineering", SkillCategory.TECHNICAL, Pathway.UNIVERSITY, 90_000, 170_000, 100_000),
        s(PROGRAMMING, "Programming", SkillCategory.TECHNICAL, Pathway.UNIVERSITY, 110_000, 190_000, 80_000),
        s(DESIGN, "Design", SkillCategory.CREATIVE, Pathway.UNIVERSITY, 70_000, 170_000, 100_000),
        s(RESEARCH, "Research", SkillCategory.SCIENTIFIC, Pathway.UNIVERSITY, 90_000, 150_000, 100_000),
        s(MEDICINE, "Medicine", SkillCategory.MEDICAL, Pathway.UNIVERSITY, 100_000, 130_000, 100_000),
        s(NURSING, "Nursing", SkillCategory.MEDICAL, Pathway.VOCATIONAL, 70_000, 170_000, 100_000),
        s(AVIATION, "Aviation", SkillCategory.OPERATIONAL, Pathway.UNIVERSITY, 120_000, 120_000, 50_000),
        s(ANALYSIS, "Analysis", SkillCategory.ANALYTICAL, Pathway.UNIVERSITY, 60_000, 180_000, 150_000),
        s(MANAGEMENT, "Management", SkillCategory.MANAGERIAL, Pathway.UNIVERSITY, 50_000, 160_000, 150_000),
        s(LEADERSHIP, "Leadership", SkillCategory.MANAGERIAL, Pathway.EXPERIENCE, 40_000, 120_000, 100_000),
    )

    private fun r(skill: SkillId, level: Long, weight: Long) = SkillRequirement(skill, level, weight)

    private data class Spec(val id: String, val key: Boolean, val needs: List<SkillRequirement>)

    private val specs: List<Spec> = listOf(
        Spec("laborer", false, listOf(r(GENERAL, 250_000, 1_000_000))),
        Spec("farmhand", false, listOf(r(FARMING, 250_000, 700_000), r(GENERAL, 200_000, 300_000))),
        Spec("driver", false, listOf(r(DRIVING, 450_000, 800_000), r(GENERAL, 350_000, 200_000))),
        Spec("machinist", false, listOf(r(MACHINING, 550_000, 700_000), r(GENERAL, 400_000, 300_000))),
        Spec("technician", false, listOf(r(MAINTENANCE, 600_000, 600_000), r(ELECTRICAL, 400_000, 250_000), r(ANALYSIS, 300_000, 150_000))),
        Spec("electrician", false, listOf(r(ELECTRICAL, 600_000, 800_000), r(MAINTENANCE, 350_000, 200_000))),
        Spec("miner", false, listOf(r(MINING, 500_000, 800_000), r(SAFETY, 350_000, 200_000))),
        Spec("operator", false, listOf(r(PROCESS_OPS, 550_000, 700_000), r(MAINTENANCE, 350_000, 300_000))),
        Spec("cook", false, listOf(r(COOKING, 500_000, 800_000), r(SAFETY, 300_000, 200_000))),
        Spec("cashier", false, listOf(r(GENERAL, 300_000, 700_000), r(SELLING, 250_000, 300_000))),
        Spec("sales", false, listOf(r(SELLING, 500_000, 700_000), r(COMMUNICATION, 450_000, 300_000))),
        Spec("clerk", false, listOf(r(CLERICAL, 500_000, 700_000), r(COMMUNICATION, 400_000, 300_000))),
        Spec("engineer", false, listOf(r(ENGINEERING, 700_000, 700_000), r(ANALYSIS, 500_000, 300_000))),
        Spec("programmer", false, listOf(r(PROGRAMMING, 700_000, 800_000), r(ANALYSIS, 500_000, 200_000))),
        Spec("designer", false, listOf(r(DESIGN, 650_000, 750_000), r(COMMUNICATION, 450_000, 250_000))),
        Spec("scientist", false, listOf(r(RESEARCH, 800_000, 800_000), r(ANALYSIS, 650_000, 200_000))),
        Spec("physician", false, listOf(r(MEDICINE, 850_000, 850_000), r(COMMUNICATION, 500_000, 150_000))),
        Spec("nurse", false, listOf(r(NURSING, 650_000, 800_000), r(COMMUNICATION, 450_000, 200_000))),
        Spec("pilot", false, listOf(r(AVIATION, 850_000, 850_000), r(SAFETY, 700_000, 150_000))),
        Spec("analyst", false, listOf(r(ANALYSIS, 650_000, 700_000), r(COMMUNICATION, 500_000, 300_000))),
        Spec("manager", true, listOf(r(MANAGEMENT, 650_000, 600_000), r(LEADERSHIP, 600_000, 250_000), r(COMMUNICATION, 500_000, 150_000))),
        Spec("executive", true, listOf(r(LEADERSHIP, 800_000, 550_000), r(MANAGEMENT, 750_000, 450_000))),
    )

    /** Level a role of the band asks of its main skill when content brings a role this taxonomy has not heard of. */
    fun levelForBand(band: SkillBand): Long = when (band) {
        SkillBand.UNSKILLED -> 250_000L
        SkillBand.SKILLED -> 450_000L
        SkillBand.SPECIALIST -> 650_000L
        SkillBand.EXPERT -> 800_000L
    }

    fun fallbackProfile(def: RoleDef): RoleProfile =
        RoleProfile(def.id, def.name, def.skillBand, def.baseWage, def.trainingTicks, listOf(r(GENERAL, levelForBand(def.skillBand), Ppm.ONE)))

    /** The shipped taxonomy for the given role definitions; roles without an explicit profile fall back to general work. */
    fun forRoles(defs: List<RoleDef>): SkillTaxonomy {
        val byId = defs.associateBy { it.id.key }
        val profiles = ArrayList<RoleProfile>()
        for (spec in specs) {
            val def = byId[spec.id] ?: continue
            profiles += RoleProfile(def.id, def.name, def.skillBand, def.baseWage, def.trainingTicks, spec.needs, spec.key)
        }
        val known = profiles.map { it.role }.toSet()
        for (def in defs) if (def.id !in known) profiles += fallbackProfile(def)
        return SkillTaxonomy(skills, profiles)
    }

    /** Role profiles from plain numbers, used by tests and scenarios that have no content pack at hand. */
    fun profileOf(id: String, name: String, band: SkillBand, wageUnits: Long, trainingTicks: Int): RoleProfile {
        val spec = specs.firstOrNull { it.id == id }
        val needs = spec?.needs ?: listOf(r(GENERAL, levelForBand(band), Ppm.ONE))
        return RoleProfile(RoleId(id), name, band, Money.units(wageUnits), trainingTicks, needs, spec?.key ?: false)
    }
}

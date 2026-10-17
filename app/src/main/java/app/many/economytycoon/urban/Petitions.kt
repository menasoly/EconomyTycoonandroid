package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class PetitionKind(val label: String) {
    REZONE("Rezoning"), VARIANCE("Density variance"), DOWNZONE("Downzoning")
}

enum class PetitionStatus(val label: String, val open: Boolean) {
    FILED("Filed", true), HEARING("Heard, awaiting vote", true), APPROVED("Approved", true), DENIED("Denied", false),
    WITHDRAWN("Withdrawn", false), ENACTED("In force", false), VOID("Void", false)
}

/** A request to change what may be built on a district. */
class Petition(
    val id: PetitionId,
    val district: Int,
    val kind: PetitionKind,
    /** The company that filed it, or null if the city itself proposes the change. */
    val applicant: CompanyId?,
    val fromZone: ZoneUse,
    val toZone: ZoneUse,
    /** Floor-area ratio sought, thousandths; the new zone's default if the filer asked for none. */
    val requestedFarMilli: Int,
    val requestedFloors: Int,
    val filedAt: Tick,
    val fee: Money,
) {
    var status: PetitionStatus = PetitionStatus.FILED
        internal set
    var hearingAt: Tick = filedAt
        internal set
    var voteAt: Tick = filedAt
        internal set
    var enactAt: Tick? = null
        internal set
    var resolvedAt: Tick? = null
        internal set
    var staffScore: Int = 0
        internal set
    var opinion: PublicOpinion = PublicOpinion(0, 0)
        internal set
    /** Votes in seat order after the vote; empty before. */
    var votes: List<Boolean> = emptyList()
        internal set
    var protestPetition: Boolean = false
        internal set
    var lobbied: Money = Money.ZERO
        internal set
    var note: String = ""
        internal set

    val yes: Int get() = votes.count { it }
    val no: Int get() = votes.count { !it }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(district).putInt(kind.ordinal).putLong(applicant?.value ?: -1L).putInt(fromZone.ordinal).putInt(toZone.ordinal)
        h.putInt(requestedFarMilli).putInt(requestedFloors).putLong(filedAt.value).putMoney(fee).putInt(status.ordinal)
        h.putLong(hearingAt.value).putLong(voteAt.value).putLong(enactAt?.value ?: -1L).putLong(resolvedAt?.value ?: -1L)
        h.putInt(staffScore).putInt(opinion.supportPermille).putInt(opinion.oppositionPermille).putBoolean(protestPetition).putMoney(lobbied).putString(note)
        h.putInt(votes.size)
        for (v in votes) h.putBoolean(v)
    }
}

sealed class PetitionEvent {
    abstract val petition: PetitionId
    data class Filed(override val petition: PetitionId, val district: Int, val tick: Tick) : PetitionEvent()
    data class Heard(override val petition: PetitionId, val staffScore: Int, val opinion: PublicOpinion, val tick: Tick) : PetitionEvent()
    data class Voted(override val petition: PetitionId, val yes: Int, val no: Int, val needed: Int, val approved: Boolean, val tick: Tick) : PetitionEvent()
    data class Scandal(override val petition: PetitionId, val company: CompanyId?, val lobbied: Money, val tick: Tick) : PetitionEvent()
    data class Enacted(override val petition: PetitionId, val district: Int, val tick: Tick) : PetitionEvent()
    data class Withdrawn(override val petition: PetitionId, val tick: Tick) : PetitionEvent()
}

/** Everything the book needs to know about the city to judge a petition. */
class PetitionContext(
    val geometry: CityGeometry,
    val districts: List<District>,
    val council: Council,
    val params: UrbanParams,
    val random: DeterministicRandom,
    val hooks: List<LobbyingHook> = emptyList(),
)

/** Staff review, public opinion and the voting rule for petitions. */
object PetitionRules {
    /** Floor-area ratio a petition would end with. */
    fun targetFar(p: Petition): Int = when {
        p.requestedFarMilli > 0 -> p.requestedFarMilli
        else -> p.toZone.defaultFarMilli
    }

    /** How much more (or less) intense the land would become, permille from -1000 to +1000. */
    fun intensityChange(p: Petition, district: District): Int {
        val old = district.rules.effectiveFarMilli().coerceAtLeast(1)
        val new = targetFar(p)
        val denom = maxOf(old, new, 1)
        return ((new - old) * 1000L / denom).toInt().coerceIn(-1000, 1000)
    }

    /** Mean compatibility of [zone] with the districts around [district], -500..500. */
    fun neighbourCompatibility(zone: ZoneUse, district: Int, geometry: CityGeometry, districts: List<District>): Int {
        val around = geometry.neighbours(district)
        if (around.isEmpty()) return 0
        return around.sumOf { Zoning.compatibility(zone, districts[it].zone) } / around.size
    }

    /**
     * The planners' recommendation, -1000..1000: compatibility with the neighbours counts most, then whether transport
     * can bear the extra density, then how hard the change is.
     */
    fun staffScore(p: Petition, geometry: CityGeometry, districts: List<District>): Int {
        val d = districts[p.district]
        val compat = neighbourCompatibility(p.toZone, p.district, geometry, districts)
        val capacity = if (intensityChange(p, d) > 0) (d.accessibilityPermille - 450) else 0
        val difficulty = Zoning.rezoneDifficulty(p.fromZone, p.toZone)
        return (compat * 2 + capacity - difficulty / 3).coerceIn(-1000, 1000)
    }

    /** What the neighbourhood thinks, from the fit of the new use, the extra density, the housing shortage and local anger. */
    fun opinion(p: Petition, geometry: CityGeometry, districts: List<District>, params: UrbanParams): PublicOpinion {
        val d = districts[p.district]
        val compat = neighbourCompatibility(p.toZone, p.district, geometry, districts)
        val change = intensityChange(p, d)
        val shortage = ((params.targetVacancyPpm - d.vacancyPpm).coerceAtLeast(0L) * 1000L / params.targetVacancyPpm.coerceAtLeast(1L)).toInt()
        val anger = (d.backlashPpm / 1000L).toInt()
        val opposition = 120 + maxOf(0, -compat) + maxOf(0, change) / 4 + (if (change > 0) anger / 2 else 0)
        val support = 120 + shortage * 3 / 10 + maxOf(0, compat) * 2 / 5 + (if (change < 0) anger / 3 else 0)
        return PublicOpinion(support.coerceIn(0, 1000), opposition.coerceIn(0, 1000))
    }

    /** A vote leans protest-petition when more than a fifth of the neighbourhood objects. */
    fun isProtest(o: PublicOpinion): Boolean = o.oppositionPermille >= 600 && o.oppositionPermille - o.supportPermille > 200

    /** One member's inclination: positive votes yes. */
    fun inclination(
        p: Petition, seat: CouncilSeat, council: Council, geometry: CityGeometry, districts: List<District>, now: Tick, hooks: List<LobbyingHook>,
    ): Int {
        val change = intensityChange(p, districts[p.district])
        val lean = seat.ideology * change / 1000
        val local = when {
            seat.ward == p.district -> -(p.opinion.oppositionPermille - p.opinion.supportPermille) * 6 / 10
            seat.ward in geometry.neighbours(p.district) -> -(p.opinion.oppositionPermille - p.opinion.supportPermille) / 5
            else -> 0
        }
        val staff = p.staffScore * 4 / 10
        val mood = if (change > 0) -council.moodAgainstGrowth * 3 / 10 else council.moodAgainstGrowth / 5
        val ties = if (p.applicant != null) (seat.developerTies - 400) / 8 else 0
        val pull = Lobbying.effective(seat.pressureFor(p.id), seat)
        val extra = hooks.sumOf { it.influence(p, seat, now) }
        return lean + local + staff + mood + ties + pull + extra
    }
}

/** The petitions before a city council. */
class PetitionBook {
    private val petitions = java.util.TreeMap<PetitionId, Petition>()
    private val efforts = ArrayList<LobbyEffort>()

    fun all(): List<Petition> = petitions.values.toList()
    fun get(id: PetitionId): Petition? = petitions[id]
    fun open(): List<Petition> = petitions.values.filter { it.status.open }
    fun openFor(district: Int): Petition? = petitions.values.firstOrNull { it.district == district && it.status.open }
    fun history(): List<Petition> = petitions.values.filter { !it.status.open }
    fun efforts(): List<LobbyEffort> = efforts.toList()
    fun effortsFor(id: PetitionId): List<LobbyEffort> = efforts.filter { it.petition == id }

    fun file(
        id: PetitionId, district: District, kind: PetitionKind, applicant: CompanyId?, toZone: ZoneUse, farMilli: Int, floors: Int,
        fee: Money, now: Tick, params: UrbanParams,
    ): Outcome<Petition> {
        if (open().size >= params.maxOpenPetitionsPerCity) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the council docket is full")
        if (openFor(district.index) != null) return Outcome.reject(Rules.DUPLICATE, "a petition for that district is already open")
        if (kind == PetitionKind.VARIANCE && toZone != district.zone) return Outcome.reject(Rules.INVALID_ARGUMENT, "a variance keeps the zone")
        if (kind != PetitionKind.VARIANCE && !Zoning.allowed(district.zone, toZone)) return Outcome.reject(Rules.INVALID_ARGUMENT, "that change of use is not allowed")
        if (farMilli < 0 || floors < 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "limits cannot be negative")
        if (kind == PetitionKind.VARIANCE && farMilli <= district.rules.maxFarMilli && floors <= district.rules.maxFloors) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "a variance must ask for more than the district already allows")
        }
        val denied = petitions.values.filter { it.district == district.index && it.status == PetitionStatus.DENIED }.maxByOrNull { it.resolvedAt?.value ?: 0L }
        val deniedAt = denied?.resolvedAt
        if (deniedAt != null && now.value - deniedAt.value < REAPPLY_DAYS) return Outcome.reject(Rules.RATE_LIMITED, "a petition for this district was denied recently")
        val p = Petition(
            id, district.index, kind, applicant, district.zone, toZone,
            if (farMilli > 0) farMilli else toZone.defaultFarMilli,
            if (floors > 0) floors else toZone.defaultFloors, now, fee,
        )
        p.hearingAt = Tick(now.value + params.hearingNoticeDays)
        petitions[id] = p
        return Outcome.ok(p)
    }

    fun withdraw(id: PetitionId, company: CompanyId?, now: Tick): Outcome<Petition> {
        val p = petitions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such petition")
        if (p.applicant != company) return Outcome.reject(Rules.NOT_OWNER, "only the applicant can withdraw a petition")
        if (p.status != PetitionStatus.FILED && p.status != PetitionStatus.HEARING) return Outcome.reject(Rules.WRONG_STATE, "that petition has already been decided")
        p.status = PetitionStatus.WITHDRAWN
        p.resolvedAt = now
        return Outcome.ok(p)
    }

    /**
     * Spends money to push a petition. Only a petition that has not been voted on can be lobbied, the total is capped, and
     * a targeted member must exist. Returns the effort recorded; the caller moves the money.
     */
    fun lobby(id: PetitionId, company: CompanyId, seat: Int?, amount: Money, council: Council, params: UrbanParams, now: Tick): Outcome<LobbyEffort> {
        val p = petitions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such petition")
        if (p.applicant != company) return Outcome.reject(Rules.NOT_OWNER, "only the applicant may lobby for a petition")
        if (p.status != PetitionStatus.FILED && p.status != PetitionStatus.HEARING) return Outcome.reject(Rules.WRONG_STATE, "the vote has already been taken")
        if (!amount.isPositive) return Outcome.reject(Rules.INVALID_ARGUMENT, "spend a positive amount")
        if (seat != null && seat !in council.seats.indices) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such council member")
        if (p.lobbied + amount > params.lobbyingCapPerPetition) return Outcome.reject(Rules.LIMIT_EXCEEDED, "that is more than the rules allow for one petition")
        val effort = LobbyEffort(id, company, seat, amount, Lobbying.points(amount), now)
        efforts += effort
        p.lobbied += amount
        for (s in council.seats) {
            val add = Lobbying.pointsFor(effort, s.index)
            if (add != 0) s.pressure[id] = s.pressureFor(id) + add
        }
        return Outcome.ok(effort)
    }

    /** Moves every open petition one day on. Draws from the random stream are always taken in the same order. */
    fun advanceDay(now: Tick, ctx: PetitionContext): List<PetitionEvent> {
        val out = ArrayList<PetitionEvent>()
        for (p in petitions.values) {
            when (p.status) {
                PetitionStatus.FILED -> if (now >= p.hearingAt) hear(p, now, ctx, out)
                PetitionStatus.HEARING -> if (now >= p.voteAt) vote(p, now, ctx, out)
                PetitionStatus.APPROVED -> if (now >= (p.enactAt ?: now)) {
                    p.status = PetitionStatus.ENACTED
                    p.resolvedAt = now
                    out += PetitionEvent.Enacted(p.id, p.district, now)
                    for (s in ctx.council.seats) s.pressure.remove(p.id)
                }
                else -> {}
            }
        }
        return out
    }

    private fun hear(p: Petition, now: Tick, ctx: PetitionContext, out: MutableList<PetitionEvent>) {
        p.staffScore = PetitionRules.staffScore(p, ctx.geometry, ctx.districts)
        p.opinion = PetitionRules.opinion(p, ctx.geometry, ctx.districts, ctx.params)
        p.protestPetition = PetitionRules.isProtest(p.opinion)
        p.status = PetitionStatus.HEARING
        p.voteAt = Tick(now.value + ctx.params.voteDelayDays)
        out += PetitionEvent.Heard(p.id, p.staffScore, p.opinion, now)
    }

    private fun vote(p: Petition, now: Tick, ctx: PetitionContext, out: MutableList<PetitionEvent>) {
        val council = ctx.council
        val scandalDraw = ctx.random.nextRate().ppm
        val noise = IntArray(council.seats.size) { ctx.random.nextInRange(-60L, 60L).toInt() }
        val risk = Lobbying.scandalRiskPpm(p.lobbied, ctx.params.lobbyingCapPerPetition, council.seats.sumOf { it.integrity } / council.seats.size)
        if (scandalDraw < risk) {
            p.status = PetitionStatus.DENIED
            p.resolvedAt = now
            p.note = "Lobbying scandal"
            council.moodAgainstGrowth = (council.moodAgainstGrowth + 150).coerceAtMost(1000)
            out += PetitionEvent.Scandal(p.id, p.applicant, p.lobbied, now)
            for (s in council.seats) s.pressure.remove(p.id)
            return
        }
        val votes = council.seats.map { s ->
            PetitionRules.inclination(p, s, council, ctx.geometry, ctx.districts, now, ctx.hooks) + noise[s.index] >= 0
        }
        p.votes = votes
        val needed = if (p.protestPetition) council.supermajority else council.majority
        val approved = p.yes >= needed
        if (approved) {
            p.status = PetitionStatus.APPROVED
            p.enactAt = Tick(now.value + ENACT_DELAY_DAYS)
        } else {
            p.status = PetitionStatus.DENIED
            p.resolvedAt = now
            for (s in council.seats) s.pressure.remove(p.id)
        }
        out += PetitionEvent.Voted(p.id, p.yes, p.no, needed, approved, now)
    }

    internal fun restore(p: Petition) { petitions[p.id] = p }
    internal fun restoreEfforts(list: List<LobbyEffort>) { efforts.clear(); efforts.addAll(list) }
    internal fun clear() { petitions.clear(); efforts.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(petitions.size)
        for (p in petitions.values) p.hashInto(h)
        h.putInt(efforts.size)
        for (e in efforts) h.putLong(e.petition.value).putLong(e.company.value).putInt(e.seat ?: -1).putMoney(e.amount).putInt(e.points).putLong(e.at.value)
    }

    companion object {
        const val REAPPLY_DAYS = 180L
        const val ENACT_DELAY_DAYS = 30L
    }
}

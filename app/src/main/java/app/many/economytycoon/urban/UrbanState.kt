package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.StateHash

/**
 * The urban partition of the economy: one [CityPlan] per planned city, the id counters and the random streams. Mutation
 * goes through the [UrbanService]; everything else reads.
 */
class UrbanState(val params: UrbanParams = UrbanParams(), seed: Long = 0L) : Hashable {
    val ids = UrbanIds()
    val random = RandomProvider(seed)
    private val plans = java.util.TreeMap<CityId, CityPlan>()

    fun cities(): List<CityId> = plans.keys.toList()
    fun plans(): List<CityPlan> = plans.values.toList()
    fun plan(city: CityId): CityPlan? = plans[city]
    fun requirePlan(city: CityId): CityPlan = plans[city] ?: error("no urban plan for $city")
    fun has(city: CityId): Boolean = plans.containsKey(city)
    val size: Int get() = plans.size

    /** Random stream for a topic in one city. Topics have fixed names so streams never shift. */
    fun stream(topic: String, city: CityId): DeterministicRandom = random.stream("urban.$topic", city.value)

    internal fun put(plan: CityPlan) { plans[plan.city] = plan }
    internal fun remove(city: CityId) { plans.remove(city) }
    internal fun clear() { plans.clear() }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("urban")
        hasher.putLong(params.gridSize.toLong()).putLong(params.blockSize.toLong())
        for (v in ids.snapshot()) hasher.putLong(v)
        for ((k, v) in random.snapshot()) hasher.putString(k).putLong(v)
        hasher.putInt(plans.size)
        for (p in plans.values) p.hashInto(hasher)
    }

    /** Canonical hash of the whole partition. */
    fun stateHash(): StateHash {
        val h = CanonicalHasher()
        hashInto(h)
        return h.finish()
    }
}

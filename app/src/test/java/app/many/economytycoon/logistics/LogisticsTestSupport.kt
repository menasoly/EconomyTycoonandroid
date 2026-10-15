package app.many.economytycoon.logistics

import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.Payer
import app.many.economytycoon.production.ProductionService
import app.many.economytycoon.world.WorldSize
import app.many.economytycoon.world.WorldState
import app.many.economytycoon.world.WorldTestSupport

object LogisticsTestSupport {
    val content = WorldTestSupport.content
    val alice = CompanyId(1)
    val bob = CompanyId(2)

    class Charge(val company: CompanyId, val amount: Money, val memo: String)

    class Rig(seed: Long = 21, size: WorldSize = WorldSize.MEDIUM, params: LogisticsParams = LogisticsParams()) {
        val ids: IdAllocators
        val world: WorldState
        val production: ProductionService
        val logistics: LogisticsService
        val random = RandomProvider(seed)
        val charges = ArrayList<Charge>()
        var funds: Boolean = true
        var tick = 0L

        init {
            val (w, i) = WorldTestSupport.generate(seed, size)
            ids = i; world = w
            production = ProductionService(ids, content)
            logistics = LogisticsService(ids, content, world, production, params)
            val p = Payer { c, amt, memo -> if (funds) { charges += Charge(c, amt, memo); true } else false }
            logistics.payer = p
        }

        val durable: GoodId = content.allGoods.first { it.spoilagePpm == 0L && it.storable && it.category == GoodCategory.INDUSTRIAL }.id
        val perishable: GoodId = content.allGoods.first { it.spoilagePpm >= 5_000L && it.storable }.id

        private val factoryType = content.allFacilityTypes.first { it.facilityClass == FacilityClass.FACTORY && it.storageCapacity.raw > 0 }

        fun city(i: Int): CityId = world.cities()[i].id

        fun facilityIn(owner: CompanyId, city: CityId): Facility {
            val c = world.city(city)
            val f = production.grantFacility(owner, factoryType.id, c.region, city, null, null, Tick(tick))
            f.inventory.capacity = Quantity.units(5_000_000)
            return f
        }

        fun stock(f: Facility, good: GoodId, units: Long, unitPrice: Long = 10) {
            f.inventory.add(good, Quantity.units(units), Money.units(units * unitPrice))
        }

        fun step(): LogisticsReport { tick++; world.updateTick(Tick(tick), random); return logistics.step(Tick(tick), random) }
        fun run(n: Int): List<LogisticsReport> = (1..n).map { step() }

        /** Two cities in one country that the network connects, in different regions. */
        fun domesticPair(): Pair<CityId, CityId> {
            val cities = world.cities()
            for (a in cities) for (b in cities) {
                if (a.id == b.id || a.region == b.region) continue
                if (world.countryOfCity(a.id).id != world.countryOfCity(b.id).id) continue
                if (world.network.route(a.id, b.id, 0) != null) return a.id to b.id
            }
            error("no connected domestic pair")
        }

        fun foreignPair(): Pair<CityId, CityId>? {
            val cities = world.cities()
            for (a in cities) for (b in cities) {
                if (world.countryOfCity(a.id).id == world.countryOfCity(b.id).id) continue
                if (world.network.route(a.id, b.id, 0) != null) return a.id to b.id
            }
            return null
        }

        fun hash(): String = CanonicalHasher().also { logistics.hashInto(it); production.hashInto(it) }.finish().toHex()
    }
}

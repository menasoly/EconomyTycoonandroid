package app.many.economytycoon.world

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.SegmentId
import app.many.economytycoon.core.Tick

/** Binary save format for the world. The full world is stored, not just changes, so saves do not depend on the generator. */
object WorldCodec {
    const val VERSION = 1

    fun write(world: WorldState, w: ByteWriter) {
        w.writeVarInt(VERSION)
        val p = world.params
        w.writeVarLong(p.seed); w.writeVarInt(p.size.ordinal)
        writeDemography(p.demography, w)
        w.writeMoney(p.referenceWage); w.writeVarLong(p.inequalityPpm); w.writeMoney(p.benefitPerTick); w.writeVarInt(p.monthTicks)

        w.writeVarInt(world.countries().size)
        for (c in world.countries()) {
            w.writeVarLong(c.id.value); w.writeString(c.name); w.writeVarLong(c.capital?.value ?: -1L)
            w.writeVarLong(c.corporateTaxPpm); w.writeVarLong(c.salesTaxPpm); w.writeVarLong(c.payrollTaxPpm)
            w.writeVarLong(c.minimumWagePerTick); w.writeVarLong(c.environmentalStrictnessPpm); w.writeVarLong(c.tariffPpm); w.writeVarLong(c.stabilityPpm)
        }
        w.writeVarInt(world.regions().size)
        for (r in world.regions()) {
            w.writeVarLong(r.id.value); w.writeVarLong(r.country.value); w.writeString(r.name); writePoint(r.center, w)
            w.writeVarInt(r.terrain.ordinal); w.writeVarInt(r.climate.ordinal); w.writeVarLong(r.area); w.writeVarLong(r.fertilityPpm); w.writeBoolean(r.coastal)
        }
        w.writeVarInt(world.cities().size)
        for (c in world.cities()) {
            w.writeVarLong(c.id.value); w.writeVarLong(c.region.value); w.writeString(c.name); writePoint(c.location, w)
            w.writeBoolean(c.coastal); w.writeVarLong(c.foundedPopulation); w.writeBoolean(c.hasPort); w.writeBoolean(c.hasAirport); w.writeBoolean(c.isCapital)
            w.writeVarLong(world.housingCapacity(c.id))
            val pop = world.population(c.id)
            w.writeVarLong(pop.children); w.writeVarLong(pop.working); w.writeVarLong(pop.seniors)
            w.writeVarLong(pop.lowShare); w.writeVarLong(pop.middleShare); w.writeVarLong(pop.employed); w.writeMoney(pop.averageWage)
            w.writeVarLong(pop.educationPpm); w.writeVarLong(pop.happinessPpm); w.writeVarLong(pop.rentIndexPpm); w.writeVarLong(pop.participation)
            val carries = pop.carries()
            w.writeVarInt(carries.size)
            for (x in carries) w.writeVarLong(x)
        }
        val segs = world.network.all()
        w.writeVarInt(segs.size)
        for (s in segs) {
            w.writeVarLong(s.id.value); w.writeVarLong(s.a.value); w.writeVarLong(s.b.value); w.writeVarInt(s.mode.ordinal); w.writeVarInt(s.length)
            w.writeQuantity(s.capacity); w.writeVarLong(s.qualityPpm); w.writeVarLong(s.closedUntil); w.writeQuantity(s.lifetimeLoad)
        }
        val deps = world.deposits.all()
        w.writeVarInt(deps.size)
        for (d in deps) {
            w.writeVarLong(d.id.value); w.writeVarLong(d.region.value); writePoint(d.location, w); w.writeString(d.good.key)
            w.writeVarInt(d.kind.ordinal); w.writeQuantity(d.initialReserve); w.writeVarLong(d.gradePpm); w.writeVarLong(d.regrowthPpm)
            w.writeQuantity(d.remaining); w.writeBoolean(d.surveyed); w.writeVarLong(d.owner?.value ?: -1L)
        }
        val parcels = world.land.all()
        w.writeVarInt(parcels.size)
        for (p2 in parcels) {
            w.writeVarLong(p2.id.value); w.writeVarLong(p2.region.value); w.writeVarLong(p2.city?.value ?: -1L); writePoint(p2.location, w)
            w.writeVarInt(p2.zone.ordinal); w.writeVarInt(p2.area); w.writeVarInt(p2.terrain.ordinal)
            w.writeVarLong(p2.owner?.value ?: -1L); w.writeBoolean(p2.developed); w.writeMoney(p2.lastPrice); w.writeVarInt((p2.rezonedTo?.ordinal ?: -1) + 1)
        }
        writeEvents(world.weather.active(), w)
        writeEvents(world.weather.history(), w)
    }

    fun read(r: ByteReader): WorldState {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported world save version $version")
        val seed = r.readVarLong()
        val size = WorldSize.values().getOrNull(r.readVarInt()) ?: throw ByteReader.CorruptDataException("bad world size")
        val demography = readDemography(r)
        val params = WorldParams(seed, size, demography, r.readMoney(), r.readVarLong(), r.readMoney(), r.readVarInt())
        val world = WorldState(params)

        repeat(r.readVarInt()) {
            val id = CountryId(r.readVarLong()); val name = r.readString(); val cap = r.readVarLong()
            world.addCountry(Country(id, name, if (cap < 0) null else CityId(cap), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong()))
        }
        repeat(r.readVarInt()) {
            world.addRegion(Region(
                RegionId(r.readVarLong()), CountryId(r.readVarLong()), r.readString(), readPoint(r),
                Terrain.values()[r.readVarInt()], ClimateZone.values()[r.readVarInt()], r.readVarLong(), r.readVarLong(), r.readBoolean(),
            ))
        }
        repeat(r.readVarInt()) {
            val city = City(CityId(r.readVarLong()), RegionId(r.readVarLong()), r.readString(), readPoint(r), r.readBoolean(), r.readVarLong(), r.readBoolean(), r.readBoolean(), r.readBoolean())
            val housing = r.readVarLong()
            val pop = PopulationState(city.id, 0, 0, 0)
            pop.restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            val n = r.readVarInt()
            pop.restoreCarries(LongArray(n) { r.readVarLong() })
            world.addCity(city, pop, housing)
        }
        world.finishConstruction()
        repeat(r.readVarInt()) {
            val s = Segment(SegmentId(r.readVarLong()), CityId(r.readVarLong()), CityId(r.readVarLong()), TransportMode.values()[r.readVarInt()], r.readVarInt(), Quantity.ZERO)
            s.restore(r.readQuantity(), r.readVarLong(), r.readVarLong(), r.readQuantity())
            world.network.add(s)
        }
        repeat(r.readVarInt()) {
            val d = Deposit(
                DepositId(r.readVarLong()), RegionId(r.readVarLong()), readPoint(r), GoodId(r.readString()), DepositKind.values()[r.readVarInt()],
                r.readQuantity(), r.readVarLong(), r.readVarLong(),
            )
            val owner = { v: Long -> if (v < 0) null else CompanyId(v) }
            d.restore(r.readQuantity(), r.readBoolean(), owner(r.readVarLong()))
            world.deposits.add(d)
        }
        repeat(r.readVarInt()) {
            val id = ParcelId(r.readVarLong()); val region = RegionId(r.readVarLong()); val cityRaw = r.readVarLong(); val loc = readPoint(r)
            val p = Parcel(id, region, if (cityRaw < 0) null else CityId(cityRaw), loc, ZoneType.values()[r.readVarInt()], r.readVarInt(), Terrain.values()[r.readVarInt()])
            val ownerRaw = r.readVarLong()
            val developed = r.readBoolean(); val price = r.readMoney(); val rez = r.readVarInt() - 1
            p.restore(if (ownerRaw < 0) null else CompanyId(ownerRaw), developed, price, if (rez < 0) null else ZoneType.values()[rez])
            world.land.add(p)
        }
        val active = readEvents(r)
        val past = readEvents(r)
        world.weather.restore(active, past)
        return world
    }

    private fun writePoint(p: Point, w: ByteWriter) { w.writeVarInt(p.x); w.writeVarInt(p.y) }
    private fun readPoint(r: ByteReader) = Point(r.readVarInt(), r.readVarInt())

    private fun writeDemography(d: DemographicParams, w: ByteWriter) {
        w.writeVarLong(d.birthRatePpm); w.writeVarLong(d.deathRateWorkingPpm); w.writeVarLong(d.deathRateSeniorPpm)
        w.writeVarLong(d.childToWorkingPpm); w.writeVarLong(d.workingToSeniorPpm); w.writeVarLong(d.participationPpm)
        w.writeVarLong(d.migrationPpm); w.writeVarLong(d.maxGrowthPerYearPpm)
    }

    private fun readDemography(r: ByteReader) = DemographicParams(
        r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(),
    )

    private fun writeEvents(list: List<WeatherEvent>, w: ByteWriter) {
        w.writeVarInt(list.size)
        for (e in list) { w.writeVarLong(e.region.value); w.writeVarInt(e.kind.ordinal); w.writeVarLong(e.start.value); w.writeVarInt(e.durationTicks); w.writeVarLong(e.severityPpm) }
    }

    private fun readEvents(r: ByteReader): List<WeatherEvent> {
        val out = ArrayList<WeatherEvent>()
        repeat(r.readVarInt()) { out.add(WeatherEvent(RegionId(r.readVarLong()), WeatherKind.values()[r.readVarInt()], Tick(r.readVarLong()), r.readVarInt(), r.readVarLong())) }
        return out
    }
}

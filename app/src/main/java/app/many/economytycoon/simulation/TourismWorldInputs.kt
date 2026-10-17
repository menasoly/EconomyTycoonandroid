package app.many.economytycoon.simulation

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.tourism.DestinationId
import app.many.economytycoon.tourism.OriginId
import app.many.economytycoon.tourism.Staffing
import app.many.economytycoon.tourism.StaffSupply
import app.many.economytycoon.tourism.TourismState
import app.many.economytycoon.tourism.TransportCapacity
import app.many.economytycoon.world.WorldState

/**
 * Passenger seats a day into a destination, read from the world's transport network: every open link that touches the
 * destination's city brings seats according to its mode and its state of repair, and an airport or a port adds seats of
 * its own. A closed or worn link therefore costs the town visitors. The seats of a destination are shared equally by the
 * home markets, so the sum over all of them never exceeds what the town can take. A town with no links at all still
 * takes a few visitors by road. The freight vehicles of the logistics module carry no passengers and are not counted.
 */
class WorldTransport(private val world: WorldState, private val state: TourismState) : TransportCapacity {
    override fun seatsPerDay(origin: OriginId, destination: DestinationId, tick: Tick): Long {
        val d = state.destination(destination) ?: return 0L
        val city = world.cityOrNull(d.city) ?: return 0L
        var total = 0L
        for (seg in world.network.touching(city.id)) {
            if (!seg.isOpen(tick.value)) continue
            total += mulDiv(seatsOf(seg.mode), seg.qualityPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        }
        if (city.hasAirport) total += AIRPORT_SEATS
        if (city.hasPort) total += PORT_SEATS
        if (total <= 0L) total = ROAD_SEATS / 2L
        return total / state.origins.size.coerceAtLeast(1)
    }

    companion object {
        const val ROAD_SEATS = 600L
        const val RAIL_SEATS = 1_200L
        const val SEA_SEATS = 300L
        const val AIR_SEATS = 2_000L
        const val AIRPORT_SEATS = 1_500L
        const val PORT_SEATS = 300L

        fun seatsOf(mode: TransportMode): Long = when (mode) {
            TransportMode.ROAD -> ROAD_SEATS
            TransportMode.RAIL -> RAIL_SEATS
            TransportMode.SEA -> SEA_SEATS
            TransportMode.AIR -> AIR_SEATS
        }
    }
}

/**
 * Hospitality staff a business can field, read from the labour force of the destination's city. A share of the city's
 * labour force works in hospitality at most; when the open properties together would need more people than that, every
 * business gets the same fraction of the staff it asks for. Wages are paid by the tourism module itself, because its
 * properties are not facilities with cohorts of their own.
 */
class WorldStaffSupply(private val world: WorldState, private val state: TourismState) : StaffSupply {
    override fun available(company: CompanyId?, destination: DestinationId, wanted: Int, tick: Tick): Int {
        if (wanted <= 0) return 0
        val d = state.destination(destination) ?: return wanted
        val city = world.cityOrNull(d.city) ?: return wanted
        val pool = mulDiv(world.population(city.id).laborForce, POOL_SHARE_PPM, 1_000_000L)
        val peak = state.openPropertiesIn(destination).sumOf { Staffing.needed(it.kind, it.stars, it.totalRooms, it.totalRooms).toLong() }
        if (peak <= pool) return wanted
        return mulDiv(wanted.toLong(), pool, peak.coerceAtLeast(1L)).toInt().coerceIn(0, wanted)
    }

    companion object {
        /** The most of a city's labour force that works in hotels, ppm. */
        const val POOL_SHARE_PPM = 80_000L
    }
}

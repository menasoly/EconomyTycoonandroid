package app.many.economytycoon.tourism

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What the day's computations share: the clock, the inputs, and conditions that depend on a destination and a date. */
internal class DayContext(val tick: Tick, val inputs: DayInputs, val state: TourismState, val params: TourismParams) {
    val destinations: List<Destination> = state.destinations
    val origins: List<OriginMarket> = state.origins
    val events = ArrayList<TourismEvent>()

    /** Destination drivers after the pull of their attractions. */
    val drivers: Map<DestinationId, DestinationDrivers> = destinations.associate { d -> d.id to boostedDrivers(d) }

    private fun boostedDrivers(d: Destination): DestinationDrivers {
        val atts = state.attractionsIn(d.id).filter { it.open && tick.value >= it.opensAt }
        val (culture, nature, nightlife) = AttractionModel.boosts(atts)
        return d.drivers.copy(
            culturePpm = Attractiveness.boosted(d.drivers.culturePpm, culture),
            naturePpm = Attractiveness.boosted(d.drivers.naturePpm, nature),
            nightlifePpm = Attractiveness.boosted(d.drivers.nightlifePpm, nightlife),
        )
    }

    /** Effects of the shocks aimed at destinations that are active on [date]. */
    fun shockEffects(dest: DestinationId, date: Long): ShockEffects {
        val at = Tick(date)
        val list = state.shocks.filter { it.origin == null && it.touches(dest) && it.activeAt(at) }.map { ShockEffects.of(it) }
        return ShockEffects.combine(list)
    }

    /** Multiplier on the trips a market starts on [date], from shocks aimed at the market. */
    fun originShockPpm(origin: OriginId, date: Long): Long {
        val at = Tick(date)
        var m = 1_000_000L
        for (s in state.shocks) {
            if (s.origin != origin || !s.activeAt(at)) continue
            val e = ShockEffects.of(s)
            m = mulDiv(m, e.arrivalsPpm.sum() / e.arrivalsPpm.size, 1_000_000L)
        }
        return m
    }

    /** Weather is only known a couple of days ahead; further out travellers assume it is fine. */
    fun weatherPenaltyPpm(dest: DestinationId, lead: Int): Long {
        if (lead > FORECAST_DAYS) return 0L
        val events = inputs.weather[dest].orEmpty()
        var keep = 1_000_000L
        for (e in events) {
            if (!e.activeAt(Tick(tick.value + lead))) continue
            val p = mulDiv(Attractiveness.weatherPenaltyPpm(e.kind), e.severityPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
            keep = mulDiv(keep, 1_000_000L - p, 1_000_000L)
        }
        return 1_000_000L - keep
    }

    /** Share of transport capacity left by weather on the date, ppm. */
    fun weatherTransportPpm(dest: DestinationId, lead: Int): Long {
        if (lead > FORECAST_DAYS) return 1_000_000L
        var m = 1_000_000L
        for (e in inputs.weather[dest].orEmpty()) {
            if (!e.activeAt(Tick(tick.value + lead))) continue
            m = mulDiv(m, e.scaled(e.kind.capacityPpm).coerceIn(0L, 1_000_000L), 1_000_000L)
        }
        return m
    }

    fun appealContext(dest: Destination, date: Long, lead: Int, eff: ShockEffects): AppealContext {
        val at = Tick(date)
        val festival = ShockGenerator.festivalPpm(dest.festivalMonth, dest.festivalDays, at)
        val referenceNight = Money(mulDiv(REFERENCE_NIGHT.raw, dest.priceLevelPpm, 1_000_000L))
        return AppealContext(
            season = Calendar.season(at),
            drivers = drivers.getValue(dest.id),
            eventPpm = maxOf(festival, eff.eventPpm),
            safetyDropPpm = eff.safetyDropPpm,
            weatherPenaltyPpm = weatherPenaltyPpm(dest.id, lead),
            taxPriceAddPpm = TourismTax.priceFeelPpm(dest, referenceNight),
        )
    }

    fun arrivalsMultipliers(dest: Destination, date: Long, eff: ShockEffects): LongArray =
        LongArray(VisitorSegment.COUNT) { i ->
            val s = VisitorSegment.values()[i]
            val festival = ShockGenerator.festivalArrivalsPpm(s, dest.festivalMonth, dest.festivalDays, Tick(date))
            mulDiv(eff.arrivals(s), festival, 1_000_000L)
        }

    companion object {
        const val FORECAST_DAYS = 2

        /** What a typical night costs a visitor at the reference price level: a three-star room and a day's spending. */
        val REFERENCE_NIGHT: Money = Money.units(175)
    }
}

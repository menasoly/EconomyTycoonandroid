package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** One suggestion about a property or a destination. Priority runs from 0 to 100; higher is more worth doing soon. */
data class TourismInsight(val code: String, val property: PropertyId?, val destination: DestinationId, val priority: Int, val title: String, val detail: String) {
    init { require(priority in 0..100) { "priority out of range" } }
}

/**
 * Everything the tourism rules look at about one property, as plain numbers, so a rule can be tried against any
 * situation by writing the numbers down. Ratios are parts per million.
 */
data class PropertyFacts(
    val property: PropertyId,
    val destination: DestinationId,
    val name: String,
    val policy: RatePolicy = RatePolicy.FIXED,
    val overbookPpm: Long = 0L,
    val rooms: Int = 100,
    val occupancy14Ppm: Long = 700_000L,
    val occupancy30Ppm: Long = 700_000L,
    /** Own rate against the other properties of the destination, 1,000,000 = par; 0 when there is no comparison. */
    val adrIndexPpm: Long = 1_000_000L,
    val occupancyIndexPpm: Long = 1_000_000L,
    val ratingTenths: Int = 40,
    val conditionPpm: Long = 900_000L,
    val serviceLevel7Ppm: Long = 1_000_000L,
    val walked14: Int = 0,
    val soldOutNights14: Int = 0,
    val observedNoShowAndCancelPpm: Long = 100_000L,
    /** Expected demand over the next month against the last month, 1,000,000 = same. */
    val demandOutlookPpm: Long = 1_000_000L,
    val staffNow: Int = 0,
    val staffNextMonth: Int = 0,
    val commissionShareOfRevenuePpm: Long = 0L,
    val listedPlatforms: Int = 0,
    val platformsInDestination: Int = 0,
    val allotmentSellThroughPpm: Long? = null,
    val destinationUnmetPpm: Long = 0L,
    val badShocks: Int = 0,
    val taxFeelPpm: Long = 0L,
    val stars: Int = 3,
    val maxStars: Int = 5,
) {
    companion object {
        /** Reads the facts of [p] from the state. */
        fun of(state: TourismState, p: Property, tick: Long): PropertyFacts {
            val dest = state.destination(p.destination)
            val peers = state.openPropertiesIn(p.destination)
            val index = TourismAnalytics.marketIndex(p, peers)
            val k14 = TourismAnalytics.kpis(p, 14)
            val k30 = TourismAnalytics.kpis(p, 30)
            val week = p.days.takeLast(7)
            val weekNeeded = week.sumOf { it.staffNeeded.toLong() }
            val service7 = if (weekNeeded <= 0L) 1_000_000L else mulDiv(week.sumOf { it.staffHad.toLong() }, 1_000_000L, weekNeeded)
            val mix = TourismAnalytics.marketMix(state)
            val outlook = DemandForecast.calendarRatioPpm(mix, tick, tick + 30L)
            val next = Staffing.needed(p.kind, p.stars, p.totalRooms, (p.sellableTotal().toLong() * (k30.occupancyPpm.coerceAtLeast(DemandForecast.DEFAULT_OCCUPANCY_PPM / 2) * outlook / 1_000_000L).coerceAtMost(1_000_000L) / 1_000_000L).toInt())
            val now = week.lastOrNull()?.staffNeeded ?: 0
            val allots = state.liveAllotmentsOfProperty(p.id)
            val offered = allots.sumOf { a -> a.roomsPerNight.toLong() * (minOf(tick, a.endsAt.value) - a.startsAt.value).coerceAtLeast(0L) }
            val sell = if (offered < 14L) null else mulDiv(allots.sumOf { it.lifetimeSold }.coerceAtMost(offered), 1_000_000L, offered)
            val sold = p.days.takeLast(14)
            val noShowCancel = if (sold.isEmpty()) 100_000L else {
                val gone = sold.sumOf { (it.cancelled + it.noShows).toLong() }
                val total = gone + sold.sumOf { it.roomsSold.toLong() }
                if (total <= 0L) 100_000L else mulDiv(gone, 1_000_000L, total)
            }
            val referenceNight = Money(mulDiv(DayContext.REFERENCE_NIGHT.raw, dest?.priceLevelPpm ?: 1_000_000L, 1_000_000L))
            return PropertyFacts(
                property = p.id, destination = p.destination, name = p.name, policy = p.settings.policy, overbookPpm = p.overbookPpm,
                rooms = p.totalRooms, occupancy14Ppm = k14.occupancyPpm, occupancy30Ppm = k30.occupancyPpm,
                adrIndexPpm = index?.adrPpm ?: 0L, occupancyIndexPpm = index?.occupancyPpm ?: 0L, ratingTenths = p.reviews.ratingTenths,
                conditionPpm = p.conditionPpm, serviceLevel7Ppm = service7, walked14 = k14.walked.toInt(),
                soldOutNights14 = p.days.takeLast(14).count { it.roomsAvailable > 0 && it.roomsSold >= it.roomsAvailable },
                observedNoShowAndCancelPpm = noShowCancel, demandOutlookPpm = outlook, staffNow = now, staffNextMonth = next,
                commissionShareOfRevenuePpm = k30.commissionShareOfRevenuePpm, listedPlatforms = p.listedOn.size,
                platformsInDestination = state.intermediaries.size, allotmentSellThroughPpm = sell,
                destinationUnmetPpm = dest?.unmetPpm ?: 0L,
                badShocks = state.activeShocks(tick).count { it.touches(p.destination) && it.kind.isBad && it.origin == null },
                taxFeelPpm = dest?.let { TourismTax.priceFeelPpm(it, referenceNight) } ?: 0L, stars = p.stars, maxStars = p.kind.maxStars,
            )
        }
    }
}

/** Pure rules over [PropertyFacts]. Each rule yields at most one insight; [advise] sorts them by urgency. */
class TourismAdvisor(private val rules: List<(PropertyFacts) -> TourismInsight?> = DEFAULT_RULES) {
    fun all(f: PropertyFacts): List<TourismInsight> =
        rules.mapNotNull { it(f) }.sortedWith(compareByDescending<TourismInsight> { it.priority }.thenBy { it.code })

    fun advise(f: PropertyFacts, minPriority: Int = 20): List<TourismInsight> = all(f).filter { it.priority >= minPriority }

    companion object {
        const val FULL_PPM = 920_000L
        const val EMPTY_PPM = 500_000L
        const val LOW_RATING_TENTHS = 35
        const val LOW_CONDITION_PPM = 550_000L
        const val UNDERSTAFFED_PPM = 850_000L
        const val SEASON_SWING_PPM = 250_000L
        const val COMMISSION_HEAVY_PPM = 200_000L
        const val WALKS_TO_WARN = 3
        const val DEMAND_UNMET_PPM = 200_000L
        const val TAX_FEEL_PPM = 80_000L

        private fun make(f: PropertyFacts, code: String, priority: Int, title: String, detail: String) =
            TourismInsight(code, f.property, f.destination, priority.coerceIn(0, 100), title, detail)

        private val priceTooLow = { f: PropertyFacts ->
            if (f.occupancy14Ppm < FULL_PPM || f.adrIndexPpm == 0L || f.adrIndexPpm > 1_050_000L) null else make(
                f, "price-too-low", 55 + ((f.occupancy14Ppm - FULL_PPM) / 4_000L).toInt().coerceAtMost(15),
                "${f.name} sells out below the going rate",
                "Rooms are ${f.occupancy14Ppm / 10_000L}% full and the rate is ${f.adrIndexPpm / 10_000L}% of the destination's. Raise the rack rate or turn on dynamic pricing.",
            )
        }

        private val priceTooHigh = { f: PropertyFacts ->
            if (f.occupancy14Ppm >= EMPTY_PPM || f.adrIndexPpm < 1_080_000L) null else make(
                f, "price-too-high", 50 + ((EMPTY_PPM - f.occupancy14Ppm) / 5_000L).toInt().coerceAtMost(20),
                "${f.name} is dear and half empty",
                "Occupancy is ${f.occupancy14Ppm / 10_000L}% at a rate ${(f.adrIndexPpm - 1_000_000L) / 10_000L}% above the destination's. Lower the rate or add value.",
            )
        }

        private val useDynamic = { f: PropertyFacts ->
            if (f.policy == RatePolicy.DYNAMIC || (f.occupancy14Ppm in 650_000L..880_000L)) null else make(
                f, "use-dynamic-pricing", 38,
                "${f.name} could price by demand",
                "A fixed rate leaves money on the table in busy weeks and rooms empty in slow ones. Dynamic pricing follows the booking pace.",
            )
        }

        private val seasonalStaffing = { f: PropertyFacts ->
            val up = f.demandOutlookPpm - 1_000_000L
            when {
                f.staffNow <= 0 -> null
                up >= SEASON_SWING_PPM -> make(
                    f, "seasonal-staffing", 48 + (up / 20_000L).toInt().coerceAtMost(20),
                    "Hire for the coming season at ${f.name}",
                    "Demand next month looks ${up / 10_000L}% stronger; the property needs about ${f.staffNextMonth} staff against ${f.staffNow} now.",
                )
                -up >= SEASON_SWING_PPM -> make(
                    f, "seasonal-staffing", 32,
                    "Slow season ahead at ${f.name}",
                    "Demand next month looks ${-up / 10_000L}% weaker; about ${f.staffNextMonth} staff will do against ${f.staffNow} now.",
                )
                else -> null
            }
        }

        private val understaffed = { f: PropertyFacts ->
            if (f.serviceLevel7Ppm >= UNDERSTAFFED_PPM) null else make(
                f, "understaffed", 62 + ((UNDERSTAFFED_PPM - f.serviceLevel7Ppm) / 10_000L).toInt().coerceAtMost(20),
                "${f.name} is short of staff",
                "Only ${f.serviceLevel7Ppm / 10_000L}% of the staff it needs turned up this week; guests notice and ratings suffer.",
            )
        }

        private val walkedGuests = { f: PropertyFacts ->
            if (f.walked14 < WALKS_TO_WARN || f.overbookPpm <= 0L) null else make(
                f, "walked-guests", 58 + f.walked14.coerceAtMost(20),
                "${f.name} has walked ${f.walked14} guests",
                "Overbooking is set at ${f.overbookPpm / 10_000L}% and more people turned up than there were rooms. Lower it.",
            )
        }

        private val overbookChance = { f: PropertyFacts ->
            if (f.overbookPpm > 0L || f.soldOutNights14 < 5 || f.observedNoShowAndCancelPpm < 80_000L) null else make(
                f, "overbooking-chance", 36,
                "${f.name} could overbook",
                "It sold out on ${f.soldOutNights14} of the last 14 nights while about ${f.observedNoShowAndCancelPpm / 10_000L}% of bookings fell through.",
            )
        }

        private val lowRating = { f: PropertyFacts ->
            if (f.ratingTenths >= LOW_RATING_TENTHS) null else make(
                f, "low-reviews", 50 + (LOW_RATING_TENTHS - f.ratingTenths).coerceAtMost(20),
                "${f.name} is rated ${f.ratingTenths / 10}.${f.ratingTenths % 10}",
                "Poor reviews steer guests to other properties. Staff, condition and value for money all feed the score.",
            )
        }

        private val wornBuilding = { f: PropertyFacts ->
            if (f.conditionPpm >= LOW_CONDITION_PPM) null else make(
                f, "condition-low", 55 + ((LOW_CONDITION_PPM - f.conditionPpm) / 10_000L).toInt().coerceAtMost(25),
                "${f.name} is run down",
                "The building is at ${f.conditionPpm / 10_000L}% and rooms are failing. A renovation restores it.",
            )
        }

        private val allotmentUnsold = { f: PropertyFacts ->
            val s = f.allotmentSellThroughPpm
            if (s == null || s >= 400_000L) null else make(
                f, "allotment-unsold", 44,
                "Operators sell little of the allotment at ${f.name}",
                "Only ${s / 10_000L}% of the set-aside rooms were sold. Shorten the release period or cut the allotment.",
            )
        }

        private val commissionHeavy = { f: PropertyFacts ->
            if (f.commissionShareOfRevenuePpm < COMMISSION_HEAVY_PPM) null else make(
                f, "commission-heavy", 40 + (f.commissionShareOfRevenuePpm / 20_000L).toInt().coerceAtMost(15),
                "${f.name} pays ${f.commissionShareOfRevenuePpm / 10_000L}% of room revenue in commission",
                "Platforms and agencies take a large cut. Promote direct booking or trade rate for non-refundable terms.",
            )
        }

        private val notListed = { f: PropertyFacts ->
            if (f.listedPlatforms > 0 || f.platformsInDestination == 0 || f.occupancy30Ppm >= 650_000L) null else make(
                f, "not-listed", 34,
                "${f.name} is not on any platform",
                "Occupancy is ${f.occupancy30Ppm / 10_000L}%. Listing with a platform or agency brings in guests for a commission.",
            )
        }

        private val destinationFull = { f: PropertyFacts ->
            if (f.destinationUnmetPpm < DEMAND_UNMET_PPM || f.occupancy30Ppm < 750_000L) null else make(
                f, "destination-full", 46,
                "Visitors are turned away in town",
                "${f.destinationUnmetPpm / 10_000L}% of the rooms asked for could not be sold. More rooms or a higher rate would pay.",
            )
        }

        private val badNews = { f: PropertyFacts ->
            if (f.badShocks <= 0) null else make(
                f, "shock-active", 66,
                "Trouble is hitting visitor numbers",
                "${f.badShocks} adverse event(s) are active. Expect cancellations; trim staff and watch cash until it passes.",
            )
        }

        private val heavyTax = { f: PropertyFacts ->
            if (f.taxFeelPpm < TAX_FEEL_PPM) null else make(
                f, "tax-heavy", 26,
                "The tourist levy is felt",
                "The levy adds about ${f.taxFeelPpm / 10_000L}% to the cost of a visit and makes the destination less attractive.",
            )
        }

        private val upgradeChance = { f: PropertyFacts ->
            if (f.stars >= f.maxStars || f.ratingTenths < 42 || f.occupancy30Ppm < 820_000L || f.adrIndexPpm < 1_000_000L) null else make(
                f, "upgrade-chance", 30,
                "${f.name} could move up a star",
                "It is full, well liked and already charges more than the rest of town.",
            )
        }

        val DEFAULT_RULES: List<(PropertyFacts) -> TourismInsight?> = listOf(
            priceTooLow, priceTooHigh, useDynamic, seasonalStaffing, understaffed, walkedGuests, overbookChance, lowRating, wornBuilding,
            allotmentUnsold, commissionHeavy, notListed, destinationFull, badNews, heavyTax, upgradeChance,
        )
    }
}

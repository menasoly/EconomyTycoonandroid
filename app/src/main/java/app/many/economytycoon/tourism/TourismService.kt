package app.many.economytycoon.tourism

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point

/** A purchase the caller must pay for: what was done, what it cost, and the ledger intent (null when it was free). */
data class Capex(val cost: Money, val payment: SettlementIntent?)

/** A new property and the intent that pays for its construction. */
data class PropertyOrder(val property: Property, val payment: SettlementIntent?)

/** A new attraction and the intent that pays for it. */
data class AttractionOrder(val attraction: Attraction, val payment: SettlementIntent?)

/** The result of ending an allotment early. */
data class Termination(val allotment: Allotment, val fee: Money, val payment: SettlementIntent?)

/**
 * The owner of tourism state. Every change goes through here and every expected failure comes back as a rejection.
 * Spending is not posted: methods that cost money return the [SettlementIntent] and the caller posts it, so a
 * command that cannot be paid for can be refused before it changes anything.
 */
class TourismService(val state: TourismState = TourismState(), val params: TourismParams = TourismParams()) {

    // ----- world set-up ----------------------------------------------------------------------

    fun addDestination(
        city: CityId, region: RegionId, country: CountryId, name: String, climate: ClimateZone, location: Point, coastal: Boolean,
        drivers: DestinationDrivers = DestinationDrivers(), basePriceLevelPpm: Long = 1_000_000L, festivalMonth: Int = 0,
    ): Outcome<Destination> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a destination needs a name")
        if (state.destinationOfCity(city) != null) return Outcome.reject(Rules.DUPLICATE, "that city is already a destination")
        if (basePriceLevelPpm !in 300_000L..4_000_000L) return Outcome.reject(Rules.INVALID_PRICE, "price level out of range")
        if (festivalMonth !in 0..12) return Outcome.reject(Rules.INVALID_ARGUMENT, "festival month out of range")
        val d = Destination(state.counters.destination(), name, city, region, country, climate, location, coastal)
        d.drivers = drivers
        d.setPriceLevel(basePriceLevelPpm, basePriceLevelPpm)
        d.setFestival(festivalMonth, 7)
        state.destinationMap[d.id] = d
        return Outcome.ok(d)
    }

    fun setDrivers(id: DestinationId, drivers: DestinationDrivers): Outcome<Destination> {
        val d = state.destination(id) ?: return unknown("destination")
        d.replaceDrivers(drivers)
        return Outcome.ok(d)
    }

    fun setTaxes(id: DestinationId, levyPerGuestNight: Money, levyRevenuePpm: Long, vatPpm: Long): Outcome<Destination> {
        val d = state.destination(id) ?: return unknown("destination")
        if (levyPerGuestNight.raw < 0L || levyRevenuePpm !in 0..300_000L || vatPpm !in 0..500_000L) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "tax settings out of range")
        }
        d.setTaxes(levyPerGuestNight, levyRevenuePpm, vatPpm)
        return Outcome.ok(d)
    }

    fun setFestival(id: DestinationId, month: Int, days: Int): Outcome<Destination> {
        val d = state.destination(id) ?: return unknown("destination")
        if (month !in 0..12 || days !in 1..30) return Outcome.reject(Rules.INVALID_ARGUMENT, "festival out of range")
        d.setFestival(month, days)
        return Outcome.ok(d)
    }

    fun addOrigin(
        name: String, country: CountryId?, location: Point, outboundPerDay: Long, incomePpm: Long = 1_000_000L,
        mixPpm: LongArray = longArrayOf(400_000L, 300_000L, 200_000L, 100_000L),
    ): Outcome<OriginMarket> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "an origin market needs a name")
        if (outboundPerDay < 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "outbound trips cannot be negative")
        if (mixPpm.size != VisitorSegment.COUNT || mixPpm.any { it < 0L } || mixPpm.sum() != 1_000_000L) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "the segment mix must add up to one")
        }
        val o = OriginMarket(state.counters.origin(), name, country, location)
        o.configure(outboundPerDay, incomePpm, mixPpm)
        state.originMap[o.id] = o
        return Outcome.ok(o)
    }

    fun setOutbound(id: OriginId, outboundPerDay: Long): Outcome<OriginMarket> {
        val o = state.origin(id) ?: return unknown("origin market")
        if (outboundPerDay < 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "outbound trips cannot be negative")
        o.outboundPerDay = outboundPerDay
        return Outcome.ok(o)
    }

    // ----- properties ------------------------------------------------------------------------

    /**
     * Orders a property. It is built over the kind's construction time unless [instant] is set (used when a game
     * starts with hotels already standing). [rooms] counts rooms per [RoomClass].
     */
    fun addProperty(
        destination: DestinationId, owner: CompanyId?, facility: FacilityId?, kind: PropertyKind, stars: Int, rooms: IntArray,
        tick: Tick, name: String, rackRate: Money? = null, instant: Boolean = false,
    ): Outcome<PropertyOrder> {
        val d = state.destination(destination) ?: return unknown("destination")
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a property needs a name")
        if (!kind.allowsStars(stars)) return Outcome.reject(Rules.INVALID_ARGUMENT, "${kind.label} cannot have $stars stars")
        if (rooms.size != RoomClass.COUNT || rooms.any { it < 0 }) return Outcome.reject(Rules.INVALID_QUANTITY, "room counts are wrong")
        val total = rooms.sum()
        if (total < 1) return Outcome.reject(Rules.INVALID_QUANTITY, "a property needs rooms")
        if (total > params.maxRoomsPerProperty) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many rooms in one property")
        if (owner != null && state.propertiesOf(owner).size >= params.maxPropertiesPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many properties")
        val rack = rackRate ?: Money(mulDiv(AccommodationRules.referenceRack(kind, stars).raw, d.priceLevelPpm, 1_000_000L))
        if (rack.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "the rack rate must be positive")
        val p = Property(state.counters.property(), destination, owner, facility, kind, name)
        p.stars = stars
        for (i in rooms.indices) p.rooms[i] = rooms[i]
        p.settings = RateSettings(RatePolicy.FIXED, rack)
        p.status = if (instant) PropertyStatus.OPEN else PropertyStatus.UNDER_CONSTRUCTION
        p.opensAt = if (instant) tick.value else tick.value + kind.buildDays
        p.openedAt = p.opensAt
        val cost = if (instant) Money.ZERO else AccommodationRules.buildCost(kind, stars, total)
        p.invested = cost
        state.propertyMap[p.id] = p
        return Outcome.ok(PropertyOrder(p, capex(owner, cost, "property", p.id.value, tick, "build ${kind.label.lowercase()} $name")))
    }

    fun setRates(id: PropertyId, settings: RateSettings): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        p.settings = settings
        return Outcome.ok(p)
    }

    /** Convenience for the common change: new policy and rack rate with the other settings kept. */
    fun setPricing(id: PropertyId, policy: RatePolicy, rackRate: Money): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (rackRate.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "the rack rate must be positive")
        p.settings = p.settings.copy(policy = policy, rackRate = rackRate)
        return Outcome.ok(p)
    }

    fun setRackRate(id: PropertyId, rackRate: Money): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        return setPricing(id, p.settings.policy, rackRate)
    }

    fun setPolicy(id: PropertyId, policy: RatePolicy, targetOccupancyPpm: Long? = null): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        val target = targetOccupancyPpm ?: p.settings.targetOccupancyPpm
        if (target !in 100_000L..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "target occupancy out of range")
        p.settings = p.settings.copy(policy = policy, targetOccupancyPpm = target)
        return Outcome.ok(p)
    }

    fun setPricingBand(id: PropertyId, floorPpm: Long, ceilingPpm: Long, aggressionPpm: Long, targetOccupancyPpm: Long): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (floorPpm < 1L || floorPpm > ceilingPpm) return Outcome.reject(Rules.INVALID_PRICE, "the floor must not exceed the ceiling")
        if (ceilingPpm > 4_000_000L) return Outcome.reject(Rules.INVALID_PRICE, "ceiling out of range")
        if (aggressionPpm !in 0..2_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "aggression out of range")
        if (targetOccupancyPpm !in 100_000L..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "target occupancy out of range")
        p.settings = p.settings.copy(floorPpm = floorPpm, ceilingPpm = ceilingPpm, aggressionPpm = aggressionPpm, targetOccupancyPpm = targetOccupancyPpm)
        return Outcome.ok(p)
    }

    fun setOverbooking(id: PropertyId, ppm: Long): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (ppm !in 0..params.maxOverbookPpm) return Outcome.reject(Rules.LIMIT_EXCEEDED, "overbooking out of range")
        p.overbookPpm = ppm
        return Outcome.ok(p)
    }

    fun setCancellation(id: PropertyId, policy: CancellationPolicy): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        p.cancellation = policy
        return Outcome.ok(p)
    }

    fun listWith(id: PropertyId, intermediary: IntermediaryId): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (state.intermediary(intermediary) == null) return unknown("intermediary")
        if (!p.listed.add(intermediary)) return Outcome.reject(Rules.DUPLICATE, "already listed there")
        return Outcome.ok(p)
    }

    fun delist(id: PropertyId, intermediary: IntermediaryId): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (!p.listed.remove(intermediary)) return Outcome.reject(Rules.UNKNOWN_ENTITY, "not listed there")
        return Outcome.ok(p)
    }

    /** Adds rooms of one class at once. The cost is the build cost per room, a little more for working around guests. */
    fun expandRooms(id: PropertyId, room: RoomClass, count: Int, tick: Tick): Outcome<Capex> {
        val p = state.property(id) ?: return unknown("property")
        if (count < 1 || count > params.maxRoomsPerExpansion) return Outcome.reject(Rules.INVALID_QUANTITY, "an extension adds between 1 and ${params.maxRoomsPerExpansion} rooms")
        if (p.totalRooms + count > params.maxRoomsPerProperty) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many rooms in one property")
        if (p.status == PropertyStatus.CLOSED) return Outcome.reject(Rules.WRONG_STATE, "the property is closed")
        val base = AccommodationRules.buildCost(p.kind, p.stars, count)
        val cost = Money(mulDiv(base.raw, params.expansionCostPpm, 1_000_000L))
        p.rooms[room.ordinal] += count
        p.invested += cost
        return Outcome.ok(Capex(cost, capex(p.owner, cost, "extend", p.id.value, tick, "extend ${p.name}")))
    }

    fun upgradeStars(id: PropertyId, stars: Int, tick: Tick): Outcome<Capex> {
        val p = state.property(id) ?: return unknown("property")
        if (!p.kind.allowsStars(stars)) return Outcome.reject(Rules.INVALID_ARGUMENT, "${p.kind.label} cannot have $stars stars")
        if (stars <= p.stars) return Outcome.reject(Rules.WRONG_STATE, "that is not an upgrade")
        if (p.status == PropertyStatus.CLOSED) return Outcome.reject(Rules.WRONG_STATE, "the property is closed")
        val cost = AccommodationRules.upgradeCost(p.kind, p.stars, stars, p.totalRooms)
        val scaled = mulDiv(p.settings.rackRate.raw, AccommodationRules.starFactorPpm(stars), AccommodationRules.starFactorPpm(p.stars))
        p.settings = p.settings.copy(rackRate = Money(scaled))
        p.stars = stars
        p.conditionPpm = 1_000_000L
        p.invested += cost
        return Outcome.ok(Capex(cost, capex(p.owner, cost, "upgrade", p.id.value, tick, "refurbish ${p.name}")))
    }

    /** Restores the building to new; the cost grows with the wear. */
    fun renovate(id: PropertyId, tick: Tick): Outcome<Capex> {
        val p = state.property(id) ?: return unknown("property")
        if (p.conditionPpm >= 950_000L) return Outcome.reject(Rules.WRONG_STATE, "the building is in good order")
        val full = AccommodationRules.buildCost(p.kind, p.stars, p.totalRooms)
        val cost = Money(mulDiv(mulDiv(full.raw, 1_000_000L - p.conditionPpm, 1_000_000L), params.renovationShare, 1_000_000L))
        p.conditionPpm = 1_000_000L
        p.invested += cost
        return Outcome.ok(Capex(cost, capex(p.owner, cost, "renovate", p.id.value, tick, "renovate ${p.name}")))
    }

    fun closeProperty(id: PropertyId): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (p.status == PropertyStatus.CLOSED) return Outcome.reject(Rules.WRONG_STATE, "already closed")
        if (state.liveAllotmentsOfProperty(id).isNotEmpty()) return Outcome.reject(Rules.WRONG_STATE, "end the operator contracts first")
        p.status = PropertyStatus.CLOSED
        p.bookMap.clear()
        return Outcome.ok(p)
    }

    fun reopenProperty(id: PropertyId, tick: Tick): Outcome<Property> {
        val p = state.property(id) ?: return unknown("property")
        if (p.status != PropertyStatus.CLOSED) return Outcome.reject(Rules.WRONG_STATE, "the property is not closed")
        p.status = PropertyStatus.OPEN
        p.opensAt = tick.value
        return Outcome.ok(p)
    }

    // ----- attractions -----------------------------------------------------------------------

    fun addAttraction(
        destination: DestinationId, owner: CompanyId?, facility: FacilityId?, kind: AttractionKind, name: String, tick: Tick,
        capacityPerDay: Int = kind.referenceCapacity, ticket: Money = kind.referenceTicket, instant: Boolean = false,
    ): Outcome<AttractionOrder> {
        if (state.destination(destination) == null) return unknown("destination")
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "an attraction needs a name")
        if (capacityPerDay < 1) return Outcome.reject(Rules.INVALID_QUANTITY, "an attraction needs capacity")
        if (ticket.raw < 0L) return Outcome.reject(Rules.INVALID_PRICE, "the ticket price cannot be negative")
        if (owner != null && state.attractionsOf(owner).size >= params.maxAttractionsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many attractions")
        val a = Attraction(state.counters.attraction(), destination, owner, facility, kind, name)
        a.capacityPerDay = capacityPerDay
        a.ticketPrice = ticket
        a.opensAt = if (instant) tick.value else tick.value + params.attractionBuildDays
        val cost = if (instant) Money.ZERO else Money(mulDiv(kind.buildCost.raw, capacityPerDay.toLong(), kind.referenceCapacity.toLong()))
        a.invested = cost
        state.attractionMap[a.id] = a
        return Outcome.ok(AttractionOrder(a, capex(owner, cost, "attraction", a.id.value, tick, "build ${kind.label.lowercase()} $name")))
    }

    fun setTicketPrice(id: AttractionId, price: Money): Outcome<Attraction> {
        val a = state.attraction(id) ?: return unknown("attraction")
        if (price.raw < 0L) return Outcome.reject(Rules.INVALID_PRICE, "the ticket price cannot be negative")
        a.ticketPrice = price
        return Outcome.ok(a)
    }

    fun setAttractionCapacity(id: AttractionId, capacity: Int): Outcome<Attraction> {
        val a = state.attraction(id) ?: return unknown("attraction")
        if (capacity < 1 || capacity > a.kind.referenceCapacity * 4) return Outcome.reject(Rules.INVALID_QUANTITY, "capacity out of range")
        a.capacityPerDay = capacity
        return Outcome.ok(a)
    }

    fun setAttractionOpen(id: AttractionId, open: Boolean): Outcome<Attraction> {
        val a = state.attraction(id) ?: return unknown("attraction")
        a.open = open
        return Outcome.ok(a)
    }

    // ----- distribution ----------------------------------------------------------------------

    fun addIntermediary(kind: IntermediaryKind, owner: CompanyId?, name: String, commissionPpm: Long, reachPpm: Long = 1_000_000L, listingFee: Money = Money.ZERO): Outcome<Intermediary> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "an intermediary needs a name")
        if (commissionPpm !in 0..Intermediary.MAX_COMMISSION_PPM) return Outcome.reject(Rules.INVALID_ARGUMENT, "commission out of range")
        if (reachPpm <= 0L || listingFee.raw < 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "reach and fee must be positive")
        val i = Intermediary(state.counters.intermediary(), kind, owner, name)
        i.commissionPpm = commissionPpm; i.reachPpm = reachPpm; i.listingFeePerMonth = listingFee
        state.intermediaryMap[i.id] = i
        return Outcome.ok(i)
    }

    fun setCommission(id: IntermediaryId, commissionPpm: Long): Outcome<Intermediary> {
        val i = state.intermediary(id) ?: return unknown("intermediary")
        if (commissionPpm !in 0..Intermediary.MAX_COMMISSION_PPM) return Outcome.reject(Rules.INVALID_ARGUMENT, "commission out of range")
        i.commissionPpm = commissionPpm
        return Outcome.ok(i)
    }

    fun setReach(id: IntermediaryId, reachPpm: Long): Outcome<Intermediary> {
        val i = state.intermediary(id) ?: return unknown("intermediary")
        if (reachPpm <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "reach must be positive")
        i.reachPpm = reachPpm
        return Outcome.ok(i)
    }

    // ----- operators and allotments ------------------------------------------------------------

    fun addOperator(owner: CompanyId?, name: String, strengthPpm: Long = 1_000_000L, marginPpm: Long = 180_000L): Outcome<TourOperator> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "an operator needs a name")
        if (strengthPpm <= 0L || marginPpm !in 0..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "strength or margin out of range")
        val o = TourOperator(state.counters.operator(), owner, name)
        o.strengthPpm = strengthPpm; o.marginPpm = marginPpm
        state.operatorMap[o.id] = o
        return Outcome.ok(o)
    }

    fun setOperatorMargin(id: OperatorId, marginPpm: Long): Outcome<TourOperator> {
        val o = state.operator(id) ?: return unknown("operator")
        if (marginPpm !in 0..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "margin out of range")
        o.marginPpm = marginPpm
        return Outcome.ok(o)
    }

    /**
     * Offers an allotment contract. [side] says who is offering and [proposer] must own that side. If the other side
     * has no owner (an independent operator or property) it answers at once, by [AllotmentRules.autoAccepts].
     */
    fun proposeAllotment(
        side: ContractSide, proposer: CompanyId, operator: OperatorId, property: PropertyId, room: RoomClass, roomsPerNight: Int,
        netRate: Money, startsAt: Tick, days: Int, releaseDays: Int, committedPpm: Long, tick: Tick,
    ): Outcome<Allotment> {
        val op = state.operator(operator) ?: return unknown("operator")
        val p = state.property(property) ?: return unknown("property")
        val mine = if (side == ContractSide.OPERATOR) op.owner else p.owner
        if (mine != proposer) return Outcome.reject(Rules.NOT_OWNER, "you do not own that side of the contract")
        if (p.status == PropertyStatus.CLOSED) return Outcome.reject(Rules.WRONG_STATE, "the property is closed")
        if (roomsPerNight < 1) return Outcome.reject(Rules.INVALID_QUANTITY, "an allotment needs rooms")
        if (days < 1 || days > params.maxContractDays) return Outcome.reject(Rules.INVALID_ARGUMENT, "contract length out of range")
        if (startsAt < tick) return Outcome.reject(Rules.INVALID_ARGUMENT, "the contract cannot start in the past")
        if (releaseDays !in 0 until Seasonality.HORIZON) return Outcome.reject(Rules.INVALID_ARGUMENT, "release days out of range")
        if (committedPpm !in 0..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "commitment out of range")
        val rack = p.rackRate(room)
        if (netRate.raw <= 0L || mulDiv(netRate.raw, 1_000_000L, rack.raw) < params.minNetRatePpm || netRate > rack) {
            return Outcome.reject(Rules.INVALID_PRICE, "the net rate must be between ${params.minNetRatePpm / 10_000L}% and 100% of the rack rate")
        }
        val end = Tick(startsAt.value + days)
        val setAside = state.allotmentsOfProperty(property).filter { it.room == room && (it.status == AllotmentStatus.ACTIVE || it.status == AllotmentStatus.PROPOSED) && it.startsAt < end && it.endsAt > startsAt }
            .sumOf { it.roomsPerNight } + roomsPerNight
        if (setAside.toLong() * 1_000_000L > p.rooms[room.ordinal].toLong() * params.maxAllotmentSharePpm) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many rooms set aside for operators")
        }
        val a = Allotment(state.counters.allotment(), operator, property, room, roomsPerNight, netRate, startsAt, end, releaseDays, committedPpm, side, tick)
        state.allotmentMap[a.id] = a
        val other = if (side == ContractSide.OPERATOR) p.owner else op.owner
        if (other == null) {
            val share = AllotmentRules.netShareOfRack(a, rack)
            a.status = if (AllotmentRules.autoAccepts(share)) AllotmentStatus.ACTIVE else AllotmentStatus.DECLINED
        }
        return Outcome.ok(a)
    }

    fun acceptAllotment(id: AllotmentId, company: CompanyId, tick: Tick): Outcome<Allotment> {
        val a = state.allotment(id) ?: return unknown("allotment")
        if (a.status != AllotmentStatus.PROPOSED) return Outcome.reject(Rules.WRONG_STATE, "that offer is no longer open")
        if (company != counterparty(a)) return Outcome.reject(Rules.NOT_OWNER, "only the other side can accept")
        if (tick >= a.endsAt) return Outcome.reject(Rules.WRONG_STATE, "the contract period is over")
        a.status = AllotmentStatus.ACTIVE
        return Outcome.ok(a)
    }

    fun declineAllotment(id: AllotmentId, company: CompanyId): Outcome<Allotment> {
        val a = state.allotment(id) ?: return unknown("allotment")
        if (a.status != AllotmentStatus.PROPOSED) return Outcome.reject(Rules.WRONG_STATE, "that offer is no longer open")
        if (company != counterparty(a) && company != proposerOf(a)) return Outcome.reject(Rules.NOT_OWNER, "you are not a party to that contract")
        a.status = if (company == proposerOf(a)) AllotmentStatus.CANCELLED else AllotmentStatus.DECLINED
        return Outcome.ok(a)
    }

    /** Ends a live contract early. The side that ends it pays a share of the committed value that remains. */
    fun terminateAllotment(id: AllotmentId, company: CompanyId, tick: Tick): Outcome<Termination> {
        val a = state.allotment(id) ?: return unknown("allotment")
        if (a.status != AllotmentStatus.ACTIVE) return Outcome.reject(Rules.WRONG_STATE, "that contract is not running")
        val op = state.operator(a.operator) ?: return unknown("operator")
        val p = state.property(a.property) ?: return unknown("property")
        val side = when (company) {
            op.owner -> ContractSide.OPERATOR
            p.owner -> ContractSide.PROPERTY
            else -> return Outcome.reject(Rules.NOT_OWNER, "you are not a party to that contract")
        }
        val fee = AllotmentRules.terminationFee(a, tick)
        a.status = AllotmentStatus.CANCELLED
        a.endedAt = tick.value
        a.terminatedBy = side
        a.terminationFee = fee
        val receiver = if (side == ContractSide.OPERATOR) p.owner else op.owner
        val payment = if (fee.raw > 0L) SettlementIntent.simple(
            "tour:term:${a.id.value}", LedgerOwner.company(company), receiver?.let { LedgerOwner.company(it) } ?: LedgerOwner.SYSTEM, fee,
            SettlementPriority.OTHER, ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "early end of allotment ${a.id.value}",
        ) else null
        return Outcome.ok(Termination(a, fee, payment))
    }

    private fun proposerOf(a: Allotment): CompanyId? =
        if (a.proposedBy == ContractSide.OPERATOR) state.operator(a.operator)?.owner else state.property(a.property)?.owner

    private fun counterparty(a: Allotment): CompanyId? =
        if (a.proposedBy == ContractSide.OPERATOR) state.property(a.property)?.owner else state.operator(a.operator)?.owner

    // ----- marketing and shocks ----------------------------------------------------------------

    /**
     * Spends on marketing for a destination. The effect on a segment closes a share of the gap to full strength that
     * grows with the spend and saturates; [segment] null spreads the spend over all four segments.
     */
    fun launchCampaign(destination: DestinationId, company: CompanyId, segment: VisitorSegment?, spend: Money, tick: Tick): Outcome<Capex> {
        val d = state.destination(destination) ?: return unknown("destination")
        if (spend < params.minCampaignSpend) return Outcome.reject(Rules.INVALID_QUANTITY, "a campaign needs at least ${params.minCampaignSpend.wholeUnits()}")
        val targets = if (segment == null) VisitorSegment.values().toList() else listOf(segment)
        val each = Money(spend.raw / targets.size)
        for (s in targets) {
            val gap = 1_000_000L - d.marketingPpm[s.ordinal]
            val reach = mulDiv(each.raw, 1_000_000L, each.raw + params.campaignHalfSaturation.raw)
            d.marketingPpm[s.ordinal] = (d.marketingPpm[s.ordinal] + mulDiv(gap, reach, 1_000_000L)).coerceIn(0L, 1_000_000L)
        }
        val intent = SettlementIntent.simple(
            "tour:mkt:${destination.value}:${tick.value}:${company.value}", LedgerOwner.company(company), LedgerOwner.SYSTEM, spend,
            SettlementPriority.SUPPLIERS, ReasonCode.FEE, Chart.MARKETING_EXPENSE, Chart.REVENUE_SERVICES, deferrable = true, memo = "tourism campaign for ${d.name}",
        )
        return Outcome.ok(Capex(spend, intent))
    }

    fun triggerShock(kind: ShockKind, destination: DestinationId?, origin: OriginId?, start: Tick, durationDays: Int, severityPpm: Long, label: String): Outcome<Shock> {
        if (destination != null && state.destination(destination) == null) return unknown("destination")
        if (origin != null && state.origin(origin) == null) return unknown("origin market")
        if (durationDays < 1 || durationDays > 720) return Outcome.reject(Rules.INVALID_ARGUMENT, "duration out of range")
        if (severityPpm !in 1..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "severity out of range")
        if (destination != null && state.activeShocks(start.value).count { it.destination == destination } >= params.maxActiveShocksPerDestination) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many shocks at that destination")
        }
        val s = Shock(state.counters.shock(), kind, destination, origin, start, durationDays, severityPpm, label.ifBlank { kind.label })
        state.shockMap[s.id] = s
        return Outcome.ok(s)
    }

    // ----- the clock ---------------------------------------------------------------------------

    /** Runs one day. Days must come in order; a repeated or earlier day is refused. */
    fun advanceDay(tick: Tick, inputs: DayInputs, random: RandomProvider): DayReport {
        require(tick.value > state.lastTick) { "tourism already ran day ${tick.value}" }
        val report = TourismDayRunner(state, params).run(tick, inputs, random)
        state.lastTick = tick.value
        return report
    }

    /** Bills a month. Call once, on the last day of the month, after [advanceDay]. */
    fun settleMonth(tick: Tick): SettlementRun {
        val month = tick.value / app.many.economytycoon.core.Calendar.DAYS_PER_MONTH
        require(month > state.lastSettledMonth) { "tourism already settled month $month" }
        val run = TourismSettlement.run(state, tick)
        state.lastSettledMonth = month
        return run
    }

    // ----- helpers -----------------------------------------------------------------------------

    private fun capex(owner: CompanyId?, cost: Money, what: String, id: Long, tick: Tick, memo: String): SettlementIntent? {
        if (owner == null || cost.raw <= 0L) return null
        return SettlementIntent.simple(
            "tour:capex:$what:$id:${tick.value}", LedgerOwner.company(owner), LedgerOwner.SYSTEM, cost, SettlementPriority.SUPPLIERS,
            ReasonCode.PURCHASE, Chart.FIXED_ASSETS, Chart.REVENUE_SALES, deferrable = false, memo = memo,
        )
    }

    private fun unknown(what: String): Outcome<Nothing> = Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown $what")
}

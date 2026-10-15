package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.property.Lease
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.world.Parcel

enum class ParcelState { FREE, LISTED, OFFERED, LET, BUILT }

data class ParcelCard(
    val id: Long,
    val title: String,
    val place: String,
    val value: String,
    val paid: String,
    val state: ParcelState,
    val stateLabel: String,
    val canSell: Boolean,
    val canLet: Boolean,
    val appraisal: Money,
    val fairRent: Money,
)

data class ListingCard(val parcel: Long, val title: String, val place: String, val ask: String, val appraisal: String, val verdict: String, val tone: Tone, val expires: String, val mine: Boolean)

data class LeaseCard(
    val parcel: Long,
    val title: String,
    val tenant: String,
    val rent: String,
    val ends: String,
    val earned: String,
    val trouble: String?,
    val buyout: String,
)

data class PropertyModel(
    val tiles: List<Tile>,
    val parcels: List<ParcelCard>,
    val market: List<ListingCard>,
    val leases: List<LeaseCard>,
    val terms: List<Int>,
    val note: String?,
)

object PropertyPresenter {
    val TENANTS = listOf(
        "A bakery chain", "A freight broker", "A print shop", "A garden centre", "A used-car dealer", "A storage firm",
        "A building merchant", "A market trader", "A film crew", "A scrap yard", "A fairground operator", "A catering company",
    )

    fun zoneLabel(p: Parcel): String = Formatting.title(p.effectiveZone.name.lowercase())

    private fun placeOf(session: GameSession, p: Parcel): String {
        val w = session.economy.world
        return p.city?.let { w.cityOrNull(it)?.name } ?: w.region(p.region).name
    }

    private fun titleOf(p: Parcel) = "${zoneLabel(p)} plot, ${Formatting.grouped(p.area.toLong())} units"

    fun verdict(ask: Money, appraisal: Money): Pair<String, Tone> {
        if (appraisal.raw <= 0L) return "No appraisal" to Tone.NEUTRAL
        val ppm = (ask.raw * 1_000_000L / appraisal.raw) - 1_000_000L
        return when {
            ppm <= -30_000L -> "${Formatting.percentPpm(-ppm)} under appraisal" to Tone.GOOD
            ppm >= 30_000L -> "${Formatting.percentPpm(ppm)} over appraisal" to Tone.WARN
            else -> "Near appraisal" to Tone.NEUTRAL
        }
    }

    fun leaseCard(session: GameSession, l: Lease, now: Tick): LeaseCard {
        val e = session.economy
        val p = e.world.land.get(l.parcel)
        val left = (l.endsAt.value - now.value).coerceAtLeast(0L)
        return LeaseCard(
            l.parcel.value, p?.let { titleOf(it) } ?: "Plot ${l.parcel.value}", TENANTS[l.tenant % TENANTS.size],
            session.settings.money(l.monthlyRent) + " a month", "Ends in ${Formatting.plural(left, "day")}", "Collected ${session.settings.money(l.collected)}",
            if (l.missed > 0) "${Formatting.plural(l.missed.toLong(), "payment")} missed" else null,
            "Ending early costs " + session.settings.money(e.property.earlyEndCost(l)),
        )
    }

    fun parcelCard(session: GameSession, p: Parcel): ParcelCard {
        val e = session.economy
        val value = e.propertyRunner.appraise(p)
        val built = p.developed || e.production.facilities().any { it.parcel == p.id && it.status != app.many.economytycoon.production.FacilityStatus.DEMOLISHED }
        val (state, label) = when {
            built -> ParcelState.BUILT to "Built on"
            e.property.listing(p.id) != null -> ParcelState.LISTED to "For sale"
            e.property.offer(p.id) != null -> ParcelState.OFFERED to "Looking for a tenant"
            e.property.leaseOf(p.id) != null -> ParcelState.LET to "Let"
            else -> ParcelState.FREE to "Vacant"
        }
        val free = state == ParcelState.FREE
        return ParcelCard(
            p.id.value, titleOf(p), placeOf(session, p), session.settings.money(value), "Paid " + session.settings.money(p.lastPrice),
            state, label, free, free, value, e.property.fairRent(value),
        )
    }

    fun build(session: GameSession): PropertyModel? {
        val me: CompanyId = session.selectedCompany ?: return null
        val e = session.economy
        val land = e.world.land
        val mine = land.ownedBy(me).sortedBy { it.id }
        val cards = mine.map { parcelCard(session, it) }
        val market = e.property.listings().sortedBy { it.ask.raw }.mapNotNull { l ->
            val p = land.get(l.parcel) ?: return@mapNotNull null
            val appraisal = e.propertyRunner.appraise(p)
            val (v, tone) = verdict(l.ask, appraisal)
            ListingCard(
                l.parcel.value, titleOf(p), placeOf(session, p), session.settings.money(l.ask), session.settings.money(appraisal), v, tone,
                "Listed for ${Formatting.plural((l.expiresAt.value - session.tick.value).coerceAtLeast(0L), "more day")}", l.seller == me,
            )
        }
        val leases = e.property.leasesOf(me).sortedBy { it.id }.map { leaseCard(session, it, session.tick) }
        val income = e.propertyRunner.monthlyIncome(me)
        val tiles = listOf(
            Tile("Parcels", mine.size.toString()),
            Tile("Land value", session.settings.money(e.propertyRunner.landValue(me))),
            Tile("Rent a month", session.settings.money(income), if (income.raw > 0L) Tone.GOOD else Tone.NEUTRAL),
            Tile("For sale", e.property.listingsOf(me).size.toString()),
        )
        val note = if (mine.isEmpty() && market.isEmpty()) "Buy land when building, then sell or rent out what you do not use." else null
        return PropertyModel(tiles, cards, market, leases, e.property.params.leaseTerms, note)
    }
}

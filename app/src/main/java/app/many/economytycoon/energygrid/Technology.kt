package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/**
 * Units used across the module. Power is whole kilowatts, energy whole kilowatt-hours, and one tick is one day of
 * [HOURS_PER_DAY] hourly dispatch periods, so one hour at one kilowatt is one kilowatt-hour. Prices are [Money] per
 * megawatt-hour, the way wholesale power is quoted. One unit of the production module's "electricity" good is one
 * megawatt-hour. Carbon is kilograms per megawatt-hour, which is the same number as grams per kilowatt-hour.
 */
object GridUnits {
    const val HOURS_PER_DAY = 24
    const val KW_PER_MW = 1_000L

    /** Cost of [kwh] kilowatt-hours at [pricePerMwh]. */
    fun cost(pricePerMwh: Money, kwh: Long, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(mulDiv(pricePerMwh.raw, kwh, KW_PER_MW, mode))

    /** Price per megawatt-hour that makes [kwh] kilowatt-hours cost [total]; zero when there is no energy. */
    fun priceOf(total: Money, kwh: Long): Money =
        if (kwh <= 0L) Money.ZERO else Money(mulDiv(total.raw, KW_PER_MW, kwh, RoundingMode.HALF_EVEN))

    fun mwh(kwh: Long): Long = kwh / KW_PER_MW
}

/** What a unit burns. */
enum class FuelKind(val label: String) { NONE("None"), COAL("Coal"), GAS("Gas"), URANIUM("Uranium") }

/**
 * Fuel prices per megawatt-hour of heat, and the carbon price per tonne, used to work out running costs. The
 * wiring reads them from the markets for coal, natural gas and nuclear fuel.
 */
data class FuelPrices(
    val coal: Money = Money.units(12),
    val gas: Money = Money.units(28),
    val uranium: Money = Money.units(6),
    val carbonPerTonne: Money = Money.ZERO,
) {
    fun of(fuel: FuelKind): Money = when (fuel) {
        FuelKind.NONE -> Money.ZERO
        FuelKind.COAL -> coal
        FuelKind.GAS -> gas
        FuelKind.URANIUM -> uranium
    }
}

/** Fixed characteristics of one kind of unit. Rates are parts per million, money is per kilowatt or per megawatt-hour. */
data class TechProfile(
    val fuel: FuelKind,
    /** Electricity out per unit of heat in. Zero for plants with no fuel. */
    val efficiencyPpm: Long,
    val variableOmPerMwh: Money,
    val carbonKgPerMwh: Long,
    val rampUpPpmPerHour: Long,
    val rampDownPpmPerHour: Long,
    val minStablePpm: Long,
    val capexPerKw: Money,
    val buildTicks: Int,
    val lifetimeTicks: Int,
    val forcedOutagePpmPerDay: Long,
    val forcedOutageTicks: Int,
    val maintenanceEveryTicks: Int,
    val maintenanceTicks: Int,
    /** Share of nameplate counted as firm capacity in the capacity market and in reserve planning. */
    val capacityCreditPpm: Long,
    val fixedOmPerKwYear: Money,
    /** Round trip efficiency of storage; zero for everything else. */
    val roundTripPpm: Long = 0L,
    /** Hours of full-power discharge a storage unit can hold. */
    val storageHours: Int = 0,
)

/** The kinds of unit a grid can hold. Storage is listed with generation because it is built and owned the same way. */
enum class Technology(val label: String, val dispatchable: Boolean, val profile: TechProfile) {
    COAL(
        "Coal", true,
        TechProfile(
            FuelKind.COAL, 380_000L, Money.units(4), 950L, 80_000L, 80_000L, 400_000L, Money.units(110), 120, 14_400, 6_000L, 7,
            120, 14, 880_000L, Money.units(6),
        ),
    ),
    GAS(
        "Gas", true,
        TechProfile(
            FuelKind.GAS, 500_000L, Money.units(3), 400L, 300_000L, 300_000L, 250_000L, Money.units(75), 100, 10_800, 4_000L, 4,
            90, 8, 900_000L, Money.units(4),
        ),
    ),
    NUCLEAR(
        "Nuclear", true,
        TechProfile(
            FuelKind.URANIUM, 330_000L, Money.units(8), 12L, 20_000L, 20_000L, 600_000L, Money.units(640), 300, 21_600, 3_000L, 12,
            360, 30, 930_000L, Money.units(20),
        ),
    ),
    HYDRO(
        "Hydro", true,
        TechProfile(
            FuelKind.NONE, 0L, Money.units(8), 4L, 1_000_000L, 1_000_000L, 0L, Money.units(520), 240, 28_800, 1_000L, 2,
            360, 10, 500_000L, Money.units(9),
        ),
    ),
    WIND(
        "Wind", false,
        TechProfile(
            FuelKind.NONE, 0L, Money.units(1), 11L, 1_000_000L, 1_000_000L, 0L, Money.units(190), 110, 9_000, 8_000L, 2,
            180, 6, 150_000L, Money.units(8),
        ),
    ),
    SOLAR(
        "Solar", false,
        TechProfile(
            FuelKind.NONE, 0L, Money(5_000L), 45L, 1_000_000L, 1_000_000L, 0L, Money.units(160), 100, 10_800, 1_500L, 1,
            360, 4, 100_000L, Money.units(5),
        ),
    ),
    STORAGE(
        "Storage", true,
        TechProfile(
            FuelKind.NONE, 0L, Money.units(2), 0L, 1_000_000L, 1_000_000L, 0L, Money.units(120), 60, 5_400, 2_000L, 1,
            360, 3, 600_000L, Money.units(3), roundTripPpm = 850_000L, storageHours = 4,
        ),
    );

    val isVariable: Boolean get() = !dispatchable
    val isStorage: Boolean get() = this == STORAGE
    val isThermal: Boolean get() = profile.fuel != FuelKind.NONE
    val isRenewable: Boolean get() = this == HYDRO || this == WIND || this == SOLAR

    /** Share of full power the unit can add in one hour, in kilowatts, for a unit of [capacityKw]. */
    fun rampUpKw(capacityKw: Long): Long = Rate.ofPpm(profile.rampUpPpmPerHour).let { mulDiv(capacityKw, it.ppm, Rate.ONE_PPM, RoundingMode.UP) }
    fun rampDownKw(capacityKw: Long): Long = mulDiv(capacityKw, profile.rampDownPpmPerHour, Rate.ONE_PPM, RoundingMode.UP)
    fun minStableKw(capacityKw: Long): Long = mulDiv(capacityKw, profile.minStablePpm, Rate.ONE_PPM, RoundingMode.UP)

    /** Running cost per megawatt-hour at an efficiency of [efficiencyPpm]: fuel, other variable costs and carbon. */
    fun marginalCost(prices: FuelPrices, efficiencyPpm: Long = profile.efficiencyPpm): Money {
        val fuel = if (profile.fuel == FuelKind.NONE || efficiencyPpm <= 0L) Money.ZERO
        else Money(mulDiv(prices.of(profile.fuel).raw, Rate.ONE_PPM, efficiencyPpm, RoundingMode.UP))
        val carbon = Money(mulDiv(prices.carbonPerTonne.raw, profile.carbonKgPerMwh, 1_000L, RoundingMode.HALF_EVEN))
        return fuel + profile.variableOmPerMwh + carbon
    }

    companion object {
        /** Dispatchable kinds that burn something or move water, in a stable order. */
        val FIRM: List<Technology> = listOf(NUCLEAR, COAL, GAS, HYDRO)

        /** Kinds an operator may order. */
        val BUILDABLE: List<Technology> = values().toList()
    }
}

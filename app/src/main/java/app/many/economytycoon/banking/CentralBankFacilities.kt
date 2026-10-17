package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** The central bank's rate corridor: it pays the floor on deposits, charges the ceiling on lending, and more in an emergency. */
data class Corridor(val floor: Rate, val policy: Rate, val ceiling: Rate, val emergency: Rate)

/** Collateral a bank can pledge, after haircuts. */
data class CollateralValue(val securities: Money, val loans: Money) {
    val total: Money get() = securities + loans
}

/** The central bank's standing facilities: overnight lending against collateral and emergency liquidity assistance. */
object CentralBankFacilities {
    fun corridor(policy: Rate, p: FacilityParams): Corridor {
        val p0 = policy.coerceIn(Rate.ZERO, Rate.ONE)
        val floor = Rate((p0.ppm - p.corridorHalfWidth.ppm).coerceAtLeast(0L))
        val ceiling = Rate(p0.ppm + p.corridorHalfWidth.ppm)
        return Corridor(floor, p0, ceiling, Rate(ceiling.ppm + p.emergencyPenalty.ppm))
    }

    fun collateral(s: BankSnapshot, p: FacilityParams): CollateralValue {
        val securities = s.securityOf(SecurityKind.GOVERNMENT_SHORT).times(Rate.ONE - p.haircutGovernmentShort) +
            s.securityOf(SecurityKind.GOVERNMENT_LONG).times(Rate.ONE - p.haircutGovernmentLong) +
            s.securityOf(SecurityKind.CORPORATE_BONDS).times(Rate.ONE - p.haircutCorporate)
        val loans = s.loansPerforming().times(p.loanCollateralShare).times(Rate.ONE - p.haircutLoans)
        return CollateralValue(securities, loans)
    }

    /** What the ordinary overnight facility will still lend against securities. */
    fun marginalCapacity(s: BankSnapshot, p: FacilityParams): Money =
        (collateral(s, p).securities - s.centralBankDebt).coerceAtLeast(Money.ZERO)

    /** What emergency assistance will still lend against securities and loans. */
    fun emergencyCapacity(s: BankSnapshot, p: FacilityParams): Money =
        (collateral(s, p).total - s.centralBankDebt).coerceAtLeast(Money.ZERO)

    /** Emergency assistance goes only to banks that are solvent on the numbers and still operating. */
    fun eligibleForEmergency(s: BankSnapshot, cet1Ratio: Rate, p: FacilityParams): Boolean =
        s.status.isOperating && cet1Ratio >= p.emergencyMinCet1 && s.cet1Capital.isPositive

    /** Borrows overnight against securities. Returns the amount lent. */
    fun borrowMarginal(bank: Bank, amount: Money, p: FacilityParams): Money {
        val got = Money.min(amount, marginalCapacity(bank.snapshot(), p))
        if (got.raw <= 0L) return Money.ZERO
        bank.reserves += got
        bank.centralBankMarginal += got
        return got
    }

    /** Borrows emergency liquidity. Returns the amount lent, zero when the bank is not eligible. */
    fun borrowEmergency(bank: Bank, amount: Money, cet1Ratio: Rate, p: FacilityParams): Money {
        val snap = bank.snapshot()
        if (!eligibleForEmergency(snap, cet1Ratio, p)) return Money.ZERO
        val got = Money.min(amount, emergencyCapacity(snap, p))
        if (got.raw <= 0L) return Money.ZERO
        bank.reserves += got
        bank.centralBankEmergency += got
        return got
    }

    /** Repays facility debt out of reserves, emergency first because it costs more. Returns the amount repaid. */
    fun repay(bank: Bank, amount: Money): Money {
        var left = Money.min(Money.min(amount, bank.reserves.coerceAtLeast(Money.ZERO)), bank.centralBankDebt)
        val total = left
        val fromEmergency = Money.min(left, bank.centralBankEmergency)
        bank.centralBankEmergency -= fromEmergency
        left -= fromEmergency
        val fromMarginal = Money.min(left, bank.centralBankMarginal)
        bank.centralBankMarginal -= fromMarginal
        bank.reserves -= total
        return total
    }

    /** One month of interest on facility borrowing. */
    fun monthlyInterest(s: BankSnapshot, c: Corridor): Money =
        BankMath.monthlyInterest(s.centralBankMarginal, c.ceiling.ppm) + BankMath.monthlyInterest(s.centralBankEmergency, c.emergency.ppm)

    /** One month of interest on reserves held above the requirement, paid at the floor. */
    fun monthlyRemuneration(s: BankSnapshot, required: Money, c: Corridor): Money =
        BankMath.monthlyInterest((s.reserves - required).coerceAtLeast(Money.ZERO), c.floor.ppm)
}

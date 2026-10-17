package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Something that happened to an offset project. The service turns these into events and ledger entries. */
sealed class OffsetOutcome {
    abstract val project: OffsetProject

    data class Validated(override val project: OffsetProject) : OffsetOutcome()

    /** The verifier turned the project down. [writtenOff] is the money that was spent and is now lost. */
    data class Rejected(override val project: OffsetProject, val writtenOff: Money) : OffsetOutcome()

    /**
     * A period was verified. [nominal] was claimed, [delivered] came out after any shortfall, [credits] were issued after
     * the additionality haircut and the buffer, and [buffered] were held back. [cost] is the part of the money paid that
     * these credits carry.
     */
    data class Issued(
        override val project: OffsetProject, val nominal: Tonnes, val delivered: Tonnes, val credits: Tonnes, val buffered: Tonnes,
        val short: Boolean, val cost: Money,
    ) : OffsetOutcome()

    /** The insurance buffer was used up by a reversal (fire, pests, leakage). */
    data class Reversed(override val project: OffsetProject, val lost: Tonnes) : OffsetOutcome()

    /** The project ended. [released] is the buffer handed over and [writtenOff] any money still not charged to credits. */
    data class Completed(override val project: OffsetProject, val released: Tonnes, val writtenOff: Money) : OffsetOutcome()
}

/** The life of an offset project, advanced one day at a time. */
object OffsetLifecycle {

    /** Sets up a new project's schedule from its profile. */
    fun schedule(project: OffsetProject, profile: OffsetProfile) {
        val validationAt = project.startedAt + profile.leadDays.toLong()
        val first = validationAt + (profile.validationDays + profile.issuanceEveryDays).toLong()
        val ends = validationAt + profile.lifeYears.toLong() * CarbonCalendar.DAYS_PER_YEAR
        project.setSchedule(validationAt, first, ends)
    }

    /**
     * Moves every live project forward to [tick]. [random] gives the stream for a project's draws. Credits are put
     * where they belong by the caller from the returned outcomes; the lifecycle only changes the projects themselves.
     */
    fun advance(book: OffsetBook, tick: Tick, random: (OffsetProjectId) -> DeterministicRandom): List<OffsetOutcome> {
        val out = ArrayList<OffsetOutcome>()
        for (p in book.projects()) {
            if (!p.isLive) continue
            val profile = OffsetProfiles.of(p.kind)
            val r = random(p.id)
            if (p.status == OffsetStatus.DEVELOPMENT && tick >= p.validationAt) {
                p.advanceStatus(OffsetStatus.VALIDATION)
            }
            if (p.status == OffsetStatus.VALIDATION && tick >= p.validationAt + profile.validationDays.toLong()) {
                if (r.chance(Rate(profile.validationFailPpm))) {
                    p.advanceStatus(OffsetStatus.REJECTED)
                    val lost = p.basisLeft
                    p.amortise(lost)
                    out += OffsetOutcome.Rejected(p, lost)
                    continue
                }
                p.advanceStatus(OffsetStatus.ISSUING)
                out += OffsetOutcome.Validated(p)
            }
            if (p.status != OffsetStatus.ISSUING) continue
            while (tick >= p.nextIssuance && p.nextIssuance <= p.endsAt) {
                out += issue(p, profile, r)
                p.nextIssuance = p.nextIssuance + profile.issuanceEveryDays.toLong()
            }
            if (p.status == OffsetStatus.ISSUING && tick >= p.endsAt) out += complete(p)
        }
        return out
    }

    private fun issue(p: OffsetProject, profile: OffsetProfile, r: DeterministicRandom): List<OffsetOutcome> {
        val out = ArrayList<OffsetOutcome>()
        val nominal = Tonnes(mulDiv(p.annual.raw, profile.issuanceEveryDays.toLong(), CarbonCalendar.DAYS_PER_YEAR.toLong(), RoundingMode.DOWN))
        val short = r.chance(Rate(profile.deliveryRiskPpm))
        val delivered = if (short) nominal.times(Rate(Rate.ONE_PPM - profile.underDeliveryPpm)) else nominal
        val net = delivered.times(Rate(Rate.ONE_PPM - profile.haircutPpm))
        val buffered = net.times(Rate(profile.bufferPpm))
        val credits = net - buffered
        // The period carries its slice of the money paid, straight-line over the nominal life.
        val lifeNominal = p.annual * profile.lifeYears.toLong()
        val slice = if (lifeNominal.raw <= 0L) Money.ZERO else Money.min(p.basisLeft, Money(mulDiv(p.capex.raw, nominal.raw, lifeNominal.raw, RoundingMode.HALF_EVEN)))
        p.amortise(slice)
        p.addIssued(credits, buffered, short)
        out += OffsetOutcome.Issued(p, nominal, delivered, credits, buffered, short, slice)
        if (p.buffer.isPositive && r.chance(Rate(profile.reversalRiskPpm))) {
            val lost = p.buffer
            p.loseBuffer()
            out += OffsetOutcome.Reversed(p, lost)
        }
        return out
    }

    private fun complete(p: OffsetProject): OffsetOutcome {
        p.advanceStatus(OffsetStatus.COMPLETED)
        val released = p.releaseBuffer()
        val left = p.basisLeft
        p.amortise(left)
        return OffsetOutcome.Completed(p, released, left)
    }

    /** Owners of the live projects that would credit [company]; used for reports. */
    fun pipelineCredits(book: OffsetBook, company: CompanyId): Tonnes {
        var total = Tonnes.ZERO
        for (p in book.projectsOf(company)) {
            if (!p.isLive) continue
            val profile = OffsetProfiles.of(p.kind)
            val remainingDays = (p.endsAt.value - p.nextIssuance.value).coerceAtLeast(0L) + profile.issuanceEveryDays
            val nominal = Tonnes(mulDiv(p.annual.raw, remainingDays, CarbonCalendar.DAYS_PER_YEAR.toLong(), RoundingMode.DOWN))
            val net = nominal.times(Rate(Rate.ONE_PPM - profile.haircutPpm))
            total += net - net.times(Rate(profile.bufferPpm))
        }
        return total
    }
}

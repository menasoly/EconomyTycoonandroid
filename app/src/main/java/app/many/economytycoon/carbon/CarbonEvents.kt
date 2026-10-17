package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** Things that happened in carbon markets and compliance; the news feed and the alerts read these. */
sealed class CarbonEvent {
    abstract val tick: Tick
    abstract val country: CountryId

    data class RegimeChanged(override val tick: Tick, override val country: CountryId, val from: RegimeKind, val to: RegimeKind) : CarbonEvent()

    data class AuctionCleared(override val tick: Tick, override val country: CountryId, val result: AuctionResult) : CarbonEvent()

    data class PermitsAllocated(override val tick: Tick, override val country: CountryId, val year: Int, val tonnes: Tonnes, val recipients: Int) : CarbonEvent()

    data class ReserveAdjusted(override val tick: Tick, override val country: CountryId, val year: Int, val withheld: Tonnes, val released: Tonnes, val cancelled: Tonnes) : CarbonEvent()

    data class YearClosed(override val tick: Tick, override val country: CountryId, val company: CompanyId, val year: Int, val covered: Tonnes, val status: ComplianceStatus) : CarbonEvent()

    data class TrueUp(override val tick: Tick, override val country: CountryId, val company: CompanyId, val record: ComplianceRecord) : CarbonEvent()

    data class TaxCharged(override val tick: Tick, override val country: CountryId, val company: CompanyId, val amount: Money, val tonnes: Tonnes) : CarbonEvent()

    data class PermitsExpired(override val tick: Tick, override val country: CountryId, val tonnes: Tonnes) : CarbonEvent()

    data class OffsetProjectChanged(override val tick: Tick, override val country: CountryId, val outcome: OffsetOutcome) : CarbonEvent()

    data class AbatementDone(override val tick: Tick, override val country: CountryId, val company: CompanyId, val facility: FacilityId, val kind: AbatementKind, val subsidy: Money) : CarbonEvent()

    data class AbatementRetired(override val tick: Tick, override val country: CountryId, val company: CompanyId, val facility: FacilityId, val kind: AbatementKind) : CarbonEvent()

    data class BorderLevy(override val tick: Tick, override val country: CountryId, val importer: CompanyId?, val amount: Money, val embedded: Tonnes) : CarbonEvent()

    data class RatingChanged(override val tick: Tick, override val country: CountryId, val company: CompanyId, val from: CarbonGrade?, val to: CarbonGrade, val score: Int) : CarbonEvent()
}

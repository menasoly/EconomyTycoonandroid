package app.many.economytycoon.banking

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.CreditRating

/** Things that happened in the banking sector, for news, alerts and the activity log. */
sealed class BankEvent {
    abstract val tick: Long

    /** The bank the event is about, if there is one. */
    open val subject: BankId? get() = null

    data class Founded(val bank: BankId, val country: CountryId, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class RungChanged(val bank: BankId, val from: SupervisoryRung, val to: SupervisoryRung, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class RunStageChanged(val bank: BankId, val from: RunStage, val to: RunStage, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class RatingChanged(val bank: BankId, val from: CreditRating, val to: CreditRating, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class DividendPaid(val bank: BankId, val amount: Money, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class Rumour(val bank: BankId, val intensityPpm: Long, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class FacilityUsed(val bank: BankId, val marginal: Money, val emergency: Money, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class FireSale(val bank: BankId, val amount: Money, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class LiquidityFailure(val bank: BankId, val shortfall: Money, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class InterbankFrozen(val country: CountryId, override val tick: Long) : BankEvent()
    data class Supervised(val bank: BankId, val reason: String, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class Released(val bank: BankId, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class Resolved(val outcome: ResolutionOutcome, override val tick: Long) : BankEvent() { override val subject get() = outcome.bank }
    data class CapitalRaised(val bank: BankId, val instrument: CapitalInstrument, val amount: Money, override val tick: Long) : BankEvent() { override val subject get() = bank }
    data class LevyCollected(val country: CountryId, val amount: Money, override val tick: Long) : BankEvent()
    data class StressTestPublished(val country: CountryId, val scenario: String, val failures: Int, val banks: Int, override val tick: Long) : BankEvent()
    data class Strategy(val bank: BankId, val summary: String, override val tick: Long) : BankEvent() { override val subject get() = bank }
}

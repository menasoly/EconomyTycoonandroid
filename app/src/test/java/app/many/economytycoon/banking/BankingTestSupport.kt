package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals

/** Shared fixtures for the banking tests. */
object BankingFx {
    val country = CountryId(1)
    val params = BankingParams()
    val calm = StaticBankingInputs()

    fun money(units: Long) = Money.units(units)

    /** A bank with books laid out by the factory; capital is in whole units. */
    fun bank(
        id: Long = 1L,
        capital: Long = 1_000_000L,
        operator: BankOperator = BankOperator.AI,
        strategy: BankStrategy? = BankStrategies.of(BankArchetype.BALANCED),
        spec: OpeningSpec? = null,
        inputs: BankingInputs = calm,
        owner: CompanyId? = null,
    ): Bank = BankFactory.open(
        BankId(id), "Bank $id", country, operator, owner, strategy,
        spec ?: OpeningSpec(Money.units(capital)), inputs, params, Tick(0),
    )

    fun banks(n: Int, capital: Long = 1_000_000L): List<Bank> = (1..n).map { bank(it.toLong(), capital) }

    fun assertBalanced(b: Bank) = assertEquals("books out of balance for ${b.name}", Money.ZERO, b.snapshot().imbalance)

    fun assertBalanced(all: List<Bank>) { for (b in all) assertBalanced(b) }

    /** Interbank positions must match across a market. */
    fun assertInterbankMatches(all: List<Bank>) =
        assertEquals(Money.sum(all.map { it.interbankLent }), Money.sum(all.map { it.interbankBorrowed }))

    fun pct(p: Long) = Rate.percent(p)
}

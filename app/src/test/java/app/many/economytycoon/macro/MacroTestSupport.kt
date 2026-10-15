package app.many.economytycoon.macro

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick

class FakeView : MacroView {
    val ids = mutableListOf(CountryId(1))
    val inflation = HashMap<CountryId, Long>()
    val unemployment = HashMap<CountryId, Long>()
    override fun countries() = ids.toList()
    override fun inflationPpm(country: CountryId) = inflation[country] ?: 20_000L
    override fun unemploymentPpm(country: CountryId) = unemployment[country] ?: 50_000L
    override fun population(country: CountryId) = 1_000_000L
    override fun averageWage(country: CountryId) = Money.units(100)
}

class MacroRig(seed: Long = 11, val params: MacroParams = MacroParams()) {
    val view = FakeView()
    val service = MacroService(params, view)
    val random = RandomProvider(seed)
    val country = CountryId(1)
    var tick = 0L

    /** Records a steady flow and runs [periods] reviews. */
    fun run(periods: Int, consumption: Long = 800_000, investment: Long = 200_000, government: Long = 100_000): List<MacroSnapshot> {
        val out = ArrayList<MacroSnapshot>()
        repeat(periods) {
            for (id in view.ids) {
                service.record(id, GdpKind.CONSUMPTION, Money.units(consumption))
                service.record(id, GdpKind.INVESTMENT, Money.units(investment))
                service.record(id, GdpKind.GOVERNMENT, Money.units(government))
            }
            tick += params.periodTicks
            out += service.step(Tick(tick), random)
        }
        return out
    }
}

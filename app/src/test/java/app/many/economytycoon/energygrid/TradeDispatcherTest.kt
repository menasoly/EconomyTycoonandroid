package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeDispatcherTest {
    private val a = GridId(1)
    private val b = GridId(2)
    private val c = GridId(3)

    private fun unit(id: Long, cost: Long, kw: Long) =
        DispatchUnit(UnitId(id), Technology.GAS, kw, Money.units(cost), kw, kw, kw, 0L)

    private fun link(id: Long, x: GridId, y: GridId, cap: Long, loss: Long = 100_000L, fee: Long = 0L) =
        LinkView(LinkId(id), x, y, cap, loss, Money.units(fee))

    @Test fun cheapGridSuppliesTheDearOneUpToTheLine() {
        val inputs = mapOf(
            a to DispatchInput(50_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(150_000, listOf(unit(2, 60, 100_000), unit(3, 200, 100_000))),
        )
        val out = TradeDispatcher.run(inputs, listOf(link(1, a, b, 40_000, fee = 2)))
        val t = out.transfers.single()
        assertEquals(a, t.from)
        assertEquals(b, t.to)
        assertEquals(40_000L, t.sentKw)
        assertEquals(36_000L, t.receivedKw)
        assertEquals(90_000L, out.results.getValue(a).generationKw)
        assertEquals(114_000L, out.results.getValue(b).generationKw)
    }

    @Test fun flowStopsBeforeThePricesCross() {
        val inputs = mapOf(
            a to DispatchInput(100_000, listOf(unit(1, 20, 100_000), unit(2, 55, 100_000))),
            b to DispatchInput(120_000, listOf(unit(3, 60, 100_000), unit(4, 90, 100_000))),
        )
        val out = TradeDispatcher.run(inputs, listOf(link(1, a, b, 500_000, fee = 0)))
        val t = out.transfers.single()
        assertEquals(22_223L, t.sentKw)
        assertEquals(20_000L, t.receivedKw)
        assertEquals(Money.units(55), out.results.getValue(a).price)
        assertEquals(Money.units(60), out.results.getValue(b).price)
        assertTrue(out.results.getValue(a).price <= out.results.getValue(b).price)
    }

    @Test fun noFlowWhenLossesAndFeesEatTheGap() {
        val inputs = mapOf(
            a to DispatchInput(50_000, listOf(unit(1, 50, 100_000))),
            b to DispatchInput(50_000, listOf(unit(2, 55, 100_000))),
        )
        // 50 / 0.9 = 55.6 > 55
        assertTrue(TradeDispatcher.run(inputs, listOf(link(1, a, b, 50_000))).transfers.isEmpty())
    }

    @Test fun equalPricesDoNotTrade() {
        val inputs = mapOf(a to DispatchInput(10_000, listOf(unit(1, 40, 50_000))), b to DispatchInput(10_000, listOf(unit(2, 40, 50_000))))
        assertTrue(TradeDispatcher.run(inputs, listOf(link(1, a, b, 50_000, loss = 0L))).transfers.isEmpty())
    }

    @Test fun aLineOutOfServiceCarriesNothing() {
        val inputs = mapOf(
            a to DispatchInput(50_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(150_000, listOf(unit(2, 60, 100_000), unit(3, 200, 100_000))),
        )
        assertTrue(TradeDispatcher.run(inputs, listOf(link(1, a, b, 0L))).transfers.isEmpty())
    }

    @Test fun aShortGridImportsToAvoidBlackout() {
        val inputs = mapOf(
            a to DispatchInput(40_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(130_000, listOf(unit(2, 60, 100_000))),
        )
        val local = DispatchEngine.dispatch(inputs.getValue(b))
        assertEquals(30_000L, local.unservedKw)
        val out = TradeDispatcher.run(inputs, listOf(link(1, a, b, 100_000, loss = 0L)))
        assertEquals(0L, out.results.getValue(b).unservedKw)
        assertEquals(30_000L, out.transfers.single().receivedKw)
    }

    @Test fun energyIsConservedAcrossTheLine() {
        val inputs = mapOf(
            a to DispatchInput(50_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(150_000, listOf(unit(2, 60, 100_000), unit(3, 200, 100_000))),
        )
        val out = TradeDispatcher.run(inputs, listOf(link(1, a, b, 40_000)))
        val made = out.results.values.sumOf { it.generationKw }
        val lost = out.transfers.sumOf { it.sentKw - it.receivedKw }
        assertEquals(200_000L + lost, made)
    }

    @Test fun linksAreWorkedInIdOrderAndSeePriorFlows() {
        val inputs = mapOf(
            a to DispatchInput(10_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(110_000, listOf(unit(2, 60, 100_000), unit(3, 150, 100_000))),
            c to DispatchInput(10_000, listOf(unit(4, 30, 100_000))),
        )
        val links = listOf(link(2, c, b, 100_000, loss = 0L), link(1, a, b, 5_000, loss = 0L))
        val out = TradeDispatcher.run(inputs, links)
        assertEquals(LinkId(1), out.transfers.first().link)
        assertEquals(5_000L, out.transfers.first().sentKw)
        assertEquals(LinkId(2), out.transfers[1].link)
        // the full 90 MW of spare capacity would leave C with no reserve and lift its price above B's, so the flow is halved
        assertEquals(45_000L, out.transfers[1].sentKw)
        assertEquals(Money.units(60), out.results.getValue(b).price)
        assertEquals(0L, out.results.getValue(b).unservedKw)
    }

    @Test fun sameInputsGiveTheSameFlows() {
        val inputs = mapOf(
            a to DispatchInput(50_000, listOf(unit(1, 20, 100_000))),
            b to DispatchInput(150_000, listOf(unit(2, 60, 100_000), unit(3, 200, 100_000))),
        )
        val x = TradeDispatcher.run(inputs, listOf(link(1, a, b, 40_000)))
        val y = TradeDispatcher.run(inputs, listOf(link(1, a, b, 40_000)))
        assertEquals(x.transfers, y.transfers)
    }
}

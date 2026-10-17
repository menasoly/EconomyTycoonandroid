package app.many.economytycoon.marketing

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertTrue

/** Shared fixtures: two companies, two goods, two regions and a small city, with stable audiences. */
object MarketingTestSupport {
    val acme = CompanyId(1)
    val rival = CompanyId(2)
    val beer = GoodId("beer")
    val bread = GoodId("bread")
    val north = RegionId(1)
    val south = RegionId(2)
    val harbour = CityId(10)

    fun audiences(): FixedAudienceSource = FixedAudienceSource(
        listOf(
            Audience.even(north, 500_000L),
            Audience.even(south, 200_000L, incomeIndexPpm = 1_300_000L),
            Audience.even(north, 100_000L, city = harbour, regionInhabitants = 500_000L),
        ),
    )

    fun system(seed: Long = 7L, params: MarketingParams = MarketingParams()) = MarketingSystem(params, worldSeed = seed)

    fun request(
        company: CompanyId = acme, good: GoodId = beer, region: RegionId = north, channel: Channel = Channel.RADIO,
        daily: Long = 500, days: Int = 30, tier: CreativeTier = CreativeTier.PROFESSIONAL, segment: TargetSegment = TargetSegment.EVERYONE,
        city: CityId? = null, start: Tick? = null,
    ) = CampaignRequest(company, good, region, city, channel, segment, Money.units(daily), days, tier, start)

    val bank: Money = Money.units(10_000_000)

    fun book(s: MarketingSystem, request: CampaignRequest = request(), tick: Long = 0L, cash: Money = bank): Campaign {
        val r = s.campaigns.create(request, Tick(tick), cash, audiences())
        assertTrue("booking rejected: ${(r as? Outcome.Rejected)?.rejection}", r is Outcome.Ok)
        return (r as Outcome.Ok).value.campaign
    }

    fun run(s: MarketingSystem, fromTick: Long, days: Int, quality: QualitySource = QualitySource.NEUTRAL): List<DayReport> =
        (0 until days).map { s.engine.advance(Tick(fromTick + it), audiences(), quality) }

    fun context(
        good: GoodId = beer, region: RegionId = north, people: Long = 500_000L, revenue: Long = 20_000, margin: Long = 300_000L,
        income: Long = 1_000_000L,
    ) = MarketContext(good, region, Audience.even(region, people, income), Money.units(revenue), margin)

    fun rejection(o: Outcome<*>): String? = o.rejectionOrNull()?.rule

    /** A system that has seen a bit of everything: running, paused and cancelled campaigns, a test, a sale and a scandal. */
    fun busy(seed: Long = 7L, days: Int = 25): MarketingSystem {
        val s = system(seed)
        val tv = book(s, request(channel = Channel.TELEVISION, daily = 1_500, days = 60, tier = CreativeTier.STUDIO))
        val radio = book(s, request(channel = Channel.RADIO, daily = 600, days = 40))
        book(s, request(company = rival, channel = Channel.ONLINE, daily = 900, days = 50))
        book(s, request(good = bread, region = south, channel = Channel.SPONSORSHIP, daily = 700, days = 80, tier = CreativeTier.BASIC))
        val city = book(s, request(channel = Channel.IN_STORE, daily = 120, days = 30, city = harbour, segment = TargetSegment.BUDGET))
        s.experiments.start(radio.id, CreativeTier.STUDIO, 12, Tick(0), bank).getOrThrow()
        s.promotions.launch(PromotionRequest(acme, beer, north, null, 30, 100_000L, Money.units(100), Money.units(50), 3_000_000L), Tick(0)).getOrThrow()
        run(s, 0, days)
        s.campaigns.pause(tv.id)
        s.campaigns.cancel(city.id, Tick(days.toLong()))
        s.damage.record(rival, null, null, DamageKind.RECALL, 600_000L, Tick(days.toLong())).getOrThrow()
        return s
    }
}

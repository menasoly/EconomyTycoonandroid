package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.DistrictView
import app.many.economytycoon.urban.Insight
import app.many.economytycoon.urban.InsightSeverity
import app.many.economytycoon.urban.UrbanView
import app.many.economytycoon.urban.ZoneUse

/** How money is written on the urban screens; the real one comes from the player's settings. */
typealias MoneyText = (Money) -> String

data class UrbanCityRow(val city: CityId, val name: String, val rank: String, val liveability: String, val tone: Tone, val headline: String, val warnings: Int)

data class UrbanOverviewModel(val tiles: List<Tile>, val cities: List<UrbanCityRow>, val note: String)

data class UrbanDistrictRow(val index: Int, val name: String, val zone: String, val tone: Tone, val facts: List<Fact>, val badges: List<String>)

data class InsightRow(val title: String, val detail: String, val suggestion: String?, val severity: String, val tone: Tone, val category: String)

data class UrbanCityModel(
    val title: String,
    val tiles: List<Tile>,
    val factors: List<Fact>,
    val districts: List<UrbanDistrictRow>,
    val insights: List<InsightRow>,
    val trend: String,
)

/** The urban overview: how the player's cities rank, and a district-by-district look at one of them. */
object UrbanPresenter {
    fun liveabilityText(index: Int): String = "${index / 10}.${index % 10}"

    fun liveabilityTone(index: Int): Tone = Tones.health(index * 1000L)

    fun severityLabel(s: InsightSeverity): String = when (s) {
        InsightSeverity.INFO -> "For information"
        InsightSeverity.NOTICE -> "Worth a look"
        InsightSeverity.WARNING -> "Needs attention"
        InsightSeverity.CRITICAL -> "Urgent"
    }

    fun severityTone(s: InsightSeverity): Tone = when (s) {
        InsightSeverity.INFO -> Tone.MUTED
        InsightSeverity.NOTICE -> Tone.INFO
        InsightSeverity.WARNING -> Tone.WARN
        InsightSeverity.CRITICAL -> Tone.BAD
    }

    fun zoneTone(z: ZoneUse): Tone = when (z) {
        ZoneUse.RESIDENTIAL, ZoneUse.MIXED -> Tone.INFO
        ZoneUse.COMMERCIAL -> Tone.WARN
        ZoneUse.INDUSTRIAL -> Tone.MUTED
        ZoneUse.GREEN -> Tone.GOOD
        ZoneUse.CIVIC -> Tone.NEUTRAL
    }

    fun insightRow(i: Insight) = InsightRow(i.title, i.detail, i.suggestion, severityLabel(i.severity), severityTone(i.severity), i.category.label)

    fun overview(view: UrbanView): UrbanOverviewModel {
        if (view.cities.isEmpty()) return UrbanOverviewModel(emptyList(), emptyList(), "No city has a plan yet.")
        val best = view.ranking.first()
        val bestName = view.city(best.city)?.name ?: "A city"
        val people = view.cities.sumOf { it.population }
        val tiles = listOf(
            Tile("Cities planned", view.cities.size.toString()),
            Tile("Residents", Formatting.compactCount(people)),
            Tile("Most liveable", bestName, Tone.GOOD, liveabilityText(best.index)),
            Tile("Urgent problems", view.cities.sumOf { c -> c.insights.count { it.severity == InsightSeverity.CRITICAL } }.toString(),
                if (view.cities.any { c -> c.insights.any { it.severity == InsightSeverity.CRITICAL } }) Tone.BAD else Tone.GOOD),
        )
        val rows = view.ranking.mapNotNull { r ->
            val c = view.city(r.city) ?: return@mapNotNull null
            UrbanCityRow(
                c.city, c.name, "#${r.rank} of ${view.ranking.size}", liveabilityText(c.liveability), liveabilityTone(c.liveability),
                "Best: ${r.best.label.lowercase()}; weakest: ${r.worst.label.lowercase()}",
                c.insights.count { it.severity >= InsightSeverity.WARNING },
            )
        }
        return UrbanOverviewModel(tiles, rows, "Liveability combines housing, commute, safety, environment, services, green space, amenities and jobs.")
    }

    private fun districtRow(d: DistrictView, money: MoneyText): UrbanDistrictRow {
        val badges = ArrayList<String>()
        if (d.heritage) badges += "Heritage"
        if (d.openPetition != null) badges += "Petition open"
        if (d.rezonedRecently) badges += "Recently rezoned"
        if (d.backlashPpm >= 650_000L) badges += "Protests"
        if (d.pipelineArea > 0L) badges += "Building"
        val facts = listOf(
            Fact("Residents", Formatting.grouped(d.residents)),
            Fact("Jobs", Formatting.grouped(d.jobs)),
            Fact("Floor-area ratio", "${ratio(d.farMilli)} of ${ratio(d.maxFarMilli)}"),
            Fact("Rent a month per sqm", money(d.rentPerSqm), if (d.rentGrowthPpm > 80_000L) Tone.WARN else Tone.NEUTRAL, "Change over the year: ${Formatting.signedPercentPpm(d.rentGrowthPpm)}"),
            Fact("Empty homes", Formatting.percentPpm(d.vacancyPpm)),
            Fact("Land value per sqm", money(d.landValuePerSqm)),
            Fact("Accessibility", "${d.accessibilityPermille / 10} of 100", Tones.health(d.accessibilityPermille * 1000L)),
            Fact("Buildings", "${d.averageAgeYears} years old, ${Formatting.percentPpm(d.conditionPpm)} condition", Tones.health(d.conditionPpm)),
            Fact("Low-income households", Formatting.percentPpm(d.lowIncomeSharePpm)),
            Fact("Commute", if (d.commuteSeconds == 0L) "none" else "${d.commuteSeconds / 60} min"),
        )
        return UrbanDistrictRow(d.index, d.name, d.zone.label, zoneTone(d.zone), facts, badges)
    }

    fun ratio(milli: Int): String = "${milli / 1000}.${(milli % 1000 / 10).toString().padStart(2, '0')}"

    fun city(view: UrbanView, city: CityId, money: MoneyText = { Formatting.compactMoney(it) }): UrbanCityModel? {
        val c = view.city(city) ?: return null
        return city(c, money)
    }

    fun city(c: CityView, money: MoneyText = { Formatting.compactMoney(it) }): UrbanCityModel {
        val tiles = listOf(
            Tile("Liveability", liveabilityText(c.liveability), liveabilityTone(c.liveability), "Rank ${c.rank} of ${c.cityCount}"),
            Tile("Residents", Formatting.compactCount(c.population), Tone.NEUTRAL, if (c.unhoused > 0L) "${Formatting.compactCount(c.unhoused)} unhoused" else null),
            Tile("Empty homes", Formatting.percentPpm(c.vacancyPpm), if (c.vacancyPpm < 25_000L) Tone.WARN else Tone.NEUTRAL),
            Tile("Rent burden", Formatting.percentPpm(c.rentBurdenPpm), if (c.rentBurdenPpm > 400_000L) Tone.BAD else Tone.NEUTRAL, "Low incomes: ${Formatting.percentPpm(c.lowIncomeBurdenPpm)}"),
            Tile("Commute", "${c.averageCommuteSeconds / 60} min", if (c.averageCommuteSeconds > 2_400L) Tone.WARN else Tone.NEUTRAL),
            Tile("Cash", money(c.budget.cash), if (c.budget.cash.isNegative) Tone.BAD else Tone.NEUTRAL, "Debt ${money(c.budget.debt)}"),
        )
        val factors = c.factors.map { Fact(it.factor.label, "${it.score / 10} of 100", Tones.health(it.score * 1000L)) }
        val trend = when {
            c.liveabilityHistory.size < 2 -> "Not enough history yet."
            else -> {
                val change = c.liveability - c.liveabilityHistory.first()
                if (change > 5) "Up ${liveabilityText(change)} over ${c.liveabilityHistory.size} months."
                else if (change < -5) "Down ${liveabilityText(-change)} over ${c.liveabilityHistory.size} months."
                else "Steady over ${c.liveabilityHistory.size} months."
            }
        }
        return UrbanCityModel(c.name, tiles, factors, c.districts.map { districtRow(it, money) }, c.insights.map { insightRow(it) }, trend)
    }
}

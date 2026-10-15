package app.many.economytycoon.advisor

/** One thing the advisor looks for. A rule returns nothing when all is well. */
fun interface AdvisorRule {
    fun check(f: AdvisorFacts, t: AdvisorTuning): List<Insight>
}

object AdvisorRules {
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"
    private fun one(i: Insight?): List<Insight> = if (i == null) emptyList() else listOf(i)

    val noFacilities = AdvisorRule { f, _ ->
        one(if (f.facilities.isEmpty()) Insight("no-facilities", Topic.PRODUCTION, 90, "Build your first facility", "Money in the bank earns nothing. Pick a region and build something that makes a good people buy.") else null)
    }

    val lossMaking = AdvisorRule { f, _ ->
        one(if (f.monthRevenueUnits > 0L && f.monthNetIncomeUnits < 0L)
            Insight("loss-making", Topic.FINANCE, 70, "The last month made a loss", "Costs ran ahead of sales. Look at prices, wages and idle facilities before cash runs thin.") else null)
    }

    val thinMargin = AdvisorRule { f, t ->
        one(if (f.monthRevenueUnits > 0L && f.monthNetIncomeUnits >= 0L && f.netMarginPpm < t.thinMarginPpm)
            Insight("thin-margin", Topic.FINANCE, 45, "Margins are thin", "Little of each sale is left as profit. Raise prices where the market allows, or cut what you pay for inputs.") else null)
    }

    val tightLiquidity = AdvisorRule { f, t ->
        one(if (f.debtUnits > 0L && f.currentRatioPpm < t.tightLiquidityPpm)
            Insight("tight-liquidity", Topic.FINANCE, 75, "Short-term bills outweigh what you can quickly turn to cash", "Sell stock, collect what you are owed or hold off on new spending until the ratio is back above one.") else null)
    }

    val idleCash = AdvisorRule { f, t ->
        val big = f.cashUnits >= t.idleCashFloorUnits && f.netWorthUnits > 0L && f.cashUnits * 1_000_000L / f.netWorthUnits >= t.idleCashShareOfWorthPpm
        one(if (big && f.debtUnits == 0L && f.facilities.isNotEmpty())
            Insight("idle-cash", Topic.FINANCE, 50, "A lot of cash is sitting idle", "Put it to work: build or expand, buy bonds, or acquire land. Cash earns nothing and shrinks in real terms.") else null)
    }

    val expensiveDebt = AdvisorRule { f, t ->
        val rate = f.loanRatePpm
        one(if (rate != null && rate >= t.expensiveLoanPpm && f.cashUnits > f.debtUnits)
            Insight("expensive-debt", Topic.FINANCE, 55, "You can afford to repay an expensive loan", "You hold more cash than you owe, and the loan costs ${rate / 10_000}% a year. Early repayment saves interest.") else null)
    }

    val goPublic = AdvisorRule { f, t ->
        one(if (!f.listed && f.netWorthUnits >= t.listingWorthUnits)
            Insight("go-public", Topic.FINANCE, 30, "The company is big enough to list", "A public offering raises money without debt, at the price of shared control.") else null)
    }

    val idleFacilities = AdvisorRule { f, t ->
        val idle = f.facilities.filter { it.running && it.blocked == null && it.utilizationPpm < t.idleUtilizationPpm }
        one(if (idle.isNotEmpty())
            Insight("idle-facility", Topic.PRODUCTION, (50 + idle.size).coerceAtMost(75), "${plural(idle.size, "facility", "facilities")} barely running",
                "${idle.take(3).joinToString { it.name }}${if (idle.size > 3) " and more" else ""} use little of their capacity. Set a recipe, supply inputs or close what you cannot fill.") else null)
    }

    val blockedFacilities = AdvisorRule { f, _ ->
        val blocked = f.facilities.filter { it.running && it.blocked != null }
        one(if (blocked.isNotEmpty()) {
            val first = blocked.first()
            Insight("blocked-facility", Topic.PRODUCTION, (62 + blocked.size).coerceAtMost(80), "${plural(blocked.size, "facility", "facilities")} held up",
                "${first.name} is stuck: ${first.blocked}.${if (blocked.size > 1) " ${blocked.size - 1} more have trouble too." else ""}")
        } else null)
    }

    val wornFacilities = AdvisorRule { f, t ->
        val worn = f.facilities.filter { it.running && it.conditionPpm < t.wornConditionPpm }
        one(if (worn.isNotEmpty())
            Insight("worn-facility", Topic.PRODUCTION, 40, "${plural(worn.size, "facility", "facilities")} in poor repair", "Worn plant breaks down and turns out less. Pay for maintenance before it fails.") else null)
    }

    val overstock = AdvisorRule { f, t ->
        one(if (f.netWorthUnits > 0L && f.stockUnits * 1_000_000L / f.netWorthUnits >= t.overstockShareOfWorthPpm)
            Insight("overstock", Topic.MARKET, 40, "Too much capital is tied up in stock", "Goods in the warehouse are not earning. Lower the asking price or open sales in another region.") else null)
    }

    val understaffed = AdvisorRule { f, _ ->
        one(if (f.vacancies > 0L)
            Insight("understaffed", Topic.PEOPLE, 60, "${plural(f.vacancies.toInt(), "position")} unfilled", "Facilities without a full crew run slowly. Raise the wage you offer, or train in a region with more people.") else null)
    }

    val lowMorale = AdvisorRule { f, t ->
        one(if (f.headcount > 0L && f.averageMoralePpm < t.lowMoralePpm)
            Insight("low-morale", Topic.PEOPLE, 55, "Morale is low", "Unhappy workers are less productive, quit more often and organise. Look at wages and benefits.") else null)
    }

    val underpaid = AdvisorRule { f, _ ->
        one(if (f.underpaidCohorts > 0)
            Insight("underpaid", Topic.PEOPLE, 45, "${plural(f.underpaidCohorts, "crew")} paid below the market", "Staff paid under the going rate leave for better offers. A modest rise is cheaper than rehiring.") else null)
    }

    val heavyPayroll = AdvisorRule { f, t ->
        val month = f.dailyPayrollUnits * 30L
        one(if (f.monthRevenueUnits > 0L && month * 1_000_000L / f.monthRevenueUnits >= t.heavyPayrollShareOfRevenuePpm)
            Insight("heavy-payroll", Topic.PEOPLE, 45, "Wages take most of your sales", "The payroll is a large part of revenue. Automate, raise output per worker or raise prices.") else null)
    }

    val idleResearch = AdvisorRule { f, _ ->
        one(if (f.researchIdle)
            Insight("idle-research", Topic.RESEARCH, 40, "The laboratory has nothing to work on", "Research points are going unused. Queue a project; technology lowers costs and opens new products.") else null)
    }

    val concentration = AdvisorRule { f, t ->
        one(if (f.industryCount == 1 && f.facilities.size >= t.concentrationFacilities)
            Insight("concentration", Topic.MARKET, 35, "Everything depends on one industry", "A downturn in a single market would hit every facility at once. Consider a second line of business.") else null)
    }

    val idleLand = AdvisorRule { f, _ ->
        one(if (f.idleParcels > 0)
            Insight("idle-land", Topic.PROPERTY, 35, "${plural(f.idleParcels, "parcel")} of land doing nothing", "Land you own but neither build on, lease nor list earns no income. Offer it for rent or sell it.") else null)
    }

    val contractOffers = AdvisorRule { f, _ ->
        one(if (f.contractOffers > 0)
            Insight("contract-offers", Topic.MARKET, 60, "${plural(f.contractOffers, "supply offer")} waiting", "Another company has proposed a supply contract. Steady sales at an agreed price smooth out the market.") else null)
    }

    val all: List<AdvisorRule> = listOf(
        noFacilities, lossMaking, thinMargin, tightLiquidity, idleCash, expensiveDebt, goPublic, idleFacilities, blockedFacilities,
        wornFacilities, overstock, understaffed, lowMorale, underpaid, heavyPayroll, idleResearch, concentration, idleLand, contractOffers,
    )
}

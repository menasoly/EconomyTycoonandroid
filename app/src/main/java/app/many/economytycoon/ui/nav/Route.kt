package app.many.economytycoon.ui.nav

/** The bottom bar's top-level places. Each keeps its own history so switching back finds you where you were. */
enum class Tab(val label: String, val glyph: String) {
    OVERVIEW("Overview", "◈"),
    OPERATIONS("Operations", "⚙"),
    MARKET("Market", "⇄"),
    FINANCE("Finance", "¤"),
    MORE("More", "≡"),
}

/**
 * A screen the player can be on. A route is plain data with a stable text form, so a back stack can be saved
 * across process death and restored without any framework.
 */
sealed class Route(val tab: Tab, val name: String) {
    abstract val args: List<String>

    val key: String get() = if (args.isEmpty()) name else name + ARG_SEPARATOR + args.joinToString(ARG_SEPARATOR)

    object Overview : Route(Tab.OVERVIEW, "overview") { override val args = emptyList<String>() }
    object Goals : Route(Tab.OVERVIEW, "goals") { override val args = emptyList<String>() }
    object Tutorial : Route(Tab.OVERVIEW, "tutorial") { override val args = emptyList<String>() }
    object Guide : Route(Tab.OVERVIEW, "guide") { override val args = emptyList<String>() }
    object Advisor : Route(Tab.OVERVIEW, "advisor") { override val args = emptyList<String>() }
    object News : Route(Tab.OVERVIEW, "news") { override val args = emptyList<String>() }
    object Alerts : Route(Tab.OVERVIEW, "alerts") { override val args = emptyList<String>() }

    object Facilities : Route(Tab.OPERATIONS, "facilities") { override val args = emptyList<String>() }
    object FacilityProfit : Route(Tab.OPERATIONS, "profit") { override val args = emptyList<String>() }
    data class FacilityDetail(val facility: Long) : Route(Tab.OPERATIONS, "facility") { override val args = listOf(facility.toString()) }
    data class Build(val region: Long) : Route(Tab.OPERATIONS, "build") { override val args = listOf(region.toString()) }
    object People : Route(Tab.OPERATIONS, "people") { override val args = emptyList<String>() }
    object Unions : Route(Tab.OPERATIONS, "unions") { override val args = emptyList<String>() }
    object Talent : Route(Tab.OPERATIONS, "talent") { override val args = emptyList<String>() }
    object TalentEducation : Route(Tab.OPERATIONS, "talent-education") { override val args = emptyList<String>() }
    object TalentTraining : Route(Tab.OPERATIONS, "talent-training") { override val args = emptyList<String>() }
    object TalentRecruiting : Route(Tab.OPERATIONS, "talent-recruiting") { override val args = emptyList<String>() }
    object TalentCareers : Route(Tab.OPERATIONS, "talent-careers") { override val args = emptyList<String>() }
    object TalentProviders : Route(Tab.OPERATIONS, "talent-providers") { override val args = emptyList<String>() }
    object Research : Route(Tab.OPERATIONS, "research") { override val args = emptyList<String>() }
    object Logistics : Route(Tab.OPERATIONS, "logistics") { override val args = emptyList<String>() }
    object Blueprints : Route(Tab.OPERATIONS, "blueprints") { override val args = emptyList<String>() }

    object Market : Route(Tab.MARKET, "market") { override val args = emptyList<String>() }
    object Sectors : Route(Tab.MARKET, "sectors") { override val args = emptyList<String>() }
    object Planner : Route(Tab.MARKET, "planner") { override val args = emptyList<String>() }
    object Watch : Route(Tab.MARKET, "watch") { override val args = emptyList<String>() }
    object Industries : Route(Tab.MARKET, "industries") { override val args = emptyList<String>() }
    object Managers : Route(Tab.MORE, "managers") { override val args = emptyList<String>() }
    object Trade : Route(Tab.MARKET, "trade") { override val args = emptyList<String>() }
    data class Good(val good: String) : Route(Tab.MARKET, "good") { override val args = listOf(good) }
    object Orders : Route(Tab.MARKET, "orders") { override val args = emptyList<String>() }
    object Rules : Route(Tab.MARKET, "rules") { override val args = emptyList<String>() }
    object Tenders : Route(Tab.MARKET, "tenders") { override val args = emptyList<String>() }
    object Contracts : Route(Tab.MARKET, "contracts") { override val args = emptyList<String>() }
    object Exchange : Route(Tab.FINANCE, "exchange") { override val args = emptyList<String>() }
    data class Stock(val company: Long) : Route(Tab.FINANCE, "stock") { override val args = listOf(company.toString()) }

    object Insurance : Route(Tab.FINANCE, "insurance") { override val args = emptyList<String>() }

    object Bonds : Route(Tab.FINANCE, "bonds") { override val args = emptyList<String>() }

    object Property : Route(Tab.OPERATIONS, "property") { override val args = emptyList<String>() }

    object Futures : Route(Tab.MARKET, "futures") { override val args = emptyList<String>() }
    object Marketing : Route(Tab.MARKET, "marketing") { override val args = emptyList<String>() }

    object Finance : Route(Tab.FINANCE, "finance") { override val args = emptyList<String>() }
    object Loans : Route(Tab.FINANCE, "loans") { override val args = emptyList<String>() }
    object Credit : Route(Tab.FINANCE, "credit") { override val args = emptyList<String>() }
    object Analysis : Route(Tab.FINANCE, "analysis") { override val args = emptyList<String>() }
    object Outlook : Route(Tab.FINANCE, "outlook") { override val args = emptyList<String>() }
    object Grants : Route(Tab.FINANCE, "grants") { override val args = emptyList<String>() }
    object Ip : Route(Tab.OPERATIONS, "ip") { override val args = emptyList<String>() }
    object Carbon : Route(Tab.FINANCE, "carbon") { override val args = emptyList<String>() }
    object Banking : Route(Tab.FINANCE, "banking") { override val args = emptyList<String>() }
    data class BankDetail(val bank: Long) : Route(Tab.FINANCE, "bank") { override val args = listOf(bank.toString()) }
    object BankStress : Route(Tab.FINANCE, "bank-stress") { override val args = emptyList<String>() }
    object FoundBank : Route(Tab.FINANCE, "found-bank") { override val args = emptyList<String>() }
    object Group : Route(Tab.FINANCE, "group") { override val args = emptyList<String>() }
    object Reports : Route(Tab.FINANCE, "reports") { override val args = emptyList<String>() }
    object Valuation : Route(Tab.FINANCE, "valuation") { override val args = emptyList<String>() }
    object CashForecast : Route(Tab.FINANCE, "cashforecast") { override val args = emptyList<String>() }
    object Statements : Route(Tab.FINANCE, "statements") { override val args = emptyList<String>() }
    object Ownership : Route(Tab.FINANCE, "ownership") { override val args = emptyList<String>() }

    object MoreMenu : Route(Tab.MORE, "more") { override val args = emptyList<String>() }
    object World : Route(Tab.MORE, "world") { override val args = emptyList<String>() }
    object Electricity : Route(Tab.MORE, "electricity") { override val args = emptyList<String>() }
    object Tourism : Route(Tab.MORE, "tourism") { override val args = emptyList<String>() }
    object Urban : Route(Tab.MORE, "urban") { override val args = emptyList<String>() }
    object UrbanCouncil : Route(Tab.MORE, "urban-council") { override val args = emptyList<String>() }
    object UrbanTransit : Route(Tab.MORE, "urban-transit") { override val args = emptyList<String>() }
    object UrbanBudget : Route(Tab.MORE, "urban-budget") { override val args = emptyList<String>() }
    object Multiplayer : Route(Tab.MORE, "multiplayer") { override val args = emptyList<String>() }
    data class Region(val region: Long) : Route(Tab.MORE, "region") { override val args = listOf(region.toString()) }
    object Scenario : Route(Tab.MORE, "scenario") { override val args = emptyList<String>() }
    object Achievements : Route(Tab.MORE, "achievements") { override val args = emptyList<String>() }
    object Activity : Route(Tab.MORE, "activity") { override val args = emptyList<String>() }
    object Agenda : Route(Tab.MORE, "agenda") { override val args = emptyList<String>() }
    object Export : Route(Tab.MORE, "export") { override val args = emptyList<String>() }
    object Government : Route(Tab.MORE, "government") { override val args = emptyList<String>() }
    object Rivals : Route(Tab.MORE, "rivals") { override val args = emptyList<String>() }
    data class Rival(val company: Long) : Route(Tab.MORE, "rival") { override val args = listOf(company.toString()) }
    object Saves : Route(Tab.MORE, "saves") { override val args = emptyList<String>() }
    object Settings : Route(Tab.MORE, "settings") { override val args = emptyList<String>() }
    object Help : Route(Tab.MORE, "help") { override val args = emptyList<String>() }
    object Glossary : Route(Tab.MORE, "glossary") { override val args = emptyList<String>() }

    override fun equals(other: Any?): Boolean = other is Route && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = key

    companion object {
        const val ARG_SEPARATOR = "/"

        /** The first screen of a tab. */
        fun root(tab: Tab): Route = when (tab) {
            Tab.OVERVIEW -> Overview
            Tab.OPERATIONS -> Facilities
            Tab.MARKET -> Market
            Tab.FINANCE -> Finance
            Tab.MORE -> MoreMenu
        }

        /** Rebuilds a route from [key]; null when the text is not a route this version knows. */
        fun parse(key: String): Route? {
            val parts = key.split(ARG_SEPARATOR)
            val args = parts.drop(1)
            fun number(i: Int): Long? = args.getOrNull(i)?.toLongOrNull()
            return when (parts[0]) {
                "overview" -> Overview
                "goals" -> Goals
                "guide" -> Guide
                "tutorial" -> Tutorial
                "advisor" -> Advisor
                "news" -> News
                "alerts" -> Alerts
                "facilities" -> Facilities
                "profit" -> FacilityProfit
                "facility" -> number(0)?.let { FacilityDetail(it) }
                "build" -> number(0)?.let { Build(it) }
                "people" -> People
                "unions" -> Unions
                "talent" -> Talent
                "talent-education" -> TalentEducation
                "talent-training" -> TalentTraining
                "talent-recruiting" -> TalentRecruiting
                "talent-careers" -> TalentCareers
                "talent-providers" -> TalentProviders
                "research" -> Research
                "logistics" -> Logistics
                "market" -> Market
                "good" -> args.singleOrNull()?.takeIf { it.isNotBlank() }?.let { Good(it) }
                "orders" -> Orders
                "rules" -> Rules
                "tenders" -> Tenders
                "contracts" -> Contracts
                "exchange" -> Exchange
                "stock" -> number(0)?.let { Stock(it) }
                "insurance" -> Insurance
                "bonds" -> Bonds
                "futures" -> Futures
                "marketing" -> Marketing
                "property" -> Property
                "finance" -> Finance
                "loans" -> Loans
                "credit" -> Credit
                "analysis" -> Analysis
                "outlook" -> Outlook
                "grants" -> Grants
                "carbon" -> Carbon
                "ip" -> Ip
                "banking" -> Banking
                "bank" -> number(0)?.let { BankDetail(it) }
                "bank-stress" -> BankStress
                "found-bank" -> FoundBank
                "group" -> Group
                "sectors" -> Sectors
                "reports" -> Reports
                "statements" -> Statements
                "cashforecast" -> CashForecast
                "valuation" -> Valuation
                "ownership" -> Ownership
                "more" -> MoreMenu
                "world" -> World
                "electricity" -> Electricity
                "tourism" -> Tourism
                "urban" -> Urban
                "urban-council" -> UrbanCouncil
                "urban-transit" -> UrbanTransit
                "urban-budget" -> UrbanBudget
                "multiplayer" -> Multiplayer
                "region" -> number(0)?.let { Region(it) }
                "scenario" -> Scenario
                "achievements" -> Achievements
                "activity" -> Activity
                "trade" -> Trade
                "watch" -> Watch
                "industries" -> Industries
                "blueprints" -> Blueprints
                "managers" -> Managers
                "planner" -> Planner
                "agenda" -> Agenda
                "export" -> Export
                "government" -> Government
                "rivals" -> Rivals
                "rival" -> number(0)?.let { Rival(it) }
                "saves" -> Saves
                "settings" -> Settings
                "help" -> Help
                "glossary" -> Glossary
                else -> null
            }
        }
    }
}

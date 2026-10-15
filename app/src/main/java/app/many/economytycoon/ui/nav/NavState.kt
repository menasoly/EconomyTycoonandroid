package app.many.economytycoon.ui.nav

/**
 * Navigation history with one stack per tab. Pure data and no framework, so every rule here is covered by
 * ordinary tests. All operations return a new state; the interface keeps the current one and swaps it.
 */
class NavState private constructor(
    val tab: Tab,
    private val stacks: Map<Tab, List<Route>>,
) {
    constructor(start: Tab = Tab.OVERVIEW) : this(start, Tab.values().associateWith { listOf(Route.root(it)) })

    val stack: List<Route> get() = stacks.getValue(tab)
    val current: Route get() = stack.last()
    val canGoUpInTab: Boolean get() = stack.size > 1

    /** Opens [route] on its own tab, above whatever is already there. A route already on top is not repeated. */
    fun open(route: Route): NavState {
        val target = route.tab
        val existing = stacks.getValue(target)
        val next = if (existing.last() == route) existing else existing + route
        return NavState(target, stacks + (target to next))
    }

    /** Switches tab. Choosing the tab you are on returns to its first screen. */
    fun select(tab: Tab): NavState =
        if (tab == this.tab) NavState(tab, stacks + (tab to listOf(Route.root(tab)))) else NavState(tab, stacks)

    /**
     * Goes back one screen. At the first screen of another tab it returns to the Overview tab; at the first
     * screen of Overview there is nowhere to go and null is returned so the caller can leave the game.
     */
    fun back(): NavState? = when {
        stack.size > 1 -> NavState(tab, stacks + (tab to stack.dropLast(1)))
        tab != Tab.OVERVIEW -> NavState(Tab.OVERVIEW, stacks)
        else -> null
    }

    /** Pops screens until [route] is on top. Does nothing when it is not in the current stack. */
    fun popTo(route: Route): NavState {
        val i = stack.lastIndexOf(route)
        return if (i < 0) this else NavState(tab, stacks + (tab to stack.take(i + 1)))
    }

    /** Replaces the top screen, for flows that finish and should not be returned to. */
    fun replaceTop(route: Route): NavState {
        if (route.tab != tab) return open(route)
        return NavState(tab, stacks + (tab to stack.dropLast(1) + route))
    }

    /** Drops every screen that refers to something that no longer exists, e.g. a demolished facility. */
    fun prune(exists: (Route) -> Boolean): NavState {
        val cleaned = stacks.mapValues { (t, list) ->
            val kept = list.filterIndexed { i, r -> i == 0 || exists(r) }
            kept.ifEmpty { listOf(Route.root(t)) }
        }
        return NavState(tab, cleaned)
    }

    fun title(): String = current.name

    /** Text form for saving across process death: `tab|stack;stack|...`. */
    fun encode(): String =
        tab.name + "#" + Tab.values().joinToString("|") { t -> stacks.getValue(t).joinToString(";") { it.key } }

    override fun equals(other: Any?): Boolean = other is NavState && other.encode() == encode()
    override fun hashCode(): Int = encode().hashCode()
    override fun toString(): String = encode()

    companion object {
        /** Restores a state from [encode]; anything unreadable gives a fresh state rather than a crash. */
        fun decode(text: String?): NavState {
            if (text.isNullOrBlank()) return NavState()
            val head = text.substringBefore('#', "")
            val body = text.substringAfter('#', "")
            val tab = Tab.values().firstOrNull { it.name == head } ?: return NavState()
            val parts = body.split('|')
            if (parts.size != Tab.values().size) return NavState()
            val stacks = HashMap<Tab, List<Route>>()
            for ((i, t) in Tab.values().withIndex()) {
                val routes = parts[i].split(';').mapNotNull { if (it.isBlank()) null else Route.parse(it) }
                val root = Route.root(t)
                val valid = routes.isNotEmpty() && routes.first() == root && routes.all { it.tab == t }
                stacks[t] = if (valid) routes else listOf(root)
            }
            return NavState(tab, stacks)
        }
    }
}

package app.many.economytycoon.ui.present

import app.many.economytycoon.achievements.Achievement
import app.many.economytycoon.achievements.AchievementCatalog
import app.many.economytycoon.achievements.AchievementEvaluator
import app.many.economytycoon.achievements.AchievementProgress
import app.many.economytycoon.achievements.Category
import app.many.economytycoon.achievements.Check
import app.many.economytycoon.achievements.Tier
import app.many.economytycoon.core.Money
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class AchievementItem(
    val id: String,
    val title: String,
    val description: String,
    val tier: String,
    val unlocked: Boolean,
    val progress: String,
    val ratio: Double,
    val tone: Tone,
)

data class AchievementGroup(val name: String, val items: List<AchievementItem>, val unlockedCount: Int)

data class AchievementsModel(
    val points: String,
    val summary: String,
    val groups: List<AchievementGroup>,
    val stats: List<Fact>,
)

object AchievementsPresenter {
    private val moneyKinds = setOf(GoalKind.NET_WORTH, GoalKind.CASH, GoalKind.MONTHLY_PROFIT, GoalKind.RENT_INCOME, GoalKind.INVESTED)

    fun tierTone(t: Tier): Tone = when (t) { Tier.GOLD -> Tone.GOOD; Tier.SILVER -> Tone.INFO; Tier.BRONZE -> Tone.WARN }

    private fun amount(check: Check, v: Long): String = when {
        check is Check.Meter && check.kind in moneyKinds -> Formatting.compactMoney(Money.units(v.coerceAtLeast(0L)))
        else -> Formatting.grouped(v.coerceAtLeast(0L))
    }

    private fun progressText(a: Achievement, p: AchievementProgress): String {
        if (p.done) return "Done"
        val c = a.check
        if (c is Check.Meter && p.target == 1L && c.kind == GoalKind.MARKET_LEADER) return "Not yet"
        val target = when {
            c is Check.Meter && c.kind in moneyKinds -> Formatting.compactMoney(Money.units(p.target))
            else -> Formatting.grouped(p.target)
        }
        return "${amount(c, p.value.coerceAtMost(p.target))} of $target"
    }

    fun item(a: Achievement, profile: PlayerProfile, meters: (GoalKind) -> Long): AchievementItem {
        val unlocked = profile.achievements.isUnlocked(a.id)
        val p = AchievementEvaluator.progress(a, profile, meters)
        return AchievementItem(
            a.id, a.title, a.description, a.tier.label, unlocked,
            if (unlocked) "Done" else progressText(a, p), if (unlocked) 1.0 else p.ratio,
            if (unlocked) tierTone(a.tier) else Tone.NEUTRAL,
        )
    }

    /** Everything on one screen. [meters] reads the running game; pass a reader of zero to show a profile alone. */
    fun build(profile: PlayerProfile, meters: (GoalKind) -> Long): AchievementsModel {
        val groups = Category.values().map { c ->
            val items = AchievementCatalog.inCategory(c).map { item(it, profile, meters) }
            AchievementGroup(c.label, items, items.count { it.unlocked })
        }.filter { it.items.isNotEmpty() }
        val total = AchievementCatalog.all.size
        val stats = profile.stats.nonZero().map { (s, v) -> Fact(s.label, Formatting.grouped(v)) }
        return AchievementsModel(
            "${profile.achievements.points()} of ${AchievementCatalog.maxPoints} points",
            "${profile.achievements.count} of $total unlocked",
            groups, stats,
        )
    }

    /** The model for a running game; null when no profile is attached to it. */
    fun build(session: GameSession): AchievementsModel? {
        val profile = session.achievements.profile ?: return null
        return build(profile) { session.achievements.meter(it) }
    }

    fun recent(profile: PlayerProfile, limit: Int = 3): List<String> =
        profile.achievements.all().entries.sortedByDescending { it.value }.take(limit).mapNotNull { AchievementCatalog.byId(it.key)?.title }
}

package app.many.economytycoon.ui.present

import app.many.economytycoon.credit.CreditExplainer
import app.many.economytycoon.credit.CreditExplanation
import app.many.economytycoon.credit.FactorState
import app.many.economytycoon.credit.FactorView
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.session.GameSession

data class FactorRow(
    val label: String,
    val points: String,
    val fillPpm: Long,
    val state: String,
    val tone: Tone,
    /** Shown only where there is something to improve. */
    val advice: String?,
)

data class CreditModel(
    val rating: String,
    val ratingTone: Tone,
    val headline: String,
    val score: String,
    val outlook: String?,
    val nextStep: String?,
    val cushion: String?,
    val factors: List<FactorRow>,
    val levers: List<String>,
)

/** Why the company's credit rating is what it is, and what would change it. */
object CreditPresenter {
    fun ratingTone(r: CreditRating): Tone = when {
        r.ordinal <= CreditRating.A.ordinal -> Tone.GOOD
        r == CreditRating.BBB -> Tone.INFO
        r == CreditRating.BB || r == CreditRating.B -> Tone.WARN
        else -> Tone.BAD
    }

    fun stateTone(s: FactorState): Tone = when (s) {
        FactorState.STRONG -> Tone.GOOD
        FactorState.FAIR -> Tone.INFO
        FactorState.WEAK -> Tone.WARN
    }

    fun stateLabel(s: FactorState): String = when (s) {
        FactorState.STRONG -> "Strong"
        FactorState.FAIR -> "Fair"
        FactorState.WEAK -> "Weak"
    }

    fun pointsText(p: Int): String = when {
        p > 0 -> "+$p"
        p < 0 -> "−${-p}"
        else -> "0"
    }

    fun headline(e: CreditExplanation): String = when {
        e.willRise -> "Rated ${e.heldRating.label} for now. Your figures already earn ${e.earned.label}."
        e.willFall -> "Rated ${e.heldRating.label} for now. Your figures only earn ${e.earned.label}."
        else -> "Rated ${e.heldRating.label}, in line with your figures."
    }

    fun outlook(e: CreditExplanation): String? = when {
        e.willRise -> "The rating moves up one step at a time after a couple of good reviews."
        e.willFall -> "The rating moves down one step at a time, a little quicker than it rises."
        else -> null
    }

    fun row(f: FactorView): FactorRow = FactorRow(
        label = f.factor.label, points = pointsText(f.points), fillPpm = f.fillPpm, state = stateLabel(f.state), tone = stateTone(f.state),
        advice = if (f.state == FactorState.STRONG || f.headroom <= 0) null else f.advice,
    )

    fun model(e: CreditExplanation): CreditModel = CreditModel(
        rating = e.heldRating.label,
        ratingTone = ratingTone(e.heldRating),
        headline = headline(e),
        score = "Score ${e.score} of 1000",
        outlook = outlook(e),
        nextStep = e.nextUp?.let { "${it.gap} more ${if (it.gap == 1) "point" else "points"} for ${it.rating.label}" },
        cushion = e.cushion?.let { "${it} ${if (it == 1) "point" else "points"} above the line for ${e.earned.label}" },
        factors = e.factors.map { row(it) },
        levers = e.levers().map { "${it.factor.label}: ${it.advice}" },
    )

    fun build(session: GameSession): CreditModel? = session.credit()?.let { model(it) }

    /** For tests and callers that already hold the explanation's inputs. */
    fun explain(inputs: app.many.economytycoon.finance.CreditInputs, held: CreditRating): CreditModel = model(CreditExplainer.explain(inputs, held))
}

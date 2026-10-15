package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.session.NewGameOptions
import app.many.economytycoon.simulation.SimTestSupport

object PresentSupport {
    fun session(name: String = "Presenter Works", seed: Long = 5L): GameSession =
        (NewGame.start(NewGameOptions(name, Difficulty.RELAXED, seed = seed), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false)) as Outcome.Ok).value
}

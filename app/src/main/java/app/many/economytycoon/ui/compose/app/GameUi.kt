package app.many.economytycoon.ui.compose.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.SessionChange
import app.many.economytycoon.session.SessionListener
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.nav.NavState
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.nav.Tab

/**
 * Compose-facing state for a running game. Screens read [revision] to recompose after every tick or command,
 * and call [issue] to act. Navigation lives in [nav], a plain value swapped on every move.
 */
@Stable
class GameUi(val session: GameSession, initialNav: NavState = NavState()) : SessionListener {
    var nav: NavState by mutableStateOf(initialNav)
        private set

    /** Bumped whenever the session reports a change; reading it subscribes a composable to updates. */
    var revision: Int by mutableStateOf(0)
        private set

    /** A short message shown at the bottom of the screen until dismissed. */
    var message: String? by mutableStateOf(null)
        private set

    var problems: List<String> by mutableStateOf(emptyList())
        private set

    init { session.addListener(this) }

    override fun onChanged(session: GameSession, change: SessionChange) { revision++ }

    fun dispose() { session.removeListener(this) }

    fun open(route: Route) { nav = nav.open(route) }

    fun select(tab: Tab) { nav = nav.select(tab) }

    /** Handles the system back gesture; false when there is nothing left to go back to. */
    fun back(): Boolean {
        val previous = nav.back() ?: return false
        nav = previous
        return true
    }

    fun showMessage(text: String?) { message = text }

    fun showProblems(list: List<String>) { problems = list }

    fun dismissProblems() { problems = emptyList() }

    /** Runs a command now. On refusal the reason is shown and false returned. */
    fun issue(command: Command, success: String? = null): Boolean = when (val r = session.issueNow(command)) {
        is Outcome.Ok -> { if (success != null) message = success; revision++; true }
        is Outcome.Rejected -> { message = r.rejection.message; false }
    }

    /** Queues a command for the next tick; used for bulk or low-urgency changes. */
    fun queue(command: Command) { session.issue(command); revision++ }

    fun encodeNav(): String = nav.encode()
}

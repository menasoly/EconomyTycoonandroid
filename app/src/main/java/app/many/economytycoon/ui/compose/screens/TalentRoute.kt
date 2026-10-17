package app.many.economytycoon.ui.compose.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.CareersPresenter
import app.many.economytycoon.ui.present.EducationPresenter
import app.many.economytycoon.ui.present.ProvidersPresenter
import app.many.economytycoon.ui.present.RecruitingPresenter
import app.many.economytycoon.ui.present.TalentPresenter
import app.many.economytycoon.ui.present.TrainingPresenter

/**
 * The people and skills screens: reads the selected company's view of the module once and hands it to the screen of
 * [route]. A button on a screen carries the order it stands for; this forwards it as the company's own command.
 */
@Composable
fun TalentRoute(ui: GameUi, route: Route) {
    val view = remember(ui.revision / 3, ui.session.tick.value, route) { ui.session.talentView() }
    val company = ui.session.selectedCompany
    if (view == null || company == null) {
        ScreenFrame("People and skills", onBack = { ui.back() }) {
            ScreenList { item { EmptyState("Nothing here yet", "Skills and education show up once you have a company and its people.") } }
        }
        return
    }
    val act: (TalentCommand) -> Unit = { cmd -> ui.issue(Command.Talent(company, cmd), "Done.") }
    val back = { ui.back(); Unit }
    when (route) {
        Route.TalentEducation -> TalentEducationScreen(EducationPresenter.build(view), act, back)
        Route.TalentTraining -> TalentTrainingScreen(TrainingPresenter.build(view), act, back)
        Route.TalentRecruiting -> TalentRecruitingScreen(RecruitingPresenter.build(view), act, back)
        Route.TalentCareers -> TalentCareersScreen(CareersPresenter.build(view), act, back)
        Route.TalentProviders -> TalentProvidersScreen(ProvidersPresenter.build(view), act, back)
        else -> TalentScreen(TalentPresenter.build(view), { ui.open(it) }, back)
    }
}

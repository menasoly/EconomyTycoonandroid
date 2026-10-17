package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.talent.Scenario
import app.many.economytycoon.talent.TalentReader
import app.many.economytycoon.talent.TalentView

/** Builds the views the presenter tests read, from a small scenario with one of everything. */
object TalentViews {
    val company = CompanyId(1)

    fun quiet(days: Int = 60, seed: Long = 9L): Pair<Scenario, TalentView> {
        val sc = Scenario(seed); sc.run(days)
        return sc to TalentReader(sc.system).view(company, Tick(sc.day), sc.inputs(), sc.world)
    }

    fun busy(seed: Long = 9L, who: CompanyId = company): Pair<Scenario, TalentView> {
        val sc = Scenario(seed); sc.run(20); sc.populate(); sc.run(40)
        return sc to TalentReader(sc.system).view(who, Tick(sc.day), sc.inputs(), sc.world)
    }
}

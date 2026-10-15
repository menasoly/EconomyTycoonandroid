package app.many.economytycoon.competitors

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId

/**
 * A decision an AI company wants carried out. The simulation executes each through the same validated
 * services a player uses, so a rejected action simply has no effect.
 */
sealed class AiAction {
    abstract val company: CompanyId

    data class Build(override val company: CompanyId, val type: FacilityTypeId, val region: RegionId) : AiAction()
    data class AddLine(override val company: CompanyId, val facility: FacilityId) : AiAction()
    data class SetRecipe(override val company: CompanyId, val facility: FacilityId, val line: LineId, val recipe: RecipeId) : AiAction()
    data class Shutdown(override val company: CompanyId, val facility: FacilityId) : AiAction()
    data class SetStaffing(override val company: CompanyId, val facility: FacilityId, val role: RoleId, val target: Long, val wage: Money) : AiAction()
    data class SetSell(override val company: CompanyId, val rule: SellRule) : AiAction()
    data class SetBuy(override val company: CompanyId, val rule: BuyRule) : AiAction()
    data class SetBudget(override val company: CompanyId, val department: Department, val perTick: Money) : AiAction()
    data class SetDividend(override val company: CompanyId, val policy: DividendPolicy) : AiAction()
    data class Borrow(override val company: CompanyId, val amount: Money, val termTicks: Int) : AiAction()
    data class Repay(override val company: CompanyId, val amount: Money) : AiAction()
    data class StartResearch(override val company: CompanyId, val tech: TechId) : AiAction()
    data class Bid(override val company: CompanyId, val target: CompanyId, val shares: Long, val pricePerShare: Money) : AiAction()
}

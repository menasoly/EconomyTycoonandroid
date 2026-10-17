package app.many.economytycoon.activity

import app.many.economytycoon.urban.UrbanCommand

/** Writes an order of the urban module as a sentence a player would recognise. */
object UrbanCommandDescriber {
    private fun pct(ppm: Long): String = if (ppm % 10_000L == 0L) "${ppm / 10_000L}%" else "%.1f%%".format(ppm / 10_000.0)

    fun describe(cmd: UrbanCommand, n: DescribeNames = DescribeNames.PLAIN): String {
        val city = n.city(cmd.city)
        return when (cmd) {
            is UrbanCommand.SetTaxRates -> "Set the taxes of $city: property ${pct(cmd.rates.propertyPerYearPpm)} a year, income ${pct(cmd.rates.incomePpm)}"
            is UrbanCommand.SetServiceLevel -> "Set ${cmd.service.label.lowercase()} in $city to level ${cmd.level}"
            is UrbanCommand.SetDensityRules -> "Changed the density limits of district ${cmd.district} in $city"
            is UrbanCommand.SetTenantProtection -> "Set tenant protection in district ${cmd.district} of $city to ${cmd.permille} of 1000"
            is UrbanCommand.AddRoad -> "Built a ${cmd.roadClass.label.lowercase()} of ${cmd.lanes} lanes between districts ${cmd.a} and ${cmd.b} of $city"
            is UrbanCommand.PlanLine -> "Planned the ${cmd.mode.label.lowercase()} line ${cmd.name} in $city"
            is UrbanCommand.SetLineHeadway -> "Set the headway of line ${cmd.line.value} in $city to ${cmd.headwaySeconds} seconds"
            is UrbanCommand.SetLineFare -> "Set the fare of line ${cmd.line.value} in $city to ${n.money(cmd.fare)}"
            is UrbanCommand.ToggleLine -> "Suspended or restored line ${cmd.line.value} in $city"
            is UrbanCommand.StartProject -> "Started the ${cmd.kind.label.lowercase()} ${cmd.name} in $city"
            is UrbanCommand.CancelProject -> "Cancelled project ${cmd.project.value} in $city"
            is UrbanCommand.Borrow -> "Borrowed ${n.money(cmd.amount)} for $city"
            is UrbanCommand.Repay -> "Repaid ${n.money(cmd.amount)} of the debt of $city"
            is UrbanCommand.TenderConcession -> "Put the ${cmd.kind.label.lowercase()} concession ${cmd.name} in $city out to tender"
            is UrbanCommand.TerminateConcession -> "Ended concession ${cmd.concession.value} in $city"
            is UrbanCommand.FilePetition -> "File ${cmd.kind.label.lowercase()} petition for district ${cmd.district} of $city (${cmd.toZone.label.lowercase()})"
            is UrbanCommand.WithdrawPetition -> "Withdrew petition ${cmd.petition.value} in $city"
            is UrbanCommand.LobbyPetition -> "Spent ${n.money(cmd.amount)} lobbying for petition ${cmd.petition.value} in $city"
            is UrbanCommand.BidConcession -> "Bid for concession ${cmd.concession.value} in $city with ${pct(cmd.discountPpm)} off"
        }
    }

    /** True for orders that spend a company's money straight away. */
    fun movesMoney(cmd: UrbanCommand): Boolean = when (cmd) {
        is UrbanCommand.FilePetition -> cmd.applicant != null
        is UrbanCommand.LobbyPetition -> true
        else -> false
    }
}

package app.many.economytycoon.session

import app.many.economytycoon.companies.Consolidation
import app.many.economytycoon.consolidation.GroupStatements
import app.many.economytycoon.consolidation.MemberStatements
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Economy

/** Reads a company and everything it controls into one set of group statements. */
class GroupReader(private val e: Economy, private val reports: SessionReports) {
    /** Companies the given one controls, directly or through others. Empty for a stand-alone company. */
    fun subsidiariesOf(id: CompanyId): List<CompanyId> = e.companies.groups.descendants(id)

    fun hasGroup(id: CompanyId): Boolean = subsidiariesOf(id).isNotEmpty()

    fun read(id: CompanyId): GroupStatements? {
        if (e.companies.company(id) == null) return null
        val interests = Consolidation.interests(e.companies.groups, id)
        val members = ArrayList<MemberStatements>()
        for (member in e.companies.groups.group(id).sortedBy { if (it == id) -1L else it.value }) {
            val c = e.companies.company(member) ?: continue
            val pack = reports.company.finance(member) ?: continue
            members += MemberStatements(member, c.name, interests[member] ?: continue, pack.month, pack.balance)
        }
        return GroupStatements.of(id, members)
    }
}

package app.many.economytycoon.ip

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

data class UnprotectedFact(val name: String, val advantageUnits: Long, val choice: Protection)
data class RenewalFact(val title: String, val daysAway: Long, val feeUnits: Long, val autoMaintain: Boolean, val canPay: Boolean)
data class ExpiringFact(val title: String, val daysLeft: Long, val licensedOut: Boolean)
data class UserFact(val title: String, val user: CompanyId, val salesUnits: Long)
data class CaseFact(val dispute: DisputeId, val title: String, val stage: DisputeStage, val winPpm: Long, val exposureUnits: Long, val zoneOfAgreement: Boolean, val byNpe: Boolean, val kind: DisputeKind)
data class SecretFact(val name: String, val halfLifeDays: Long, val protectionPpm: Long, val knowers: Int)
data class ArrearsFact(val licensee: CompanyId, val owedUnits: Long, val strikes: Int)
data class AuditFact(val license: LicenseId, val licensee: CompanyId, val daysSinceAudit: Long, val yearlyIncomeUnits: Long)

/** What the intellectual property rules look at, as plain numbers, so a rule can be tried on any situation by writing them down. */
data class IpFacts(
    val tick: Long = 0L,
    val cashUnits: Long = 0L,
    val unprotected: List<UnprotectedFact> = emptyList(),
    val renewals: List<RenewalFact> = emptyList(),
    val expiring: List<ExpiringFact> = emptyList(),
    val unlicensedUsers: List<UserFact> = emptyList(),
    val casesAgainst: List<CaseFact> = emptyList(),
    val casesBy: List<CaseFact> = emptyList(),
    val secretsAtRisk: List<SecretFact> = emptyList(),
    val arrears: List<ArrearsFact> = emptyList(),
    val auditsDue: List<AuditFact> = emptyList(),
    val offersWaiting: Int = 0,
    val idlePatents: Int = 0,
    val royaltyBurdenPpm: Long = 0L,
    val researchSpendUnits: Long = 0L,
    val creditPpm: Long = 0L,
)

data class IpAdvisorTuning(
    val renewalWindowDays: Long = 60L,
    val expiryWindowDays: Long = 360L,
    val secretHalfLifeWorryDays: Long = 360L,
    val heavyBurdenPpm: Long = 150_000L,
    val strongCasePpm: Long = 600_000L,
    val auditAfterDays: Long = 360L,
    val auditWorthUnits: Long = 20_000L,
)

fun interface IpRule {
    fun check(f: IpFacts, t: IpAdvisorTuning): List<Insight>
}

/** Advice on the company's patents, secrets, licences and disputes. */
object IpAdvisor {
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"
    private fun one(i: Insight?): List<Insight> = if (i == null) emptyList() else listOf(i)
    private fun units(v: Long): String {
        val digits = Math.abs(v).toString()
        val sb = StringBuilder()
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return if (v < 0) "-$sb" else sb.toString()
    }

    val unprotected = IpRule { f, _ ->
        one(
            if (f.unprotected.isNotEmpty()) {
                val top = f.unprotected.maxByOrNull { it.advantageUnits }!!
                Insight(
                    "ip-unprotected", Topic.RESEARCH, (55 + f.unprotected.size).coerceAtMost(75), "${plural(f.unprotected.size, "technology", "technologies")} with no protection",
                    "${top.name} brings in the most and is open to copying. ${if (top.choice == Protection.PATENT) "A patent looks the better choice." else if (top.choice == Protection.SECRET) "Keeping it secret looks the better choice." else "Neither route pays for itself yet."}",
                )
            } else null,
        )
    }

    val renewals = IpRule { f, t ->
        val due = f.renewals.filter { it.daysAway <= t.renewalWindowDays }
        val cannotPay = due.filter { !it.canPay }
        when {
            cannotPay.isNotEmpty() -> listOf(
                Insight("ip-renewal-unpaid", Topic.FINANCE, 85, "Cannot pay to keep ${plural(cannotPay.size, "patent")} alive", "${cannotPay.first().title} needs ${units(cannotPay.first().feeUnits)} within ${cannotPay.first().daysAway} days. A patent that lapses is gone for good."),
            )
            due.any { !it.autoMaintain } -> {
                val m = due.first { !it.autoMaintain }
                listOf(Insight("ip-renewal", Topic.RESEARCH, 68, "A renewal fee is coming up", "${m.title} needs ${units(m.feeUnits)} in ${m.daysAway} days and automatic renewal is off. Pay it or let it go knowingly."))
            }
            else -> emptyList()
        }
    }

    val expiring = IpRule { f, t ->
        val soon = f.expiring.filter { it.daysLeft <= t.expiryWindowDays }
        one(if (soon.isNotEmpty()) Insight("ip-expiring", Topic.RESEARCH, 40, "${plural(soon.size, "patent")} about to expire", "${soon.first().title} has ${soon.first().daysLeft} days left. Licence income ends with it, and rivals may then use the technology freely.") else null)
    }

    val unlicensedUsers = IpRule { f, _ ->
        one(
            if (f.unlicensedUsers.isNotEmpty()) {
                val u = f.unlicensedUsers.maxByOrNull { it.salesUnits }!!
                Insight("ip-unlicensed", Topic.MARKET, 65, "${plural(f.unlicensedUsers.size, "rival")} using your patents without a licence", "Company ${u.user.value} sold ${units(u.salesUnits)} with ${u.title}. Offer a licence, or accuse them if they refuse.")
            } else null,
        )
    }

    val casesAgainst = IpRule { f, t ->
        val out = ArrayList<Insight>()
        val npe = f.casesAgainst.firstOrNull { it.byNpe && it.stage == DisputeStage.NOTICE }
        if (npe != null) out += Insight("ip-npe-demand", Topic.FINANCE, 80, "A patent holder that makes nothing wants money", "${npe.title}: the demand is cheaper than a defence, but paying invites more. Weigh a settlement against the ${npe.winPpm / 10_000}% chance of losing in court.")
        val strong = f.casesAgainst.filter { it.winPpm >= t.strongCasePpm }
        if (strong.isNotEmpty()) {
            val c = strong.maxByOrNull { it.exposureUnits }!!
            out += Insight("ip-case-losing", Topic.FINANCE, 84, "You are likely to lose a patent case", "${c.title}: about ${c.winPpm / 10_000}% against you, ${units(c.exposureUnits)} at stake. ${if (c.zoneOfAgreement) "A settlement now costs less than losing." else "Consider designing around the patent."}")
        } else if (f.casesAgainst.any { it.kind == DisputeKind.MISAPPROPRIATION }) {
            out += Insight("ip-theft-case", Topic.FINANCE, 70, "You are accused of taking a trade secret", "Proof decides these cases. Settle if the evidence is against you.")
        }
        out
    }

    val casesBy = IpRule { f, _ ->
        val open = f.casesBy.filter { it.zoneOfAgreement && it.stage.ordinal >= DisputeStage.FILED.ordinal }
        one(if (open.isNotEmpty()) Insight("ip-settle", Topic.FINANCE, 52, "A settlement could end ${plural(open.size, "case")} sooner", "${open.first().title} costs both sides more in court than a deal would. A fair offer ends the fees.") else null)
    }

    val secrets = IpRule { f, t ->
        val risky = f.secretsAtRisk.filter { it.halfLifeDays < t.secretHalfLifeWorryDays }
        one(if (risky.isNotEmpty()) Insight("ip-secret-risk", Topic.RESEARCH, 62, "${plural(risky.size, "trade secret")} unlikely to last a year", "${risky.first().name} is more likely out than kept within ${risky.first().halfLifeDays} days. Spend on security, or file a patent while it is still yours alone.") else null)
    }

    val arrears = IpRule { f, _ ->
        one(if (f.arrears.isNotEmpty()) Insight("ip-arrears", Topic.FINANCE, 66, "${plural(f.arrears.size, "licensee")} behind on royalties", "Company ${f.arrears.first().licensee.value} owes ${units(f.arrears.first().owedUnits)}. Two missed periods end the licence.") else null)
    }

    val audits = IpRule { f, t ->
        val due = f.auditsDue.filter { it.daysSinceAudit >= t.auditAfterDays && it.yearlyIncomeUnits >= t.auditWorthUnits }
        one(if (due.isNotEmpty()) Insight("ip-audit", Topic.FINANCE, 45, "A royalty audit may pay for itself", "Licence ${due.first().license.value} brings in ${units(due.first().yearlyIncomeUnits)} a year and has not been checked for ${due.first().daysSinceAudit} days.") else null)
    }

    val offers = IpRule { f, _ ->
        one(if (f.offersWaiting > 0) Insight("ip-offers", Topic.MARKET, 50, "${plural(f.offersWaiting, "licence offer")} waiting for an answer", "Offers lapse after a month. Accept to use the technology lawfully, or decline.") else null)
    }

    val idle = IpRule { f, _ ->
        one(if (f.idlePatents >= 2) Insight("ip-idle", Topic.RESEARCH, 35, "${f.idlePatents} patents earn nothing", "They cost renewal fees every year. License them, sell them or let them go.") else null)
    }

    val burden = IpRule { f, t ->
        one(if (f.royaltyBurdenPpm >= t.heavyBurdenPpm) Insight("ip-royalty-burden", Topic.FINANCE, 55, "Royalties take ${f.royaltyBurdenPpm / 10_000}% of sales", "Stacked licences are heavy. Join a pool, negotiate a cross-licence or design around the dearest one.") else null)
    }

    val credit = IpRule { f, _ ->
        one(if (f.researchSpendUnits > 0L && f.creditPpm > 0L) Insight("ip-credit", Topic.RESEARCH, 25, "Research spending earns a tax credit", "Your country returns ${f.creditPpm / 10_000}% of qualifying research spending at the start of each year.") else null)
    }

    val all: List<IpRule> = listOf(unprotected, renewals, expiring, unlicensedUsers, casesAgainst, casesBy, secrets, arrears, audits, offers, idle, burden, credit)

    fun advise(f: IpFacts, t: IpAdvisorTuning = IpAdvisorTuning(), rules: List<IpRule> = all): List<Insight> =
        rules.flatMap { it.check(f, t) }.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code })

    /** Reads the live engine into [IpFacts] for one company. */
    fun facts(engine: IpEngine, run: IpRun, company: CompanyId): IpFacts {
        val state = engine.state
        val me = IpOwner.company(company)
        val tick = run.tick
        val titles = { id: PatentId? -> id?.let { state.patentMap[it]?.title } ?: "a patent" }
        val cash = run.ctx.cash(me)
        val mine = state.patentsOf(me).filter { it.isLive }
        val renewals = ArrayList<RenewalFact>()
        val expiring = ArrayList<ExpiringFact>()
        for (p in mine) {
            for (f in p.filingMap.values) {
                val due = f.nextFeeDue
                if (f.status == FilingStatus.GRANTED && due != null) {
                    val fee = PatentRules.maintenanceFee(engine.params.office, engine.office.regimeOf(f.country), f.feeYearsPaid + 1)
                    renewals += RenewalFact(p.title, (due.value - tick.value).coerceAtLeast(0L), fee.raw / Money.SCALE, p.autoMaintain, cash >= fee)
                }
            }
            val left = engine.office.remainingDays(p, tick)
            if (left > 0L) expiring += ExpiringFact(p.title, left, state.licenseMap.values.any { it.isActive && it.licensor == me && p.id in it.patents })
        }
        val caseFact = { d: Dispute ->
            val e = engine.disputes.estimate(run, d)
            CaseFact(d.id, titles(d.patent), d.stage, e.claimantWinPpm, e.awardIfWon.raw / Money.SCALE, e.zoneOfAgreement, d.claimant.isNpe, d.kind)
        }
        val open = state.disputesOf(me).filter { it.isOpen }
        val licenses = state.licenseMap.values
        val secretsAtRisk = state.secretsOf(company).filter { it.isLive }.map { s ->
            val h = engine.secrets.hazardPpm(s)
            SecretFact(engine.content.technologyOrNull(s.tech)?.name ?: s.tech.key, if (h <= 0L) Long.MAX_VALUE / 4 else 693_147L / h, s.protectionPpm, s.knowers)
        }
        val unlicensed = state.patentsOf(me).filter { it.isLive }.flatMap { p ->
            p.countriesInForce(tick).flatMap { c ->
                run.ctx.practicing(p.tech, c, Tick((tick.value - engine.params.litigation.lookbackDays).coerceAtLeast(0L)), tick)
                    .filter { it.company != company && !engine.disputes.mayPractise(it.company, p, c, tick) }
                    .map { UserFact(p.title, it.company, it.usage.sales.raw / Money.SCALE) }
            }
        }
        val burden = run.ctx.knownTechs(company).maxOfOrNull { t ->
            val country = run.ctx.homeCountry(company)
            if (country == null) 0L else engine.effects.royaltyStackPpm(company, t, country, null, tick)
        } ?: 0L
        val country = run.ctx.homeCountry(company)
        val spend = country?.let { run.ctx.qualifyingResearchSpend(company, Tick((tick.value - Calendar.DAYS_PER_YEAR).coerceAtLeast(0L)), tick) } ?: Money.ZERO
        return IpFacts(
            tick = tick.value,
            cashUnits = cash.raw / Money.SCALE,
            unprotected = engine.planner.adviceFor(run, company).map { UnprotectedFact(it.name, it.annualAdvantage.raw / Money.SCALE, it.advice.choice) },
            renewals = renewals.sortedBy { it.daysAway },
            expiring = expiring.sortedBy { it.daysLeft },
            unlicensedUsers = unlicensed,
            casesAgainst = open.filter { it.defendant == me }.map(caseFact),
            casesBy = open.filter { it.claimant == me }.map(caseFact),
            secretsAtRisk = secretsAtRisk,
            arrears = licenses.filter { it.licensor == me && it.isActive && it.arrears.raw > 0L }.map { ArrearsFact(it.licensee, it.arrears.raw / Money.SCALE, it.strikes) },
            auditsDue = licenses.filter { it.licensor == me && it.isActive && it.terms.isRecurring }.map { l ->
                AuditFact(l.id, l.licensee, tick.value - (l.lastAuditAt?.value ?: l.startsAt?.value ?: tick.value), l.paidTotal.raw / Money.SCALE * Calendar.DAYS_PER_YEAR / maxOf(1L, tick.value - (l.startsAt?.value ?: tick.value)))
            },
            offersWaiting = licenses.count { it.isOffer && it.licensee == company },
            idlePatents = mine.count { p -> licenses.none { it.isActive && it.licensor == me && p.id in it.patents } && !run.ctx.isWorkedLocally(company, p.tech, p.countriesInForce(tick).firstOrNull() ?: return@count false) },
            royaltyBurdenPpm = burden,
            researchSpendUnits = spend.raw / Money.SCALE,
            creditPpm = country?.let { engine.office.regimeOf(it).rdCreditPpm } ?: 0L,
        )
    }
}

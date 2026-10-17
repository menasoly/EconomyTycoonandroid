package app.many.economytycoon.session

import app.many.economytycoon.companies.CasePhase
import app.many.economytycoon.companies.OfferState
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Economy

enum class Severity { INFO, WARNING, CRITICAL }

/**
 * Something the player should look at. [code] is stable so screens can route to the right place and so a
 * dismissed alert is recognised again; [subject] distinguishes several alerts of the same kind.
 */
data class Alert(
    val code: String,
    val subject: Long,
    val severity: Severity,
    val title: String,
    val detail: String,
)

/** Looks over one company and lists what needs attention, worst first. */
class AlertScanner(private val e: Economy, private val thresholds: AlertThresholds = AlertThresholds()) {

    fun scan(company: CompanyId): List<Alert> {
        val c = e.companies.company(company) ?: return emptyList()
        if (!c.isActive) return emptyList()
        val out = ArrayList<Alert>()
        cash(company, out)
        distress(company, out)
        loans(company, out)
        workforce(company, out)
        facilities(company, out)
        offers(company, out)
        insolvency(company, out)
        research(company, out)
        contracts(company, out)
        portfolio(company, out)
        cover(company, out)
        tenders(company, out)
        grants(company, out)
        marketing(company, out)
        energy(company, out)
        carbon(company, out)
        banks(company, out)
        regulation(company, out)
        bonds(company, out)
        unions(company, out)
        futures(company, out)
        property(company, out)
        return out.sortedWith(compareByDescending<Alert> { it.severity }.thenBy { it.code }.thenBy { it.subject })
    }

    private fun cash(id: CompanyId, out: MutableList<Alert>) {
        val owner = e.companies.require(id).owner
        val cash = e.finance.ledger.cash(owner)
        val now = e.tick
        val month = e.finance.statements.income(owner, Tick((now.value - 30).coerceAtLeast(0L)), now)
        val burnPerDay = (month.operatingExpenses + month.cogs - month.revenue).raw / 30L
        if (cash.raw < 0) {
            out += Alert("cash.negative", 0, Severity.CRITICAL, "Overdrawn", "Cash is ${Formatting.money(cash)}.")
        } else if (burnPerDay > 0) {
            val days = cash.raw / burnPerDay
            if (days < thresholds.criticalRunwayDays) {
                out += Alert("cash.runway", 0, Severity.CRITICAL, "Cash runs out in ${Formatting.plural(days, "day")}", "Spending exceeds income by ${Formatting.money(Money(burnPerDay))} a day.")
            } else if (days < thresholds.warningRunwayDays) {
                out += Alert("cash.runway", 0, Severity.WARNING, "Cash covers ${days} days of losses", "Spending exceeds income by ${Formatting.money(Money(burnPerDay))} a day.")
            }
        }
    }

    private fun distress(id: CompanyId, out: MutableList<Alert>) {
        val state = e.finance.company(e.companies.require(id).owner)?.distress?.state ?: return
        when (state) {
            DistressState.WATCH -> out += Alert("distress", 0, Severity.INFO, "Lenders are watching you", "Financial ratios are weakening.")
            DistressState.DISTRESSED -> out += Alert("distress", 0, Severity.WARNING, "Financial distress", "Ratios breach the limits lenders accept.")
            DistressState.INSOLVENT -> out += Alert("distress", 0, Severity.CRITICAL, "Insolvent", "Liabilities exceed what the company can pay.")
            else -> Unit
        }
    }

    private fun loans(id: CompanyId, out: MutableList<Alert>) {
        val owner = e.companies.require(id).owner
        for (l in e.finance.loans.forBorrower(owner)) {
            if (l.status == LoanStatus.DEFAULTED) {
                out += Alert("loan.default", l.id.value, Severity.CRITICAL, "Loan in default", "${Formatting.money(l.outstanding)} is overdue.")
            } else if (l.status == LoanStatus.ACTIVE && l.missedPayments > 0) {
                out += Alert("loan.missed", l.id.value, Severity.WARNING, "Missed loan payment", "${Formatting.plural(l.missedPayments.toLong(), "payment")} behind on a ${Formatting.money(l.outstanding)} loan.")
            }
        }
    }

    private fun workforce(id: CompanyId, out: MutableList<Alert>) {
        val s = e.workforce.stats(id, e.tick)
        if (s.unpaidTicks > 0) {
            out += Alert("wages.unpaid", 0, Severity.CRITICAL, "Wages unpaid", "Staff have gone ${Formatting.plural(s.unpaidTicks.toLong(), "day")} without pay.")
        }
        if (s.onStrike > 0) {
            out += Alert("strike", 0, Severity.WARNING, "Strike", "${Formatting.grouped(s.onStrike)} workers are on strike.")
        }
        if (s.headcount > 0 && s.averageMoralePpm < thresholds.lowMoralePpm) {
            out += Alert("morale", 0, Severity.WARNING, "Low morale", "Average morale is ${Formatting.percentPpm(s.averageMoralePpm)}.")
        }
        if (s.vacancies > 0 && s.vacancies * 4 > s.headcount + 4) {
            out += Alert("vacancies", 0, Severity.INFO, "Many vacancies", "${Formatting.grouped(s.vacancies)} posts are unfilled.")
        }
    }

    private fun facilities(id: CompanyId, out: MutableList<Alert>) {
        for (f in e.production.facilitiesOf(id)) {
            val name = f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }
            if (f.status == FacilityStatus.SHUTDOWN) {
                out += Alert("facility.shutdown", f.id.value, Severity.INFO, "$name is shut down", "It costs upkeep without producing.")
            }
            if (f.status == FacilityStatus.OPERATING && f.conditionPpm < thresholds.poorConditionPpm) {
                out += Alert("facility.condition", f.id.value, Severity.WARNING, "$name is run down", "Condition is ${Formatting.percentPpm(f.conditionPpm)}; output is falling.")
            }
            if (f.pollutionPpm > thresholds.pollutionPpm) {
                out += Alert("facility.pollution", f.id.value, Severity.WARNING, "$name is polluting", "Regulators will intervene if it keeps rising.")
            }
        }
    }

    private fun offers(id: CompanyId, out: MutableList<Alert>) {
        for (o in e.acquisitions.pendingFor(id)) {
            if (o.state != OfferState.PENDING) continue
            val bidder = e.companies.company(o.acquirer)?.name ?: "A company"
            out += Alert("offer.takeover", o.id, Severity.WARNING, "Takeover bid from $bidder", "${Formatting.plural(o.shares, "share")} at ${Formatting.money(o.pricePerShare)} each.")
        }
    }

    private fun insolvency(id: CompanyId, out: MutableList<Alert>) {
        val case = e.insolvency.case(id) ?: return
        when (case.phase) {
            CasePhase.ASSESSMENT -> out += Alert("insolvency", 0, Severity.CRITICAL, "In administration", "An administrator is assessing the company.")
            CasePhase.RESTRUCTURING -> out += Alert("insolvency", 0, Severity.CRITICAL, "Restructuring", "Creditors are standing still while debts are reworked.")
            CasePhase.LIQUIDATION -> out += Alert("insolvency", 0, Severity.CRITICAL, "Being liquidated", "Assets are being sold to repay creditors.")
            CasePhase.CLOSED -> Unit
        }
    }

    private fun portfolio(id: CompanyId, out: MutableList<Alert>) {
        for (h in e.exchangeRunner.portfolio(id)) {
            if (h.cost.raw <= 0L) continue
            val lossPpm = (h.cost.raw - h.value.raw) * 1_000_000L / h.cost.raw
            if (lossPpm >= thresholds.portfolioLossPpm) {
                val name = e.companies.company(h.issuer)?.name ?: "a company"
                out += Alert("portfolio.loss", h.issuer.value, Severity.INFO, "Your shares in $name are down", "They are worth ${Formatting.percentPpm(lossPpm)} less than you paid.")
            }
        }
    }

    private fun unions(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick
        for (d in e.unions.openDemandsFor(id)) {
            val u = e.unions.get(d.union) ?: continue
            val left = (d.deadline.value - now.value).coerceAtLeast(0L)
            val region = e.world.regions().firstOrNull { it.id == u.region }?.name ?: "a region"
            val sev = if (left <= thresholds.demandUrgentDays) Severity.CRITICAL else Severity.WARNING
            out += Alert("union.demand", d.id, sev, "Workers in $region want a ${Formatting.percentPpm(d.raisePpm)} raise", "Answer within ${Formatting.plural(left, "day")} or they may strike.")
        }
        for (u in e.unions.ofCompany(id)) {
            val until = u.strikeUntil ?: continue
            if (!u.onStrike(now)) continue
            val region = e.world.regions().firstOrNull { it.id == u.region }?.name ?: "a region"
            out += Alert("union.strike", u.id.value, Severity.WARNING, "Union strike in $region", "Work resumes in ${Formatting.plural((until.value - now.value).coerceAtLeast(0L), "day")}, or sooner if you settle.")
        }
    }

    private fun property(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick
        for (l in e.property.leasesOf(id)) {
            val title = e.world.land.get(l.parcel)?.let { "${it.effectiveZone.name.lowercase().replaceFirstChar { c -> c.uppercase() }} plot" } ?: "A plot"
            if (l.missed > 0) {
                out += Alert("property.rent", l.id, Severity.WARNING, "$title: rent is late", "The tenant missed ${Formatting.plural(l.missed.toLong(), "payment")}. A second miss ends the lease.")
            } else if (l.endsAt.value - now.value in 0..thresholds.propertyNoticeDays) {
                out += Alert("property.lease", l.id, Severity.INFO, "$title: lease ends soon", "It ends in ${Formatting.plural((l.endsAt.value - now.value).coerceAtLeast(0L), "day")}.")
            }
        }
        for (l in e.property.listingsOf(id)) {
            val left = l.expiresAt.value - now.value
            if (left in 0..thresholds.propertyNoticeDays / 2) {
                out += Alert("property.listing", l.parcel.value, Severity.INFO, "Your land listing is about to lapse", "It comes down in ${Formatting.plural(left, "day")} unless you sell.")
            }
        }
    }

    private fun futures(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick
        for (p in e.futures.openOf(id)) {
            val name = e.content.good(p.good).name
            if (p.calledAt != null) {
                out += Alert("futures.call", p.id.value, Severity.CRITICAL, "Margin call on $name futures", "Add ${e.futures.shortfall(p)} or the position will be closed out.")
            } else if (e.futures.daysLeft(p, now) <= thresholds.futuresExpiryDays) {
                out += Alert("futures.expiring", p.id.value, Severity.INFO, "$name futures expire soon", "The position settles in cash in ${Formatting.plural(e.futures.daysLeft(p, now).toLong(), "day")}.")
            }
        }
    }

    private fun bonds(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick
        for (b in e.bonds.heldBy(id)) {
            val left = b.maturesAt.value - now.value
            if (left > thresholds.bondNoticeDays) continue
            val face = Money(b.faceEach.raw * b.heldBy(id))
            out += Alert("bond.matures", b.id.value, Severity.INFO, "Bonds mature in ${Formatting.plural(left.coerceAtLeast(0L), "day")}", "You will be repaid ${Formatting.money(face)} face value.")
        }
        val cash = e.finance.ledger.cash(e.companies.require(id).owner)
        for (b in e.bonds.open()) {
            if (b.heldBy(id) > 0L) continue
            val left = b.subscriptionEnds.value - now.value
            val cost = Money(b.faceEach.raw * b.unitsLeft.coerceAtMost(1L))
            if (cash < cost * thresholds.bondCashCover) continue
            val country = e.world.countries().firstOrNull { it.id == b.country }?.name ?: "A government"
            out += Alert("bond.open", b.id.value, Severity.INFO, "$country is selling bonds at ${Formatting.percentPpm(b.couponPpm)}", "Subscriptions close in ${Formatting.plural(left.coerceAtLeast(0L), "day")}.")
        }
    }

    private fun regulation(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick
        val regions = e.production.facilitiesOf(id).map { it.region }.toSet()
        val countries = regions.map { e.world.region(it).country }.toSet()
        for (law in e.regulation.laws().filter { it.isOpen && it.country in countries }) {
            val left = law.voteAt.value - now.value
            if (left > thresholds.lawNoticeDays) continue
            val side = when {
                law.backedBy(id).raw > 0L -> "You are backing it."
                law.opposedBy(id).raw > 0L -> "You are opposing it."
                else -> "You can still lobby for or against it."
            }
            out += Alert("law.vote", law.id.value, Severity.INFO, "Parliament votes on ${Formatting.lawSummary(law)} in ${Formatting.plural(left.coerceAtLeast(0L), "day")}", side)
        }
        for (fine in e.regulation.finesOf(id)) {
            val age = now.value - fine.tick.value
            if (age > thresholds.fineNoticeDays) continue
            val name = e.production.facility(fine.facility)?.name?.ifBlank { null } ?: "A facility"
            if (fine.closed) out += Alert("fine.closed", fine.facility.value, Severity.CRITICAL, "$name was shut for pollution", "Three fines in half a year close a plant. Dispose of waste before reopening it.")
            else out += Alert("fine.issued", fine.facility.value, Severity.WARNING, "$name was fined ${Formatting.money(fine.amount)}", "Pollution is above what the law allows. More fines will close it.")
        }
    }

    private fun tenders(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick.value
        for (t in e.tenders.awardedTo(id)) {
            val left = t.deliverBy.value - now
            val f = t.winnerFacility?.let { e.production.facility(it) }
            val have = f?.inventory?.available(t.good) ?: app.many.economytycoon.core.Quantity.ZERO
            val name = e.content.goodOrNull(t.good)?.name ?: t.good.key
            if (have >= t.quantity) {
                out += Alert("tender.ready", t.id.value, Severity.INFO, "Tender for $name is ready", "The stock is in place and will be delivered on the next day.")
            } else if (left <= thresholds.tenderWarnDays) {
                out += Alert("tender.late", t.id.value, Severity.CRITICAL, "Tender for $name is due in ${Formatting.plural(left.coerceAtLeast(0L), "day")}", "You hold ${Formatting.quantity(have)} of ${Formatting.quantity(t.quantity)}; a miss costs a penalty.")
            } else {
                out += Alert("tender.won", t.id.value, Severity.WARNING, "You won a tender for $name", "Deliver ${Formatting.quantity(t.quantity)} within ${Formatting.plural(left, "day")}.")
            }
        }
        if (e.tenders.isBarred(id, e.tick)) out += Alert("tender.barred", 0, Severity.WARNING, "Barred from public tenders", "Repeated failures to deliver have cost you the right to bid for now.")
    }

    private fun grants(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick.value
        for (g in e.grants.open()) {
            if (g.applicationOf(id) != null) continue
            val left = g.applyUntil.value - now
            if (left > thresholds.grantNoticeDays) continue
            val regions = e.world.regionsOf(g.country).map { it.id }.toSet()
            if (e.production.facilitiesOf(id).none { it.region in regions }) continue
            val what = if (g.kind == app.many.economytycoon.grants.GrantKind.JOBS) "new jobs" else "new ${g.family?.displayName?.lowercase() ?: ""} facilities"
            out += Alert("grant.window", g.id.value, Severity.INFO, "A grant for $what closes in ${Formatting.plural(left.coerceAtLeast(0L), "day")}", "Applying is free. You are paid ${Formatting.money(g.rate)} for each, up to ${Formatting.money(g.cap)}.")
        }
        for (g in e.grants.active()) {
            val a = g.applicationOf(id) ?: continue
            val left = g.endsAt.value - now
            if (left > thresholds.grantNoticeDays * 4L) continue
            val gain = e.grantRunner.countFor(g, id) - a.baseline
            if (gain <= 0L) out += Alert("grant.slipping", g.id.value, Severity.WARNING, "A grant you applied for ends in ${Formatting.plural(left.coerceAtLeast(0L), "day")}", "You have not grown since you applied, so nothing would be paid.")
        }
    }

    /** Grids the company draws power from or sells into: a thin reserve or a recent blackout, and agreements that are not being kept. */
    private fun energy(id: CompanyId, out: MutableList<Alert>) {
        if (!e.energyGridRunner.active) return
        val advisor = app.many.economytycoon.energygrid.GridAdvisor()
        for (g in e.energyGrids.grids) {
            val draws = g.loads.any { it.company == id }
            val sells = g.units.any { it.owner == id }
            if (!draws && !sells) continue
            val facts = app.many.economytycoon.energygrid.GridFacts.of(e.energyGrids, g, e.tick.value, e.energyGridService.peakDemandKw(g.id))
            for (i in advisor.advise(facts, thresholds.gridAlertPriority)) {
                val mine = (draws && (i.code == "low-reserve" || i.code == "recent-blackout")) || (sells && i.code == "ppa-shortfall")
                if (mine) out += Alert("energy.${i.code}", g.id.value, Severity.WARNING, i.title, i.detail)
            }
        }
    }

    /** Supervision, capital breaches and runs at the banks the company runs. */
    private fun banks(id: CompanyId, out: MutableList<Alert>) {
        if (!e.bankingRunner.active) return
        for (b in e.banking.banksOf(id)) {
            if (!b.status.isOperating) continue
            val view = e.banking.view(b.id, e.bankingInputs) ?: continue
            for (text in app.many.economytycoon.ui.present.BankingPresenter.alerts(e.banking, view)) {
                val serious = text.startsWith("Under supervision") || text.startsWith("Capital:") || text.startsWith("Depositors are leaving")
                val slug = text.lowercase().filter { it.isLetterOrDigit() }.take(24)
                out += Alert("bank.$slug", b.id.value, if (serious) Severity.WARNING else Severity.INFO, "${b.name}: $text", "Open the bank to see why and what the advisor suggests.")
            }
        }
    }

    /** Deadlines for permits and carbon obligations, a shortfall that is near, and permits about to lapse. */
    private fun carbon(id: CompanyId, out: MutableList<Alert>) {
        if (!e.carbonRunner.active || e.carbonState.compliance.accountsOf(id).isEmpty()) return
        val view = e.carbonViews.company(id, e.tick)
        for (a in view.accounts) {
            val p = a.pending
            if (p != null && p.outstanding.isPositive) {
                val days = p.daysLeft.toLong()
                if (p.projectedShortfall.isPositive && days <= 7L) {
                    out += Alert("carbon.shortfall", a.country.value, Severity.CRITICAL, "Carbon shortfall in ${Formatting.plural(days, "day")}", "You are short by ${p.projectedShortfall.format()} t after the permits and credits you hold; the rest is fined.")
                } else if (days <= thresholds.carbonNoticeDays) {
                    out += Alert("carbon.deadline", a.country.value, Severity.WARNING, "Carbon deadline in ${Formatting.plural(days, "day")}", "You still owe ${p.outstanding.format()} t for ${p.year}.")
                }
            }
            if (a.expiringNext.isPositive && app.many.economytycoon.carbon.CarbonCalendar.dayOfYear(e.tick) >= app.many.economytycoon.core.Calendar.DAYS_PER_YEAR * 3 / 4) {
                out += Alert("carbon.lapsing", a.country.value, Severity.INFO, "Permits will lapse at the year end", "${a.expiringNext.format()} t of permits expire unused; use or sell them.")
            }
        }
    }

    private fun marketing(id: CompanyId, out: MutableList<Alert>) {
        if (e.marketing.state.campaignMap.isEmpty() && e.marketing.state.damageMap.isEmpty()) return
        val now = e.tick.value
        for (c in e.marketing.state.campaignsOf(id)) {
            val ended = c.endedAt?.value
            if (c.status == app.many.economytycoon.marketing.CampaignStatus.SUSPENDED && ended != null && now - ended <= thresholds.marketingNoticeDays) {
                out += Alert("marketing.suspended", c.id.value, Severity.WARNING, "A ${c.channel.label.lowercase()} campaign was suspended", "The bills went unpaid, so the agency stopped it. Restore cash before booking it again.")
            }
            if (c.status == app.many.economytycoon.marketing.CampaignStatus.ACTIVE && c.creative.freshnessPpm < app.many.economytycoon.marketing.MarketingEngine.WORN_LINE) {
                out += Alert("marketing.worn", c.id.value, Severity.INFO, "An advertisement has worn out", "People have seen the ${c.channel.label.lowercase()} creative too often. A new one restores its strength.")
            }
        }
        for (d in e.marketing.state.damagesOf(id)) {
            if (now - d.tick.value <= thresholds.marketingNoticeDays) {
                out += Alert("marketing.damage", d.id.value, Severity.WARNING, "Your brand took a hit: ${d.kind.label.lowercase()}", "Distrust blunts new advertising until it fades. Steady, modest campaigns rebuild it.")
            }
        }
    }

    private fun cover(id: CompanyId, out: MutableList<Alert>) {
        val now = e.tick.value
        for (p in e.risk.policiesOf(id)) {
            val ended = p.endedAt?.value ?: continue
            if (p.status == app.many.economytycoon.risk.PolicyStatus.LAPSED && now - ended <= thresholds.lapseNoticeDays) {
                val f = e.production.facility(p.facility)
                val name = f?.name?.ifBlank { null } ?: e.content.facilityTypeOrNull(f?.type ?: continue)?.name ?: "A facility"
                out += Alert("insurance.lapsed", p.id.value, Severity.WARNING, "Cover lapsed on $name", "The premium could not be paid, so it is no longer insured.")
            }
        }
        for (f in e.production.facilitiesOf(id)) {
            if (f.status != FacilityStatus.OPERATING || e.risk.activeFor(f.id) != null) continue
            val value = e.riskRunner.insuredValueOf(f)
            if (value >= thresholds.uninsuredValue) {
                val name = f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }
                out += Alert("insurance.none", f.id.value, Severity.INFO, "$name is uninsured", "Worth about ${Formatting.money(value)}; a fire or flood would fall on you.")
            }
        }
    }

    private fun contracts(id: CompanyId, out: MutableList<Alert>) {
        for (o in e.contracts.proposalsFor(id)) {
            val from = e.companies.company(o.proposer)?.name ?: "A company"
            val verb = if (o.proposerSide == app.many.economytycoon.contracts.ContractSide.SELLER) "sell you" else "buy from you"
            out += Alert("contract.offer", o.id.value, Severity.INFO, "$from offers a contract", "To $verb ${Formatting.quantity(o.terms.quantity)} of ${o.terms.good.key} every ${Formatting.plural(o.terms.intervalTicks.toLong(), "day")}.")
        }
        for (k in e.contracts.activeOf(id)) {
            if (k.strikes <= 0) continue
            val who = e.companies.company(k.other(id))?.name ?: "the other side"
            val sev = if (k.strikes + 1 >= e.contracts.params.strikesToBreach) Severity.CRITICAL else Severity.WARNING
            out += Alert("contract.missed", k.id.value, sev, "Contract with $who is slipping", "${Formatting.plural(k.strikes.toLong(), "delivery")} in a row missed; ${e.contracts.params.strikesToBreach} ends it.")
        }
    }

    private fun research(id: CompanyId, out: MutableList<Alert>) {
        val state = e.research.peek(id) ?: return
        val points = e.companies.progress(id).researchPoints
        if (state.projects().isEmpty() && state.queued().isEmpty() && points > thresholds.idleResearchPoints) {
            out += Alert("research.idle", 0, Severity.INFO, "Research is idle", "${Formatting.grouped(points)} research points are waiting to be spent.")
        }
    }
}

data class AlertThresholds(
    val criticalRunwayDays: Long = 10,
    val warningRunwayDays: Long = 30,
    val lowMoralePpm: Long = 350_000,
    val poorConditionPpm: Long = 400_000,
    val pollutionPpm: Long = 800_000,
    val idleResearchPoints: Long = 200,
    val portfolioLossPpm: Long = 250_000,
    val lapseNoticeDays: Long = 15,
    val tenderWarnDays: Long = 5,
    val grantNoticeDays: Long = 4,
    val marketingNoticeDays: Long = 10,
    val gridAlertPriority: Int = 70,
    val lawNoticeDays: Long = 10,
    val bondNoticeDays: Long = 10,
    val demandUrgentDays: Long = 3,
    val futuresExpiryDays: Long = 5,
    val propertyNoticeDays: Long = 10,
    val bondCashCover: Long = 20,
    val fineNoticeDays: Long = 20,
    val carbonNoticeDays: Long = 20,
    val uninsuredValue: Money = Money.units(250_000),
)

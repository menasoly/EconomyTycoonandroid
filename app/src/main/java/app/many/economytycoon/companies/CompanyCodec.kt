package app.many.economytycoon.companies

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder

/** Binary save format for the company registry, soft state, insolvency cases and takeover offers. */
object CompanyCodec {
    const val VERSION = 1

    fun write(s: CompanyService, insolvency: InsolvencyService, acquisitions: AcquisitionService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val all = s.all()
        out.writeVarInt(all.size)
        for (c in all) {
            out.writeVarLong(c.id.value); out.writeString(c.name); out.writeVarInt(c.kind.ordinal); out.writeVarLong(c.country.value)
            out.writeVarLong(c.founded.value); out.writeString(c.founder.kind); out.writeVarLong(c.founder.id)
            out.writeBoolean(c.hq != null); c.hq?.let { out.writeVarLong(it.value) }
            out.writeVarInt(c.status.ordinal); out.writeString(c.strategy)
            out.writeBoolean(c.endedAt != null); c.endedAt?.let { out.writeVarLong(it.value) }
            out.writeVarInt(c.industries.size); for (i in c.industries) out.writeString(i.key)
        }
        val budgets = s.budgetEntries()
        out.writeVarInt(budgets.size)
        for ((id, b) in budgets) {
            out.writeVarLong(id.value)
            val e = b.entries()
            out.writeVarInt(e.size)
            for ((d, m) in e) { out.writeVarInt(d.ordinal); out.writeMoney(m) }
        }
        val progress = s.progressEntries()
        out.writeVarInt(progress.size)
        for ((id, p) in progress) {
            out.writeVarLong(id.value); out.writeVarLong(p.researchPoints); out.writeVarLong(p.compliancePpm)
            out.writeVarLong(p.recruitingPpm); out.writeVarLong(p.efficiencyPpm)
        }
        val reps = s.brands.allReputations()
        out.writeVarInt(reps.size)
        for ((id, r) in reps) { out.writeVarLong(id.value); out.writeVarLong(r.customers); out.writeVarLong(r.employees); out.writeVarLong(r.regulators); out.writeVarLong(r.investors) }
        val brands = s.brands.allBrands()
        out.writeVarInt(brands.size)
        for ((id, ind, b) in brands) { out.writeVarLong(id.value); out.writeString(ind.key); out.writeVarLong(b.awarenessPpm); out.writeVarLong(b.loyaltyPpm) }
        val stakes = s.groups.entries()
        out.writeVarInt(stakes.size)
        for ((p, c, v) in stakes) { out.writeVarLong(p.value); out.writeVarLong(c.value); out.writeVarLong(v) }

        val sells = s.policies.allSells()
        out.writeVarInt(sells.size)
        for ((c, r) in sells) {
            out.writeVarLong(c.value); out.writeVarLong(r.facility.value); out.writeString(r.good.key); out.writeVarInt(r.strategy.ordinal)
            out.writeVarLong(r.markupPpm); out.writeQuantity(r.reserve)
            out.writeBoolean(r.maxPerOrder != null); r.maxPerOrder?.let { out.writeQuantity(it) }
            out.writeVarLong(r.minMarginPpm); out.writeBoolean(r.enabled)
        }
        val buys = s.policies.allBuys()
        out.writeVarInt(buys.size)
        for ((c, r) in buys) {
            out.writeVarLong(c.value); out.writeVarLong(r.facility.value); out.writeString(r.good.key); out.writeQuantity(r.target)
            out.writeVarLong(r.maxPriceMultiplePpm); out.writeBoolean(r.enabled)
        }
        val divs = s.policies.allDividends()
        out.writeVarInt(divs.size)
        for ((c, d) in divs) { out.writeVarLong(c.value); out.writeVarLong(d.payoutPpm); out.writeVarInt(d.reserveTicks); out.writeVarInt(d.intervalTicks) }

        val cases = insolvency.cases()
        out.writeVarInt(cases.size)
        for (c in cases) {
            out.writeVarLong(c.company.value); out.writeVarLong(c.opened.value); out.writeVarInt(c.phase.ordinal); out.writeVarLong(c.phaseSince.value)
            out.writeVarInt(c.outcome?.ordinal ?: -1); out.writeBoolean(c.closed != null); c.closed?.let { out.writeVarLong(it.value) }
        }
        val offers = acquisitions.offers()
        out.writeVarInt(offers.size)
        for (o in offers) {
            out.writeVarLong(o.id); out.writeVarLong(o.acquirer.value); out.writeVarLong(o.target.value); out.writeVarLong(o.shares)
            out.writeMoney(o.pricePerShare); out.writeVarLong(o.made.value); out.writeVarLong(o.expires.value); out.writeVarInt(o.state.ordinal)
        }
    }

    fun read(r: ByteReader, s: CompanyService, insolvency: InsolvencyService, acquisitions: AcquisitionService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported company save version $version")
        s.clearAll(); insolvency.clearAll(); acquisitions.clearAll()
        repeat(r.readVarInt()) {
            val id = CompanyId(r.readVarLong()); val name = r.readString()
            val kind = CompanyKind.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad company kind") }
            val country = CountryId(r.readVarLong()); val founded = Tick(r.readVarLong())
            val founder = Holder(r.readString(), r.readVarLong())
            val c = Company(id, name, kind, country, founded, founder)
            val hq = if (r.readBoolean()) CityId(r.readVarLong()) else null
            val status = CompanyStatus.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad company status") }
            val strategy = r.readString()
            val ended = if (r.readBoolean()) Tick(r.readVarLong()) else null
            c.restore(name, hq, status, strategy, ended)
            repeat(r.readVarInt()) { c.industries.add(IndustryId(r.readString())) }
            s.restoreCompany(c)
        }
        repeat(r.readVarInt()) {
            val id = CompanyId(r.readVarLong())
            val values = HashMap<Department, Money>()
            repeat(r.readVarInt()) { values[Department.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad department") }] = r.readMoney() }
            val b = DepartmentBudgets(); b.restore(values)
            s.restoreBudgets(id, b)
        }
        repeat(r.readVarInt()) {
            val id = CompanyId(r.readVarLong())
            val p = DepartmentProgress(); p.restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            s.restoreProgress(id, p)
        }
        repeat(r.readVarInt()) { s.brands.reputation(CompanyId(r.readVarLong())).restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong()) }
        repeat(r.readVarInt()) { s.brands.brand(CompanyId(r.readVarLong()), IndustryId(r.readString())).restore(r.readVarLong(), r.readVarLong()) }
        val stakes = ArrayList<Triple<CompanyId, CompanyId, Long>>()
        repeat(r.readVarInt()) { stakes += Triple(CompanyId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarLong()) }
        s.groups.restore(stakes, s.params.controlThreshold)

        repeat(r.readVarInt()) {
            val c = CompanyId(r.readVarLong()); val fac = FacilityId(r.readVarLong()); val good = GoodId(r.readString())
            val strat = PriceStrategy.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad price strategy") }
            val markup = r.readVarLong(); val reserve = r.readQuantity()
            val max = if (r.readBoolean()) r.readQuantity() else null
            s.policies.setSell(c, SellRule(fac, good, strat, markup, reserve, max, r.readVarLong(), r.readBoolean()))
        }
        repeat(r.readVarInt()) {
            val c = CompanyId(r.readVarLong())
            s.policies.setBuy(c, BuyRule(FacilityId(r.readVarLong()), GoodId(r.readString()), r.readQuantity(), r.readVarLong(), r.readBoolean()))
        }
        repeat(r.readVarInt()) { s.policies.setDividend(CompanyId(r.readVarLong()), DividendPolicy(r.readVarLong(), r.readVarInt(), r.readVarInt())) }

        repeat(r.readVarInt()) {
            val case = InsolvencyCase(CompanyId(r.readVarLong()), Tick(r.readVarLong()))
            val phase = CasePhase.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad case phase") }
            val since = Tick(r.readVarLong())
            val o = r.readVarInt()
            val outcome = if (o < 0) null else CaseOutcome.values().getOrElse(o) { throw ByteReader.CorruptDataException("bad case outcome") }
            val closed = if (r.readBoolean()) Tick(r.readVarLong()) else null
            case.restore(phase, since, outcome, closed)
            insolvency.restoreCase(case)
        }
        repeat(r.readVarInt()) {
            val offer = Offer(r.readVarLong(), CompanyId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarLong(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()))
            offer.restore(OfferState.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad offer state") })
            acquisitions.restoreOffer(offer)
        }
    }
}

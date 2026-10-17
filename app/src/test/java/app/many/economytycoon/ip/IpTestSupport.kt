package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.research.ResearchTestSupport

/** Fixtures for the intellectual property tests: a scriptable host context and a small rig around the services. */
object IpTestSupport {
    val content get() = ResearchTestSupport.content

    val a = CompanyId(1)
    val b = CompanyId(2)
    val c = CompanyId(3)
    val us = CountryId(1)
    val eu = CountryId(2)
    val milling = TechId("milling")
    val ovens = TechId("ovens")
    val efficiency = TechId("efficiency")
    val megamill = TechId("megamill")

    class TestContext : IpContext {
        val cashOf = java.util.TreeMap<IpOwner, Money>()
        var defaultCash: Money = Money.units(50_000_000)
        val usageOf = java.util.HashMap<Pair<CompanyId, TechId>, UsageReport>()
        val practicers = java.util.HashMap<Pair<TechId, CountryId>, MutableList<Practice>>()
        val holders = java.util.HashMap<TechId, List<CompanyId>>()
        val knowerCount = java.util.HashMap<Pair<CompanyId, TechId>, Int>()
        val exposureOf = java.util.HashMap<Pair<CompanyId, TechId>, Rate>()
        val honesty = java.util.HashMap<CompanyId, Rate>()
        val health = java.util.HashSet<CountryId>()
        val worked = java.util.HashSet<Triple<CompanyId, TechId, CountryId>>()
        val shares = java.util.HashMap<Pair<CompanyId, TechField>, Rate>()
        val rdSpend = java.util.HashMap<CompanyId, Money>()
        val fieldSales = java.util.HashMap<Pair<CompanyId, TechField>, Money>()
        val distressed = java.util.HashSet<CompanyId>()
        val techsOf = java.util.HashMap<CompanyId, List<TechId>>()
        val ai = ArrayList<CompanyId>()
        val inactive = java.util.HashSet<CompanyId>()
        val homes = java.util.HashMap<CompanyId, CountryId>()

        override fun cash(owner: IpOwner): Money = cashOf[owner] ?: defaultCash
        override fun isActive(company: CompanyId) = company !in inactive
        override fun aiCompanies(): List<CompanyId> = ai
        override fun homeCountry(company: CompanyId): CountryId? = homes[company]
        override fun independentHolders(tech: TechId, exclude: CompanyId): List<CompanyId> = (holders[tech] ?: emptyList()).filter { it != exclude }
        override fun knownTechs(company: CompanyId): List<TechId> = techsOf[company] ?: emptyList()
        override fun knowers(company: CompanyId, tech: TechId): Int = knowerCount[company to tech] ?: 0
        override fun exposure(company: CompanyId, tech: TechId): Rate = exposureOf[company to tech] ?: Rate.ZERO
        override fun usage(company: CompanyId, tech: TechId, industries: List<IndustryFamily>, countries: List<CountryId>, from: Tick, to: Tick): UsageReport =
            usageOf[company to tech] ?: UsageReport.ZERO
        override fun practicing(tech: TechId, country: CountryId, from: Tick, to: Tick): List<Practice> = practicers[tech to country] ?: emptyList()
        override fun reportingHonesty(company: CompanyId): Rate = honesty[company] ?: Rate.ONE
        override fun publicHealthEmergency(country: CountryId) = country in health
        override fun isWorkedLocally(owner: CompanyId, tech: TechId, country: CountryId) = Triple(owner, tech, country) in worked
        override fun marketShare(company: CompanyId, field: TechField): Rate = shares[company to field] ?: Rate.ZERO
        override fun qualifyingResearchSpend(company: CompanyId, from: Tick, to: Tick): Money = rdSpend[company] ?: Money.ZERO
        override fun annualSalesInField(company: CompanyId, field: TechField): Money = fieldSales[company to field] ?: Money.ZERO
        override fun isDistressed(company: CompanyId) = company in distressed
    }

    fun usage(units: Long, price: Long) = UsageReport(Quantity.units(units), Money.units(units * price))

    /** A world with a seed, a state, a scriptable context and the office wired together. */
    class Rig(seed: Long = 20_240L, val params: IpParams = IpParams()) {
        val state = IpState(seed)
        val ctx = TestContext()
        val known = HashSet<Pair<CompanyId, TechId>>()
        val holding = TechHolding { co, t -> (co to t) in known }
        val office = PatentOffice(state, content, holding, params)
        val secrets = TradeSecretService(state, content, holding, params)
        val book = LicenseBook(state, office, params)
        val valuer = PatentValuer(state, office, params)
        val pools = PoolService(state, book, params)
        val cross = CrossLicenseService(state, book, valuer)
        val disputes = DisputeService(state, office, book, pools, params)
        val designs = DesignAroundService(state, disputes, params)
        val market = PatentMarket(state, office, book, pools, valuer, params)
        val npes = NpeService(state, disputes, market, params)
        val compulsory = CompulsoryService(state, office, book, params)
        val tax = IpTax(state, office, params)
        var tick = 0L
        var out = IpOutput(state.ids)
        val wallet get() = Wallet(ctx)

        fun teach(company: CompanyId, t1: TechId) { known += company to t1 }
        fun teach(company: CompanyId, t1: TechId, t2: TechId) { teach(company, t1); teach(company, t2) }
        fun teach(company: CompanyId, t1: TechId, t2: TechId, t3: TechId) { teach(company, t1, t2); teach(company, t3) }
        fun teach(company: CompanyId, t1: TechId, t2: TechId, t3: TechId, t4: TechId) { teach(company, t1, t2, t3); teach(company, t4) }

        fun run(at: Long = tick): IpRun { tick = at; return IpRun(Tick(at), ctx, Wallet(ctx), out) }

        fun fileIn(company: CompanyId, tech: TechId, countries: List<CountryId>, breadth: BreadthChoice = BreadthChoice.STANDARD, claims: Int = 10): app.many.economytycoon.core.Outcome<Patent> {
            teach(company, tech)
            return office.file(run(), FilingRequest(company, tech, countries, breadth, claims), 0)
        }

        fun file(company: CompanyId, tech: TechId, c1: CountryId, breadth: BreadthChoice = BreadthChoice.STANDARD, claims: Int = 10) = fileIn(company, tech, listOf(c1), breadth, claims)
        fun file(company: CompanyId, tech: TechId, c1: CountryId, c2: CountryId, breadth: BreadthChoice = BreadthChoice.STANDARD, claims: Int = 10) = fileIn(company, tech, listOf(c1, c2), breadth, claims)

        /** A patent already granted in the given countries, built directly so tests do not depend on an examiner's roll. */
        fun granted(
            company: CompanyId, tech: TechId, countries: List<CountryId> = listOf(us), breadthPpm: Long = 600_000L, noveltyPpm: Long = 700_000L,
            industry: IndustryFamily? = null,
        ): Patent {
            val def = content.technology(tech)
            val id = state.ids.patent()
            val p = Patent(id, company, tech, def.name, TechFields.of(def), industry, Tick(tick), noveltyPpm, 10, breadthPpm, emptyList(), def.researchCost, false, IpOwner.company(company))
            for (c in countries) {
                val f = Filing(c, Tick(tick), Tick(tick), breadthPpm)
                f.status = FilingStatus.GRANTED; f.grantedAt = Tick(tick); f.expiresAt = Tick(tick + 7_200); f.nextFeeDue = Tick(tick + 360)
                p.filingMap[c] = f
            }
            state.patentMap[id] = p
            known += company to tech
            return p
        }

        fun offer(licensor: CompanyId, licensee: CompanyId, patents: List<PatentId>, terms: RoyaltyTerms = percent(5), kind: LicenseKind = LicenseKind.NON_EXCLUSIVE, scope: LicenseScope = LicenseScope.EVERYWHERE, termDays: Int = 1_800) =
            book.offer(run(), OfferRequest(IpOwner.company(licensor), licensee, patents, terms, kind, scope, termDays))

        fun sign(licensor: CompanyId, licensee: CompanyId, patents: List<PatentId>, terms: RoyaltyTerms = percent(5), kind: LicenseKind = LicenseKind.NON_EXCLUSIVE, scope: LicenseScope = LicenseScope.EVERYWHERE, termDays: Int = 1_800): License {
            val l = offer(licensor, licensee, patents, terms, kind, scope, termDays).getOrThrow()
            return book.accept(run(), l.id, licensee).getOrThrow()
        }

        fun percent(p: Long) = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(p))

        fun events() = out.events.toList()
        fun charges() = out.charges.toList()
    }

    /** A whole engine over a scriptable context, for scenario tests. */
    class EngineRig(seed: Long = 20_240L, val params: IpParams = IpParams()) {
        val ctx = TestContext()
        val known = HashSet<Pair<CompanyId, TechId>>()
        val engine = IpEngine(seed, content, TechHolding { co, t -> (co to t) in known }, params)
        var day = 0L
        val charges = ArrayList<IpCharge>()
        val events = ArrayList<IpEvent>()

        fun teach(company: CompanyId, tech: TechId) { known += company to tech; ctx.techsOf[company] = ((ctx.techsOf[company] ?: emptyList()) + tech).distinct() }

        fun absorb(r: IpStepResult) { charges += r.charges; events += r.events }

        /** Runs days up to and including [to]. */
        fun advanceTo(to: Long) {
            while (day < to) { day++; absorb(engine.step(Tick(day), ctx)) }
        }

        fun exec(cmd: IpCommand): IpCommandResult {
            val r = engine.execute(Tick(day), ctx, cmd)
            absorb(r.step)
            return r
        }

        fun hash() = engine.stateHash().toString()

        /** A patent already granted in the given countries, built directly so scenarios do not depend on an examiner's roll. */
        fun granted(company: CompanyId, tech: TechId, countries: List<CountryId> = listOf(us), breadthPpm: Long = 600_000L, noveltyPpm: Long = 700_000L): Patent {
            val def = content.technology(tech)
            val st = engine.state
            val id = st.ids.patent()
            val p = Patent(id, company, tech, def.name, TechFields.of(def), null, Tick(day), noveltyPpm, 10, breadthPpm, emptyList(), def.researchCost, false, IpOwner.company(company))
            for (c in countries) {
                val f = Filing(c, Tick(day), Tick(day), breadthPpm)
                f.status = FilingStatus.GRANTED; f.grantedAt = Tick(day); f.expiresAt = Tick(day + 7_200); f.nextFeeDue = Tick(day + 360)
                p.filingMap[c] = f
            }
            st.patentMap[id] = p
            teach(company, tech)
            return p
        }
    }

    /** A world with every kind of record in it, at various stages of life. */
    fun busyWorld(seed: Long = 7L): EngineRig {
        val r = EngineRig(seed)
        r.engine.start()
        r.ctx.homes[a] = us; r.ctx.homes[b] = eu
        r.ctx.rdSpend[a] = Money.units(300_000)
        r.engine.state.regimes.assign(eu, RegimeKind.PRO_PATENT)
        val pm = r.granted(a, milling, listOf(us, eu), breadthPpm = 700_000L)
        val po = r.granted(b, ovens, listOf(us), breadthPpm = 650_000L)
        r.granted(c, efficiency, listOf(us), noveltyPpm = 300_000L)
        r.teach(c, milling)
        r.exec(IpCommand.KeepSecret(c, milling)).outcome.getOrThrow()
        r.engine.state.secretsOf(c).first().knownToSet.add(b)
        r.exec(IpCommand.InvestInSecret(c, r.engine.state.secretsOf(c).first().id, Money.units(40_000))).outcome.getOrThrow()
        r.exec(IpCommand.DeclareEssential(a, r.granted(a, ovens, listOf(us), breadthPpm = 800_000L).id, true)).outcome.getOrThrow()
        r.exec(IpCommand.SetPolicy(a, IpPolicy(EnforcementStance.LITIGATE, 700_000L, BreadthChoice.BROAD, listOf(us, eu), false))).outcome.getOrThrow()
        val terms = RoyaltyTerms(RoyaltyBasis.HYBRID, perUnit = Money.units(2), percent = Rate.percent(2), minimumPerPeriod = Money.units(500), capPerPeriod = Money.units(90_000), upfront = Money.units(1_000))
        r.exec(IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), c, listOf(pm.id), terms, LicenseKind.SOLE, LicenseScope.of(emptyList(), listOf(us)), 900, true))).outcome.getOrThrow()
        val l = r.engine.state.licenseMap.values.first()
        r.exec(IpCommand.AcceptLicense(c, l.id)).outcome.getOrThrow()
        r.exec(IpCommand.OfferLicense(b, OfferRequest(IpOwner.company(b), a, listOf(po.id), RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(9_000))))).outcome.getOrThrow()
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        r.exec(IpCommand.ReportInfringement(a, pm.id, b, us)).outcome.getOrThrow()
        val d = r.engine.state.disputeMap.values.first()
        r.exec(IpCommand.SendNotice(a, d.id)).outcome.getOrThrow()
        r.exec(IpCommand.OfferSettlement(b, d.id, Money.units(1_234))).outcome.getOrThrow()
        r.exec(IpCommand.StartDesignAround(b, pm.id)).outcome.getOrThrow()
        r.engine.state.clearanceSet.add(Clearance(c, po.id, Tick(3)))
        r.ctx.health += us
        r.exec(IpCommand.RequestCompulsory(c, po.id, us, CompulsoryGround.PUBLIC_HEALTH)).outcome.getOrThrow()
        r.exec(IpCommand.ListPatent(c, r.engine.state.patentsOf(c).first().id, Money.units(5_000))).outcome.getOrThrow()
        r.engine.state.creditYearMap[a] = 3L
        r.advanceTo(60)
        return r
    }

}

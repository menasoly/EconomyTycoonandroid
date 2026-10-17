package app.many.economytycoon.ip

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.StateHash
import app.many.economytycoon.core.Tick

/** What one command did: its answer and the events and payments it caused. */
data class IpCommandResult(val outcome: Outcome<String>, val step: IpStepResult)

/**
 * The module's front door. It owns the state and every service, runs the daily schedule in a fixed order, and carries
 * out commands. The host calls [step] once a day after production and markets have recorded the day's sales, settles
 * the returned payments, and calls [execute] for each command a player or computer company issues.
 */
class IpEngine(
    worldSeed: Long,
    val content: ContentRepository,
    holding: TechHolding,
    val params: IpParams = IpParams(),
    val state: IpState = IpState(worldSeed),
) {
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
    val standards = StandardsService(state, params)
    val effects = IpEffects(state, book, disputes, pools, params)
    val planner = IpPlanner(state, office, book, disputes, designs, valuer, content, params)

    /** Creates the scripted holders on a new game; safe to call again. */
    fun start() { npes.seed() }

    fun stateHash(): StateHash = state.stateHash()

    /**
     * One day. The order matters: the register moves first (grants and lapses change who has rights), then secrets,
     * licence settlement, the courts, the programmes, and finally the monthly and yearly rounds that decide new moves.
     */
    fun step(tick: Tick, ctx: IpContext): IpStepResult {
        val out = IpOutput(state.ids)
        val run = IpRun(tick, ctx, Wallet(ctx), out)
        office.step(run)
        secrets.step(run)
        book.step(run)
        disputes.step(run)
        designs.step(run)
        compulsory.step(run)
        if (Calendar.isMonthStart(tick)) monthly(run)
        if (tick.value % QUARTER_DAYS == 0L) designs.prune(tick)
        tax.step(run)
        return out.result()
    }

    private fun monthly(run: IpRun) {
        market.expireListings(run.tick)
        npes.step(run)
        for (c in tax.knownCompanies()) {
            if (run.ctx.isActive(c) && run.ctx.isDistressed(c)) market.fireSale(run, IpOwner.company(c))
        }
        for (c in run.ctx.aiCompanies().sorted()) {
            if (!run.ctx.isActive(c)) continue
            for (cmd in planner.plan(run, c)) apply(run, cmd)
        }
    }

    /** Runs one command on its own and returns what it did. */
    fun execute(tick: Tick, ctx: IpContext, cmd: IpCommand): IpCommandResult {
        val out = IpOutput(state.ids)
        val run = IpRun(tick, ctx, Wallet(ctx), out)
        val outcome = if (!ctx.isActive(cmd.actor)) Outcome.reject(Rules.WRONG_STATE, "the company is out of business") else apply(run, cmd)
        return IpCommandResult(outcome, out.result())
    }

    /** A company has closed: its rights are sold or ended and everything it was party to is wound up. */
    fun companyClosed(tick: Tick, ctx: IpContext, company: CompanyId): IpStepResult {
        val out = IpOutput(state.ids)
        val run = IpRun(tick, ctx, Wallet(ctx), out)
        pools.companyClosed(run, company)
        book.companyClosed(run, company)
        market.companyClosed(run, company)
        for (s in state.secretsOf(company)) if (s.isLive) secrets.drop(run, s.id, company)
        for (d in designs.running(company)) designs.cancel(run, d.id, company)
        for (r in compulsory.requestsOf(company)) if (r.status.open) compulsory.withdraw(r.id, company)
        return out.result()
    }

    internal fun apply(run: IpRun, cmd: IpCommand): Outcome<String> {
        val me = IpOwner.company(cmd.actor)
        return when (cmd) {
            is IpCommand.FilePatent -> {
                val others = run.ctx.independentHolders(cmd.tech, cmd.actor).size
                val r = office.file(run, FilingRequest(cmd.actor, cmd.tech, cmd.countries, cmd.breadth, cmd.claims, cmd.industry), others)
                if (r is Outcome.Ok) state.liveSecretFor(cmd.actor, cmd.tech)?.let { secrets.markPatented(it.id, run.tick) }
                r.map { "Filed ${it.title}" }
            }
            is IpCommand.AbandonPatent -> office.abandon(run, cmd.patent, cmd.country, cmd.actor).map { "Abandoned" }
            is IpCommand.SetAutoMaintain -> office.setAutoMaintain(cmd.patent, cmd.actor, cmd.on).map { "Renewal ${if (cmd.on) "on" else "off"}" }
            is IpCommand.PayMaintenance -> office.payMaintenance(run, cmd.patent, cmd.country, cmd.actor).map { "Paid ${it.format()}" }
            is IpCommand.DeclareEssential -> standards.declare(cmd.patent, me, cmd.pledgeFrand).map { "Declared essential" }
            is IpCommand.KeepSecret -> secrets.keep(run, cmd.actor, cmd.tech).map { "Kept secret ${it.id.value}" }
            is IpCommand.InvestInSecret -> secrets.invest(run, cmd.secret, cmd.actor, cmd.amount).map { "Protection now ${it / 10_000}%" }
            is IpCommand.DropSecret -> secrets.drop(run, cmd.secret, cmd.actor).map { "Dropped" }
            is IpCommand.StealSecret -> steal(run, cmd)
            is IpCommand.OfferLicense -> {
                if (cmd.request.licensor != me) Outcome.reject(Rules.NOT_OWNER, "you can only offer your own patents")
                else book.offer(run, cmd.request).map { "Offered licence ${it.id.value}" }
            }
            is IpCommand.AcceptLicense -> book.accept(run, cmd.license, cmd.actor).also { if (it is Outcome.Ok) disputes.onLicensed(run, it.value) }.map { "Licence ${it.id.value} in force" }
            is IpCommand.DeclineLicense -> book.decline(run, cmd.license, cmd.actor).map { "Declined" }
            is IpCommand.WithdrawOffer -> book.withdraw(run, cmd.license, me).map { "Withdrawn" }
            is IpCommand.TerminateLicense -> book.terminate(run, cmd.license, me).map { "Ended, ${it.format()} due" }
            is IpCommand.RequestFrandLicense -> book.requestFrand(run, cmd.actor, cmd.patent).map { "Offered licence ${it.id.value}" }
            is IpCommand.AuditLicense -> book.audit(run, cmd.license, me).map { if (it.found) "Found ${it.underpaid.format()} unpaid" else "Nothing found" }
            is IpCommand.ProposeCrossLicense -> cross.propose(run, cmd.actor, cmd.partner, cmd.mine, cmd.theirs, termDays = cmd.termDays).map { "Offered cross-licence ${it.first.id.value}" }
            is IpCommand.FormPool -> pools.form(run, cmd.actor, cmd.name, cmd.field, cmd.terms, cmd.adminFeePpm).map { "Formed ${it.name}" }
            is IpCommand.JoinPool -> pools.join(run, cmd.pool, cmd.actor, cmd.patents).map { "Joined" }
            is IpCommand.LeavePool -> pools.leave(run, cmd.pool, cmd.actor).map { "Left" }
            is IpCommand.DissolvePool -> pools.dissolve(run, cmd.pool, cmd.actor).map { "Dissolved" }
            is IpCommand.OfferPool -> pools.offerBundle(run, cmd.pool, cmd.licensee).map { "Offered licence ${it.id.value}" }
            is IpCommand.ReportInfringement -> disputes.report(run, me, cmd.patent, cmd.defendant, cmd.country).map { "Opened dispute ${it.id.value}" }
            is IpCommand.SendNotice -> disputes.sendNotice(run, cmd.dispute, me, cmd.demand).map { "Demand sent" }
            is IpCommand.FileSuit -> disputes.file(run, cmd.dispute, me).map { "Suit filed" }
            is IpCommand.Appeal -> disputes.appeal(run, cmd.dispute, me).map { "Appeal lodged" }
            is IpCommand.WithdrawSuit -> disputes.withdraw(run, cmd.dispute, me).map { "Withdrawn" }
            is IpCommand.OfferSettlement -> disputes.offerSettlement(run, cmd.dispute, me, cmd.amount).map { "Offer made" }
            is IpCommand.AcceptSettlement -> disputes.acceptSettlement(run, cmd.dispute, me).map { "Settled for ${it.format()}" }
            is IpCommand.ChallengePatent -> disputes.challenge(run, cmd.actor, cmd.patent, cmd.country).map { "Challenge filed" }
            is IpCommand.StartDesignAround -> designs.start(run, cmd.actor, cmd.patent, cmd.dispute).map { "Redesign ${it.id.value} started" }
            is IpCommand.CancelDesignAround -> designs.cancel(run, cmd.id, cmd.actor).map { "Stopped" }
            is IpCommand.RequestCompulsory -> compulsory.request(run, cmd.actor, cmd.patent, cmd.country, cmd.ground).map { "Request ${it.id.value} filed" }
            is IpCommand.WithdrawCompulsory -> compulsory.withdraw(cmd.id, cmd.actor).map { "Withdrawn" }
            is IpCommand.ListPatent -> market.list(run, cmd.patent, me, cmd.price).map { "Listed" }
            is IpCommand.UnlistPatent -> market.unlist(cmd.patent, me).map { "Unlisted" }
            is IpCommand.BuyPatent -> market.buy(run, cmd.patent, me).map { "Bought for ${it.price.format()}" }
            is IpCommand.SetPolicy -> { state.policyMap[cmd.actor] = cmd.policy; Outcome.ok("Policy saved") }
        }
    }

    /** An attempt on a rival's secret; a theft that is noticed becomes a case against the thief. */
    private fun steal(run: IpRun, cmd: IpCommand.StealSecret): Outcome<String> {
        val r = secrets.espionage(run, cmd.actor, cmd.secret)
        if (r is Outcome.Rejected) return r
        val res = (r as Outcome.Ok).value
        if (res.success && res.detected) disputes.reportTheft(run, cmd.secret, cmd.actor, res.evidencePpm)
        return Outcome.ok(
            when {
                res.success && res.detected -> "Obtained, but traced to you"
                res.success -> "Obtained unnoticed"
                res.detected -> "Failed and was noticed"
                else -> "Failed"
            },
        )
    }

    private companion object {
        const val QUARTER_DAYS = 90L
    }
}

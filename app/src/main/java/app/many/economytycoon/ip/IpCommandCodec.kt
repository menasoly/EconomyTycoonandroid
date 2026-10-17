package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.TechId

/** Binary form of [IpCommand] for the command log and replays. Each command is a tag byte followed by its fields. */
object IpCommandCodec {
    private const val T_FILE = 1
    private const val T_ABANDON = 2
    private const val T_AUTO = 3
    private const val T_PAY_MAINT = 4
    private const val T_ESSENTIAL = 5
    private const val T_KEEP = 6
    private const val T_INVEST = 7
    private const val T_DROP = 8
    private const val T_STEAL = 9
    private const val T_OFFER = 10
    private const val T_ACCEPT = 11
    private const val T_DECLINE = 12
    private const val T_WITHDRAW_OFFER = 13
    private const val T_TERMINATE = 14
    private const val T_FRAND = 15
    private const val T_AUDIT = 16
    private const val T_CROSS = 17
    private const val T_FORM_POOL = 18
    private const val T_JOIN_POOL = 19
    private const val T_LEAVE_POOL = 20
    private const val T_DISSOLVE_POOL = 21
    private const val T_OFFER_POOL = 22
    private const val T_REPORT = 23
    private const val T_NOTICE = 24
    private const val T_SUIT = 25
    private const val T_APPEAL = 26
    private const val T_WITHDRAW_SUIT = 27
    private const val T_OFFER_SETTLE = 28
    private const val T_ACCEPT_SETTLE = 29
    private const val T_CHALLENGE = 30
    private const val T_DESIGN = 31
    private const val T_CANCEL_DESIGN = 32
    private const val T_COMPULSORY = 33
    private const val T_WITHDRAW_COMPULSORY = 34
    private const val T_LIST = 35
    private const val T_UNLIST = 36
    private const val T_BUY = 37
    private const val T_POLICY = 38

    fun encode(c: IpCommand): ByteArray = ByteWriter().also { write(c, it) }.toByteArray()

    fun decode(bytes: ByteArray): IpCommand = read(ByteReader(bytes))

    private fun ByteWriter.countries(list: List<CountryId>) { writeVarInt(list.size); for (c in list) writeVarLong(c.value) }
    private fun ByteReader.countries(): List<CountryId> = List(readVarInt()) { CountryId(readVarLong()) }
    private fun ByteWriter.patents(list: List<PatentId>) { writeVarInt(list.size); for (p in list) writeVarLong(p.value) }
    private fun ByteReader.patents(): List<PatentId> = List(readVarInt()) { PatentId(readVarLong()) }

    fun write(c: IpCommand, out: ByteWriter) {
        when (c) {
            is IpCommand.FilePatent -> {
                out.writeByte(T_FILE); out.writeVarLong(c.actor.value); out.writeString(c.tech.key); out.countries(c.countries)
                out.writeVarInt(c.breadth.ordinal); out.writeVarInt(c.claims); out.writeVarInt(c.industry?.ordinal ?: -1)
            }
            is IpCommand.AbandonPatent -> { out.writeByte(T_ABANDON); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.country?.value ?: -1L) }
            is IpCommand.SetAutoMaintain -> { out.writeByte(T_AUTO); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeBoolean(c.on) }
            is IpCommand.PayMaintenance -> { out.writeByte(T_PAY_MAINT); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.country.value) }
            is IpCommand.DeclareEssential -> { out.writeByte(T_ESSENTIAL); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeBoolean(c.pledgeFrand) }
            is IpCommand.KeepSecret -> { out.writeByte(T_KEEP); out.writeVarLong(c.actor.value); out.writeString(c.tech.key) }
            is IpCommand.InvestInSecret -> { out.writeByte(T_INVEST); out.writeVarLong(c.actor.value); out.writeVarLong(c.secret.value); out.writeMoney(c.amount) }
            is IpCommand.DropSecret -> { out.writeByte(T_DROP); out.writeVarLong(c.actor.value); out.writeVarLong(c.secret.value) }
            is IpCommand.StealSecret -> { out.writeByte(T_STEAL); out.writeVarLong(c.actor.value); out.writeVarLong(c.secret.value) }
            is IpCommand.OfferLicense -> {
                val q = c.request
                out.writeByte(T_OFFER); out.writeVarLong(c.actor.value); out.writeVarInt(q.licensor.kind.ordinal); out.writeVarLong(q.licensor.id)
                out.writeVarLong(q.licensee.value); out.patents(q.patents); IpCodec.writeTerms(q.terms, out); out.writeVarInt(q.kind.ordinal)
                out.writeVarInt(q.scope.industries.size); for (i in q.scope.industries) out.writeVarInt(i.ordinal)
                out.countries(q.scope.countries); out.writeVarInt(q.termDays); out.writeBoolean(q.sublicensable); out.writeVarLong(q.poolId?.value ?: -1L)
            }
            is IpCommand.AcceptLicense -> { out.writeByte(T_ACCEPT); out.writeVarLong(c.actor.value); out.writeVarLong(c.license.value) }
            is IpCommand.DeclineLicense -> { out.writeByte(T_DECLINE); out.writeVarLong(c.actor.value); out.writeVarLong(c.license.value) }
            is IpCommand.WithdrawOffer -> { out.writeByte(T_WITHDRAW_OFFER); out.writeVarLong(c.actor.value); out.writeVarLong(c.license.value) }
            is IpCommand.TerminateLicense -> { out.writeByte(T_TERMINATE); out.writeVarLong(c.actor.value); out.writeVarLong(c.license.value) }
            is IpCommand.RequestFrandLicense -> { out.writeByte(T_FRAND); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value) }
            is IpCommand.AuditLicense -> { out.writeByte(T_AUDIT); out.writeVarLong(c.actor.value); out.writeVarLong(c.license.value) }
            is IpCommand.ProposeCrossLicense -> {
                out.writeByte(T_CROSS); out.writeVarLong(c.actor.value); out.writeVarLong(c.partner.value); out.patents(c.mine); out.patents(c.theirs); out.writeVarInt(c.termDays)
            }
            is IpCommand.FormPool -> {
                out.writeByte(T_FORM_POOL); out.writeVarLong(c.actor.value); out.writeString(c.name); out.writeVarInt(c.field.ordinal)
                IpCodec.writeTerms(c.terms, out); out.writeVarLong(c.adminFeePpm)
            }
            is IpCommand.JoinPool -> { out.writeByte(T_JOIN_POOL); out.writeVarLong(c.actor.value); out.writeVarLong(c.pool.value); out.patents(c.patents) }
            is IpCommand.LeavePool -> { out.writeByte(T_LEAVE_POOL); out.writeVarLong(c.actor.value); out.writeVarLong(c.pool.value) }
            is IpCommand.DissolvePool -> { out.writeByte(T_DISSOLVE_POOL); out.writeVarLong(c.actor.value); out.writeVarLong(c.pool.value) }
            is IpCommand.OfferPool -> { out.writeByte(T_OFFER_POOL); out.writeVarLong(c.actor.value); out.writeVarLong(c.pool.value); out.writeVarLong(c.licensee.value) }
            is IpCommand.ReportInfringement -> {
                out.writeByte(T_REPORT); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.defendant.value); out.writeVarLong(c.country.value)
            }
            is IpCommand.SendNotice -> {
                out.writeByte(T_NOTICE); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value)
                out.writeBoolean(c.demand != null); c.demand?.let { out.writeMoney(it) }
            }
            is IpCommand.FileSuit -> { out.writeByte(T_SUIT); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value) }
            is IpCommand.Appeal -> { out.writeByte(T_APPEAL); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value) }
            is IpCommand.WithdrawSuit -> { out.writeByte(T_WITHDRAW_SUIT); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value) }
            is IpCommand.OfferSettlement -> { out.writeByte(T_OFFER_SETTLE); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value); out.writeMoney(c.amount) }
            is IpCommand.AcceptSettlement -> { out.writeByte(T_ACCEPT_SETTLE); out.writeVarLong(c.actor.value); out.writeVarLong(c.dispute.value) }
            is IpCommand.ChallengePatent -> { out.writeByte(T_CHALLENGE); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.country.value) }
            is IpCommand.StartDesignAround -> { out.writeByte(T_DESIGN); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.dispute?.value ?: -1L) }
            is IpCommand.CancelDesignAround -> { out.writeByte(T_CANCEL_DESIGN); out.writeVarLong(c.actor.value); out.writeVarLong(c.id.value) }
            is IpCommand.RequestCompulsory -> {
                out.writeByte(T_COMPULSORY); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.country.value); out.writeVarInt(c.ground.ordinal)
            }
            is IpCommand.WithdrawCompulsory -> { out.writeByte(T_WITHDRAW_COMPULSORY); out.writeVarLong(c.actor.value); out.writeVarLong(c.id.value) }
            is IpCommand.ListPatent -> { out.writeByte(T_LIST); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value); out.writeMoney(c.price) }
            is IpCommand.UnlistPatent -> { out.writeByte(T_UNLIST); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value) }
            is IpCommand.BuyPatent -> { out.writeByte(T_BUY); out.writeVarLong(c.actor.value); out.writeVarLong(c.patent.value) }
            is IpCommand.SetPolicy -> { out.writeByte(T_POLICY); out.writeVarLong(c.actor.value); IpCodec.writePolicy(c.policy, out) }
        }
    }

    fun read(r: ByteReader): IpCommand {
        val tag = r.readByte()
        val actor = CompanyId(r.readVarLong())
        fun optLong(): Long? = r.readVarLong().let { if (it < 0L) null else it }
        return when (tag) {
            T_FILE -> {
                val tech = TechId(r.readString()); val countries = r.countries()
                val breadth = IpCodec.pick(BreadthChoice.values(), r.readVarInt(), "breadth"); val claims = r.readVarInt()
                val idx = r.readVarInt()
                IpCommand.FilePatent(actor, tech, countries, breadth, claims, if (idx < 0) null else IpCodec.pick(IndustryFamily.values(), idx, "industry"))
            }
            T_ABANDON -> IpCommand.AbandonPatent(actor, PatentId(r.readVarLong()), optLong()?.let { CountryId(it) })
            T_AUTO -> IpCommand.SetAutoMaintain(actor, PatentId(r.readVarLong()), r.readBoolean())
            T_PAY_MAINT -> IpCommand.PayMaintenance(actor, PatentId(r.readVarLong()), CountryId(r.readVarLong()))
            T_ESSENTIAL -> IpCommand.DeclareEssential(actor, PatentId(r.readVarLong()), r.readBoolean())
            T_KEEP -> IpCommand.KeepSecret(actor, TechId(r.readString()))
            T_INVEST -> IpCommand.InvestInSecret(actor, SecretId(r.readVarLong()), r.readMoney())
            T_DROP -> IpCommand.DropSecret(actor, SecretId(r.readVarLong()))
            T_STEAL -> IpCommand.StealSecret(actor, SecretId(r.readVarLong()))
            T_OFFER -> {
                val licensor = IpOwner(IpCodec.pick(IpOwnerKind.values(), r.readVarInt(), "owner kind"), r.readVarLong())
                val licensee = CompanyId(r.readVarLong()); val patents = r.patents(); val terms = IpCodec.readTerms(r)
                val kind = IpCodec.pick(LicenseKind.values(), r.readVarInt(), "licence kind")
                val industries = List(r.readVarInt()) { IpCodec.pick(IndustryFamily.values(), r.readVarInt(), "industry") }
                val countries = r.countries(); val termDays = r.readVarInt(); val sub = r.readBoolean(); val pool = optLong()?.let { PoolId(it) }
                IpCommand.OfferLicense(actor, OfferRequest(licensor, licensee, patents, terms, kind, LicenseScope(industries, countries), termDays, sub, pool))
            }
            T_ACCEPT -> IpCommand.AcceptLicense(actor, LicenseId(r.readVarLong()))
            T_DECLINE -> IpCommand.DeclineLicense(actor, LicenseId(r.readVarLong()))
            T_WITHDRAW_OFFER -> IpCommand.WithdrawOffer(actor, LicenseId(r.readVarLong()))
            T_TERMINATE -> IpCommand.TerminateLicense(actor, LicenseId(r.readVarLong()))
            T_FRAND -> IpCommand.RequestFrandLicense(actor, PatentId(r.readVarLong()))
            T_AUDIT -> IpCommand.AuditLicense(actor, LicenseId(r.readVarLong()))
            T_CROSS -> IpCommand.ProposeCrossLicense(actor, CompanyId(r.readVarLong()), r.patents(), r.patents(), r.readVarInt())
            T_FORM_POOL -> IpCommand.FormPool(actor, r.readString(), IpCodec.pick(TechField.values(), r.readVarInt(), "field"), IpCodec.readTerms(r), r.readVarLong())
            T_JOIN_POOL -> IpCommand.JoinPool(actor, PoolId(r.readVarLong()), r.patents())
            T_LEAVE_POOL -> IpCommand.LeavePool(actor, PoolId(r.readVarLong()))
            T_DISSOLVE_POOL -> IpCommand.DissolvePool(actor, PoolId(r.readVarLong()))
            T_OFFER_POOL -> IpCommand.OfferPool(actor, PoolId(r.readVarLong()), CompanyId(r.readVarLong()))
            T_REPORT -> IpCommand.ReportInfringement(actor, PatentId(r.readVarLong()), CompanyId(r.readVarLong()), CountryId(r.readVarLong()))
            T_NOTICE -> IpCommand.SendNotice(actor, DisputeId(r.readVarLong()), if (r.readBoolean()) r.readMoney() else null)
            T_SUIT -> IpCommand.FileSuit(actor, DisputeId(r.readVarLong()))
            T_APPEAL -> IpCommand.Appeal(actor, DisputeId(r.readVarLong()))
            T_WITHDRAW_SUIT -> IpCommand.WithdrawSuit(actor, DisputeId(r.readVarLong()))
            T_OFFER_SETTLE -> IpCommand.OfferSettlement(actor, DisputeId(r.readVarLong()), r.readMoney())
            T_ACCEPT_SETTLE -> IpCommand.AcceptSettlement(actor, DisputeId(r.readVarLong()))
            T_CHALLENGE -> IpCommand.ChallengePatent(actor, PatentId(r.readVarLong()), CountryId(r.readVarLong()))
            T_DESIGN -> IpCommand.StartDesignAround(actor, PatentId(r.readVarLong()), optLong()?.let { DisputeId(it) })
            T_CANCEL_DESIGN -> IpCommand.CancelDesignAround(actor, DesignAroundId(r.readVarLong()))
            T_COMPULSORY -> IpCommand.RequestCompulsory(actor, PatentId(r.readVarLong()), CountryId(r.readVarLong()), IpCodec.pick(CompulsoryGround.values(), r.readVarInt(), "ground"))
            T_WITHDRAW_COMPULSORY -> IpCommand.WithdrawCompulsory(actor, CompulsoryId(r.readVarLong()))
            T_LIST -> IpCommand.ListPatent(actor, PatentId(r.readVarLong()), r.readMoney())
            T_UNLIST -> IpCommand.UnlistPatent(actor, PatentId(r.readVarLong()))
            T_BUY -> IpCommand.BuyPatent(actor, PatentId(r.readVarLong()))
            T_POLICY -> IpCommand.SetPolicy(actor, IpCodec.readPolicy(r))
            else -> throw ByteReader.CorruptDataException("unknown intellectual property command tag $tag")
        }
    }
}

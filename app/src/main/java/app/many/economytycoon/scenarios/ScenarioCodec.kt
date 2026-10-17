package app.many.economytycoon.scenarios

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Tick

/** Binary save format for the scenario a game is being played as. */
object ScenarioCodec {
    /** Written for games without a scenario and for catalog scenarios, so their saves are what they always were. */
    const val VERSION = 1
    /** Written when the run carries its own definition; the definition and the count of scripted events follow the usual fields. */
    const val VERSION_CUSTOM = 2

    fun write(s: ScenarioService, out: ByteWriter) {
        val r = s.run
        val custom = r?.custom
        out.writeVarInt(if (custom != null) VERSION_CUSTOM else VERSION)
        out.writeBoolean(r != null)
        if (r == null) return
        out.writeString(r.id); out.writeVarLong(r.playerId); out.writeVarLong(r.startedAt.value)
        out.writeVarInt(r.status.ordinal)
        val fin = r.finishedAt; out.writeBoolean(fin != null); if (fin != null) out.writeVarLong(fin.value)
        out.writeVarInt((r.reason?.ordinal ?: -1) + 1)
        out.writeVarInt(r.medal.ordinal); out.writeVarLong(r.reachedMask)
        if (custom != null) { ScenarioDefCodec.write(custom, out); out.writeVarInt(r.eventsDone) }
    }

    fun read(r: ByteReader, s: ScenarioService) {
        val version = r.readVarInt()
        if (version != VERSION && version != VERSION_CUSTOM) throw ByteReader.CorruptDataException("unsupported scenario save version $version")
        if (!r.readBoolean()) { s.restore(null); return }
        val id = r.readString()
        if (version == VERSION && ScenarioCatalog.byId(id) == null) throw ByteReader.CorruptDataException("unknown scenario '$id'")
        val playerId = r.readVarLong()
        val startedAt = Tick(r.readVarLong())
        val statusAt = r.readVarInt()
        val status = ScenarioStatus.values().getOrNull(statusAt) ?: throw ByteReader.CorruptDataException("unknown scenario status $statusAt")
        val fin = if (r.readBoolean()) Tick(r.readVarLong()) else null
        val reasonAt = r.readVarInt() - 1
        val reason = if (reasonAt < 0) null else LossReason.values().getOrNull(reasonAt) ?: throw ByteReader.CorruptDataException("unknown loss reason $reasonAt")
        val medalAt = r.readVarInt()
        val medal = Medal.values().getOrNull(medalAt) ?: throw ByteReader.CorruptDataException("unknown medal $medalAt")
        val mask = r.readVarLong()
        val custom = if (version == VERSION_CUSTOM) ScenarioDefCodec.read(r) else null
        if (custom != null && custom.id != id) throw ByteReader.CorruptDataException("scenario definition '${custom.id}' does not match run '$id'")
        val run = ScenarioRun(id, playerId, startedAt, custom)
        run.restore(status, fin, reason, medal, mask)
        if (custom != null) {
            val done = r.readVarInt()
            if (done !in 0..custom.events.size) throw ByteReader.CorruptDataException("scripted event count $done is out of range")
            run.restoreEvents(done)
        }
        s.restore(run)
    }
}

package app.many.economytycoon.scenarios

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Tick

/** Binary save format for the scenario a game is being played as. */
object ScenarioCodec {
    const val VERSION = 1

    fun write(s: ScenarioService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val r = s.run
        out.writeBoolean(r != null)
        if (r == null) return
        out.writeString(r.id); out.writeVarLong(r.playerId); out.writeVarLong(r.startedAt.value)
        out.writeVarInt(r.status.ordinal)
        val fin = r.finishedAt; out.writeBoolean(fin != null); if (fin != null) out.writeVarLong(fin.value)
        out.writeVarInt((r.reason?.ordinal ?: -1) + 1)
        out.writeVarInt(r.medal.ordinal); out.writeVarLong(r.reachedMask)
    }

    fun read(r: ByteReader, s: ScenarioService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported scenario save version $version")
        if (!r.readBoolean()) { s.restore(null); return }
        val id = r.readString()
        if (ScenarioCatalog.byId(id) == null) throw ByteReader.CorruptDataException("unknown scenario '$id'")
        val run = ScenarioRun(id, r.readVarLong(), Tick(r.readVarLong()))
        val statusAt = r.readVarInt()
        val status = ScenarioStatus.values().getOrNull(statusAt) ?: throw ByteReader.CorruptDataException("unknown scenario status $statusAt")
        val fin = if (r.readBoolean()) Tick(r.readVarLong()) else null
        val reasonAt = r.readVarInt() - 1
        val reason = if (reasonAt < 0) null else LossReason.values().getOrNull(reasonAt) ?: throw ByteReader.CorruptDataException("unknown loss reason $reasonAt")
        val medalAt = r.readVarInt()
        val medal = Medal.values().getOrNull(medalAt) ?: throw ByteReader.CorruptDataException("unknown medal $medalAt")
        run.restore(status, fin, reason, medal, r.readVarLong())
        s.restore(run)
    }
}

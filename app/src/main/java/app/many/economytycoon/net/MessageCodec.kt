package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

/** A decoded frame: the sender's per-connection counter and the message. */
class Packet(val seq: Long, val message: NetMessage)

/** The outcome of decoding bytes from the network. Hostile input produces [Bad], never an exception. */
sealed class Decoded {
    class Ok(val packet: Packet) : Decoded()
    data class Bad(val error: DecodeError, val detail: String) : Decoded()
}

/**
 * Binary layout of every message, built on the same primitives as the save format.
 *
 * ```
 * frame := magic(2) protocol:varint kind:byte seq:varlong body:bytes crc32:fixed64
 * ```
 * The checksum covers everything before it. The decoder is strict: sizes are bounded before anything is
 * allocated, text must be valid UTF-8 without control characters, every tag must be known, no byte may be left
 * over, and a frame is accepted only if encoding what was decoded gives back exactly the bytes received, so a
 * message has one spelling and nothing can hide in padding or over-long numbers.
 */
object MessageCodec {
    private const val MAGIC0 = 0xE7
    private const val MAGIC1 = 0x7C

    fun encode(seq: Long, message: NetMessage, protocol: Int = NetLimits.PROTOCOL): ByteArray {
        val body = ByteWriter()
        writeBody(message, body)
        val w = ByteWriter()
        w.writeByte(MAGIC0); w.writeByte(MAGIC1)
        w.writeVarInt(protocol)
        w.writeByte(message.kind.tag)
        w.writeVarLong(seq)
        w.writeBytes(body.toByteArray())
        val head = w.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(head)
        out.writeFixedLong(crc(head))
        val bytes = out.toByteArray()
        require(bytes.size <= NetLimits.MAX_FRAME_BYTES) { "frame of ${bytes.size} bytes exceeds the limit" }
        return bytes
    }

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.isEmpty()) return Decoded.Bad(DecodeError.EMPTY, "no data")
        if (bytes.size > NetLimits.MAX_FRAME_BYTES) return Decoded.Bad(DecodeError.TOO_LARGE, "${bytes.size} bytes")
        if (bytes.size < MIN_FRAME) return Decoded.Bad(DecodeError.TOO_SHORT, "${bytes.size} bytes")
        if ((bytes[0].toInt() and 0xFF) != MAGIC0 || (bytes[1].toInt() and 0xFF) != MAGIC1) return Decoded.Bad(DecodeError.BAD_MAGIC, "not a game frame")
        val headLength = bytes.size - 8
        val stored = ByteReader(bytes, headLength).readFixedLong()
        val head = bytes.copyOfRange(0, headLength)
        if (stored != crc(head)) return Decoded.Bad(DecodeError.BAD_CHECKSUM, "checksum mismatch")
        return try {
            val r = ByteReader(head, 2)
            val protocol = r.readVarInt()
            if (protocol < NetLimits.MIN_PROTOCOL || protocol > NetLimits.PROTOCOL) return Decoded.Bad(DecodeError.BAD_VERSION, "protocol $protocol")
            val tag = r.readByte()
            val kind = MessageKind.fromTag(tag) ?: return Decoded.Bad(DecodeError.UNKNOWN_KIND, "kind $tag")
            val seq = r.readVarLong()
            if (seq < 0) return Decoded.Bad(DecodeError.BAD_VALUE, "negative sequence")
            val body = r.readBytes()
            if (r.remaining != 0) return Decoded.Bad(DecodeError.TRAILING_BYTES, "${r.remaining} bytes after the body")
            val br = ByteReader(body)
            val message = Wire(br).read(kind)
            if (br.remaining != 0) return Decoded.Bad(DecodeError.TRAILING_BYTES, "${br.remaining} bytes after the message")
            if (!encode(seq, message, protocol).contentEquals(bytes)) return Decoded.Bad(DecodeError.NOT_CANONICAL, "non-canonical encoding")
            Decoded.Ok(Packet(seq, message))
        } catch (e: Refusal) {
            Decoded.Bad(e.error, e.message ?: e.error.name)
        } catch (e: ByteReader.CorruptDataException) {
            val truncated = e.message?.startsWith("unexpected end") == true || e.message?.startsWith("bad ") == true
            Decoded.Bad(if (truncated) DecodeError.TRUNCATED else DecodeError.BAD_VALUE, e.message ?: "corrupt")
        } catch (e: IllegalArgumentException) {
            Decoded.Bad(DecodeError.BAD_VALUE, e.message ?: "invalid value")
        }
    }

    /** Smallest possible frame: magic, protocol, kind, seq, empty body, checksum. */
    private const val MIN_FRAME = 2 + 1 + 1 + 1 + 1 + 8

    private fun crc(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value

    /** Internal signal for a refused value; converted to [Decoded.Bad] at the top of [decode]. */
    private class Refusal(val error: DecodeError, message: String) : RuntimeException(message)

    // ---- writing ----

    private fun writeBody(m: NetMessage, w: ByteWriter) {
        when (m) {
            is NetMessage.Hello -> {
                w.writeVarInt(m.protocol); w.writeVarInt(m.minProtocol); w.writeVarLong(m.capabilities)
                w.writeString(m.name); w.writeVarLong(m.session.value); w.writeString(m.content); w.writeBoolean(m.spectate)
                w.writeBoolean(m.resume != null)
                m.resume?.let { w.writeVarLong(it.player.value); w.writeVarLong(it.nonce); w.writeVarLong(it.lastTick); w.writeBytesRaw(it.proof) }
            }
            is NetMessage.Welcome -> {
                w.writeVarInt(m.protocol); w.writeVarLong(m.capabilities); w.writeVarLong(m.player.value); w.writeByte(m.role.ordinal)
                w.writeVarLong(m.session.value); w.writeBytesRaw(m.token); w.writeVarLong(m.tick); w.writeVarInt(m.commandDelay)
                w.writeVarInt(m.hashInterval); w.writeBoolean(m.needsSnapshot); w.writeVarLong(m.seed); w.writeFixedLong(m.chain)
            }
            is NetMessage.Error -> { w.writeVarInt(m.error.code); w.writeString(m.detail) }
            is NetMessage.Bye -> w.writeByte(m.reason.tag)
            is NetMessage.Submit -> { w.writeVarLong(m.seq); w.writeVarLong(m.targetTick); w.writeBytes(m.payload); w.writeBytesRaw(m.mac) }
            is NetMessage.Ack -> { w.writeVarLong(m.seq); w.writeVarLong(m.targetTick) }
            is NetMessage.Reject -> { w.writeVarLong(m.seq); w.writeVarInt(m.error.code); w.writeString(m.rule); w.writeVarLong(m.serverTick) }
            is NetMessage.Commit -> {
                w.writeVarLong(m.tick)
                w.writeVarInt(m.entries.size)
                for (e in m.entries) { w.writeVarLong(e.player.value); w.writeVarLong(e.seq); w.writeBytes(e.payload) }
                w.writeVarInt(m.transfers.size)
                for (t in m.transfers) { w.writeVarLong(t.trade.value); w.writeVarLong(t.from); w.writeVarLong(t.to); writeAsset(t.asset, w) }
                w.writeFixedLong(m.chain)
            }
            is NetMessage.Resend -> { w.writeVarLong(m.fromTick); w.writeVarInt(m.count) }
            is NetMessage.HashReport -> { w.writeVarLong(m.tick); w.writeBytesRaw(m.hash) }
            is NetMessage.HashCheckpoint -> { w.writeVarLong(m.tick); w.writeBytesRaw(m.hash) }
            is NetMessage.DesyncNotice -> { w.writeVarLong(m.tick); w.writeBytesRaw(m.serverHash) }
            is NetMessage.SnapshotRequest -> { w.writeByte(m.reason.tag); w.writeVarLong(m.snapshotId); w.writeVarInt(m.resumeChunk) }
            is NetMessage.SnapshotHeader -> {
                w.writeVarLong(m.snapshotId); w.writeVarLong(m.tick); w.writeVarInt(m.totalBytes); w.writeVarInt(m.chunkSize)
                w.writeVarInt(m.chunkCount); w.writeBytesRaw(m.digest); w.writeBytesRaw(m.stateHash); w.writeFixedLong(m.chain)
            }
            is NetMessage.SnapshotChunk -> { w.writeVarLong(m.snapshotId); w.writeVarInt(m.index); w.writeBytes(m.data); w.writeFixedLong(m.crc) }
            is NetMessage.SnapshotAck -> { w.writeVarLong(m.snapshotId); w.writeVarInt(m.next); w.writeBoolean(m.complete) }
            is NetMessage.Ping -> { w.writeVarLong(m.nonce); w.writeVarLong(m.sentAt) }
            is NetMessage.Pong -> { w.writeVarLong(m.nonce); w.writeVarLong(m.sentAt); w.writeVarLong(m.serverTick) }
            is NetMessage.LobbyRequest -> { w.writeByte(m.action.tag); w.writeVarLong(m.target.value); w.writeVarLong(m.arg) }
            is NetMessage.LobbyReply -> { w.writeVarLong(m.requestSeq); w.writeVarLong(m.version) }
            is NetMessage.LobbyState -> {
                w.writeVarLong(m.version); w.writeByte(m.phase.tag); w.writeVarLong(m.host.value)
                w.writeVarInt(m.members.size)
                for (x in m.members) {
                    w.writeVarLong(x.id.value); w.writeString(x.name); w.writeByte(x.role.ordinal); w.writeVarInt(x.slot)
                    w.writeBoolean(x.ready); w.writeByte(x.presence.tag)
                }
            }
            is NetMessage.Chat -> { w.writeByte(m.chatKind.tag); w.writeVarInt(m.emote); w.writeString(m.text) }
            is NetMessage.ChatBroadcast -> { w.writeVarLong(m.from.value); w.writeByte(m.chatKind.tag); w.writeVarInt(m.emote); w.writeString(m.text); w.writeVarLong(m.tick) }
            is NetMessage.TradeRequest -> {
                w.writeByte(m.action.tag); w.writeVarLong(m.trade.value); w.writeVarLong(m.counterparty.value)
                writeAssets(m.give, w); writeAssets(m.want, w); w.writeVarInt(m.ttlTicks)
            }
            is NetMessage.TradeNotice -> {
                w.writeVarLong(m.trade.value); w.writeByte(m.state.tag); w.writeVarLong(m.from.value); w.writeVarLong(m.to.value)
                writeAssets(m.give, w); writeAssets(m.want, w); w.writeVarLong(m.expiresAt)
            }
        }
    }

    private fun writeAsset(a: TradeAsset, w: ByteWriter) { w.writeByte(a.kind.tag); w.writeString(a.key); w.writeVarLong(a.amount) }

    private fun writeAssets(list: List<TradeAsset>, w: ByteWriter) {
        w.writeVarInt(list.size)
        for (a in list) writeAsset(a, w)
    }

    // ---- reading ----

    private class Wire(private val r: ByteReader) {
        private fun refuse(error: DecodeError, message: String): Nothing = throw Refusal(error, message)

        fun byte(): Int = r.readByte()
        fun bool(): Boolean = r.readBoolean()
        fun long(): Long = r.readVarLong()
        fun nonNegative(what: String): Long = r.readVarLong().also { if (it < 0) refuse(DecodeError.BAD_VALUE, "negative $what") }
        fun int(min: Int, max: Int, what: String): Int = r.readVarInt().also { if (it < min || it > max) refuse(DecodeError.BAD_VALUE, "$what $it outside $min..$max") }
        fun fixed(): Long = r.readFixedLong()

        fun text(maxChars: Int, what: String): String {
            val raw = r.readBytes()
            if (raw.size > maxChars * 4) refuse(DecodeError.TOO_LARGE, "$what is too long")
            val s = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString()
            } catch (e: CharacterCodingException) {
                refuse(DecodeError.BAD_TEXT, "$what is not valid UTF-8")
            }
            if (s.codePointCount(0, s.length) > maxChars) refuse(DecodeError.TOO_LARGE, "$what is too long")
            if (s.any { it.isISOControl() }) refuse(DecodeError.BAD_TEXT, "$what contains control characters")
            return s
        }

        fun blob(max: Int, what: String): ByteArray {
            val b = r.readBytes()
            if (b.size > max) refuse(DecodeError.TOO_LARGE, "$what is ${b.size} bytes")
            return b
        }

        fun fixedBytes(n: Int): ByteArray {
            if (r.remaining < n) refuse(DecodeError.TRUNCATED, "expected $n bytes")
            return ByteArray(n) { r.readByte().toByte() }
        }

        fun <T> list(max: Int, what: String, item: () -> T): List<T> {
            val n = r.readVarInt()
            if (n < 0 || n > max) refuse(DecodeError.TOO_MANY_ITEMS, "$what count $n")
            if (n > r.remaining) refuse(DecodeError.TRUNCATED, "$what count $n exceeds the data")
            return List(n) { item() }
        }

        fun <E> tag(lookup: (Int) -> E?, what: String): E {
            val t = r.readByte()
            return lookup(t) ?: refuse(DecodeError.BAD_VALUE, "unknown $what $t")
        }

        fun role(): Role = Role.values().getOrNull(r.readByte()) ?: refuse(DecodeError.BAD_VALUE, "unknown role")
        fun error(): NetError = NetError.fromCode(r.readVarInt()) ?: refuse(DecodeError.BAD_VALUE, "unknown error code")

        fun asset(): TradeAsset {
            val kind = tag(AssetKind::fromTag, "asset kind")
            val key = text(NetLimits.MAX_KEY_CHARS, "asset key")
            val amount = long()
            if (amount <= 0L) refuse(DecodeError.BAD_VALUE, "asset amount must be positive")
            if (kind == AssetKind.CASH && key.isNotEmpty()) refuse(DecodeError.BAD_VALUE, "cash has no key")
            if (kind == AssetKind.GOODS && key.isEmpty()) refuse(DecodeError.BAD_VALUE, "goods need a key")
            return TradeAsset(kind, key, amount)
        }

        fun assets(what: String): List<TradeAsset> = list(NetLimits.MAX_TRADE_ASSETS, what) { asset() }

        fun read(kind: MessageKind): NetMessage = when (kind) {
            MessageKind.HELLO -> {
                val protocol = int(0, 65_535, "protocol"); val min = int(0, 65_535, "minimum protocol")
                val caps = long(); val name = text(NetLimits.MAX_NAME_CHARS, "name"); val session = SessionId(long())
                val content = text(NetLimits.MAX_KEY_CHARS, "content id"); val spectate = bool()
                val resume = if (bool()) ResumeInfo(PlayerId(nonNegative("player")), nonNegative("nonce"), nonNegative("tick"), fixedBytes(NetLimits.MAC_BYTES)) else null
                NetMessage.Hello(protocol, min, caps, name, session, content, spectate, resume)
            }
            MessageKind.WELCOME -> NetMessage.Welcome(
                int(0, 65_535, "protocol"), long(), PlayerId(nonNegative("player")), role(), SessionId(long()),
                fixedBytes(NetLimits.TOKEN_BYTES), nonNegative("tick"), int(0, 1_000, "command delay"), int(1, 100_000, "hash interval"), bool(), long(), fixed(),
            )
            MessageKind.ERROR -> NetMessage.Error(error(), text(NetLimits.MAX_DETAIL_CHARS, "detail"))
            MessageKind.BYE -> NetMessage.Bye(tag(ByeReason::fromTag, "bye reason"))
            MessageKind.SUBMIT -> NetMessage.Submit(nonNegative("sequence"), nonNegative("tick"), blob(NetLimits.MAX_COMMAND_BYTES, "command"), fixedBytes(NetLimits.MAC_BYTES))
            MessageKind.ACK -> NetMessage.Ack(nonNegative("sequence"), nonNegative("tick"))
            MessageKind.REJECT -> NetMessage.Reject(nonNegative("sequence"), error(), text(NetLimits.MAX_KEY_CHARS, "rule"), nonNegative("tick"))
            MessageKind.COMMIT -> {
                val tick = nonNegative("tick")
                val entries = list(NetLimits.MAX_COMMIT_ENTRIES, "entries") {
                    CommitEntry(PlayerId(nonNegative("player")), nonNegative("sequence"), blob(NetLimits.MAX_COMMAND_BYTES, "command"))
                }
                val transfers = list(NetLimits.MAX_TRANSFERS, "transfers") {
                    Transfer(TradeId(nonNegative("trade")), nonNegative("company"), nonNegative("company"), asset())
                }
                NetMessage.Commit(tick, entries, transfers, fixed())
            }
            MessageKind.RESEND -> NetMessage.Resend(nonNegative("tick"), int(1, NetLimits.MAX_RESEND_TICKS, "count"))
            MessageKind.HASH_REPORT -> NetMessage.HashReport(nonNegative("tick"), fixedBytes(NetLimits.HASH_BYTES))
            MessageKind.HASH_CHECKPOINT -> NetMessage.HashCheckpoint(nonNegative("tick"), fixedBytes(NetLimits.HASH_BYTES))
            MessageKind.DESYNC_NOTICE -> NetMessage.DesyncNotice(nonNegative("tick"), fixedBytes(NetLimits.HASH_BYTES))
            MessageKind.SNAPSHOT_REQUEST -> NetMessage.SnapshotRequest(tag(SnapshotReason::fromTag, "snapshot reason"), nonNegative("snapshot"), int(0, NetLimits.MAX_SNAPSHOT_CHUNKS, "chunk"))
            MessageKind.SNAPSHOT_HEADER -> {
                val id = nonNegative("snapshot"); val tick = nonNegative("tick")
                val total = int(1, NetLimits.MAX_SNAPSHOT_BYTES, "size"); val chunkSize = int(1, NetLimits.MAX_CHUNK_BYTES, "chunk size")
                val count = int(1, NetLimits.MAX_SNAPSHOT_CHUNKS, "chunk count")
                if (count != (total + chunkSize - 1) / chunkSize) refuse(DecodeError.BAD_VALUE, "chunk count does not fit the size")
                NetMessage.SnapshotHeader(id, tick, total, chunkSize, count, fixedBytes(32), fixedBytes(NetLimits.HASH_BYTES), fixed())
            }
            MessageKind.SNAPSHOT_CHUNK -> NetMessage.SnapshotChunk(nonNegative("snapshot"), int(0, NetLimits.MAX_SNAPSHOT_CHUNKS, "chunk"), blob(NetLimits.MAX_CHUNK_BYTES, "chunk"), fixed())
            MessageKind.SNAPSHOT_ACK -> NetMessage.SnapshotAck(nonNegative("snapshot"), int(0, NetLimits.MAX_SNAPSHOT_CHUNKS, "chunk"), bool())
            MessageKind.PING -> NetMessage.Ping(long(), long())
            MessageKind.PONG -> NetMessage.Pong(long(), long(), nonNegative("tick"))
            MessageKind.LOBBY_REQUEST -> NetMessage.LobbyRequest(tag(LobbyAction::fromTag, "lobby action"), PlayerId(nonNegative("player")), long())
            MessageKind.LOBBY_REPLY -> NetMessage.LobbyReply(nonNegative("request"), nonNegative("version"))
            MessageKind.LOBBY_STATE -> NetMessage.LobbyState(
                nonNegative("version"), tag(LobbyPhase::fromTag, "phase"), PlayerId(nonNegative("host")),
                list(NetLimits.MAX_MEMBERS, "members") {
                    MemberInfo(PlayerId(nonNegative("player")), text(NetLimits.MAX_NAME_CHARS, "name"), role(), int(-1, NetLimits.MAX_PLAYERS, "slot"), bool(), tag(Presence::fromTag, "presence"))
                },
            )
            MessageKind.CHAT -> NetMessage.Chat(tag(ChatKind::fromTag, "chat kind"), int(0, NetLimits.MAX_EMOTE, "emote"), text(NetLimits.MAX_CHAT_CHARS, "chat text"))
            MessageKind.CHAT_BROADCAST -> NetMessage.ChatBroadcast(
                PlayerId(nonNegative("player")), tag(ChatKind::fromTag, "chat kind"), int(0, NetLimits.MAX_EMOTE, "emote"),
                text(NetLimits.MAX_CHAT_CHARS, "chat text"), nonNegative("tick"),
            )
            MessageKind.TRADE_REQUEST -> NetMessage.TradeRequest(
                tag(TradeAction::fromTag, "trade action"), TradeId(nonNegative("trade")), PlayerId(nonNegative("player")),
                assets("give"), assets("want"), int(0, 100_000, "ttl"),
            )
            MessageKind.TRADE_NOTICE -> NetMessage.TradeNotice(
                TradeId(nonNegative("trade")), tag(TradeState::fromTag, "trade state"), PlayerId(nonNegative("player")), PlayerId(nonNegative("player")),
                assets("give"), assets("want"), nonNegative("tick"),
            )
        }
    }
}

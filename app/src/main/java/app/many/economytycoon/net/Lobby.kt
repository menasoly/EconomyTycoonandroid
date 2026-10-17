package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules

/** Who takes over the host duties when the host goes away. */
enum class HostPolicy {
    /** The connected player who joined earliest, then the earliest connected spectator. */
    OLDEST_PLAYER,

    /** The earliest connected member whatever their role. */
    OLDEST_MEMBER,

    /** The session ends with its host. */
    CLOSE_SESSION,
}

data class LobbyConfig(
    val maxPlayers: Int = 4,
    val maxSpectators: Int = 8,
    val minPlayers: Int = 1,
    /** How long a dropped member keeps their place before it is given up, in network milliseconds. */
    val graceMs: Long = 60_000L,
    val hostPolicy: HostPolicy = HostPolicy.OLDEST_PLAYER,
    val allowSpectators: Boolean = true,
    /** Seats nobody took are played by computer-controlled companies. */
    val fillWithAi: Boolean = true,
    val requireAllReady: Boolean = true,
) {
    init {
        require(maxPlayers in 1..NetLimits.MAX_PLAYERS && maxSpectators >= 0 && minPlayers in 1..maxPlayers) { "lobby limits are inconsistent" }
        require(maxPlayers + maxSpectators <= NetLimits.MAX_MEMBERS) { "too many members" }
        require(graceMs >= 0L) { "grace period cannot be negative" }
    }
}

/** One person in the lobby. Read-only outside the lobby. */
class Member internal constructor(
    val id: PlayerId,
    name: String,
    val joinOrder: Int,
) {
    var name: String = name
        internal set
    var role: Role = Role.PLAYER
        internal set
    var slot: Int = -1
        internal set
    var ready: Boolean = false
        internal set
    var presence: Presence = Presence.CONNECTED
        internal set
    var droppedAt: Long = 0L
        internal set

    /** Company the member runs once the game has started, or -1. */
    var company: Long = -1L
        internal set

    fun info() = MemberInfo(id, name, role, slot, ready, presence)
}

sealed class LobbyEvent {
    data class Joined(val member: PlayerId, val role: Role) : LobbyEvent()
    data class Left(val member: PlayerId, val reason: ByeReason) : LobbyEvent()
    data class Kicked(val member: PlayerId, val by: PlayerId) : LobbyEvent()
    data class ReadyChanged(val member: PlayerId, val ready: Boolean) : LobbyEvent()
    data class RoleChanged(val member: PlayerId, val role: Role) : LobbyEvent()
    data class HostChanged(val from: PlayerId, val to: PlayerId) : LobbyEvent()
    data class Dropped(val member: PlayerId) : LobbyEvent()
    data class Returned(val member: PlayerId) : LobbyEvent()
    data class GaveUp(val member: PlayerId, val keptSeat: Boolean) : LobbyEvent()
    data class Started(val plan: StartPlan) : LobbyEvent()
    data class Closed(val reason: String) : LobbyEvent()
}

data class SeatAssignment(val slot: Int, val player: PlayerId, val name: String)

/** The seats at the start of a game: the people in slot order and how many seats the computer fills. */
data class StartPlan(val seats: List<SeatAssignment>, val aiSeats: Int)

class LobbyView(val version: Long, val phase: LobbyPhase, val host: PlayerId, val members: List<MemberInfo>) {
    fun toMessage() = NetMessage.LobbyState(version, phase, host, members)
    val players: List<MemberInfo> get() = members.filter { it.role == Role.PLAYER }
    val spectators: List<MemberInfo> get() = members.filter { it.role == Role.SPECTATOR }
}

/**
 * Who is in a session and in what capacity. It is a plain state machine: every operation checks its rules and
 * either changes the lobby and reports what happened or returns the rule it broke. Time arrives as an argument,
 * so grace periods are reproducible. People who are present but not yet in a seat are spectators.
 */
class Lobby(val config: LobbyConfig = LobbyConfig()) {
    private val members = java.util.TreeMap<PlayerId, Member>()
    private val banned = java.util.TreeSet<PlayerId>()
    private var nextOrder = 0
    private var host: PlayerId = PlayerId.SERVER

    var phase: LobbyPhase = LobbyPhase.OPEN
        private set
    var version: Long = 0L
        private set

    val hostId: PlayerId get() = host
    fun member(id: PlayerId): Member? = members[id]
    fun members(): List<Member> = members.values.sortedBy { it.joinOrder }
    fun players(): List<Member> = members().filter { it.role == Role.PLAYER }
    fun spectators(): List<Member> = members().filter { it.role == Role.SPECTATOR }
    fun isBanned(id: PlayerId) = id in banned

    fun view(): LobbyView = LobbyView(version, phase, host, members().map { it.info() })

    private fun ok(vararg events: LobbyEvent): Outcome<List<LobbyEvent>> { version++; return Outcome.ok(events.toList()) }
    private fun okList(events: List<LobbyEvent>): Outcome<List<LobbyEvent>> { version++; return Outcome.ok(events) }
    private fun fail(rule: String, message: String): Outcome<List<LobbyEvent>> = Outcome.reject(rule, message)

    private fun freeSlot(): Int? = (0 until config.maxPlayers).firstOrNull { s -> members.values.none { it.role == Role.PLAYER && it.slot == s } }

    // ---- joining and leaving ----

    /** Adds a new member. During a game only spectators can be added; a returning player uses [reconnect]. */
    fun join(id: PlayerId, rawName: String, now: Long, spectate: Boolean = false): Outcome<List<LobbyEvent>> {
        if (phase == LobbyPhase.CLOSED) return fail(Rules.WRONG_STATE, "the session is closed")
        if (id in banned) return fail(Rules.REGULATION, "banned from this session")
        if (members.containsKey(id)) return fail(Rules.DUPLICATE, "already in the session")
        val name = NameRules.clean(rawName) ?: return fail(Rules.INVALID_ARGUMENT, "that name cannot be used")
        if (members.values.any { it.name.equals(name, ignoreCase = true) }) return fail(Rules.NAME_TAKEN, "the name is taken")
        val wantsSeat = !spectate && phase == LobbyPhase.OPEN
        if (!spectate && phase == LobbyPhase.RUNNING && !config.allowSpectators) return fail(Rules.WRONG_STATE, "the game has started")
        val role: Role
        var slot = -1
        if (wantsSeat) {
            val free = freeSlot()
            if (free != null) { role = Role.PLAYER; slot = free }
            else if (config.allowSpectators && spectators().size < config.maxSpectators) role = Role.SPECTATOR
            else return fail(Rules.LIMIT_EXCEEDED, "the session is full")
        } else {
            if (!config.allowSpectators) return fail(Rules.WRONG_STATE, "spectators are not allowed")
            if (spectators().size >= config.maxSpectators) return fail(Rules.LIMIT_EXCEEDED, "no room for more spectators")
            role = Role.SPECTATOR
        }
        val m = Member(id, name, nextOrder++)
        m.role = role; m.slot = slot; m.droppedAt = now
        members[id] = m
        val events = ArrayList<LobbyEvent>()
        events += LobbyEvent.Joined(id, role)
        if (host == PlayerId.SERVER) { host = id; events += LobbyEvent.HostChanged(PlayerId.SERVER, id) }
        return okList(events)
    }

    /** Removes a member for good during setup; during a game a player's seat is kept for them. */
    fun leave(id: PlayerId, reason: ByeReason = ByeReason.LEAVE): Outcome<List<LobbyEvent>> {
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        val events = ArrayList<LobbyEvent>()
        if (phase == LobbyPhase.RUNNING && m.role == Role.PLAYER) {
            m.presence = Presence.GONE; m.ready = false
            events += LobbyEvent.Left(id, reason)
        } else {
            members.remove(id)
            events += LobbyEvent.Left(id, reason)
        }
        events += handOverHost(id)
        return okList(events)
    }

    fun kick(by: PlayerId, target: PlayerId): Outcome<List<LobbyEvent>> {
        if (by != host) return fail(Rules.NOT_OWNER, "only the host can remove people")
        if (target == by) return fail(Rules.INVALID_ARGUMENT, "the host cannot kick themselves")
        val m = members[target] ?: return fail(Rules.UNKNOWN_ENTITY, "no such member")
        banned += target
        if (phase == LobbyPhase.RUNNING && m.role == Role.PLAYER) { m.presence = Presence.GONE; m.ready = false } else members.remove(target)
        return ok(LobbyEvent.Kicked(target, by))
    }

    /** Removes a member on the server's own authority, optionally for good. During a game a player's seat is kept. */
    fun expel(target: PlayerId, ban: Boolean): Outcome<List<LobbyEvent>> {
        val m = members[target] ?: return fail(Rules.UNKNOWN_ENTITY, "no such member")
        if (ban) banned += target
        if (phase == LobbyPhase.RUNNING && m.role == Role.PLAYER) { m.presence = Presence.GONE; m.ready = false } else members.remove(target)
        val events = arrayListOf<LobbyEvent>(LobbyEvent.Kicked(target, PlayerId.SERVER))
        events += handOverHost(target)
        return okList(events)
    }

    // ---- setup ----

    fun setReady(id: PlayerId, ready: Boolean): Outcome<List<LobbyEvent>> {
        if (phase != LobbyPhase.OPEN) return fail(Rules.WRONG_STATE, "the game has started")
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        if (m.role != Role.PLAYER) return fail(Rules.WRONG_STATE, "spectators do not ready up")
        if (m.ready == ready) return fail(Rules.DUPLICATE, "no change")
        m.ready = ready
        return ok(LobbyEvent.ReadyChanged(id, ready))
    }

    /** Moves between a seat and the gallery before the game starts. */
    fun setSpectating(id: PlayerId, spectate: Boolean): Outcome<List<LobbyEvent>> {
        if (phase != LobbyPhase.OPEN) return fail(Rules.WRONG_STATE, "roles are fixed once the game has started")
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        if (spectate) {
            if (m.role == Role.SPECTATOR) return fail(Rules.DUPLICATE, "already spectating")
            if (!config.allowSpectators) return fail(Rules.WRONG_STATE, "spectators are not allowed")
            if (spectators().size >= config.maxSpectators) return fail(Rules.LIMIT_EXCEEDED, "no room for more spectators")
            m.role = Role.SPECTATOR; m.slot = -1; m.ready = false
        } else {
            if (m.role == Role.PLAYER) return fail(Rules.DUPLICATE, "already playing")
            val slot = freeSlot() ?: return fail(Rules.LIMIT_EXCEEDED, "every seat is taken")
            m.role = Role.PLAYER; m.slot = slot
        }
        return ok(LobbyEvent.RoleChanged(id, m.role))
    }

    fun transferHost(by: PlayerId, to: PlayerId): Outcome<List<LobbyEvent>> {
        if (by != host) return fail(Rules.NOT_OWNER, "only the host can hand over")
        val target = members[to] ?: return fail(Rules.UNKNOWN_ENTITY, "no such member")
        if (to == by) return fail(Rules.DUPLICATE, "already the host")
        if (target.presence != Presence.CONNECTED) return fail(Rules.WRONG_STATE, "that member is not connected")
        host = to
        return ok(LobbyEvent.HostChanged(by, to))
    }

    /** Renames a member; the same rules as joining apply. */
    fun rename(id: PlayerId, rawName: String): Outcome<List<LobbyEvent>> {
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        val name = NameRules.clean(rawName) ?: return fail(Rules.INVALID_ARGUMENT, "that name cannot be used")
        if (members.values.any { it.id != id && it.name.equals(name, ignoreCase = true) }) return fail(Rules.NAME_TAKEN, "the name is taken")
        m.name = name
        return okList(emptyList())
    }

    /** Locks the seats and begins the game. */
    fun start(by: PlayerId): Outcome<List<LobbyEvent>> {
        if (by != host) return fail(Rules.NOT_OWNER, "only the host can start the game")
        if (phase != LobbyPhase.OPEN) return fail(Rules.WRONG_STATE, "the game has already started")
        val seated = players().filter { it.presence == Presence.CONNECTED }
        if (seated.size < config.minPlayers) return fail(Rules.PREREQUISITE_MISSING, "at least ${config.minPlayers} players are needed")
        if (config.requireAllReady && seated.any { !it.ready }) return fail(Rules.PREREQUISITE_MISSING, "not everyone is ready")
        // Anyone still in a grace period at the start is dropped from their seat.
        for (m in players().filter { it.presence != Presence.CONNECTED }) members.remove(m.id)
        phase = LobbyPhase.RUNNING
        val seats = seated.sortedBy { it.slot }.map { SeatAssignment(it.slot, it.id, it.name) }
        val ai = if (config.fillWithAi) config.maxPlayers - seats.size else 0
        return ok(LobbyEvent.Started(StartPlan(seats, ai)))
    }

    fun close(by: PlayerId, reason: String = "closed by the host"): Outcome<List<LobbyEvent>> {
        if (by != host && by != PlayerId.SERVER) return fail(Rules.NOT_OWNER, "only the host can close the session")
        if (phase == LobbyPhase.CLOSED) return fail(Rules.WRONG_STATE, "already closed")
        phase = LobbyPhase.CLOSED
        return ok(LobbyEvent.Closed(reason))
    }

    /** Records the company a started game gave to a player. */
    fun assignCompany(id: PlayerId, company: Long) { members[id]?.company = company }

    // ---- connection state ----

    fun dropped(id: PlayerId, now: Long): Outcome<List<LobbyEvent>> {
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        if (m.presence != Presence.CONNECTED) return fail(Rules.DUPLICATE, "already dropped")
        m.presence = Presence.GRACE; m.droppedAt = now
        val events = arrayListOf<LobbyEvent>(LobbyEvent.Dropped(id))
        events += handOverHost(id, keepMember = true)
        return okList(events)
    }

    /** A member's connection is back. Valid during the grace period and, for players, afterwards while the seat is kept. */
    fun returned(id: PlayerId): Outcome<List<LobbyEvent>> {
        val m = members[id] ?: return fail(Rules.UNKNOWN_ENTITY, "not in the session")
        if (id in banned) return fail(Rules.REGULATION, "banned from this session")
        if (m.presence == Presence.CONNECTED) return fail(Rules.DUPLICATE, "already connected")
        m.presence = Presence.CONNECTED
        val events = arrayListOf<LobbyEvent>(LobbyEvent.Returned(id))
        if (host == PlayerId.SERVER) { host = id; events += LobbyEvent.HostChanged(PlayerId.SERVER, id) }
        return okList(events)
    }

    /** Gives up the places of members whose grace period has run out. */
    fun expire(now: Long): List<LobbyEvent> {
        val events = ArrayList<LobbyEvent>()
        for (m in members().filter { it.presence == Presence.GRACE && now - it.droppedAt >= config.graceMs }) {
            if (phase == LobbyPhase.RUNNING && m.role == Role.PLAYER) {
                m.presence = Presence.GONE
                events += LobbyEvent.GaveUp(m.id, keptSeat = true)
            } else {
                members.remove(m.id)
                events += LobbyEvent.GaveUp(m.id, keptSeat = false)
            }
            events += handOverHost(m.id)
        }
        if (events.isNotEmpty()) version++
        return events
    }

    private fun handOverHost(gone: PlayerId, keepMember: Boolean = false): List<LobbyEvent> {
        if (host != gone) return emptyList()
        if (config.hostPolicy == HostPolicy.CLOSE_SESSION) {
            if (phase != LobbyPhase.CLOSED) { phase = LobbyPhase.CLOSED; return listOf(LobbyEvent.Closed("the host left")) }
            return emptyList()
        }
        val candidates = members().filter { it.id != gone && it.presence == Presence.CONNECTED }
        val next = when (config.hostPolicy) {
            HostPolicy.OLDEST_PLAYER -> candidates.firstOrNull { it.role == Role.PLAYER } ?: candidates.firstOrNull()
            HostPolicy.OLDEST_MEMBER -> candidates.firstOrNull()
            HostPolicy.CLOSE_SESSION -> null
        }
        if (next == null) {
            // Nobody is around to take over. A host who only dropped keeps the title for when they return.
            if (keepMember) return emptyList()
            host = PlayerId.SERVER
            return listOf(LobbyEvent.HostChanged(gone, PlayerId.SERVER))
        }
        host = next.id
        return listOf(LobbyEvent.HostChanged(gone, next.id))
    }

    // ---- persistence ----

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeByte(phase.tag); w.writeVarLong(version); w.writeVarLong(host.value); w.writeVarInt(nextOrder)
        w.writeVarInt(members.size)
        for (m in members()) {
            w.writeVarLong(m.id.value); w.writeString(m.name); w.writeVarInt(m.joinOrder); w.writeByte(m.role.ordinal)
            w.writeVarInt(m.slot); w.writeBoolean(m.ready); w.writeByte(m.presence.tag); w.writeVarLong(m.droppedAt); w.writeVarLong(m.company)
        }
        w.writeVarInt(banned.size)
        for (b in banned) w.writeVarLong(b.value)
    }

    fun read(r: ByteReader) {
        val v = r.readVarInt()
        if (v != VERSION) throw ByteReader.CorruptDataException("unsupported lobby version $v")
        val newPhase = LobbyPhase.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown lobby phase")
        val newVersion = r.readVarLong(); val newHost = PlayerId(r.readVarLong()); val newOrder = r.readVarInt()
        val n = r.readVarInt()
        if (n < 0 || n > NetLimits.MAX_MEMBERS) throw ByteReader.CorruptDataException("implausible member count $n")
        val loaded = java.util.TreeMap<PlayerId, Member>()
        repeat(n) {
            val id = PlayerId(r.readVarLong())
            val m = Member(id, r.readString(), r.readVarInt())
            m.role = Role.values().getOrNull(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown role")
            m.slot = r.readVarInt(); m.ready = r.readBoolean()
            m.presence = Presence.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown presence")
            m.droppedAt = r.readVarLong(); m.company = r.readVarLong()
            loaded[id] = m
        }
        val b = r.readVarInt()
        if (b < 0 || b > 10_000) throw ByteReader.CorruptDataException("implausible ban count $b")
        val bans = java.util.TreeSet<PlayerId>()
        repeat(b) { bans += PlayerId(r.readVarLong()) }
        phase = newPhase; version = newVersion; host = newHost; nextOrder = newOrder
        members.clear(); members.putAll(loaded); banned.clear(); banned.addAll(bans)
    }

    companion object { const val VERSION = 1 }
}

package app.many.economytycoon.net

/**
 * Why a request was refused. The numeric [code] is part of the wire format and never changes meaning;
 * new reasons take new numbers. [fatal] reasons end the connection after being reported.
 */
enum class NetError(val code: Int, val fatal: Boolean, val text: String) {
    BAD_FRAME(1, false, "the message could not be read"),
    UNSUPPORTED_VERSION(2, true, "the protocol versions do not overlap"),
    NOT_JOINED(3, false, "join the session first"),
    SESSION_UNKNOWN(4, true, "there is no such session"),
    SESSION_FULL(5, true, "the session is full"),
    TOKEN_INVALID(6, true, "the session token is not valid"),
    CONTENT_MISMATCH(7, true, "the game content differs from the host's"),
    MISSING_CAPABILITY(8, true, "a required capability is missing"),
    RATE_LIMITED(9, false, "too many requests"),
    TOO_LARGE(10, false, "the message is too large"),
    DUPLICATE(11, false, "that sequence number was already used"),
    STALE_SEQUENCE(12, false, "that sequence number is too old"),
    SEQUENCE_AHEAD(13, false, "that sequence number is too far ahead"),
    BAD_SIGNATURE(14, false, "the signature does not match"),
    NOT_OWNER(15, false, "you do not run that company"),
    INVALID_COMMAND(16, false, "the command is not valid"),
    TOO_LATE(17, false, "the target tick has already been committed"),
    TOO_EARLY(18, false, "the target tick is too far ahead"),
    SPECTATOR(19, false, "spectators cannot act"),
    KICKED(20, true, "removed from the session"),
    BANNED(21, true, "banned from the session"),
    DESYNC(22, false, "the game state diverged"),
    SNAPSHOT_UNAVAILABLE(23, false, "no snapshot can be sent right now"),
    BAD_CHECKSUM(24, false, "the data was damaged in transit"),
    MUTED(25, false, "you are muted"),
    NOT_HOST(26, false, "only the host can do that"),
    WRONG_PHASE(27, false, "not possible in the current phase"),
    TRADE_REFUSED(28, false, "the trade was refused"),
    SERVER_BUSY(29, false, "the server is busy"),
    UNKNOWN_PLAYER(30, false, "no such player"),
    BAD_NAME(31, true, "that name cannot be used"),
    NAME_TAKEN(32, true, "that name is taken");

    companion object {
        private val byCode = values().associateBy { it.code }
        fun fromCode(code: Int): NetError? = byCode[code]
    }
}

/** Why a received frame was not accepted. Decoding returns these instead of throwing. */
enum class DecodeError {
    EMPTY, TOO_SHORT, TOO_LARGE, BAD_MAGIC, BAD_VERSION, BAD_CHECKSUM, UNKNOWN_KIND, TRUNCATED, TRAILING_BYTES,
    BAD_VALUE, BAD_TEXT, TOO_MANY_ITEMS, NOT_CANONICAL,
}

/** Hard bounds on everything that arrives from outside. A value over its bound is refused before it is used. */
object NetLimits {
    const val PROTOCOL = 1
    const val MIN_PROTOCOL = 1

    const val MAX_FRAME_BYTES = 32 * 1024
    const val MAX_COMMAND_BYTES = 2 * 1024
    const val MAX_CHUNK_BYTES = 16 * 1024
    const val MAX_SNAPSHOT_BYTES = 64 * 1024 * 1024
    const val MAX_SNAPSHOT_CHUNKS = MAX_SNAPSHOT_BYTES / 1024

    const val MAX_NAME_CHARS = 24
    const val MIN_NAME_CHARS = 2
    const val MAX_CHAT_CHARS = 200
    const val MAX_DETAIL_CHARS = 120
    const val MAX_KEY_CHARS = 64
    const val MAX_EMOTE = 15

    const val MAX_PLAYERS = 16
    const val MAX_MEMBERS = 32
    const val MAX_COMMIT_ENTRIES = 128
    const val MAX_TRANSFERS = 64
    const val MAX_TRADE_ASSETS = 4
    const val MAX_RESEND_TICKS = 64

    const val HASH_BYTES = 32
    const val MAC_BYTES = 16
    const val TOKEN_BYTES = 32
}

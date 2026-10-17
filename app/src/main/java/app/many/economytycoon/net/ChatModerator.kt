package app.many.economytycoon.net

data class ChatConfig(
    val burst: Int = 4,
    val perSecond: Int = 1,
    val emoteBurst: Int = 6,
    val emotePerSecond: Int = 2,
    /** The same text from the same player within this many ms is spam. */
    val repeatWindowMs: Long = 10_000L,
    /** Words replaced by asterisks wherever they appear, compared without regard to case. */
    val maskedTerms: Set<String> = emptySet(),
    val allowLinks: Boolean = false,
    /** Strikes within [strikeWindowMs] that earn an automatic mute. */
    val strikesToMute: Int = 3,
    val strikeWindowMs: Long = 60_000L,
    val muteMs: Long = 120_000L,
    val historySize: Int = 100,
)

sealed class ChatVerdict {
    /** The message may be shown; [text] is the cleaned form that replaces what was typed. */
    data class Allowed(val kind: ChatKind, val emote: Int, val text: String) : ChatVerdict()

    /** The message is dropped. [strike] says whether it counts against the sender. */
    data class Blocked(val error: NetError, val reason: String, val strike: Boolean) : ChatVerdict()
}

/**
 * Keeps chat civil without a human in the loop: rate limits, a mute that the host or the system can apply,
 * repeat and link filtering, masked words and a softening of shouting. Breaking the rules earns strikes and
 * enough strikes mute the sender for a while. All time is network time passed in by the caller.
 */
class ChatModerator(val config: ChatConfig = ChatConfig()) {
    private class Sender(config: ChatConfig) {
        val text = RateLimiter(config.burst, config.perSecond)
        val emotes = RateLimiter(config.emoteBurst, config.emotePerSecond)
        var lastText = ""
        var lastAt = Long.MIN_VALUE
        val strikes = ArrayDeque<Long>()
        var mutedUntil = Long.MIN_VALUE
        var mutes = 0
    }

    private val senders = java.util.TreeMap<PlayerId, Sender>()
    private fun of(p: PlayerId) = senders.getOrPut(p) { Sender(config) }

    fun isMuted(p: PlayerId, now: Long): Boolean = (senders[p]?.mutedUntil ?: Long.MIN_VALUE) > now
    fun mutedUntil(p: PlayerId): Long? = senders[p]?.mutedUntil?.takeIf { it != Long.MIN_VALUE }

    fun mute(p: PlayerId, until: Long) { of(p).mutedUntil = until }
    fun unmute(p: PlayerId) { senders[p]?.let { it.mutedUntil = Long.MIN_VALUE; it.strikes.clear() } }

    fun check(player: PlayerId, message: NetMessage.Chat, now: Long): ChatVerdict {
        val s = of(player)
        if (s.mutedUntil > now) return ChatVerdict.Blocked(NetError.MUTED, "muted", strike = false)
        if (message.chatKind == ChatKind.SYSTEM) return ChatVerdict.Blocked(NetError.INVALID_COMMAND, "system messages are reserved", strike = true).also { strike(s, now) }
        if (message.chatKind == ChatKind.EMOTE) {
            if (message.text.isNotEmpty() || message.emote !in 0..NetLimits.MAX_EMOTE) return ChatVerdict.Blocked(NetError.INVALID_COMMAND, "bad emote", strike = false)
            if (!s.emotes.tryTake(now)) return blocked(s, now, NetError.RATE_LIMITED, "too many emotes", strike = true)
            return ChatVerdict.Allowed(ChatKind.EMOTE, message.emote, "")
        }
        if (!s.text.tryTake(now)) return blocked(s, now, NetError.RATE_LIMITED, "too fast", strike = true)
        val clean = normalise(message.text)
        if (clean.isEmpty()) return ChatVerdict.Blocked(NetError.INVALID_COMMAND, "empty message", strike = false)
        if (!config.allowLinks && hasLink(clean)) return blocked(s, now, NetError.INVALID_COMMAND, "links are not allowed", strike = true)
        if (clean.equals(s.lastText, ignoreCase = true) && now - s.lastAt < config.repeatWindowMs) {
            return blocked(s, now, NetError.RATE_LIMITED, "repeated message", strike = true)
        }
        s.lastText = clean; s.lastAt = now
        return ChatVerdict.Allowed(ChatKind.TEXT, 0, soften(mask(clean)))
    }

    private fun blocked(s: Sender, now: Long, error: NetError, reason: String, strike: Boolean): ChatVerdict {
        if (strike) strike(s, now)
        return ChatVerdict.Blocked(error, reason, strike)
    }

    private fun strike(s: Sender, now: Long) {
        s.strikes.addLast(now)
        while (s.strikes.isNotEmpty() && now - s.strikes.first() > config.strikeWindowMs) s.strikes.removeFirst()
        if (s.strikes.size >= config.strikesToMute) {
            s.mutes++
            s.mutedUntil = now + config.muteMs * s.mutes
            s.strikes.clear()
        }
    }

    private fun normalise(raw: String): String =
        raw.filter { it != '​' && it != '‌' && it != '‍' && it != '﻿' && it != '‮' && it != '‭' }
            .trim().replace(Regex("\\s+"), " ")

    private fun hasLink(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("http://") || t.contains("https://") || t.contains("www.") || Regex("[a-z0-9-]+\\.(com|net|org|io|gg|ru|xyz)\\b").containsMatchIn(t)
    }

    private fun mask(text: String): String {
        var out = text
        for (term in config.maskedTerms.map { it.trim() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            out = out.replace(Regex(Regex.escape(term), RegexOption.IGNORE_CASE), "*".repeat(term.length))
        }
        return out
    }

    private fun soften(text: String): String {
        val letters = text.count { it.isLetter() }
        if (letters < 12) return text
        val upper = text.count { it.isUpperCase() }
        return if (upper * 100 / letters > 80) text.lowercase().replaceFirstChar { it.uppercase() } else text
    }
}

/** A line of chat as stored for late joiners and for display. */
data class ChatLine(val tick: Long, val from: PlayerId, val kind: ChatKind, val emote: Int, val text: String)

/** The most recent chat lines, oldest first. */
class ChatHistory(private val capacity: Int = 100) {
    private val lines = ArrayDeque<ChatLine>()

    fun add(line: ChatLine) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    fun recent(count: Int = capacity): List<ChatLine> = lines.toList().takeLast(count)
    val size: Int get() = lines.size
}

package app.many.economytycoon.net

/** What a host offers to anyone who says hello. */
data class ServerOffer(
    val session: SessionId,
    val content: String,
    val protocol: Int = NetLimits.PROTOCOL,
    val minProtocol: Int = NetLimits.MIN_PROTOCOL,
    val capabilities: Long = Capabilities.ALL,
    /** Capabilities a client must support to join at all. */
    val required: Long = 0L,
)

sealed class Negotiation {
    /** The connection runs at [protocol] with exactly the [capabilities] both sides have. */
    data class Agreed(val protocol: Int, val capabilities: Long) : Negotiation()

    data class Refused(val error: NetError, val detail: String) : Negotiation()
}

/** Rules for the names players choose. Names are shown to everyone, so they are kept plain and short. */
object NameRules {
    /** The name as it will be used (surrounding space removed, runs of spaces collapsed), or null when it is not acceptable. */
    fun clean(raw: String): String? {
        val collapsed = raw.trim().replace(Regex(" {2,}"), " ")
        val length = collapsed.codePointCount(0, collapsed.length)
        if (length < NetLimits.MIN_NAME_CHARS || length > NetLimits.MAX_NAME_CHARS) return null
        if (collapsed.any { it.isISOControl() || it == '​' || it == '‮' || it == '‭' }) return null
        if (collapsed.none { it.isLetterOrDigit() }) return null
        return collapsed
    }
}

/** Decides whether a hello can be served and what the connection will use. */
object Handshake {
    fun negotiate(hello: NetMessage.Hello, offer: ServerOffer): Negotiation {
        if (hello.session != offer.session) return Negotiation.Refused(NetError.SESSION_UNKNOWN, "no session ${hello.session.value} here")
        val top = minOf(hello.protocol, offer.protocol)
        val bottom = maxOf(hello.minProtocol, offer.minProtocol)
        if (hello.minProtocol > hello.protocol || top < bottom) {
            return Negotiation.Refused(NetError.UNSUPPORTED_VERSION, "client speaks ${hello.minProtocol}..${hello.protocol}, host ${offer.minProtocol}..${offer.protocol}")
        }
        if (hello.content != offer.content) return Negotiation.Refused(NetError.CONTENT_MISMATCH, "host runs ${offer.content}")
        val shared = hello.capabilities and offer.capabilities and Capabilities.KNOWN_MASK
        val missing = offer.required and shared.inv()
        if (missing != 0L) return Negotiation.Refused(NetError.MISSING_CAPABILITY, Capabilities.names(missing).joinToString())
        return Negotiation.Agreed(top, shared)
    }
}

package app.many.economytycoon.net

/**
 * The token source for real play: secrets come from the platform's secure random generator, so one player cannot
 * guess another's. [SeededTokenSource] is predictable and belongs in tests and local replays only.
 */
class SecureTokenSource(private val random: java.security.SecureRandom = java.security.SecureRandom()) : TokenSource {
    override fun next(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}

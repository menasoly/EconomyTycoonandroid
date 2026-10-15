package app.many.economytycoon.core

/** A violated rule, returned instead of throwing when a command or operation is not allowed. */
data class Rejection(val rule: String, val message: String) {
    override fun toString() = "$rule: $message"
}

/** Outcome of a validated operation. */
sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Rejected(val rejection: Rejection) : Outcome<Nothing>()

    val isOk get() = this is Ok
    val isRejected get() = this is Rejected

    fun getOrNull(): T? = (this as? Ok)?.value
    fun rejectionOrNull(): Rejection? = (this as? Rejected)?.rejection

    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Rejected -> throw IllegalStateException("rejected: $rejection")
    }

    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Rejected -> this
    }

    inline fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Ok -> transform(value)
        is Rejected -> this
    }

    inline fun onRejected(action: (Rejection) -> Unit): Outcome<T> {
        if (this is Rejected) action(rejection)
        return this
    }

    companion object {
        fun <T> ok(value: T): Outcome<T> = Ok(value)
        fun reject(rule: String, message: String): Outcome<Nothing> = Rejected(Rejection(rule, message))
    }
}

/** Stable rule identifiers used in rejections. UI and AI react to these ids. */
object Rules {
    const val INSUFFICIENT_FUNDS = "insufficient_funds"
    const val INSUFFICIENT_STOCK = "insufficient_stock"
    const val INSUFFICIENT_CAPACITY = "insufficient_capacity"
    const val UNKNOWN_ENTITY = "unknown_entity"
    const val NOT_OWNER = "not_owner"
    const val INVALID_QUANTITY = "invalid_quantity"
    const val INVALID_PRICE = "invalid_price"
    const val INVALID_ARGUMENT = "invalid_argument"
    const val NOT_UNLOCKED = "not_unlocked"
    const val PREREQUISITE_MISSING = "prerequisite_missing"
    const val RATE_LIMITED = "rate_limited"
    const val CREDIT_LIMIT = "credit_limit"
    const val NAME_TAKEN = "name_taken"
    const val REGULATION = "regulation"
    const val OWNERSHIP_CYCLE = "ownership_cycle"
    const val ANTITRUST = "antitrust"
    const val WRONG_STATE = "wrong_state"
    const val DUPLICATE = "duplicate"
    const val NO_LABOR = "no_labor"
    const val NO_ROUTE = "no_route"
    const val LIMIT_EXCEEDED = "limit_exceeded"
}

/** Convenience guard for command validation. */
inline fun guardRule(condition: Boolean, rule: String, message: () -> String): Rejection? =
    if (condition) null else Rejection(rule, message())

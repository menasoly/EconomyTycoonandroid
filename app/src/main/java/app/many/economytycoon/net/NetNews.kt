package app.many.economytycoon.net

/** Turns what a client reports into the one-line headlines the news feed shows; events that need no headline give null. */
object NetNews {
    fun headline(event: ClientEvent, name: (PlayerId) -> String): String? = when (event) {
        is ClientEvent.Desync -> "Your game drifted from the host's at tick ${event.tick}; repairing"
        is ClientEvent.Resynced -> "Back in step with the host at tick ${event.tick}"
        is ClientEvent.Disconnected -> "Connection lost, reconnecting"
        is ClientEvent.Reconnected -> "Reconnected"
        is ClientEvent.Trade -> {
            val n = event.notice
            val other = name(if (n.from == PlayerId.SERVER) n.to else n.from)
            when (n.state) {
                TradeState.SETTLED -> "Trade with $other settled"
                TradeState.EXPIRED -> "Trade with $other ran out of time"
                TradeState.FAILED -> "Trade with $other could not be completed"
                else -> null
            }
        }
        is ClientEvent.Rejected -> "Order refused: ${event.rule}"
        is ClientEvent.Closed -> "The session ended"
        is ClientEvent.Chat -> if (event.message.chatKind == ChatKind.SYSTEM) event.message.text else null
        else -> null
    }
}

package app.many.economytycoon.net

import app.many.economytycoon.core.DeterministicRandom

/** Sample values shared by the protocol tests. */
object NetFixtures {
    val session = SessionId(77)
    fun bytes(n: Int, seed: Int = 1) = ByteArray(n) { (it * 31 + seed).toByte() }
    fun hash(seed: Int = 1) = bytes(32, seed)

    /** One message of every kind, filled with non-trivial values. */
    fun samples(): List<NetMessage> = listOf(
        NetMessage.Hello(1, 1, Capabilities.ALL, "Ada Works", session, "base:1.0", false, null),
        NetMessage.Hello(1, 1, Capabilities.CHAT, "Grace", session, "base:1.0", true, ResumeInfo(PlayerId(3), 9, 120, bytes(16))),
        NetMessage.Welcome(1, Capabilities.CHAT or Capabilities.TRADE, PlayerId(2), Role.PLAYER, session, bytes(32, 5), 40, 3, 10, true, 12345L, 0x77L),
        NetMessage.Error(NetError.RATE_LIMITED, "slow down"),
        NetMessage.Bye(ByeReason.KICKED),
        NetMessage.Submit(5, 44, bytes(40), bytes(16, 9)),
        NetMessage.Ack(5, 44),
        NetMessage.Reject(6, NetError.NOT_OWNER, "not_owner", 45),
        NetMessage.Commit(
            44,
            listOf(CommitEntry(PlayerId(1), 5, bytes(10)), CommitEntry(PlayerId(2), 9, bytes(3, 2))),
            listOf(Transfer(TradeId(4), 11, 12, TradeAsset.cash(5_000_000)), Transfer(TradeId(4), 12, 11, TradeAsset.goods("wheat", 250_000))),
            0x1234_5678_9abcL,
        ),
        NetMessage.Commit(0, emptyList(), emptyList(), 0L),
        NetMessage.Resend(10, 5),
        NetMessage.HashReport(50, hash(3)),
        NetMessage.HashCheckpoint(50, hash(4)),
        NetMessage.DesyncNotice(50, hash(5)),
        NetMessage.SnapshotRequest(SnapshotReason.DESYNC, 9, 3),
        NetMessage.SnapshotHeader(9, 60, 40_000, 16_384, 3, hash(6), hash(7), 0x99L),
        NetMessage.SnapshotChunk(9, 2, bytes(300, 8), 0xDEADBEEFL),
        NetMessage.SnapshotAck(9, 3, true),
        NetMessage.Ping(99, 1_000),
        NetMessage.Pong(99, 1_000, 61),
        NetMessage.LobbyRequest(LobbyAction.KICK, PlayerId(4), 0),
        NetMessage.LobbyState(
            7, LobbyPhase.OPEN, PlayerId(1),
            listOf(
                MemberInfo(PlayerId(1), "Host", Role.PLAYER, 0, true, Presence.CONNECTED),
                MemberInfo(PlayerId(2), "Watcher", Role.SPECTATOR, -1, false, Presence.GRACE),
            ),
        ),
        NetMessage.LobbyReply(12, 8),
        NetMessage.Chat(ChatKind.TEXT, 0, "hello there"),
        NetMessage.Chat(ChatKind.EMOTE, 3, ""),
        NetMessage.ChatBroadcast(PlayerId(2), ChatKind.TEXT, 0, "unicode ok: é中", 61),
        NetMessage.TradeRequest(TradeAction.OFFER, TradeId(0), PlayerId(3), listOf(TradeAsset.cash(10_000)), listOf(TradeAsset.goods("steel", 500)), 30),
        NetMessage.TradeNotice(TradeId(8), TradeState.SETTLED, PlayerId(2), PlayerId(3), listOf(TradeAsset.cash(10_000)), emptyList(), 90),
    )

    /** Deterministic garbage of mixed lengths. */
    fun garbage(seed: Long, count: Int, maxLen: Int = 200): List<ByteArray> {
        val rnd = DeterministicRandom(seed)
        return List(count) { ByteArray(rnd.nextInt(maxLen + 1)) { rnd.nextInt(256).toByte() } }
    }
}

# Module: net

Multiplayer protocol and server logic. Pure Kotlin, no `android.*`, no sockets. Only the presenters and screens in `ui`
read from it; `session` and the engine do not depend on it.

Depends on: core (binary primitives, money, results, deterministic random), simulation (`Command`, `Simulation`),
persistence (`CommandCodec`, `SimulationSnapshot`), content, finance, companies, world (through the economy port only).

## Responsibility

Lets several people play one economy: a lobby, an authoritative host that orders commands, clients that follow it
tick for tick, desync detection and repair, reconnecting, spectating, trading between players, chat and a season
leaderboard. See ADR-0011 for the reasoning.

## Layers

| Layer | Files | What it does |
|---|---|---|
| Wire | `NetIds`, `NetError`, `Messages`, `MessageCodec`, `Assets` | Typed ids, error codes, the message set and a strict canonical binary codec. `MessageCodec.decode` returns a typed result and never throws. |
| Trust | `Crypto`, `Identity`, `Handshake`, `SeqWindow`, `CommandGate`, `CommandPayload`, `CommandSanity`, `AntiCheat` | HMAC-SHA256, per-player keys, resume proofs, replay windows, rate limits, command validation, offense scoring. |
| Transport | `Transport`, `MemoryNetwork` | The datagram seam and a deterministic test network. |
| Game seam | `SimulationPort`, `RealSimulationPort`, `SimulationDriver`, `Seating` | What the protocol needs from a game. Cash moves as `Command.EscrowTransfer`, a system-only command (codec tag 104) executed immediately and logged, so replays of the host's own command log reproduce trades. |
| Device | `MultiplayerNode`, `LocalTable`, `NetNews`, `SecureTokenSource` | What one device runs: pump the shard and clients from the app's clock, host or join over a `Transport`, news headlines, production token source. |
| History | `CommitLog`, `SessionRecord`, `SnapshotTransfer`, `SnapshotService` | The chained log of commits, replay of a recorded session, chunked snapshots with resume. |
| Session | `Lobby`, `ChatModerator`, `Escrow`, `Scoring`, `Season`, `DesyncAnalysis` | Who is in the game, chat limits, trades, scores, who diverged. |
| Endpoints | `ServerShard`, `ShardConfig`, `Connection`, `ClientSession` | The two ends of the protocol. |
| Presentation | `ui/present/Net*Presenter`, `NetScreenSource`, `ui/compose/screens/MultiplayerScreen` | Lobby, player list with connection quality, desync report, leaderboard, trades and chat. |

## Hosting and joining on a device

`LocalTable` builds a host and its guests on a `MemoryNetwork` (one process: local tables, tests). The host runs a
headless `RealSimulationPort` as the authority; every person, the host included, plays through their own port. A port
built with a `SimulationDriver` advances through the owner of the game instead of around it: the app passes
`GameSession.step()` so news, stats and achievements run after each commit, and sets `GameSession.externallyClocked`
so the local clock, speed and saving cannot move the game. A driver refuses `replace`, so a game on screen is never
swapped by a snapshot; the app rebuilds the session when a client reports a desync it cannot repair.

The app calls `MultiplayerNode.pump(nowMs)` about every 20 ms from a monotonic clock and posts the returned
`headlines` to the news feed. To play across devices, give `ServerShard` and `ClientSession` a `Transport` over a
socket or relay instead of a `MemoryNetwork` endpoint (the Android adapter belongs in `android/`): `send(to, bytes)`
must not block, endpoint ids are small integers the adapter maps to addresses, frames are at most 32 KiB, and the
welcome message carries the one-time token, so use DTLS or a TLS relay. Use `SecureTokenSource` for tokens; `SeededTokenSource`
is for tests.

## A tick, in order

1. The shard releases the escrow transfers due this tick, and takes the commands that were accepted for this tick.
2. It puts the commands in a rotating priority order (so no seat always goes first), appends the commit to the log
   with its chain value and sends it to everyone.
3. It applies the transfers, then enqueues the commands, then advances its own simulation by one tick.
4. Every tenth tick it hashes the state and publishes the hash. Clients do the same step 3 on receiving the commit,
   verify the chain, and report their hash back.
5. The season clock advances; at the end of a season the table is closed into the ledger.

## Invariants

- The commit for tick `t` is the only thing that moves any copy of the game from `t` to `t + 1`.
- The chain value of commit `t` is a function of the chain value of commit `t - 1` and the contents of `t`.
- A hash for tick `t` is the hash of the state after `t` ticks.
- Cash in the game plus cash in escrow is constant across a trade. `EscrowBook.invariants` checks the book against
  itself (what it says is held equals what its open trades and queued payouts account for) and the tests compare
  `heldTotals` with the game's own escrow balance.
- Every sequence number is accepted at most once per sender; a repeated request is answered with the same reply.
- A message that fails to decode costs the sender nothing unless its checksum was valid, in which case it is an
  offense. Line noise cannot get anyone removed.

## Saved state

Every piece of server state has a codec that begins with a version byte and refuses unknown versions with a
`CorruptDataException` instead of a crash:

| State | Codec | Notes |
|---|---|---|
| `IdentityBook` | `write` / `read` | Names, keys, resume nonces, bans. |
| `Lobby` | `write` / `read` | Members, slots, host, phase, bans. |
| `EscrowBook` | `write` / `read` | Open and finished trades, queued steps. |
| `SeasonLedger`, `SeasonTracker` | `write` / `read` | Finished seasons, ratings, current clock and baselines. |
| `CommitLog` | `write` / `read` | Every commit with its chain, verified on read. |
| `SessionRecord` | `SessionRecordCodec` | A finished or running game as a replayable file, with a checksum. |
| `ServerShard` | `writeState` / `readState` / `stateHash` | All of the above plus seats and checkpoints. |

## Limits

All bounds are in `NetLimits`: frame 32 KiB, command 2 KiB, snapshot chunk at most 16 KiB, at most 16 players and
32 members, 128 commands and 64 transfers per commit, at most 64 commits per resend request. Anything over a limit
is refused before it is used.

## Tests

About 480 tests. The protocol is exercised end to end on the toy game (`NetRig`): two to four clients and spectators
over perfect, lossy, duplicating, reordering and corrupting links for 100 to 260 ticks, desync injection and
repair, partitions and resume, a host restart, escrow trades, malicious input from a joined peer, and a replay of
the recorded session. A few short scenarios run the real economy through `RealSimulationPort`.

## Known limits

- No socket transport exists yet; hosting works over the in-memory network only (see above for what an adapter must provide).
- The host's own state (`ServerShard.writeState`) is not yet written beside the game save.

- Goods cannot be traded between players through the real economy yet (no command moves goods between companies).
- Late joiners can only watch.
- Seats nobody takes are described in the start plan (`StartPlan.aiSeats`) but the number of computer-controlled
  companies is fixed when the simulation is created.
- The transport layer provides no encryption; see ADR-0011.

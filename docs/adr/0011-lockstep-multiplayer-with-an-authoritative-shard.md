# ADR-0011: Lockstep multiplayer through an authoritative shard

Status: accepted

## Context

The economy is a deterministic simulation: the same seed, the same content and the same ordered commands give the
same state, tick for tick, and the replay and save code already depend on it. Phase 8 adds other people. Two things
have to hold at once. Everyone must see the same economy, and nobody may be able to change it in a way the rules do
not allow, including by sending crafted messages, replaying old ones, or claiming a company that is not theirs.

The state is large. The snapshot of a mid-game economy is hundreds of kilobytes, and most of it changes every tick,
so streaming state to every player would cost more than the game is worth on a mobile connection. The commands, by
contrast, are tiny: a handful of bytes each, a few per tick.

The game also has to cope with the network a phone really has: lost datagrams, duplicated and reordered ones, a
player who walks into a lift and returns a minute later, and a player who joins when the game has been running for an
hour. And it has to be testable without a socket, because the test suite is pure JVM and must be fast.

## Decision

**Lockstep over commands, with one authority.** One server shard owns a real copy of the simulation. Players send it
signed requests to run a command. Each tick the shard chooses which requests run, in which order, and publishes that
choice as a commit. Every participant, including the shard, applies exactly the commits it receives, in order, to
its own copy of the simulation. Nothing but commits ever moves a client's game forward, so there is no prediction
and nothing to roll back. A command is aimed a few ticks ahead (the command delay) so that the commit carrying it
reaches everyone before the tick it runs on.

**A chain and a hash catch divergence.** Each commit carries a chain value, the first eight bytes of a SHA-256 over
the previous chain value and the commit's own contents. A client that sees a commit whose chain does not follow its
own has missed or been given something other than what the server committed. Every ten ticks (configurable) the
server also publishes a hash of the whole state, and clients report theirs. A mismatch is a desync. The server names
who diverged, and the diverged client throws its copy away and takes a snapshot.

**Snapshots are chunked and resumable.** A snapshot is split into chunks, each with a CRC, with a SHA-256 over the
whole. The sender uses a sliding window and retransmits on a timeout; the receiver acknowledges the first chunk it is
missing, and after an interruption can ask to continue from there. Late joiners, reconnecting clients that fell too
far behind, and desynced clients all use the same path.

**The network is behind an interface.** The server and the client are plain objects that take a `Transport`, which
sends and receives datagrams, and a time value in milliseconds on every call. They never read a clock or open a
socket. The tests use an in-memory network with seeded loss, duplication, corruption, jitter and partitions on a
virtual clock, so a scenario with 15 per cent loss runs in milliseconds and gives the same result every time.
A socket or relay transport is a small adapter written later.

**Trust is verified at every step.** Frames carry a protocol version, a checksum, and a canonical encoding that the
decoder re-checks, so there is exactly one valid byte string for each message and the decoder never throws. A
player's commands are signed with a key derived from a secret token given at join time. The server checks, in order:
the signature, the message rate, the sequence window (replays), the target tick, whether the bytes parse as exactly
one command, whether the command is plausible, whether the sender owns the company, and per-tick budgets. Every
refusal is remembered, so a repeated request gets the same answer. Offenses add to a suspicion score that warns,
then removes, then bans.

**Trading is held in escrow by the server.** Players cannot move each other's assets. An offer locks the offered
assets into an escrow account through a transfer that is part of a commit, so all copies of the game apply it at the
same tick. Acceptance locks the wanted assets, and settlement pays both sides out. Anything that cannot be
completed is refunded. The escrow book can check at any time that what it says is held matches what its open trades and queued payouts
account for, and the tests compare it with the game's own escrow balance.

**The game stays unaware.** The server and client talk to the game through a small port: enqueue a command, apply
transfers, advance one tick, hash, snapshot, restore. The economy implements it in one class. A toy
implementation with the same shape runs the protocol tests for hundreds of ticks in milliseconds.

## Consequences

- Bandwidth is small and independent of the size of the economy; the cost grows with the number of commands.
- The simulation must stay deterministic, which it already has to for saves. Any nondeterminism now shows up as a
  desync within ten ticks and is repaired by snapshot, so it degrades the experience instead of corrupting it, and
  the desync report says which tick and which player.
- Players act with a delay of the command delay plus the round trip. That suits a management game; it would not
  suit an action game.
- The server is the single point of authority and of failure. Its own state (enrolled players, lobby, trades,
  season, commit log) has a versioned codec and a state hash so that a restarted server picks up where it stopped,
  and players resume with a signed proof.
- The wire format is versioned and capability-negotiated. A new message kind or capability does not break older
  clients that did not ask for it.
- Founding a company is a minting risk if a player can send it, so it is refused over the wire by default; the
  server seats players itself when a game starts.
- Frames are not encrypted or authenticated at the transport layer. Commands are signed, which protects the game,
  but a transport that carries the secret token, which it does exactly once, in the welcome, must itself be secure.
  A production transport must provide that.
- Goods cannot yet be traded through the real economy because it has no command that moves goods between
  companies. Cash trades work. The escrow supports goods and the toy game exercises them.

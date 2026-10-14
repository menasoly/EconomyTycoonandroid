# Security and Anti-Cheat

## Threats

Client tampering (editing saves, memory), replay and duplicate command attacks, market manipulation, account takeover, entitlement fraud, data leakage.

## Single-player

Local saves are the user's own; tampering affects only them. Achievements and leaderboards require server validation or are marked unverified.

## Multiplayer

- The server recomputes everything; client-calculated outcomes are ignored.
- Commands are authenticated, authorized per world and player, rate limited, and idempotent.
- Anti-manipulation rules in the market engine apply server-side.
- Anomaly detection on economic statistics per player.

## Data protection

Minimal personal data. TLS everywhere. Tokens are short-lived with refresh. Secrets never ship in the app. Backups encrypted. Privacy policy and deletion flow for accounts.

## Entitlements

Purchases verified server-side with the store. Offline: cached entitlement with expiry window. Restore purchases supported. Paid content cannot change shared competitive markets silently; cosmetic and content packs are gated, with equal rules for all players in a world.

## Monetization architecture

Premium purchase, expansion and industry packs, scenario packs, cosmetics, optional subscription for nonessential services (cloud backup, extra save slots). No pay-to-win.

## Security testing

Dependency scanning, secret scanning in CI, API fuzzing, authorization tests for every endpoint.

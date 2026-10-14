# Simulation Execution Model

Scope: Volume 1 defines the execution model and contracts at a high level. Per-engine algorithms are specified in later volumes.

## Core components

| Component | Responsibility | Owns |
|---|---|---|
| SimulationClock | Converts ticks to in-game time; speed and pause state | Tick counter, calendar |
| TickScheduler | Runs the phase pipeline once per tick in a fixed order | Phase order |
| SimulationCoordinator | Entry point: accepts commands, advances ticks, publishes snapshots | Session lifecycle |
| EconomicState | Authoritative mutable state, grouped by domain partition | All entities |
| EventQueue | Time-ordered scheduled events, stable tie-breaking by sequence id | Pending events |
| DeterministicRandomProvider | Named, seeded streams per subsystem | RNG state |
| SimulationSnapshotManager | Immutable snapshots for UI, AI and persistence | Snapshot history |
| ReplayEngine | Re-executes a command journal from a snapshot and verifies hashes | Replay sessions |

Engines (Market clearing, Production, Demand, Supply, Financial settlement) are domain modules invoked by phases; they do not call each other directly.

## Determinism rules

- Fixed tick length; simulation never reads wall-clock time.
- Fixed-point integer arithmetic for money and quantities (ADR-0003).
- Randomness only from DeterministicRandomProvider, one stream per subsystem and partition, so adding a consumer does not shift other streams (ADR-0004).
- Entities iterate in ascending stable id order. No iteration over hash order.
- Commands are applied in a total order: (tick, issuer priority class, sequence number).
- Parallel phases partition work by disjoint entity sets, write to per-partition buffers, and merge in partition-id order.

## Tick lifecycle

The proposed order was derived from data dependencies, not copied from a template. Principles: producers before consumers, flows settle before accounting, decisions see a consistent prior state, effects are lagged by one tick where a true cycle exists.

| # | Phase | Purpose | Reads | Writes |
|---|---|---|---|---|
| 0 | Begin tick | Advance clock, freeze snapshot S(t-1) for observation | Clock | Tick counter |
| 1 | Scheduled events | Fire events due this tick (disasters, policy changes, contract expiry) | EventQueue | Event effects |
| 2 | Decisions | Players' queued commands and AI agents (observing S(t-1)) emit commands | S(t-1) | Command list |
| 3 | Command validation and application | Validate against constraints and apply in total order | Commands, state | Orders, project starts, hiring |
| 4 | Resource availability | Update natural resource and energy availability | World | Resource pools |
| 5 | Procurement and shipments | Advance in-transit goods; deliver arrivals into inventories | Shipments | Inventory, shipment state |
| 6 | Production | Consume inputs, produce outputs from available inventory and capacity | Inventory, workforce, facilities | Inventory, wip |
| 7 | Demand | Compute demand for each market from population, prices and brand from the previous tick | Population, S(t-1) prices | Demand curves |
| 8 | Market clearing | Match supply orders and demand; determine prices and trades | Orders, demand | Trades, prices |
| 9 | Fulfillment | Move goods for matched trades; shortfalls become backorders | Trades, inventory | Inventory, shipments |
| 10 | Financial settlement | Post all value transfers to the ledger atomically; pay wages, tax, interest | Trades, payroll | Ledger, cash |
| 11 | Workforce update | Hiring completion, morale, turnover, wages due next tick | Company results | Employees (aggregates) |
| 12 | Macro update | Aggregate indicators (inflation, GDP, unemployment) from this tick's results | Trades, ledger | Macro indicators |
| 13 | Invariant checks | Verify conservation, non-negativity, ledger balance | State | Diagnostics |
| 14 | Publish | Emit simulation events, publish snapshot S(t), enqueue checkpoint | State | Snapshot |

### Why this order

- **Decisions use S(t-1).** All agents observe the same frozen prior state, which makes decision order irrelevant and removes same-tick feedback between agents.
- **Production before demand and clearing.** Output must exist before it can be offered, so no good is sold before it is produced.
- **Demand uses lagged prices.** Demand depends on price and price depends on demand; using the previous tick's price breaks the loop and damps oscillation.
- **Settlement after fulfillment.** Money moves only for goods that actually moved. Unfulfilled quantity does not settle.
- **Macro last.** Macro indicators are outputs of the tick and feed the next tick only, preventing double counting.

### Known risks to validate

- A one-tick lag in prices can produce oscillation in thin markets; needs stability tests with adaptive step damping.
- Order of phases 5 and 6 affects whether goods delivered this tick can be consumed this tick. Current choice: yes. Confirm in the production volume.
- Fulfillment may fail after clearing; clearing must reserve inventory to avoid selling goods twice.

## Stability and safety mechanisms

| Hazard | Mechanism |
|---|---|
| Negative inventory | Reservation at order placement; withdrawals checked in a single mutation path |
| Money creation | Only the ledger posts value; every posting balances; central mint is an explicit named account |
| Duplicate settlement | Settlement entries carry a unique idempotency key (tick, trade id) |
| Unstable feedback | Price change caps per tick, lagged demand, smoothing |
| Impossible states | Invariant phase every tick in debug; quarantine in release |
| Deadlocked supply chains | Detect unfulfilled dependency cycles and surface to AI and UI as shortages |

## Time and speed

- One tick equals one in-game unit (initially one day; open question).
- Speed changes the number of ticks per second of wall time, never the tick content.
- On resume after background, the simulation does not catch up automatically; catch-up policy is a product decision (open).

## Snapshots and replay

- A snapshot is an immutable copy of EconomicState partitions plus RNG state and tick number.
- Replay starts from a snapshot, applies the journaled command log, and compares state hashes per N ticks.
- State hashes use a canonical serialization defined with the persistence volume.

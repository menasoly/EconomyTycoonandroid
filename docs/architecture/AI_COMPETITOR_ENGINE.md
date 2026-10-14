# AI Competitor Engine

Module: `competitors`. Phase 2 (decisions).

## Principles

- AI uses the same command interface and validation as the player. It cannot bypass cash, inventory, capacity or market rules.
- Decisions are deterministic given the observation snapshot S(t-1), the agent's memory and its random stream.
- Fast deterministic logic runs on-device. Any LLM-assisted features are optional, offline-disabled, and never affect authoritative outcomes in replays (their outputs are journaled as commands).

## Approach evaluation

| Approach | Strength | Weakness | Role |
|---|---|---|---|
| Utility AI | Cheap, tunable, explainable | Hand-tuned | Primary for operational decisions |
| GOAP | Flexible plans | Search cost | Selected strategic plans (expansion, entry) |
| Behavior trees | Readable sequences | Rigid | Orchestration of routines |
| FSM | Simple | State explosion | Agent lifecycle (startup, growth, distress) |
| Hierarchical planner | Matches org structure | Design effort | Backbone (see below) |
| Reinforcement learning | Adaptive | Training, determinism, size | Offline tooling only, not shipped initially |
| LLM assistance | Flavor, advice | Cost, nondeterminism | Optional, non-authoritative |

## Hierarchy

CEO agent (strategy, budget allocation, entry/exit, M&A) -> functional directors (finance, operations, logistics, HR, marketing, investment) -> facility managers (production scheduling). Consumers are aggregate demand models, not agents. Government agents set policy (WORLD_SIMULATION.md).

## Agent definition

Each agent has: goals, constraints, owned resources, memory (bounded ring of observations and outcomes), decision interval (ticks), strategy profile (aggression, risk tolerance, planning horizon, innovation focus), observation model (what it can see, with delay and noise from its own stream), and an output command set.

## Decision pipeline

1. Observe: build an observation from the snapshot (own state, visible market stats, rumors with noise).
2. Evaluate: utilities for candidate actions computed in fixed-point.
3. Select: choose top action(s) with seeded tie-breaking and bounded exploration.
4. Emit commands, each validated like a player command. Rejections feed memory.

## Scaling

Agents are scheduled round-robin by decision interval so that load per tick is bounded. Thousands of companies are supported by staggering intervals and by simplifying inactive or tiny companies into template behavior.

## Personalities

Profiles are data (content). Adaptation adjusts weights slowly from outcomes within bounds.

## Testing

Decision tests with fixed observations; fuzz tests that agents never produce commands violating constraints; long-run tests that AI companies do not crash the economy.

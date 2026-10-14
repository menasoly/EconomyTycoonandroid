# Market Engine

Module: `markets`. Phase 8 of the tick (SIMULATION_ENGINE.md).

## Responsibilities

Order books per (market, good), demand coupling, price formation, trade matching, shortage and oversupply signals.

## Market types

| Type | Clearing | Used for |
|---|---|---|
| Commodity exchange | Periodic call auction per tick | Raw materials, bulk goods |
| Consumer market | Demand curve against listed offers by price, quality and brand | Retail goods and services |
| Contract market | Bilateral, scheduled delivery | Long-term supply agreements |
| Labor market | Aggregate matching by skill and region | Hiring |
| Capital market | See finance volume | Shares, bonds |

## Order contract

An order has: id, issuer, market, good, side, limit price (nullable for market orders), quantity, time in force, and a reservation (goods for sells, funds for buys). Orders are validated at submission; the reservation makes failure after clearing impossible.

## Call auction algorithm

1. Collect active orders; sort buys by price descending, sells by price ascending; ties by order id.
2. Find the clearing price that maximizes traded volume; ties resolved toward the previous price.
3. Apply the per-tick price move cap (INV-10); if the cap binds, ration at the capped price using pro-rata with largest-remainder.
4. Emit trades; release unused reservations.

## Consumer demand

Aggregated per population segment: demand = base need x income factor x price elasticity vs. reference price x seasonality x brand/quality preference. Computed from the previous tick's prices (lagged). Allocation across competing offers uses a deterministic logit-style share with fixed-point weights.

## Signals

Per market: last price, volume, order imbalance, stock-out count. Used by AI observation models and UI analytics. Manipulation safeguards: per-issuer order rate limits, wash-trade detection (same owner group both sides), price-move caps.

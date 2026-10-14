# Economic Model and Invariants

Resolves open questions 3 from VOLUME_1_CLOSEOUT.md. Builds on ADR-0003.

## Numeric representation

| Quantity | Type | Scale | Notes |
|---|---|---|---|
| Money | Int64 | 1 unit = 1/10,000 of a currency unit (mille-cent) | Range comfortably covers game scale; checked arithmetic |
| Goods quantity | Int64 | 1 unit = 1/1,000 of a base unit | Discrete goods use multiples of 1,000 |
| Price | Int64 | Money per one base unit of goods, at money scale | |
| Rates (interest, tax, elasticity) | Int32/Int64 | Parts per million | |
| Time | Int64 tick count | | |

Rules:
- All multiplications that can exceed 63 bits use a `mulDiv(a, b, c)` helper with 128-bit intermediate and a declared rounding mode (half-even for money, floor for physical quantities).
- Overflow is a defect: checked arithmetic throws in debug and saturates with a diagnostic in release.
- Rounding remainders on splits are assigned deterministically (largest-remainder, ties by ascending id) so splits always sum exactly.

## Core invariants

| ID | Invariant | Checked |
|---|---|---|
| INV-01 | Every ledger transaction has debits equal to credits | At posting |
| INV-02 | Sum of all account balances by currency equals the sum of minted minus destroyed money | Every tick |
| INV-03 | Inventory quantity is never negative; reserved <= on hand | At mutation |
| INV-04 | Goods are conserved: change in global stock = produced + imported-from-outside minus consumed minus destroyed | Every tick |
| INV-05 | A settlement idempotency key is applied at most once | At posting |
| INV-06 | A company cannot spend more than its available cash plus approved credit | At command validation |
| INV-07 | An order never trades more than its remaining quantity | At clearing |
| INV-08 | Every entity reference resolves | Snapshot validation |
| INV-09 | Headcount of a department equals the sum of its role counts | Every tick, sampled |
| INV-10 | Prices stay within [floor, cap] relative to the previous tick | At price formation |

## Money creation and destruction

Money exists only as ledger balances. Creation and destruction are allowed only through named rules: central bank issuance, government spending and taxation, bank lending (deposits created against loan assets, modeled as ledger entries), loan write-offs, and the player starting capital. Each uses a dedicated system account so INV-02 is auditable.

## Goods creation and destruction

Goods are created by extraction and production recipes, destroyed by consumption, spoilage, waste and shipping loss. Each has a named reason code recorded in the tick statistics for INV-04.

## Feedback loops and damping

| Loop | Risk | Damping |
|---|---|---|
| Price and demand | Oscillation | Lagged demand, per-tick price move cap, smoothing |
| Wages and prices | Spiral | Wage adjustment lag, bounded by productivity |
| Credit and asset prices | Bubble | Lending limits tied to cash flow, reserve ratios |
| Shortage and hoarding | Collapse | Order size caps relative to consumption history |
| Bankruptcy contagion | Cascade | Settlement priority rules, liquidation delay |

## Calibration

Parameters live in versioned content, not in code. Each parameter has a documented unit, range and rationale. Golden scenarios (see TEST_STRATEGY.md) pin expected behavior.

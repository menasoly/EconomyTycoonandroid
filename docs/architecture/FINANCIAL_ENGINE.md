# Financial Engine

Module: `finance`. Phase 10 (settlement).

## Ledger

Double-entry. Chart of accounts is a fixed taxonomy (assets, liabilities, equity, revenue, expense) with per-entity account instances. A **Transaction** has an id, tick, idempotency key, a reason code, and two or more **Entries** (account, signed amount). Entries sum to zero per currency.

Posting is the only way to change a balance. Engines produce *settlement intents*; the FinancialSettlementEngine validates, orders them (by priority class then id), and posts them atomically.

## Settlement priorities

Taxes and statutory payments, secured debt service, wages, suppliers, other. If cash is insufficient, lower classes fail with a typed outcome (unpaid), producing arrears rather than negative cash.

## Statements

Income statement, balance sheet and cash-flow statement are projections computed from the ledger at period close; they are cached, rebuildable and never authoritative.

## Debt and credit

Loans have principal, rate (ppm per tick basis), schedule, collateral and covenants. Credit rating derives from coverage ratios and history, with hysteresis to avoid flapping. Interest accrues per tick using fixed-point compounding with remainder carry.

## Equity

Share classes, issuance, dividends (bounded by retained earnings and cash), ownership registry. Valuation methods: book, earnings multiple, discounted cash flow with deterministic parameters.

## Taxes and depreciation

Tax rules come from government policy data per jurisdiction. Depreciation is straight-line or declining balance per asset class, posted at period close.

## Distress and bankruptcy

States: healthy, watch, distressed, insolvent, liquidating. Triggers are ratio-based with grace periods. Liquidation sells assets via the market engine, pays creditors by priority, and removes the company with its AI agent.

## Testing

Property tests: random sequences of intents never unbalance the ledger (INV-01), never duplicate settlement (INV-05), and conserve money (INV-02).

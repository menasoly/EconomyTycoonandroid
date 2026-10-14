# Module: finance

- Module id: `finance`
- Source directory: `app/src/main/java/app/many/economytycoon/finance/`
- Responsibility: Double-entry ledger, accounts, settlement, loans, statements, equity registry, distress states.
- Public API: `Ledger.post(tx)`, `SettlementEngine.settle(intents)`, `AccountId`, `SettlementIntent`, `LoanService`, `StatementProjector`
- Dependencies: core (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: FinancialAccount, Transaction, Loan, Investment
- Commands handled: TakeLoan, RepayLoan, IssueShares, PayDividend
- Events produced: TransactionPosted, LoanIssued, LoanDefaulted, CompanyDistressed
- Events consumed: TradeMatched, PayrollDue, TaxAssessed
- Tick phase(s): 10 (settlement), period close
- Tests required: Ledger property tests INV-01/02/05; interest accrual fixtures; priority ordering
- Estimated complexity: L
- Implementation priority: 2
- Parallel development suitability: Medium: shares contracts with markets

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

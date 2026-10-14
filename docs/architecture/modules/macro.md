# Module: macro

- Module id: `macro`
- Source directory: `app/src/main/java/app/many/economytycoon/macro/`
- Responsibility: Aggregate indicators, central bank and government policy, exchange rates, regulation effects.
- Public API: `MacroEngine`, `Indicators`, `PolicyService`, `ExchangeRates`
- Dependencies: core, markets, finance, world (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Government, Regulation, Indicators
- Commands handled: none (policy agents emit PolicyChange)
- Events produced: IndicatorsPublished, PolicyChanged, RecessionDeclared
- Events consumed: TradeMatched, PayrollDue, TransactionPosted
- Tick phase(s): 12
- Tests required: Inflation stability bands; recession scenario golden
- Estimated complexity: L
- Implementation priority: 5
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

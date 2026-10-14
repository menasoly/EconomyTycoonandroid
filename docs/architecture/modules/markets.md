# Module: markets

- Module id: `markets`
- Source directory: `app/src/main/java/app/many/economytycoon/markets/`
- Responsibility: Order books, call auction clearing, price formation, consumer demand, contract matching, manipulation safeguards.
- Public API: `MarketEngine.submit(order)`, `MarketEngine.clear(tick)`, `MarketView`, `DemandEngine`, `OrderId`
- Dependencies: core, content, world, finance (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Market, MarketOrder, Trade
- Commands handled: PlaceOrder, CancelOrder, SetPrice, SetMarketing
- Events produced: OrderFilled, TradeMatched, PriceChanged, ShortageDetected
- Events consumed: BatchProduced, PopulationChanged
- Tick phase(s): 7, 8
- Tests required: INV-07/10; clearing properties; zero-demand; extreme shortage
- Estimated complexity: XL
- Implementation priority: 3
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

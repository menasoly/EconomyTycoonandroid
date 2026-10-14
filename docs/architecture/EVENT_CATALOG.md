# Event Catalog

Domain events are published in the publish phase (14) and routed to subscribers. They are observations of what happened, never commands. Subscribers must be deterministic and must not mutate state outside their module; reactions become commands or next-tick effects.

| Event | Producer | Consumers | Payload (summary) |
|---|---|---|---|
| TickCompleted | simulation | persistence, session | tick, hash (if checkpoint) |
| EconomicEventFired | simulation | world, logistics, macro, UI | event id, kind, scope |
| TransactionPosted | finance | macro, analytics | tx id, reason, totals |
| LoanIssued / LoanDefaulted | finance | companies, UI | loan id, amounts |
| CompanyDistressed | finance | companies, workforce, competitors | company, state |
| CompanyFounded / CompanyBankrupt | companies | competitors, UI, markets | company |
| AcquisitionCompleted | companies | UI, competitors | acquirer, target |
| FacilityCompleted | production | workforce, UI | facility |
| BatchProduced | production | markets, UI | line, good, quantity, quality |
| LineBlocked | production | UI, competitors | line, cause |
| MachineBroken | production | UI | machine |
| OrderFilled | markets | competitors, UI | order, quantity, price |
| TradeMatched | markets | finance, logistics, macro | trade |
| PriceChanged | markets | competitors, macro, UI | market, old, new |
| ShortageDetected | markets | competitors, UI | market, severity |
| ShipmentDelivered / ShipmentLost | logistics | production, UI | shipment |
| ContractBreached | logistics | companies, UI | contract |
| WorkersHired / WorkersLeft | workforce | production, UI | cohort, count |
| PayrollDue | workforce | finance | company, amount |
| MoraleChanged | workforce | UI | cohort |
| PopulationChanged | world | markets, UI | city, delta |
| ResourceDepleted | world | production, UI | deposit |
| InfrastructureChanged | world | logistics, UI | network |
| IndicatorsPublished | macro | UI, competitors | indicators |
| PolicyChanged | macro | finance, logistics, UI | policy |
| RecessionDeclared | macro | UI, competitors | region |
| SaveCompleted / SaveFailed | persistence | session, UI | slot, result |

## Delivery rules

Events carry (tick, sequence). Delivery order is deterministic: by producer phase order, then sequence. Events are ephemeral in the simulation but a subset is journaled for the news feed and analytics.

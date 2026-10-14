# Golden Scenarios

Each scenario fixes seed, content version, initial state and command script, and asserts state hashes at named ticks plus key metrics. They run in CI; changes require review.

| Id | Scenario | Setup | Assertions |
|---|---|---|---|
| G-01 | Single farm to market | One farm, one retailer, one city | Goods conserved; ledger balanced; price within band; hash at ticks 10, 100, 365 |
| G-02 | Two retailers competing | Same market, different pricing | Demand splits per model; no negative inventory; deterministic |
| G-03 | Supply shortage | Mill outage event | Shortage signal; price rise capped; recovery |
| G-04 | Zero-demand market | Population drops to zero | No division by zero; orders expire; prices floor |
| G-05 | Insolvent company | Cash below obligations | Arrears, distress states, liquidation conserves value |
| G-06 | Mass bankruptcy | Credit shock | Settlement priority order; no money created; economy stabilizes |
| G-07 | Recession and recovery | Demand shock + rate response | Indicators within bands; unemployment bounded |
| G-08 | Circular dependency content | Invalid pack | Rejected at load |
| G-09 | Replay equivalence | G-01 plus journal | Replay hash equals live hash |
| G-10 | Serial versus parallel | G-02 | Identical hashes |
| G-11 | Save/load round trip | Any | Hash identical after load |
| G-12 | Migration | Stored old-version save | Loads and passes invariants |

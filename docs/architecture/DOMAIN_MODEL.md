# Domain Model

Authoritative entities, by owning module. Cross-module references are by id only.

## Identity and versioning

- Ids are 64-bit integers, typed per entity (`CompanyId`, `FacilityId`, ...), allocated from a deterministic per-type counter. Never reused.
- Every persisted entity type has a schema version; migrations are pure functions between versions.
- Authoritative state is distinguished from **projections** (statements, rankings, charts) that are rebuildable.

## Entity catalog

| Entity | Owner | Key attributes | Relationships | Key invariants | Lifecycle |
|---|---|---|---|---|---|
| World | world | seed, calendar, content version | has Regions | Seed immutable | Created at new game |
| Region/Country | world | name, government id, currency, resources | contains Cities | Currency exists | Static plus events |
| City | world | region, population segments, infrastructure | hosts Facilities | Population >= 0 | Grows or shrinks |
| PopulationSegment | world | city, income band, size, needs | consumes goods | Sizes sum to city population | Updated per tick |
| Company | companies | owner, name, cash account, status | owns Facilities, Employees | Cash via ledger only | Founded, active, distressed, liquidated |
| CorporateGroup | companies | parent, members | contains Companies | No ownership cycles | Created by M&A |
| Facility | production | type, city, capacity, condition | has ProductionLines, Inventory | Capacity <= design | Planned, building, operating, closed |
| ProductionLine | production | recipe, throughput, staffing | uses Machines | Recipe inputs available | Idle, running, blocked |
| Machine | production | type, wear, efficiency | in line | Wear in [0,1] | Install, maintain, retire |
| Product | companies/content | category, specification, quality, brand | sold in Markets | Quality bounded | Developed, launched, retired |
| Material/Resource | content | unit, category, perishability | used by recipes | Immutable definition | Static content |
| Inventory | production | holder, good, on hand, reserved | at Facility/Warehouse | INV-03 | Continuous |
| Warehouse | logistics | city, capacity, holdings | holds Inventory | Capacity respected | Built, expanded |
| Supplier | logistics | company, offers, reliability | supplies Facilities | Reliability in [0,1] | Active, inactive |
| Shipment | logistics | origin, destination, goods, vehicle, eta | carries goods | Goods reserved at origin | Created, in transit, delivered, lost |
| Vehicle | logistics | type, capacity, location | assigned to routes | Capacity respected | Bought, operating, retired |
| Employee (aggregate) | workforce | cohort of company, role, skill, count, wage, morale | in Department | Count >= 0 | Hired, trained, left |
| Department | workforce | company, function, budget | has cohorts | INV-09 | Created, reorganized |
| EmploymentContract | workforce | cohort, wage, term | binds cohort | Wage >= legal minimum | Active, ended |
| Market | markets | type, region, good | has Orders | One per (region, good, type) | Static |
| MarketOrder | markets | issuer, side, price, qty, reservation | in Market | INV-07 | Open, filled, expired, cancelled |
| Transaction | finance | tick, key, entries | in Ledger | INV-01, INV-05 | Immutable once posted |
| FinancialAccount | finance | owner, kind, balance | in Ledger | Balance = sum of entries | Open, closed |
| Loan | finance | borrower, lender, principal, rate, schedule | collateral Facility | Principal >= 0 | Issued, servicing, defaulted, repaid |
| Investment | finance | holder, instrument, quantity | in capital markets | Quantity >= 0 | Open, closed |
| Technology | research | tier, prerequisites, effects | unlocks recipes | Acyclic prerequisites | Locked, researching, known |
| ResearchProject | research | company, technology, progress, budget | produces Technology | Progress monotone | Active, complete |
| Government | macro | jurisdiction, policies, budget | sets Regulations | Budget via ledger | Continuous |
| Regulation | macro | scope, rule, effective tick | applies to companies | Effective ticks ordered | Proposed, active, repealed |
| EconomicEvent | simulation | kind, scope, start, duration, parameters | scheduled in EventQueue | Fires once | Scheduled, active, ended |
| SimulationTick | simulation | number, phase timings, stats | per tick | Monotone | Per tick |
| SimulationSnapshot | simulation | tick, state partitions, RNG state, hash | persisted | Immutable | Created at checkpoints |

## Indexing needs

By owner id, by (market, good), by city, by tick (events, journal), by status (active only). Index definitions for persistence are in DATA_ARCHITECTURE.md.

## Ownership rule

Only the owning module mutates an entity. Other modules request changes through commands or events.

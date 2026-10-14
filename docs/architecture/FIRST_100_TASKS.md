# First 100 Implementation Tasks

Dependencies refer to task ids in this list. Each task is expanded with TASK_TEMPLATE.md before assignment. Tasks in different modules whose dependencies are satisfied can run in parallel.

| Id | Task | Module | Depends on |
|---|---|---|---|
| T001 | Create domain folder structure with package docs | infra | - |
| T002 | Add architecture dependency test harness | infra | T001 |
| T003 | Encode manifest dependency rules in the harness | infra | T002 |
| T004 | Add typed ID value classes | core | T001 |
| T005 | Add Tick and Calendar types | core | T004 |
| T006 | Add Money fixed-point type | core | T004 |
| T007 | Add Quantity fixed-point type | core | T004 |
| T008 | Add mulDiv with 128-bit intermediate | core | T006 |
| T009 | Add rounding modes and largest-remainder split | core | T008 |
| T010 | Add Result and rejection types | core | T004 |
| T011 | Add DeterministicRandomProvider with named streams | core | T004 |
| T012 | Add canonical hashing utility | core | T004 |
| T013 | Add property tests for fixed-point arithmetic | core | T009, T007 |
| T014 | Add property tests for random stream independence | core | T011 |
| T015 | Add dispatcher abstraction and logging | infra | T001 |
| T016 | Add content definition models (Good, Recipe, FacilityType) | content | T004, T007 |
| T017 | Add content loader and schema validation | content | T016 |
| T018 | Add recipe cycle detection | content | T017 |
| T019 | Add sample agriculture content pack | content | T017 |
| T020 | Add Account and Transaction models | finance | T006, T010 |
| T021 | Add Ledger posting with balance check | finance | T020 |
| T022 | Add idempotency key handling | finance | T021 |
| T023 | Add system accounts for mint and sink | finance | T021 |
| T024 | Add settlement intent and priority ordering | finance | T021 |
| T025 | Add FinancialSettlementEngine | finance | T024, T022 |
| T026 | Add ledger property tests (INV-01, INV-02, INV-05) | finance | T025, T023 |
| T027 | Add income statement projection | finance | T025 |
| T028 | Add balance sheet projection | finance | T025 |
| T029 | Add Loan model and interest accrual | finance | T025, T009 |
| T030 | Add Region and City models | world | T016 |
| T031 | Add seeded world generator (regions, cities) | world | T030, T011 |
| T032 | Add population segment model | world | T030 |
| T033 | Add Inventory with reservation | production | T007, T010 |
| T034 | Add inventory invariant tests | production | T033 |
| T035 | Add Facility and ProductionLine models | production | T016, T030 |
| T036 | Add production step engine | production | T035, T033, T011 |
| T037 | Add production quality and yield | production | T036 |
| T038 | Add maintenance and wear | production | T036 |
| T039 | Add Employee cohort model | workforce | T006, T004 |
| T040 | Add hiring and wages | workforce | T039 |
| T041 | Add payroll settlement intents | workforce | T040, T024 |
| T042 | Add MarketOrder and order book | markets | T007, T006, T010 |
| T043 | Add call auction clearing | markets | T042, T009 |
| T044 | Add price move caps and floors | markets | T043 |
| T045 | Add market reservation integration with inventory and ledger | markets | T043, T033, T025 |
| T046 | Add clearing property tests (INV-07) | markets | T043 |
| T047 | Add consumer demand model | markets | T032, T043 |
| T048 | Add EventQueue with stable ordering | simulation | T005, T010 |
| T049 | Add SimulationClock | simulation | T005 |
| T050 | Add EconomicState container and partitions | simulation | T004 |
| T051 | Add command model and total ordering | simulation | T010, T005 |
| T052 | Add TickScheduler with phase pipeline | simulation | T049, T048, T050, T051 |
| T053 | Add invariant check phase | simulation | T052, T026, T034 |
| T054 | Add SimulationCoordinator | simulation | T052 |
| T055 | Add snapshot model and manager | simulation | T050, T012 |
| T056 | Add state hash computation | simulation | T055 |
| T057 | Add determinism test (serial) | simulation | T054, T056 |
| T058 | Add golden scenario: single farm to market | simulation | T057, T036, T043, T025, T047 |
| T059 | Add Company model and creation command | companies | T025, T051 |
| T060 | Add Shipment and Warehouse models | logistics | T033, T030 |
| T061 | Add route graph and pathfinding | logistics | T060 |
| T062 | Add shipment movement and delivery | logistics | T061, T052 |
| T063 | Add procurement contracts | logistics | T062, T043 |
| T064 | Add AI agent interface and observation model | competitors | T055, T059 |
| T065 | Add utility scoring framework | competitors | T064 |
| T066 | Add retail pricing agent | competitors | T065, T047 |
| T067 | Add production manager agent | competitors | T065, T036 |
| T068 | Add CEO agent skeleton | competitors | T065, T066, T067 |
| T069 | Add constraint fuzz test for AI commands | competitors | T068 |
| T070 | Add Macro indicators model | macro | T043, T025 |
| T071 | Add inflation and unemployment update | macro | T070, T040 |
| T072 | Add central bank rule | macro | T071 |
| T073 | Add Room database and slot metadata | persistence | T055 |
| T074 | Add snapshot encoder with checksum | persistence | T055, T012 |
| T075 | Add command journal store | persistence | T051, T073 |
| T076 | Add autosave and atomic writes | persistence | T074, T073 |
| T077 | Add ReplayEngine | simulation | T075, T056 |
| T078 | Add replay hash comparison tests | simulation | T077 |
| T079 | Add migration framework and fixtures | persistence | T074 |
| T080 | Add GameSession service and read models | session | T054, T076 |
| T081 | Add compose theme and design tokens | ui | T001 |
| T082 | Add adaptive navigation scaffold | ui | T081 |
| T083 | Add dashboard screen | ui | T082, T080 |
| T084 | Add simulation controls bar | ui | T082, T080 |
| T085 | Add company overview screen | ui | T082, T080, T059 |
| T086 | Add factory operations screen | ui | T082, T080, T035 |
| T087 | Add market analysis screen | ui | T082, T080, T043 |
| T088 | Add financial dashboard screen | ui | T082, T080, T027, T028 |
| T089 | Add command rejection presentation | ui | T082, T080 |
| T090 | Add chart components with table alternative | ui | T081 |
| T091 | Add accessibility checks for core screens | ui | T083, T085, T088 |
| T092 | Add visualization viewport and spatial index | visualization | T080 |
| T093 | Add world map renderer (regions and overlays) | visualization | T092, T030 |
| T094 | Add tick benchmark harness | simulation | T054 |
| T095 | Add parallel partition execution with deterministic merge | simulation | T052, T011 |
| T096 | Add parallel versus serial determinism test | simulation | T095, T057 |
| T097 | Add event types and effects (disaster, policy) | simulation | T048, T052 |
| T098 | Add golden scenario: supply shortage | simulation | T058, T097, T062 |
| T099 | Add golden scenario: recession | macro | T058, T072, T097 |
| T100 | Add vertical slice integration test (farm to retail with AI) | simulation | T058, T068, T063 |

# Module: talent

Skills, education and talent pipelines. Package `app.many.economytycoon.talent`; presenters in `ui/present`
(`TalentPresenter`, `EducationPresenter`, `TrainingPresenter`, `RecruitingPresenter`, `CareersPresenter`,
`ProvidersPresenter`); screens in `ui/compose/screens/Talent*Screen.kt`. Decision record: ADR-0018. Depends on core,
finance (settlement intents), world (population view), workforce (`CohortKey` only), competitors (`AiPersonality`)
and advisor (insight shape). Nothing depends on it yet.

## Concepts

| Concept | Where | Notes |
|---|---|---|
| Ids, ratios | TalentIds.kt, Ppm.kt | Typed ids (`SkillId`, `InstitutionId`, `ProgrammeId`, `OfferId`, ...) and ppm helpers; own id counters in `TalentCounters` |
| Taxonomy | SkillTaxonomy.kt | Skills with decay and learning rates, pathways, role profiles with weighted requirements; `DefaultTaxonomy.forRoles` |
| Distribution | SkillDistribution.kt | 20-bucket histogram of heads by level with carry; mass-conserving shifts; `TakePolicy` |
| Dynamics | SkillDynamics.kt | Monthly learning and decay of a histogram |
| Productivity | Productivity.kt | `SkillMatch`: multiplier from requirements, surplus bonus, weakest-link penalty; `equivalentSkillPpm` |
| Cohort skills | CohortSkills.kt | Per-cohort histograms: reconcile with workforce head counts, hire, take, learn, retire |
| Education | Education.kt, EducationDemand.kt, EducationService.kt | Institutions, intake on day 240, graduation, funding, demand for places |
| Skill markets | SkillMarkets.kt | Pools per (region, skill), tightness average, wage premium, shortage kinds |
| Training | Training.kt, TrainingService.kt | Employer programmes, retention clauses, clawback, training share |
| Providers | Providers.kt, ProviderService.kt | Courses, accreditation, ranking, employer seats with fee and payee |
| Migration | Migration.kt | Openness, quotas, diaspora ledger, brain drain, waves |
| People, succession | People.kt, Succession.kt | Key positions, persons, successors, development, key-role index |
| Poaching | Poaching.kt, PoachingService.kt | Offers, acceptance odds, counters, non-competes, retention, cooldowns |
| Branding | Branding.kt, BrandService.kt | Appeal and awareness per (company, region), recruiting campaigns |
| Careers | Careers.kt, CareerService.kt | Ladders of rungs, reviews every 90 days |
| Facts and effects | TalentInputs.kt, TalentWorld.kt | `CohortFact`, `CompanyFact`, `WorkforceEffect`, `TalentReport`; `TalentWorld` and `WorldTalentView` |
| Ledger, events | TalentLedger.kt, TalentEvents.kt | Intents keyed `talent:<kind>:<id>:<tick>:<extra>`; sealed `TalentEvent` |
| Commands | TalentCommand.kt | 33 company commands, one country policy command, `TalentResult` |
| Advice | TalentAdvisor.kt | Twelve rules, codes `talent-*`, topic PEOPLE |
| Read model | TalentReadModel.kt | `TalentReader` builds an immutable `TalentView`; reading never mutates |
| AI | TalentAi.kt | `TalentStance.of(personality)`, `plan`, `planProvider` |
| State | TalentState.kt, TalentCodec.kt, TalentInvariants.kt | Owned maps, codec version 1, canonical hash, invariant checks |
| System | TalentSystem.kt, TalentParams.kt | Wires the services around one state; `execute` and `advance` |

## Daily flow

`TalentSystem.advance(tick, world, inputs)` runs, in this order: reconcile cohorts with the workforce facts;
graduations into pools; providers; (monthly) pool tightness and on-the-job learning; employer training; migration
review; poaching; career reviews; (monthly) succession and employer brand; (yearly) roll the market, migration and
succession year; (every 30 days) prune. The returned `TalentReport` holds the intents to post, the effects to apply
to the workforce and the events to publish.

## Invariants

Checked by `TalentInvariants.violations` and used in the tests: histogram mass equals head count, every bucket and
carry is in range, ratios stay within 0..1,000,000 ppm, ids are below their counters, enrolled never exceeds
capacity, a programme belongs to an existing cohort or is finished, a successor belongs to an existing position,
and no two intents of a report share a key.

## Determinism

No floating point in state, all iteration over `TreeMap`, random draws only through
`RandomProvider.deriveSeed(seed, name, partition)`. Two systems with the same seed, commands and inputs have equal
hashes; a save taken mid-run and loaded continues to the same hash as an uninterrupted run (tested).

## Not covered here

Applying `WorkforceEffect`s in the workforce module, the command wrapper, the snapshot section, routes and news.


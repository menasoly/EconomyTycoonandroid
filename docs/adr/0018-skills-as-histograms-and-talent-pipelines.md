# ADR-0018: Skills as level histograms with talent pipelines

Status: accepted

## Context

Workforce cohorts carry a head count, a wage, morale and a single skill number. That is not enough to make
education, training, hiring and rivalry between employers matter. Players should be able to open a college where
engineers are scarce, pay for a course so that a team learns a missing skill, lose a team to a rival and fight back,
and plan who will run a plant when its manager retires. Computer-controlled rivals must use the same levers, and a
save must replay to the same hash on every device.

Two simple designs were rejected. A skill value per person needs millions of records and breaks the cohort model of
the workforce module. A single "education level" per region ignores that welders and chemists are different
shortages and that a company can train its own people.

## Decision

A new package `talent` keeps skills as histograms and follows the workforce module through plain facts.

- A `SkillDistribution` is a 20-bucket histogram of heads by skill level (parts per million of mastery) with a
  per-bucket carry for fractions. Shifts (learning, decay) move mass between buckets and conserve the head count.
  Taking people out uses a `TakePolicy` (proportional, best first, worst first), so poaching, promotion and
  departures change a team's skill mix in an explainable way.
- Every role has a `RoleProfile`: weighted skill requirements and a primary skill. `SkillMatch` turns a cohort's
  histograms into a productivity multiplier with a floor of 60 percent, a bonus for surplus skill and a penalty for
  the weakest required skill. `equivalentSkillPpm` maps it onto the term the workforce formula already uses.
- Education is a pipeline: schools, vocational colleges and universities (owned by companies or the state) take an
  intake once a year (day 240), teach for a fixed number of years with quality driven by spend, and deposit
  graduates, with a level and a spread, into a regional pool per skill.
- A skill market per region and skill has a pool of unemployed qualified people and a tightness measure (an
  exponential average of openings against supply). Tightness drives a wage premium and a shortage kind
  (structural, cyclical, regional).
- Employer training takes a cohort's people off the job for a number of days, raises their level and can bind them
  with a retention clause; a leaver triggers a clawback. Training providers sell courses to the market and to
  employers, with accreditation levels that change prices and outcomes.
- Migration moves qualified people between countries by openness, quota, a diaspora ledger and brain drain; it
  changes pools and, for employed people, emits `Emigrate` effects.
- Poaching is a sealed-offer game: acceptance odds depend on the wage gap, employer brand, loyalty and covenants;
  the victim can counter, sign non-competes or grant retention; the hunter can respect, buy out or ignore a covenant.
- Employer brand (appeal and awareness per company and region) and recruiting campaigns raise the share of the best
  graduates a company gets. Careers (ladders of rungs with reviews every 90 days) and succession (key positions,
  named successors, development plans) turn skills into promotions and continuity.
- The module never writes workforce state. It reads `CohortFact`s and returns `WorkforceEffect`s (hire, transfer,
  raise wage, morale, training share, depart, emigrate), settlement intents with deterministic keys, and
  `TalentEvent`s. The runner applies them.
- Randomness comes only from `DeterministicRandom(RandomProvider.deriveSeed(seed, name, partition))` per decision.
  All iteration is over sorted maps. The state has a codec with a version byte and a canonical hash.
- Computer-controlled rivals use `TalentAi`, a pure function from views to the same commands a player issues.
  `TalentAdvisor` adds twelve `talent-*` insights in the existing `Insight` shape.

## Consequences

- Skills become visible and tradable: shortages, wage premiums, poaching and training have measurable effects and
  clear reasons shown by the presenters.
- The cost is bounded: state size depends on the number of cohorts, pools and institutions, not on people.
- Fractions of heads are carried per bucket, so integer rounding never creates or destroys people; invariants check
  this after every mutation in the tests.
- Balance lives in `TalentParams` and its parts; tuning needs no code change elsewhere.
- Wiring touches shared files: a command wrapper, executor cases, a snapshot section, a tick hook, effect
  application in the workforce module, routes and news.
- Switching the workforce skill term to `SkillMatch` changes golden production and wage figures; this should be done
  in its own commit.
- The snapshot gains a section, so all saved hashes change.

## Integration notes

The module is wired through a runner, a system-only establish command and a conditional snapshot section. The workforce
skill term follows the module only once it is established, so games without it keep their numbers; games that establish
it (new games do) differ from earlier ones by that term and by the extra snapshot section.

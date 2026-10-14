# Module: workforce

- Module id: `workforce`
- Source directory: `app/src/main/java/app/many/economytycoon/workforce/`
- Responsibility: Cohort-based employment: hiring, wages, morale, turnover, training, automation displacement, payroll intents.
- Public API: `WorkforceEngine`, `Cohort`, `WagePolicy`, `HiringPlan`, `LaborMarketView`
- Dependencies: core, finance (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Department, EmploymentContract, EmployeeCohort
- Commands handled: HireWorkers, SetWage, StartTraining, Layoff
- Events produced: WorkersHired, WorkersLeft, MoraleChanged, PayrollDue
- Events consumed: CompanyDistressed, FacilityCompleted
- Tick phase(s): 11, payroll in 10
- Tests required: INV-09; wage gap to turnover monotonicity; shortage behavior
- Estimated complexity: M
- Implementation priority: 3
- Parallel development suitability: High

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

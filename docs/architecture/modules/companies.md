# Module: companies

- Module id: `companies`
- Source directory: `app/src/main/java/app/many/economytycoon/companies/`
- Responsibility: Company lifecycle, ownership, groups, subsidiaries, M&A, objectives, reputation, valuation.
- Public API: `CompanyService`, `Company`, `OwnershipRegistry`, `DealService`
- Dependencies: core, content, finance (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Company, CorporateGroup
- Commands handled: FoundCompany, Acquire, Merge, CreateSubsidiary, SetObjective
- Events produced: CompanyFounded, CompanyBankrupt, AcquisitionCompleted
- Events consumed: CompanyDistressed, LoanDefaulted
- Tick phase(s): 3, 11
- Tests required: Ownership cycle rejection; liquidation conservation
- Estimated complexity: L
- Implementation priority: 4
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

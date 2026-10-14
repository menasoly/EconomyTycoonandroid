# Module: content

- Module id: `content`
- Source directory: `app/src/main/java/app/many/economytycoon/content/`
- Responsibility: Data definitions for goods, recipes, facility types, industries, technologies; loading and validation; content versioning.
- Public API: `ContentRepository`, `GoodDef`, `RecipeDef`, `FacilityTypeDef`, `IndustryDef`, `TechDef`, `ContentValidator`, `ContentVersion`
- Dependencies: core (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Definitions (static)
- Commands handled: none
- Events produced: none
- Events consumed: none
- Tick phase(s): load time
- Tests required: Schema validation, recipe cycle detection, id stability, content-only pull request test
- Estimated complexity: M
- Implementation priority: 2
- Parallel development suitability: High: content packs parallel to engine

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.

# Dependency Graph

```mermaid
flowchart TB
    infra
    core
    content --> core
    finance --> core
    world --> core
    world --> content
    workforce --> core
    workforce --> finance
    production --> core
    production --> content
    production --> workforce
    markets --> core
    markets --> content
    markets --> world
    markets --> finance
    logistics --> production
    logistics --> markets
    logistics --> world
    companies --> finance
    companies --> content
    macro --> markets
    macro --> finance
    macro --> world
    competitors --> companies
    competitors --> markets
    competitors --> production
    competitors --> logistics
    simulation --> competitors
    simulation --> macro
    simulation --> logistics
    persistence --> core
    persistence --> simulation
    session --> simulation
    session --> persistence
    ui --> session
    visualization --> session
```

Rules: arrows point to allowed dependencies; no cycles; `infra` is usable by anyone and depends on nothing. Where this graph and MODULE_CATALOG.md differ, MODULE_CATALOG.md and the architecture manifest win and this file must be corrected.

Note: `persistence` depends on `simulation` only through snapshot and state contracts defined in `core`/domain types; it must not call orchestration code.

# Repository Strategy

## Layout

The repository is the Android application. Engine and game code live inside the app as folders under the base package, grouped by domain. There are no extra Gradle modules or published packages at this stage.

```
app/src/main/java/app/many/economytycoon/
    core/            primitives
    content/         definitions and validation
    world/
    companies/
    finance/
    markets/
    production/
    logistics/
    workforce/
    macro/
    competitors/
    simulation/
    session/
    persistence/
    ui/
    visualization/
    infra/
app/src/test/java/app/many/economytycoon/     mirrors main
app/src/androidTest/java/app/many/economytycoon/
docs/architecture/                            this documentation
```

Inside a domain folder: `model/`, `api/` (public contracts), `internal/` as needed.

## Dependency enforcement

- Architecture tests (to be added in the foundation phase) assert the allowed-dependency table in MODULE_CATALOG.md.
- Domain folders must not import `android.*` or `androidx.*`.

## Branching and commits

- Work lands on feature branches named `feature/<id>-<short-name>`; never directly on `main`.
- One substantial feature per branch, many small meaningful commits.
- Commit messages: `<type>(<scope>): <description>` with types feat, fix, test, refactor, perf, docs, build, chore.
- Features are reviewed and pushed by the owner.

## Documentation

- Architecture docs live under `docs/architecture/`.
- Decisions are recorded as ADRs under `docs/architecture/adr/`.
- Changes to a binding decision require a new ADR that supersedes the old one.

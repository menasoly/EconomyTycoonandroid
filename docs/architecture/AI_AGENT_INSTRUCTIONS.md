# Implementation Agent Instructions

Read this file and your task file. You do not need the full architecture.

## Rules

1. Implement only the task assigned. Touch only files listed as creatable or modifiable.
2. Use the public APIs and data contracts named in the task; do not invent new ones. If a contract is missing or wrong, stop and file a change request.
3. Do not create a parallel model of an existing entity or duplicate an existing service. Search first.
4. Respect the dependency table in MODULE_CATALOG.md. No cycles.
5. Do not change architecture decisions (ADRs). Propose changes in a change request.
6. No placeholder implementations presented as complete. Unfinished parts must fail loudly and be listed in the task result.
7. Tests are part of the task. Do not skip or weaken them.
8. No silent behavior changes: if existing behavior changes, call it out and update tests and docs.
9. Follow the branching and commit rules in REPOSITORY_STRATEGY.md and CODING_STANDARDS.md.

## Change-request process

Write a short request: problem, affected contracts, proposal, impact. The owner of the affected module decides; accepted changes become an ADR or a manifest update before implementation continues.

## Module ownership

Each module in ARCHITECTURE_MANIFEST.yaml names an owner role. Changes outside your task's modules go through that owner.

## Result report

State what was implemented, tests added, commands run with results, deviations, and open issues.

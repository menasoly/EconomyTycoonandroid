# ADR-0006: Domain folders inside the app, not separate packages or Gradle modules

Status: Accepted

## Context

The project is a single Android app and the team prefers domains as folders inside the app.

## Decision

Organize the code as domain folders under the base package. Enforce boundaries with dependency rules and architecture tests instead of build modules.

## Consequences

Simple build and navigation. Boundary discipline relies on tests rather than the compiler, so those tests are required early.

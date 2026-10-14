# ADR-0002: Native Compose UI, no general game engine

Status: Accepted

## Context

The product is dominated by dashboards, tables and forms, with maps and factory views as secondary visuals.

## Decision

Use Jetpack Compose for all UI. Build map and factory views in a visualization layer on Canvas, moving to a GPU surface only if profiling requires it.

## Consequences

Strong accessibility and platform fit, smaller app. Custom rendering work is limited to the visualization layer.

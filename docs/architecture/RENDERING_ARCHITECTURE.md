# Rendering Architecture

Module: `visualization`.

## Approach

2D first. Compose Canvas for maps, graphs and factory layouts; a dedicated GPU surface is introduced only if profiling shows Canvas cannot hold frame budget. Isometric sprites are a presentation choice for factory views; selective 3D is out of scope for the first release.

## Separation from simulation

Renderers consume immutable view models derived from snapshots. They keep their own animation state (interpolated positions, particles) that is never fed back into the simulation.

## Level of detail

| Zoom | Content |
|---|---|
| World | Region fills with overlay colors, major trade flows aggregated |
| Region | Cities, infrastructure, aggregated shipments |
| City | Industrial zones, facilities as icons |
| Facility | Layout with line animations driven by line status |

Only the visible viewport is built. Shipments are drawn as sampled markers on routes, not one per simulated unit.

## Data pipeline

Snapshot -> spatial index (grid or quadtree, rebuilt incrementally) -> viewport query -> draw list. Overlays (prices, population, supply) are textures or color ramps computed off the main thread.

## Performance

Budgets in PERFORMANCE_BUDGETS.md. Avoid allocations in draw loops, reuse paths, cap animated entities per frame.

## Accessibility

Every map view has a list alternative; overlays include legends and non-color cues.

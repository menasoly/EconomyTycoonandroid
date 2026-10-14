# Manufacturing Engine

Module: `production`. Phase 6.

## Data-driven recipes

A **Recipe** defines inputs (good, quantity), outputs (good, quantity, yield curve), duration in ticks, required facility type, required machines, labor roles and skill, energy use, waste output, and the technology that unlocks it. Industries add recipes as content; the engine has no industry-specific code.

## Production step per line

1. Check status and staffing; compute effective capacity = nominal x condition x staffing factor.
2. Determine feasible batches = min over inputs of available / required, bounded by capacity and energy.
3. Reserve and consume inputs, advance work-in-progress by tick, output on completion.
4. Quality: defect rate from machine condition, worker skill and quality assurance spend, drawn from the production random stream.
5. Waste and emissions recorded with reason codes (INV-04).

## Bottlenecks and scheduling

Lines report blocked causes (missing input, no labor, no energy, output full). Scheduling policy per facility: priority list plus optional automatic allocation by margin. Circular recipe dependencies are rejected at content validation.

## Maintenance and upgrades

Wear increases with use; maintenance spend restores condition. Upgrades replace machines with new efficiency values. Breakdown probability rises as condition drops.

## Construction

Facilities build over ticks with staged cost and labor; delays from material shortages or events. Construction is a project entity, not a special case in production.

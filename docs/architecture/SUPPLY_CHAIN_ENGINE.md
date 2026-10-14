# Supply Chain Engine

Module: `logistics`. Phases 5 and 9.

## Components

Procurement (purchase orders and contracts), warehousing, transport networks (road, rail, sea, air), shipments, trade (tariffs, customs).

## Procurement

Buyers place orders through the market engine or contracts. Contracts specify good, quantity schedule, price formula, penalties. Supplier reliability affects delivery probability using the logistics random stream.

## Shipments

A shipment reserves goods at origin, moves along a route with per-leg time and cost, and delivers at destination. Capacity of vehicles and infrastructure limits throughput. Lost or delayed shipments follow reliability and event effects; goods lost are destroyed with a reason code.

## Routing

Routes come from a precomputed network graph per transport mode, updated when infrastructure changes. Pathfinding uses deterministic tie-breaking. Aggregation: shipments between the same pair in a tick are bundled.

## Trade

Import and export pass through customs with tariff and delay from trade policy. Exchange rates come from the macro module.

## Inventory policy support

Reorder points and safety stock computed from consumption history; just-in-time modes shorten buffers and raise disruption risk.

## Disruption

Events (strikes, storms, closures) change route capacity or availability. Unfulfilled dependency chains are surfaced as shortage signals.

# Industry Model

Hundreds of industries are supported by data, not code. An industry is a set of definitions that the shared engines interpret.

## Definition schema

| Field | Meaning |
|---|---|
| id, name, family | Stable id; family such as agriculture, mining, energy |
| goods | Raw, intermediate and final goods with units, perishability, category |
| facility types | Capacity, build cost, build time, workforce roles, energy and utility needs |
| recipes | Inputs, outputs, yield curve, duration, machines, labor, waste |
| labor roles | Skill bands and base wages |
| technologies | Unlocks, efficiency modifiers, obsolescence |
| demand profile | Segments served, elasticity, seasonality, substitutes |
| pricing model | Reference price, volatility class |
| regulation tags | Permits, emissions, safety rules |
| unlock conditions | Milestones, technologies, regions |

## Standard abstractions

- **Extractive** industries produce goods from resource deposits.
- **Transformative** industries run recipes.
- **Service** industries produce capacity-based outputs with perishable "stock" (seat-hours, bandwidth).
- **Distribution** industries move or hold goods.
- **Financial** industries use ledger instruments rather than goods recipes.

## Families

Agriculture, mining, energy, oil and gas, chemicals, metals, construction, automotive, aerospace, electronics, semiconductors, food, pharmaceuticals, textiles, consumer goods, furniture, transportation, logistics, retail, e-commerce, hospitality, entertainment, media, telecommunications, software, financial services, healthcare, real estate, robotics, renewable energy.

## Adding an industry

Add a content pack; run content validation (id uniqueness, recipe closure, cycle detection, balance checks); add a golden scenario. No engine code changes unless a new abstraction is needed, which requires an ADR.

## Balance checks

Automated: every good is producible and consumable; no recipe creates value from nothing under reference prices; chain margins within configured bands.

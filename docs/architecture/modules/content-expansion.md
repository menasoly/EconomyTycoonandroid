# Content expansion (industry packs)

Package `app.many.economytycoon.content` (packs, `ExpandedContent`), `content.catalog`, and `ui.present` /
`ui.compose.screens` for browsing. Decision record: ADR-0015.

## Purpose

Adds eight industries to the game as data: chemicals, pharmaceuticals, textiles, construction materials,
electronics, automotive, aerospace and medical devices. Nothing in the simulation, the engine or the save format
changes. The same DSL, validator and audit tools that cover the shipped packs cover these.

## Contents

| Pack | Industries | Goods | Recipes | Facilities | Machines | Technologies |
| --- | --- | --- | --- | --- | --- | --- |
| chemicals | 4 | 34 | 35 | 6 | 3 | 6 |
| pharmaceuticals | 3 | 27 | 27 | 7 | 4 | 8 |
| textiles | 4 | 29 | 29 | 6 | 3 | 6 |
| construction_materials | 4 | 26 | 26 | 7 | 3 | 8 |
| electronics | 4 | 36 | 38 | 6 | 3 | 8 |
| automotive | 5 | 34 | 34 | 7 | 3 | 10 |
| aerospace | 5 | 28 | 28 | 8 | 4 | 10 |
| medical_devices | 5 | 38 | 40 | 7 | 3 | 9 |

Four new deposits: sulfur ore and potash ore (chemicals), gypsum (construction), titanium ore (aerospace).
Ten new worker roles: chemist, biotechnologist, lab technician, textile technician, mason, assembler, quality
inspector, welder, aerospace technician and clinical engineer.

Dependencies run one way, in the order the table lists them. Pharmaceuticals, textiles and electronics use
chemicals. Construction materials uses chemicals and textiles. Automotive uses chemicals, textiles, electronics
and construction materials. Aerospace uses textiles and electronics. Medical devices uses every earlier pack
except automotive, and takes technology prerequisites from chemicals and electronics. Chemicals uses no other
expansion pack.

Goods the packs buy but nothing makes: water, packaging, coal, foam and carbon black. These are bought from the
import desk. Every other input is made by a recipe or extracted from a deposit.

## Files

- `content/packs/*Pack.kt` - the eight packs written by the generator.
- `content/ExpandedContent.kt` - shipped packs plus the expansion, merged and indexed once.
- `content/catalog/CatalogModel.kt`, `CatalogBuilder.kt`, `CatalogFormat.kt` - the pure catalog report.
- `ui/present/IndustryCatalogPresenter.kt` - filter by pack, family and name; open an industry.
- `ui/compose/screens/IndustryCatalogScreen.kt` - the screen.
- `tools/content/expgen.py` and `spec_*.py` - the generator and the specs.

## Generator

Run `python3 tools/content/expgen.py` from the repository root to rewrite the pack files, `--check` to list the
packs that no longer match their spec, and `--report` to print margins and paybacks per recipe and facility.

Bands the generator enforces: recipe margin between 6% and 90% of cost (the specs aim for 16% to 40%), facility
payback between 90 and 1,500 days (the packs land between 417 and 1,274), at least four ticks per
batch for goods sold in pieces, and an energy bill of at most 30% of the material bill.

## Catalog report

`CatalogBuilder.build(packs, only)` returns a `CatalogReport` of `PackEntry` and `IndustryEntry` rows. An
industry's goods are the outputs of its recipes; its facilities, machines and technologies are found through the
same recipes and the unlock lists of technologies. Margins are the recipe margin in parts per million of cost,
reported as lowest, middle (the lower middle value for an even count) and highest. Payback is the shortest and
longest payback among the facilities the industry's recipes run in. `CatalogFormat` renders text and CSV with one
stable line per pack or industry.

The builder needs every pack the listed packs depend on, because prices and wages come from the whole repository.
Pass all packs and limit the listing with `only`.

## Tests

`ExpansionPackContract` holds the checks every pack must pass; each pack has a subclass that adds what is particular
to it. Cross-cutting tests cover the validator on the combined content, reachability from raw resources, absence of
value-minting loops, technology acyclicity, id and name uniqueness against the shipped packs, determinism, demand,
machines, facilities, recipes, goods, roles, price order, tight audit thresholds and pack dependencies. The catalog
has tests on the real content and on two small hand-made packs whose margins can be worked out by hand.

## Extending

To add an industry: write a `spec_<name>.py` in the style of the others, add it to the list in `expgen.py`, run the
generator, add the pack to `ExpandedContent.expansionPacks` in dependency order, add a subclass of
`ExpansionPackContract`, and update the counts in `ExpandedContentTest`.

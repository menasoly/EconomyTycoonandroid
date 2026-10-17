# ADR-0015: New industries are content packs written by generators, not code

Status: accepted

## Context

The roadmap for phase 6 says a new industry must be added by data only. The game already ships eight content packs,
each built with the content DSL, and a validator and four audit tools (recipe economics, facility payback, chain
cost and good flows) that say whether a pack is in balance. What was missing was proof that the DSL and the tools
are enough to add whole industries without touching the engine, and a way to add them that keeps the economics
consistent when hundreds of prices, quantities and build costs depend on each other.

Writing a recipe by hand means choosing input amounts, an output amount, a duration, a headcount and an energy use
that together give a sensible margin, and then choosing a facility cost and upkeep that give a sensible payback.
Those numbers are linked: change one price and every recipe that uses the good is out of balance. Done by hand for
over two hundred recipes, the result is either unbalanced or never finished.

## Decision

Eight industries are added as eight new packs: chemicals, pharmaceuticals, textiles, construction materials,
electronics, automotive, aerospace and medical devices. Each is one file in `content/packs/`, built only with the
existing DSL, so no engine, schema or save code changes.

The packs are not written by hand. Each industry has a short Python spec in `tools/content/spec_<name>.py` that
lists goods with a rough price, recipes as input ratios, facilities with a target payback, technologies and demand.
`tools/content/expgen.py` turns the specs into the Kotlin files:

- It reads the existing packs for prices, wages and ids, so new recipes use real input prices and never clash
  with existing ids.
- It fixes the price of each made good from its recipe: the sum of inputs, labor and energy plus the margin for its
  category. Goods sold in whole pieces (aircraft, scanners, cars) are fitted from a per-unit bill of materials and
  a batch size, so a price follows from what goes into the product.
- It sizes each batch so the facility pays back in the target number of days, and caps the energy bill at a
  fraction of the material bill.
- It links technologies, so the unlock lists on a technology and the `requiredTech` fields agree.
- It checks the result (every input producible, no cycle, margins and paybacks in band) and refuses to write a pack
  that fails.

The Kotlin the generator writes is committed. The generator is deterministic and a `--check` mode reports a pack file that is
out of date with its spec.

`ExpandedContent` merges the shipped packs with the expansion packs without editing `ShippedContent`. Tests build
their content from it. Once `ShippedContent.packs` lists the new packs `ExpandedContent` keeps
working because it only appends packs that are missing.

A catalog report is built from content alone. It lists each industry with the counts of its goods, recipes,
facilities, machines and technologies, the lowest, middle and highest recipe margin, and the payback of its
facilities. A presenter filters and opens it, and a thin screen shows it.

## Consequences

- Retuning an industry means editing its spec and running the generator, not editing numbers in Kotlin. A price
  change in a spec ripples through every recipe that uses the good.
- The packs depend on each other in one direction only (chemicals first, medical devices last). The dependency
  order is a list in `ExpandedContent` and a test checks that no pack uses a good or technology of a later one.
- Four new deposits (sulfur ore, potash ore, gypsum, titanium ore) change world generation, because every region
  rolls for every extractable good. Worlds made before the packs are shipped do not match worlds made after.
- Four goods that existing packs used but nothing made (server hardware, network equipment, medical equipment and
  its kit) become makeable, so import-desk stock for them is no longer the only source.
- The pack files are long and repetitive. Reviewers read the specs; the Kotlin is checked by the validator, the
  audit and the tests, not by eye.
- The expansion tests are the contract for future packs: a new industry has to pass the same checks (clean validator,
  no audit findings, every good reachable and wanted, no value-minting loop) before it is shipped.

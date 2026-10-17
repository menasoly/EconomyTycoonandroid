#!/usr/bin/env python3
"""Generator for the expanded industry packs (pharmaceuticals, electronics, automotive, textiles, chemicals,
aerospace, construction materials, medical devices).

It reuses contentgen.Spec and contentgen.render for the Kotlin output and adds three things the base
generator does not do:

  * roles: packs may define their own roles, which are emitted right before the first non-good definition;
  * calibration: recipe batch sizes are solved so that every recipe earns its target margin at reference
    prices AND every facility pays back in its target number of days (see CALIBRATION below);
  * checks: ids are unique against the shipped packs, references resolve, and the same arithmetic the Kotlin
    audit performs is mirrored here so a bad spec fails before any Kotlin is compiled.

Usage:
    python3 expgen.py              write the pack files
    python3 expgen.py --check      exit 1 when a committed file differs from a fresh generation
    python3 expgen.py --report     print the recipe and facility economics table, write nothing
"""
import math
import os
import re
import sys
import importlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import contentgen as cg  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PACK_DIR = os.environ.get("ET_PACK_DIR", os.path.join(ROOT, "app/src/main/java/app/many/economytycoon/content/packs"))

# Dependency order: a pack only references goods, roles and technologies of the packs before it.
MODULES = [
    "spec_chemicals", "spec_pharma", "spec_textiles", "spec_construction",
    "spec_electronics", "spec_automotive", "spec_aerospace", "spec_medical",
]
PACK_OBJECTS = {
    "ChemicalsPack", "PharmaceuticalsPack", "TextilesPack", "ConstructionMaterialsPack",
    "ElectronicsPack", "AutomotivePack", "AerospacePack", "MedicalDevicesPack",
}

# CALIBRATION
# A recipe is described by a base batch (relative input amounts) and a margin. The generator scales the base
# batch by k so that the facility it runs in repays its build cost in `pb` days:
#     lines * margin * cost(k) / ticks  =  build / pb + upkeep + overhead wages
# which gives cost(k) = k * (inputs + energy) + labor, hence k. Outputs then follow from margin * cost.
MARGIN_BY_CATEGORY = {"RAW": 0.15, "INTERMEDIATE": 0.16, "CONSUMER": 0.22, "INDUSTRIAL": 0.18, "ENERGY": 0.12, "SERVICE": 0.24}
MARGIN_BAND = (0.06, 0.90)
PAYBACK_BAND = (90, 1500)
ENERGY_SHARE_CAP = 0.3  # energy never costs more than this fraction of the material bill
MIN_FIT_TICKS = 4
WAGES = {}
KNOWN = {k: set() for k in ("industry", "good", "role", "facility", "recipe", "tech", "machine", "vehicle")}


def load_existing():
    """Reads prices, categories, wages and ids from every committed pack that this tool does not generate."""
    for name in sorted(os.listdir(PACK_DIR)):
        if not name.endswith(".kt") or name[:-3] in PACK_OBJECTS:
            continue
        text = open(os.path.join(PACK_DIR, name)).read()
        for m in re.finditer(r'good\("([a-z_0-9]+)",\s*"[^"]*",\s*GoodCategory\.(\w+),\s*([0-9.]+)', text):
            cg.PRICES[m.group(1)] = float(m.group(3))
            cg.GOODCAT[m.group(1)] = m.group(2)
        for m in re.finditer(r'role\("([a-z_]+)",\s*"[^"]*",\s*SkillBand\.\w+,\s*([0-9.]+)', text):
            WAGES[m.group(1)] = float(m.group(2))
        for m in re.finditer(r'^\s+(industry|good|role|facility|recipe|tech|machine|vehicle)\("([a-z_0-9]+)"', text, re.M):
            KNOWN[m.group(1)].add(m.group(2))


class ExpSpec(cg.Spec):
    """contentgen.Spec plus roles, payback targets and automatic storage."""

    def __init__(self, pack_id, obj, major=1, minor=0):
        super().__init__(pack_id, obj, major, minor)
        self.role_defs = []
        self.payback = {}

    def role(self, rid, name, band, wage, training=30):
        self.role_defs.append((rid, name, band, wage, training))
        WAGES[rid] = wage

    def add_goods(self, cat, rows, vol="NORMAL"):
        """rows: (id, name, price) or (id, name, price, spoilage_ppm) or (..., spoilage, vol)."""
        for row in rows:
            gid, name, price = row[0], row[1], row[2]
            spoil = row[3] if len(row) > 3 else 0
            v = row[4] if len(row) > 4 else vol
            weight = 5_000 if price >= 20_000 else (3_000 if price >= 2_000 else 1_000)
            volume = 8_000 if price >= 20_000 else (4_000 if price >= 2_000 else 1_000)
            self.good(gid, name, cat, price, v, spoil, weight, volume)

    def fac(self, fid, name, cls, fam, cost, ticks, lines, upkeep, energy, staff, pb,
            tech=None, extracts=None, storage=None):
        self.facility(fid, name, cls, fam, cost, ticks, lines, storage, upkeep, energy, staff, extracts, tech, {})
        self.payback[fid] = pb

    def rec(self, rid, name, industry, facility, ticks, inputs, outputs, labor, energy=0.0,
            margin=None, waste=None, tech=None, machines=None, qty=None, defect=20000, fit=False, scale=1.0):
        """qty: batch size of the primary output for discrete goods; with fit=True the inputs and energy are given
        per unit and the good's reference price is derived from them (see fit_prices)."""
        self.recipe(rid, name, industry, facility, ticks, inputs, outputs, labor, energy, waste, tech, machines, margin, defect, False)
        self.recipes[-1]["qty"] = qty
        self.recipes[-1]["fit"] = fit
        self.recipes[-1]["scale"] = scale


def nice(x):
    """Rounds a quantity to a figure a designer would write by hand."""
    if x >= 5000: return float(round(x / 50) * 50)
    if x >= 1000: return float(round(x / 10) * 10)
    if x >= 100: return float(round(x / 5) * 5)
    if x >= 20: return float(round(x))
    if x >= 5: return round(x * 2) / 2
    if x >= 1: return round(x, 1)
    return max(round(x, 3), 0.001)


def _labor_cost(r):
    return sum(WAGES[role] * n for role, n in r["labor"].items()) * r["ticks"]


def fit_prices(spec):
    """Derives the reference price of discrete big-ticket goods from their bill of materials.

    For a recipe declared with fit=True, `inputs` and `energy` are per unit of the primary output and `qty` is the
    batch size. The batch duration follows from the facility payback target and the price from cost plus margin, so
    a jetliner-sized good cannot end up priced far from the parts it is made of."""
    facs = {f[0]: f for f in spec.facilities}
    for r in spec.recipes:
        if not r.get("fit"):
            continue
        f = facs[r["facility"]]
        build, lines, upkeep, staff = f[4], f[6], f[8], f[10]
        net = build / spec.payback[r["facility"]] + upkeep + sum(WAGES[role] * n for role, n in staff.items())
        primary = next(iter(r["outputs"]))
        m = r["margin"] if r["margin"] is not None else MARGIN_BY_CATEGORY[cg.GOODCAT[primary]]
        qty = r["qty"]
        r["inputs"] = {g: q * r["scale"] for g, q in r["inputs"].items()}
        r["energy"] = r["energy"] * r["scale"]
        unit = sum(cg.PRICES[g] * q for g, q in r["inputs"].items()) + r["energy"] * cg.ENERGY_PRICE
        while True:
            # A batch must take a few days or the labor of a whole plant would be idle between batches.
            materials = unit * qty
            ticks = 10
            for _ in range(40):
                cost = materials + sum(WAGES[role] * n for role, n in r["labor"].items()) * ticks
                nxt = max(1, round(lines * m * cost / net))
                if nxt == ticks:
                    break
                ticks = nxt
            if ticks >= MIN_FIT_TICKS or qty >= 100_000:
                break
            qty *= 2
        r["qty"] = qty
        price = float(f"{cost * (1 + m) / qty:.3g}")
        cg.PRICES[primary] = price
        spec.goods = [(g[0], g[1], g[2], price, *g[4:]) if g[0] == primary else g for g in spec.goods]
        r["ticks"] = 0
        r["inputs"] = {g: q * qty for g, q in r["inputs"].items()}
        r["energy"] = r["energy"] * qty
        r["qty_fitted"] = True


def calibrate(spec):
    facs = {f[0]: list(f) for f in spec.facilities}
    for r in spec.recipes:
        if r["fixed"]:
            continue
        f = facs[r["facility"]]
        build, lines, upkeep, staff = f[4], f[6], f[8], f[10]
        overhead = sum(WAGES[role] * n for role, n in staff.items())
        prices = cg.PRICES
        primary = next(iter(r["outputs"]))
        m = r["margin"] if r["margin"] is not None else MARGIN_BY_CATEGORY[cg.GOODCAT[primary]]
        iv = sum(prices[g] * q for g, q in r["inputs"].items())
        ev = r["energy"] * cg.ENERGY_PRICE
        if iv > 0 and ev > iv * ENERGY_SHARE_CAP:
            ev = iv * ENERGY_SHARE_CAP
            r["energy"] = ev / cg.ENERGY_PRICE
        unit_value = sum(prices[g] * ratio for g, ratio in r["outputs"].items())
        if r.get("qty"):
            scale = r["qty"] / r["outputs"][primary]
            cost = scale * unit_value / (1 + m)
            if r["ticks"] == 0:
                # Discrete big-ticket goods: the batch is fixed, so the duration is what meets the payback target.
                net = build / spec.payback[r["facility"]] + upkeep + overhead
                r["ticks"] = max(1, round(lines * (scale * unit_value - cost) / net))
        else:
            net = build / spec.payback[r["facility"]] + upkeep + overhead
            cost = r["ticks"] * net / (lines * m)
        lc = _labor_cost(r)
        k = (cost - lc) / (iv + ev)
        if k < 0.02:
            raise SystemExit(f"{r['id']}: labor {lc:.0f} leaves no room in a target cost of {cost:.0f}; raise margin, lower labor or payback")
        inputs = {g: nice(q * k) for g, q in r["inputs"].items()}
        energy = float(round(r["energy"] * k)) if r["energy"] else 0.0
        waste = {g: nice(q * k) for g, q in r["waste"].items()}
        cost2 = sum(prices[g] * q for g, q in inputs.items()) + energy * cg.ENERGY_PRICE + lc
        if r.get("qty"):
            scale = r["qty"] / r["outputs"][primary]
            outputs = {g: nice(ratio * scale) if g != primary else float(r["qty"]) for g, ratio in r["outputs"].items()}
        else:
            revenue = cost2 * (1 + m)
            outputs = {g: nice(ratio * revenue / unit_value) for g, ratio in r["outputs"].items()}
        r["inputs"], r["outputs"], r["energy"], r["waste"], r["fixed"] = inputs, outputs, energy, waste, True
        r["k"] = k
    for fid, f in facs.items():
        if f[7] is None:
            need = 0.0
            for r in spec.recipes:
                if r["facility"] == fid:
                    need = max(need, (sum(r["inputs"].values()) + sum(r["outputs"].values())) * 3)
            f[7] = float(max(10_000, math.ceil(need / 5_000) * 5_000))
    spec.facilities = [tuple(f) for f in facs.values()]


def link_tech(spec):
    """Makes the technology unlock lists and the requiredTech fields of recipes, facilities and machines agree."""
    by_recipe = {r["id"]: r for r in spec.recipes}
    facs = {f[0]: list(f) for f in spec.facilities}
    machines = {m[0]: list(m) for m in spec.machines}
    for (tid, _n, _c, _t, _co, _ti, _req, recipes, facilities, macs, _e) in spec.techs:
        for rid in recipes:
            if by_recipe[rid]["tech"] not in (None, tid):
                raise SystemExit(f"recipe {rid} is gated by two technologies")
            by_recipe[rid]["tech"] = tid
        for fid in facilities:
            if facs[fid][12] not in (None, tid):
                raise SystemExit(f"facility {fid} is gated by two technologies")
            facs[fid][12] = tid
        for mid in macs:
            if machines[mid][7] not in (None, tid):
                raise SystemExit(f"machine {mid} is gated by two technologies")
            machines[mid][7] = tid
    spec.facilities = [tuple(f) for f in facs.values()]
    spec.machines = [tuple(m) for m in machines.values()]


def economics(spec):
    """Mirrors RecipeEconomics and FacilityPayback of the Kotlin audit for this pack's recipes."""
    rows, per_facility = [], {}
    facs = {f[0]: f for f in spec.facilities}
    for r in spec.recipes:
        cost = sum(cg.PRICES[g] * q for g, q in r["inputs"].items()) + _labor_cost(r) + r["energy"] * cg.ENERGY_PRICE
        value = sum(cg.PRICES[g] * q for g, q in r["outputs"].items())
        margin = (value - cost) / cost
        per_day = (value - cost) / r["ticks"]
        rows.append((r["id"], r["facility"], cost, value, margin, per_day, r["ticks"], r.get("k", 1.0)))
        per_facility.setdefault(r["facility"], []).append(per_day)
    pays = {}
    for fid, f in facs.items():
        best = max(per_facility.get(fid, [0.0]))
        overhead = sum(WAGES[role] * n for role, n in f[10].items())
        net = best * f[6] - f[8] - overhead
        pays[fid] = math.ceil(f[4] / net) if net > 0 else None
    return rows, pays


def check(spec):
    problems = []
    own = {k: set() for k in KNOWN}
    own["industry"] = {i[0] for i in spec.industries}
    own["good"] = {g[0] for g in spec.goods}
    own["role"] = {r[0] for r in spec.role_defs}
    own["facility"] = {f[0] for f in spec.facilities}
    own["recipe"] = {r["id"] for r in spec.recipes}
    own["tech"] = {t[0] for t in spec.techs}
    own["machine"] = {m[0] for m in spec.machines}
    for kind, ids in own.items():
        clash = ids & KNOWN[kind]
        if clash:
            problems.append(f"{kind} ids already shipped: {sorted(clash)}")
    goods = own["good"] | KNOWN["good"]
    roles = own["role"] | KNOWN["role"]
    techs = own["tech"] | KNOWN["tech"]
    for r in spec.recipes:
        for g in list(r["inputs"]) + list(r["outputs"]) + list(r["waste"]):
            if g not in goods: problems.append(f"{r['id']}: unknown good {g}")
        for role in r["labor"]:
            if role not in roles: problems.append(f"{r['id']}: unknown role {role}")
        if r["industry"] not in own["industry"] | KNOWN["industry"]: problems.append(f"{r['id']}: unknown industry")
        if r["facility"] not in own["facility"]: problems.append(f"{r['id']}: facility {r['facility']} is not part of this pack")
        for m in r["machines"]:
            if m not in own["machine"]: problems.append(f"{r['id']}: unknown machine {m}")
    for t in spec.techs:
        for p in t[6]:
            if p not in techs: problems.append(f"tech {t[0]}: unknown prerequisite {p}")
    for d in spec.demand:
        if d[0] not in goods: problems.append(f"demand for unknown good {d[0]}")
    for f in spec.facilities:
        for role in f[10]:
            if role not in roles: problems.append(f"facility {f[0]}: unknown role {role}")
        if f[11] and f[11] not in goods: problems.append(f"facility {f[0]}: unknown extracted good")
    return problems


def render_pack(spec):
    text = cg.render(spec, WAGES)
    if not spec.role_defs:
        return text
    lines = text.split("\n")
    at = next(i for i, l in enumerate(lines) if re.match(r"^        (machine|facility|recipe|tech|demand)\(", l))
    block = [f'        role({cg.kt_str(rid)}, {cg.kt_str(name)}, SkillBand.{band}, {cg.fmt(wage)}, {training})'
             for (rid, name, band, wage, training) in spec.role_defs] + [""]
    return "\n".join(lines[:at] + block + lines[at:])


def build_all():
    load_existing()
    specs = []
    for name in MODULES:
        if not os.path.exists(os.path.join(HERE, name + ".py")):
            continue
        spec = importlib.import_module(name).build()
        for g in spec.goods:
            cg.GOODCAT[g[0]] = g[2]
            cg.PRICES[g[0]] = g[3]
        specs.append(spec)
    problems = []
    for spec in specs:
        link_tech(spec)
        fit_prices(spec)
        calibrate(spec)
        problems += [f"{spec.obj}: {p}" for p in check(spec)]
        for kind, ids in (("industry", [i[0] for i in spec.industries]), ("good", [g[0] for g in spec.goods]),
                          ("role", [r[0] for r in spec.role_defs]), ("facility", [f[0] for f in spec.facilities]),
                          ("recipe", [r["id"] for r in spec.recipes]), ("tech", [t[0] for t in spec.techs]),
                          ("machine", [m[0] for m in spec.machines])):
            KNOWN[kind] |= set(ids)
    return specs, problems


def r_is_discrete(spec, rid):
    return any(r["id"] == rid and r.get("qty") for r in spec.recipes)


def report(specs):
    for spec in specs:
        rows, pays = economics(spec)
        print(f"== {spec.obj}: {len(spec.goods)} goods, {len(spec.recipes)} recipes, {len(spec.facilities)} facilities, {len(spec.techs)} techs")
        for (rid, fid, cost, value, margin, per_day, ticks, k) in rows:
            flag = "" if MARGIN_BAND[0] <= margin <= MARGIN_BAND[1] else "  <-- margin"
            if r_is_discrete(spec, rid) and not 0.7 <= k <= 1.4:
                flag += f"  <-- batch scale {k:.2f}"
            print(f"  {rid:34s} {fid:26s} cost {cost:12,.0f} value {value:12,.0f} margin {margin*100:6.1f}% ticks {ticks:3d}{flag}")
        for fid, days in pays.items():
            flag = "" if days is not None and PAYBACK_BAND[0] <= days <= PAYBACK_BAND[1] else "  <-- payback"
            print(f"  facility {fid:30s} payback {days}{flag}")


def main(argv):
    specs, problems = build_all()
    if problems:
        print("\n".join(problems))
        return 2
    if "--report" in argv:
        report(specs)
        return 0
    bad = 0
    for spec in specs:
        text = render_pack(spec)
        path = os.path.join(PACK_DIR, spec.obj + ".kt")
        if "--check" in argv:
            if not os.path.exists(path) or open(path).read() != text:
                print("stale:", spec.obj)
                bad = 1
        else:
            open(path, "w").write(text)
            print("wrote", spec.obj, len(spec.recipes), "recipes", len(spec.goods), "goods")
    return bad


if __name__ == "__main__":
    import expgen  # the specs import this module by name, so run the one they share
    sys.exit(expgen.main(sys.argv[1:]))

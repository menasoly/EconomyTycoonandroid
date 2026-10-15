#!/usr/bin/env python3
import re, sys, os, importlib
sys.path.insert(0, os.path.dirname(__file__))
import contentgen as cg
MODS = [m for m in os.environ.get("PACKS", "spec_agri,spec_food").split(",") if m]
base = open("/home/claude/et/src/app/many/economytycoon/content/packs/BasePack.kt").read()
wages = {m.group(1): float(m.group(2)) for m in re.finditer(r'role\("([a-z_]+)",\s*"[^"]*",\s*SkillBand\.\w+,\s*([0-9.]+)', base)}
for m in re.finditer(r'good\("([a-z_0-9]+)",\s*"[^"]*",\s*GoodCategory\.(\w+),\s*([0-9.]+)', base):
    cg.PRICES[m.group(1)] = float(m.group(3)); cg.GOODCAT[m.group(1)] = m.group(2)
specs = [importlib.import_module(m).build() for m in MODS]
for sp in specs:
    for g in sp.goods: cg.GOODCAT[g[0]] = g[2]; cg.PRICES[g[0]] = g[3]
for sp in specs:
    open(os.path.join(cg.OUT, sp.obj + ".kt"), "w").write(cg.render(sp, wages))
    print("wrote", sp.obj, len(sp.recipes), "recipes", len(sp.goods), "goods")

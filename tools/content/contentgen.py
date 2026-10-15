#!/usr/bin/env python3
"""Generates Kotlin content packs from compact specs. Output quantities of each recipe are solved so that
the recipe margin at reference prices matches a per-category target after labor and energy."""
import math, os, sys, importlib

BASE_WAGES = {}
PRICES = {}
ENERGY_PRICE = 8.0
OUT = "/home/claude/et/src/app/many/economytycoon/content/packs/"

def fmt(x):
    if abs(x - round(x)) < 1e-9: return f"{int(round(x))}.0"
    s = f"{x:.3f}".rstrip("0")
    return s

def kt_str(s): return '"' + s.replace('"', '\\"') + '"'

class Spec:
    def __init__(self, pack_id, obj, major=1, minor=0):
        self.pack_id, self.obj, self.major, self.minor = pack_id, obj, major, minor
        self.industries=[]; self.goods=[]; self.roles=[]; self.facilities=[]; self.machines=[]
        self.recipes=[]; self.techs=[]; self.vehicles=[]; self.demand=[]
    def industry(self, id, name, family, desc=""): self.industries.append((id,name,family,desc))
    def good(self, id, name, cat, price, vol="NORMAL", spoil=0, weight=1000, volume=1000, tradable=True):
        self.goods.append((id,name,cat,price,vol,spoil,weight,volume,tradable)); PRICES[id]=price
    def facility(self, id, name, cls, family, cost, ticks, lines, storage, upkeep, energy=0.0, staff=None, extracts=None, tech=None, build_inputs=None):
        self.facilities.append((id,name,cls,family,cost,ticks,lines,storage,upkeep,energy,staff or {},extracts,tech,build_inputs or {}))
    def machine(self, id, name, cost, thr=1.0, wear=500, maint=0.0, labor_sav=0.0, tech=None):
        self.machines.append((id,name,cost,thr,wear,maint,labor_sav,tech))
    def recipe(self, id, name, industry, facility, ticks, inputs, outputs, labor, energy=0.0, waste=None, tech=None, machines=None, margin=None, defect=20000, fixed=False):
        self.recipes.append(dict(id=id,name=name,industry=industry,facility=facility,ticks=ticks,inputs=inputs,outputs=outputs,labor=labor,energy=energy,waste=waste or {},tech=tech,machines=machines or [],margin=margin,defect=defect,fixed=fixed))
    def tech(self, id, name, cat, tier, cost, ticks, requires=(), recipes=(), facilities=(), machines=(), effects=()):
        self.techs.append((id,name,cat,tier,cost,ticks,list(requires),list(recipes),list(facilities),list(machines),list(effects)))
    def vehicle(self, *a): self.vehicles.append(a)
    def dem(self, good, low, mid, high, elasticity, income=0.8, subs=(), seasons=None, non=0.0):
        self.demand.append((good,low,mid,high,elasticity,income,list(subs),seasons or {},non))

CATEGORY_MARGIN = {"RAW":0.12, "INTERMEDIATE":0.14, "CONSUMER":0.20, "INDUSTRIAL":0.16, "ENERGY":0.12, "SERVICE":0.24, "WASTE":0.0}
GOODCAT = {}

def solve_outputs(r, wages):
    inp = sum(PRICES[g]*q for g,q in r["inputs"].items())
    lab = sum(wages[role]*n for role,n in r["labor"].items()) * r["ticks"]
    en = r["energy"] * ENERGY_PRICE
    cost = inp + lab + en
    # primary output decides margin target
    prim = list(r["outputs"].items())[0][0]
    m = r["margin"] if r["margin"] is not None else CATEGORY_MARGIN[GOODCAT[prim]]
    revenue = cost * (1 + m)
    unit_value = sum(PRICES[g]*ratio for g,ratio in r["outputs"].items())
    scale = revenue / unit_value
    out = {}
    for g, ratio in r["outputs"].items():
        q = ratio * scale
        if q >= 20: q = round(q / 5) * 5
        elif q >= 5: q = round(q)
        else: q = round(q, 1)
        out[g] = max(q, 0.5)
    return out

def render(spec, wages):
    L = []
    a = L.append
    a(f"package app.many.economytycoon.content.packs\n")
    a("import app.many.economytycoon.content.ContentPack")
    a("import app.many.economytycoon.content.EffectKind")
    a("import app.many.economytycoon.content.FacilityClass")
    a("import app.many.economytycoon.content.GoodCategory")
    a("import app.many.economytycoon.content.IndustryFamily")
    a("import app.many.economytycoon.content.SkillBand")
    a("import app.many.economytycoon.content.TechCategory")
    a("import app.many.economytycoon.content.TransportMode")
    a("import app.many.economytycoon.content.VolatilityClass")
    a("import app.many.economytycoon.content.contentPack\n")
    a(f"object {spec.obj} {{")
    a(f"    val pack: ContentPack = contentPack({kt_str(spec.pack_id)}, {spec.major}, {spec.minor}) {{")
    for id,name,fam,desc in spec.industries:
        a(f"        industry({kt_str(id)}, {kt_str(name)}, IndustryFamily.{fam}{', '+kt_str(desc) if desc else ''})")
    if spec.industries: a("")
    for id,name,cat,price,vol,spoil,weight,volume,tradable in spec.goods:
        extra=""
        if vol!="NORMAL": extra+=f", VolatilityClass.{vol}"
        if spoil: extra+=f", spoilagePpm = {spoil}"
        if weight!=1000: extra+=f", weight = {weight}"
        if volume!=1000: extra+=f", volume = {volume}"
        if not tradable: extra+=", tradable = false"
        a(f"        good({kt_str(id)}, {kt_str(name)}, GoodCategory.{cat}, {fmt(price)}{extra})")
    if spec.goods: a("")
    for id,name,cost,thr,wear,maint,lsav,tech in spec.machines:
        extra=""
        if thr!=1.0: extra+=f", throughput = {fmt(thr) if thr==int(thr) else thr}"
        if wear!=500: extra+=f", wearPerBatchPpm = {wear}"
        if maint: extra+=f", maintenance = {fmt(maint)}"
        if lsav: extra+=f", laborSavings = {lsav}"
        if tech: extra+=f", tech = {kt_str(tech)}"
        a(f"        machine({kt_str(id)}, {kt_str(name)}, {fmt(cost)}{extra})")
    if spec.machines: a("")
    for (id,name,cls,fam,cost,ticks,lines,storage,upkeep,energy,staff,extracts,tech,binputs) in spec.facilities:
        a(f"        facility({kt_str(id)}, {kt_str(name)}, FacilityClass.{cls}, IndustryFamily.{fam}) {{")
        a(f"            cost = {fmt(cost)}; buildTicks = {ticks}; lines = {lines}; storage = {fmt(storage)}; upkeep = {fmt(upkeep)}")
        if energy: a(f"            energyPerLine = {fmt(energy)}")
        if tech: a(f"            tech = {kt_str(tech)}")
        if extracts: a(f"            extracts = {kt_str(extracts)}")
        for role,n in staff.items(): a(f"            staff({kt_str(role)}, {n})")
        for g,q in binputs.items(): a(f"            buildInput({kt_str(g)}, {fmt(q)})")
        a("        }")
    if spec.facilities: a("")
    for r in spec.recipes:
        outs = r["outputs"] if r["fixed"] else solve_outputs(r, wages)
        a(f"        recipe({kt_str(r['id'])}, {kt_str(r['name'])}, {kt_str(r['industry'])}, {kt_str(r['facility'])}, {r['ticks']}) {{")
        for g,q in r["inputs"].items(): a(f"            input({kt_str(g)}, {fmt(q)})")
        for g,q in outs.items(): a(f"            output({kt_str(g)}, {fmt(q)})")
        for g,q in r["waste"].items(): a(f"            waste({kt_str(g)}, {fmt(q)})")
        for role,n in r["labor"].items(): a(f"            labor({kt_str(role)}, {n})")
        for m in r["machines"]: a(f"            machine({kt_str(m)})")
        if r["energy"]: a(f"            energy = {fmt(r['energy'])}")
        if r["tech"]: a(f"            tech = {kt_str(r['tech'])}")
        if r["defect"]!=20000: a(f"            defectPpm = {r['defect']}")
        a("        }")
    if spec.recipes: a("")
    for (id,name,cat,tier,cost,ticks,req,rec,fac,mac,eff) in spec.techs:
        a(f"        tech({kt_str(id)}, {kt_str(name)}, TechCategory.{cat}, {tier}, {fmt(cost)}, {ticks}) {{")
        if req: a(f"            requires({', '.join(kt_str(x) for x in req)})")
        if rec: a(f"            unlocksRecipe({', '.join(kt_str(x) for x in rec)})")
        if fac: a(f"            unlocksFacility({', '.join(kt_str(x) for x in fac)})")
        if mac: a(f"            unlocksMachine({', '.join(kt_str(x) for x in mac)})")
        for (kind,mag,ind,rcp) in eff:
            ex=""
            if ind: ex+=f", industry = {kt_str(ind)}"
            if rcp: ex+=f", recipe = {kt_str(rcp)}"
            a(f"            effect(EffectKind.{kind}, {mag}{ex})")
        a("        }")
    if spec.techs: a("")
    for (good,low,mid,high,el,inc,subs,seasons,non) in spec.demand:
        ex=""
        if inc!=0.8: ex+=f", incomeElasticity = {inc}"
        if subs: ex+=f", substitutes = listOf({', '.join(kt_str(x) for x in subs)})"
        if seasons: ex+=f", seasons = mapOf({', '.join(f'app.many.economytycoon.core.Season.{k} to {v}' for k,v in seasons.items())})"
        if non: ex+=f", nonConsumers = {non}"
        a(f"        demand({kt_str(good)}, {low}L, {mid}L, {high}L, {el}{ex})")
    if L[-1]=="": L.pop()
    a("    }")
    a("}")
    return "\n".join(L)+"\n"

def run(modname, wages):
    m = importlib.import_module(modname)
    spec = m.build()
    for g in spec.goods: GOODCAT[g[0]] = g[2]
    return spec

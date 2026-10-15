from contentgen import Spec

def build():
    s = Spec("mining", "MiningPack")
    s.industry("mining_ores", "Ore mining", "MINING", "Metal ores and industrial minerals")
    s.industry("quarrying", "Quarrying", "MINING", "Stone, sand, gravel, clay")
    s.industry("metallurgy", "Metallurgy", "METALS", "Smelting and refining")
    s.industry("metalworking", "Metalworking", "METALS", "Rolling, casting, fabrication")

    for gid, name, price, vol in [
        ("iron_ore", "Iron ore", 18, "NORMAL"), ("copper_ore", "Copper ore", 40, "VOLATILE"), ("bauxite", "Bauxite", 25, "NORMAL"),
        ("limestone", "Limestone", 8, "STABLE"), ("sand", "Sand", 6, "STABLE"), ("gravel", "Gravel", 5, "STABLE"),
        ("clay", "Clay", 7, "STABLE"), ("salt", "Salt", 6, "STABLE"), ("stone_blocks_raw", "Quarry stone", 12, "STABLE"),
        ("gold_ore", "Gold ore", 260, "SPECULATIVE"), ("lithium_ore", "Lithium ore", 90, "VOLATILE"),
        ("rare_earth_ore", "Rare earth ore", 200, "SPECULATIVE"), ("silica_sand", "Silica sand", 12, "STABLE"),
        ("tin_ore", "Tin ore", 70, "VOLATILE"), ("zinc_ore", "Zinc ore", 32, "VOLATILE"), ("nickel_ore", "Nickel ore", 55, "VOLATILE"),
    ]:
        s.good(gid, name, "RAW", price, vol, 0, 1_400 if vol != "SPECULATIVE" else 1_200)
    for gid, name, price, vol in [
        ("pig_iron", "Pig iron", 40, "NORMAL"), ("steel", "Steel", 75, "NORMAL"), ("steel_sheet", "Steel sheet", 95, "NORMAL"),
        ("steel_beams", "Structural steel", 90, "NORMAL"), ("steel_wire", "Steel wire", 85, "NORMAL"), ("steel_pipe", "Steel pipe", 92, "NORMAL"),
        ("copper_ingot", "Copper ingot", 120, "VOLATILE"), ("copper_wire", "Copper wire", 150, "VOLATILE"), ("copper_pipe", "Copper pipe", 145, "VOLATILE"),
        ("alumina", "Alumina", 55, "NORMAL"), ("aluminum_ingot", "Aluminum ingot", 110, "VOLATILE"), ("aluminum_sheet", "Aluminum sheet", 135, "VOLATILE"),
        ("scrap_metal", "Scrap metal", 10, "NORMAL"), ("zinc_ingot", "Zinc ingot", 85, "VOLATILE"), ("tin_ingot", "Tin ingot", 190, "VOLATILE"),
        ("nickel_ingot", "Nickel ingot", 160, "VOLATILE"), ("gold_bar", "Gold bar", 1_400, "SPECULATIVE"), ("lithium_carbonate", "Lithium carbonate", 340, "VOLATILE"),
        ("rare_earth_oxide", "Rare earth oxide", 650, "SPECULATIVE"), ("cement", "Cement", 14, "STABLE"), ("lime", "Quicklime", 12, "STABLE"),
        ("glass", "Glass sheet", 30, "STABLE"), ("bricks", "Bricks", 18, "STABLE"), ("stainless_steel", "Stainless steel", 190, "NORMAL"),
        ("castings", "Iron castings", 70, "NORMAL"), ("forgings", "Steel forgings", 110, "NORMAL"), ("fasteners", "Fasteners", 22, "STABLE"),
        ("sheet_metal_parts", "Sheet metal parts", 55, "STABLE"),
    ]:
        s.good(gid, name, "INTERMEDIATE", price, vol, 0, 1_500)

    F = s.facility
    F("iron_mine", "Iron mine", "MINE", "MINING", 1_200_000, 70, 3, 60_000, 500, 30.0, {"miner": 2}, "iron_ore")
    F("copper_mine", "Copper mine", "MINE", "MINING", 1_500_000, 80, 3, 60_000, 560, 35.0, {"miner": 2}, "copper_ore")
    F("bauxite_mine", "Bauxite mine", "MINE", "MINING", 1_100_000, 70, 3, 60_000, 480, 28.0, {"miner": 2}, "bauxite")
    F("quarry", "Quarry", "MINE", "MINING", 700_000, 50, 4, 100_000, 320, 18.0, {"miner": 1}, "stone_blocks_raw")
    F("sand_pit", "Sand and gravel pit", "MINE", "MINING", 380_000, 35, 3, 100_000, 200, 10.0, {"laborer": 1}, "sand")
    F("precious_mine", "Precious metals mine", "MINE", "MINING", 2_600_000, 100, 2, 20_000, 900, 55.0, {"miner": 3, "engineer": 1}, "gold_ore")
    F("lithium_mine", "Lithium mine", "MINE", "MINING", 2_200_000, 90, 2, 30_000, 800, 50.0, {"miner": 3, "engineer": 1}, "lithium_ore")
    F("rare_earth_mine", "Rare earth mine", "MINE", "MINING", 3_100_000, 110, 2, 20_000, 1_000, 60.0, {"miner": 3, "engineer": 1}, "rare_earth_ore")
    F("blast_furnace", "Blast furnace", "FACTORY", "METALS", 3_500_000, 90, 2, 80_000, 1_100, 150.0, {"operator": 3})
    F("steel_mill", "Steel mill", "FACTORY", "METALS", 4_200_000, 100, 3, 80_000, 1_400, 180.0, {"operator": 3, "engineer": 1})
    F("copper_smelter", "Copper smelter", "FACTORY", "METALS", 2_900_000, 80, 2, 50_000, 900, 120.0, {"operator": 3})
    F("aluminum_smelter", "Aluminum smelter", "FACTORY", "METALS", 3_400_000, 90, 2, 50_000, 1_100, 260.0, {"operator": 3})
    F("rolling_mill", "Rolling mill", "FACTORY", "METALS", 2_400_000, 70, 3, 60_000, 800, 110.0, {"operator": 2})
    F("foundry", "Foundry", "FACTORY", "METALS", 1_800_000, 60, 3, 40_000, 700, 90.0, {"operator": 2})
    F("metalworks", "Metal fabrication shop", "FACTORY", "METALS", 1_100_000, 50, 4, 30_000, 480, 45.0, {"machinist": 2})
    F("refinery_minerals", "Mineral processing plant", "FACTORY", "METALS", 1_900_000, 70, 3, 40_000, 650, 80.0, {"operator": 2, "technician": 1})
    F("cement_plant", "Cement plant", "FACTORY", "CONSTRUCTION", 2_300_000, 80, 2, 80_000, 850, 140.0, {"operator": 3})
    F("glassworks", "Glassworks", "FACTORY", "CONSTRUCTION", 1_700_000, 65, 2, 40_000, 700, 120.0, {"operator": 2})
    F("brickworks", "Brickworks", "FACTORY", "CONSTRUCTION", 900_000, 55, 3, 60_000, 420, 70.0, {"operator": 2})

    R = s.recipe
    # Extraction
    R("mine_iron", "Mine iron ore", "mining_ores", "iron_mine", 8, {"diesel": 90, "explosives": 15, "water": 80}, {"iron_ore": 1}, {"miner": 8, "operator": 1}, energy=60)
    R("mine_copper", "Mine copper ore", "mining_ores", "copper_mine", 9, {"diesel": 100, "explosives": 18, "water": 90}, {"copper_ore": 1}, {"miner": 8, "operator": 1}, energy=70)
    R("mine_bauxite", "Mine bauxite", "mining_ores", "bauxite_mine", 8, {"diesel": 80, "explosives": 12, "water": 70}, {"bauxite": 1}, {"miner": 7}, energy=55)
    R("mine_gold", "Mine gold ore", "mining_ores", "precious_mine", 14, {"diesel": 120, "explosives": 30, "water": 200}, {"gold_ore": 1}, {"miner": 9, "engineer": 1}, energy=110)
    R("mine_lithium", "Mine lithium ore", "mining_ores", "lithium_mine", 12, {"diesel": 110, "explosives": 25, "water": 250}, {"lithium_ore": 1}, {"miner": 8, "engineer": 1}, energy=100)
    R("mine_rare_earth", "Mine rare earth ore", "mining_ores", "rare_earth_mine", 14, {"diesel": 120, "explosives": 30, "water": 220}, {"rare_earth_ore": 1}, {"miner": 9, "engineer": 1}, energy=120)
    R("mine_tin", "Mine tin ore", "mining_ores", "copper_mine", 10, {"diesel": 90, "explosives": 15, "water": 90}, {"tin_ore": 1}, {"miner": 7}, energy=60)
    R("mine_zinc", "Mine zinc ore", "mining_ores", "copper_mine", 9, {"diesel": 90, "explosives": 15, "water": 80}, {"zinc_ore": 1}, {"miner": 7}, energy=60)
    R("mine_nickel", "Mine nickel ore", "mining_ores", "copper_mine", 10, {"diesel": 95, "explosives": 18, "water": 90}, {"nickel_ore": 1}, {"miner": 7}, energy=65)
    R("quarry_stone", "Quarry stone", "quarrying", "quarry", 5, {"diesel": 60, "explosives": 10}, {"stone_blocks_raw": 1}, {"miner": 4, "operator": 1}, energy=40)
    R("quarry_limestone", "Quarry limestone", "quarrying", "quarry", 4, {"diesel": 50, "explosives": 8}, {"limestone": 1}, {"miner": 4}, energy=35)
    R("dig_sand", "Dig sand", "quarrying", "sand_pit", 3, {"diesel": 45}, {"sand": 1}, {"laborer": 3, "operator": 1}, energy=18)
    R("dig_gravel", "Dig gravel", "quarrying", "sand_pit", 3, {"diesel": 45}, {"gravel": 1}, {"laborer": 3, "operator": 1}, energy=18)
    R("dig_clay", "Dig clay", "quarrying", "sand_pit", 3, {"diesel": 40}, {"clay": 1}, {"laborer": 3}, energy=16)
    R("wash_silica", "Wash silica sand", "quarrying", "sand_pit", 3, {"sand": 150, "water": 200}, {"silica_sand": 1}, {"operator": 2}, energy=24)
    R("evaporate_salt", "Harvest salt", "quarrying", "sand_pit", 10, {"water": 600}, {"salt": 1}, {"laborer": 2}, energy=10)
    # Smelting and refining
    R("make_pig_iron", "Smelt pig iron", "metallurgy", "blast_furnace", 5, {"iron_ore": 300, "coal": 150, "limestone": 40}, {"pig_iron": 1}, {"operator": 6}, energy=240, waste={"scrap": 20})
    R("make_steel", "Make steel", "metallurgy", "steel_mill", 4, {"pig_iron": 200, "scrap_metal": 60, "lubricants": 2}, {"steel": 1}, {"operator": 6, "engineer": 1}, energy=320, waste={"scrap": 15})
    R("make_steel_scrap", "Recycle scrap into steel", "metallurgy", "steel_mill", 4, {"scrap_metal": 280, "coal": 20}, {"steel": 1}, {"operator": 5}, energy=380)
    R("make_stainless", "Make stainless steel", "metallurgy", "steel_mill", 5, {"steel": 120, "nickel_ingot": 20}, {"stainless_steel": 1}, {"operator": 5, "engineer": 1}, energy=300)
    R("smelt_copper", "Smelt copper", "metallurgy", "copper_smelter", 5, {"copper_ore": 400, "coal": 80, "limestone": 30}, {"copper_ingot": 1}, {"operator": 5}, energy=260, waste={"scrap": 12})
    R("smelt_zinc", "Smelt zinc", "metallurgy", "copper_smelter", 5, {"zinc_ore": 350, "coal": 60}, {"zinc_ingot": 1}, {"operator": 5}, energy=230)
    R("smelt_tin", "Smelt tin", "metallurgy", "copper_smelter", 5, {"tin_ore": 300, "coal": 60}, {"tin_ingot": 1}, {"operator": 5}, energy=220)
    R("smelt_nickel", "Smelt nickel", "metallurgy", "copper_smelter", 6, {"nickel_ore": 320, "coal": 70}, {"nickel_ingot": 1}, {"operator": 5}, energy=260)
    R("refine_gold", "Refine gold", "metallurgy", "refinery_minerals", 6, {"gold_ore": 400, "water": 200, "sulfuric_acid": 40}, {"gold_bar": 1}, {"operator": 3, "technician": 1}, energy=180)
    R("extract_lithium", "Process lithium carbonate", "metallurgy", "refinery_minerals", 6, {"lithium_ore": 300, "sulfuric_acid": 30, "water": 300}, {"lithium_carbonate": 1}, {"operator": 3, "technician": 1}, energy=160)
    R("separate_rare_earths", "Separate rare earth oxides", "metallurgy", "refinery_minerals", 8, {"rare_earth_ore": 250, "sulfuric_acid": 60, "water": 300}, {"rare_earth_oxide": 1}, {"operator": 3, "technician": 2}, energy=200)
    R("make_alumina", "Refine alumina", "metallurgy", "refinery_minerals", 5, {"bauxite": 400, "lime": 40, "water": 200}, {"alumina": 1}, {"operator": 4}, energy=170)
    R("smelt_aluminum", "Smelt aluminum", "metallurgy", "aluminum_smelter", 5, {"alumina": 300}, {"aluminum_ingot": 1}, {"operator": 5}, energy=520)
    R("burn_lime", "Burn lime", "metallurgy", "cement_plant", 3, {"limestone": 200, "coal": 25}, {"lime": 1}, {"operator": 2}, energy=60)
    # Rolling and fabrication
    R("roll_sheet", "Roll steel sheet", "metalworking", "rolling_mill", 3, {"steel": 120, "lubricants": 2}, {"steel_sheet": 1}, {"operator": 4}, energy=140)
    R("roll_beams", "Roll structural steel", "metalworking", "rolling_mill", 3, {"steel": 130, "lubricants": 2}, {"steel_beams": 1}, {"operator": 4}, energy=150)
    R("draw_steel_wire", "Draw steel wire", "metalworking", "rolling_mill", 3, {"steel": 100, "lubricants": 2}, {"steel_wire": 1}, {"operator": 3}, energy=100)
    R("make_steel_pipe", "Make steel pipe", "metalworking", "rolling_mill", 3, {"steel": 110, "lubricants": 2}, {"steel_pipe": 1}, {"operator": 3}, energy=110)
    R("draw_copper_wire", "Draw copper wire", "metalworking", "rolling_mill", 3, {"copper_ingot": 90, "lubricants": 2}, {"copper_wire": 1}, {"operator": 3}, energy=70)
    R("make_copper_pipe", "Make copper pipe", "metalworking", "rolling_mill", 3, {"copper_ingot": 95}, {"copper_pipe": 1}, {"operator": 3}, energy=70)
    R("roll_aluminum", "Roll aluminum sheet", "metalworking", "rolling_mill", 3, {"aluminum_ingot": 100, "lubricants": 2}, {"aluminum_sheet": 1}, {"operator": 3}, energy=110)
    R("cast_iron", "Cast iron parts", "metalworking", "foundry", 3, {"pig_iron": 130, "scrap_metal": 30}, {"castings": 1}, {"operator": 3}, energy=150)
    R("forge_steel", "Forge steel parts", "metalworking", "foundry", 4, {"steel": 120, "lubricants": 3}, {"forgings": 1}, {"operator": 3, "machinist": 1}, energy=180)
    R("press_sheet_parts", "Press sheet metal parts", "metalworking", "metalworks", 2, {"steel_sheet": 90, "lubricants": 1}, {"sheet_metal_parts": 1}, {"machinist": 3}, energy=60, waste={"scrap": 6})
    R("make_fasteners", "Make fasteners", "metalworking", "metalworks", 2, {"steel_wire": 70, "lubricants": 1}, {"fasteners": 1}, {"machinist": 2}, energy=45)
    # Construction minerals
    R("make_cement", "Make cement", "metallurgy", "cement_plant", 4, {"limestone": 300, "clay": 60, "coal": 60}, {"cement": 1}, {"operator": 3}, energy=200)
    R("make_glass", "Make glass sheet", "metallurgy", "glassworks", 3, {"silica_sand": 200, "limestone": 40, "natural_gas": 40}, {"glass": 1}, {"operator": 3}, energy=160)
    R("fire_bricks", "Fire bricks", "metallurgy", "brickworks", 4, {"clay": 300, "natural_gas": 30}, {"bricks": 1}, {"operator": 2}, energy=80)
    # Technologies
    s.tech("deep_mining", "Deep shaft mining", "MATERIALS", 1, 200_000, 60, effects=[("YIELD_BONUS", 0.06, "mining_ores", None)])
    s.tech("blast_optimization", "Blast optimization", "MATERIALS", 2, 380_000, 90, requires=["deep_mining"], effects=[("INPUT_SAVINGS", 0.08, "mining_ores", None)])
    s.tech("continuous_casting", "Continuous casting", "MATERIALS", 2, 450_000, 90, effects=[("YIELD_BONUS", 0.05, "metallurgy", None), ("ENERGY_SAVINGS", 0.06, "metallurgy", None)])
    s.tech("electric_arc_furnace", "Electric arc furnaces", "ENERGY", 3, 750_000, 120, requires=["continuous_casting"], effects=[("ENERGY_SAVINGS", 0.10, "metallurgy", None)])
    s.tech("precision_rolling", "Precision rolling", "PRODUCTION", 2, 400_000, 90, requires=["continuous_casting"], effects=[("QUALITY_BONUS", 0.12, "metalworking", None)])
    return s

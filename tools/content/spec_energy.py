from contentgen import Spec

def build():
    s = Spec("energy", "EnergyPack")
    s.industry("oil_gas", "Oil and gas", "OIL_AND_GAS", "Extraction and refining")
    s.industry("power", "Power generation", "ENERGY", "Thermal and nuclear generation")
    s.industry("renewables", "Renewable energy", "RENEWABLE_ENERGY", "Wind, solar, hydro, geothermal")
    s.industry("chemicals", "Chemicals", "CHEMICALS", "Basic and specialty chemicals")

    s.good("crude_oil", "Crude oil", "RAW", 70, "VOLATILE", 0, 1_100)
    s.good("uranium_ore", "Uranium ore", "RAW", 320, "VOLATILE", 0, 1_600)
    s.good("biomass", "Biomass feedstock", "RAW", 9, "NORMAL", 8000)
    s.good("brine", "Brine", "RAW", 2, "STABLE")
    for gid, name, price, vol in [
        ("kerosene", "Jet fuel", 15, "VOLATILE"), ("naphtha", "Naphtha", 46, "VOLATILE"), ("bitumen", "Bitumen", 24, "NORMAL"),
        ("propane", "Propane", 11, "VOLATILE"), ("polyethylene", "Polyethylene resin", 48, "NORMAL"), ("polypropylene", "Polypropylene resin", 52, "NORMAL"),
        ("pvc_resin", "PVC resin", 44, "NORMAL"), ("synthetic_rubber", "Synthetic rubber", 60, "NORMAL"), ("sulfuric_acid", "Sulfuric acid", 25, "STABLE"),
        ("caustic_soda", "Caustic soda", 32, "STABLE"), ("chlorine", "Chlorine", 28, "STABLE"), ("ammonia", "Ammonia", 45, "NORMAL"),
        ("nitric_acid", "Nitric acid", 34, "STABLE"), ("industrial_gases", "Industrial gases", 20, "STABLE"), ("solvents", "Solvents", 38, "NORMAL"),
        ("pigments", "Pigments", 70, "NORMAL"), ("resins_epoxy", "Epoxy resin", 90, "NORMAL"), ("adhesives", "Adhesives", 55, "NORMAL"),
        ("phosphate_rock", "Phosphate rock", 20, "NORMAL"), ("enriched_uranium", "Nuclear fuel", 2_400, "STABLE"), ("biodiesel", "Biodiesel", 14, "NORMAL"),
        ("ethanol", "Ethanol", 22, "NORMAL"), ("hydrogen", "Hydrogen", 40, "NORMAL"), ("activated_carbon", "Activated carbon", 52, "STABLE"),
        ("plastic_granules", "Plastic granules", 50, "NORMAL"), ("detergent_base", "Detergent base", 36, "NORMAL"), ("glycerin", "Glycerin", 40, "NORMAL"),
    ]:
        s.good(gid, name, "INTERMEDIATE", price, vol)
    s.good("explosives", "Industrial explosives", "INDUSTRIAL", 60, "STABLE")
    s.good("paint", "Paint", "INDUSTRIAL", 34, "STABLE")
    s.good("pesticides", "Pesticides", "INDUSTRIAL", 66, "STABLE")
    s.good("lithium_battery_cells", "Battery cells", "INDUSTRIAL", 160, "NORMAL")
    s.good("solar_cells", "Solar cells", "INDUSTRIAL", 140, "NORMAL")
    s.good("fertilizer_compound", "Compound fertilizer", "INDUSTRIAL", 31, "NORMAL")

    F = s.facility
    F("oil_well", "Oil well field", "WELL", "OIL_AND_GAS", 3_000_000, 80, 3, 80_000, 900, 60.0, {"operator": 2}, "crude_oil")
    F("gas_well", "Gas well field", "WELL", "OIL_AND_GAS", 2_600_000, 70, 3, 60_000, 800, 45.0, {"operator": 2}, "natural_gas")
    F("oil_refinery", "Oil refinery", "REFINERY", "OIL_AND_GAS", 9_000_000, 130, 3, 150_000, 2_600, 300.0, {"operator": 5, "engineer": 2})
    F("petrochemical_plant", "Petrochemical plant", "REFINERY", "CHEMICALS", 7_500_000, 120, 3, 100_000, 2_100, 260.0, {"operator": 4, "engineer": 2})
    F("chemical_plant", "Chemical plant", "FACTORY", "CHEMICALS", 3_200_000, 90, 4, 60_000, 1_100, 120.0, {"operator": 3, "technician": 1})
    F("fertilizer_plant", "Fertilizer plant", "FACTORY", "CHEMICALS", 2_800_000, 85, 3, 80_000, 950, 130.0, {"operator": 3})
    F("explosives_plant", "Explosives plant", "FACTORY", "CHEMICALS", 2_200_000, 80, 2, 20_000, 900, 70.0, {"operator": 3, "technician": 1})
    F("coal_power_plant", "Coal power plant", "POWER_PLANT", "ENERGY", 6_000_000, 120, 3, 120_000, 1_700, 0.0, {"operator": 4, "engineer": 1})
    F("gas_power_plant", "Gas power plant", "POWER_PLANT", "ENERGY", 4_500_000, 100, 3, 40_000, 1_300, 0.0, {"operator": 3, "engineer": 1})
    F("oil_power_plant", "Oil power plant", "POWER_PLANT", "ENERGY", 3_800_000, 90, 3, 60_000, 1_200, 0.0, {"operator": 3})
    F("nuclear_plant", "Nuclear power plant", "POWER_PLANT", "ENERGY", 40_000_000, 300, 2, 30_000, 8_000, 0.0, {"operator": 12, "engineer": 4}, tech="nuclear_power")
    F("wind_farm", "Wind farm", "POWER_PLANT", "RENEWABLE_ENERGY", 5_500_000, 110, 4, 5_000, 900, 0.0, {"technician": 2})
    F("solar_farm", "Solar farm", "POWER_PLANT", "RENEWABLE_ENERGY", 6_500_000, 100, 4, 5_000, 800, 0.0, {"technician": 2})
    F("hydro_plant", "Hydroelectric dam", "POWER_PLANT", "RENEWABLE_ENERGY", 25_000_000, 240, 2, 5_000, 3_000, 0.0, {"operator": 4, "engineer": 2})
    F("geothermal_plant", "Geothermal plant", "POWER_PLANT", "RENEWABLE_ENERGY", 14_000_000, 180, 2, 10_000, 2_200, 0.0, {"operator": 4, "engineer": 1})
    F("biomass_plant", "Biomass power plant", "POWER_PLANT", "RENEWABLE_ENERGY", 4_800_000, 100, 3, 60_000, 1_300, 0.0, {"operator": 3})
    F("biofuel_plant", "Biofuel plant", "REFINERY", "RENEWABLE_ENERGY", 3_600_000, 90, 2, 60_000, 1_100, 90.0, {"operator": 3, "technician": 1})
    F("battery_plant", "Battery plant", "FACTORY", "RENEWABLE_ENERGY", 8_500_000, 130, 3, 40_000, 2_300, 220.0, {"operator": 4, "engineer": 2}, tech="battery_manufacturing")

    R = s.recipe
    R("pump_crude", "Pump crude oil", "oil_gas", "oil_well", 10, {"explosives": 6, "water": 100}, {"crude_oil": 1}, {"operator": 6}, energy=140)
    R("extract_gas", "Extract natural gas", "oil_gas", "gas_well", 10, {"water": 80}, {"natural_gas": 1}, {"operator": 5}, energy=120)
    R("refine_gasoline", "Refine fuels", "oil_gas", "oil_refinery", 4, {"crude_oil": 600, "hydrogen": 20}, {"gasoline": 0.34, "diesel": 0.30, "kerosene": 0.14, "naphtha": 0.08, "bitumen": 0.08, "propane": 0.06}, {"operator": 10, "engineer": 2}, energy=380, waste={"chemical_waste": 20})
    R("blend_lubricants", "Blend lubricants", "oil_gas", "oil_refinery", 3, {"naphtha": 80, "bitumen": 20}, {"lubricants": 1}, {"operator": 4}, energy=100)
    R("crack_ethylene", "Produce polyethylene", "chemicals", "petrochemical_plant", 4, {"naphtha": 250, "natural_gas": 80}, {"polyethylene": 1}, {"operator": 6, "engineer": 1}, energy=260, waste={"chemical_waste": 25})
    R("make_polypropylene", "Produce polypropylene", "chemicals", "petrochemical_plant", 4, {"naphtha": 240, "propane": 40}, {"polypropylene": 1}, {"operator": 6, "engineer": 1}, energy=250, waste={"chemical_waste": 25})
    R("make_pvc", "Produce PVC resin", "chemicals", "petrochemical_plant", 4, {"naphtha": 150, "chlorine": 90}, {"pvc_resin": 1}, {"operator": 6}, energy=220, waste={"chemical_waste": 20})
    R("make_synthetic_rubber", "Produce synthetic rubber", "chemicals", "petrochemical_plant", 5, {"naphtha": 200, "sulfuric_acid": 20}, {"synthetic_rubber": 1}, {"operator": 5}, energy=230, waste={"chemical_waste": 22})
    R("granulate_plastic", "Compound plastic granules", "chemicals", "chemical_plant", 2, {"polyethylene": 90, "polypropylene": 60, "pigments": 6}, {"plastic_granules": 1}, {"operator": 3}, energy=90)
    R("make_solvents", "Make solvents", "chemicals", "chemical_plant", 3, {"naphtha": 120}, {"solvents": 1}, {"operator": 3}, energy=100, waste={"chemical_waste": 10})
    R("make_sulfuric_acid", "Make sulfuric acid", "chemicals", "chemical_plant", 2, {"coal": 40, "water": 100, "industrial_gases": 30}, {"sulfuric_acid": 1}, {"operator": 3}, energy=70, waste={"chemical_waste": 8})
    R("electrolyze_brine", "Electrolyze brine", "chemicals", "chemical_plant", 2, {"brine": 900, "salt": 40}, {"caustic_soda": 0.55, "chlorine": 0.45}, {"operator": 3}, energy=250)
    R("make_ammonia", "Synthesize ammonia", "chemicals", "fertilizer_plant", 3, {"natural_gas": 160, "industrial_gases": 60}, {"ammonia": 1}, {"operator": 4}, energy=220)
    R("make_nitric_acid", "Make nitric acid", "chemicals", "chemical_plant", 2, {"ammonia": 60, "water": 100}, {"nitric_acid": 1}, {"operator": 3}, energy=70)
    R("separate_gases", "Separate industrial gases", "chemicals", "chemical_plant", 2, {"water": 40}, {"industrial_gases": 1}, {"operator": 3}, energy=240)
    R("make_hydrogen", "Produce hydrogen", "chemicals", "chemical_plant", 2, {"natural_gas": 90, "water": 60}, {"hydrogen": 1}, {"operator": 3}, energy=120)
    R("mix_fertilizer", "Mix fertilizer", "chemicals", "fertilizer_plant", 2, {"ammonia": 40, "phosphate_rock": 60, "packaging": 20}, {"fertilizer": 1}, {"operator": 3}, energy=70)
    R("make_compound_fertilizer", "Make compound fertilizer", "chemicals", "fertilizer_plant", 2, {"ammonia": 35, "nitric_acid": 15, "phosphate_rock": 55, "packaging": 20}, {"fertilizer_compound": 1}, {"operator": 3}, energy=75)
    R("mine_phosphate", "Mine phosphate rock", "chemicals", "fertilizer_plant", 6, {"diesel": 70, "water": 80}, {"phosphate_rock": 1}, {"operator": 5}, energy=70)
    R("make_explosives", "Make explosives", "chemicals", "explosives_plant", 4, {"nitric_acid": 50, "ammonia": 30, "packaging": 15}, {"explosives": 1}, {"operator": 3, "technician": 1}, energy=60, waste={"chemical_waste": 5}, defect=40000)
    R("make_pigments", "Make pigments", "chemicals", "chemical_plant", 3, {"sulfuric_acid": 30, "limestone": 40}, {"pigments": 1}, {"operator": 3}, energy=70, waste={"chemical_waste": 10})
    R("make_paint", "Mix paint", "chemicals", "chemical_plant", 2, {"pigments": 20, "solvents": 40, "polypropylene": 15, "packaging": 20}, {"paint": 1}, {"operator": 3}, energy=45)
    R("make_epoxy", "Make epoxy resin", "chemicals", "chemical_plant", 4, {"solvents": 70, "chlorine": 30, "caustic_soda": 20}, {"resins_epoxy": 1}, {"operator": 3, "technician": 1}, energy=90, waste={"chemical_waste": 14})
    R("make_adhesives", "Make adhesives", "chemicals", "chemical_plant", 2, {"resins_epoxy": 20, "solvents": 30, "packaging": 12}, {"adhesives": 1}, {"operator": 2}, energy=40)
    R("make_pesticides", "Make pesticides", "chemicals", "chemical_plant", 4, {"solvents": 50, "chlorine": 30, "packaging": 15}, {"pesticides": 1}, {"operator": 3, "technician": 1}, energy=70, waste={"chemical_waste": 12})
    R("make_detergent_base", "Make detergent base", "chemicals", "chemical_plant", 2, {"caustic_soda": 40, "glycerin": 10, "water": 80}, {"detergent_base": 1}, {"operator": 3}, energy=50)
    R("make_activated_carbon", "Make activated carbon", "chemicals", "chemical_plant", 3, {"coal": 120}, {"activated_carbon": 1}, {"operator": 2}, energy=110)
    # Power
    R("burn_coal", "Coal power", "power", "coal_power_plant", 1, {"coal": 700, "water": 250}, {"electricity": 1}, {"operator": 8, "engineer": 1}, waste={"scrap": 1}, margin=0.12)
    R("burn_gas", "Gas power", "power", "gas_power_plant", 1, {"natural_gas": 650, "water": 120}, {"electricity": 1}, {"operator": 6, "engineer": 1}, margin=0.12)
    R("burn_oil", "Oil power", "power", "oil_power_plant", 1, {"diesel": 420, "water": 100}, {"electricity": 1}, {"operator": 5}, margin=0.10)
    R("fission", "Nuclear power", "power", "nuclear_plant", 1, {"enriched_uranium": 1.2, "water": 400}, {"electricity": 1}, {"operator": 30, "engineer": 8}, margin=0.18, tech="nuclear_power")
    R("enrich_uranium", "Enrich uranium", "power", "chemical_plant", 12, {"uranium_ore": 400, "industrial_gases": 100}, {"enriched_uranium": 1}, {"operator": 6, "engineer": 3}, energy=500, tech="nuclear_power")
    R("burn_biomass", "Biomass power", "power", "biomass_plant", 1, {"biomass": 900, "water": 120}, {"electricity": 1}, {"operator": 6}, margin=0.10)
    R("wind_power", "Wind power", "renewables", "wind_farm", 1, {"spare_parts": 4}, {"electricity": 1}, {"technician": 4}, margin=0.30)
    R("solar_power", "Solar power", "renewables", "solar_farm", 1, {"spare_parts": 3}, {"electricity": 1}, {"technician": 4}, margin=0.30)
    R("hydro_power", "Hydroelectric power", "renewables", "hydro_plant", 1, {"spare_parts": 5}, {"electricity": 1}, {"operator": 10, "engineer": 3}, margin=0.30)
    R("geothermal_power", "Geothermal power", "renewables", "geothermal_plant", 1, {"spare_parts": 5, "water": 300}, {"electricity": 1}, {"operator": 10, "engineer": 2}, margin=0.25)
    R("grow_biomass", "Cultivate biomass", "renewables", "biomass_plant", 30, {"fertilizer": 30, "water": 400}, {"biomass": 1}, {"farmhand": 3})
    R("make_biodiesel", "Make biodiesel", "renewables", "biofuel_plant", 3, {"vegetable_oil": 120, "ethanol": 20}, {"biodiesel": 1}, {"operator": 3, "technician": 1}, energy=60)
    R("ferment_ethanol", "Ferment ethanol", "renewables", "biofuel_plant", 5, {"corn": 400, "water": 300}, {"ethanol": 1}, {"operator": 3}, energy=90)
    R("make_glycerin", "Refine glycerin", "chemicals", "chemical_plant", 3, {"vegetable_oil": 100, "caustic_soda": 15}, {"glycerin": 1}, {"operator": 2}, energy=50)
    R("make_solar_cells", "Make solar cells", "renewables", "battery_plant", 4, {"silica_sand": 200, "industrial_gases": 30, "copper_wire": 20}, {"solar_cells": 1}, {"operator": 5, "engineer": 1}, energy=260)
    R("make_battery_cells", "Make battery cells", "renewables", "battery_plant", 4, {"lithium_carbonate": 30, "nickel_ingot": 25, "copper_wire": 20, "aluminum_sheet": 25}, {"lithium_battery_cells": 1}, {"operator": 5, "engineer": 2}, energy=280, tech="battery_manufacturing")

    s.tech("nuclear_power", "Nuclear power", "ENERGY", 4, 3_000_000, 300, requires=["steam_turbines"], facilities=["nuclear_plant"], recipes=["fission", "enrich_uranium"])
    s.tech("steam_turbines", "High-efficiency turbines", "ENERGY", 2, 500_000, 90, effects=[("ENERGY_SAVINGS", 0.04, "power", None), ("YIELD_BONUS", 0.05, "power", None)])
    s.tech("battery_manufacturing", "Battery manufacturing", "ENERGY", 3, 1_800_000, 180, requires=["steam_turbines"], facilities=["battery_plant"], recipes=["make_battery_cells"])
    s.tech("catalytic_cracking", "Catalytic cracking", "MATERIALS", 2, 600_000, 100, effects=[("YIELD_BONUS", 0.06, "oil_gas", None)])
    s.tech("green_chemistry", "Green chemistry", "MATERIALS", 3, 900_000, 120, requires=["catalytic_cracking"], effects=[("INPUT_SAVINGS", 0.05, "chemicals", None)])
    s.tech("smart_grid", "Smart grid", "DIGITAL", 3, 1_100_000, 140, requires=["steam_turbines"], effects=[("YIELD_BONUS", 0.04, "renewables", None)])
    return s

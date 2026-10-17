"""Chemicals: sulfur and potash deposits, inorganic and organic intermediates, engineering polymers, coatings."""
from expgen import ExpSpec


def build():
    s = ExpSpec("chemicals", "ChemicalsPack")
    s.role("chemist", "Process chemist", "SPECIALIST", 140.0, 120)

    s.industry("inorganic_chemicals", "Inorganic chemicals", "CHEMICALS", "Sulfur, potash, soda ash, acids and oxidisers")
    s.industry("organic_chemicals", "Organic chemicals", "CHEMICALS", "Methanol chain, phenol, styrene and acrylics")
    s.industry("engineering_polymers", "Engineering polymers", "CHEMICALS", "Polystyrene, PET, nylon, polycarbonate and silicone")
    s.industry("coatings_and_sealants", "Coatings and sealants", "CHEMICALS", "Coatings, sealants, treatment chemicals and consumer formulations")

    s.add_goods("RAW", [("sulfur_ore", "Sulfur ore", 22), ("potash_ore", "Potash ore", 28)], "STABLE")
    s.add_goods("INTERMEDIATE", [
        ("sulfur", "Sulfur", 34), ("potash", "Potash", 36), ("soda_ash", "Soda ash", 30), ("hydrochloric_acid", "Hydrochloric acid", 22),
        ("hydrogen_peroxide", "Hydrogen peroxide", 58), ("sodium_hypochlorite", "Sodium hypochlorite", 18), ("zinc_oxide", "Zinc oxide", 70),
    ], "STABLE")
    s.add_goods("INTERMEDIATE", [
        ("methanol", "Methanol", 31), ("formaldehyde", "Formaldehyde", 44), ("acetic_acid", "Acetic acid", 40), ("urea", "Urea", 29),
        ("phenol", "Phenol", 82), ("styrene", "Styrene", 64), ("acrylic_acid", "Acrylic acid", 77), ("uf_resin", "Urea-formaldehyde resin", 45),
        ("polystyrene", "Polystyrene resin", 66), ("pet_resin", "PET resin", 58), ("nylon_resin", "Nylon resin", 96),
        ("polycarbonate", "Polycarbonate resin", 120), ("silicone", "Silicone polymer", 210), ("acrylic_resin", "Acrylic resin", 74),
    ])
    s.add_goods("INDUSTRIAL", [
        ("industrial_coating", "Industrial coating", 62), ("silicone_sealant", "Silicone sealant", 55), ("water_treatment_chem", "Water treatment chemicals", 40),
        ("fire_retardant", "Fire retardant", 75), ("herbicide", "Herbicide", 70), ("refrigerant_gas", "Refrigerant gas", 95),
        ("urea_fertilizer", "Urea fertilizer", 27), ("potash_fertilizer", "Potash fertilizer", 33),
    ], "STABLE")
    s.add_goods("CONSUMER", [("household_bleach", "Household bleach", 4), ("antifreeze", "Antifreeze", 12), ("pool_chlorine_tablets", "Pool chlorine tablets", 18)], "STABLE")

    # Extraction and processing facilities
    s.fac("sulfur_mine", "Sulfur mine", "MINE", "CHEMICALS", 1_400_000, 70, 3, 520, 30.0, {"miner": 2}, 480, extracts="sulfur_ore", storage=60_000)
    s.fac("potash_mine", "Potash mine", "MINE", "CHEMICALS", 1_600_000, 80, 3, 560, 32.0, {"miner": 2}, 520, extracts="potash_ore", storage=60_000)
    s.fac("inorganic_plant", "Inorganic chemicals plant", "FACTORY", "CHEMICALS", 5_400_000, 100, 3, 1_700, 200.0, {"operator": 3, "chemist": 1}, 650)
    s.fac("organic_synthesis_plant", "Organic synthesis plant", "FACTORY", "CHEMICALS", 9_200_000, 130, 3, 2_600, 280.0, {"operator": 4, "chemist": 2}, 750)
    s.fac("polymer_plant", "Polymer plant", "FACTORY", "CHEMICALS", 12_000_000, 150, 3, 3_300, 340.0, {"operator": 5, "chemist": 2}, 800)
    s.fac("formulation_works", "Formulation works", "FACTORY", "CHEMICALS", 4_800_000, 90, 4, 1_500, 140.0, {"operator": 4, "chemist": 1}, 550)

    s.machine("catalytic_reactor", "Catalytic reactor", 900_000, thr=1.2, wear=550, maint=80.0)
    s.machine("extrusion_line", "Polymer extrusion line", 650_000, thr=1.25, wear=450, maint=55.0, labor_sav=0.08)
    s.machine("spray_dryer", "Spray dryer", 420_000, thr=1.1, wear=400, maint=35.0)

    # Mining
    s.rec("mine_sulfur", "Mine sulfur ore", "inorganic_chemicals", "sulfur_mine", 8, {"diesel": 85, "explosives": 12, "water": 90}, {"sulfur_ore": 1}, {"miner": 7}, energy=55, margin=0.2)
    s.rec("mine_potash", "Mine potash ore", "inorganic_chemicals", "potash_mine", 9, {"diesel": 90, "explosives": 14, "water": 100}, {"potash_ore": 1}, {"miner": 7, "operator": 1}, energy=60, margin=0.2)
    # Inorganic chemistry
    R = s.rec
    R("refine_sulfur", "Refine sulfur", "inorganic_chemicals", "inorganic_plant", 3, {"sulfur_ore": 120, "natural_gas": 14, "water": 60}, {"sulfur": 1}, {"operator": 3}, energy=70, margin=0.2)
    R("process_potash", "Process potash", "inorganic_chemicals", "inorganic_plant", 3, {"potash_ore": 130, "water": 120}, {"potash": 1}, {"operator": 3}, energy=60, margin=0.2)
    R("make_soda_ash", "Make soda ash", "inorganic_chemicals", "inorganic_plant", 4, {"salt": 90, "limestone": 70, "ammonia": 12}, {"soda_ash": 1}, {"operator": 3, "chemist": 1}, energy=90, margin=0.2)
    R("make_hydrochloric_acid", "Make hydrochloric acid", "inorganic_chemicals", "inorganic_plant", 2, {"chlorine": 40, "hydrogen": 12, "water": 60}, {"hydrochloric_acid": 1}, {"operator": 3}, energy=50, margin=0.22)
    R("make_hydrogen_peroxide", "Make hydrogen peroxide", "inorganic_chemicals", "inorganic_plant", 4, {"hydrogen": 30, "industrial_gases": 40, "solvents": 8, "water": 40}, {"hydrogen_peroxide": 1}, {"operator": 3, "chemist": 1}, energy=110, margin=0.25)
    R("make_hypochlorite", "Make sodium hypochlorite", "inorganic_chemicals", "inorganic_plant", 2, {"caustic_soda": 30, "chlorine": 28, "water": 80}, {"sodium_hypochlorite": 1}, {"operator": 3}, energy=40, margin=0.2)
    R("make_zinc_oxide", "Burn zinc oxide", "inorganic_chemicals", "inorganic_plant", 3, {"zinc_ingot": 30, "industrial_gases": 20}, {"zinc_oxide": 1}, {"operator": 3}, energy=100, margin=0.22)
    R("contact_sulfuric_acid", "Make sulfuric acid from sulfur", "inorganic_chemicals", "inorganic_plant", 3, {"sulfur": 40, "industrial_gases": 30, "water": 60}, {"sulfuric_acid": 1}, {"operator": 3}, energy=60, margin=0.18)
    # Organic chemistry
    R("make_methanol", "Make methanol", "organic_chemicals", "organic_synthesis_plant", 4, {"natural_gas": 90, "water": 60, "industrial_gases": 10}, {"methanol": 1}, {"operator": 4, "chemist": 1}, energy=160, margin=0.2, machines=["catalytic_reactor"])
    R("make_formaldehyde", "Make formaldehyde", "organic_chemicals", "organic_synthesis_plant", 3, {"methanol": 60, "industrial_gases": 25}, {"formaldehyde": 1}, {"operator": 4, "chemist": 1}, energy=90, margin=0.22)
    R("make_acetic_acid", "Make acetic acid", "organic_chemicals", "organic_synthesis_plant", 4, {"methanol": 60, "industrial_gases": 35}, {"acetic_acid": 1}, {"operator": 4, "chemist": 1}, energy=110, margin=0.22)
    R("make_urea", "Synthesize urea", "organic_chemicals", "organic_synthesis_plant", 3, {"ammonia": 60, "industrial_gases": 40}, {"urea": 1}, {"operator": 4}, energy=130, margin=0.2)
    R("make_phenol", "Make phenol", "organic_chemicals", "organic_synthesis_plant", 5, {"naphtha": 70, "propane": 30, "sulfuric_acid": 6}, {"phenol": 1}, {"operator": 4, "chemist": 2}, energy=180, margin=0.24, waste={"chemical_waste": 12}, machines=["catalytic_reactor"])
    R("make_styrene", "Make styrene", "organic_chemicals", "organic_synthesis_plant", 4, {"naphtha": 80, "industrial_gases": 20}, {"styrene": 1}, {"operator": 4, "chemist": 1}, energy=150, margin=0.22, waste={"chemical_waste": 8})
    R("make_acrylic_acid", "Make acrylic acid", "organic_chemicals", "organic_synthesis_plant", 4, {"propane": 70, "industrial_gases": 30, "sulfuric_acid": 5}, {"acrylic_acid": 1}, {"operator": 4, "chemist": 1}, energy=140, margin=0.24, waste={"chemical_waste": 10})
    R("make_uf_resin", "Cook urea-formaldehyde resin", "organic_chemicals", "organic_synthesis_plant", 3, {"urea": 40, "formaldehyde": 36}, {"uf_resin": 1}, {"operator": 4}, energy=70, margin=0.2)
    # Polymers
    R("polymerize_styrene", "Polymerize polystyrene", "engineering_polymers", "polymer_plant", 3, {"styrene": 90, "pigments": 2}, {"polystyrene": 1}, {"operator": 5}, energy=130, margin=0.22)
    R("make_pet", "Make PET resin", "engineering_polymers", "polymer_plant", 4, {"naphtha": 60, "acetic_acid": 30, "ethanol": 20}, {"pet_resin": 1}, {"operator": 5, "chemist": 1}, energy=150, margin=0.22, machines=["extrusion_line"])
    R("make_nylon", "Make nylon resin", "engineering_polymers", "polymer_plant", 5, {"phenol": 40, "ammonia": 25, "naphtha": 20}, {"nylon_resin": 1}, {"operator": 5, "chemist": 2}, energy=200, margin=0.24, machines=["extrusion_line"])
    R("make_polycarbonate", "Make polycarbonate", "engineering_polymers", "polymer_plant", 5, {"phenol": 50, "methanol": 25, "chlorine": 10}, {"polycarbonate": 1}, {"operator": 5, "chemist": 2}, energy=210, margin=0.26)
    R("make_silicone", "Make silicone polymer", "engineering_polymers", "polymer_plant", 6, {"silica_sand": 80, "methanol": 30, "coal": 25, "hydrochloric_acid": 6}, {"silicone": 1}, {"operator": 5, "chemist": 2}, energy=260, margin=0.28, waste={"chemical_waste": 10})
    R("make_acrylic_resin", "Make acrylic resin", "engineering_polymers", "polymer_plant", 3, {"acrylic_acid": 50, "methanol": 24, "solvents": 8}, {"acrylic_resin": 1}, {"operator": 5}, energy=120, margin=0.22)
    # Formulation
    R("blend_industrial_coating", "Blend industrial coating", "coatings_and_sealants", "formulation_works", 2, {"acrylic_resin": 20, "zinc_oxide": 8, "pigments": 6, "solvents": 12}, {"industrial_coating": 1}, {"operator": 4}, energy=40, margin=0.24, machines=["spray_dryer"])
    R("compound_sealant", "Compound silicone sealant", "coatings_and_sealants", "formulation_works", 2, {"silicone": 18, "pigments": 2, "plastic_granules": 6}, {"silicone_sealant": 1}, {"operator": 4}, energy=35, margin=0.22)
    R("blend_water_treatment", "Blend water treatment chemicals", "coatings_and_sealants", "formulation_works", 2, {"soda_ash": 10, "hydrochloric_acid": 10, "sodium_hypochlorite": 10, "hydrogen_peroxide": 4}, {"water_treatment_chem": 1}, {"operator": 4}, energy=25, margin=0.2)
    R("mix_fire_retardant", "Mix fire retardant", "coatings_and_sealants", "formulation_works", 2, {"phosphate_rock": 24, "ammonia": 8, "uf_resin": 6, "water": 20}, {"fire_retardant": 1}, {"operator": 4, "chemist": 1}, energy=30, margin=0.26)
    R("formulate_herbicide", "Formulate herbicide", "coatings_and_sealants", "formulation_works", 3, {"urea": 12, "solvents": 8, "sulfur": 6, "water": 20}, {"herbicide": 1}, {"operator": 4, "chemist": 1}, energy=40, margin=0.26)
    R("fill_refrigerant", "Fill refrigerant gas", "coatings_and_sealants", "formulation_works", 2, {"hydrochloric_acid": 12, "industrial_gases": 30, "solvents": 6}, {"refrigerant_gas": 1}, {"operator": 4}, energy=60, margin=0.24)
    R("prill_urea_fertilizer", "Prill urea fertilizer", "coatings_and_sealants", "formulation_works", 2, {"urea": 24, "limestone": 6, "packaging": 2}, {"urea_fertilizer": 1}, {"operator": 4}, energy=30, margin=0.18)
    R("granulate_potash_fertilizer", "Granulate potash fertilizer", "coatings_and_sealants", "formulation_works", 2, {"potash": 26, "phosphate_rock": 6, "packaging": 2}, {"potash_fertilizer": 1}, {"operator": 4}, energy=30, margin=0.18)
    R("pack_household_bleach", "Package household bleach", "coatings_and_sealants", "formulation_works", 2, {"sodium_hypochlorite": 6, "plastic_granules": 4, "packaging": 4}, {"household_bleach": 1}, {"operator": 4}, energy=18, margin=0.25)
    R("pack_antifreeze", "Package antifreeze", "coatings_and_sealants", "formulation_works", 2, {"methanol": 6, "water": 8, "plastic_granules": 3, "packaging": 3}, {"antifreeze": 1}, {"operator": 4}, energy=18, margin=0.25)
    R("press_pool_tablets", "Press pool chlorine tablets", "coatings_and_sealants", "formulation_works", 2, {"sodium_hypochlorite": 12, "soda_ash": 4, "packaging": 4}, {"pool_chlorine_tablets": 1}, {"operator": 4}, energy=30, margin=0.3)

    # Technology: ties into catalytic_cracking, green_chemistry and deep_mining of the base content
    s.tech("sulfur_recovery", "Sulfur recovery", "MATERIALS", 2, 520_000, 80, requires=["deep_mining"], effects=[("YIELD_BONUS", 0.06, "inorganic_chemicals", None)])
    s.tech("organic_synthesis", "Organic synthesis", "MATERIALS", 3, 2_100_000, 160, requires=["catalytic_cracking"],
           facilities=["organic_synthesis_plant"], machines=["catalytic_reactor"])
    s.tech("polymer_engineering", "Polymer engineering", "MATERIALS", 3, 3_400_000, 200, requires=["organic_synthesis"],
           facilities=["polymer_plant"], recipes=["make_silicone", "make_polycarbonate"], machines=["extrusion_line"])
    s.tech("specialty_formulation", "Specialty formulation", "PRODUCTION", 2, 900_000, 110, requires=["catalytic_cracking"],
           machines=["spray_dryer"], effects=[("INPUT_SAVINGS", 0.05, "coatings_and_sealants", None)])
    s.tech("process_intensification", "Process intensification", "PRODUCTION", 3, 1_900_000, 150, requires=["organic_synthesis", "green_chemistry"],
           effects=[("ENERGY_SAVINGS", 0.08, "organic_chemicals", None), ("YIELD_BONUS", 0.04, "inorganic_chemicals", None)])
    s.tech("chemical_safety_systems", "Chemical safety systems", "MANAGEMENT", 2, 600_000, 90,
           effects=[("QUALITY_BONUS", 0.15, "organic_chemicals", None), ("QUALITY_BONUS", 0.10, "engineering_polymers", None)])

    D = s.dem
    D("household_bleach", 380, 400, 410, 0.4, 0.2)
    D("antifreeze", 30, 60, 80, 0.5, 0.5, [], {"WINTER": 2.0, "SUMMER": 0.3})
    D("pool_chlorine_tablets", 5, 25, 60, 0.8, 1.2, [], {"SUMMER": 2.2, "WINTER": 0.2}, 0.55)
    D("industrial_coating", 20, 28, 30, 0.6, 0.5)
    D("silicone_sealant", 18, 24, 26, 0.7, 0.6)
    D("water_treatment_chem", 40, 44, 46, 0.3, 0.1)
    D("fire_retardant", 8, 12, 14, 0.5, 0.3)
    D("herbicide", 14, 18, 20, 0.6, 0.4, [], {"SPRING": 1.8, "WINTER": 0.2})
    D("refrigerant_gas", 6, 10, 12, 0.5, 0.4, [], {"SUMMER": 1.6})
    D("urea_fertilizer", 30, 34, 36, 0.7, 0.3, ["fertilizer"], {"SPRING": 2.0, "WINTER": 0.2})
    D("potash_fertilizer", 22, 26, 28, 0.7, 0.3, ["fertilizer"], {"SPRING": 2.0, "WINTER": 0.2})
    return s

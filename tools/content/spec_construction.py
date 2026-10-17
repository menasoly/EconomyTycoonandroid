"""Construction materials: gypsum, interior finishes, building envelope, engineered timber, precast and building systems."""
from expgen import ExpSpec


def build():
    s = ExpSpec("construction_materials", "ConstructionMaterialsPack")
    s.role("mason", "Mason", "SKILLED", 82.0, 30)

    s.industry("interior_finishes", "Interior finishes", "CONSTRUCTION", "Plaster, drywall, ceramic tiles, bathtubs and flooring")
    s.industry("building_envelope", "Building envelope", "CONSTRUCTION", "Insulation, membranes, cladding and curtain walls")
    s.industry("engineered_wood", "Engineered timber", "CONSTRUCTION", "Boards, cross-laminated timber, glulam, flooring and cabinets")
    s.industry("structural_precast", "Precast and building systems", "CONSTRUCTION", "Precast concrete, steel decking, elevators and scaffolding")

    s.add_goods("RAW", [("gypsum", "Gypsum", 9)], "STABLE")
    s.add_goods("INTERMEDIATE", [
        ("plaster", "Plaster", 20), ("drywall_board", "Drywall board", 12), ("ceramic_tiles", "Ceramic tiles", 14), ("glazed_tiles", "Glazed tiles", 24),
        ("mineral_wool", "Mineral wool", 28), ("eps_board", "Expanded polystyrene board", 30), ("waterproof_membrane", "Waterproof membrane", 48),
        ("cladding_panels", "Cladding panels", 85), ("osb_board", "OSB board", 22), ("particleboard", "Particleboard", 17), ("clt_panels", "Cross-laminated timber", 360),
        ("glulam_beams", "Glulam beams", 210), ("precast_pipes", "Precast pipes", 95), ("precast_slabs", "Precast slabs", 130), ("steel_decking", "Steel decking", 110),
        ("curtain_wall", "Curtain wall units", 780),
    ], "STABLE")
    s.add_goods("INDUSTRIAL", [
        ("elevators", "Elevators", 48_000), ("scaffolding", "Scaffolding sets", 2_400), ("drainage_system", "Drainage system", 4_500), ("road_barriers", "Road barriers", 140),
    ], "STABLE")
    s.add_goods("CONSUMER", [
        ("laminate_flooring", "Laminate flooring", 18), ("hardwood_flooring", "Hardwood flooring", 38), ("kitchen_cabinets", "Kitchen cabinets", 420),
        ("bathtub", "Bathtub", 260), ("garden_pavers", "Garden pavers", 9),
    ], "STABLE")

    s.fac("gypsum_quarry", "Gypsum quarry", "MINE", "CONSTRUCTION", 800_000, 55, 3, 330, 20.0, {"miner": 1}, 420, extracts="gypsum", storage=80_000)
    s.fac("gypsum_works", "Gypsum works", "FACTORY", "CONSTRUCTION", 3_400_000, 80, 3, 1_150, 120.0, {"operator": 3, "mason": 1}, 520)
    s.fac("tile_works", "Tile works", "FACTORY", "CONSTRUCTION", 3_100_000, 75, 3, 1_050, 130.0, {"operator": 3, "mason": 1}, 560)
    s.fac("insulation_works", "Insulation and envelope works", "FACTORY", "CONSTRUCTION", 4_200_000, 90, 3, 1_300, 160.0, {"operator": 4}, 600)
    s.fac("timber_engineering_plant", "Timber engineering plant", "FACTORY", "CONSTRUCTION", 5_600_000, 100, 3, 1_700, 170.0, {"operator": 4, "machinist": 2}, 640)
    s.fac("precast_plant", "Precast plant", "FACTORY", "CONSTRUCTION", 6_000_000, 100, 3, 1_800, 190.0, {"operator": 4, "mason": 2}, 700)
    s.fac("building_systems_works", "Building systems works", "FACTORY", "CONSTRUCTION", 9_500_000, 130, 3, 2_500, 220.0, {"machinist": 4, "engineer": 2}, 850)

    s.machine("tile_kiln_line", "Tile kiln line", 700_000, thr=1.2, wear=450, maint=60.0)
    s.machine("panel_press", "Continuous panel press", 1_100_000, thr=1.2, wear=500, maint=90.0)
    s.machine("precast_mold_line", "Precast mold line", 900_000, thr=1.25, wear=480, maint=75.0, labor_sav=0.06)

    R = s.rec
    R("quarry_gypsum", "Quarry gypsum", "interior_finishes", "gypsum_quarry", 7, {"diesel": 70, "explosives": 10, "water": 60}, {"gypsum": 1}, {"miner": 5}, energy=40, margin=0.2)
    R("calcine_plaster", "Calcine plaster", "interior_finishes", "gypsum_works", 3, {"gypsum": 160, "natural_gas": 20, "water": 40}, {"plaster": 1}, {"operator": 3, "mason": 1}, energy=90, margin=0.2)
    R("press_drywall", "Press drywall board", "interior_finishes", "gypsum_works", 3, {"plaster": 60, "wood_pulp": 10, "water": 30}, {"drywall_board": 1}, {"operator": 3, "mason": 1}, energy=80, margin=0.2)
    R("fire_ceramic_tiles", "Fire ceramic tiles", "interior_finishes", "tile_works", 3, {"clay": 180, "silica_sand": 40, "natural_gas": 24}, {"ceramic_tiles": 1}, {"operator": 3, "mason": 1}, energy=100, margin=0.2, machines=["tile_kiln_line"])
    R("glaze_tiles", "Glaze tiles", "interior_finishes", "tile_works", 3, {"ceramic_tiles": 60, "pigments": 4, "silica_sand": 12, "natural_gas": 10}, {"glazed_tiles": 1}, {"operator": 3}, energy=80, margin=0.22)
    R("press_garden_pavers", "Press garden pavers", "interior_finishes", "tile_works", 2, {"ceramic_tiles": 30, "gravel": 60, "cement": 20}, {"garden_pavers": 1}, {"operator": 3}, energy=40, margin=0.24)
    R("enamel_bathtubs", "Enamel bathtubs", "interior_finishes", "tile_works", 4, {"steel_sheet": 14, "pigments": 2, "silica_sand": 6, "natural_gas": 6}, {"bathtub": 1}, {"operator": 3, "mason": 1}, energy=60, margin=0.26)
    R("spin_mineral_wool", "Spin mineral wool", "building_envelope", "insulation_works", 3, {"silica_sand": 80, "limestone": 50, "coal": 20}, {"mineral_wool": 1}, {"operator": 4}, energy=110, margin=0.2)
    R("expand_eps_board", "Expand polystyrene board", "building_envelope", "insulation_works", 2, {"polystyrene": 30, "industrial_gases": 10}, {"eps_board": 1}, {"operator": 4}, energy=60, margin=0.22)
    R("roll_waterproof_membrane", "Roll waterproof membrane", "building_envelope", "insulation_works", 2, {"bitumen": 40, "polypropylene": 16}, {"waterproof_membrane": 1}, {"operator": 4}, energy=60, margin=0.22)
    R("make_cladding_panels", "Make cladding panels", "building_envelope", "insulation_works", 3, {"aluminum_sheet": 14, "mineral_wool": 20, "silicone_sealant": 3}, {"cladding_panels": 1}, {"operator": 4}, energy=60, margin=0.24)
    R("press_osb", "Press OSB board", "engineered_wood", "timber_engineering_plant", 3, {"logs": 160, "uf_resin": 18, "water": 30}, {"osb_board": 1}, {"operator": 4, "machinist": 1}, energy=110, margin=0.2, machines=["panel_press"])
    R("press_particleboard", "Press particleboard", "engineered_wood", "timber_engineering_plant", 3, {"logs": 120, "uf_resin": 14, "water": 20}, {"particleboard": 1}, {"operator": 4, "machinist": 1}, energy=100, margin=0.2, machines=["panel_press"])
    R("laminate_clt", "Laminate cross-laminated timber", "engineered_wood", "timber_engineering_plant", 4, {"lumber": 60, "adhesives": 8, "fire_retardant": 4}, {"clt_panels": 1}, {"operator": 4, "machinist": 2}, energy=90, margin=0.24)
    R("glue_glulam", "Glue glulam beams", "engineered_wood", "timber_engineering_plant", 3, {"lumber": 45, "uf_resin": 8}, {"glulam_beams": 1}, {"operator": 4, "machinist": 2}, energy=70, margin=0.22)
    R("press_laminate_flooring", "Press laminate flooring", "engineered_wood", "timber_engineering_plant", 2, {"particleboard": 30, "paper_goods": 10, "adhesives": 4}, {"laminate_flooring": 1}, {"operator": 4}, energy=60, margin=0.24)
    R("mill_hardwood_flooring", "Mill hardwood flooring", "engineered_wood", "timber_engineering_plant", 3, {"lumber": 40, "paint": 2}, {"hardwood_flooring": 1}, {"operator": 4, "machinist": 1}, energy=60, margin=0.24)
    R("build_kitchen_cabinets", "Build kitchen cabinets", "engineered_wood", "timber_engineering_plant", 4, {"particleboard": 20, "plywood": 14, "fasteners": 6, "paint": 3, "packaging": 8}, {"kitchen_cabinets": 1}, {"operator": 4, "machinist": 2}, energy=60, margin=0.26)
    R("cast_precast_pipes", "Cast precast pipes", "structural_precast", "precast_plant", 3, {"concrete": 90, "rebar": 12}, {"precast_pipes": 1}, {"operator": 4, "mason": 2}, energy=60, margin=0.2, machines=["precast_mold_line"])
    R("cast_precast_slabs", "Cast precast slabs", "structural_precast", "precast_plant", 3, {"concrete": 100, "rebar": 18}, {"precast_slabs": 1}, {"operator": 4, "mason": 2}, energy=60, margin=0.2, machines=["precast_mold_line"])
    R("roll_steel_decking", "Roll steel decking", "structural_precast", "precast_plant", 2, {"steel_sheet": 40, "lubricants": 1}, {"steel_decking": 1}, {"operator": 4, "mason": 1}, energy=70, margin=0.2)
    R("cast_road_barriers", "Cast road barriers", "structural_precast", "precast_plant", 2, {"concrete": 60, "rebar": 8}, {"road_barriers": 1}, {"operator": 4, "mason": 1}, energy=40, margin=0.22)
    R("assemble_drainage_systems", "Assemble drainage systems", "structural_precast", "precast_plant", 6, {"precast_pipes": 30, "geotextile": 6, "gravel": 120}, {"drainage_system": 1}, {"operator": 4, "mason": 2}, energy=60, margin=0.24)
    R("assemble_curtain_wall", "Assemble curtain wall units", "building_envelope", "building_systems_works", 4, {"glass": 24, "aluminum_sheet": 12, "silicone_sealant": 4}, {"curtain_wall": 1}, {"machinist": 4, "engineer": 1}, energy=100, margin=0.26)
    R("assemble_elevators", "Assemble elevators", "structural_precast", "building_systems_works", 20, {"electric_motor": 8, "controller_unit": 6, "steel_beams": 40, "steel_wire": 20, "gearbox": 2}, {"elevators": 1}, {"machinist": 6, "engineer": 2}, energy=300, margin=0.26)
    R("fabricate_scaffolding", "Fabricate scaffolding sets", "structural_precast", "building_systems_works", 6, {"steel_pipe": 24, "fasteners": 12, "steel_sheet": 6}, {"scaffolding": 1}, {"machinist": 4, "engineer": 1}, energy=90, margin=0.22)

    s.tech("gypsum_processing", "Gypsum processing", "MATERIALS", 1, 350_000, 70, requires=["deep_mining"],
           facilities=["gypsum_works"], effects=[("YIELD_BONUS", 0.06, "interior_finishes", None)])
    s.tech("kiln_technology", "Kiln technology", "PRODUCTION", 1, 420_000, 70, machines=["tile_kiln_line"],
           effects=[("ENERGY_SAVINGS", 0.05, "interior_finishes", None)])
    s.tech("thermal_envelope", "Thermal envelope design", "MATERIALS", 2, 1_000_000, 110, requires=["catalytic_cracking"],
           facilities=["insulation_works"], effects=[("ENERGY_SAVINGS", 0.06, "building_envelope", None)])
    s.tech("engineered_timber", "Engineered timber", "MATERIALS", 2, 1_100_000, 110, requires=["prefab_construction"],
           facilities=["timber_engineering_plant"], machines=["panel_press"], effects=[("YIELD_BONUS", 0.05, "engineered_wood", None)])
    s.tech("precast_engineering", "Precast engineering", "PRODUCTION", 2, 1_400_000, 120, requires=["prefab_construction"],
           facilities=["precast_plant"], machines=["precast_mold_line"])
    s.tech("building_systems", "Building systems integration", "PRODUCTION", 3, 3_200_000, 180, requires=["precast_engineering", "factory_automation"],
           facilities=["building_systems_works"], recipes=["assemble_elevators"], effects=[("THROUGHPUT_BONUS", 0.06, "structural_precast", None)])
    s.tech("green_building_codes", "Green building codes", "MANAGEMENT", 2, 800_000, 100, requires=["thermal_envelope"],
           effects=[("INPUT_SAVINGS", 0.04, "building_envelope", None), ("INPUT_SAVINGS", 0.03, "engineered_wood", None)])
    s.tech("site_logistics", "Modular site logistics", "LOGISTICS", 2, 900_000, 100, requires=["route_optimization"],
           effects=[("TRANSPORT_SAVINGS", 0.05, None, None)])

    D = s.dem
    D("laminate_flooring", 60, 90, 100, 0.8, 0.8)
    D("hardwood_flooring", 10, 30, 70, 1.0, 1.3)
    D("kitchen_cabinets", 1, 3, 6, 1.0, 1.3, [], {}, 0.4)
    D("bathtub", 1, 2, 4, 1.0, 1.2, [], {}, 0.5)
    D("garden_pavers", 20, 50, 80, 1.0, 1.2, [], {"SPRING": 1.8, "SUMMER": 1.5, "WINTER": 0.2})
    D("drywall_board", 60, 80, 90, 0.6, 0.6, [], {"WINTER": 0.7, "SUMMER": 1.2})
    D("glazed_tiles", 30, 50, 70, 0.8, 0.9)
    D("mineral_wool", 20, 30, 34, 0.6, 0.5)
    D("eps_board", 20, 28, 30, 0.6, 0.5)
    D("waterproof_membrane", 10, 14, 16, 0.5, 0.4)
    D("cladding_panels", 6, 10, 12, 0.7, 0.8)
    D("clt_panels", 1, 2, 3, 0.8, 0.8, [], {}, 0.5)
    D("glulam_beams", 1, 2, 3, 0.8, 0.8, [], {}, 0.5)
    D("osb_board", 30, 40, 44, 0.6, 0.5)
    D("precast_slabs", 4, 6, 8, 0.6, 0.5)
    D("steel_decking", 4, 6, 8, 0.6, 0.5)
    D("curtain_wall", 1, 2, 3, 0.8, 0.9, [], {}, 0.5)
    D("elevators", 0, 1, 1, 0.5, 0.6, [], {}, 0.97)
    D("scaffolding", 0, 1, 1, 0.5, 0.5, [], {}, 0.9)
    D("drainage_system", 0, 1, 1, 0.5, 0.5, [], {}, 0.9)
    D("road_barriers", 4, 6, 6, 0.5, 0.4)
    return s

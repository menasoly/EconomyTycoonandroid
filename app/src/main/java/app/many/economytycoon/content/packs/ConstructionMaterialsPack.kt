package app.many.economytycoon.content.packs

import app.many.economytycoon.content.ContentPack
import app.many.economytycoon.content.EffectKind
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.content.VolatilityClass
import app.many.economytycoon.content.contentPack

object ConstructionMaterialsPack {
    val pack: ContentPack = contentPack("construction_materials", 1, 0) {
        industry("interior_finishes", "Interior finishes", IndustryFamily.CONSTRUCTION, "Plaster, drywall, ceramic tiles, bathtubs and flooring")
        industry("building_envelope", "Building envelope", IndustryFamily.CONSTRUCTION, "Insulation, membranes, cladding and curtain walls")
        industry("engineered_wood", "Engineered timber", IndustryFamily.CONSTRUCTION, "Boards, cross-laminated timber, glulam, flooring and cabinets")
        industry("structural_precast", "Precast and building systems", IndustryFamily.CONSTRUCTION, "Precast concrete, steel decking, elevators and scaffolding")

        good("gypsum", "Gypsum", GoodCategory.RAW, 9.0, VolatilityClass.STABLE)
        good("plaster", "Plaster", GoodCategory.INTERMEDIATE, 20.0, VolatilityClass.STABLE)
        good("drywall_board", "Drywall board", GoodCategory.INTERMEDIATE, 12.0, VolatilityClass.STABLE)
        good("ceramic_tiles", "Ceramic tiles", GoodCategory.INTERMEDIATE, 14.0, VolatilityClass.STABLE)
        good("glazed_tiles", "Glazed tiles", GoodCategory.INTERMEDIATE, 24.0, VolatilityClass.STABLE)
        good("mineral_wool", "Mineral wool", GoodCategory.INTERMEDIATE, 28.0, VolatilityClass.STABLE)
        good("eps_board", "Expanded polystyrene board", GoodCategory.INTERMEDIATE, 30.0, VolatilityClass.STABLE)
        good("waterproof_membrane", "Waterproof membrane", GoodCategory.INTERMEDIATE, 48.0, VolatilityClass.STABLE)
        good("cladding_panels", "Cladding panels", GoodCategory.INTERMEDIATE, 85.0, VolatilityClass.STABLE)
        good("osb_board", "OSB board", GoodCategory.INTERMEDIATE, 22.0, VolatilityClass.STABLE)
        good("particleboard", "Particleboard", GoodCategory.INTERMEDIATE, 17.0, VolatilityClass.STABLE)
        good("clt_panels", "Cross-laminated timber", GoodCategory.INTERMEDIATE, 360.0, VolatilityClass.STABLE)
        good("glulam_beams", "Glulam beams", GoodCategory.INTERMEDIATE, 210.0, VolatilityClass.STABLE)
        good("precast_pipes", "Precast pipes", GoodCategory.INTERMEDIATE, 95.0, VolatilityClass.STABLE)
        good("precast_slabs", "Precast slabs", GoodCategory.INTERMEDIATE, 130.0, VolatilityClass.STABLE)
        good("steel_decking", "Steel decking", GoodCategory.INTERMEDIATE, 110.0, VolatilityClass.STABLE)
        good("curtain_wall", "Curtain wall units", GoodCategory.INTERMEDIATE, 780.0, VolatilityClass.STABLE)
        good("elevators", "Elevators", GoodCategory.INDUSTRIAL, 48000.0, VolatilityClass.STABLE, weight = 5000, volume = 8000)
        good("scaffolding", "Scaffolding sets", GoodCategory.INDUSTRIAL, 2400.0, VolatilityClass.STABLE, weight = 3000, volume = 4000)
        good("drainage_system", "Drainage system", GoodCategory.INDUSTRIAL, 4500.0, VolatilityClass.STABLE, weight = 3000, volume = 4000)
        good("road_barriers", "Road barriers", GoodCategory.INDUSTRIAL, 140.0, VolatilityClass.STABLE)
        good("laminate_flooring", "Laminate flooring", GoodCategory.CONSUMER, 18.0, VolatilityClass.STABLE)
        good("hardwood_flooring", "Hardwood flooring", GoodCategory.CONSUMER, 38.0, VolatilityClass.STABLE)
        good("kitchen_cabinets", "Kitchen cabinets", GoodCategory.CONSUMER, 420.0, VolatilityClass.STABLE)
        good("bathtub", "Bathtub", GoodCategory.CONSUMER, 260.0, VolatilityClass.STABLE)
        good("garden_pavers", "Garden pavers", GoodCategory.CONSUMER, 9.0, VolatilityClass.STABLE)

        role("mason", "Mason", SkillBand.SKILLED, 82.0, 30)

        machine("tile_kiln_line", "Tile kiln line", 700000.0, throughput = 1.2, wearPerBatchPpm = 450, maintenance = 60.0, tech = "kiln_technology")
        machine("panel_press", "Continuous panel press", 1100000.0, throughput = 1.2, maintenance = 90.0, tech = "engineered_timber")
        machine("precast_mold_line", "Precast mold line", 900000.0, throughput = 1.25, wearPerBatchPpm = 480, maintenance = 75.0, laborSavings = 0.06, tech = "precast_engineering")

        facility("gypsum_quarry", "Gypsum quarry", FacilityClass.MINE, IndustryFamily.CONSTRUCTION) {
            cost = 800000.0; buildTicks = 55; lines = 3; storage = 80000.0; upkeep = 330.0
            energyPerLine = 20.0
            extracts = "gypsum"
            staff("miner", 1)
        }
        facility("gypsum_works", "Gypsum works", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 3400000.0; buildTicks = 80; lines = 3; storage = 20000.0; upkeep = 1150.0
            energyPerLine = 120.0
            tech = "gypsum_processing"
            staff("operator", 3)
            staff("mason", 1)
        }
        facility("tile_works", "Tile works", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 3100000.0; buildTicks = 75; lines = 3; storage = 20000.0; upkeep = 1050.0
            energyPerLine = 130.0
            staff("operator", 3)
            staff("mason", 1)
        }
        facility("insulation_works", "Insulation and envelope works", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 4200000.0; buildTicks = 90; lines = 3; storage = 15000.0; upkeep = 1300.0
            energyPerLine = 160.0
            tech = "thermal_envelope"
            staff("operator", 4)
        }
        facility("timber_engineering_plant", "Timber engineering plant", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 5600000.0; buildTicks = 100; lines = 3; storage = 20000.0; upkeep = 1700.0
            energyPerLine = 170.0
            tech = "engineered_timber"
            staff("operator", 4)
            staff("machinist", 2)
        }
        facility("precast_plant", "Precast plant", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 6000000.0; buildTicks = 100; lines = 3; storage = 10000.0; upkeep = 1800.0
            energyPerLine = 190.0
            tech = "precast_engineering"
            staff("operator", 4)
            staff("mason", 2)
        }
        facility("building_systems_works", "Building systems works", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 9500000.0; buildTicks = 130; lines = 3; storage = 10000.0; upkeep = 2500.0
            energyPerLine = 220.0
            tech = "building_systems"
            staff("machinist", 4)
            staff("engineer", 2)
        }

        recipe("quarry_gypsum", "Quarry gypsum", "interior_finishes", "gypsum_quarry", 7) {
            input("diesel", 905.0)
            input("explosives", 130.0)
            input("water", 775.0)
            output("gypsum", 3620.0)
            labor("miner", 5)
            energy = 518.0
        }
        recipe("calcine_plaster", "Calcine plaster", "interior_finishes", "gypsum_works", 3) {
            input("gypsum", 2870.0)
            input("natural_gas", 360.0)
            input("water", 720.0)
            output("plaster", 2420.0)
            labor("operator", 3)
            labor("mason", 1)
            energy = 1131.0
        }
        recipe("press_drywall", "Press drywall board", "interior_finishes", "gypsum_works", 3) {
            input("plaster", 1170.0)
            input("wood_pulp", 195.0)
            input("water", 585.0)
            output("drywall_board", 4030.0)
            labor("operator", 3)
            labor("mason", 1)
            energy = 1131.0
        }
        recipe("fire_ceramic_tiles", "Fire ceramic tiles", "interior_finishes", "tile_works", 3) {
            input("clay", 2380.0)
            input("silica_sand", 530.0)
            input("natural_gas", 320.0)
            output("ceramic_tiles", 2980.0)
            labor("operator", 3)
            labor("mason", 1)
            machine("tile_kiln_line")
            energy = 972.0
        }
        recipe("glaze_tiles", "Glaze tiles", "interior_finishes", "tile_works", 3) {
            input("ceramic_tiles", 1050.0)
            input("pigments", 70.0)
            input("silica_sand", 210.0)
            input("natural_gas", 175.0)
            output("glazed_tiles", 1610.0)
            labor("operator", 3)
            energy = 887.0
        }
        recipe("press_garden_pavers", "Press garden pavers", "interior_finishes", "tile_works", 2) {
            input("ceramic_tiles", 435.0)
            input("gravel", 865.0)
            input("cement", 290.0)
            output("garden_pavers", 2670.0)
            labor("operator", 3)
            energy = 541.0
        }
        recipe("enamel_bathtubs", "Enamel bathtubs", "interior_finishes", "tile_works", 4) {
            input("steel_sheet", 230.0)
            input("pigments", 33.0)
            input("silica_sand", 99.0)
            input("natural_gas", 99.0)
            output("bathtub", 175.0)
            labor("operator", 3)
            labor("mason", 1)
            energy = 986.0
        }
        recipe("spin_mineral_wool", "Spin mineral wool", "building_envelope", "insulation_works", 3) {
            input("silica_sand", 1590.0)
            input("limestone", 990.0)
            input("coal", 395.0)
            output("mineral_wool", 1860.0)
            labor("operator", 4)
            energy = 1220.0
        }
        recipe("expand_eps_board", "Expand polystyrene board", "building_envelope", "insulation_works", 2) {
            input("polystyrene", 290.0)
            input("industrial_gases", 96.0)
            output("eps_board", 1080.0)
            labor("operator", 4)
            energy = 576.0
        }
        recipe("roll_waterproof_membrane", "Roll waterproof membrane", "building_envelope", "insulation_works", 2) {
            input("bitumen", 450.0)
            input("polypropylene", 180.0)
            output("waterproof_membrane", 670.0)
            labor("operator", 4)
            energy = 675.0
        }
        recipe("make_cladding_panels", "Make cladding panels", "building_envelope", "insulation_works", 3) {
            input("aluminum_sheet", 160.0)
            input("mineral_wool", 225.0)
            input("silicone_sealant", 34.0)
            output("cladding_panels", 530.0)
            labor("operator", 4)
            energy = 679.0
        }
        recipe("press_osb", "Press OSB board", "engineered_wood", "timber_engineering_plant", 3) {
            input("logs", 1170.0)
            input("uf_resin", 130.0)
            input("water", 220.0)
            output("osb_board", 3000.0)
            labor("operator", 4)
            labor("machinist", 1)
            machine("panel_press")
            energy = 805.0
        }
        recipe("press_particleboard", "Press particleboard", "engineered_wood", "timber_engineering_plant", 3) {
            input("logs", 1140.0)
            input("uf_resin", 135.0)
            input("water", 190.0)
            output("particleboard", 3900.0)
            labor("operator", 4)
            labor("machinist", 1)
            machine("panel_press")
            energy = 949.0
        }
        recipe("laminate_clt", "Laminate cross-laminated timber", "engineered_wood", "timber_engineering_plant", 4) {
            input("lumber", 815.0)
            input("adhesives", 110.0)
            input("fire_retardant", 54.0)
            output("clt_panels", 210.0)
            labor("operator", 4)
            labor("machinist", 2)
            energy = 1223.0
        }
        recipe("glue_glulam", "Glue glulam beams", "engineered_wood", "timber_engineering_plant", 3) {
            input("lumber", 705.0)
            input("uf_resin", 125.0)
            output("glulam_beams", 290.0)
            labor("operator", 4)
            labor("machinist", 2)
            energy = 1100.0
        }
        recipe("press_laminate_flooring", "Press laminate flooring", "engineered_wood", "timber_engineering_plant", 2) {
            input("particleboard", 885.0)
            input("paper_goods", 295.0)
            input("adhesives", 120.0)
            output("laminate_flooring", 2120.0)
            labor("operator", 4)
            energy = 861.0
        }
        recipe("mill_hardwood_flooring", "Mill hardwood flooring", "engineered_wood", "timber_engineering_plant", 3) {
            input("lumber", 720.0)
            input("paint", 36.0)
            output("hardwood_flooring", 1500.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 1082.0
        }
        recipe("build_kitchen_cabinets", "Build kitchen cabinets", "engineered_wood", "timber_engineering_plant", 4) {
            input("particleboard", 720.0)
            input("plywood", 505.0)
            input("fasteners", 215.0)
            input("paint", 110.0)
            input("packaging", 290.0)
            output("kitchen_cabinets", 170.0)
            labor("operator", 4)
            labor("machinist", 2)
            energy = 1565.0
        }
        recipe("cast_precast_pipes", "Cast precast pipes", "structural_precast", "precast_plant", 3) {
            input("concrete", 1390.0)
            input("rebar", 185.0)
            output("precast_pipes", 690.0)
            labor("operator", 4)
            labor("mason", 2)
            machine("precast_mold_line")
            energy = 929.0
        }
        recipe("cast_precast_slabs", "Cast precast slabs", "structural_precast", "precast_plant", 3) {
            input("concrete", 1290.0)
            input("rebar", 230.0)
            output("precast_slabs", 505.0)
            labor("operator", 4)
            labor("mason", 2)
            machine("precast_mold_line")
            energy = 771.0
        }
        recipe("roll_steel_decking", "Roll steel decking", "structural_precast", "precast_plant", 2) {
            input("steel_sheet", 325.0)
            input("lubricants", 8.0)
            output("steel_decking", 400.0)
            labor("operator", 4)
            labor("mason", 1)
            energy = 567.0
        }
        recipe("cast_road_barriers", "Cast road barriers", "structural_precast", "precast_plant", 2) {
            input("concrete", 845.0)
            input("rebar", 115.0)
            output("road_barriers", 290.0)
            labor("operator", 4)
            labor("mason", 1)
            energy = 564.0
        }
        recipe("assemble_drainage_systems", "Assemble drainage systems", "structural_precast", "precast_plant", 6) {
            input("precast_pipes", 610.0)
            input("geotextile", 120.0)
            input("gravel", 2450.0)
            output("drainage_system", 25.0)
            labor("operator", 4)
            labor("mason", 2)
            energy = 1224.0
        }
        recipe("assemble_curtain_wall", "Assemble curtain wall units", "building_envelope", "building_systems_works", 4) {
            input("glass", 515.0)
            input("aluminum_sheet", 260.0)
            input("silicone_sealant", 86.0)
            output("curtain_wall", 120.0)
            labor("machinist", 4)
            labor("engineer", 1)
            energy = 2064.0
        }
        recipe("assemble_elevators", "Assemble elevators", "structural_precast", "building_systems_works", 20) {
            input("electric_motor", 165.0)
            input("controller_unit", 125.0)
            input("steel_beams", 835.0)
            input("steel_wire", 415.0)
            input("gearbox", 42.0)
            output("elevators", 9.5)
            labor("machinist", 6)
            labor("engineer", 2)
            energy = 6250.0
            tech = "building_systems"
        }
        recipe("fabricate_scaffolding", "Fabricate scaffolding sets", "structural_precast", "building_systems_works", 6) {
            input("steel_pipe", 815.0)
            input("fasteners", 405.0)
            input("steel_sheet", 205.0)
            output("scaffolding", 67.0)
            labor("machinist", 4)
            labor("engineer", 1)
            energy = 3049.0
        }

        tech("gypsum_processing", "Gypsum processing", TechCategory.MATERIALS, 1, 350000.0, 70) {
            requires("deep_mining")
            unlocksFacility("gypsum_works")
            effect(EffectKind.YIELD_BONUS, 0.06, industry = "interior_finishes")
        }
        tech("kiln_technology", "Kiln technology", TechCategory.PRODUCTION, 1, 420000.0, 70) {
            unlocksMachine("tile_kiln_line")
            effect(EffectKind.ENERGY_SAVINGS, 0.05, industry = "interior_finishes")
        }
        tech("thermal_envelope", "Thermal envelope design", TechCategory.MATERIALS, 2, 1000000.0, 110) {
            requires("catalytic_cracking")
            unlocksFacility("insulation_works")
            effect(EffectKind.ENERGY_SAVINGS, 0.06, industry = "building_envelope")
        }
        tech("engineered_timber", "Engineered timber", TechCategory.MATERIALS, 2, 1100000.0, 110) {
            requires("prefab_construction")
            unlocksFacility("timber_engineering_plant")
            unlocksMachine("panel_press")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "engineered_wood")
        }
        tech("precast_engineering", "Precast engineering", TechCategory.PRODUCTION, 2, 1400000.0, 120) {
            requires("prefab_construction")
            unlocksFacility("precast_plant")
            unlocksMachine("precast_mold_line")
        }
        tech("building_systems", "Building systems integration", TechCategory.PRODUCTION, 3, 3200000.0, 180) {
            requires("precast_engineering", "factory_automation")
            unlocksRecipe("assemble_elevators")
            unlocksFacility("building_systems_works")
            effect(EffectKind.THROUGHPUT_BONUS, 0.06, industry = "structural_precast")
        }
        tech("green_building_codes", "Green building codes", TechCategory.MANAGEMENT, 2, 800000.0, 100) {
            requires("thermal_envelope")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "building_envelope")
            effect(EffectKind.INPUT_SAVINGS, 0.03, industry = "engineered_wood")
        }
        tech("site_logistics", "Modular site logistics", TechCategory.LOGISTICS, 2, 900000.0, 100) {
            requires("route_optimization")
            effect(EffectKind.TRANSPORT_SAVINGS, 0.05)
        }

        demand("laminate_flooring", 60L, 90L, 100L, 0.8)
        demand("hardwood_flooring", 10L, 30L, 70L, 1.0, incomeElasticity = 1.3)
        demand("kitchen_cabinets", 1L, 3L, 6L, 1.0, incomeElasticity = 1.3, nonConsumers = 0.4)
        demand("bathtub", 1L, 2L, 4L, 1.0, incomeElasticity = 1.2, nonConsumers = 0.5)
        demand("garden_pavers", 20L, 50L, 80L, 1.0, incomeElasticity = 1.2, seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 1.8, app.many.economytycoon.core.Season.SUMMER to 1.5, app.many.economytycoon.core.Season.WINTER to 0.2))
        demand("drywall_board", 60L, 80L, 90L, 0.6, incomeElasticity = 0.6, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 0.7, app.many.economytycoon.core.Season.SUMMER to 1.2))
        demand("glazed_tiles", 30L, 50L, 70L, 0.8, incomeElasticity = 0.9)
        demand("mineral_wool", 20L, 30L, 34L, 0.6, incomeElasticity = 0.5)
        demand("eps_board", 20L, 28L, 30L, 0.6, incomeElasticity = 0.5)
        demand("waterproof_membrane", 10L, 14L, 16L, 0.5, incomeElasticity = 0.4)
        demand("cladding_panels", 6L, 10L, 12L, 0.7)
        demand("clt_panels", 1L, 2L, 3L, 0.8, nonConsumers = 0.5)
        demand("glulam_beams", 1L, 2L, 3L, 0.8, nonConsumers = 0.5)
        demand("osb_board", 30L, 40L, 44L, 0.6, incomeElasticity = 0.5)
        demand("precast_slabs", 4L, 6L, 8L, 0.6, incomeElasticity = 0.5)
        demand("steel_decking", 4L, 6L, 8L, 0.6, incomeElasticity = 0.5)
        demand("curtain_wall", 1L, 2L, 3L, 0.8, incomeElasticity = 0.9, nonConsumers = 0.5)
        demand("elevators", 0L, 1L, 1L, 0.5, incomeElasticity = 0.6, nonConsumers = 0.97)
        demand("scaffolding", 0L, 1L, 1L, 0.5, incomeElasticity = 0.5, nonConsumers = 0.9)
        demand("drainage_system", 0L, 1L, 1L, 0.5, incomeElasticity = 0.5, nonConsumers = 0.9)
        demand("road_barriers", 4L, 6L, 6L, 0.5, incomeElasticity = 0.4)
    }
}

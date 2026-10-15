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

object MiningPack {
    val pack: ContentPack = contentPack("mining", 1, 0) {
        industry("mining_ores", "Ore mining", IndustryFamily.MINING, "Metal ores and industrial minerals")
        industry("quarrying", "Quarrying", IndustryFamily.MINING, "Stone, sand, gravel, clay")
        industry("metallurgy", "Metallurgy", IndustryFamily.METALS, "Smelting and refining")
        industry("metalworking", "Metalworking", IndustryFamily.METALS, "Rolling, casting, fabrication")

        good("iron_ore", "Iron ore", GoodCategory.RAW, 18.0, weight = 1400)
        good("copper_ore", "Copper ore", GoodCategory.RAW, 40.0, VolatilityClass.VOLATILE, weight = 1400)
        good("bauxite", "Bauxite", GoodCategory.RAW, 25.0, weight = 1400)
        good("limestone", "Limestone", GoodCategory.RAW, 8.0, VolatilityClass.STABLE, weight = 1400)
        good("sand", "Sand", GoodCategory.RAW, 6.0, VolatilityClass.STABLE, weight = 1400)
        good("gravel", "Gravel", GoodCategory.RAW, 5.0, VolatilityClass.STABLE, weight = 1400)
        good("clay", "Clay", GoodCategory.RAW, 7.0, VolatilityClass.STABLE, weight = 1400)
        good("salt", "Salt", GoodCategory.RAW, 6.0, VolatilityClass.STABLE, weight = 1400)
        good("stone_blocks_raw", "Quarry stone", GoodCategory.RAW, 12.0, VolatilityClass.STABLE, weight = 1400)
        good("gold_ore", "Gold ore", GoodCategory.RAW, 260.0, VolatilityClass.SPECULATIVE, weight = 1200)
        good("lithium_ore", "Lithium ore", GoodCategory.RAW, 90.0, VolatilityClass.VOLATILE, weight = 1400)
        good("rare_earth_ore", "Rare earth ore", GoodCategory.RAW, 200.0, VolatilityClass.SPECULATIVE, weight = 1200)
        good("silica_sand", "Silica sand", GoodCategory.RAW, 12.0, VolatilityClass.STABLE, weight = 1400)
        good("tin_ore", "Tin ore", GoodCategory.RAW, 70.0, VolatilityClass.VOLATILE, weight = 1400)
        good("zinc_ore", "Zinc ore", GoodCategory.RAW, 32.0, VolatilityClass.VOLATILE, weight = 1400)
        good("nickel_ore", "Nickel ore", GoodCategory.RAW, 55.0, VolatilityClass.VOLATILE, weight = 1400)
        good("pig_iron", "Pig iron", GoodCategory.INTERMEDIATE, 40.0, weight = 1500)
        good("steel", "Steel", GoodCategory.INTERMEDIATE, 75.0, weight = 1500)
        good("steel_sheet", "Steel sheet", GoodCategory.INTERMEDIATE, 95.0, weight = 1500)
        good("steel_beams", "Structural steel", GoodCategory.INTERMEDIATE, 90.0, weight = 1500)
        good("steel_wire", "Steel wire", GoodCategory.INTERMEDIATE, 85.0, weight = 1500)
        good("steel_pipe", "Steel pipe", GoodCategory.INTERMEDIATE, 92.0, weight = 1500)
        good("copper_ingot", "Copper ingot", GoodCategory.INTERMEDIATE, 120.0, VolatilityClass.VOLATILE, weight = 1500)
        good("copper_wire", "Copper wire", GoodCategory.INTERMEDIATE, 150.0, VolatilityClass.VOLATILE, weight = 1500)
        good("copper_pipe", "Copper pipe", GoodCategory.INTERMEDIATE, 145.0, VolatilityClass.VOLATILE, weight = 1500)
        good("alumina", "Alumina", GoodCategory.INTERMEDIATE, 55.0, weight = 1500)
        good("aluminum_ingot", "Aluminum ingot", GoodCategory.INTERMEDIATE, 110.0, VolatilityClass.VOLATILE, weight = 1500)
        good("aluminum_sheet", "Aluminum sheet", GoodCategory.INTERMEDIATE, 135.0, VolatilityClass.VOLATILE, weight = 1500)
        good("scrap_metal", "Scrap metal", GoodCategory.INTERMEDIATE, 10.0, weight = 1500)
        good("zinc_ingot", "Zinc ingot", GoodCategory.INTERMEDIATE, 85.0, VolatilityClass.VOLATILE, weight = 1500)
        good("tin_ingot", "Tin ingot", GoodCategory.INTERMEDIATE, 190.0, VolatilityClass.VOLATILE, weight = 1500)
        good("nickel_ingot", "Nickel ingot", GoodCategory.INTERMEDIATE, 160.0, VolatilityClass.VOLATILE, weight = 1500)
        good("gold_bar", "Gold bar", GoodCategory.INTERMEDIATE, 1400.0, VolatilityClass.SPECULATIVE, weight = 1500)
        good("lithium_carbonate", "Lithium carbonate", GoodCategory.INTERMEDIATE, 340.0, VolatilityClass.VOLATILE, weight = 1500)
        good("rare_earth_oxide", "Rare earth oxide", GoodCategory.INTERMEDIATE, 650.0, VolatilityClass.SPECULATIVE, weight = 1500)
        good("cement", "Cement", GoodCategory.INTERMEDIATE, 14.0, VolatilityClass.STABLE, weight = 1500)
        good("lime", "Quicklime", GoodCategory.INTERMEDIATE, 12.0, VolatilityClass.STABLE, weight = 1500)
        good("glass", "Glass sheet", GoodCategory.INTERMEDIATE, 30.0, VolatilityClass.STABLE, weight = 1500)
        good("bricks", "Bricks", GoodCategory.INTERMEDIATE, 18.0, VolatilityClass.STABLE, weight = 1500)
        good("stainless_steel", "Stainless steel", GoodCategory.INTERMEDIATE, 190.0, weight = 1500)
        good("castings", "Iron castings", GoodCategory.INTERMEDIATE, 70.0, weight = 1500)
        good("forgings", "Steel forgings", GoodCategory.INTERMEDIATE, 110.0, weight = 1500)
        good("fasteners", "Fasteners", GoodCategory.INTERMEDIATE, 22.0, VolatilityClass.STABLE, weight = 1500)
        good("sheet_metal_parts", "Sheet metal parts", GoodCategory.INTERMEDIATE, 55.0, VolatilityClass.STABLE, weight = 1500)

        facility("iron_mine", "Iron mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 1200000.0; buildTicks = 70; lines = 3; storage = 60000.0; upkeep = 500.0
            energyPerLine = 30.0
            extracts = "iron_ore"
            staff("miner", 2)
        }
        facility("copper_mine", "Copper mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 1500000.0; buildTicks = 80; lines = 3; storage = 60000.0; upkeep = 560.0
            energyPerLine = 35.0
            extracts = "copper_ore"
            staff("miner", 2)
        }
        facility("bauxite_mine", "Bauxite mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 1100000.0; buildTicks = 70; lines = 3; storage = 60000.0; upkeep = 480.0
            energyPerLine = 28.0
            extracts = "bauxite"
            staff("miner", 2)
        }
        facility("quarry", "Quarry", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 700000.0; buildTicks = 50; lines = 4; storage = 100000.0; upkeep = 320.0
            energyPerLine = 18.0
            extracts = "stone_blocks_raw"
            staff("miner", 1)
        }
        facility("sand_pit", "Sand and gravel pit", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 380000.0; buildTicks = 35; lines = 3; storage = 100000.0; upkeep = 200.0
            energyPerLine = 10.0
            extracts = "sand"
            staff("laborer", 1)
        }
        facility("precious_mine", "Precious metals mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 2600000.0; buildTicks = 100; lines = 2; storage = 20000.0; upkeep = 900.0
            energyPerLine = 55.0
            extracts = "gold_ore"
            staff("miner", 3)
            staff("engineer", 1)
        }
        facility("lithium_mine", "Lithium mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 2200000.0; buildTicks = 90; lines = 2; storage = 30000.0; upkeep = 800.0
            energyPerLine = 50.0
            extracts = "lithium_ore"
            staff("miner", 3)
            staff("engineer", 1)
        }
        facility("rare_earth_mine", "Rare earth mine", FacilityClass.MINE, IndustryFamily.MINING) {
            cost = 3100000.0; buildTicks = 110; lines = 2; storage = 20000.0; upkeep = 1000.0
            energyPerLine = 60.0
            extracts = "rare_earth_ore"
            staff("miner", 3)
            staff("engineer", 1)
        }
        facility("blast_furnace", "Blast furnace", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 3500000.0; buildTicks = 90; lines = 2; storage = 80000.0; upkeep = 1100.0
            energyPerLine = 150.0
            staff("operator", 3)
        }
        facility("steel_mill", "Steel mill", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 4200000.0; buildTicks = 100; lines = 3; storage = 80000.0; upkeep = 1400.0
            energyPerLine = 180.0
            staff("operator", 3)
            staff("engineer", 1)
        }
        facility("copper_smelter", "Copper smelter", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 2900000.0; buildTicks = 80; lines = 2; storage = 50000.0; upkeep = 900.0
            energyPerLine = 120.0
            staff("operator", 3)
        }
        facility("aluminum_smelter", "Aluminum smelter", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 3400000.0; buildTicks = 90; lines = 2; storage = 50000.0; upkeep = 1100.0
            energyPerLine = 260.0
            staff("operator", 3)
        }
        facility("rolling_mill", "Rolling mill", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 2400000.0; buildTicks = 70; lines = 3; storage = 60000.0; upkeep = 800.0
            energyPerLine = 110.0
            staff("operator", 2)
        }
        facility("foundry", "Foundry", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 1800000.0; buildTicks = 60; lines = 3; storage = 40000.0; upkeep = 700.0
            energyPerLine = 90.0
            staff("operator", 2)
        }
        facility("metalworks", "Metal fabrication shop", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 1100000.0; buildTicks = 50; lines = 4; storage = 30000.0; upkeep = 480.0
            energyPerLine = 45.0
            staff("machinist", 2)
        }
        facility("refinery_minerals", "Mineral processing plant", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 1900000.0; buildTicks = 70; lines = 3; storage = 40000.0; upkeep = 650.0
            energyPerLine = 80.0
            staff("operator", 2)
            staff("technician", 1)
        }
        facility("cement_plant", "Cement plant", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 2300000.0; buildTicks = 80; lines = 2; storage = 80000.0; upkeep = 850.0
            energyPerLine = 140.0
            staff("operator", 3)
        }
        facility("glassworks", "Glassworks", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 1700000.0; buildTicks = 65; lines = 2; storage = 40000.0; upkeep = 700.0
            energyPerLine = 120.0
            staff("operator", 2)
        }
        facility("brickworks", "Brickworks", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 900000.0; buildTicks = 55; lines = 3; storage = 60000.0; upkeep = 420.0
            energyPerLine = 70.0
            staff("operator", 2)
        }

        recipe("mine_iron", "Mine iron ore", "mining_ores", "iron_mine", 8) {
            input("diesel", 90.0)
            input("explosives", 15.0)
            input("water", 80.0)
            output("iron_ore", 570.0)
            labor("miner", 8)
            labor("operator", 1)
            energy = 60.0
        }
        recipe("mine_copper", "Mine copper ore", "mining_ores", "copper_mine", 9) {
            input("diesel", 100.0)
            input("explosives", 18.0)
            input("water", 90.0)
            output("copper_ore", 290.0)
            labor("miner", 8)
            labor("operator", 1)
            energy = 70.0
        }
        recipe("mine_bauxite", "Mine bauxite", "mining_ores", "bauxite_mine", 8) {
            input("diesel", 80.0)
            input("explosives", 12.0)
            input("water", 70.0)
            output("bauxite", 325.0)
            labor("miner", 7)
            energy = 55.0
        }
        recipe("mine_gold", "Mine gold ore", "mining_ores", "precious_mine", 14) {
            input("diesel", 120.0)
            input("explosives", 30.0)
            input("water", 200.0)
            output("gold_ore", 80.0)
            labor("miner", 9)
            labor("engineer", 1)
            energy = 110.0
        }
        recipe("mine_lithium", "Mine lithium ore", "mining_ores", "lithium_mine", 12) {
            input("diesel", 110.0)
            input("explosives", 25.0)
            input("water", 250.0)
            output("lithium_ore", 180.0)
            labor("miner", 8)
            labor("engineer", 1)
            energy = 100.0
        }
        recipe("mine_rare_earth", "Mine rare earth ore", "mining_ores", "rare_earth_mine", 14) {
            input("diesel", 120.0)
            input("explosives", 30.0)
            input("water", 220.0)
            output("rare_earth_ore", 100.0)
            labor("miner", 9)
            labor("engineer", 1)
            energy = 120.0
        }
        recipe("mine_tin", "Mine tin ore", "mining_ores", "copper_mine", 10) {
            input("diesel", 90.0)
            input("explosives", 15.0)
            input("water", 90.0)
            output("tin_ore", 140.0)
            labor("miner", 7)
            energy = 60.0
        }
        recipe("mine_zinc", "Mine zinc ore", "mining_ores", "copper_mine", 9) {
            input("diesel", 90.0)
            input("explosives", 15.0)
            input("water", 80.0)
            output("zinc_ore", 290.0)
            labor("miner", 7)
            energy = 60.0
        }
        recipe("mine_nickel", "Mine nickel ore", "mining_ores", "copper_mine", 10) {
            input("diesel", 95.0)
            input("explosives", 18.0)
            input("water", 90.0)
            output("nickel_ore", 185.0)
            labor("miner", 7)
            energy = 65.0
        }
        recipe("quarry_stone", "Quarry stone", "quarrying", "quarry", 5) {
            input("diesel", 60.0)
            input("explosives", 10.0)
            output("stone_blocks_raw", 365.0)
            labor("miner", 4)
            labor("operator", 1)
            energy = 40.0
        }
        recipe("quarry_limestone", "Quarry limestone", "quarrying", "quarry", 4) {
            input("diesel", 50.0)
            input("explosives", 8.0)
            output("limestone", 390.0)
            labor("miner", 4)
            energy = 35.0
        }
        recipe("dig_sand", "Dig sand", "quarrying", "sand_pit", 3) {
            input("diesel", 45.0)
            output("sand", 275.0)
            labor("laborer", 3)
            labor("operator", 1)
            energy = 18.0
        }
        recipe("dig_gravel", "Dig gravel", "quarrying", "sand_pit", 3) {
            input("diesel", 45.0)
            output("gravel", 330.0)
            labor("laborer", 3)
            labor("operator", 1)
            energy = 18.0
        }
        recipe("dig_clay", "Dig clay", "quarrying", "sand_pit", 3) {
            input("diesel", 40.0)
            output("clay", 175.0)
            labor("laborer", 3)
            energy = 16.0
        }
        recipe("wash_silica", "Wash silica sand", "quarrying", "sand_pit", 3) {
            input("sand", 150.0)
            input("water", 200.0)
            output("silica_sand", 185.0)
            labor("operator", 2)
            energy = 24.0
        }
        recipe("evaporate_salt", "Harvest salt", "quarrying", "sand_pit", 10) {
            input("water", 600.0)
            output("salt", 390.0)
            labor("laborer", 2)
            energy = 10.0
        }
        recipe("make_pig_iron", "Smelt pig iron", "metallurgy", "blast_furnace", 5) {
            input("iron_ore", 300.0)
            input("coal", 150.0)
            input("limestone", 40.0)
            output("pig_iron", 360.0)
            waste("scrap", 20.0)
            labor("operator", 6)
            energy = 240.0
        }
        recipe("make_steel", "Make steel", "metallurgy", "steel_mill", 4) {
            input("pig_iron", 200.0)
            input("scrap_metal", 60.0)
            input("lubricants", 2.0)
            output("steel", 215.0)
            waste("scrap", 15.0)
            labor("operator", 6)
            labor("engineer", 1)
            energy = 320.0
        }
        recipe("make_steel_scrap", "Recycle scrap into steel", "metallurgy", "steel_mill", 4) {
            input("scrap_metal", 280.0)
            input("coal", 20.0)
            output("steel", 125.0)
            labor("operator", 5)
            energy = 380.0
        }
        recipe("make_stainless", "Make stainless steel", "metallurgy", "steel_mill", 5) {
            input("steel", 120.0)
            input("nickel_ingot", 20.0)
            output("stainless_steel", 105.0)
            labor("operator", 5)
            labor("engineer", 1)
            energy = 300.0
        }
        recipe("smelt_copper", "Smelt copper", "metallurgy", "copper_smelter", 5) {
            input("copper_ore", 400.0)
            input("coal", 80.0)
            input("limestone", 30.0)
            output("copper_ingot", 210.0)
            waste("scrap", 12.0)
            labor("operator", 5)
            energy = 260.0
        }
        recipe("smelt_zinc", "Smelt zinc", "metallurgy", "copper_smelter", 5) {
            input("zinc_ore", 350.0)
            input("coal", 60.0)
            output("zinc_ingot", 220.0)
            labor("operator", 5)
            energy = 230.0
        }
        recipe("smelt_tin", "Smelt tin", "metallurgy", "copper_smelter", 5) {
            input("tin_ore", 300.0)
            input("coal", 60.0)
            output("tin_ingot", 155.0)
            labor("operator", 5)
            energy = 220.0
        }
        recipe("smelt_nickel", "Smelt nickel", "metallurgy", "copper_smelter", 6) {
            input("nickel_ore", 320.0)
            input("coal", 70.0)
            output("nickel_ingot", 170.0)
            labor("operator", 5)
            energy = 260.0
        }
        recipe("refine_gold", "Refine gold", "metallurgy", "refinery_minerals", 6) {
            input("gold_ore", 400.0)
            input("water", 200.0)
            input("sulfuric_acid", 40.0)
            output("gold_bar", 90.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 180.0
        }
        recipe("extract_lithium", "Process lithium carbonate", "metallurgy", "refinery_minerals", 6) {
            input("lithium_ore", 300.0)
            input("sulfuric_acid", 30.0)
            input("water", 300.0)
            output("lithium_carbonate", 105.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 160.0
        }
        recipe("separate_rare_earths", "Separate rare earth oxides", "metallurgy", "refinery_minerals", 8) {
            input("rare_earth_ore", 250.0)
            input("sulfuric_acid", 60.0)
            input("water", 300.0)
            output("rare_earth_oxide", 100.0)
            labor("operator", 3)
            labor("technician", 2)
            energy = 200.0
        }
        recipe("make_alumina", "Refine alumina", "metallurgy", "refinery_minerals", 5) {
            input("bauxite", 400.0)
            input("lime", 40.0)
            input("water", 200.0)
            output("alumina", 290.0)
            labor("operator", 4)
            energy = 170.0
        }
        recipe("smelt_aluminum", "Smelt aluminum", "metallurgy", "aluminum_smelter", 5) {
            input("alumina", 300.0)
            output("aluminum_ingot", 240.0)
            labor("operator", 5)
            energy = 520.0
        }
        recipe("burn_lime", "Burn lime", "metallurgy", "cement_plant", 3) {
            input("limestone", 200.0)
            input("coal", 25.0)
            output("lime", 285.0)
            labor("operator", 2)
            energy = 60.0
        }
        recipe("roll_sheet", "Roll steel sheet", "metalworking", "rolling_mill", 3) {
            input("steel", 120.0)
            input("lubricants", 2.0)
            output("steel_sheet", 135.0)
            labor("operator", 4)
            energy = 140.0
        }
        recipe("roll_beams", "Roll structural steel", "metalworking", "rolling_mill", 3) {
            input("steel", 130.0)
            input("lubricants", 2.0)
            output("steel_beams", 155.0)
            labor("operator", 4)
            energy = 150.0
        }
        recipe("draw_steel_wire", "Draw steel wire", "metalworking", "rolling_mill", 3) {
            input("steel", 100.0)
            input("lubricants", 2.0)
            output("steel_wire", 125.0)
            labor("operator", 3)
            energy = 100.0
        }
        recipe("make_steel_pipe", "Make steel pipe", "metalworking", "rolling_mill", 3) {
            input("steel", 110.0)
            input("lubricants", 2.0)
            output("steel_pipe", 125.0)
            labor("operator", 3)
            energy = 110.0
        }
        recipe("draw_copper_wire", "Draw copper wire", "metalworking", "rolling_mill", 3) {
            input("copper_ingot", 90.0)
            input("lubricants", 2.0)
            output("copper_wire", 95.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("make_copper_pipe", "Make copper pipe", "metalworking", "rolling_mill", 3) {
            input("copper_ingot", 95.0)
            output("copper_pipe", 100.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("roll_aluminum", "Roll aluminum sheet", "metalworking", "rolling_mill", 3) {
            input("aluminum_ingot", 100.0)
            input("lubricants", 2.0)
            output("aluminum_sheet", 110.0)
            labor("operator", 3)
            energy = 110.0
        }
        recipe("cast_iron", "Cast iron parts", "metalworking", "foundry", 3) {
            input("pig_iron", 130.0)
            input("scrap_metal", 30.0)
            output("castings", 125.0)
            labor("operator", 3)
            energy = 150.0
        }
        recipe("forge_steel", "Forge steel parts", "metalworking", "foundry", 4) {
            input("steel", 120.0)
            input("lubricants", 3.0)
            output("forgings", 125.0)
            labor("operator", 3)
            labor("machinist", 1)
            energy = 180.0
        }
        recipe("press_sheet_parts", "Press sheet metal parts", "metalworking", "metalworks", 2) {
            input("steel_sheet", 90.0)
            input("lubricants", 1.0)
            output("sheet_metal_parts", 200.0)
            waste("scrap", 6.0)
            labor("machinist", 3)
            energy = 60.0
        }
        recipe("make_fasteners", "Make fasteners", "metalworking", "metalworks", 2) {
            input("steel_wire", 70.0)
            input("lubricants", 1.0)
            output("fasteners", 350.0)
            labor("machinist", 2)
            energy = 45.0
        }
        recipe("make_cement", "Make cement", "metallurgy", "cement_plant", 4) {
            input("limestone", 300.0)
            input("clay", 60.0)
            input("coal", 60.0)
            output("cement", 525.0)
            labor("operator", 3)
            energy = 200.0
        }
        recipe("make_glass", "Make glass sheet", "metallurgy", "glassworks", 3) {
            input("silica_sand", 200.0)
            input("limestone", 40.0)
            input("natural_gas", 40.0)
            output("glass", 200.0)
            labor("operator", 3)
            energy = 160.0
        }
        recipe("fire_bricks", "Fire bricks", "metallurgy", "brickworks", 4) {
            input("clay", 300.0)
            input("natural_gas", 30.0)
            output("bricks", 240.0)
            labor("operator", 2)
            energy = 80.0
        }

        tech("deep_mining", "Deep shaft mining", TechCategory.MATERIALS, 1, 200000.0, 60) {
            effect(EffectKind.YIELD_BONUS, 0.06, industry = "mining_ores")
        }
        tech("blast_optimization", "Blast optimization", TechCategory.MATERIALS, 2, 380000.0, 90) {
            requires("deep_mining")
            effect(EffectKind.INPUT_SAVINGS, 0.08, industry = "mining_ores")
        }
        tech("continuous_casting", "Continuous casting", TechCategory.MATERIALS, 2, 450000.0, 90) {
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "metallurgy")
            effect(EffectKind.ENERGY_SAVINGS, 0.06, industry = "metallurgy")
        }
        tech("electric_arc_furnace", "Electric arc furnaces", TechCategory.ENERGY, 3, 750000.0, 120) {
            requires("continuous_casting")
            effect(EffectKind.ENERGY_SAVINGS, 0.1, industry = "metallurgy")
        }
        tech("precision_rolling", "Precision rolling", TechCategory.PRODUCTION, 2, 400000.0, 90) {
            requires("continuous_casting")
            effect(EffectKind.QUALITY_BONUS, 0.12, industry = "metalworking")
        }
    }
}

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

object EnergyPack {
    val pack: ContentPack = contentPack("energy", 1, 0) {
        industry("oil_gas", "Oil and gas", IndustryFamily.OIL_AND_GAS, "Extraction and refining")
        industry("power", "Power generation", IndustryFamily.ENERGY, "Thermal and nuclear generation")
        industry("renewables", "Renewable energy", IndustryFamily.RENEWABLE_ENERGY, "Wind, solar, hydro, geothermal")
        industry("chemicals", "Chemicals", IndustryFamily.CHEMICALS, "Basic and specialty chemicals")

        good("crude_oil", "Crude oil", GoodCategory.RAW, 70.0, VolatilityClass.VOLATILE, weight = 1100)
        good("uranium_ore", "Uranium ore", GoodCategory.RAW, 320.0, VolatilityClass.VOLATILE, weight = 1600)
        good("biomass", "Biomass feedstock", GoodCategory.RAW, 9.0, spoilagePpm = 8000)
        good("brine", "Brine", GoodCategory.RAW, 2.0, VolatilityClass.STABLE)
        good("kerosene", "Jet fuel", GoodCategory.INTERMEDIATE, 15.0, VolatilityClass.VOLATILE)
        good("naphtha", "Naphtha", GoodCategory.INTERMEDIATE, 46.0, VolatilityClass.VOLATILE)
        good("bitumen", "Bitumen", GoodCategory.INTERMEDIATE, 24.0)
        good("propane", "Propane", GoodCategory.INTERMEDIATE, 11.0, VolatilityClass.VOLATILE)
        good("polyethylene", "Polyethylene resin", GoodCategory.INTERMEDIATE, 48.0)
        good("polypropylene", "Polypropylene resin", GoodCategory.INTERMEDIATE, 52.0)
        good("pvc_resin", "PVC resin", GoodCategory.INTERMEDIATE, 44.0)
        good("synthetic_rubber", "Synthetic rubber", GoodCategory.INTERMEDIATE, 60.0)
        good("sulfuric_acid", "Sulfuric acid", GoodCategory.INTERMEDIATE, 25.0, VolatilityClass.STABLE)
        good("caustic_soda", "Caustic soda", GoodCategory.INTERMEDIATE, 32.0, VolatilityClass.STABLE)
        good("chlorine", "Chlorine", GoodCategory.INTERMEDIATE, 28.0, VolatilityClass.STABLE)
        good("ammonia", "Ammonia", GoodCategory.INTERMEDIATE, 45.0)
        good("nitric_acid", "Nitric acid", GoodCategory.INTERMEDIATE, 34.0, VolatilityClass.STABLE)
        good("industrial_gases", "Industrial gases", GoodCategory.INTERMEDIATE, 20.0, VolatilityClass.STABLE)
        good("solvents", "Solvents", GoodCategory.INTERMEDIATE, 38.0)
        good("pigments", "Pigments", GoodCategory.INTERMEDIATE, 70.0)
        good("resins_epoxy", "Epoxy resin", GoodCategory.INTERMEDIATE, 90.0)
        good("adhesives", "Adhesives", GoodCategory.INTERMEDIATE, 55.0)
        good("phosphate_rock", "Phosphate rock", GoodCategory.INTERMEDIATE, 20.0)
        good("enriched_uranium", "Nuclear fuel", GoodCategory.INTERMEDIATE, 2400.0, VolatilityClass.STABLE)
        good("biodiesel", "Biodiesel", GoodCategory.INTERMEDIATE, 14.0)
        good("ethanol", "Ethanol", GoodCategory.INTERMEDIATE, 22.0)
        good("hydrogen", "Hydrogen", GoodCategory.INTERMEDIATE, 40.0)
        good("activated_carbon", "Activated carbon", GoodCategory.INTERMEDIATE, 52.0, VolatilityClass.STABLE)
        good("plastic_granules", "Plastic granules", GoodCategory.INTERMEDIATE, 50.0)
        good("detergent_base", "Detergent base", GoodCategory.INTERMEDIATE, 36.0)
        good("glycerin", "Glycerin", GoodCategory.INTERMEDIATE, 40.0)
        good("explosives", "Industrial explosives", GoodCategory.INDUSTRIAL, 60.0, VolatilityClass.STABLE)
        good("paint", "Paint", GoodCategory.INDUSTRIAL, 34.0, VolatilityClass.STABLE)
        good("pesticides", "Pesticides", GoodCategory.INDUSTRIAL, 66.0, VolatilityClass.STABLE)
        good("lithium_battery_cells", "Battery cells", GoodCategory.INDUSTRIAL, 160.0)
        good("solar_cells", "Solar cells", GoodCategory.INDUSTRIAL, 140.0)
        good("fertilizer_compound", "Compound fertilizer", GoodCategory.INDUSTRIAL, 31.0)

        facility("oil_well", "Oil well field", FacilityClass.WELL, IndustryFamily.OIL_AND_GAS) {
            cost = 3000000.0; buildTicks = 80; lines = 3; storage = 80000.0; upkeep = 900.0
            energyPerLine = 60.0
            extracts = "crude_oil"
            staff("operator", 2)
        }
        facility("gas_well", "Gas well field", FacilityClass.WELL, IndustryFamily.OIL_AND_GAS) {
            cost = 2600000.0; buildTicks = 70; lines = 3; storage = 60000.0; upkeep = 800.0
            energyPerLine = 45.0
            extracts = "natural_gas"
            staff("operator", 2)
        }
        facility("oil_refinery", "Oil refinery", FacilityClass.REFINERY, IndustryFamily.OIL_AND_GAS) {
            cost = 9000000.0; buildTicks = 130; lines = 3; storage = 150000.0; upkeep = 2600.0
            energyPerLine = 300.0
            staff("operator", 5)
            staff("engineer", 2)
        }
        facility("petrochemical_plant", "Petrochemical plant", FacilityClass.REFINERY, IndustryFamily.CHEMICALS) {
            cost = 7500000.0; buildTicks = 120; lines = 3; storage = 100000.0; upkeep = 2100.0
            energyPerLine = 260.0
            staff("operator", 4)
            staff("engineer", 2)
        }
        facility("chemical_plant", "Chemical plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 3200000.0; buildTicks = 90; lines = 4; storage = 60000.0; upkeep = 1100.0
            energyPerLine = 120.0
            staff("operator", 3)
            staff("technician", 1)
        }
        facility("fertilizer_plant", "Fertilizer plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 2800000.0; buildTicks = 85; lines = 3; storage = 80000.0; upkeep = 950.0
            energyPerLine = 130.0
            staff("operator", 3)
        }
        facility("explosives_plant", "Explosives plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 2200000.0; buildTicks = 80; lines = 2; storage = 20000.0; upkeep = 900.0
            energyPerLine = 70.0
            staff("operator", 3)
            staff("technician", 1)
        }
        facility("coal_power_plant", "Coal power plant", FacilityClass.POWER_PLANT, IndustryFamily.ENERGY) {
            cost = 6000000.0; buildTicks = 120; lines = 3; storage = 120000.0; upkeep = 1700.0
            staff("operator", 4)
            staff("engineer", 1)
        }
        facility("gas_power_plant", "Gas power plant", FacilityClass.POWER_PLANT, IndustryFamily.ENERGY) {
            cost = 4500000.0; buildTicks = 100; lines = 3; storage = 40000.0; upkeep = 1300.0
            staff("operator", 3)
            staff("engineer", 1)
        }
        facility("oil_power_plant", "Oil power plant", FacilityClass.POWER_PLANT, IndustryFamily.ENERGY) {
            cost = 3800000.0; buildTicks = 90; lines = 3; storage = 60000.0; upkeep = 1200.0
            staff("operator", 3)
        }
        facility("nuclear_plant", "Nuclear power plant", FacilityClass.POWER_PLANT, IndustryFamily.ENERGY) {
            cost = 40000000.0; buildTicks = 300; lines = 2; storage = 30000.0; upkeep = 8000.0
            tech = "nuclear_power"
            staff("operator", 12)
            staff("engineer", 4)
        }
        facility("wind_farm", "Wind farm", FacilityClass.POWER_PLANT, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 5500000.0; buildTicks = 110; lines = 4; storage = 5000.0; upkeep = 900.0
            staff("technician", 2)
        }
        facility("solar_farm", "Solar farm", FacilityClass.POWER_PLANT, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 6500000.0; buildTicks = 100; lines = 4; storage = 5000.0; upkeep = 800.0
            staff("technician", 2)
        }
        facility("hydro_plant", "Hydroelectric dam", FacilityClass.POWER_PLANT, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 25000000.0; buildTicks = 240; lines = 2; storage = 5000.0; upkeep = 3000.0
            staff("operator", 4)
            staff("engineer", 2)
        }
        facility("geothermal_plant", "Geothermal plant", FacilityClass.POWER_PLANT, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 14000000.0; buildTicks = 180; lines = 2; storage = 10000.0; upkeep = 2200.0
            staff("operator", 4)
            staff("engineer", 1)
        }
        facility("biomass_plant", "Biomass power plant", FacilityClass.POWER_PLANT, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 4800000.0; buildTicks = 100; lines = 3; storage = 60000.0; upkeep = 1300.0
            staff("operator", 3)
        }
        facility("biofuel_plant", "Biofuel plant", FacilityClass.REFINERY, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 3600000.0; buildTicks = 90; lines = 2; storage = 60000.0; upkeep = 1100.0
            energyPerLine = 90.0
            staff("operator", 3)
            staff("technician", 1)
        }
        facility("battery_plant", "Battery plant", FacilityClass.FACTORY, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 8500000.0; buildTicks = 130; lines = 3; storage = 40000.0; upkeep = 2300.0
            energyPerLine = 220.0
            tech = "battery_manufacturing"
            staff("operator", 4)
            staff("engineer", 2)
        }

        recipe("pump_crude", "Pump crude oil", "oil_gas", "oil_well", 10) {
            input("explosives", 6.0)
            input("water", 100.0)
            output("crude_oil", 120.0)
            labor("operator", 6)
            energy = 140.0
        }
        recipe("extract_gas", "Extract natural gas", "oil_gas", "gas_well", 10) {
            input("water", 80.0)
            output("natural_gas", 745.0)
            labor("operator", 5)
            energy = 120.0
        }
        recipe("refine_gasoline", "Refine fuels", "oil_gas", "oil_refinery", 4) {
            input("crude_oil", 600.0)
            input("hydrogen", 20.0)
            output("gasoline", 1185.0)
            output("diesel", 1045.0)
            output("kerosene", 490.0)
            output("naphtha", 280.0)
            output("bitumen", 280.0)
            output("propane", 210.0)
            waste("chemical_waste", 20.0)
            labor("operator", 10)
            labor("engineer", 2)
            energy = 380.0
        }
        recipe("blend_lubricants", "Blend lubricants", "oil_gas", "oil_refinery", 3) {
            input("naphtha", 80.0)
            input("bitumen", 20.0)
            output("lubricants", 395.0)
            labor("operator", 4)
            energy = 100.0
        }
        recipe("crack_ethylene", "Produce polyethylene", "chemicals", "petrochemical_plant", 4) {
            input("naphtha", 250.0)
            input("natural_gas", 80.0)
            output("polyethylene", 410.0)
            waste("chemical_waste", 25.0)
            labor("operator", 6)
            labor("engineer", 1)
            energy = 260.0
        }
        recipe("make_polypropylene", "Produce polypropylene", "chemicals", "petrochemical_plant", 4) {
            input("naphtha", 240.0)
            input("propane", 40.0)
            output("polypropylene", 360.0)
            waste("chemical_waste", 25.0)
            labor("operator", 6)
            labor("engineer", 1)
            energy = 250.0
        }
        recipe("make_pvc", "Produce PVC resin", "chemicals", "petrochemical_plant", 4) {
            input("naphtha", 150.0)
            input("chlorine", 90.0)
            output("pvc_resin", 350.0)
            waste("chemical_waste", 20.0)
            labor("operator", 6)
            energy = 220.0
        }
        recipe("make_synthetic_rubber", "Produce synthetic rubber", "chemicals", "petrochemical_plant", 5) {
            input("naphtha", 200.0)
            input("sulfuric_acid", 20.0)
            output("synthetic_rubber", 265.0)
            waste("chemical_waste", 22.0)
            labor("operator", 5)
            energy = 230.0
        }
        recipe("granulate_plastic", "Compound plastic granules", "chemicals", "chemical_plant", 2) {
            input("polyethylene", 90.0)
            input("polypropylene", 60.0)
            input("pigments", 6.0)
            output("plastic_granules", 210.0)
            labor("operator", 3)
            energy = 90.0
        }
        recipe("make_solvents", "Make solvents", "chemicals", "chemical_plant", 3) {
            input("naphtha", 120.0)
            output("solvents", 215.0)
            waste("chemical_waste", 10.0)
            labor("operator", 3)
            energy = 100.0
        }
        recipe("make_sulfuric_acid", "Make sulfuric acid", "chemicals", "chemical_plant", 2) {
            input("coal", 40.0)
            input("water", 100.0)
            input("industrial_gases", 30.0)
            output("sulfuric_acid", 110.0)
            waste("chemical_waste", 8.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("electrolyze_brine", "Electrolyze brine", "chemicals", "chemical_plant", 2) {
            input("brine", 900.0)
            input("salt", 40.0)
            output("caustic_soda", 95.0)
            output("chlorine", 80.0)
            labor("operator", 3)
            energy = 250.0
        }
        recipe("make_ammonia", "Synthesize ammonia", "chemicals", "fertilizer_plant", 3) {
            input("natural_gas", 160.0)
            input("industrial_gases", 60.0)
            output("ammonia", 140.0)
            labor("operator", 4)
            energy = 220.0
        }
        recipe("make_nitric_acid", "Make nitric acid", "chemicals", "chemical_plant", 2) {
            input("ammonia", 60.0)
            input("water", 100.0)
            output("nitric_acid", 135.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("separate_gases", "Separate industrial gases", "chemicals", "chemical_plant", 2) {
            input("water", 40.0)
            output("industrial_gases", 145.0)
            labor("operator", 3)
            energy = 240.0
        }
        recipe("make_hydrogen", "Produce hydrogen", "chemicals", "chemical_plant", 2) {
            input("natural_gas", 90.0)
            input("water", 60.0)
            output("hydrogen", 70.0)
            labor("operator", 3)
            energy = 120.0
        }
        recipe("mix_fertilizer", "Mix fertilizer", "chemicals", "fertilizer_plant", 2) {
            input("ammonia", 40.0)
            input("phosphate_rock", 60.0)
            input("packaging", 20.0)
            output("fertilizer", 195.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("make_compound_fertilizer", "Make compound fertilizer", "chemicals", "fertilizer_plant", 2) {
            input("ammonia", 35.0)
            input("nitric_acid", 15.0)
            input("phosphate_rock", 55.0)
            input("packaging", 20.0)
            output("fertilizer_compound", 165.0)
            labor("operator", 3)
            energy = 75.0
        }
        recipe("mine_phosphate", "Mine phosphate rock", "chemicals", "fertilizer_plant", 6) {
            input("diesel", 70.0)
            input("water", 80.0)
            output("phosphate_rock", 255.0)
            labor("operator", 5)
            energy = 70.0
        }
        recipe("make_explosives", "Make explosives", "chemicals", "explosives_plant", 4) {
            input("nitric_acid", 50.0)
            input("ammonia", 30.0)
            input("packaging", 15.0)
            output("explosives", 100.0)
            waste("chemical_waste", 5.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 60.0
            defectPpm = 40000
        }
        recipe("make_pigments", "Make pigments", "chemicals", "chemical_plant", 3) {
            input("sulfuric_acid", 30.0)
            input("limestone", 40.0)
            output("pigments", 40.0)
            waste("chemical_waste", 10.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("make_paint", "Mix paint", "chemicals", "chemical_plant", 2) {
            input("pigments", 20.0)
            input("solvents", 40.0)
            input("polypropylene", 15.0)
            input("packaging", 20.0)
            output("paint", 160.0)
            labor("operator", 3)
            energy = 45.0
        }
        recipe("make_epoxy", "Make epoxy resin", "chemicals", "chemical_plant", 4) {
            input("solvents", 70.0)
            input("chlorine", 30.0)
            input("caustic_soda", 20.0)
            output("resins_epoxy", 80.0)
            waste("chemical_waste", 14.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 90.0
        }
        recipe("make_adhesives", "Make adhesives", "chemicals", "chemical_plant", 2) {
            input("resins_epoxy", 20.0)
            input("solvents", 30.0)
            input("packaging", 12.0)
            output("adhesives", 75.0)
            labor("operator", 2)
            energy = 40.0
        }
        recipe("make_pesticides", "Make pesticides", "chemicals", "chemical_plant", 4) {
            input("solvents", 50.0)
            input("chlorine", 30.0)
            input("packaging", 15.0)
            output("pesticides", 85.0)
            waste("chemical_waste", 12.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 70.0
        }
        recipe("make_detergent_base", "Make detergent base", "chemicals", "chemical_plant", 2) {
            input("caustic_soda", 40.0)
            input("glycerin", 10.0)
            input("water", 80.0)
            output("detergent_base", 90.0)
            labor("operator", 3)
            energy = 50.0
        }
        recipe("make_activated_carbon", "Make activated carbon", "chemicals", "chemical_plant", 3) {
            input("coal", 120.0)
            output("activated_carbon", 70.0)
            labor("operator", 2)
            energy = 110.0
        }
        recipe("burn_coal", "Coal power", "power", "coal_power_plant", 1) {
            input("coal", 700.0)
            input("water", 250.0)
            output("electricity", 1555.0)
            waste("scrap", 1.0)
            labor("operator", 8)
            labor("engineer", 1)
        }
        recipe("burn_gas", "Gas power", "power", "gas_power_plant", 1) {
            input("natural_gas", 650.0)
            input("water", 120.0)
            output("electricity", 950.0)
            labor("operator", 6)
            labor("engineer", 1)
        }
        recipe("burn_oil", "Oil power", "power", "oil_power_plant", 1) {
            input("diesel", 420.0)
            input("water", 100.0)
            output("electricity", 780.0)
            labor("operator", 5)
        }
        recipe("fission", "Nuclear power", "power", "nuclear_plant", 1) {
            input("enriched_uranium", 1.2)
            input("water", 400.0)
            output("electricity", 1135.0)
            labor("operator", 30)
            labor("engineer", 8)
            tech = "nuclear_power"
        }
        recipe("enrich_uranium", "Enrich uranium", "power", "chemical_plant", 12) {
            input("uranium_ore", 400.0)
            input("industrial_gases", 100.0)
            output("enriched_uranium", 70.0)
            labor("operator", 6)
            labor("engineer", 3)
            energy = 500.0
            tech = "nuclear_power"
        }
        recipe("burn_biomass", "Biomass power", "power", "biomass_plant", 1) {
            input("biomass", 900.0)
            input("water", 120.0)
            output("electricity", 1220.0)
            labor("operator", 6)
        }
        recipe("wind_power", "Wind power", "renewables", "wind_farm", 1) {
            input("spare_parts", 4.0)
            output("electricity", 90.0)
            labor("technician", 4)
        }
        recipe("solar_power", "Solar power", "renewables", "solar_farm", 1) {
            input("spare_parts", 3.0)
            output("electricity", 85.0)
            labor("technician", 4)
        }
        recipe("hydro_power", "Hydroelectric power", "renewables", "hydro_plant", 1) {
            input("spare_parts", 5.0)
            output("electricity", 260.0)
            labor("operator", 10)
            labor("engineer", 3)
        }
        recipe("geothermal_power", "Geothermal power", "renewables", "geothermal_plant", 1) {
            input("spare_parts", 5.0)
            input("water", 300.0)
            output("electricity", 295.0)
            labor("operator", 10)
            labor("engineer", 2)
        }
        recipe("grow_biomass", "Cultivate biomass", "renewables", "biomass_plant", 30) {
            input("fertilizer", 30.0)
            input("water", 400.0)
            output("biomass", 820.0)
            labor("farmhand", 3)
        }
        recipe("make_biodiesel", "Make biodiesel", "renewables", "biofuel_plant", 3) {
            input("vegetable_oil", 120.0)
            input("ethanol", 20.0)
            output("biodiesel", 610.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 60.0
        }
        recipe("ferment_ethanol", "Ferment ethanol", "renewables", "biofuel_plant", 5) {
            input("corn", 400.0)
            input("water", 300.0)
            output("ethanol", 470.0)
            labor("operator", 3)
            energy = 90.0
        }
        recipe("make_glycerin", "Refine glycerin", "chemicals", "chemical_plant", 3) {
            input("vegetable_oil", 100.0)
            input("caustic_soda", 15.0)
            output("glycerin", 170.0)
            labor("operator", 2)
            energy = 50.0
        }
        recipe("make_solar_cells", "Make solar cells", "renewables", "battery_plant", 4) {
            input("silica_sand", 200.0)
            input("industrial_gases", 30.0)
            input("copper_wire", 20.0)
            output("solar_cells", 90.0)
            labor("operator", 5)
            labor("engineer", 1)
            energy = 260.0
        }
        recipe("make_battery_cells", "Make battery cells", "renewables", "battery_plant", 4) {
            input("lithium_carbonate", 30.0)
            input("nickel_ingot", 25.0)
            input("copper_wire", 20.0)
            input("aluminum_sheet", 25.0)
            output("lithium_battery_cells", 190.0)
            labor("operator", 5)
            labor("engineer", 2)
            energy = 280.0
            tech = "battery_manufacturing"
        }

        tech("nuclear_power", "Nuclear power", TechCategory.ENERGY, 4, 3000000.0, 300) {
            requires("steam_turbines")
            unlocksRecipe("fission", "enrich_uranium")
            unlocksFacility("nuclear_plant")
        }
        tech("steam_turbines", "High-efficiency turbines", TechCategory.ENERGY, 2, 500000.0, 90) {
            effect(EffectKind.ENERGY_SAVINGS, 0.04, industry = "power")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "power")
        }
        tech("battery_manufacturing", "Battery manufacturing", TechCategory.ENERGY, 3, 1800000.0, 180) {
            requires("steam_turbines")
            unlocksRecipe("make_battery_cells")
            unlocksFacility("battery_plant")
        }
        tech("catalytic_cracking", "Catalytic cracking", TechCategory.MATERIALS, 2, 600000.0, 100) {
            effect(EffectKind.YIELD_BONUS, 0.06, industry = "oil_gas")
        }
        tech("green_chemistry", "Green chemistry", TechCategory.MATERIALS, 3, 900000.0, 120) {
            requires("catalytic_cracking")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "chemicals")
        }
        tech("smart_grid", "Smart grid", TechCategory.DIGITAL, 3, 1100000.0, 140) {
            requires("steam_turbines")
            effect(EffectKind.YIELD_BONUS, 0.04, industry = "renewables")
        }
    }
}

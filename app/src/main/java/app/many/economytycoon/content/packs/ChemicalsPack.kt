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

object ChemicalsPack {
    val pack: ContentPack = contentPack("chemicals", 1, 0) {
        industry("inorganic_chemicals", "Inorganic chemicals", IndustryFamily.CHEMICALS, "Sulfur, potash, soda ash, acids and oxidisers")
        industry("organic_chemicals", "Organic chemicals", IndustryFamily.CHEMICALS, "Methanol chain, phenol, styrene and acrylics")
        industry("engineering_polymers", "Engineering polymers", IndustryFamily.CHEMICALS, "Polystyrene, PET, nylon, polycarbonate and silicone")
        industry("coatings_and_sealants", "Coatings and sealants", IndustryFamily.CHEMICALS, "Coatings, sealants, treatment chemicals and consumer formulations")

        good("sulfur_ore", "Sulfur ore", GoodCategory.RAW, 22.0, VolatilityClass.STABLE)
        good("potash_ore", "Potash ore", GoodCategory.RAW, 28.0, VolatilityClass.STABLE)
        good("sulfur", "Sulfur", GoodCategory.INTERMEDIATE, 34.0, VolatilityClass.STABLE)
        good("potash", "Potash", GoodCategory.INTERMEDIATE, 36.0, VolatilityClass.STABLE)
        good("soda_ash", "Soda ash", GoodCategory.INTERMEDIATE, 30.0, VolatilityClass.STABLE)
        good("hydrochloric_acid", "Hydrochloric acid", GoodCategory.INTERMEDIATE, 22.0, VolatilityClass.STABLE)
        good("hydrogen_peroxide", "Hydrogen peroxide", GoodCategory.INTERMEDIATE, 58.0, VolatilityClass.STABLE)
        good("sodium_hypochlorite", "Sodium hypochlorite", GoodCategory.INTERMEDIATE, 18.0, VolatilityClass.STABLE)
        good("zinc_oxide", "Zinc oxide", GoodCategory.INTERMEDIATE, 70.0, VolatilityClass.STABLE)
        good("methanol", "Methanol", GoodCategory.INTERMEDIATE, 31.0)
        good("formaldehyde", "Formaldehyde", GoodCategory.INTERMEDIATE, 44.0)
        good("acetic_acid", "Acetic acid", GoodCategory.INTERMEDIATE, 40.0)
        good("urea", "Urea", GoodCategory.INTERMEDIATE, 29.0)
        good("phenol", "Phenol", GoodCategory.INTERMEDIATE, 82.0)
        good("styrene", "Styrene", GoodCategory.INTERMEDIATE, 64.0)
        good("acrylic_acid", "Acrylic acid", GoodCategory.INTERMEDIATE, 77.0)
        good("uf_resin", "Urea-formaldehyde resin", GoodCategory.INTERMEDIATE, 45.0)
        good("polystyrene", "Polystyrene resin", GoodCategory.INTERMEDIATE, 66.0)
        good("pet_resin", "PET resin", GoodCategory.INTERMEDIATE, 58.0)
        good("nylon_resin", "Nylon resin", GoodCategory.INTERMEDIATE, 96.0)
        good("polycarbonate", "Polycarbonate resin", GoodCategory.INTERMEDIATE, 120.0)
        good("silicone", "Silicone polymer", GoodCategory.INTERMEDIATE, 210.0)
        good("acrylic_resin", "Acrylic resin", GoodCategory.INTERMEDIATE, 74.0)
        good("industrial_coating", "Industrial coating", GoodCategory.INDUSTRIAL, 62.0, VolatilityClass.STABLE)
        good("silicone_sealant", "Silicone sealant", GoodCategory.INDUSTRIAL, 55.0, VolatilityClass.STABLE)
        good("water_treatment_chem", "Water treatment chemicals", GoodCategory.INDUSTRIAL, 40.0, VolatilityClass.STABLE)
        good("fire_retardant", "Fire retardant", GoodCategory.INDUSTRIAL, 75.0, VolatilityClass.STABLE)
        good("herbicide", "Herbicide", GoodCategory.INDUSTRIAL, 70.0, VolatilityClass.STABLE)
        good("refrigerant_gas", "Refrigerant gas", GoodCategory.INDUSTRIAL, 95.0, VolatilityClass.STABLE)
        good("urea_fertilizer", "Urea fertilizer", GoodCategory.INDUSTRIAL, 27.0, VolatilityClass.STABLE)
        good("potash_fertilizer", "Potash fertilizer", GoodCategory.INDUSTRIAL, 33.0, VolatilityClass.STABLE)
        good("household_bleach", "Household bleach", GoodCategory.CONSUMER, 4.0, VolatilityClass.STABLE)
        good("antifreeze", "Antifreeze", GoodCategory.CONSUMER, 12.0, VolatilityClass.STABLE)
        good("pool_chlorine_tablets", "Pool chlorine tablets", GoodCategory.CONSUMER, 18.0, VolatilityClass.STABLE)

        role("chemist", "Process chemist", SkillBand.SPECIALIST, 140.0, 120)

        machine("catalytic_reactor", "Catalytic reactor", 900000.0, throughput = 1.2, wearPerBatchPpm = 550, maintenance = 80.0, tech = "organic_synthesis")
        machine("extrusion_line", "Polymer extrusion line", 650000.0, throughput = 1.25, wearPerBatchPpm = 450, maintenance = 55.0, laborSavings = 0.08, tech = "polymer_engineering")
        machine("spray_dryer", "Spray dryer", 420000.0, throughput = 1.1, wearPerBatchPpm = 400, maintenance = 35.0, tech = "specialty_formulation")

        facility("sulfur_mine", "Sulfur mine", FacilityClass.MINE, IndustryFamily.CHEMICALS) {
            cost = 1400000.0; buildTicks = 70; lines = 3; storage = 60000.0; upkeep = 520.0
            energyPerLine = 30.0
            extracts = "sulfur_ore"
            staff("miner", 2)
        }
        facility("potash_mine", "Potash mine", FacilityClass.MINE, IndustryFamily.CHEMICALS) {
            cost = 1600000.0; buildTicks = 80; lines = 3; storage = 60000.0; upkeep = 560.0
            energyPerLine = 32.0
            extracts = "potash_ore"
            staff("miner", 2)
        }
        facility("inorganic_plant", "Inorganic chemicals plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 5400000.0; buildTicks = 100; lines = 3; storage = 25000.0; upkeep = 1700.0
            energyPerLine = 200.0
            staff("operator", 3)
            staff("chemist", 1)
        }
        facility("organic_synthesis_plant", "Organic synthesis plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 9200000.0; buildTicks = 130; lines = 3; storage = 50000.0; upkeep = 2600.0
            energyPerLine = 280.0
            tech = "organic_synthesis"
            staff("operator", 4)
            staff("chemist", 2)
        }
        facility("polymer_plant", "Polymer plant", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 12000000.0; buildTicks = 150; lines = 3; storage = 25000.0; upkeep = 3300.0
            energyPerLine = 340.0
            tech = "polymer_engineering"
            staff("operator", 5)
            staff("chemist", 2)
        }
        facility("formulation_works", "Formulation works", FacilityClass.FACTORY, IndustryFamily.CHEMICALS) {
            cost = 4800000.0; buildTicks = 90; lines = 4; storage = 25000.0; upkeep = 1500.0
            energyPerLine = 140.0
            staff("operator", 4)
            staff("chemist", 1)
        }

        recipe("mine_sulfur", "Mine sulfur ore", "inorganic_chemicals", "sulfur_mine", 8) {
            input("diesel", 1590.0)
            input("explosives", 225.0)
            input("water", 1680.0)
            output("sulfur_ore", 2640.0)
            labor("miner", 7)
            energy = 1026.0
        }
        recipe("mine_potash", "Mine potash ore", "inorganic_chemicals", "potash_mine", 9) {
            input("diesel", 1790.0)
            input("explosives", 280.0)
            input("water", 1990.0)
            output("potash_ore", 2460.0)
            labor("miner", 7)
            labor("operator", 1)
            energy = 1193.0
        }
        recipe("refine_sulfur", "Refine sulfur", "inorganic_chemicals", "inorganic_plant", 3) {
            input("sulfur_ore", 1800.0)
            input("natural_gas", 210.0)
            input("water", 900.0)
            output("sulfur", 1840.0)
            labor("operator", 3)
            energy = 1052.0
        }
        recipe("process_potash", "Process potash", "inorganic_chemicals", "inorganic_plant", 3) {
            input("potash_ore", 1550.0)
            input("water", 1430.0)
            output("potash", 1740.0)
            labor("operator", 3)
            energy = 716.0
        }
        recipe("make_soda_ash", "Make soda ash", "inorganic_chemicals", "inorganic_plant", 4) {
            input("salt", 2870.0)
            input("limestone", 2230.0)
            input("ammonia", 380.0)
            output("soda_ash", 2780.0)
            labor("operator", 3)
            labor("chemist", 1)
            energy = 1958.0
        }
        recipe("make_hydrochloric_acid", "Make hydrochloric acid", "inorganic_chemicals", "inorganic_plant", 2) {
            input("chlorine", 595.0)
            input("hydrogen", 180.0)
            input("water", 890.0)
            output("hydrochloric_acid", 1760.0)
            labor("operator", 3)
            energy = 743.0
        }
        recipe("make_hydrogen_peroxide", "Make hydrogen peroxide", "inorganic_chemicals", "inorganic_plant", 4) {
            input("hydrogen", 525.0)
            input("industrial_gases", 700.0)
            input("solvents", 140.0)
            input("water", 700.0)
            output("hydrogen_peroxide", 1200.0)
            labor("operator", 3)
            labor("chemist", 1)
            energy = 1556.0
        }
        recipe("make_hypochlorite", "Make sodium hypochlorite", "inorganic_chemicals", "inorganic_plant", 2) {
            input("caustic_soda", 470.0)
            input("chlorine", 440.0)
            input("water", 1250.0)
            output("sodium_hypochlorite", 2320.0)
            labor("operator", 3)
            energy = 627.0
        }
        recipe("make_zinc_oxide", "Burn zinc oxide", "inorganic_chemicals", "inorganic_plant", 3) {
            input("zinc_ingot", 375.0)
            input("industrial_gases", 250.0)
            output("zinc_oxide", 830.0)
            labor("operator", 3)
            energy = 1242.0
        }
        recipe("contact_sulfuric_acid", "Make sulfuric acid from sulfur", "inorganic_chemicals", "inorganic_plant", 3) {
            input("sulfur", 905.0)
            input("industrial_gases", 675.0)
            input("water", 1350.0)
            output("sulfuric_acid", 2740.0)
            labor("operator", 3)
            energy = 1355.0
        }
        recipe("make_methanol", "Make methanol", "organic_chemicals", "organic_synthesis_plant", 4) {
            input("natural_gas", 6400.0)
            input("water", 4260.0)
            input("industrial_gases", 710.0)
            output("methanol", 4020.0)
            labor("operator", 4)
            labor("chemist", 1)
            machine("catalytic_reactor")
            energy = 2927.0
        }
        recipe("make_formaldehyde", "Make formaldehyde", "organic_chemicals", "organic_synthesis_plant", 3) {
            input("methanol", 1350.0)
            input("industrial_gases", 565.0)
            output("formaldehyde", 1960.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 1991.0
        }
        recipe("make_acetic_acid", "Make acetic acid", "organic_chemicals", "organic_synthesis_plant", 4) {
            input("methanol", 1660.0)
            input("industrial_gases", 970.0)
            output("acetic_acid", 2870.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 2655.0
        }
        recipe("make_urea", "Synthesize urea", "organic_chemicals", "organic_synthesis_plant", 3) {
            input("ammonia", 1010.0)
            input("industrial_gases", 675.0)
            output("urea", 3210.0)
            labor("operator", 4)
            energy = 2191.0
        }
        recipe("make_phenol", "Make phenol", "organic_chemicals", "organic_synthesis_plant", 5) {
            input("naphtha", 1520.0)
            input("propane", 650.0)
            input("sulfuric_acid", 130.0)
            output("phenol", 1630.0)
            waste("chemical_waste", 260.0)
            labor("operator", 4)
            labor("chemist", 2)
            machine("catalytic_reactor")
            energy = 3016.0
        }
        recipe("make_styrene", "Make styrene", "organic_chemicals", "organic_synthesis_plant", 4) {
            input("naphtha", 1390.0)
            input("industrial_gases", 350.0)
            output("styrene", 1790.0)
            waste("chemical_waste", 140.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 2615.0
        }
        recipe("make_acrylic_acid", "Make acrylic acid", "organic_chemicals", "organic_synthesis_plant", 4) {
            input("propane", 3030.0)
            input("industrial_gases", 1300.0)
            input("sulfuric_acid", 215.0)
            output("acrylic_acid", 1390.0)
            waste("chemical_waste", 435.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 2429.0
        }
        recipe("make_uf_resin", "Cook urea-formaldehyde resin", "organic_chemicals", "organic_synthesis_plant", 3) {
            input("urea", 925.0)
            input("formaldehyde", 835.0)
            output("uf_resin", 2070.0)
            labor("operator", 4)
            energy = 1621.0
        }
        recipe("polymerize_styrene", "Polymerize polystyrene", "engineering_polymers", "polymer_plant", 3) {
            input("styrene", 1110.0)
            input("pigments", 25.0)
            output("polystyrene", 1610.0)
            labor("operator", 5)
            energy = 1596.0
        }
        recipe("make_pet", "Make PET resin", "engineering_polymers", "polymer_plant", 4) {
            input("naphtha", 1210.0)
            input("acetic_acid", 605.0)
            input("ethanol", 405.0)
            output("pet_resin", 2430.0)
            labor("operator", 5)
            labor("chemist", 1)
            machine("extrusion_line")
            energy = 3028.0
        }
        recipe("make_nylon", "Make nylon resin", "engineering_polymers", "polymer_plant", 5) {
            input("phenol", 745.0)
            input("ammonia", 465.0)
            input("naphtha", 370.0)
            output("nylon_resin", 1710.0)
            labor("operator", 5)
            labor("chemist", 2)
            machine("extrusion_line")
            energy = 3709.0
        }
        recipe("make_polycarbonate", "Make polycarbonate", "engineering_polymers", "polymer_plant", 5) {
            input("phenol", 885.0)
            input("methanol", 440.0)
            input("chlorine", 175.0)
            output("polycarbonate", 1280.0)
            labor("operator", 5)
            labor("chemist", 2)
            energy = 3415.0
            tech = "polymer_engineering"
        }
        recipe("make_silicone", "Make silicone polymer", "engineering_polymers", "polymer_plant", 6) {
            input("silica_sand", 3410.0)
            input("methanol", 1280.0)
            input("coal", 1070.0)
            input("hydrochloric_acid", 255.0)
            output("silicone", 830.0)
            waste("chemical_waste", 425.0)
            labor("operator", 5)
            labor("chemist", 2)
            energy = 3796.0
            tech = "polymer_engineering"
        }
        recipe("make_acrylic_resin", "Make acrylic resin", "engineering_polymers", "polymer_plant", 3) {
            input("acrylic_acid", 725.0)
            input("methanol", 350.0)
            input("solvents", 115.0)
            output("acrylic_resin", 1430.0)
            labor("operator", 5)
            energy = 1746.0
        }
        recipe("blend_industrial_coating", "Blend industrial coating", "coatings_and_sealants", "formulation_works", 2) {
            input("acrylic_resin", 135.0)
            input("zinc_oxide", 53.0)
            input("pigments", 40.0)
            input("solvents", 80.0)
            output("industrial_coating", 450.0)
            labor("operator", 4)
            machine("spray_dryer")
            energy = 267.0
        }
        recipe("compound_sealant", "Compound silicone sealant", "coatings_and_sealants", "formulation_works", 2) {
            input("silicone", 95.0)
            input("pigments", 10.5)
            input("plastic_granules", 32.0)
            output("silicone_sealant", 545.0)
            labor("operator", 4)
            energy = 184.0
        }
        recipe("blend_water_treatment", "Blend water treatment chemicals", "coatings_and_sealants", "formulation_works", 2) {
            input("soda_ash", 230.0)
            input("hydrochloric_acid", 230.0)
            input("sodium_hypochlorite", 230.0)
            input("hydrogen_peroxide", 92.0)
            output("water_treatment_chem", 805.0)
            labor("operator", 4)
            energy = 577.0
        }
        recipe("mix_fire_retardant", "Mix fire retardant", "coatings_and_sealants", "formulation_works", 2) {
            input("phosphate_rock", 340.0)
            input("ammonia", 115.0)
            input("uf_resin", 85.0)
            input("water", 285.0)
            output("fire_retardant", 350.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 427.0
        }
        recipe("formulate_herbicide", "Formulate herbicide", "coatings_and_sealants", "formulation_works", 3) {
            input("urea", 305.0)
            input("solvents", 205.0)
            input("sulfur", 155.0)
            input("water", 510.0)
            output("herbicide", 560.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 849.0
        }
        recipe("fill_refrigerant", "Fill refrigerant gas", "coatings_and_sealants", "formulation_works", 2) {
            input("hydrochloric_acid", 185.0)
            input("industrial_gases", 455.0)
            input("solvents", 91.0)
            output("refrigerant_gas", 290.0)
            labor("operator", 4)
            energy = 624.0
        }
        recipe("prill_urea_fertilizer", "Prill urea fertilizer", "coatings_and_sealants", "formulation_works", 2) {
            input("urea", 715.0)
            input("limestone", 180.0)
            input("packaging", 60.0)
            output("urea_fertilizer", 1300.0)
            labor("operator", 4)
            energy = 840.0
        }
        recipe("granulate_potash_fertilizer", "Granulate potash fertilizer", "coatings_and_sealants", "formulation_works", 2) {
            input("potash", 580.0)
            input("phosphate_rock", 135.0)
            input("packaging", 45.0)
            output("potash_fertilizer", 1070.0)
            labor("operator", 4)
            energy = 671.0
        }
        recipe("pack_household_bleach", "Package household bleach", "coatings_and_sealants", "formulation_works", 2) {
            input("sodium_hypochlorite", 300.0)
            input("plastic_granules", 200.0)
            input("packaging", 200.0)
            output("household_bleach", 6750.0)
            labor("operator", 4)
            energy = 598.0
        }
        recipe("pack_antifreeze", "Package antifreeze", "coatings_and_sealants", "formulation_works", 2) {
            input("methanol", 270.0)
            input("water", 355.0)
            input("plastic_granules", 135.0)
            input("packaging", 135.0)
            output("antifreeze", 2250.0)
            labor("operator", 4)
            energy = 598.0
        }
        recipe("press_pool_tablets", "Press pool chlorine tablets", "coatings_and_sealants", "formulation_works", 2) {
            input("sodium_hypochlorite", 455.0)
            input("soda_ash", 150.0)
            input("packaging", 150.0)
            output("pool_chlorine_tablets", 1290.0)
            labor("operator", 4)
            energy = 495.0
        }

        tech("sulfur_recovery", "Sulfur recovery", TechCategory.MATERIALS, 2, 520000.0, 80) {
            requires("deep_mining")
            effect(EffectKind.YIELD_BONUS, 0.06, industry = "inorganic_chemicals")
        }
        tech("organic_synthesis", "Organic synthesis", TechCategory.MATERIALS, 3, 2100000.0, 160) {
            requires("catalytic_cracking")
            unlocksFacility("organic_synthesis_plant")
            unlocksMachine("catalytic_reactor")
        }
        tech("polymer_engineering", "Polymer engineering", TechCategory.MATERIALS, 3, 3400000.0, 200) {
            requires("organic_synthesis")
            unlocksRecipe("make_silicone", "make_polycarbonate")
            unlocksFacility("polymer_plant")
            unlocksMachine("extrusion_line")
        }
        tech("specialty_formulation", "Specialty formulation", TechCategory.PRODUCTION, 2, 900000.0, 110) {
            requires("catalytic_cracking")
            unlocksMachine("spray_dryer")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "coatings_and_sealants")
        }
        tech("process_intensification", "Process intensification", TechCategory.PRODUCTION, 3, 1900000.0, 150) {
            requires("organic_synthesis", "green_chemistry")
            effect(EffectKind.ENERGY_SAVINGS, 0.08, industry = "organic_chemicals")
            effect(EffectKind.YIELD_BONUS, 0.04, industry = "inorganic_chemicals")
        }
        tech("chemical_safety_systems", "Chemical safety systems", TechCategory.MANAGEMENT, 2, 600000.0, 90) {
            effect(EffectKind.QUALITY_BONUS, 0.15, industry = "organic_chemicals")
            effect(EffectKind.QUALITY_BONUS, 0.1, industry = "engineering_polymers")
        }

        demand("household_bleach", 380L, 400L, 410L, 0.4, incomeElasticity = 0.2)
        demand("antifreeze", 30L, 60L, 80L, 0.5, incomeElasticity = 0.5, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 2.0, app.many.economytycoon.core.Season.SUMMER to 0.3))
        demand("pool_chlorine_tablets", 5L, 25L, 60L, 0.8, incomeElasticity = 1.2, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 2.2, app.many.economytycoon.core.Season.WINTER to 0.2), nonConsumers = 0.55)
        demand("industrial_coating", 20L, 28L, 30L, 0.6, incomeElasticity = 0.5)
        demand("silicone_sealant", 18L, 24L, 26L, 0.7, incomeElasticity = 0.6)
        demand("water_treatment_chem", 40L, 44L, 46L, 0.3, incomeElasticity = 0.1)
        demand("fire_retardant", 8L, 12L, 14L, 0.5, incomeElasticity = 0.3)
        demand("herbicide", 14L, 18L, 20L, 0.6, incomeElasticity = 0.4, seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 1.8, app.many.economytycoon.core.Season.WINTER to 0.2))
        demand("refrigerant_gas", 6L, 10L, 12L, 0.5, incomeElasticity = 0.4, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.6))
        demand("urea_fertilizer", 30L, 34L, 36L, 0.7, incomeElasticity = 0.3, substitutes = listOf("fertilizer"), seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 2.0, app.many.economytycoon.core.Season.WINTER to 0.2))
        demand("potash_fertilizer", 22L, 26L, 28L, 0.7, incomeElasticity = 0.3, substitutes = listOf("fertilizer"), seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 2.0, app.many.economytycoon.core.Season.WINTER to 0.2))
    }
}

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

object PharmaceuticalsPack {
    val pack: ContentPack = contentPack("pharmaceuticals", 1, 0) {
        industry("biologics", "Biologics", IndustryFamily.PHARMACEUTICALS, "Fermentation, protein purification and injectable biologics")
        industry("specialty_pharma", "Specialty pharmaceuticals", IndustryFamily.PHARMACEUTICALS, "Botanicals, hormones and branded formulations")
        industry("pharma_packaging", "Pharmaceutical packaging", IndustryFamily.PHARMACEUTICALS, "Sterile containers, blister packs and cold-chain shippers")

        good("medicinal_herbs", "Medicinal herbs", GoodCategory.RAW, 38.0, spoilagePpm = 4000)
        good("herbal_extract", "Herbal extract", GoodCategory.INTERMEDIATE, 120.0)
        good("culture_media", "Culture media", GoodCategory.INTERMEDIATE, 85.0)
        good("fermentation_broth", "Fermentation broth", GoodCategory.INTERMEDIATE, 150.0)
        good("purified_protein", "Purified protein", GoodCategory.INTERMEDIATE, 900.0)
        good("antibody_bulk", "Antibody bulk", GoodCategory.INTERMEDIATE, 4200.0, weight = 3000, volume = 4000)
        good("insulin_bulk", "Insulin bulk", GoodCategory.INTERMEDIATE, 2600.0, weight = 3000, volume = 4000)
        good("peptide_api", "Peptide active ingredient", GoodCategory.INTERMEDIATE, 1800.0)
        good("steroid_api", "Steroid active ingredient", GoodCategory.INTERMEDIATE, 1500.0)
        good("sterile_diluent", "Sterile diluent", GoodCategory.INTERMEDIATE, 25.0)
        good("adjuvant", "Vaccine adjuvant", GoodCategory.INTERMEDIATE, 700.0)
        good("gelatin", "Pharmaceutical gelatin", GoodCategory.INTERMEDIATE, 40.0)
        good("capsule_shells", "Capsule shells", GoodCategory.INTERMEDIATE, 14.0)
        good("sterile_vials", "Sterile vials", GoodCategory.INTERMEDIATE, 9.0)
        good("prefilled_syringes", "Prefilled syringes", GoodCategory.INTERMEDIATE, 7.0)
        good("blister_packs", "Blister packs", GoodCategory.INTERMEDIATE, 3.0)
        good("cold_chain_boxes", "Cold-chain shippers", GoodCategory.INTERMEDIATE, 18.0)
        good("insulin_pens", "Insulin pens", GoodCategory.CONSUMER, 55.0)
        good("mab_therapy", "Antibody therapy dose", GoodCategory.CONSUMER, 900.0)
        good("flu_vaccine", "Seasonal flu vaccine", GoodCategory.CONSUMER, 22.0)
        good("allergy_tablets", "Allergy tablets", GoodCategory.CONSUMER, 9.0)
        good("herbal_supplements", "Herbal supplements", GoodCategory.CONSUMER, 15.0)
        good("cough_syrup", "Cough syrup", GoodCategory.CONSUMER, 8.0)
        good("topical_ointment", "Topical ointment", GoodCategory.CONSUMER, 7.0)
        good("hormone_therapy", "Hormone therapy", GoodCategory.CONSUMER, 60.0)
        good("eye_drops", "Eye drops", GoodCategory.CONSUMER, 11.0)
        good("antiviral_course", "Antiviral course", GoodCategory.CONSUMER, 85.0)

        role("biotechnologist", "Biotechnologist", SkillBand.EXPERT, 230.0, 300)
        role("lab_technician", "Laboratory technician", SkillBand.SKILLED, 110.0, 60)

        machine("bioreactor", "Stainless bioreactor", 1200000.0, throughput = 1.25, wearPerBatchPpm = 600, maintenance = 120.0, tech = "biotech_fermentation")
        machine("lyophilizer", "Freeze dryer", 900000.0, maintenance = 90.0, tech = "aseptic_fill_finish")
        machine("filling_isolator", "Aseptic filling isolator", 700000.0, throughput = 1.15, wearPerBatchPpm = 450, maintenance = 70.0, laborSavings = 0.1, tech = "aseptic_fill_finish")
        machine("blister_line", "Blister packaging line", 350000.0, throughput = 1.3, wearPerBatchPpm = 400, maintenance = 35.0, tech = "continuous_manufacturing")

        facility("herb_farm", "Medicinal herb farm", FacilityClass.FARM, IndustryFamily.PHARMACEUTICALS) {
            cost = 450000.0; buildTicks = 50; lines = 3; storage = 40000.0; upkeep = 180.0
            energyPerLine = 3.0
            staff("farmhand", 2)
        }
        facility("botanical_extraction_lab", "Botanical extraction lab", FacilityClass.LABORATORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 3200000.0; buildTicks = 90; lines = 3; storage = 15000.0; upkeep = 1100.0
            energyPerLine = 120.0
            tech = "botanical_extraction"
            staff("lab_technician", 2)
            staff("chemist", 1)
        }
        facility("fermentation_plant", "Fermentation plant", FacilityClass.FACTORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 12000000.0; buildTicks = 150; lines = 3; storage = 35000.0; upkeep = 3800.0
            energyPerLine = 300.0
            tech = "biotech_fermentation"
            staff("biotechnologist", 2)
            staff("operator", 4)
        }
        facility("biologics_plant", "Biologics plant", FacilityClass.LABORATORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 26000000.0; buildTicks = 200; lines = 2; storage = 15000.0; upkeep = 7500.0
            energyPerLine = 420.0
            tech = "monoclonal_engineering"
            staff("biotechnologist", 4)
            staff("lab_technician", 6)
        }
        facility("fill_finish_plant", "Sterile fill-finish plant", FacilityClass.LABORATORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 16000000.0; buildTicks = 170; lines = 3; storage = 35000.0; upkeep = 4600.0
            energyPerLine = 260.0
            tech = "aseptic_fill_finish"
            staff("lab_technician", 4)
            staff("biotechnologist", 1)
        }
        facility("formulation_plant", "Formulation plant", FacilityClass.FACTORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 8500000.0; buildTicks = 120; lines = 4; storage = 30000.0; upkeep = 2400.0
            energyPerLine = 180.0
            staff("operator", 4)
            staff("lab_technician", 2)
        }
        facility("pharma_packaging_plant", "Pharmaceutical packaging plant", FacilityClass.FACTORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 5200000.0; buildTicks = 100; lines = 4; storage = 40000.0; upkeep = 1500.0
            energyPerLine = 150.0
            staff("operator", 4)
            staff("machinist", 1)
        }

        recipe("grow_medicinal_herbs", "Grow medicinal herbs", "specialty_pharma", "herb_farm", 12) {
            input("seeds", 85.0)
            input("fertilizer", 565.0)
            input("water", 4250.0)
            output("medicinal_herbs", 940.0)
            labor("farmhand", 6)
            energy = 283.0
        }
        recipe("extract_herbs", "Extract herbal actives", "specialty_pharma", "botanical_extraction_lab", 4) {
            input("medicinal_herbs", 570.0)
            input("ethanol", 150.0)
            input("water", 455.0)
            output("herbal_extract", 330.0)
            labor("lab_technician", 3)
            labor("chemist", 1)
            energy = 341.0
        }
        recipe("prepare_adjuvant", "Prepare vaccine adjuvant", "specialty_pharma", "botanical_extraction_lab", 3) {
            input("herbal_extract", 95.0)
            input("excipients", 66.0)
            input("glycerin", 28.0)
            output("adjuvant", 37.0)
            labor("lab_technician", 3)
            energy = 237.0
        }
        recipe("isolate_steroid_api", "Isolate steroid ingredient", "specialty_pharma", "botanical_extraction_lab", 6) {
            input("herbal_extract", 210.0)
            input("solvents", 155.0)
            input("sulfuric_acid", 21.0)
            output("steroid_api", 35.0)
            waste("chemical_waste", 52.0)
            labor("lab_technician", 3)
            labor("chemist", 1)
            energy = 572.0
        }
        recipe("render_gelatin", "Render pharmaceutical gelatin", "specialty_pharma", "botanical_extraction_lab", 3) {
            input("hides", 805.0)
            input("lime", 115.0)
            input("water", 2310.0)
            output("gelatin", 850.0)
            waste("organic_waste", 160.0)
            labor("lab_technician", 2)
            energy = 692.0
        }
        recipe("prepare_culture_media", "Prepare culture media", "biologics", "fermentation_plant", 2) {
            input("sugar", 785.0)
            input("soybeans", 525.0)
            input("water", 2620.0)
            output("culture_media", 770.0)
            labor("operator", 4)
            energy = 786.0
        }
        recipe("ferment_broth", "Ferment cell broth", "biologics", "fermentation_plant", 8) {
            input("culture_media", 1130.0)
            input("industrial_gases", 1130.0)
            input("water", 7500.0)
            output("fermentation_broth", 1520.0)
            labor("biotechnologist", 2)
            labor("operator", 3)
            machine("bioreactor")
            energy = 4865.0
        }
        recipe("purify_protein", "Purify protein", "biologics", "fermentation_plant", 5) {
            input("fermentation_broth", 460.0)
            input("solvents", 230.0)
            input("activated_carbon", 77.0)
            output("purified_protein", 140.0)
            waste("chemical_waste", 105.0)
            labor("biotechnologist", 2)
            labor("lab_technician", 4)
            energy = 920.0
        }
        recipe("produce_antibody_bulk", "Produce antibody bulk", "biologics", "biologics_plant", 12) {
            input("purified_protein", 405.0)
            input("culture_media", 670.0)
            input("excipients", 335.0)
            output("antibody_bulk", 175.0)
            labor("biotechnologist", 4)
            labor("lab_technician", 4)
            energy = 8733.0
            tech = "monoclonal_engineering"
        }
        recipe("produce_insulin_bulk", "Produce insulin bulk", "biologics", "biologics_plant", 8) {
            input("purified_protein", 345.0)
            input("zinc_ingot", 49.0)
            input("glycerin", 150.0)
            output("insulin_bulk", 195.0)
            labor("biotechnologist", 3)
            labor("lab_technician", 4)
            energy = 4931.0
            tech = "monoclonal_engineering"
        }
        recipe("synthesize_peptide_api", "Synthesize peptide ingredient", "biologics", "biologics_plant", 8) {
            input("active_ingredient", 195.0)
            input("solvents", 2590.0)
            input("ethanol", 1300.0)
            output("peptide_api", 300.0)
            waste("chemical_waste", 1040.0)
            labor("biotechnologist", 3)
            labor("lab_technician", 3)
            energy = 11329.0
        }
        recipe("blow_sterile_vials", "Blow sterile vials", "pharma_packaging", "pharma_packaging_plant", 2) {
            input("glass", 500.0)
            input("industrial_gases", 375.0)
            output("sterile_vials", 4000.0)
            labor("operator", 4)
            energy = 842.0
        }
        recipe("mold_syringes", "Mold prefilled syringes", "pharma_packaging", "pharma_packaging_plant", 2) {
            input("plastic_granules", 255.0)
            input("stainless_steel", 25.0)
            input("silicone", 12.5)
            output("prefilled_syringes", 4730.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 758.0
        }
        recipe("form_blister_packs", "Form blister packs", "pharma_packaging", "pharma_packaging_plant", 2) {
            input("polypropylene", 180.0)
            input("aluminum_sheet", 115.0)
            output("blister_packs", 12100.0)
            labor("operator", 4)
            machine("blister_line")
            energy = 565.0
        }
        recipe("build_cold_boxes", "Build cold-chain shippers", "pharma_packaging", "pharma_packaging_plant", 2) {
            input("polyethylene", 390.0)
            input("insulation", 195.0)
            input("packaging", 97.0)
            output("cold_chain_boxes", 2000.0)
            labor("operator", 4)
            energy = 648.0
        }
        recipe("treat_sterile_diluent", "Treat sterile diluent", "pharma_packaging", "pharma_packaging_plant", 2) {
            input("water", 9050.0)
            input("industrial_gases", 450.0)
            output("sterile_diluent", 1440.0)
            labor("operator", 3)
            energy = 847.0
        }
        recipe("fill_insulin_pens", "Fill insulin pens", "biologics", "fill_finish_plant", 4) {
            input("insulin_bulk", 17.5)
            input("prefilled_syringes", 4700.0)
            input("plastic_housings", 2940.0)
            input("packaging", 1180.0)
            output("insulin_pens", 2520.0)
            labor("lab_technician", 4)
            labor("biotechnologist", 1)
            machine("filling_isolator")
            energy = 705.0
        }
        recipe("fill_mab_therapy", "Fill antibody therapy doses", "biologics", "fill_finish_plant", 5) {
            input("antibody_bulk", 17.5)
            input("sterile_vials", 2310.0)
            input("sterile_diluent", 1160.0)
            input("cold_chain_boxes", 345.0)
            output("mab_therapy", 205.0)
            labor("lab_technician", 4)
            labor("biotechnologist", 2)
            machine("lyophilizer")
            machine("filling_isolator")
            energy = 868.0
        }
        recipe("fill_flu_vaccine", "Fill flu vaccine", "biologics", "fill_finish_plant", 4) {
            input("purified_protein", 24.0)
            input("adjuvant", 29.0)
            input("sterile_vials", 2420.0)
            input("sterile_diluent", 1450.0)
            input("cold_chain_boxes", 240.0)
            output("flu_vaccine", 6600.0)
            labor("lab_technician", 4)
            labor("biotechnologist", 1)
            machine("lyophilizer")
            energy = 630.0
        }
        recipe("make_capsule_shells", "Mold capsule shells", "specialty_pharma", "formulation_plant", 2) {
            input("gelatin", 515.0)
            input("glycerin", 145.0)
            input("water", 735.0)
            output("capsule_shells", 3180.0)
            labor("operator", 4)
            energy = 1032.0
        }
        recipe("press_allergy_tablets", "Press allergy tablets", "specialty_pharma", "formulation_plant", 3) {
            input("active_ingredient", 12.0)
            input("excipients", 145.0)
            input("blister_packs", 3670.0)
            input("packaging", 245.0)
            output("allergy_tablets", 5750.0)
            labor("operator", 4)
            labor("lab_technician", 2)
            energy = 856.0
        }
        recipe("fill_herbal_supplements", "Fill herbal supplements", "specialty_pharma", "formulation_plant", 3) {
            input("herbal_extract", 86.0)
            input("capsule_shells", 1720.0)
            input("plastic_housings", 515.0)
            input("packaging", 86.0)
            output("herbal_supplements", 3480.0)
            labor("operator", 4)
            energy = 215.0
        }
        recipe("blend_cough_syrup", "Blend cough syrup", "specialty_pharma", "formulation_plant", 2) {
            input("herbal_extract", 84.0)
            input("sugar", 360.0)
            input("glycerin", 60.0)
            input("glass_vials", 600.0)
            output("cough_syrup", 4580.0)
            labor("operator", 4)
            energy = 241.0
        }
        recipe("compound_ointment", "Compound topical ointment", "specialty_pharma", "formulation_plant", 2) {
            input("steroid_api", 9.0)
            input("zinc_oxide", 46.0)
            input("vegetable_oil", 91.0)
            input("plastic_housings", 685.0)
            output("topical_ointment", 4960.0)
            labor("operator", 4)
            labor("lab_technician", 1)
            energy = 182.0
        }
        recipe("compound_hormone_therapy", "Compound hormone therapy", "specialty_pharma", "formulation_plant", 4) {
            input("steroid_api", 23.0)
            input("excipients", 58.0)
            input("blister_packs", 1160.0)
            output("hormone_therapy", 1050.0)
            labor("operator", 4)
            labor("lab_technician", 2)
            energy = 405.0
            tech = "personalized_medicine"
        }
        recipe("fill_eye_drops", "Fill eye drops", "specialty_pharma", "formulation_plant", 2) {
            input("sterile_diluent", 750.0)
            input("excipients", 15.0)
            input("plastic_housings", 1130.0)
            input("glycerin", 23.0)
            output("eye_drops", 3340.0)
            labor("operator", 4)
            labor("lab_technician", 1)
            energy = 188.0
        }
        recipe("formulate_antiviral", "Formulate antiviral course", "specialty_pharma", "formulation_plant", 4) {
            input("peptide_api", 18.0)
            input("excipients", 60.0)
            input("blister_packs", 1510.0)
            input("packaging", 120.0)
            output("antiviral_course", 745.0)
            labor("operator", 4)
            labor("lab_technician", 2)
            energy = 482.0
            tech = "personalized_medicine"
        }

        tech("botanical_extraction", "Botanical extraction", TechCategory.MATERIALS, 2, 700000.0, 100) {
            unlocksFacility("botanical_extraction_lab")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "specialty_pharma")
        }
        tech("biotech_fermentation", "Biotech fermentation", TechCategory.MATERIALS, 3, 3200000.0, 200) {
            requires("pharmaceutical_synthesis")
            unlocksFacility("fermentation_plant")
            unlocksMachine("bioreactor")
        }
        tech("monoclonal_engineering", "Monoclonal engineering", TechCategory.MATERIALS, 4, 9000000.0, 300) {
            requires("biotech_fermentation")
            unlocksRecipe("produce_antibody_bulk", "produce_insulin_bulk")
            unlocksFacility("biologics_plant")
        }
        tech("aseptic_fill_finish", "Aseptic fill-finish", TechCategory.PRODUCTION, 3, 2800000.0, 180) {
            requires("clean_room_packaging")
            unlocksFacility("fill_finish_plant")
            unlocksMachine("lyophilizer", "filling_isolator")
        }
        tech("continuous_manufacturing", "Continuous manufacturing", TechCategory.PRODUCTION, 3, 2400000.0, 160) {
            requires("lean_manufacturing", "aseptic_fill_finish")
            unlocksMachine("blister_line")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "specialty_pharma")
            effect(EffectKind.LABOR_SAVINGS, 0.06, industry = "biologics")
        }
        tech("cold_chain_logistics", "Cold-chain logistics", TechCategory.LOGISTICS, 2, 800000.0, 100) {
            requires("route_optimization")
            effect(EffectKind.TRANSPORT_SAVINGS, 0.06)
            effect(EffectKind.STORAGE_BONUS, 0.05)
        }
        tech("process_analytics", "Process analytics", TechCategory.DIGITAL, 3, 1600000.0, 140) {
            requires("biotech_fermentation")
            effect(EffectKind.QUALITY_BONUS, 0.2, industry = "biologics")
        }
        tech("personalized_medicine", "Personalized medicine", TechCategory.MATERIALS, 4, 8000000.0, 300) {
            requires("monoclonal_engineering", "process_analytics")
            unlocksRecipe("formulate_antiviral", "compound_hormone_therapy")
            effect(EffectKind.YIELD_BONUS, 0.08, industry = "biologics")
        }

        demand("insulin_pens", 20L, 35L, 40L, 0.15, incomeElasticity = 0.1, nonConsumers = 0.9)
        demand("mab_therapy", 0L, 1L, 2L, 0.2, incomeElasticity = 0.3, nonConsumers = 0.95)
        demand("flu_vaccine", 80L, 120L, 140L, 0.3, incomeElasticity = 0.3, seasons = mapOf(app.many.economytycoon.core.Season.AUTUMN to 2.2, app.many.economytycoon.core.Season.SUMMER to 0.3), nonConsumers = 0.4)
        demand("allergy_tablets", 60L, 100L, 130L, 0.5, incomeElasticity = 0.5, substitutes = listOf("antiviral_course"), seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 2.0, app.many.economytycoon.core.Season.WINTER to 0.5), nonConsumers = 0.5)
        demand("herbal_supplements", 60L, 140L, 220L, 1.0, incomeElasticity = 1.2, substitutes = listOf("vitamins"), nonConsumers = 0.5)
        demand("cough_syrup", 80L, 100L, 100L, 0.4, incomeElasticity = 0.2, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 2.0, app.many.economytycoon.core.Season.SUMMER to 0.4))
        demand("topical_ointment", 90L, 110L, 110L, 0.4, incomeElasticity = 0.2)
        demand("hormone_therapy", 30L, 50L, 60L, 0.3, incomeElasticity = 0.2, nonConsumers = 0.7)
        demand("eye_drops", 40L, 60L, 70L, 0.5, incomeElasticity = 0.4, nonConsumers = 0.6)
        demand("antiviral_course", 20L, 30L, 35L, 0.3, incomeElasticity = 0.2, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.6), nonConsumers = 0.7)
    }
}

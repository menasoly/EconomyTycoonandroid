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

object ConsumerPack {
    val pack: ContentPack = contentPack("consumer", 1, 0) {
        industry("textiles", "Textiles", IndustryFamily.TEXTILES, "Spinning, weaving and dyeing")
        industry("apparel", "Apparel and footwear", IndustryFamily.TEXTILES, "Clothing and shoes")
        industry("furniture", "Furniture", IndustryFamily.FURNITURE, "Household and office furniture")
        industry("household", "Household goods", IndustryFamily.CONSUMER_GOODS, "Cleaning, kitchenware, toys and personal care")
        industry("pharma", "Pharmaceuticals", IndustryFamily.PHARMACEUTICALS, "Medicines and medical supplies")

        good("cotton_yarn", "Cotton yarn", GoodCategory.INTERMEDIATE, 62.0)
        good("cotton_fabric", "Cotton fabric", GoodCategory.INTERMEDIATE, 90.0)
        good("leather", "Tanned leather", GoodCategory.INTERMEDIATE, 58.0)
        good("dyed_fabric", "Dyed fabric", GoodCategory.INTERMEDIATE, 110.0)
        good("synthetic_yarn", "Synthetic yarn", GoodCategory.INTERMEDIATE, 40.0)
        good("rubber_soles", "Rubber soles", GoodCategory.INTERMEDIATE, 6.0)
        good("wooden_frames", "Wooden furniture frames", GoodCategory.INTERMEDIATE, 70.0)
        good("upholstery_fabric", "Upholstery fabric", GoodCategory.INTERMEDIATE, 52.0)
        good("active_ingredient", "Active ingredient", GoodCategory.INTERMEDIATE, 900.0)
        good("excipients", "Excipients", GoodCategory.INTERMEDIATE, 60.0)
        good("glass_vials", "Glass vials", GoodCategory.INTERMEDIATE, 2.0)
        good("plastic_housings", "Plastic housings", GoodCategory.INTERMEDIATE, 5.0)
        good("cosmetic_base", "Cosmetic base", GoodCategory.INTERMEDIATE, 30.0, spoilagePpm = 5000)
        good("toy_parts", "Toy parts", GoodCategory.INTERMEDIATE, 4.0)
        good("wood_pulp", "Wood pulp", GoodCategory.INTERMEDIATE, 30.0)
        good("tshirt", "T-shirt", GoodCategory.CONSUMER, 14.0)
        good("jeans", "Jeans", GoodCategory.CONSUMER, 38.0)
        good("jacket", "Jacket", GoodCategory.CONSUMER, 85.0)
        good("dress", "Dress", GoodCategory.CONSUMER, 52.0)
        good("shoes", "Shoes", GoodCategory.CONSUMER, 62.0)
        good("sneakers", "Sneakers", GoodCategory.CONSUMER, 75.0)
        good("handbag", "Leather handbag", GoodCategory.CONSUMER, 120.0)
        good("bedding_set", "Bedding set", GoodCategory.CONSUMER, 48.0)
        good("towels", "Towels", GoodCategory.CONSUMER, 16.0)
        good("sofa", "Sofa", GoodCategory.CONSUMER, 640.0)
        good("dining_table", "Dining table", GoodCategory.CONSUMER, 380.0)
        good("chair", "Chair", GoodCategory.CONSUMER, 85.0)
        good("bed_frame", "Bed frame", GoodCategory.CONSUMER, 420.0)
        good("wardrobe", "Wardrobe", GoodCategory.CONSUMER, 520.0)
        good("office_desk", "Office desk", GoodCategory.CONSUMER, 310.0)
        good("office_chair", "Office chair", GoodCategory.CONSUMER, 190.0)
        good("detergent", "Laundry detergent", GoodCategory.CONSUMER, 9.0)
        good("dish_soap", "Dish soap", GoodCategory.CONSUMER, 4.0)
        good("shampoo", "Shampoo", GoodCategory.CONSUMER, 7.0)
        good("toothpaste", "Toothpaste", GoodCategory.CONSUMER, 3.0)
        good("cosmetics", "Cosmetics", GoodCategory.CONSUMER, 22.0)
        good("toys", "Toys", GoodCategory.CONSUMER, 18.0)
        good("kitchenware", "Kitchenware set", GoodCategory.CONSUMER, 55.0)
        good("paper_goods", "Paper goods", GoodCategory.CONSUMER, 5.0)
        good("painkillers", "Painkillers", GoodCategory.CONSUMER, 6.0)
        good("antibiotics", "Antibiotics", GoodCategory.CONSUMER, 18.0)
        good("vaccines", "Vaccines", GoodCategory.CONSUMER, 32.0)
        good("vitamins", "Vitamins", GoodCategory.CONSUMER, 12.0)
        good("bandages", "Bandages", GoodCategory.CONSUMER, 4.0)
        good("diagnostic_kits", "Diagnostic kits", GoodCategory.CONSUMER, 28.0)
        good("chronic_meds", "Chronic medication", GoodCategory.CONSUMER, 40.0)

        facility("spinning_mill", "Spinning mill", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 2800000.0; buildTicks = 75; lines = 3; storage = 40000.0; upkeep = 900.0
            energyPerLine = 110.0
            staff("operator", 4)
        }
        facility("weaving_mill", "Weaving mill", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 3200000.0; buildTicks = 80; lines = 3; storage = 40000.0; upkeep = 1000.0
            energyPerLine = 120.0
            staff("operator", 5)
        }
        facility("dye_house", "Dye house", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 2400000.0; buildTicks = 70; lines = 2; storage = 30000.0; upkeep = 900.0
            energyPerLine = 100.0
            staff("operator", 3)
        }
        facility("tannery", "Tannery", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 1800000.0; buildTicks = 60; lines = 2; storage = 30000.0; upkeep = 700.0
            energyPerLine = 70.0
            staff("operator", 3)
        }
        facility("garment_factory", "Garment factory", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 2600000.0; buildTicks = 70; lines = 5; storage = 40000.0; upkeep = 950.0
            energyPerLine = 80.0
            staff("operator", 4)
            staff("clerk", 1)
        }
        facility("shoe_factory", "Shoe factory", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 2500000.0; buildTicks = 70; lines = 3; storage = 30000.0; upkeep = 900.0
            energyPerLine = 80.0
            staff("operator", 4)
        }
        facility("furniture_factory", "Furniture factory", FacilityClass.FACTORY, IndustryFamily.FURNITURE) {
            cost = 3000000.0; buildTicks = 80; lines = 4; storage = 60000.0; upkeep = 1000.0
            energyPerLine = 90.0
            staff("machinist", 3)
            staff("operator", 2)
        }
        facility("pulp_mill", "Pulp mill", FacilityClass.FACTORY, IndustryFamily.CONSUMER_GOODS) {
            cost = 3800000.0; buildTicks = 85; lines = 2; storage = 60000.0; upkeep = 1200.0
            energyPerLine = 140.0
            staff("operator", 4)
        }
        facility("household_factory", "Household goods factory", FacilityClass.FACTORY, IndustryFamily.CONSUMER_GOODS) {
            cost = 3400000.0; buildTicks = 80; lines = 4; storage = 50000.0; upkeep = 1100.0
            energyPerLine = 100.0
            staff("operator", 4)
        }
        facility("pharma_plant", "Pharmaceutical plant", FacilityClass.LABORATORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 14000000.0; buildTicks = 160; lines = 3; storage = 20000.0; upkeep = 4200.0
            energyPerLine = 260.0
            tech = "pharmaceutical_synthesis"
            staff("scientist", 4)
            staff("technician", 6)
        }
        facility("api_plant", "Active ingredient plant", FacilityClass.LABORATORY, IndustryFamily.PHARMACEUTICALS) {
            cost = 18000000.0; buildTicks = 180; lines = 2; storage = 15000.0; upkeep = 5000.0
            energyPerLine = 320.0
            tech = "pharmaceutical_synthesis"
            staff("scientist", 6)
            staff("operator", 6)
        }

        recipe("spin_cotton", "Spin cotton yarn", "textiles", "spinning_mill", 3) {
            input("cotton", 110.0)
            output("cotton_yarn", 110.0)
            labor("operator", 4)
            energy = 60.0
        }
        recipe("spin_synthetic", "Spin synthetic yarn", "textiles", "spinning_mill", 3) {
            input("polyethylene", 40.0)
            input("polypropylene", 40.0)
            input("pigments", 2.0)
            output("synthetic_yarn", 170.0)
            labor("operator", 4)
            energy = 80.0
        }
        recipe("weave_cotton", "Weave cotton fabric", "textiles", "weaving_mill", 3) {
            input("cotton_yarn", 70.0)
            output("cotton_fabric", 80.0)
            labor("operator", 5)
            energy = 70.0
        }
        recipe("weave_synthetic", "Weave synthetic fabric", "textiles", "weaving_mill", 3) {
            input("synthetic_yarn", 60.0)
            output("fabric_synthetic", 420.0)
            labor("operator", 5)
            energy = 70.0
        }
        recipe("dye_fabric", "Dye fabric", "textiles", "dye_house", 2) {
            input("cotton_fabric", 70.0)
            input("pigments", 6.0)
            input("water", 400.0)
            output("dyed_fabric", 85.0)
            waste("chemical_waste", 12.0)
            labor("operator", 3)
            energy = 60.0
        }
        recipe("weave_upholstery", "Weave upholstery fabric", "textiles", "weaving_mill", 3) {
            input("dyed_fabric", 20.0)
            input("synthetic_yarn", 30.0)
            output("upholstery_fabric", 120.0)
            labor("operator", 5)
            energy = 70.0
        }
        recipe("tan_leather", "Tan leather", "textiles", "tannery", 5) {
            input("hides", 90.0)
            input("sulfuric_acid", 8.0)
            input("water", 500.0)
            output("leather", 90.0)
            waste("chemical_waste", 15.0)
            labor("operator", 3)
            energy = 50.0
        }
        recipe("sew_tshirts", "Sew T-shirts", "apparel", "garment_factory", 2) {
            input("cotton_fabric", 12.0)
            input("packaging", 8.0)
            output("tshirt", 175.0)
            labor("operator", 4)
            energy = 20.0
        }
        recipe("sew_jeans", "Sew jeans", "apparel", "garment_factory", 2) {
            input("dyed_fabric", 22.0)
            input("fasteners", 4.0)
            input("packaging", 8.0)
            output("jeans", 110.0)
            labor("operator", 4)
            energy = 24.0
        }
        recipe("sew_jackets", "Sew jackets", "apparel", "garment_factory", 3) {
            input("dyed_fabric", 30.0)
            input("fabric_synthetic", 20.0)
            input("fasteners", 6.0)
            input("packaging", 8.0)
            output("jacket", 70.0)
            labor("operator", 4)
            energy = 30.0
        }
        recipe("sew_dresses", "Sew dresses", "apparel", "garment_factory", 3) {
            input("dyed_fabric", 28.0)
            input("packaging", 8.0)
            output("dress", 105.0)
            labor("operator", 4)
            energy = 24.0
        }
        recipe("make_bedding", "Sew bedding sets", "apparel", "garment_factory", 2) {
            input("cotton_fabric", 30.0)
            input("packaging", 8.0)
            output("bedding_set", 90.0)
            labor("operator", 4)
            energy = 20.0
        }
        recipe("make_towels", "Sew towels", "apparel", "garment_factory", 2) {
            input("cotton_fabric", 20.0)
            input("packaging", 6.0)
            output("towels", 205.0)
            labor("operator", 4)
            energy = 20.0
        }
        recipe("make_rubber_soles", "Mold rubber soles", "apparel", "shoe_factory", 2) {
            input("synthetic_rubber", 40.0)
            input("carbon_black", 8.0)
            output("rubber_soles", 665.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("make_shoes", "Make leather shoes", "apparel", "shoe_factory", 3) {
            input("leather", 20.0)
            input("rubber_soles", 4.0)
            input("adhesives", 3.0)
            input("packaging", 8.0)
            output("shoes", 55.0)
            labor("operator", 4)
            energy = 30.0
        }
        recipe("make_sneakers", "Make sneakers", "apparel", "shoe_factory", 3) {
            input("fabric_synthetic", 12.0)
            input("rubber_soles", 4.0)
            input("foam", 5.0)
            input("adhesives", 3.0)
            input("packaging", 8.0)
            output("sneakers", 30.0)
            labor("operator", 4)
            energy = 30.0
        }
        recipe("make_handbags", "Make handbags", "apparel", "shoe_factory", 3) {
            input("leather", 28.0)
            input("fasteners", 6.0)
            input("packaging", 8.0)
            output("handbag", 30.0)
            labor("operator", 4)
            energy = 25.0
        }
        recipe("make_wooden_frames", "Build wooden frames", "furniture", "furniture_factory", 3) {
            input("lumber", 40.0)
            input("plywood", 10.0)
            input("fasteners", 8.0)
            output("wooden_frames", 65.0)
            labor("machinist", 3)
            labor("operator", 1)
            energy = 50.0
        }
        recipe("make_sofas", "Assemble sofas", "furniture", "furniture_factory", 4) {
            input("wooden_frames", 3.0)
            input("foam", 40.0)
            input("upholstery_fabric", 12.0)
            input("packaging", 12.0)
            output("sofa", 6.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("make_tables", "Build dining tables", "furniture", "furniture_factory", 3) {
            input("lumber", 40.0)
            input("fasteners", 6.0)
            input("paint", 3.0)
            input("packaging", 10.0)
            output("dining_table", 11.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("make_chairs", "Build chairs", "furniture", "furniture_factory", 2) {
            input("lumber", 10.0)
            input("fasteners", 4.0)
            input("foam", 6.0)
            input("upholstery_fabric", 2.0)
            input("packaging", 4.0)
            output("chair", 19.0)
            labor("machinist", 2)
            energy = 20.0
        }
        recipe("make_beds", "Build bed frames", "furniture", "furniture_factory", 3) {
            input("wooden_frames", 3.0)
            input("plywood", 14.0)
            input("fasteners", 8.0)
            input("packaging", 12.0)
            output("bed_frame", 6.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("make_wardrobes", "Build wardrobes", "furniture", "furniture_factory", 4) {
            input("plywood", 40.0)
            input("lumber", 20.0)
            input("fasteners", 12.0)
            input("paint", 3.0)
            input("packaging", 14.0)
            output("wardrobe", 10.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("make_office_desks", "Build office desks", "furniture", "furniture_factory", 3) {
            input("plywood", 30.0)
            input("steel_sheet", 14.0)
            input("fasteners", 8.0)
            input("packaging", 10.0)
            output("office_desk", 15.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("make_office_chairs", "Build office chairs", "furniture", "furniture_factory", 3) {
            input("steel_sheet", 6.0)
            input("foam", 8.0)
            input("fabric_synthetic", 8.0)
            input("plastic_granules", 12.0)
            input("packaging", 6.0)
            output("office_chair", 16.0)
            labor("machinist", 3)
            energy = 30.0
        }
        recipe("pack_detergent", "Package laundry detergent", "household", "household_factory", 2) {
            input("detergent_base", 8.0)
            input("plastic_granules", 6.0)
            input("packaging", 6.0)
            output("detergent", 180.0)
            labor("operator", 3)
            energy = 20.0
        }
        recipe("pack_dish_soap", "Package dish soap", "household", "household_factory", 2) {
            input("detergent_base", 4.0)
            input("plastic_granules", 4.0)
            input("packaging", 4.0)
            output("dish_soap", 330.0)
            labor("operator", 3)
            energy = 20.0
        }
        recipe("mix_cosmetic_base", "Mix cosmetic base", "household", "household_factory", 2) {
            input("vegetable_oil", 20.0)
            input("ethanol", 10.0)
            input("pigments", 2.0)
            output("cosmetic_base", 90.0)
            labor("operator", 3)
            labor("technician", 1)
            energy = 40.0
        }
        recipe("pack_shampoo", "Package shampoo", "household", "household_factory", 2) {
            input("cosmetic_base", 6.0)
            input("detergent_base", 4.0)
            input("plastic_granules", 5.0)
            input("packaging", 5.0)
            output("shampoo", 230.0)
            labor("operator", 3)
            energy = 20.0
        }
        recipe("pack_cosmetics", "Package cosmetics", "household", "household_factory", 2) {
            input("cosmetic_base", 6.0)
            input("plastic_granules", 3.0)
            input("packaging", 6.0)
            output("cosmetics", 60.0)
            labor("operator", 3)
            energy = 20.0
        }
        recipe("pack_toothpaste", "Package toothpaste", "household", "household_factory", 2) {
            input("cosmetic_base", 3.0)
            input("limestone", 14.0)
            input("plastic_granules", 3.0)
            input("packaging", 4.0)
            output("toothpaste", 445.0)
            labor("operator", 3)
            energy = 20.0
        }
        recipe("mold_toy_parts", "Mold toy parts", "household", "household_factory", 2) {
            input("plastic_granules", 30.0)
            input("pigments", 2.0)
            output("toy_parts", 750.0)
            labor("operator", 3)
            energy = 50.0
        }
        recipe("assemble_toys", "Assemble toys", "household", "household_factory", 2) {
            input("toy_parts", 6.0)
            input("packaging", 6.0)
            input("fabric_synthetic", 2.0)
            output("toys", 65.0)
            labor("operator", 4)
            energy = 20.0
        }
        recipe("make_kitchenware", "Make kitchenware", "household", "household_factory", 3) {
            input("stainless_steel", 14.0)
            input("plastic_granules", 6.0)
            input("packaging", 8.0)
            output("kitchenware", 105.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 40.0
        }
        recipe("pulp_logs", "Pulp logs", "household", "pulp_mill", 3) {
            input("logs", 90.0)
            input("water", 500.0)
            output("wood_pulp", 230.0)
            waste("organic_waste", 20.0)
            labor("operator", 4)
            energy = 120.0
        }
        recipe("make_paper_goods", "Convert paper goods", "household", "household_factory", 2) {
            input("wood_pulp", 30.0)
            input("packaging", 4.0)
            output("paper_goods", 420.0)
            labor("operator", 3)
            energy = 30.0
        }
        recipe("make_plastic_housings", "Mold plastic housings", "household", "household_factory", 2) {
            input("plastic_granules", 20.0)
            input("pigments", 1.0)
            output("plastic_housings", 450.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("synthesize_api", "Synthesize active ingredient", "pharma", "api_plant", 10) {
            input("ethanol", 120.0)
            input("solvents", 100.0)
            input("sugar", 80.0)
            input("sulfuric_acid", 20.0)
            output("active_ingredient", 30.0)
            waste("chemical_waste", 40.0)
            labor("scientist", 3)
            labor("operator", 4)
            energy = 300.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_excipients", "Make excipients", "pharma", "pharma_plant", 3) {
            input("sugar", 60.0)
            input("limestone", 20.0)
            input("water", 100.0)
            output("excipients", 70.0)
            labor("technician", 3)
            energy = 60.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_glass_vials", "Mold glass vials", "pharma", "pharma_plant", 2) {
            input("glass", 6.0)
            input("packaging", 1.0)
            output("glass_vials", 785.0)
            labor("technician", 3)
            energy = 70.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("press_painkillers", "Press painkillers", "pharma", "pharma_plant", 3) {
            input("active_ingredient", 2.0)
            input("excipients", 8.0)
            input("plastic_granules", 8.0)
            input("packaging", 6.0)
            output("painkillers", 1110.0)
            labor("technician", 5)
            labor("scientist", 1)
            energy = 80.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_antibiotics", "Make antibiotics", "pharma", "pharma_plant", 4) {
            input("active_ingredient", 3.0)
            input("excipients", 8.0)
            input("glass_vials", 40.0)
            input("packaging", 6.0)
            output("antibiotics", 470.0)
            labor("technician", 5)
            labor("scientist", 1)
            energy = 100.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_vaccines", "Make vaccines", "pharma", "pharma_plant", 6) {
            input("active_ingredient", 4.0)
            input("excipients", 6.0)
            input("glass_vials", 120.0)
            input("packaging", 8.0)
            output("vaccines", 435.0)
            labor("technician", 6)
            labor("scientist", 2)
            energy = 140.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_vitamins", "Make vitamins", "pharma", "pharma_plant", 3) {
            input("active_ingredient", 1.0)
            input("excipients", 10.0)
            input("plastic_granules", 6.0)
            input("packaging", 6.0)
            output("vitamins", 355.0)
            labor("technician", 4)
            energy = 60.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_bandages", "Make bandages", "pharma", "pharma_plant", 2) {
            input("cotton_fabric", 10.0)
            input("adhesives", 2.0)
            input("packaging", 8.0)
            output("bandages", 570.0)
            labor("technician", 3)
            energy = 30.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_diagnostic_kits", "Make diagnostic kits", "pharma", "pharma_plant", 4) {
            input("active_ingredient", 1.0)
            input("sensor_pack", 4.0)
            input("plastic_housings", 80.0)
            input("packaging", 6.0)
            output("diagnostic_kits", 220.0)
            labor("technician", 5)
            labor("scientist", 1)
            energy = 90.0
            tech = "pharmaceutical_synthesis"
        }
        recipe("make_chronic_meds", "Make chronic medication", "pharma", "pharma_plant", 4) {
            input("active_ingredient", 3.0)
            input("excipients", 8.0)
            input("plastic_housings", 20.0)
            input("packaging", 6.0)
            output("chronic_meds", 210.0)
            labor("technician", 5)
            labor("scientist", 1)
            energy = 100.0
            tech = "pharmaceutical_synthesis"
        }

        tech("pharmaceutical_synthesis", "Pharmaceutical synthesis", TechCategory.MATERIALS, 3, 5000000.0, 240) {
            unlocksFacility("pharma_plant", "api_plant")
        }
        tech("automated_looms", "Automated looms", TechCategory.PRODUCTION, 2, 900000.0, 110) {
            effect(EffectKind.THROUGHPUT_BONUS, 0.1, industry = "textiles")
            effect(EffectKind.LABOR_SAVINGS, 0.06, industry = "textiles")
        }
        tech("cad_furniture", "Computer-aided furniture design", TechCategory.DIGITAL, 2, 650000.0, 90) {
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "furniture")
        }
        tech("clean_room_packaging", "Clean-room packaging", TechCategory.PRODUCTION, 3, 2200000.0, 150) {
            requires("pharmaceutical_synthesis")
            effect(EffectKind.QUALITY_BONUS, 0.3, industry = "pharma")
        }
        tech("bulk_fulfillment", "Bulk fulfillment", TechCategory.LOGISTICS, 2, 700000.0, 100) {
            effect(EffectKind.STORAGE_BONUS, 0.12)
        }

        demand("tshirt", 900L, 1100L, 1200L, 0.9, incomeElasticity = 0.9, substitutes = listOf("jeans"))
        demand("jeans", 400L, 480L, 520L, 0.9, incomeElasticity = 1.0, substitutes = listOf("tshirt"))
        demand("jacket", 150L, 260L, 330L, 1.0, incomeElasticity = 1.1, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.8, app.many.economytycoon.core.Season.SUMMER to 0.4))
        demand("dress", 180L, 300L, 420L, 1.1, incomeElasticity = 1.2)
        demand("shoes", 240L, 340L, 420L, 0.9, incomeElasticity = 0.9, substitutes = listOf("sneakers"))
        demand("sneakers", 200L, 320L, 420L, 1.0, incomeElasticity = 1.0, substitutes = listOf("shoes"))
        demand("handbag", 20L, 80L, 220L, 1.3, incomeElasticity = 1.5)
        demand("bedding_set", 40L, 70L, 90L, 0.8)
        demand("towels", 120L, 150L, 170L, 0.7, incomeElasticity = 0.6)
        demand("sofa", 6L, 14L, 30L, 1.2, incomeElasticity = 1.4)
        demand("dining_table", 4L, 9L, 20L, 1.2, incomeElasticity = 1.4)
        demand("chair", 40L, 80L, 150L, 1.0, incomeElasticity = 1.1)
        demand("bed_frame", 8L, 16L, 30L, 1.1, incomeElasticity = 1.3)
        demand("wardrobe", 6L, 12L, 22L, 1.1, incomeElasticity = 1.3)
        demand("office_desk", 3L, 8L, 14L, 1.0, incomeElasticity = 1.0)
        demand("office_chair", 4L, 9L, 16L, 1.0, incomeElasticity = 1.0)
        demand("detergent", 350L, 380L, 400L, 0.4, incomeElasticity = 0.2)
        demand("dish_soap", 280L, 300L, 320L, 0.4, incomeElasticity = 0.2)
        demand("shampoo", 240L, 300L, 340L, 0.5, incomeElasticity = 0.4)
        demand("toothpaste", 180L, 190L, 200L, 0.3, incomeElasticity = 0.1)
        demand("cosmetics", 60L, 160L, 300L, 1.0, incomeElasticity = 1.3, nonConsumers = 0.35)
        demand("toys", 100L, 180L, 260L, 1.0, incomeElasticity = 1.1, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.5, app.many.economytycoon.core.Season.SUMMER to 0.9))
        demand("kitchenware", 4L, 8L, 14L, 0.9, incomeElasticity = 0.9)
        demand("paper_goods", 300L, 340L, 360L, 0.5, incomeElasticity = 0.3)
        demand("painkillers", 500L, 560L, 560L, 0.3, incomeElasticity = 0.1)
        demand("antibiotics", 70L, 80L, 85L, 0.25, incomeElasticity = 0.1, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.4))
        demand("vaccines", 40L, 60L, 70L, 0.3, incomeElasticity = 0.2, seasons = mapOf(app.many.economytycoon.core.Season.AUTUMN to 1.5))
        demand("vitamins", 120L, 260L, 360L, 0.8, incomeElasticity = 0.9, nonConsumers = 0.3)
        demand("bandages", 160L, 180L, 190L, 0.3, incomeElasticity = 0.1)
        demand("diagnostic_kits", 18L, 26L, 30L, 0.4, incomeElasticity = 0.2)
        demand("chronic_meds", 120L, 160L, 170L, 0.2, incomeElasticity = 0.1, nonConsumers = 0.55)
    }
}

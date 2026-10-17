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

object TextilesPack {
    val pack: ContentPack = contentPack("textiles", 1, 0) {
        industry("fibers_and_yarns", "Fibers and yarns", IndustryFamily.TEXTILES, "Viscose, polyester, nylon and wool spinning")
        industry("technical_textiles", "Technical textiles", IndustryFamily.TEXTILES, "Nonwovens, airbag fabric, filter cloth and geotextiles")
        industry("knitwear_and_sportswear", "Knitwear and sportswear", IndustryFamily.TEXTILES, "Knitted fabrics, socks, sweaters, suits and activewear")
        industry("home_textiles", "Home and outdoor textiles", IndustryFamily.TEXTILES, "Curtains, carpets, blankets and camping gear")

        good("viscose_fiber", "Viscose fiber", GoodCategory.INTERMEDIATE, 48.0)
        good("polyester_yarn", "Polyester yarn", GoodCategory.INTERMEDIATE, 44.0)
        good("nylon_yarn", "Nylon yarn", GoodCategory.INTERMEDIATE, 58.0)
        good("wool_yarn", "Wool yarn", GoodCategory.INTERMEDIATE, 78.0)
        good("blended_yarn", "Blended yarn", GoodCategory.INTERMEDIATE, 56.0)
        good("knit_fabric", "Knit fabric", GoodCategory.INTERMEDIATE, 70.0)
        good("denim_fabric", "Denim fabric", GoodCategory.INTERMEDIATE, 84.0)
        good("wool_fabric", "Wool fabric", GoodCategory.INTERMEDIATE, 118.0)
        good("nonwoven_fabric", "Nonwoven fabric", GoodCategory.INTERMEDIATE, 36.0)
        good("bleached_fabric", "Bleached fabric", GoodCategory.INTERMEDIATE, 98.0)
        good("felt", "Wool felt", GoodCategory.INTERMEDIATE, 64.0)
        good("technical_fabric", "Technical fabric", GoodCategory.INTERMEDIATE, 150.0)
        good("geotextile", "Geotextile", GoodCategory.INDUSTRIAL, 62.0, VolatilityClass.STABLE)
        good("filter_cloth", "Filter cloth", GoodCategory.INDUSTRIAL, 55.0, VolatilityClass.STABLE)
        good("airbag_fabric", "Airbag fabric", GoodCategory.INDUSTRIAL, 180.0, VolatilityClass.STABLE)
        good("socks", "Socks", GoodCategory.CONSUMER, 5.0)
        good("sweater", "Sweater", GoodCategory.CONSUMER, 58.0)
        good("suit", "Business suit", GoodCategory.CONSUMER, 190.0)
        good("activewear", "Activewear", GoodCategory.CONSUMER, 48.0)
        good("coat", "Wool coat", GoodCategory.CONSUMER, 140.0)
        good("workwear", "Workwear", GoodCategory.CONSUMER, 32.0)
        good("face_masks", "Face masks", GoodCategory.CONSUMER, 3.0)
        good("curtains", "Curtains", GoodCategory.CONSUMER, 36.0)
        good("carpet", "Carpet", GoodCategory.CONSUMER, 210.0)
        good("blanket", "Wool blanket", GoodCategory.CONSUMER, 44.0)
        good("tent", "Camping tent", GoodCategory.CONSUMER, 180.0)
        good("sleeping_bag", "Sleeping bag", GoodCategory.CONSUMER, 95.0)
        good("umbrella", "Umbrella", GoodCategory.CONSUMER, 22.0)
        good("scarf", "Scarf", GoodCategory.CONSUMER, 18.0)

        role("textile_technician", "Textile technician", SkillBand.SKILLED, 88.0, 40)

        machine("filament_spinner", "Filament spinner", 780000.0, throughput = 1.25, wearPerBatchPpm = 450, maintenance = 60.0, tech = "synthetic_fibers")
        machine("circular_knitter", "Circular knitting machine", 520000.0, throughput = 1.3, wearPerBatchPpm = 400, maintenance = 40.0, laborSavings = 0.08, tech = "seamless_knitting")
        machine("tufting_machine", "Carpet tufting machine", 480000.0, throughput = 1.2, wearPerBatchPpm = 420, maintenance = 38.0, tech = "textile_automation")

        facility("fiber_plant", "Fiber plant", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 6400000.0; buildTicks = 100; lines = 3; storage = 25000.0; upkeep = 1900.0
            energyPerLine = 220.0
            tech = "synthetic_fibers"
            staff("operator", 4)
            staff("chemist", 1)
        }
        facility("wool_mill", "Wool mill", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 3100000.0; buildTicks = 80; lines = 3; storage = 10000.0; upkeep = 1000.0
            energyPerLine = 100.0
            staff("operator", 4)
        }
        facility("knitting_mill", "Knitting mill", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 3600000.0; buildTicks = 85; lines = 4; storage = 10000.0; upkeep = 1100.0
            energyPerLine = 110.0
            staff("operator", 4)
            staff("textile_technician", 1)
        }
        facility("technical_textile_works", "Technical textile works", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 7400000.0; buildTicks = 120; lines = 3; storage = 10000.0; upkeep = 2200.0
            energyPerLine = 200.0
            tech = "technical_textiles"
            staff("operator", 4)
            staff("textile_technician", 2)
            staff("engineer", 1)
        }
        facility("apparel_plant", "Apparel plant", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 3000000.0; buildTicks = 70; lines = 5; storage = 20000.0; upkeep = 1000.0
            energyPerLine = 70.0
            staff("operator", 5)
            staff("textile_technician", 1)
        }
        facility("home_textile_factory", "Home textile factory", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 3400000.0; buildTicks = 80; lines = 4; storage = 10000.0; upkeep = 1050.0
            energyPerLine = 90.0
            staff("operator", 4)
            staff("textile_technician", 1)
        }

        recipe("spin_viscose", "Spin viscose fiber", "fibers_and_yarns", "fiber_plant", 4) {
            input("wood_pulp", 1340.0)
            input("caustic_soda", 335.0)
            input("sulfur", 135.0)
            input("water", 3340.0)
            output("viscose_fiber", 1930.0)
            waste("chemical_waste", 200.0)
            labor("operator", 4)
            labor("chemist", 1)
            energy = 1838.0
        }
        recipe("extrude_polyester", "Extrude polyester yarn", "fibers_and_yarns", "fiber_plant", 3) {
            input("pet_resin", 745.0)
            input("pigments", 21.0)
            output("polyester_yarn", 1580.0)
            labor("operator", 4)
            machine("filament_spinner")
            energy = 1492.0
        }
        recipe("extrude_nylon_yarn", "Extrude nylon yarn", "fibers_and_yarns", "fiber_plant", 3) {
            input("nylon_resin", 435.0)
            input("pigments", 14.5)
            output("nylon_yarn", 1110.0)
            labor("operator", 4)
            machine("filament_spinner")
            energy = 1087.0
        }
        recipe("make_nonwoven", "Bond nonwoven fabric", "technical_textiles", "fiber_plant", 2) {
            input("polypropylene", 550.0)
            input("adhesives", 31.0)
            output("nonwoven_fabric", 1290.0)
            labor("operator", 4)
            energy = 941.0
        }
        recipe("spin_wool_yarn", "Spin wool yarn", "fibers_and_yarns", "wool_mill", 3) {
            input("wool", 540.0)
            input("water", 810.0)
            output("wool_yarn", 535.0)
            waste("organic_waste", 43.0)
            labor("operator", 4)
            energy = 324.0
        }
        recipe("weave_wool_fabric", "Weave wool fabric", "fibers_and_yarns", "wool_mill", 3) {
            input("wool_yarn", 380.0)
            input("pigments", 16.5)
            output("wool_fabric", 350.0)
            labor("operator", 4)
            energy = 326.0
        }
        recipe("press_felt", "Press wool felt", "fibers_and_yarns", "wool_mill", 2) {
            input("wool", 320.0)
            input("adhesives", 14.0)
            input("water", 355.0)
            output("felt", 400.0)
            labor("operator", 4)
            energy = 177.0
        }
        recipe("blend_yarn", "Blend yarn", "knitwear_and_sportswear", "knitting_mill", 2) {
            input("viscose_fiber", 180.0)
            input("polyester_yarn", 180.0)
            output("blended_yarn", 410.0)
            labor("operator", 4)
            energy = 223.0
        }
        recipe("knit_fabric", "Knit fabric", "knitwear_and_sportswear", "knitting_mill", 3) {
            input("blended_yarn", 305.0)
            input("polyester_yarn", 100.0)
            output("knit_fabric", 450.0)
            labor("operator", 4)
            labor("textile_technician", 1)
            machine("circular_knitter")
            energy = 356.0
        }
        recipe("weave_denim", "Weave denim", "knitwear_and_sportswear", "knitting_mill", 3) {
            input("cotton_yarn", 370.0)
            input("pigments", 26.0)
            output("denim_fabric", 405.0)
            labor("operator", 4)
            energy = 316.0
        }
        recipe("bleach_cotton_fabric", "Bleach cotton fabric", "knitwear_and_sportswear", "knitting_mill", 2) {
            input("cotton_fabric", 155.0)
            input("hydrogen_peroxide", 13.0)
            input("water", 660.0)
            output("bleached_fabric", 215.0)
            waste("chemical_waste", 17.5)
            labor("operator", 4)
            energy = 110.0
        }
        recipe("weave_technical_fabric", "Weave technical fabric", "technical_textiles", "technical_textile_works", 4) {
            input("nylon_yarn", 695.0)
            input("silicone", 46.0)
            output("technical_fabric", 525.0)
            labor("operator", 4)
            labor("textile_technician", 2)
            energy = 1273.0
        }
        recipe("weave_airbag_fabric", "Weave airbag fabric", "technical_textiles", "technical_textile_works", 4) {
            input("nylon_yarn", 615.0)
            input("silicone", 53.0)
            output("airbag_fabric", 415.0)
            labor("operator", 4)
            labor("textile_technician", 2)
            labor("engineer", 1)
            energy = 1052.0
            tech = "technical_textiles"
        }
        recipe("make_filter_cloth", "Make filter cloth", "technical_textiles", "technical_textile_works", 3) {
            input("nonwoven_fabric", 770.0)
            input("polyester_yarn", 230.0)
            output("filter_cloth", 1140.0)
            labor("operator", 4)
            labor("textile_technician", 1)
            energy = 1422.0
        }
        recipe("make_geotextile", "Make geotextile", "technical_textiles", "technical_textile_works", 3) {
            input("nonwoven_fabric", 840.0)
            input("polypropylene", 195.0)
            output("geotextile", 1020.0)
            labor("operator", 4)
            labor("textile_technician", 1)
            energy = 1118.0
        }
        recipe("knit_socks", "Knit socks", "knitwear_and_sportswear", "apparel_plant", 2) {
            input("nylon_yarn", 71.0)
            input("blended_yarn", 88.0)
            input("packaging", 53.0)
            output("socks", 2940.0)
            labor("operator", 5)
            energy = 195.0
        }
        recipe("knit_sweaters", "Knit sweaters", "knitwear_and_sportswear", "apparel_plant", 3) {
            input("wool_yarn", 135.0)
            input("blended_yarn", 58.0)
            input("packaging", 78.0)
            output("sweater", 375.0)
            labor("operator", 5)
            energy = 253.0
        }
        recipe("tailor_suits", "Tailor business suits", "knitwear_and_sportswear", "apparel_plant", 4) {
            input("wool_fabric", 105.0)
            input("dyed_fabric", 35.0)
            input("fasteners", 35.0)
            input("packaging", 47.0)
            output("suit", 140.0)
            labor("operator", 5)
            labor("textile_technician", 1)
            energy = 176.0
        }
        recipe("sew_activewear", "Sew activewear", "knitwear_and_sportswear", "apparel_plant", 2) {
            input("knit_fabric", 100.0)
            input("nylon_yarn", 34.0)
            input("packaging", 50.0)
            output("activewear", 305.0)
            labor("operator", 5)
            energy = 202.0
        }
        recipe("sew_coats", "Sew wool coats", "knitwear_and_sportswear", "apparel_plant", 4) {
            input("wool_fabric", 145.0)
            input("fabric_synthetic", 52.0)
            input("fasteners", 52.0)
            input("packaging", 52.0)
            output("coat", 205.0)
            labor("operator", 5)
            labor("textile_technician", 1)
            energy = 194.0
        }
        recipe("sew_workwear", "Sew workwear", "knitwear_and_sportswear", "apparel_plant", 2) {
            input("denim_fabric", 105.0)
            input("fasteners", 31.0)
            input("packaging", 46.0)
            output("workwear", 470.0)
            labor("operator", 5)
            energy = 183.0
        }
        recipe("fold_face_masks", "Fold face masks", "technical_textiles", "apparel_plant", 2) {
            input("nonwoven_fabric", 195.0)
            input("polyester_yarn", 32.0)
            input("packaging", 97.0)
            output("face_masks", 5100.0)
            labor("operator", 5)
            energy = 323.0
        }
        recipe("make_curtains", "Sew curtains", "home_textiles", "home_textile_factory", 2) {
            input("bleached_fabric", 125.0)
            input("fasteners", 21.0)
            input("packaging", 62.0)
            output("curtains", 530.0)
            labor("operator", 4)
            energy = 206.0
        }
        recipe("tuft_carpets", "Tuft carpets", "home_textiles", "home_textile_factory", 4) {
            input("felt", 130.0)
            input("polyester_yarn", 280.0)
            input("adhesives", 37.0)
            output("carpet", 175.0)
            labor("operator", 4)
            labor("textile_technician", 1)
            machine("tufting_machine")
            energy = 562.0
        }
        recipe("weave_blankets", "Weave wool blankets", "home_textiles", "home_textile_factory", 3) {
            input("wool_fabric", 160.0)
            input("packaging", 81.0)
            output("blanket", 665.0)
            labor("operator", 4)
            energy = 405.0
        }
        recipe("sew_tents", "Sew camping tents", "home_textiles", "home_textile_factory", 4) {
            input("technical_fabric", 125.0)
            input("aluminum_sheet", 35.0)
            input("fasteners", 53.0)
            input("packaging", 71.0)
            output("tent", 205.0)
            labor("operator", 4)
            labor("textile_technician", 1)
            energy = 319.0
        }
        recipe("sew_sleeping_bags", "Sew sleeping bags", "home_textiles", "home_textile_factory", 3) {
            input("technical_fabric", 94.0)
            input("foam", 125.0)
            input("fasteners", 47.0)
            input("packaging", 94.0)
            output("sleeping_bag", 290.0)
            labor("operator", 4)
            energy = 409.0
        }
        recipe("assemble_umbrellas", "Assemble umbrellas", "home_textiles", "home_textile_factory", 2) {
            input("technical_fabric", 39.0)
            input("steel_wire", 39.0)
            input("plastic_granules", 39.0)
            input("packaging", 39.0)
            output("umbrella", 830.0)
            labor("operator", 4)
            energy = 314.0
        }
        recipe("knit_scarves", "Knit scarves", "home_textiles", "home_textile_factory", 2) {
            input("wool_yarn", 92.0)
            input("blended_yarn", 69.0)
            input("packaging", 69.0)
            output("scarf", 1020.0)
            labor("operator", 4)
            energy = 322.0
        }

        tech("synthetic_fibers", "Synthetic fibers", TechCategory.MATERIALS, 2, 1200000.0, 120) {
            requires("catalytic_cracking")
            unlocksFacility("fiber_plant")
            unlocksMachine("filament_spinner")
        }
        tech("technical_textiles", "Technical textiles", TechCategory.MATERIALS, 3, 2600000.0, 170) {
            requires("synthetic_fibers")
            unlocksRecipe("weave_airbag_fabric")
            unlocksFacility("technical_textile_works")
        }
        tech("seamless_knitting", "Seamless knitting", TechCategory.PRODUCTION, 2, 1000000.0, 110) {
            requires("automated_looms")
            unlocksMachine("circular_knitter")
            effect(EffectKind.THROUGHPUT_BONUS, 0.08, industry = "knitwear_and_sportswear")
        }
        tech("textile_automation", "Textile automation", TechCategory.PRODUCTION, 3, 1900000.0, 150) {
            requires("automated_looms", "factory_automation")
            unlocksMachine("tufting_machine")
            effect(EffectKind.LABOR_SAVINGS, 0.1, industry = "home_textiles")
            effect(EffectKind.LABOR_SAVINGS, 0.08, industry = "knitwear_and_sportswear")
        }
        tech("sustainable_fibers", "Sustainable fibers", TechCategory.MATERIALS, 3, 1600000.0, 140) {
            requires("green_chemistry", "synthetic_fibers")
            effect(EffectKind.INPUT_SAVINGS, 0.06, industry = "technical_textiles")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "fibers_and_yarns")
        }
        tech("digital_pattern_making", "Digital pattern making", TechCategory.DIGITAL, 2, 700000.0, 90) {
            requires("cad_furniture")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "knitwear_and_sportswear")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "home_textiles")
        }

        demand("socks", 900L, 1000L, 1000L, 0.5, incomeElasticity = 0.3)
        demand("sweater", 90L, 160L, 210L, 1.0, incomeElasticity = 1.0, substitutes = listOf("coat"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 2.0, app.many.economytycoon.core.Season.SUMMER to 0.3))
        demand("suit", 10L, 40L, 110L, 1.2, incomeElasticity = 1.4, nonConsumers = 0.5)
        demand("activewear", 80L, 200L, 300L, 1.1, incomeElasticity = 1.2, substitutes = listOf("tshirt"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.3))
        demand("coat", 40L, 90L, 140L, 1.0, incomeElasticity = 1.1, substitutes = listOf("jacket"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 2.4, app.many.economytycoon.core.Season.SUMMER to 0.1))
        demand("workwear", 50L, 70L, 70L, 0.6, incomeElasticity = 0.3, nonConsumers = 0.4)
        demand("face_masks", 120L, 160L, 180L, 0.5, incomeElasticity = 0.3, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.4), nonConsumers = 0.2)
        demand("curtains", 30L, 50L, 70L, 0.8)
        demand("carpet", 4L, 10L, 22L, 1.1, incomeElasticity = 1.3)
        demand("blanket", 40L, 60L, 70L, 0.7, incomeElasticity = 0.6, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.8, app.many.economytycoon.core.Season.SUMMER to 0.4))
        demand("tent", 2L, 6L, 12L, 1.2, incomeElasticity = 1.4, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 2.0, app.many.economytycoon.core.Season.WINTER to 0.2), nonConsumers = 0.6)
        demand("sleeping_bag", 3L, 8L, 14L, 1.1, incomeElasticity = 1.2, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.8, app.many.economytycoon.core.Season.WINTER to 0.4), nonConsumers = 0.6)
        demand("umbrella", 30L, 40L, 40L, 0.8, incomeElasticity = 0.5, seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 1.6, app.many.economytycoon.core.Season.AUTUMN to 1.6, app.many.economytycoon.core.Season.SUMMER to 0.6))
        demand("scarf", 60L, 90L, 120L, 1.0, incomeElasticity = 1.0, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 2.4, app.many.economytycoon.core.Season.SUMMER to 0.1))
        demand("geotextile", 6L, 8L, 8L, 0.6, incomeElasticity = 0.4)
        demand("filter_cloth", 8L, 10L, 10L, 0.5, incomeElasticity = 0.3)
    }
}

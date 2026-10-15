package app.many.economytycoon.content.packs

import app.many.economytycoon.content.ContentPack
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.content.VolatilityClass
import app.many.economytycoon.content.contentPack

/** Shared goods, roles and vehicles that every other pack builds on. */
object BasePack {
    val pack: ContentPack = contentPack("base", 1, 0) {
        // Utilities and energy carriers
        good("electricity", "Electricity", GoodCategory.ENERGY, 8.0, VolatilityClass.VOLATILE, storable = false)
        good("water", "Water", GoodCategory.INDUSTRIAL, 1.5, VolatilityClass.STABLE, weight = 1_000)
        good("diesel", "Diesel fuel", GoodCategory.ENERGY, 12.0, VolatilityClass.VOLATILE)
        good("gasoline", "Gasoline", GoodCategory.ENERGY, 13.0, VolatilityClass.VOLATILE)
        good("natural_gas", "Natural gas", GoodCategory.ENERGY, 9.0, VolatilityClass.VOLATILE)
        good("coal", "Coal", GoodCategory.RAW, 14.0, VolatilityClass.NORMAL, weight = 1_500)

        // Packaging and generic supplies
        good("packaging", "Packaging materials", GoodCategory.INTERMEDIATE, 3.0, VolatilityClass.STABLE)
        good("pallets", "Pallets", GoodCategory.INTERMEDIATE, 6.0, VolatilityClass.STABLE)
        good("office_supplies", "Office supplies", GoodCategory.INDUSTRIAL, 5.0, VolatilityClass.STABLE)
        good("spare_parts", "Spare parts", GoodCategory.INDUSTRIAL, 30.0, VolatilityClass.STABLE)
        good("lubricants", "Lubricants", GoodCategory.INDUSTRIAL, 18.0, VolatilityClass.NORMAL)

        // Waste streams
        good("scrap", "Scrap material", GoodCategory.WASTE, 0.5, VolatilityClass.NORMAL, tradable = true)
        good("chemical_waste", "Chemical waste", GoodCategory.WASTE, 0.0, VolatilityClass.STABLE, tradable = false)
        good("organic_waste", "Organic waste", GoodCategory.WASTE, 0.0, VolatilityClass.STABLE, spoilagePpm = 50_000, tradable = false)
        good("packaging_waste", "Packaging waste", GoodCategory.WASTE, 0.0, VolatilityClass.STABLE, tradable = false)

        // Roles
        role("laborer", "General laborer", SkillBand.UNSKILLED, 55.0, 10)
        role("farmhand", "Farm hand", SkillBand.UNSKILLED, 58.0, 15)
        role("driver", "Driver", SkillBand.SKILLED, 80.0, 30)
        role("machinist", "Machinist", SkillBand.SKILLED, 95.0, 45)
        role("technician", "Technician", SkillBand.SKILLED, 105.0, 60)
        role("electrician", "Electrician", SkillBand.SKILLED, 100.0, 60)
        role("miner", "Miner", SkillBand.SKILLED, 90.0, 40)
        role("operator", "Plant operator", SkillBand.SKILLED, 98.0, 45)
        role("cook", "Cook", SkillBand.SKILLED, 70.0, 30)
        role("cashier", "Cashier", SkillBand.UNSKILLED, 60.0, 10)
        role("sales", "Sales associate", SkillBand.SKILLED, 75.0, 20)
        role("clerk", "Clerk", SkillBand.SKILLED, 78.0, 20)
        role("engineer", "Engineer", SkillBand.SPECIALIST, 160.0, 120)
        role("programmer", "Programmer", SkillBand.SPECIALIST, 175.0, 120)
        role("designer", "Designer", SkillBand.SPECIALIST, 145.0, 90)
        role("scientist", "Scientist", SkillBand.EXPERT, 210.0, 240)
        role("physician", "Physician", SkillBand.EXPERT, 230.0, 360)
        role("nurse", "Nurse", SkillBand.SPECIALIST, 120.0, 150)
        role("pilot", "Pilot", SkillBand.EXPERT, 220.0, 300)
        role("analyst", "Analyst", SkillBand.SPECIALIST, 150.0, 90)
        role("manager", "Manager", SkillBand.SPECIALIST, 170.0, 120)
        role("executive", "Executive", SkillBand.EXPERT, 320.0, 360)

        // Vehicles
        vehicle("van", "Delivery van", TransportMode.ROAD, 8.0, 14, 38_000.0, 0.9)
        vehicle("truck", "Box truck", TransportMode.ROAD, 40.0, 12, 90_000.0, 1.4)
        vehicle("semi", "Semi trailer", TransportMode.ROAD, 120.0, 11, 160_000.0, 2.2)
        vehicle("freight_train", "Freight train", TransportMode.RAIL, 1_500.0, 20, 2_400_000.0, 6.0)
        vehicle("cargo_ship", "Container ship", TransportMode.SEA, 12_000.0, 9, 28_000_000.0, 22.0)
        vehicle("cargo_plane", "Cargo plane", TransportMode.AIR, 400.0, 70, 55_000_000.0, 130.0)
    }
}

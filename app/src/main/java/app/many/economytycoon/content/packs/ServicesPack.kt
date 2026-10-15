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

object ServicesPack {
    val pack: ContentPack = contentPack("services", 1, 0) {
        industry("hospitality", "Hospitality", IndustryFamily.HOSPITALITY, "Restaurants and hotels")
        industry("retail", "Retail", IndustryFamily.RETAIL, "Stores and supermarkets")
        industry("ecommerce", "E-commerce", IndustryFamily.ECOMMERCE, "Online fulfillment")
        industry("healthcare", "Healthcare", IndustryFamily.HEALTHCARE, "Clinics and hospitals")
        industry("software", "Software", IndustryFamily.SOFTWARE, "Software products and cloud")
        industry("telecom", "Telecommunications", IndustryFamily.TELECOM, "Mobile and broadband")
        industry("media", "Media", IndustryFamily.MEDIA, "Streaming and publishing")
        industry("entertainment", "Entertainment", IndustryFamily.ENTERTAINMENT, "Games and venues")
        industry("transport", "Transportation", IndustryFamily.TRANSPORTATION, "Passenger and freight carriage")
        industry("realestate", "Real estate", IndustryFamily.REAL_ESTATE, "Property management")
        industry("finance_services", "Financial services", IndustryFamily.FINANCIAL_SERVICES, "Banking and insurance")

        good("restaurant_meal", "Restaurant meal", GoodCategory.SERVICE, 30.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("fast_food_meal", "Fast food meal", GoodCategory.SERVICE, 10.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("coffee_cup", "Coffee", GoodCategory.SERVICE, 4.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("hotel_night", "Hotel night", GoodCategory.SERVICE, 120.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("grocery_basket", "Grocery service", GoodCategory.SERVICE, 1.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("clinic_visit", "Clinic visit", GoodCategory.SERVICE, 90.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("hospital_stay", "Hospital day", GoodCategory.SERVICE, 900.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("mobile_plan", "Mobile plan month", GoodCategory.SERVICE, 30.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("broadband_plan", "Broadband plan month", GoodCategory.SERVICE, 45.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("streaming_sub", "Streaming subscription", GoodCategory.SERVICE, 12.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("cinema_ticket", "Cinema ticket", GoodCategory.SERVICE, 13.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("game_license", "Game license", GoodCategory.SERVICE, 40.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("software_license", "Software license", GoodCategory.SERVICE, 120.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("cloud_compute", "Cloud compute hour", GoodCategory.SERVICE, 2.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("passenger_trip", "Passenger trip", GoodCategory.SERVICE, 25.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("air_ticket", "Air ticket", GoodCategory.SERVICE, 220.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("parcel_delivery", "Parcel delivery", GoodCategory.SERVICE, 6.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("rent_apartment", "Apartment rent month", GoodCategory.SERVICE, 900.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("rent_office", "Office rent month", GoodCategory.SERVICE, 4000.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("bank_service", "Banking service", GoodCategory.SERVICE, 5.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("insurance_policy", "Insurance policy month", GoodCategory.SERVICE, 60.0, VolatilityClass.STABLE, weight = 0, volume = 0, tradable = false)
        good("ad_impressions", "Ad impressions", GoodCategory.INDUSTRIAL, 2.0)
        good("app_content", "Content library", GoodCategory.INDUSTRIAL, 80.0)
        good("server_hardware", "Server hardware", GoodCategory.INDUSTRIAL, 4200.0)
        good("network_equipment", "Network equipment", GoodCategory.INDUSTRIAL, 1800.0)
        good("medical_equipment", "Medical equipment", GoodCategory.INDUSTRIAL, 3500.0)
        good("medical_equipment_kit", "Medical equipment kit", GoodCategory.INDUSTRIAL, 900.0)

        facility("restaurant", "Restaurant", FacilityClass.STORE, IndustryFamily.HOSPITALITY) {
            cost = 650000.0; buildTicks = 40; lines = 3; storage = 6000.0; upkeep = 480.0
            energyPerLine = 25.0
            staff("cook", 3)
            staff("cashier", 1)
        }
        facility("fast_food_outlet", "Fast food outlet", FacilityClass.STORE, IndustryFamily.HOSPITALITY) {
            cost = 420000.0; buildTicks = 35; lines = 3; storage = 5000.0; upkeep = 360.0
            energyPerLine = 22.0
            staff("cook", 2)
            staff("cashier", 2)
        }
        facility("cafe", "Cafe", FacilityClass.STORE, IndustryFamily.HOSPITALITY) {
            cost = 260000.0; buildTicks = 30; lines = 2; storage = 2500.0; upkeep = 220.0
            energyPerLine = 12.0
            staff("cook", 1)
            staff("cashier", 1)
        }
        facility("hotel", "Hotel", FacilityClass.HOTEL, IndustryFamily.HOSPITALITY) {
            cost = 12000000.0; buildTicks = 160; lines = 6; storage = 20000.0; upkeep = 4000.0
            energyPerLine = 120.0
            staff("clerk", 4)
            staff("laborer", 6)
            staff("manager", 1)
        }
        facility("supermarket", "Supermarket", FacilityClass.STORE, IndustryFamily.RETAIL) {
            cost = 3200000.0; buildTicks = 70; lines = 4; storage = 60000.0; upkeep = 1300.0
            energyPerLine = 90.0
            staff("cashier", 6)
            staff("sales", 4)
            staff("manager", 1)
        }
        facility("department_store", "Department store", FacilityClass.STORE, IndustryFamily.RETAIL) {
            cost = 6500000.0; buildTicks = 90; lines = 6; storage = 100000.0; upkeep = 2400.0
            energyPerLine = 140.0
            staff("sales", 14)
            staff("cashier", 6)
            staff("manager", 2)
        }
        facility("electronics_store", "Electronics store", FacilityClass.STORE, IndustryFamily.RETAIL) {
            cost = 2400000.0; buildTicks = 60; lines = 3; storage = 30000.0; upkeep = 1000.0
            energyPerLine = 60.0
            staff("sales", 6)
            staff("cashier", 2)
        }
        facility("pharmacy", "Pharmacy", FacilityClass.STORE, IndustryFamily.RETAIL) {
            cost = 1100000.0; buildTicks = 45; lines = 2; storage = 12000.0; upkeep = 520.0
            energyPerLine = 30.0
            staff("sales", 3)
            staff("cashier", 1)
        }
        facility("fulfillment_center", "Fulfillment center", FacilityClass.WAREHOUSE, IndustryFamily.ECOMMERCE) {
            cost = 14000000.0; buildTicks = 140; lines = 4; storage = 400000.0; upkeep = 4200.0
            energyPerLine = 200.0
            tech = "ecommerce_platform"
            staff("laborer", 30)
            staff("clerk", 4)
            staff("manager", 2)
        }
        facility("clinic", "Clinic", FacilityClass.CLINIC, IndustryFamily.HEALTHCARE) {
            cost = 3500000.0; buildTicks = 80; lines = 4; storage = 8000.0; upkeep = 1600.0
            energyPerLine = 50.0
            staff("physician", 2)
            staff("nurse", 3)
            staff("clerk", 1)
        }
        facility("hospital", "Hospital", FacilityClass.CLINIC, IndustryFamily.HEALTHCARE) {
            cost = 55000000.0; buildTicks = 260; lines = 10; storage = 60000.0; upkeep = 16000.0
            energyPerLine = 400.0
            tech = "hospital_administration"
            staff("physician", 20)
            staff("nurse", 40)
            staff("clerk", 6)
        }
        facility("data_center", "Data center", FacilityClass.DATA_CENTER, IndustryFamily.SOFTWARE) {
            cost = 28000000.0; buildTicks = 180; lines = 4; storage = 30000.0; upkeep = 8000.0
            energyPerLine = 900.0
            tech = "cloud_infrastructure"
            staff("technician", 8)
            staff("engineer", 4)
        }
        facility("software_studio", "Software studio", FacilityClass.OFFICE, IndustryFamily.SOFTWARE) {
            cost = 1800000.0; buildTicks = 60; lines = 6; storage = 6000.0; upkeep = 1300.0
            energyPerLine = 30.0
            staff("manager", 1)
            staff("clerk", 1)
        }
        facility("telecom_hub", "Telecom hub", FacilityClass.OFFICE, IndustryFamily.TELECOM) {
            cost = 22000000.0; buildTicks = 150; lines = 4; storage = 20000.0; upkeep = 6500.0
            energyPerLine = 600.0
            tech = "mobile_networks"
            staff("engineer", 6)
            staff("technician", 10)
        }
        facility("media_studio", "Media studio", FacilityClass.STUDIO, IndustryFamily.MEDIA) {
            cost = 8000000.0; buildTicks = 110; lines = 4; storage = 15000.0; upkeep = 3000.0
            energyPerLine = 120.0
            staff("designer", 4)
            staff("clerk", 2)
        }
        facility("game_studio", "Game studio", FacilityClass.STUDIO, IndustryFamily.ENTERTAINMENT) {
            cost = 4800000.0; buildTicks = 90; lines = 5; storage = 8000.0; upkeep = 2200.0
            energyPerLine = 60.0
            staff("designer", 3)
            staff("manager", 1)
        }
        facility("cinema", "Cinema", FacilityClass.STUDIO, IndustryFamily.ENTERTAINMENT) {
            cost = 5200000.0; buildTicks = 90; lines = 4; storage = 10000.0; upkeep = 1800.0
            energyPerLine = 90.0
            staff("cashier", 3)
            staff("laborer", 3)
        }
        facility("bus_terminal", "Bus terminal", FacilityClass.TERMINAL, IndustryFamily.TRANSPORTATION) {
            cost = 6000000.0; buildTicks = 90; lines = 4; storage = 20000.0; upkeep = 1900.0
            energyPerLine = 60.0
            staff("clerk", 3)
            staff("driver", 6)
        }
        facility("airport_terminal", "Airport terminal", FacilityClass.TERMINAL, IndustryFamily.TRANSPORTATION) {
            cost = 140000000.0; buildTicks = 400; lines = 6; storage = 100000.0; upkeep = 30000.0
            energyPerLine = 1200.0
            tech = "commercial_aviation"
            staff("clerk", 40)
            staff("pilot", 12)
            staff("manager", 6)
        }
        facility("courier_depot", "Courier depot", FacilityClass.WAREHOUSE, IndustryFamily.LOGISTICS) {
            cost = 4000000.0; buildTicks = 70; lines = 4; storage = 80000.0; upkeep = 1600.0
            energyPerLine = 60.0
            staff("driver", 8)
            staff("clerk", 2)
        }
        facility("apartment_block", "Apartment block", FacilityClass.OFFICE, IndustryFamily.REAL_ESTATE) {
            cost = 9000000.0; buildTicks = 160; lines = 6; storage = 5000.0; upkeep = 1800.0
            energyPerLine = 80.0
            staff("laborer", 3)
            staff("clerk", 1)
        }
        facility("office_tower", "Office tower", FacilityClass.OFFICE, IndustryFamily.REAL_ESTATE) {
            cost = 60000000.0; buildTicks = 300; lines = 8; storage = 20000.0; upkeep = 9000.0
            energyPerLine = 500.0
            staff("laborer", 10)
            staff("clerk", 4)
            staff("manager", 1)
        }
        facility("bank_branch", "Bank branch", FacilityClass.BANK_BRANCH, IndustryFamily.FINANCIAL_SERVICES) {
            cost = 2600000.0; buildTicks = 60; lines = 3; storage = 4000.0; upkeep = 1100.0
            energyPerLine = 40.0
            staff("clerk", 4)
            staff("analyst", 1)
            staff("manager", 1)
        }
        facility("insurance_office", "Insurance office", FacilityClass.OFFICE, IndustryFamily.FINANCIAL_SERVICES) {
            cost = 3000000.0; buildTicks = 60; lines = 4; storage = 4000.0; upkeep = 1200.0
            energyPerLine = 40.0
            staff("clerk", 4)
            staff("analyst", 2)
        }

        recipe("serve_restaurant", "Serve restaurant meals", "hospitality", "restaurant", 1) {
            input("bread", 40.0)
            input("beef", 12.0)
            input("tomatoes", 20.0)
            input("cooking_oil_bottled", 6.0)
            input("packaging", 8.0)
            output("restaurant_meal", 80.0)
            labor("cook", 3)
            labor("cashier", 1)
            energy = 40.0
        }
        recipe("serve_fast_food", "Serve fast food", "hospitality", "fast_food_outlet", 1) {
            input("bread", 36.0)
            input("chicken_meat", 10.0)
            input("potatoes", 20.0)
            input("cooking_oil_bottled", 6.0)
            input("packaging", 24.0)
            output("fast_food_meal", 160.0)
            labor("cook", 2)
            labor("cashier", 2)
            energy = 36.0
        }
        recipe("brew_coffee", "Brew coffee", "hospitality", "cafe", 1) {
            input("coffee_packaged", 8.0)
            input("fresh_milk", 40.0)
            input("sugar", 4.0)
            input("packaging", 20.0)
            output("coffee_cup", 220.0)
            labor("cook", 1)
            labor("cashier", 1)
            energy = 16.0
        }
        recipe("run_hotel", "Run hotel rooms", "hospitality", "hotel", 1) {
            input("towels", 6.0)
            input("bedding_set", 4.0)
            input("detergent", 20.0)
            input("dish_soap", 6.0)
            input("paper_goods", 30.0)
            output("hotel_night", 120.0)
            labor("clerk", 4)
            labor("laborer", 6)
            energy = 240.0
        }
        recipe("run_supermarket", "Supermarket service", "retail", "supermarket", 1) {
            input("packaging", 80.0)
            input("paper_goods", 40.0)
            input("office_supplies", 6.0)
            output("grocery_basket", 6000.0)
            labor("cashier", 6)
            labor("sales", 4)
            energy = 120.0
        }
        recipe("treat_clinic", "Treat clinic patients", "healthcare", "clinic", 1) {
            input("bandages", 40.0)
            input("painkillers", 40.0)
            input("diagnostic_kits", 6.0)
            input("medical_equipment", 0.01)
            output("clinic_visit", 60.0)
            labor("physician", 2)
            labor("nurse", 3)
            energy = 60.0
        }
        recipe("run_hospital", "Run hospital wards", "healthcare", "hospital", 1) {
            input("bandages", 200.0)
            input("painkillers", 200.0)
            input("antibiotics", 100.0)
            input("vaccines", 20.0)
            input("medical_equipment", 0.05)
            output("hospital_stay", 90.0)
            labor("physician", 20)
            labor("nurse", 40)
            energy = 800.0
        }
        recipe("sell_licenses", "Develop and sell software licenses", "software", "software_studio", 5) {
            input("office_supplies", 8.0)
            input("laptop", 0.2)
            output("software_license", 400.0)
            labor("programmer", 6)
            labor("manager", 1)
            energy = 20.0
        }
        recipe("host_cloud", "Host cloud compute", "software", "data_center", 1) {
            input("server_hardware", 0.2)
            input("spare_parts", 20.0)
            input("electricity", 900.0)
            output("cloud_compute", 40000.0)
            labor("technician", 8)
            labor("engineer", 4)
            energy = 900.0
            tech = "cloud_infrastructure"
        }
        recipe("sell_mobile_plans", "Sell mobile plans", "telecom", "telecom_hub", 1) {
            input("network_equipment", 0.05)
            input("spare_parts", 20.0)
            output("mobile_plan", 4000.0)
            labor("engineer", 6)
            labor("technician", 10)
            energy = 600.0
            tech = "mobile_networks"
        }
        recipe("sell_broadband", "Sell broadband plans", "telecom", "telecom_hub", 1) {
            input("network_equipment", 0.08)
            input("spare_parts", 20.0)
            output("broadband_plan", 2500.0)
            labor("engineer", 4)
            labor("technician", 8)
            energy = 500.0
            tech = "mobile_networks"
        }
        recipe("stream_media", "Produce streaming content", "media", "media_studio", 5) {
            input("app_content", 6.0)
            input("server_hardware", 0.05)
            output("streaming_sub", 18000.0)
            labor("designer", 4)
            labor("clerk", 2)
            energy = 120.0
        }
        recipe("publish_games", "Publish games", "entertainment", "game_studio", 5) {
            input("laptop", 0.2)
            input("office_supplies", 6.0)
            output("game_license", 1500.0)
            labor("designer", 3)
            labor("programmer", 3)
            labor("manager", 1)
            energy = 40.0
        }
        recipe("show_films", "Show films", "entertainment", "cinema", 1) {
            input("soft_drinks", 100.0)
            input("potato_chips", 80.0)
            input("candy", 60.0)
            input("electricity", 80.0)
            output("cinema_ticket", 600.0)
            labor("cashier", 3)
            labor("laborer", 3)
            energy = 90.0
        }
        recipe("run_buses", "Run city buses", "transport", "bus_terminal", 1) {
            input("diesel", 400.0)
            input("tires", 2.0)
            input("spare_parts", 20.0)
            output("passenger_trip", 4000.0)
            labor("driver", 6)
            labor("clerk", 3)
            energy = 40.0
        }
        recipe("fly_passengers", "Operate flights", "transport", "airport_terminal", 1) {
            input("kerosene", 4000.0)
            input("spare_parts", 200.0)
            input("restaurant_meal", 400.0)
            output("air_ticket", 2400.0)
            labor("pilot", 12)
            labor("clerk", 40)
            energy = 1200.0
            tech = "commercial_aviation"
        }
        recipe("deliver_parcels", "Deliver parcels", "ecommerce", "courier_depot", 1) {
            input("diesel", 250.0)
            input("packaging", 300.0)
            input("pallets", 20.0)
            output("parcel_delivery", 6000.0)
            labor("driver", 8)
            labor("clerk", 2)
            energy = 60.0
        }
        recipe("fulfill_orders", "Fulfill online orders", "ecommerce", "fulfillment_center", 1) {
            input("packaging", 600.0)
            input("pallets", 40.0)
            input("conveyor_system", 0.002)
            output("parcel_delivery", 12000.0)
            labor("laborer", 30)
            labor("clerk", 4)
            energy = 200.0
            tech = "ecommerce_platform"
        }
        recipe("rent_apartments", "Rent apartments", "realestate", "apartment_block", 1) {
            input("detergent", 10.0)
            input("paper_goods", 10.0)
            input("spare_parts", 6.0)
            output("rent_apartment", 120.0)
            labor("laborer", 3)
            labor("clerk", 1)
            energy = 80.0
        }
        recipe("rent_offices", "Rent offices", "realestate", "office_tower", 1) {
            input("paper_goods", 40.0)
            input("spare_parts", 30.0)
            input("detergent", 20.0)
            output("rent_office", 400.0)
            labor("laborer", 10)
            labor("clerk", 4)
            energy = 500.0
        }
        recipe("provide_banking", "Provide banking services", "finance_services", "bank_branch", 1) {
            input("office_supplies", 20.0)
            input("paper_goods", 20.0)
            output("bank_service", 2000.0)
            labor("clerk", 4)
            labor("analyst", 1)
            energy = 40.0
        }
        recipe("write_policies", "Write insurance policies", "finance_services", "insurance_office", 1) {
            input("office_supplies", 20.0)
            input("paper_goods", 20.0)
            output("insurance_policy", 900.0)
            labor("clerk", 4)
            labor("analyst", 2)
            energy = 40.0
        }

        tech("ecommerce_platform", "E-commerce platform", TechCategory.RETAIL, 2, 1200000.0, 120) {
            unlocksRecipe("fulfill_orders")
            unlocksFacility("fulfillment_center")
        }
        tech("hospital_administration", "Hospital administration", TechCategory.MANAGEMENT, 3, 4000000.0, 200) {
            unlocksRecipe("run_hospital")
            unlocksFacility("hospital")
        }
        tech("cloud_infrastructure", "Cloud infrastructure", TechCategory.DIGITAL, 3, 6000000.0, 240) {
            unlocksRecipe("host_cloud")
            unlocksFacility("data_center")
        }
        tech("mobile_networks", "Mobile networks", TechCategory.DIGITAL, 3, 8000000.0, 260) {
            unlocksRecipe("sell_mobile_plans", "sell_broadband")
            unlocksFacility("telecom_hub")
        }
        tech("commercial_aviation", "Commercial aviation", TechCategory.LOGISTICS, 4, 22000000.0, 420) {
            requires("aerospace_engineering")
            unlocksRecipe("fly_passengers")
            unlocksFacility("airport_terminal")
        }
        tech("loyalty_programs", "Loyalty programs", TechCategory.RETAIL, 1, 250000.0, 60) {
            effect(EffectKind.YIELD_BONUS, 0.03, industry = "retail")
        }
        tech("point_of_sale", "Point-of-sale systems", TechCategory.RETAIL, 2, 450000.0, 80) {
            requires("loyalty_programs")
            effect(EffectKind.LABOR_SAVINGS, 0.08, industry = "retail")
        }
        tech("route_optimization", "Route optimization", TechCategory.LOGISTICS, 2, 800000.0, 100) {
            effect(EffectKind.TRANSPORT_SAVINGS, 0.08)
        }
        tech("telehealth", "Telehealth", TechCategory.DIGITAL, 2, 900000.0, 100) {
            effect(EffectKind.THROUGHPUT_BONUS, 0.1, industry = "healthcare")
        }

        demand("restaurant_meal", 40L, 120L, 220L, 1.1, incomeElasticity = 1.3, substitutes = listOf("fast_food_meal"))
        demand("fast_food_meal", 160L, 200L, 180L, 0.8, incomeElasticity = 0.5, substitutes = listOf("restaurant_meal"))
        demand("coffee_cup", 120L, 260L, 320L, 0.7, incomeElasticity = 0.6)
        demand("hotel_night", 2L, 6L, 14L, 1.2, incomeElasticity = 1.5, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.6, app.many.economytycoon.core.Season.WINTER to 0.8))
        demand("grocery_basket", 700L, 800L, 820L, 0.2, incomeElasticity = 0.2)
        demand("clinic_visit", 30L, 40L, 44L, 0.3, incomeElasticity = 0.2)
        demand("hospital_stay", 2L, 3L, 3L, 0.2, incomeElasticity = 0.2)
        demand("mobile_plan", 700L, 900L, 1000L, 0.5, incomeElasticity = 0.3)
        demand("broadband_plan", 400L, 700L, 900L, 0.6, incomeElasticity = 0.5)
        demand("streaming_sub", 180L, 420L, 600L, 0.9, incomeElasticity = 0.9)
        demand("cinema_ticket", 30L, 60L, 90L, 1.0, incomeElasticity = 1.0, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.3))
        demand("game_license", 20L, 50L, 80L, 1.0, incomeElasticity = 1.0)
        demand("passenger_trip", 160L, 120L, 90L, 0.8, incomeElasticity = -0.2)
        demand("air_ticket", 2L, 10L, 40L, 1.3, incomeElasticity = 1.6, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.5))
        demand("parcel_delivery", 40L, 120L, 220L, 0.8, incomeElasticity = 1.0)
        demand("rent_apartment", 300L, 330L, 340L, 0.4, incomeElasticity = 0.4)
    }
}

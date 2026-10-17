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

object AutomotivePack {
    val pack: ContentPack = contentPack("automotive", 1, 0) {
        industry("powertrain_and_safety", "Powertrain and safety systems", IndustryFamily.AUTOMOTIVE, "Seat belts, airbags, driver assistance, exhaust and drivetrain parts")
        industry("passenger_vehicles", "Passenger vehicles", IndustryFamily.AUTOMOTIVE, "Sport utility and premium cars built from shared parts")
        industry("commercial_vehicles", "Commercial vehicles", IndustryFamily.AUTOMOTIVE, "Heavy trucks, buses, trailers and vans")
        industry("two_wheelers", "Two-wheelers", IndustryFamily.AUTOMOTIVE, "Motorcycles, scooters and bicycles")
        industry("automotive_aftermarket", "Automotive aftermarket", IndustryFamily.AUTOMOTIVE, "Brake pads, batteries, oil, winter tires and charging hardware")

        good("seat_belts", "Seat belt sets", GoodCategory.INTERMEDIATE, 28.0)
        good("airbag_modules", "Airbag modules", GoodCategory.INTERMEDIATE, 120.0)
        good("infotainment_unit", "Infotainment unit", GoodCategory.INTERMEDIATE, 210.0)
        good("adas_sensor_kit", "Driver assistance sensor kit", GoodCategory.INTERMEDIATE, 340.0)
        good("catalytic_converter", "Catalytic converter", GoodCategory.INTERMEDIATE, 240.0)
        good("exhaust_system", "Exhaust system", GoodCategory.INTERMEDIATE, 160.0)
        good("radiator", "Radiator", GoodCategory.INTERMEDIATE, 85.0)
        good("wheel_rims", "Wheel rims", GoodCategory.INTERMEDIATE, 70.0)
        good("clutch_kit", "Clutch kit", GoodCategory.INTERMEDIATE, 120.0)
        good("differential", "Differential", GoodCategory.INTERMEDIATE, 420.0)
        good("fuel_tank", "Fuel tank", GoodCategory.INTERMEDIATE, 55.0)
        good("diesel_engine", "Diesel engine", GoodCategory.INTERMEDIATE, 3600.0, weight = 3000, volume = 4000)
        good("truck_chassis", "Truck chassis", GoodCategory.INTERMEDIATE, 4200.0, weight = 3000, volume = 4000)
        good("truck_cab", "Truck cab", GoodCategory.INTERMEDIATE, 2800.0, weight = 3000, volume = 4000)
        good("bus_body", "Bus body", GoodCategory.INTERMEDIATE, 18000.0, weight = 3000, volume = 4000)
        good("motorcycle_frame", "Motorcycle frame", GoodCategory.INTERMEDIATE, 140.0)
        good("motorcycle_engine", "Motorcycle engine", GoodCategory.INTERMEDIATE, 520.0)
        good("bicycle_frame", "Bicycle frame", GoodCategory.INTERMEDIATE, 45.0)
        good("heavy_truck", "Heavy truck", GoodCategory.INDUSTRIAL, 62000.0, weight = 5000, volume = 8000)
        good("city_bus", "City bus", GoodCategory.INDUSTRIAL, 140000.0, weight = 5000, volume = 8000)
        good("cargo_trailer", "Cargo trailer", GoodCategory.INDUSTRIAL, 14000.0, weight = 3000, volume = 4000)
        good("delivery_van", "Delivery van", GoodCategory.INDUSTRIAL, 29000.0, weight = 5000, volume = 8000)
        good("ev_charging_station", "EV charging station", GoodCategory.INDUSTRIAL, 18000.0, weight = 3000, volume = 4000)
        good("car_suv", "Sport utility vehicle", GoodCategory.CONSUMER, 41000.0, weight = 5000, volume = 8000)
        good("car_premium", "Premium sedan", GoodCategory.CONSUMER, 54000.0, weight = 5000, volume = 8000)
        good("motorcycle", "Motorcycle", GoodCategory.CONSUMER, 6800.0, weight = 3000, volume = 4000)
        good("electric_scooter", "Electric scooter", GoodCategory.CONSUMER, 650.0)
        good("e_bike", "Electric bicycle", GoodCategory.CONSUMER, 1400.0)
        good("bicycle", "Bicycle", GoodCategory.CONSUMER, 380.0)
        good("brake_pads", "Brake pad set", GoodCategory.CONSUMER, 22.0)
        good("car_battery_12v", "Car battery", GoodCategory.CONSUMER, 110.0)
        good("motor_oil", "Motor oil", GoodCategory.CONSUMER, 14.0)
        good("winter_tires", "Winter tires", GoodCategory.CONSUMER, 100.0)
        good("home_ev_charger", "Home EV charger", GoodCategory.CONSUMER, 900.0)

        role("welder", "Welder", SkillBand.SKILLED, 92.0, 40)

        machine("robotic_welding_cell", "Robotic welding cell", 1800000.0, throughput = 1.3, maintenance = 130.0, laborSavings = 0.12, tech = "heavy_vehicle_engineering")
        machine("paint_booth", "Automated paint booth", 1100000.0, throughput = 1.15, wearPerBatchPpm = 420, maintenance = 90.0, tech = "premium_vehicle_platform")
        machine("cnc_center", "CNC machining center", 900000.0, throughput = 1.2, wearPerBatchPpm = 460, maintenance = 80.0, tech = "lightweight_structures")

        facility("safety_systems_plant", "Vehicle safety systems plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 8400000.0; buildTicks = 110; lines = 4; storage = 10000.0; upkeep = 2600.0
            energyPerLine = 240.0
            tech = "vehicle_safety_systems"
            staff("machinist", 3)
            staff("assembler", 3)
            staff("quality_inspector", 1)
        }
        facility("exhaust_works", "Exhaust and thermal works", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 4600000.0; buildTicks = 90; lines = 3; storage = 10000.0; upkeep = 1500.0
            energyPerLine = 150.0
            tech = "emissions_control"
            staff("welder", 3)
            staff("operator", 2)
        }
        facility("drivetrain_plant", "Drivetrain plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 9800000.0; buildTicks = 130; lines = 3; storage = 10000.0; upkeep = 2900.0
            energyPerLine = 260.0
            tech = "drivetrain_machining"
            staff("machinist", 6)
            staff("engineer", 2)
        }
        facility("heavy_vehicle_plant", "Heavy vehicle plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 26000000.0; buildTicks = 170; lines = 3; storage = 15000.0; upkeep = 6400.0
            energyPerLine = 380.0
            tech = "heavy_vehicle_engineering"
            staff("welder", 8)
            staff("operator", 6)
            staff("engineer", 3)
        }
        facility("light_vehicle_assembly", "Light vehicle assembly plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 24000000.0; buildTicks = 160; lines = 3; storage = 10000.0; upkeep = 6000.0
            energyPerLine = 360.0
            tech = "premium_vehicle_platform"
            staff("operator", 10)
            staff("engineer", 3)
            staff("quality_inspector", 2)
        }
        facility("two_wheeler_works", "Two-wheeler works", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 5400000.0; buildTicks = 100; lines = 4; storage = 10000.0; upkeep = 1700.0
            energyPerLine = 170.0
            tech = "micro_mobility"
            staff("welder", 3)
            staff("assembler", 3)
        }
        facility("aftermarket_plant", "Aftermarket parts plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 4800000.0; buildTicks = 90; lines = 4; storage = 10000.0; upkeep = 1500.0
            energyPerLine = 150.0
            staff("operator", 4)
            staff("machinist", 1)
        }

        recipe("weave_seat_belts", "Make seat belt sets", "powertrain_and_safety", "safety_systems_plant", 2) {
            input("nylon_yarn", 180.0)
            input("steel_sheet", 91.0)
            input("plastic_granules", 120.0)
            output("seat_belts", 1470.0)
            labor("machinist", 3)
            labor("assembler", 3)
            energy = 946.0
        }
        recipe("assemble_airbag_modules", "Assemble airbag modules", "powertrain_and_safety", "safety_systems_plant", 3) {
            input("airbag_fabric", 84.0)
            input("explosives", 42.0)
            input("steel_sheet", 125.0)
            input("controller_unit", 12.5)
            output("airbag_modules", 450.0)
            labor("machinist", 3)
            labor("assembler", 3)
            labor("quality_inspector", 1)
            energy = 1185.0
            tech = "vehicle_safety_systems"
        }
        recipe("build_infotainment_units", "Build infotainment units", "powertrain_and_safety", "safety_systems_plant", 3) {
            input("display_panel", 140.0)
            input("chip_logic", 140.0)
            input("pcb", 280.0)
            input("plastic_housings", 560.0)
            input("rf_modules", 70.0)
            output("infotainment_unit", 260.0)
            labor("assembler", 3)
            labor("quality_inspector", 1)
            energy = 1210.0
            tech = "adas_integration"
        }
        recipe("build_adas_kits", "Build driver assistance kits", "powertrain_and_safety", "safety_systems_plant", 4) {
            input("sensor_pack", 400.0)
            input("camera_module", 300.0)
            input("rf_modules", 200.0)
            input("controller_unit", 100.0)
            output("adas_sensor_kit", 200.0)
            labor("machinist", 3)
            labor("assembler", 3)
            labor("quality_inspector", 1)
            energy = 1462.0
            tech = "adas_integration"
        }
        recipe("form_catalytic_converters", "Form catalytic converters", "powertrain_and_safety", "exhaust_works", 3) {
            input("stainless_steel", 66.0)
            input("rare_earth_oxide", 8.5)
            input("ceramic_tiles", 220.0)
            input("steel_sheet", 110.0)
            output("catalytic_converter", 220.0)
            labor("welder", 3)
            labor("operator", 2)
            energy = 1183.0
        }
        recipe("weld_exhaust_systems", "Weld exhaust systems", "powertrain_and_safety", "exhaust_works", 2) {
            input("steel_pipe", 70.0)
            input("catalytic_converter", 58.0)
            input("sheet_metal_parts", 47.0)
            output("exhaust_system", 235.0)
            labor("welder", 3)
            labor("operator", 2)
            energy = 863.0
        }
        recipe("braze_radiators", "Braze radiators", "powertrain_and_safety", "exhaust_works", 2) {
            input("aluminum_sheet", 79.0)
            input("copper_pipe", 63.0)
            input("plastic_granules", 63.0)
            output("radiator", 440.0)
            labor("welder", 3)
            labor("operator", 2)
            energy = 863.0
        }
        recipe("cast_wheel_rims", "Cast wheel rims", "powertrain_and_safety", "exhaust_works", 2) {
            input("aluminum_ingot", 195.0)
            input("paint", 39.0)
            output("wheel_rims", 535.0)
            labor("welder", 3)
            labor("operator", 2)
            machine("cnc_center")
            energy = 863.0
        }
        recipe("form_fuel_tanks", "Form fuel tanks", "powertrain_and_safety", "exhaust_works", 2) {
            input("steel_sheet", 175.0)
            input("polyethylene", 130.0)
            output("fuel_tank", 680.0)
            labor("welder", 3)
            labor("operator", 2)
            energy = 863.0
        }
        recipe("assemble_clutch_kits", "Assemble clutch kits", "powertrain_and_safety", "drivetrain_plant", 3) {
            input("forgings", 330.0)
            input("steel_sheet", 165.0)
            input("lubricants", 33.0)
            output("clutch_kit", 730.0)
            labor("machinist", 6)
            labor("engineer", 1)
            machine("cnc_center")
            energy = 1963.0
        }
        recipe("machine_differentials", "Machine differentials", "powertrain_and_safety", "drivetrain_plant", 4) {
            input("forgings", 515.0)
            input("castings", 170.0)
            input("lubricants", 52.0)
            output("differential", 275.0)
            labor("machinist", 6)
            labor("engineer", 1)
            machine("cnc_center")
            energy = 2617.0
        }
        recipe("build_diesel_engines", "Build diesel engines", "powertrain_and_safety", "drivetrain_plant", 6) {
            input("castings", 695.0)
            input("forgings", 315.0)
            input("fasteners", 440.0)
            input("lubricants", 95.0)
            input("radiator", 9.5)
            output("diesel_engine", 45.0)
            labor("machinist", 6)
            labor("engineer", 2)
            energy = 3586.0
        }
        recipe("build_motorcycle_engines", "Build motorcycle engines", "powertrain_and_safety", "drivetrain_plant", 3) {
            input("castings", 380.0)
            input("forgings", 175.0)
            input("fasteners", 255.0)
            input("lubricants", 38.0)
            output("motorcycle_engine", 165.0)
            labor("machinist", 6)
            labor("engineer", 1)
            energy = 1963.0
        }
        recipe("weld_truck_chassis", "Weld truck chassis", "commercial_vehicles", "heavy_vehicle_plant", 5) {
            input("steel_beams", 1180.0)
            input("forgings", 430.0)
            input("differential", 54.0)
            input("fasteners", 430.0)
            output("truck_chassis", 73.0)
            labor("welder", 8)
            labor("operator", 3)
            labor("engineer", 1)
            machine("robotic_welding_cell")
            energy = 6954.0
        }
        recipe("build_truck_cabs", "Build truck cabs", "commercial_vehicles", "heavy_vehicle_plant", 5) {
            input("steel_sheet", 965.0)
            input("auto_glass", 205.0)
            input("seat_set", 140.0)
            input("paint", 140.0)
            input("wiring_harness", 69.0)
            output("truck_cab", 110.0)
            labor("welder", 6)
            labor("operator", 4)
            machine("paint_booth")
            energy = 6990.0
        }
        recipe("build_bus_bodies", "Build bus bodies", "commercial_vehicles", "heavy_vehicle_plant", 10) {
            input("steel_beams", 1690.0)
            input("aluminum_sheet", 845.0)
            input("auto_glass", 590.0)
            input("paint", 255.0)
            input("wiring_harness", 125.0)
            output("bus_body", 34.0)
            labor("welder", 8)
            labor("operator", 6)
            labor("engineer", 1)
            machine("robotic_welding_cell")
            energy = 12696.0
        }
        recipe("assemble_heavy_trucks", "Assemble heavy trucks", "commercial_vehicles", "heavy_vehicle_plant", 8) {
            input("truck_chassis", 30.0)
            input("truck_cab", 30.0)
            input("diesel_engine", 30.0)
            input("transmission", 30.0)
            input("tires", 300.0)
            input("exhaust_system", 30.0)
            input("fuel_tank", 30.0)
            input("clutch_kit", 30.0)
            output("heavy_truck", 9.0)
            labor("welder", 4)
            labor("operator", 6)
            labor("engineer", 2)
            energy = 8962.0
        }
        recipe("assemble_city_buses", "Assemble city buses", "commercial_vehicles", "heavy_vehicle_plant", 12) {
            input("bus_body", 25.0)
            input("diesel_engine", 25.0)
            input("transmission", 25.0)
            input("tires", 150.0)
            input("seat_belts", 100.0)
            input("wiring_harness", 100.0)
            input("brake_system", 51.0)
            output("city_bus", 6.0)
            labor("welder", 4)
            labor("operator", 6)
            labor("engineer", 2)
            energy = 10121.0
        }
        recipe("build_cargo_trailers", "Build cargo trailers", "commercial_vehicles", "heavy_vehicle_plant", 6) {
            input("truck_chassis", 32.0)
            input("steel_sheet", 775.0)
            input("tires", 390.0)
            input("brake_system", 130.0)
            output("cargo_trailer", 30.0)
            labor("welder", 6)
            labor("operator", 4)
            energy = 9042.0
        }
        recipe("assemble_delivery_vans", "Assemble delivery vans", "commercial_vehicles", "heavy_vehicle_plant", 6) {
            input("diesel_engine", 23.0)
            input("transmission", 45.0)
            input("car_body", 45.0)
            input("tires", 180.0)
            input("airbag_modules", 90.0)
            input("seat_belts", 90.0)
            input("brake_system", 45.0)
            input("suspension_kit", 45.0)
            input("wiring_harness", 45.0)
            output("delivery_van", 15.0)
            labor("welder", 3)
            labor("operator", 6)
            labor("engineer", 1)
            energy = 8113.0
        }
        recipe("assemble_suvs", "Assemble sport utility vehicles", "passenger_vehicles", "light_vehicle_assembly", 5) {
            input("engine_block", 29.0)
            input("transmission", 29.0)
            input("car_body", 29.0)
            input("seat_set", 29.0)
            input("tires", 145.0)
            input("brake_system", 29.0)
            input("suspension_kit", 29.0)
            input("auto_glass", 29.0)
            input("wiring_harness", 29.0)
            input("paint", 44.0)
            input("airbag_modules", 88.0)
            input("seat_belts", 88.0)
            input("exhaust_system", 29.0)
            input("wheel_rims", 145.0)
            input("fuel_tank", 29.0)
            input("radiator", 29.0)
            input("differential", 29.0)
            output("car_suv", 8.5)
            labor("operator", 22)
            labor("engineer", 3)
            machine("paint_booth")
            energy = 2563.0
        }
        recipe("assemble_premium_cars", "Assemble premium sedans", "passenger_vehicles", "light_vehicle_assembly", 6) {
            input("engine_block", 31.0)
            input("transmission", 31.0)
            input("car_body", 31.0)
            input("seat_set", 31.0)
            input("tires", 125.0)
            input("brake_system", 31.0)
            input("suspension_kit", 31.0)
            input("auto_glass", 31.0)
            input("wiring_harness", 31.0)
            input("paint", 63.0)
            input("airbag_modules", 94.0)
            input("seat_belts", 94.0)
            input("wheel_rims", 125.0)
            input("infotainment_unit", 31.0)
            input("adas_sensor_kit", 31.0)
            input("exhaust_system", 31.0)
            output("car_premium", 7.0)
            labor("operator", 22)
            labor("engineer", 4)
            labor("quality_inspector", 2)
            machine("paint_booth")
            energy = 3652.0
        }
        recipe("weld_motorcycle_frames", "Weld motorcycle frames", "two_wheelers", "two_wheeler_works", 2) {
            input("steel_pipe", 130.0)
            input("steel_sheet", 77.0)
            input("paint", 26.0)
            output("motorcycle_frame", 235.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 748.0
        }
        recipe("weld_bicycle_frames", "Weld bicycle frames", "two_wheelers", "two_wheeler_works", 2) {
            input("aluminum_sheet", 99.0)
            input("steel_pipe", 66.0)
            input("paint", 16.5)
            output("bicycle_frame", 735.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 748.0
        }
        recipe("assemble_motorcycles", "Assemble motorcycles", "two_wheelers", "two_wheeler_works", 3) {
            input("motorcycle_frame", 27.0)
            input("motorcycle_engine", 27.0)
            input("tires", 55.0)
            input("wheel_rims", 55.0)
            input("fuel_tank", 27.0)
            input("wiring_harness", 27.0)
            input("brake_system", 27.0)
            output("motorcycle", 7.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 228.0
        }
        recipe("assemble_electric_scooters", "Assemble electric scooters", "two_wheelers", "two_wheeler_works", 2) {
            input("aluminum_sheet", 64.0)
            input("electric_motor", 21.0)
            input("small_battery", 215.0)
            input("controller_unit", 6.5)
            input("tires", 43.0)
            output("electric_scooter", 47.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 683.0
        }
        recipe("assemble_e_bikes", "Assemble electric bicycles", "two_wheelers", "two_wheeler_works", 2) {
            input("bicycle_frame", 86.0)
            input("electric_motor", 22.0)
            input("small_battery", 345.0)
            input("controller_unit", 8.5)
            input("tires", 86.0)
            output("e_bike", 22.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 683.0
        }
        recipe("assemble_bicycles", "Assemble bicycles", "two_wheelers", "two_wheeler_works", 2) {
            input("bicycle_frame", 84.0)
            input("tires", 84.0)
            input("steel_wire", 42.0)
            input("synthetic_rubber", 63.0)
            output("bicycle", 81.0)
            labor("welder", 3)
            labor("assembler", 3)
            energy = 629.0
        }
        recipe("press_brake_pads", "Press brake pads", "automotive_aftermarket", "aftermarket_plant", 2) {
            input("steel_sheet", 99.0)
            input("phenol", 33.0)
            input("copper_wire", 33.0)
            input("adhesives", 33.0)
            output("brake_pads", 1410.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 707.0
        }
        recipe("assemble_car_batteries", "Assemble car batteries", "automotive_aftermarket", "aftermarket_plant", 3) {
            input("lithium_battery_cells", 25.0)
            input("zinc_ingot", 50.0)
            input("plastic_granules", 150.0)
            input("sulfuric_acid", 200.0)
            input("copper_wire", 50.0)
            output("car_battery_12v", 425.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 1061.0
        }
        recipe("blend_motor_oil", "Blend motor oil", "automotive_aftermarket", "aftermarket_plant", 2) {
            input("lubricants", 500.0)
            input("plastic_granules", 190.0)
            input("packaging", 190.0)
            output("motor_oil", 2230.0)
            labor("operator", 4)
            energy = 713.0
        }
        recipe("mold_winter_tires", "Mold winter tires", "automotive_aftermarket", "aftermarket_plant", 3) {
            input("synthetic_rubber", 285.0)
            input("steel_wire", 72.0)
            input("carbon_black", 100.0)
            input("silicone", 7.0)
            output("winter_tires", 435.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 788.0
        }
        recipe("assemble_home_chargers", "Assemble home EV chargers", "automotive_aftermarket", "aftermarket_plant", 2) {
            input("power_supply", 71.0)
            input("controller_unit", 21.0)
            input("copper_wire", 71.0)
            input("plastic_housings", 140.0)
            input("connectors", 140.0)
            output("home_ev_charger", 32.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 646.0
            tech = "charging_infrastructure"
        }
        recipe("assemble_charging_stations", "Assemble EV charging stations", "automotive_aftermarket", "aftermarket_plant", 4) {
            input("power_supply", 94.0)
            input("controller_unit", 31.0)
            input("copper_wire", 125.0)
            input("steel_sheet", 94.0)
            input("display_panel", 15.5)
            input("connectors", 94.0)
            output("ev_charging_station", 3.2)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 940.0
            tech = "charging_infrastructure"
        }

        tech("emissions_control", "Emissions control", TechCategory.MATERIALS, 2, 1000000.0, 110) {
            requires("catalytic_cracking")
            unlocksFacility("exhaust_works")
            effect(EffectKind.QUALITY_BONUS, 0.1, industry = "powertrain_and_safety")
        }
        tech("lightweight_structures", "Lightweight structures", TechCategory.MATERIALS, 3, 2000000.0, 150) {
            requires("continuous_casting")
            unlocksMachine("cnc_center")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "powertrain_and_safety")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "commercial_vehicles")
        }
        tech("vehicle_safety_systems", "Vehicle safety systems", TechCategory.PRODUCTION, 3, 2400000.0, 170) {
            requires("lean_manufacturing")
            unlocksRecipe("assemble_airbag_modules")
            unlocksFacility("safety_systems_plant")
        }
        tech("adas_integration", "Driver assistance integration", TechCategory.DIGITAL, 3, 3000000.0, 180) {
            requires("vehicle_safety_systems", "cloud_infrastructure")
            unlocksRecipe("build_adas_kits", "build_infotainment_units")
            effect(EffectKind.YIELD_BONUS, 0.04, industry = "passenger_vehicles")
        }
        tech("drivetrain_machining", "Precision drivetrain machining", TechCategory.PRODUCTION, 3, 2600000.0, 160) {
            requires("precision_rolling", "lightweight_structures")
            unlocksFacility("drivetrain_plant")
        }
        tech("heavy_vehicle_engineering", "Heavy vehicle engineering", TechCategory.PRODUCTION, 3, 3800000.0, 200) {
            requires("factory_automation", "drivetrain_machining")
            unlocksFacility("heavy_vehicle_plant")
            unlocksMachine("robotic_welding_cell")
        }
        tech("premium_vehicle_platform", "Premium vehicle platform", TechCategory.PRODUCTION, 3, 3400000.0, 190) {
            requires("vehicle_safety_systems", "ev_platform")
            unlocksFacility("light_vehicle_assembly")
            unlocksMachine("paint_booth")
        }
        tech("micro_mobility", "Micro-mobility", TechCategory.PRODUCTION, 2, 900000.0, 100) {
            requires("battery_manufacturing")
            unlocksFacility("two_wheeler_works")
            effect(EffectKind.THROUGHPUT_BONUS, 0.06, industry = "two_wheelers")
        }
        tech("charging_infrastructure", "Charging infrastructure", TechCategory.ENERGY, 3, 2200000.0, 150) {
            requires("battery_manufacturing", "smart_grid")
            unlocksRecipe("assemble_charging_stations", "assemble_home_chargers")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "automotive_aftermarket")
        }
        tech("fleet_telematics", "Fleet telematics", TechCategory.DIGITAL, 2, 1000000.0, 100) {
            requires("route_optimization")
            effect(EffectKind.TRANSPORT_SAVINGS, 0.05)
            effect(EffectKind.LABOR_SAVINGS, 0.05, industry = "commercial_vehicles")
        }

        demand("car_suv", 0L, 1L, 3L, 1.1, incomeElasticity = 1.4, substitutes = listOf("car_sedan", "pickup_truck"), nonConsumers = 0.9)
        demand("car_premium", 0L, 0L, 2L, 1.3, incomeElasticity = 1.6, substitutes = listOf("car_sedan"), nonConsumers = 0.95)
        demand("motorcycle", 0L, 1L, 2L, 1.2, incomeElasticity = 1.3, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.8, app.many.economytycoon.core.Season.WINTER to 0.2), nonConsumers = 0.9)
        demand("electric_scooter", 1L, 4L, 8L, 1.2, incomeElasticity = 1.2, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.6, app.many.economytycoon.core.Season.WINTER to 0.3), nonConsumers = 0.7)
        demand("e_bike", 0L, 3L, 7L, 1.2, incomeElasticity = 1.3, substitutes = listOf("bicycle"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.6, app.many.economytycoon.core.Season.WINTER to 0.3), nonConsumers = 0.8)
        demand("bicycle", 3L, 8L, 12L, 1.0, incomeElasticity = 0.9, substitutes = listOf("e_bike"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.6, app.many.economytycoon.core.Season.WINTER to 0.3), nonConsumers = 0.5)
        demand("brake_pads", 30L, 50L, 60L, 0.4, incomeElasticity = 0.3, nonConsumers = 0.5)
        demand("car_battery_12v", 18L, 28L, 32L, 0.4, incomeElasticity = 0.3, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.6), nonConsumers = 0.5)
        demand("motor_oil", 90L, 140L, 160L, 0.4, incomeElasticity = 0.3, nonConsumers = 0.5)
        demand("winter_tires", 12L, 30L, 40L, 0.6, incomeElasticity = 0.5, seasons = mapOf(app.many.economytycoon.core.Season.AUTUMN to 2.4, app.many.economytycoon.core.Season.WINTER to 1.4, app.many.economytycoon.core.Season.SUMMER to 0.1), nonConsumers = 0.6)
        demand("home_ev_charger", 0L, 2L, 5L, 1.1, incomeElasticity = 1.3, nonConsumers = 0.9)
        demand("heavy_truck", 0L, 1L, 1L, 0.8, nonConsumers = 0.97)
        demand("city_bus", 0L, 0L, 1L, 0.6, incomeElasticity = 0.6, nonConsumers = 0.99)
        demand("cargo_trailer", 0L, 1L, 1L, 0.8, nonConsumers = 0.97)
        demand("delivery_van", 0L, 1L, 2L, 0.9, incomeElasticity = 0.9, nonConsumers = 0.96)
        demand("ev_charging_station", 0L, 1L, 2L, 0.7, nonConsumers = 0.97)
    }
}

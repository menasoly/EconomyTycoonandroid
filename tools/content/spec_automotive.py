"""Automotive: safety systems, emissions and drivetrain parts, premium cars, commercial vehicles, two-wheelers, aftermarket."""
from expgen import ExpSpec


def build():
    s = ExpSpec("automotive", "AutomotivePack")
    s.role("welder", "Welder", "SKILLED", 92.0, 40)

    s.industry("powertrain_and_safety", "Powertrain and safety systems", "AUTOMOTIVE", "Seat belts, airbags, driver assistance, exhaust and drivetrain parts")
    s.industry("passenger_vehicles", "Passenger vehicles", "AUTOMOTIVE", "Sport utility and premium cars built from shared parts")
    s.industry("commercial_vehicles", "Commercial vehicles", "AUTOMOTIVE", "Heavy trucks, buses, trailers and vans")
    s.industry("two_wheelers", "Two-wheelers", "AUTOMOTIVE", "Motorcycles, scooters and bicycles")
    s.industry("automotive_aftermarket", "Automotive aftermarket", "AUTOMOTIVE", "Brake pads, batteries, oil, winter tires and charging hardware")

    s.add_goods("INTERMEDIATE", [
        ("seat_belts", "Seat belt sets", 28), ("airbag_modules", "Airbag modules", 120), ("infotainment_unit", "Infotainment unit", 210), ("adas_sensor_kit", "Driver assistance sensor kit", 340),
        ("catalytic_converter", "Catalytic converter", 240), ("exhaust_system", "Exhaust system", 160), ("radiator", "Radiator", 85), ("wheel_rims", "Wheel rims", 70),
        ("clutch_kit", "Clutch kit", 120), ("differential", "Differential", 420), ("fuel_tank", "Fuel tank", 55), ("diesel_engine", "Diesel engine", 3600),
        ("truck_chassis", "Truck chassis", 4200), ("truck_cab", "Truck cab", 2800), ("bus_body", "Bus body", 18000), ("motorcycle_frame", "Motorcycle frame", 140),
        ("motorcycle_engine", "Motorcycle engine", 520), ("bicycle_frame", "Bicycle frame", 45),
    ])
    s.add_goods("INDUSTRIAL", [
        ("heavy_truck", "Heavy truck", 62_000), ("city_bus", "City bus", 140_000), ("cargo_trailer", "Cargo trailer", 14_000), ("delivery_van", "Delivery van", 29_000),
        ("ev_charging_station", "EV charging station", 18_000),
    ])
    s.add_goods("CONSUMER", [
        ("car_suv", "Sport utility vehicle", 41_000), ("car_premium", "Premium sedan", 54_000), ("motorcycle", "Motorcycle", 6_800), ("electric_scooter", "Electric scooter", 650),
        ("e_bike", "Electric bicycle", 1_400), ("bicycle", "Bicycle", 380), ("brake_pads", "Brake pad set", 22), ("car_battery_12v", "Car battery", 110),
        ("motor_oil", "Motor oil", 14), ("winter_tires", "Winter tires", 100), ("home_ev_charger", "Home EV charger", 900),
    ])

    s.fac("safety_systems_plant", "Vehicle safety systems plant", "FACTORY", "AUTOMOTIVE", 8_400_000, 110, 4, 2_600, 240.0, {"machinist": 3, "assembler": 3, "quality_inspector": 1}, 720)
    s.fac("exhaust_works", "Exhaust and thermal works", "FACTORY", "AUTOMOTIVE", 4_600_000, 90, 3, 1_500, 150.0, {"welder": 3, "operator": 2}, 560)
    s.fac("drivetrain_plant", "Drivetrain plant", "FACTORY", "AUTOMOTIVE", 9_800_000, 130, 3, 2_900, 260.0, {"machinist": 6, "engineer": 2}, 750)
    s.fac("heavy_vehicle_plant", "Heavy vehicle plant", "FACTORY", "AUTOMOTIVE", 26_000_000, 170, 3, 6_400, 380.0, {"welder": 8, "operator": 6, "engineer": 3}, 950)
    s.fac("light_vehicle_assembly", "Light vehicle assembly plant", "FACTORY", "AUTOMOTIVE", 24_000_000, 160, 3, 6_000, 360.0, {"operator": 10, "engineer": 3, "quality_inspector": 2}, 900)
    s.fac("two_wheeler_works", "Two-wheeler works", "FACTORY", "AUTOMOTIVE", 5_400_000, 100, 4, 1_700, 170.0, {"welder": 3, "assembler": 3}, 560)
    s.fac("aftermarket_plant", "Aftermarket parts plant", "FACTORY", "AUTOMOTIVE", 4_800_000, 90, 4, 1_500, 150.0, {"operator": 4, "machinist": 1}, 520)

    s.machine("robotic_welding_cell", "Robotic welding cell", 1_800_000, thr=1.3, wear=500, maint=130.0, labor_sav=0.12)
    s.machine("paint_booth", "Automated paint booth", 1_100_000, thr=1.15, wear=420, maint=90.0)
    s.machine("cnc_center", "CNC machining center", 900_000, thr=1.2, wear=460, maint=80.0)

    R = s.rec
    R("weave_seat_belts", "Make seat belt sets", "powertrain_and_safety", "safety_systems_plant", 2, {"nylon_yarn": 6, "steel_sheet": 3, "plastic_granules": 4}, {"seat_belts": 1}, {"machinist": 3, "assembler": 3}, energy=50, margin=0.22)
    R("assemble_airbag_modules", "Assemble airbag modules", "powertrain_and_safety", "safety_systems_plant", 3, {"airbag_fabric": 2, "explosives": 1, "steel_sheet": 3, "controller_unit": 0.3}, {"airbag_modules": 1}, {"machinist": 3, "assembler": 3, "quality_inspector": 1}, energy=70, margin=0.26)
    R("build_infotainment_units", "Build infotainment units", "powertrain_and_safety", "safety_systems_plant", 3, {"display_panel": 1, "chip_logic": 1, "pcb": 2, "plastic_housings": 4, "rf_modules": 0.5}, {"infotainment_unit": 1}, {"assembler": 3, "quality_inspector": 1}, energy=80, margin=0.26)
    R("build_adas_kits", "Build driver assistance kits", "powertrain_and_safety", "safety_systems_plant", 4, {"sensor_pack": 4, "camera_module": 3, "rf_modules": 2, "controller_unit": 1}, {"adas_sensor_kit": 1}, {"machinist": 3, "assembler": 3, "quality_inspector": 1}, energy=90, margin=0.28)
    R("form_catalytic_converters", "Form catalytic converters", "powertrain_and_safety", "exhaust_works", 3, {"stainless_steel": 0.6, "rare_earth_oxide": 0.08, "ceramic_tiles": 2, "steel_sheet": 1}, {"catalytic_converter": 1}, {"welder": 3, "operator": 2}, energy=80, margin=0.24)
    R("weld_exhaust_systems", "Weld exhaust systems", "powertrain_and_safety", "exhaust_works", 2, {"steel_pipe": 1.2, "catalytic_converter": 1, "sheet_metal_parts": 0.8}, {"exhaust_system": 1}, {"welder": 3, "operator": 2}, energy=60, margin=0.22)
    R("braze_radiators", "Braze radiators", "powertrain_and_safety", "exhaust_works", 2, {"aluminum_sheet": 0.5, "copper_pipe": 0.4, "plastic_granules": 0.4}, {"radiator": 1}, {"welder": 3, "operator": 2}, energy=40, margin=0.22)
    R("cast_wheel_rims", "Cast wheel rims", "powertrain_and_safety", "exhaust_works", 2, {"aluminum_ingot": 0.5, "paint": 0.1}, {"wheel_rims": 1}, {"welder": 3, "operator": 2}, energy=60, margin=0.22, machines=["cnc_center"])
    R("form_fuel_tanks", "Form fuel tanks", "powertrain_and_safety", "exhaust_works", 2, {"steel_sheet": 0.4, "polyethylene": 0.3}, {"fuel_tank": 1}, {"welder": 3, "operator": 2}, energy=40, margin=0.22)
    R("assemble_clutch_kits", "Assemble clutch kits", "powertrain_and_safety", "drivetrain_plant", 3, {"forgings": 1, "steel_sheet": 0.5, "lubricants": 0.1}, {"clutch_kit": 1}, {"machinist": 6, "engineer": 1}, energy=90, margin=0.24, machines=["cnc_center"])
    R("machine_differentials", "Machine differentials", "powertrain_and_safety", "drivetrain_plant", 4, {"forgings": 3, "castings": 1, "lubricants": 0.3}, {"differential": 1}, {"machinist": 6, "engineer": 1}, energy=110, margin=0.24, machines=["cnc_center"])
    R("build_diesel_engines", "Build diesel engines", "powertrain_and_safety", "drivetrain_plant", 6, {"castings": 22, "forgings": 10, "fasteners": 14, "lubricants": 3, "radiator": 0.3}, {"diesel_engine": 1}, {"machinist": 6, "engineer": 2}, energy=200, margin=0.26)
    R("build_motorcycle_engines", "Build motorcycle engines", "powertrain_and_safety", "drivetrain_plant", 3, {"castings": 3, "forgings": 1.4, "fasteners": 2, "lubricants": 0.3}, {"motorcycle_engine": 1}, {"machinist": 6, "engineer": 1}, energy=90, margin=0.24)
    R("weld_truck_chassis", "Weld truck chassis", "commercial_vehicles", "heavy_vehicle_plant", 5, {"steel_beams": 22, "forgings": 8, "differential": 1, "fasteners": 8}, {"truck_chassis": 1}, {"welder": 8, "operator": 3, "engineer": 1}, energy=200, margin=0.24, machines=["robotic_welding_cell"])
    R("build_truck_cabs", "Build truck cabs", "commercial_vehicles", "heavy_vehicle_plant", 5, {"steel_sheet": 14, "auto_glass": 3, "seat_set": 2, "paint": 2, "wiring_harness": 1}, {"truck_cab": 1}, {"welder": 6, "operator": 4}, energy=160, margin=0.24, machines=["paint_booth"])
    R("build_bus_bodies", "Build bus bodies", "commercial_vehicles", "heavy_vehicle_plant", 10, {"steel_beams": 40, "aluminum_sheet": 20, "auto_glass": 14, "paint": 6, "wiring_harness": 3}, {"bus_body": 1}, {"welder": 8, "operator": 6, "engineer": 1}, energy=300, margin=0.24, machines=["robotic_welding_cell"])
    R("assemble_heavy_trucks", "Assemble heavy trucks", "commercial_vehicles", "heavy_vehicle_plant", 8, {"truck_chassis": 1, "truck_cab": 1, "diesel_engine": 1, "transmission": 1, "tires": 10, "exhaust_system": 1, "fuel_tank": 1, "clutch_kit": 1}, {"heavy_truck": 1}, {"welder": 4, "operator": 6, "engineer": 2}, energy=300, margin=0.2)
    R("assemble_city_buses", "Assemble city buses", "commercial_vehicles", "heavy_vehicle_plant", 12, {"bus_body": 1, "diesel_engine": 1, "transmission": 1, "tires": 6, "seat_belts": 4, "wiring_harness": 4, "brake_system": 2}, {"city_bus": 1}, {"welder": 4, "operator": 6, "engineer": 2}, energy=400, margin=0.2)
    R("build_cargo_trailers", "Build cargo trailers", "commercial_vehicles", "heavy_vehicle_plant", 6, {"truck_chassis": 0.5, "steel_sheet": 12, "tires": 6, "brake_system": 2}, {"cargo_trailer": 1}, {"welder": 6, "operator": 4}, energy=140, margin=0.2)
    R("assemble_delivery_vans", "Assemble delivery vans", "commercial_vehicles", "heavy_vehicle_plant", 6, {"diesel_engine": 0.5, "transmission": 1, "car_body": 1, "tires": 4, "airbag_modules": 2, "seat_belts": 2, "brake_system": 1, "suspension_kit": 1, "wiring_harness": 1}, {"delivery_van": 1}, {"welder": 3, "operator": 6, "engineer": 1}, energy=180, margin=0.2)
    R("assemble_suvs", "Assemble sport utility vehicles", "passenger_vehicles", "light_vehicle_assembly", 5, {"engine_block": 8, "transmission": 8, "car_body": 8, "seat_set": 8, "tires": 40, "brake_system": 8, "suspension_kit": 8, "auto_glass": 8, "wiring_harness": 8, "paint": 12, "airbag_modules": 24, "seat_belts": 24, "exhaust_system": 8, "wheel_rims": 40, "fuel_tank": 8, "radiator": 8, "differential": 8}, {"car_suv": 1}, {"operator": 22, "engineer": 3}, energy=700, margin=0.2, machines=["paint_booth"])
    R("assemble_premium_cars", "Assemble premium sedans", "passenger_vehicles", "light_vehicle_assembly", 6, {"engine_block": 6, "transmission": 6, "car_body": 6, "seat_set": 6, "tires": 24, "brake_system": 6, "suspension_kit": 6, "auto_glass": 6, "wiring_harness": 6, "paint": 12, "airbag_modules": 18, "seat_belts": 18, "wheel_rims": 24, "infotainment_unit": 6, "adas_sensor_kit": 6, "exhaust_system": 6}, {"car_premium": 1}, {"operator": 22, "engineer": 4, "quality_inspector": 2}, energy=700, margin=0.22, machines=["paint_booth"])
    R("weld_motorcycle_frames", "Weld motorcycle frames", "two_wheelers", "two_wheeler_works", 2, {"steel_pipe": 1, "steel_sheet": 0.6, "paint": 0.2}, {"motorcycle_frame": 1}, {"welder": 3, "assembler": 3}, energy=40, margin=0.22)
    R("weld_bicycle_frames", "Weld bicycle frames", "two_wheelers", "two_wheeler_works", 2, {"aluminum_sheet": 0.3, "steel_pipe": 0.2, "paint": 0.05}, {"bicycle_frame": 1}, {"welder": 3, "assembler": 3}, energy=30, margin=0.22)
    R("assemble_motorcycles", "Assemble motorcycles", "two_wheelers", "two_wheeler_works", 3, {"motorcycle_frame": 12, "motorcycle_engine": 12, "tires": 24, "wheel_rims": 24, "fuel_tank": 12, "wiring_harness": 12, "brake_system": 12}, {"motorcycle": 1}, {"welder": 3, "assembler": 3}, energy=100, margin=0.22)
    R("assemble_electric_scooters", "Assemble electric scooters", "two_wheelers", "two_wheeler_works", 2, {"aluminum_sheet": 3, "electric_motor": 1, "small_battery": 10, "controller_unit": 0.3, "tires": 2}, {"electric_scooter": 1}, {"welder": 3, "assembler": 3}, energy=50, margin=0.24)
    R("assemble_e_bikes", "Assemble electric bicycles", "two_wheelers", "two_wheeler_works", 2, {"bicycle_frame": 2, "electric_motor": 0.5, "small_battery": 8, "controller_unit": 0.2, "tires": 2}, {"e_bike": 1}, {"welder": 3, "assembler": 3}, energy=40, margin=0.24)
    R("assemble_bicycles", "Assemble bicycles", "two_wheelers", "two_wheeler_works", 2, {"bicycle_frame": 4, "tires": 4, "steel_wire": 2, "synthetic_rubber": 3}, {"bicycle": 1}, {"welder": 3, "assembler": 3}, energy=30, margin=0.24)
    R("press_brake_pads", "Press brake pads", "automotive_aftermarket", "aftermarket_plant", 2, {"steel_sheet": 3, "phenol": 1, "copper_wire": 1, "adhesives": 1}, {"brake_pads": 10}, {"operator": 4, "machinist": 1}, energy=40, margin=0.22)
    R("assemble_car_batteries", "Assemble car batteries", "automotive_aftermarket", "aftermarket_plant", 3, {"lithium_battery_cells": 0.5, "zinc_ingot": 1, "plastic_granules": 3, "sulfuric_acid": 4, "copper_wire": 1}, {"car_battery_12v": 1}, {"operator": 4, "machinist": 1}, energy=60, margin=0.22)
    R("blend_motor_oil", "Blend motor oil", "automotive_aftermarket", "aftermarket_plant", 2, {"lubricants": 8, "plastic_granules": 3, "packaging": 3}, {"motor_oil": 10}, {"operator": 4}, energy=20, margin=0.22)
    R("mold_winter_tires", "Mold winter tires", "automotive_aftermarket", "aftermarket_plant", 3, {"synthetic_rubber": 40, "steel_wire": 10, "carbon_black": 14, "silicone": 1}, {"winter_tires": 1}, {"operator": 4, "machinist": 1}, energy=110, margin=0.24)
    R("assemble_home_chargers", "Assemble home EV chargers", "automotive_aftermarket", "aftermarket_plant", 2, {"power_supply": 1, "controller_unit": 0.3, "copper_wire": 1, "plastic_housings": 2, "connectors": 2}, {"home_ev_charger": 1}, {"operator": 4, "machinist": 1}, energy=30, margin=0.24)
    R("assemble_charging_stations", "Assemble EV charging stations", "automotive_aftermarket", "aftermarket_plant", 4, {"power_supply": 6, "controller_unit": 2, "copper_wire": 8, "steel_sheet": 6, "display_panel": 1, "connectors": 6}, {"ev_charging_station": 1}, {"operator": 4, "machinist": 1}, energy=60, margin=0.24)

    s.tech("emissions_control", "Emissions control", "MATERIALS", 2, 1_000_000, 110, requires=["catalytic_cracking"],
           facilities=["exhaust_works"], effects=[("QUALITY_BONUS", 0.10, "powertrain_and_safety", None)])
    s.tech("lightweight_structures", "Lightweight structures", "MATERIALS", 3, 2_000_000, 150, requires=["continuous_casting"],
           machines=["cnc_center"], effects=[("INPUT_SAVINGS", 0.05, "powertrain_and_safety", None), ("INPUT_SAVINGS", 0.04, "commercial_vehicles", None)])
    s.tech("vehicle_safety_systems", "Vehicle safety systems", "PRODUCTION", 3, 2_400_000, 170, requires=["lean_manufacturing"],
           facilities=["safety_systems_plant"], recipes=["assemble_airbag_modules"])
    s.tech("adas_integration", "Driver assistance integration", "DIGITAL", 3, 3_000_000, 180, requires=["vehicle_safety_systems", "cloud_infrastructure"],
           recipes=["build_adas_kits", "build_infotainment_units"], effects=[("YIELD_BONUS", 0.04, "passenger_vehicles", None)])
    s.tech("drivetrain_machining", "Precision drivetrain machining", "PRODUCTION", 3, 2_600_000, 160, requires=["precision_rolling", "lightweight_structures"],
           facilities=["drivetrain_plant"])
    s.tech("heavy_vehicle_engineering", "Heavy vehicle engineering", "PRODUCTION", 3, 3_800_000, 200, requires=["factory_automation", "drivetrain_machining"],
           facilities=["heavy_vehicle_plant"], machines=["robotic_welding_cell"])
    s.tech("premium_vehicle_platform", "Premium vehicle platform", "PRODUCTION", 3, 3_400_000, 190, requires=["vehicle_safety_systems", "ev_platform"],
           facilities=["light_vehicle_assembly"], machines=["paint_booth"])
    s.tech("micro_mobility", "Micro-mobility", "PRODUCTION", 2, 900_000, 100, requires=["battery_manufacturing"],
           facilities=["two_wheeler_works"], effects=[("THROUGHPUT_BONUS", 0.06, "two_wheelers", None)])
    s.tech("charging_infrastructure", "Charging infrastructure", "ENERGY", 3, 2_200_000, 150, requires=["battery_manufacturing", "smart_grid"],
           recipes=["assemble_charging_stations", "assemble_home_chargers"], effects=[("YIELD_BONUS", 0.05, "automotive_aftermarket", None)])
    s.tech("fleet_telematics", "Fleet telematics", "DIGITAL", 2, 1_000_000, 100, requires=["route_optimization"],
           effects=[("TRANSPORT_SAVINGS", 0.05, None, None), ("LABOR_SAVINGS", 0.05, "commercial_vehicles", None)])

    D = s.dem
    D("car_suv", 0, 1, 3, 1.1, 1.4, ["car_sedan", "pickup_truck"], {}, 0.9)
    D("car_premium", 0, 0, 2, 1.3, 1.6, ["car_sedan"], {}, 0.95)
    D("motorcycle", 0, 1, 2, 1.2, 1.3, [], {"SUMMER": 1.8, "WINTER": 0.2}, 0.9)
    D("electric_scooter", 1, 4, 8, 1.2, 1.2, [], {"SUMMER": 1.6, "WINTER": 0.3}, 0.7)
    D("e_bike", 0, 3, 7, 1.2, 1.3, ["bicycle"], {"SUMMER": 1.6, "WINTER": 0.3}, 0.8)
    D("bicycle", 3, 8, 12, 1.0, 0.9, ["e_bike"], {"SUMMER": 1.6, "WINTER": 0.3}, 0.5)
    D("brake_pads", 30, 50, 60, 0.4, 0.3, [], {}, 0.5)
    D("car_battery_12v", 18, 28, 32, 0.4, 0.3, [], {"WINTER": 1.6}, 0.5)
    D("motor_oil", 90, 140, 160, 0.4, 0.3, [], {}, 0.5)
    D("winter_tires", 12, 30, 40, 0.6, 0.5, [], {"AUTUMN": 2.4, "WINTER": 1.4, "SUMMER": 0.1}, 0.6)
    D("home_ev_charger", 0, 2, 5, 1.1, 1.3, [], {}, 0.9)
    D("heavy_truck", 0, 1, 1, 0.8, 0.8, [], {}, 0.97)
    D("city_bus", 0, 0, 1, 0.6, 0.6, [], {}, 0.99)
    D("cargo_trailer", 0, 1, 1, 0.8, 0.8, [], {}, 0.97)
    D("delivery_van", 0, 1, 2, 0.9, 0.9, [], {}, 0.96)
    D("ev_charging_station", 0, 1, 2, 0.7, 0.8, [], {}, 0.97)
    return s

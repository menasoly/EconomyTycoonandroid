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

object IndustryPack {
    val pack: ContentPack = contentPack("industry", 1, 0) {
        industry("construction_materials", "Construction materials", IndustryFamily.CONSTRUCTION, "Concrete, lumber products, prefabrication")
        industry("building", "Building construction", IndustryFamily.CONSTRUCTION, "Residential and commercial construction")
        industry("automotive", "Automotive", IndustryFamily.AUTOMOTIVE, "Vehicles and components")
        industry("aerospace", "Aerospace", IndustryFamily.AEROSPACE, "Aircraft and components")
        industry("electronics", "Electronics", IndustryFamily.ELECTRONICS, "Consumer and industrial electronics")
        industry("semiconductors", "Semiconductors", IndustryFamily.SEMICONDUCTORS, "Wafer fabrication and chips")
        industry("robotics", "Robotics", IndustryFamily.ROBOTICS, "Industrial and service robots")
        industry("machinery", "Machinery", IndustryFamily.METALS, "Machine tools and equipment")

        good("concrete", "Ready-mix concrete", GoodCategory.INTERMEDIATE, 22.0, VolatilityClass.STABLE, spoilagePpm = 90000)
        good("rebar", "Rebar", GoodCategory.INTERMEDIATE, 80.0, VolatilityClass.STABLE)
        good("lumber", "Lumber", GoodCategory.INTERMEDIATE, 48.0, VolatilityClass.STABLE)
        good("plywood", "Plywood", GoodCategory.INTERMEDIATE, 40.0, VolatilityClass.STABLE)
        good("prefab_panels", "Prefabricated panels", GoodCategory.INTERMEDIATE, 160.0, VolatilityClass.STABLE)
        good("insulation", "Insulation", GoodCategory.INTERMEDIATE, 26.0, VolatilityClass.STABLE)
        good("roofing_tiles", "Roofing tiles", GoodCategory.INTERMEDIATE, 24.0, VolatilityClass.STABLE)
        good("asphalt", "Asphalt", GoodCategory.INTERMEDIATE, 28.0, VolatilityClass.STABLE, spoilagePpm = 20000)
        good("windows", "Window units", GoodCategory.INTERMEDIATE, 120.0, VolatilityClass.STABLE)
        good("doors", "Doors", GoodCategory.INTERMEDIATE, 85.0, VolatilityClass.STABLE)
        good("hvac_units", "HVAC units", GoodCategory.INTERMEDIATE, 900.0, VolatilityClass.STABLE)
        good("plumbing_fixtures", "Plumbing fixtures", GoodCategory.INTERMEDIATE, 140.0, VolatilityClass.STABLE)
        good("electrical_fittings", "Electrical fittings", GoodCategory.INTERMEDIATE, 60.0, VolatilityClass.STABLE)
        good("housing_unit", "Residential unit", GoodCategory.INDUSTRIAL, 220000.0, weight = 20000, volume = 50000, tradable = false)
        good("office_floor", "Office floor", GoodCategory.INDUSTRIAL, 900000.0, weight = 20000, volume = 50000, tradable = false)
        good("warehouse_hall", "Warehouse hall", GoodCategory.INDUSTRIAL, 650000.0, weight = 20000, volume = 50000, tradable = false)
        good("silicon_ingot", "Silicon ingot", GoodCategory.INTERMEDIATE, 220.0)
        good("silicon_wafer", "Silicon wafer", GoodCategory.INTERMEDIATE, 380.0)
        good("chip_basic", "Basic microchip", GoodCategory.INTERMEDIATE, 6.0)
        good("chip_logic", "Logic chip", GoodCategory.INTERMEDIATE, 80.0, VolatilityClass.VOLATILE)
        good("chip_memory", "Memory chip", GoodCategory.INTERMEDIATE, 38.0, VolatilityClass.VOLATILE)
        good("pcb", "Printed circuit board", GoodCategory.INTERMEDIATE, 14.0)
        good("capacitors", "Capacitors", GoodCategory.INTERMEDIATE, 3.0, VolatilityClass.STABLE)
        good("display_panel", "Display panel", GoodCategory.INTERMEDIATE, 90.0)
        good("sensor_pack", "Sensor pack", GoodCategory.INTERMEDIATE, 28.0)
        good("wiring_harness", "Wiring harness", GoodCategory.INTERMEDIATE, 60.0, VolatilityClass.STABLE)
        good("electric_motor", "Electric motor", GoodCategory.INTERMEDIATE, 180.0)
        good("power_supply", "Power supply unit", GoodCategory.INTERMEDIATE, 34.0)
        good("camera_module", "Camera module", GoodCategory.INTERMEDIATE, 26.0)
        good("battery_pack", "Battery pack", GoodCategory.INTERMEDIATE, 420.0)
        good("controller_unit", "Controller unit", GoodCategory.INTERMEDIATE, 150.0)
        good("smartphone", "Smartphone", GoodCategory.CONSUMER, 520.0)
        good("laptop", "Laptop computer", GoodCategory.CONSUMER, 780.0)
        good("television", "Television", GoodCategory.CONSUMER, 360.0)
        good("tablet", "Tablet", GoodCategory.CONSUMER, 380.0)
        good("refrigerator", "Refrigerator", GoodCategory.CONSUMER, 640.0)
        good("washing_machine", "Washing machine", GoodCategory.CONSUMER, 520.0)
        good("microwave", "Microwave oven", GoodCategory.CONSUMER, 110.0)
        good("game_console", "Game console", GoodCategory.CONSUMER, 330.0)
        good("headphones", "Headphones", GoodCategory.CONSUMER, 70.0)
        good("smartwatch", "Smartwatch", GoodCategory.CONSUMER, 210.0)
        good("router", "Network router", GoodCategory.CONSUMER, 85.0)
        good("engine_block", "Engine", GoodCategory.INDUSTRIAL, 2200.0, weight = 3000, volume = 4000)
        good("transmission", "Transmission", GoodCategory.INDUSTRIAL, 1300.0, weight = 3000, volume = 4000)
        good("car_body", "Car body", GoodCategory.INDUSTRIAL, 1900.0, weight = 3000, volume = 4000)
        good("seat_set", "Seat set", GoodCategory.INDUSTRIAL, 380.0, weight = 3000, volume = 4000)
        good("tires", "Tires", GoodCategory.INDUSTRIAL, 90.0, weight = 3000, volume = 4000)
        good("brake_system", "Brake system", GoodCategory.INDUSTRIAL, 260.0, weight = 3000, volume = 4000)
        good("suspension_kit", "Suspension kit", GoodCategory.INDUSTRIAL, 340.0, weight = 3000, volume = 4000)
        good("auto_glass", "Automotive glass", GoodCategory.INDUSTRIAL, 160.0, weight = 3000, volume = 4000)
        good("ev_drivetrain", "EV drivetrain", GoodCategory.INDUSTRIAL, 3100.0, weight = 3000, volume = 4000)
        good("aircraft_frame", "Aircraft frame", GoodCategory.INDUSTRIAL, 1400000.0, weight = 3000, volume = 4000)
        good("jet_engine", "Jet engine", GoodCategory.INDUSTRIAL, 2600000.0, weight = 3000, volume = 4000)
        good("avionics_suite", "Avionics suite", GoodCategory.INDUSTRIAL, 900000.0, weight = 3000, volume = 4000)
        good("composite_panels", "Composite panels", GoodCategory.INDUSTRIAL, 1100.0, weight = 3000, volume = 4000)
        good("carbon_fiber", "Carbon fiber", GoodCategory.INDUSTRIAL, 260.0, weight = 3000, volume = 4000)
        good("landing_gear", "Landing gear", GoodCategory.INDUSTRIAL, 480000.0, weight = 3000, volume = 4000)
        good("robot_arm", "Robot arm", GoodCategory.INDUSTRIAL, 14000.0, weight = 3000, volume = 4000)
        good("servo_drive", "Servo drive", GoodCategory.INDUSTRIAL, 420.0, weight = 3000, volume = 4000)
        good("machine_tool", "Machine tool", GoodCategory.INDUSTRIAL, 62000.0, weight = 3000, volume = 4000)
        good("conveyor_system", "Conveyor system", GoodCategory.INDUSTRIAL, 18000.0, weight = 3000, volume = 4000)
        good("turbine_blades", "Turbine blades", GoodCategory.INDUSTRIAL, 8000.0, weight = 3000, volume = 4000)
        good("gearbox", "Industrial gearbox", GoodCategory.INDUSTRIAL, 3400.0, weight = 3000, volume = 4000)
        good("solar_panel", "Solar panel", GoodCategory.INDUSTRIAL, 190.0, weight = 3000, volume = 4000)
        good("wind_turbine", "Wind turbine", GoodCategory.INDUSTRIAL, 1400000.0, weight = 3000, volume = 4000)
        good("industrial_robot", "Industrial robot", GoodCategory.INDUSTRIAL, 38000.0, weight = 3000, volume = 4000)
        good("car_compact", "Compact car", GoodCategory.CONSUMER, 19000.0, weight = 5000, volume = 8000)
        good("car_sedan", "Sedan", GoodCategory.CONSUMER, 27000.0, weight = 5000, volume = 8000)
        good("car_ev", "Electric car", GoodCategory.CONSUMER, 36000.0, weight = 5000, volume = 8000)
        good("pickup_truck", "Pickup truck", GoodCategory.CONSUMER, 34000.0, weight = 5000, volume = 8000)
        good("light_aircraft", "Light aircraft", GoodCategory.INDUSTRIAL, 350000.0, weight = 5000, volume = 8000)
        good("jetliner", "Jetliner", GoodCategory.INDUSTRIAL, 95000000.0, weight = 5000, volume = 8000)
        good("delivery_drone", "Delivery drone", GoodCategory.INDUSTRIAL, 4200.0, weight = 5000, volume = 8000)
        good("service_robot", "Service robot", GoodCategory.INDUSTRIAL, 9000.0, weight = 5000, volume = 8000)
        good("fabric_synthetic", "Synthetic fabric", GoodCategory.INTERMEDIATE, 12.0)
        good("foam", "Polyurethane foam", GoodCategory.INTERMEDIATE, 16.0)
        good("carbon_black", "Carbon black", GoodCategory.INTERMEDIATE, 25.0)
        good("titanium_alloy", "Titanium alloy", GoodCategory.INTERMEDIATE, 480.0, VolatilityClass.VOLATILE)
        good("avionics_basic", "Basic avionics", GoodCategory.INDUSTRIAL, 120000.0)
        good("safety_cage", "Safety cage", GoodCategory.INDUSTRIAL, 900.0, VolatilityClass.STABLE)

        facility("concrete_plant", "Concrete plant", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 1400000.0; buildTicks = 55; lines = 3; storage = 30000.0; upkeep = 520.0
            energyPerLine = 60.0
            staff("operator", 2)
        }
        facility("sawmill", "Sawmill", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 1100000.0; buildTicks = 50; lines = 3; storage = 80000.0; upkeep = 460.0
            energyPerLine = 60.0
            staff("operator", 2)
        }
        facility("prefab_factory", "Prefab factory", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 2200000.0; buildTicks = 70; lines = 3; storage = 50000.0; upkeep = 800.0
            energyPerLine = 90.0
            staff("operator", 3)
            staff("machinist", 1)
        }
        facility("fittings_factory", "Building fittings factory", FacilityClass.FACTORY, IndustryFamily.CONSTRUCTION) {
            cost = 1600000.0; buildTicks = 60; lines = 3; storage = 30000.0; upkeep = 620.0
            energyPerLine = 70.0
            staff("machinist", 2)
        }
        facility("construction_yard", "Construction yard", FacilityClass.OFFICE, IndustryFamily.CONSTRUCTION) {
            cost = 900000.0; buildTicks = 40; lines = 6; storage = 100000.0; upkeep = 700.0
            energyPerLine = 20.0
            staff("manager", 1)
            staff("clerk", 1)
        }
        facility("semiconductor_fab", "Semiconductor fab", FacilityClass.FACTORY, IndustryFamily.SEMICONDUCTORS) {
            cost = 60000000.0; buildTicks = 280; lines = 3; storage = 20000.0; upkeep = 14000.0
            energyPerLine = 900.0
            tech = "photolithography"
            staff("engineer", 20)
            staff("technician", 20)
        }
        facility("wafer_plant", "Wafer plant", FacilityClass.FACTORY, IndustryFamily.SEMICONDUCTORS) {
            cost = 18000000.0; buildTicks = 180; lines = 2; storage = 20000.0; upkeep = 5000.0
            energyPerLine = 500.0
            tech = "wafer_growth"
            staff("operator", 6)
            staff("engineer", 4)
        }
        facility("pcb_plant", "PCB plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 4200000.0; buildTicks = 90; lines = 3; storage = 20000.0; upkeep = 1500.0
            energyPerLine = 140.0
            staff("operator", 4)
            staff("technician", 1)
        }
        facility("electronics_assembly", "Electronics assembly plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 7500000.0; buildTicks = 110; lines = 4; storage = 40000.0; upkeep = 2200.0
            energyPerLine = 180.0
            staff("operator", 6)
            staff("technician", 2)
        }
        facility("appliance_factory", "Appliance factory", FacilityClass.FACTORY, IndustryFamily.CONSUMER_GOODS) {
            cost = 6000000.0; buildTicks = 100; lines = 3; storage = 50000.0; upkeep = 1800.0
            energyPerLine = 150.0
            staff("operator", 5)
            staff("machinist", 2)
        }
        facility("auto_parts_plant", "Auto parts plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 5200000.0; buildTicks = 100; lines = 4; storage = 60000.0; upkeep = 1700.0
            energyPerLine = 160.0
            staff("machinist", 4)
            staff("operator", 2)
        }
        facility("engine_plant", "Engine plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 9500000.0; buildTicks = 130; lines = 3; storage = 40000.0; upkeep = 2700.0
            energyPerLine = 220.0
            staff("machinist", 6)
            staff("engineer", 2)
        }
        facility("auto_assembly", "Automobile assembly plant", FacilityClass.FACTORY, IndustryFamily.AUTOMOTIVE) {
            cost = 28000000.0; buildTicks = 180; lines = 4; storage = 90000.0; upkeep = 5500.0
            energyPerLine = 400.0
            staff("operator", 12)
            staff("engineer", 4)
        }
        facility("aircraft_factory", "Aircraft factory", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 120000000.0; buildTicks = 360; lines = 2; storage = 100000.0; upkeep = 22000.0
            energyPerLine = 900.0
            tech = "aerospace_engineering"
            staff("engineer", 30)
            staff("machinist", 30)
        }
        facility("aero_components_plant", "Aerospace components plant", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 38000000.0; buildTicks = 220; lines = 3; storage = 40000.0; upkeep = 8000.0
            energyPerLine = 420.0
            tech = "aerospace_engineering"
            staff("machinist", 12)
            staff("engineer", 6)
        }
        facility("robotics_factory", "Robotics factory", FacilityClass.FACTORY, IndustryFamily.ROBOTICS) {
            cost = 22000000.0; buildTicks = 160; lines = 3; storage = 30000.0; upkeep = 5000.0
            energyPerLine = 300.0
            tech = "industrial_robotics"
            staff("engineer", 8)
            staff("technician", 6)
        }
        facility("machine_tool_works", "Machine tool works", FacilityClass.FACTORY, IndustryFamily.METALS) {
            cost = 8500000.0; buildTicks = 120; lines = 3; storage = 40000.0; upkeep = 2400.0
            energyPerLine = 200.0
            staff("machinist", 6)
            staff("engineer", 2)
        }
        facility("turbine_factory", "Turbine factory", FacilityClass.FACTORY, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 16000000.0; buildTicks = 150; lines = 2; storage = 40000.0; upkeep = 3800.0
            energyPerLine = 240.0
            staff("machinist", 8)
            staff("engineer", 4)
        }
        facility("panel_plant", "Solar panel plant", FacilityClass.FACTORY, IndustryFamily.RENEWABLE_ENERGY) {
            cost = 12000000.0; buildTicks = 130; lines = 3; storage = 30000.0; upkeep = 3000.0
            energyPerLine = 260.0
            staff("operator", 5)
            staff("engineer", 2)
        }

        recipe("mix_concrete", "Mix concrete", "construction_materials", "concrete_plant", 1) {
            input("cement", 120.0)
            input("sand", 250.0)
            input("gravel", 380.0)
            input("water", 100.0)
            output("concrete", 300.0)
            labor("operator", 3)
            energy = 30.0
        }
        recipe("saw_lumber", "Saw lumber", "construction_materials", "sawmill", 3) {
            input("logs", 220.0)
            output("lumber", 195.0)
            output("plywood", 35.0)
            waste("organic_waste", 20.0)
            labor("operator", 4)
            energy = 60.0
        }
        recipe("make_plywood", "Press plywood", "construction_materials", "sawmill", 3) {
            input("logs", 180.0)
            input("adhesives", 20.0)
            output("plywood", 250.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("make_rebar", "Roll rebar", "construction_materials", "fittings_factory", 3) {
            input("steel", 120.0)
            input("lubricants", 2.0)
            output("rebar", 150.0)
            labor("operator", 3)
            energy = 90.0
        }
        recipe("make_prefab", "Build prefab panels", "construction_materials", "prefab_factory", 4) {
            input("concrete", 80.0)
            input("rebar", 25.0)
            input("insulation", 30.0)
            output("prefab_panels", 50.0)
            labor("operator", 4)
            labor("machinist", 1)
            energy = 90.0
        }
        recipe("make_insulation", "Make insulation", "construction_materials", "prefab_factory", 3) {
            input("silica_sand", 90.0)
            input("polypropylene", 20.0)
            output("insulation", 155.0)
            labor("operator", 3)
            energy = 70.0
        }
        recipe("make_roofing_tiles", "Fire roofing tiles", "construction_materials", "fittings_factory", 3) {
            input("clay", 220.0)
            input("natural_gas", 20.0)
            output("roofing_tiles", 145.0)
            labor("operator", 3)
            energy = 60.0
        }
        recipe("mix_asphalt", "Mix asphalt", "construction_materials", "concrete_plant", 1) {
            input("bitumen", 60.0)
            input("gravel", 260.0)
            output("asphalt", 135.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("make_windows", "Assemble windows", "construction_materials", "fittings_factory", 3) {
            input("glass", 40.0)
            input("aluminum_sheet", 12.0)
            input("adhesives", 4.0)
            output("windows", 40.0)
            labor("machinist", 3)
            energy = 50.0
        }
        recipe("make_doors", "Assemble doors", "construction_materials", "fittings_factory", 2) {
            input("lumber", 30.0)
            input("fasteners", 6.0)
            input("paint", 4.0)
            output("doors", 30.0)
            labor("machinist", 2)
            energy = 30.0
        }
        recipe("make_hvac", "Assemble HVAC units", "construction_materials", "fittings_factory", 5) {
            input("sheet_metal_parts", 30.0)
            input("copper_pipe", 12.0)
            input("electric_motor", 4.0)
            input("controller_unit", 2.0)
            output("hvac_units", 9.0)
            labor("machinist", 4)
            energy = 60.0
        }
        recipe("make_plumbing", "Make plumbing fixtures", "construction_materials", "fittings_factory", 3) {
            input("copper_pipe", 20.0)
            input("stainless_steel", 6.0)
            output("plumbing_fixtures", 40.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("make_electrical_fittings", "Make electrical fittings", "construction_materials", "fittings_factory", 3) {
            input("copper_wire", 14.0)
            input("plastic_granules", 20.0)
            output("electrical_fittings", 80.0)
            labor("machinist", 3)
            energy = 40.0
        }
        recipe("build_housing", "Build housing", "building", "construction_yard", 90) {
            input("concrete", 400.0)
            input("rebar", 100.0)
            input("lumber", 150.0)
            input("bricks", 300.0)
            input("windows", 40.0)
            input("doors", 20.0)
            input("roofing_tiles", 200.0)
            input("insulation", 100.0)
            input("plumbing_fixtures", 20.0)
            input("electrical_fittings", 60.0)
            output("housing_unit", 1.7)
            labor("laborer", 30)
            labor("machinist", 8)
            labor("electrician", 4)
            labor("manager", 1)
        }
        recipe("build_office", "Build office floor", "building", "construction_yard", 150) {
            input("concrete", 900.0)
            input("rebar", 260.0)
            input("steel_beams", 140.0)
            input("glass", 220.0)
            input("hvac_units", 8.0)
            input("electrical_fittings", 120.0)
            input("insulation", 160.0)
            input("plumbing_fixtures", 30.0)
            output("office_floor", 0.9)
            labor("laborer", 35)
            labor("machinist", 12)
            labor("electrician", 6)
            labor("manager", 2)
        }
        recipe("build_warehouse", "Build warehouse hall", "building", "construction_yard", 100) {
            input("concrete", 700.0)
            input("steel_beams", 220.0)
            input("steel_sheet", 160.0)
            input("electrical_fittings", 80.0)
            output("warehouse_hall", 0.6)
            labor("laborer", 28)
            labor("machinist", 8)
            labor("electrician", 3)
            labor("manager", 1)
        }
        recipe("grow_silicon", "Grow silicon ingots", "semiconductors", "wafer_plant", 6) {
            input("silica_sand", 400.0)
            input("coal", 80.0)
            input("industrial_gases", 30.0)
            output("silicon_ingot", 85.0)
            labor("operator", 4)
            labor("engineer", 2)
            energy = 700.0
            tech = "wafer_growth"
        }
        recipe("slice_wafers", "Slice silicon wafers", "semiconductors", "wafer_plant", 3) {
            input("silicon_ingot", 30.0)
            input("industrial_gases", 8.0)
            output("silicon_wafer", 30.0)
            labor("operator", 4)
            labor("engineer", 1)
            energy = 300.0
            tech = "wafer_growth"
        }
        recipe("fab_basic_chips", "Fabricate basic chips", "semiconductors", "semiconductor_fab", 10) {
            input("silicon_wafer", 10.0)
            input("industrial_gases", 40.0)
            input("solvents", 20.0)
            output("chip_basic", 8180.0)
            waste("chemical_waste", 20.0)
            labor("engineer", 8)
            labor("technician", 10)
            energy = 1800.0
            tech = "photolithography"
        }
        recipe("fab_logic_chips", "Fabricate logic chips", "semiconductors", "semiconductor_fab", 20) {
            input("silicon_wafer", 12.0)
            input("industrial_gases", 60.0)
            input("solvents", 40.0)
            input("rare_earth_oxide", 1.0)
            output("chip_logic", 1445.0)
            waste("chemical_waste", 35.0)
            labor("engineer", 12)
            labor("technician", 14)
            energy = 3200.0
            tech = "photolithography"
        }
        recipe("fab_memory_chips", "Fabricate memory chips", "semiconductors", "semiconductor_fab", 16) {
            input("silicon_wafer", 12.0)
            input("industrial_gases", 50.0)
            input("solvents", 30.0)
            output("chip_memory", 2200.0)
            waste("chemical_waste", 28.0)
            labor("engineer", 10)
            labor("technician", 12)
            energy = 2600.0
            tech = "photolithography"
        }
        recipe("make_pcb", "Print circuit boards", "electronics", "pcb_plant", 3) {
            input("copper_ingot", 20.0)
            input("resins_epoxy", 18.0)
            input("glass", 8.0)
            input("solvents", 6.0)
            output("pcb", 545.0)
            waste("chemical_waste", 6.0)
            labor("operator", 4)
            labor("technician", 1)
            energy = 90.0
        }
        recipe("make_capacitors", "Make capacitors", "electronics", "pcb_plant", 2) {
            input("aluminum_ingot", 10.0)
            input("tin_ingot", 2.0)
            input("polypropylene", 4.0)
            output("capacitors", 985.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("make_display", "Make display panels", "electronics", "electronics_assembly", 5) {
            input("glass", 50.0)
            input("polypropylene", 10.0)
            input("chip_basic", 30.0)
            input("rare_earth_oxide", 0.5)
            output("display_panel", 95.0)
            labor("operator", 6)
            labor("technician", 2)
            energy = 140.0
        }
        recipe("make_sensors", "Make sensor packs", "electronics", "electronics_assembly", 3) {
            input("chip_basic", 30.0)
            input("pcb", 10.0)
            input("plastic_granules", 8.0)
            output("sensor_pack", 115.0)
            labor("operator", 4)
            labor("technician", 1)
            energy = 80.0
        }
        recipe("make_camera_modules", "Make camera modules", "electronics", "electronics_assembly", 3) {
            input("glass", 6.0)
            input("chip_basic", 25.0)
            input("plastic_granules", 8.0)
            input("pcb", 6.0)
            output("camera_module", 110.0)
            labor("operator", 4)
            energy = 70.0
        }
        recipe("make_power_supplies", "Make power supplies", "electronics", "electronics_assembly", 2) {
            input("copper_wire", 16.0)
            input("capacitors", 40.0)
            input("pcb", 8.0)
            input("plastic_granules", 8.0)
            output("power_supply", 135.0)
            labor("operator", 3)
            energy = 60.0
        }
        recipe("make_wiring_harness", "Make wiring harnesses", "electronics", "auto_parts_plant", 2) {
            input("copper_wire", 40.0)
            input("plastic_granules", 16.0)
            output("wiring_harness", 150.0)
            labor("operator", 4)
            energy = 40.0
        }
        recipe("make_motors", "Wind electric motors", "electronics", "auto_parts_plant", 3) {
            input("copper_wire", 40.0)
            input("steel_sheet", 30.0)
            input("rare_earth_oxide", 1.0)
            output("electric_motor", 70.0)
            labor("machinist", 4)
            energy = 80.0
        }
        recipe("make_battery_pack", "Assemble battery packs", "electronics", "electronics_assembly", 3) {
            input("lithium_battery_cells", 14.0)
            input("aluminum_sheet", 8.0)
            input("wiring_harness", 1.0)
            input("controller_unit", 1.0)
            output("battery_pack", 16.0)
            labor("operator", 4)
            labor("technician", 1)
            energy = 90.0
        }
        recipe("make_controllers", "Make controller units", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 4.0)
            input("pcb", 6.0)
            input("capacitors", 30.0)
            input("plastic_granules", 5.0)
            output("controller_unit", 20.0)
            labor("operator", 4)
            labor("technician", 1)
            energy = 70.0
        }
        recipe("assemble_smartphone", "Assemble smartphones", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 40.0)
            input("chip_memory", 50.0)
            input("display_panel", 40.0)
            input("camera_module", 80.0)
            input("pcb", 40.0)
            input("battery_pack", 3.0)
            input("aluminum_sheet", 8.0)
            input("packaging", 40.0)
            output("smartphone", 45.0)
            labor("operator", 12)
            labor("technician", 3)
            energy = 160.0
        }
        recipe("assemble_laptop", "Assemble laptops", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 20.0)
            input("chip_memory", 40.0)
            input("display_panel", 20.0)
            input("pcb", 20.0)
            input("battery_pack", 3.0)
            input("aluminum_sheet", 20.0)
            input("packaging", 20.0)
            output("laptop", 20.0)
            labor("operator", 10)
            labor("technician", 3)
            energy = 150.0
        }
        recipe("assemble_tablet", "Assemble tablets", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 20.0)
            input("chip_memory", 30.0)
            input("display_panel", 20.0)
            input("pcb", 20.0)
            input("battery_pack", 2.0)
            input("aluminum_sheet", 10.0)
            input("packaging", 20.0)
            output("tablet", 40.0)
            labor("operator", 10)
            labor("technician", 3)
            energy = 130.0
        }
        recipe("assemble_tv", "Assemble televisions", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 10.0)
            input("display_panel", 30.0)
            input("power_supply", 30.0)
            input("plastic_granules", 40.0)
            input("packaging", 40.0)
            output("television", 35.0)
            labor("operator", 8)
            labor("technician", 2)
            energy = 110.0
        }
        recipe("assemble_console", "Assemble game consoles", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 20.0)
            input("chip_memory", 25.0)
            input("pcb", 20.0)
            input("power_supply", 20.0)
            input("plastic_granules", 30.0)
            input("packaging", 30.0)
            output("game_console", 30.0)
            labor("operator", 8)
            labor("technician", 2)
            energy = 100.0
        }
        recipe("assemble_headphones", "Assemble headphones", "electronics", "electronics_assembly", 2) {
            input("chip_basic", 60.0)
            input("plastic_granules", 30.0)
            input("copper_wire", 8.0)
            input("packaging", 20.0)
            output("headphones", 75.0)
            labor("operator", 5)
            energy = 40.0
        }
        recipe("assemble_smartwatch", "Assemble smartwatches", "electronics", "electronics_assembly", 3) {
            input("chip_logic", 12.0)
            input("sensor_pack", 30.0)
            input("display_panel", 12.0)
            input("battery_pack", 1.0)
            input("packaging", 20.0)
            output("smartwatch", 35.0)
            labor("operator", 6)
            labor("technician", 2)
            energy = 60.0
        }
        recipe("assemble_router", "Assemble routers", "electronics", "electronics_assembly", 2) {
            input("chip_logic", 8.0)
            input("pcb", 20.0)
            input("power_supply", 20.0)
            input("plastic_granules", 20.0)
            input("packaging", 20.0)
            output("router", 55.0)
            labor("operator", 5)
            energy = 40.0
        }
        recipe("build_refrigerators", "Build refrigerators", "electronics", "appliance_factory", 3) {
            input("steel_sheet", 50.0)
            input("copper_pipe", 8.0)
            input("electric_motor", 10.0)
            input("insulation", 20.0)
            input("controller_unit", 2.0)
            input("packaging", 20.0)
            output("refrigerator", 25.0)
            labor("operator", 6)
            labor("machinist", 2)
            energy = 140.0
        }
        recipe("build_washing_machines", "Build washing machines", "electronics", "appliance_factory", 3) {
            input("steel_sheet", 40.0)
            input("electric_motor", 10.0)
            input("controller_unit", 3.0)
            input("plastic_granules", 16.0)
            input("packaging", 20.0)
            output("washing_machine", 25.0)
            labor("operator", 6)
            labor("machinist", 2)
            energy = 120.0
        }
        recipe("build_microwaves", "Build microwave ovens", "electronics", "appliance_factory", 2) {
            input("steel_sheet", 20.0)
            input("power_supply", 12.0)
            input("controller_unit", 2.0)
            input("packaging", 12.0)
            output("microwave", 45.0)
            labor("operator", 5)
            energy = 70.0
        }
        recipe("cast_engine_blocks", "Build engines", "automotive", "engine_plant", 5) {
            input("castings", 60.0)
            input("forgings", 20.0)
            input("fasteners", 30.0)
            input("lubricants", 6.0)
            input("spare_parts", 6.0)
            output("engine_block", 8.0)
            labor("machinist", 10)
            labor("engineer", 2)
            energy = 240.0
        }
        recipe("build_transmissions", "Build transmissions", "automotive", "engine_plant", 4) {
            input("forgings", 28.0)
            input("castings", 28.0)
            input("fasteners", 16.0)
            input("lubricants", 4.0)
            output("transmission", 9.0)
            labor("machinist", 8)
            labor("engineer", 1)
            energy = 170.0
        }
        recipe("stamp_car_bodies", "Stamp car bodies", "automotive", "auto_parts_plant", 3) {
            input("steel_sheet", 170.0)
            input("paint", 16.0)
            input("lubricants", 3.0)
            output("car_body", 13.0)
            labor("machinist", 8)
            labor("operator", 4)
            energy = 200.0
        }
        recipe("make_seats", "Make seat sets", "automotive", "auto_parts_plant", 2) {
            input("steel_sheet", 12.0)
            input("plastic_granules", 16.0)
            input("fabric_synthetic", 40.0)
            input("foam", 14.0)
            output("seat_set", 13.0)
            labor("operator", 6)
            energy = 60.0
        }
        recipe("make_tires", "Make tires", "automotive", "auto_parts_plant", 3) {
            input("synthetic_rubber", 60.0)
            input("steel_wire", 16.0)
            input("carbon_black", 20.0)
            output("tires", 105.0)
            labor("operator", 6)
            energy = 130.0
        }
        recipe("make_brakes", "Make brake systems", "automotive", "auto_parts_plant", 3) {
            input("castings", 14.0)
            input("steel_pipe", 10.0)
            input("fasteners", 8.0)
            output("brake_system", 20.0)
            labor("machinist", 6)
            energy = 80.0
        }
        recipe("make_suspension", "Make suspension kits", "automotive", "auto_parts_plant", 3) {
            input("forgings", 14.0)
            input("steel_sheet", 12.0)
            input("fasteners", 8.0)
            input("synthetic_rubber", 6.0)
            output("suspension_kit", 19.0)
            labor("machinist", 6)
            energy = 80.0
        }
        recipe("make_auto_glass", "Cut automotive glass", "automotive", "auto_parts_plant", 2) {
            input("glass", 40.0)
            output("auto_glass", 18.0)
            labor("operator", 4)
            energy = 60.0
        }
        recipe("make_ev_drivetrain", "Build EV drivetrains", "automotive", "engine_plant", 5) {
            input("electric_motor", 20.0)
            input("controller_unit", 10.0)
            input("forgings", 10.0)
            input("wiring_harness", 8.0)
            output("ev_drivetrain", 5.0)
            labor("machinist", 8)
            labor("engineer", 3)
            energy = 190.0
        }
        recipe("assemble_compact", "Assemble compact cars", "automotive", "auto_assembly", 4) {
            input("engine_block", 12.0)
            input("transmission", 12.0)
            input("car_body", 12.0)
            input("seat_set", 12.0)
            input("tires", 60.0)
            input("brake_system", 12.0)
            input("suspension_kit", 12.0)
            input("auto_glass", 12.0)
            input("wiring_harness", 12.0)
            input("paint", 20.0)
            input("packaging", 20.0)
            output("car_compact", 6.0)
            labor("operator", 24)
            labor("engineer", 3)
            energy = 700.0
        }
        recipe("assemble_sedan", "Assemble sedans", "automotive", "auto_assembly", 4) {
            input("engine_block", 10.0)
            input("transmission", 10.0)
            input("car_body", 10.0)
            input("seat_set", 10.0)
            input("tires", 50.0)
            input("brake_system", 10.0)
            input("suspension_kit", 10.0)
            input("auto_glass", 10.0)
            input("wiring_harness", 10.0)
            input("paint", 18.0)
            input("packaging", 20.0)
            output("car_sedan", 3.9)
            labor("operator", 24)
            labor("engineer", 3)
            energy = 700.0
        }
        recipe("assemble_pickup", "Assemble pickup trucks", "automotive", "auto_assembly", 4) {
            input("engine_block", 8.0)
            input("transmission", 8.0)
            input("car_body", 8.0)
            input("seat_set", 8.0)
            input("tires", 40.0)
            input("brake_system", 8.0)
            input("suspension_kit", 8.0)
            input("auto_glass", 8.0)
            input("wiring_harness", 8.0)
            input("paint", 16.0)
            input("steel_beams", 40.0)
            output("pickup_truck", 2.7)
            labor("operator", 22)
            labor("engineer", 3)
            energy = 650.0
        }
        recipe("assemble_ev", "Assemble electric cars", "automotive", "auto_assembly", 4) {
            input("ev_drivetrain", 8.0)
            input("battery_pack", 8.0)
            input("car_body", 8.0)
            input("seat_set", 8.0)
            input("tires", 32.0)
            input("brake_system", 8.0)
            input("suspension_kit", 8.0)
            input("auto_glass", 8.0)
            input("wiring_harness", 8.0)
            input("paint", 14.0)
            output("car_ev", 2.4)
            labor("operator", 22)
            labor("engineer", 4)
            energy = 600.0
            tech = "ev_platform"
        }
        recipe("lay_carbon_fiber", "Produce carbon fiber", "aerospace", "aero_components_plant", 6) {
            input("polypropylene", 200.0)
            input("natural_gas", 60.0)
            output("carbon_fiber", 80.0)
            labor("operator", 4)
            labor("engineer", 1)
            energy = 500.0
            tech = "aerospace_engineering"
        }
        recipe("cure_composites", "Cure composite panels", "aerospace", "aero_components_plant", 6) {
            input("carbon_fiber", 40.0)
            input("resins_epoxy", 30.0)
            output("composite_panels", 20.0)
            labor("machinist", 6)
            labor("engineer", 1)
            energy = 420.0
            tech = "aerospace_engineering"
        }
        recipe("build_landing_gear", "Build landing gear", "aerospace", "aero_components_plant", 30) {
            input("forgings", 400.0)
            input("stainless_steel", 150.0)
            input("lubricants", 40.0)
            output("landing_gear", 0.5)
            labor("machinist", 12)
            labor("engineer", 4)
            energy = 1500.0
            tech = "aerospace_engineering"
        }
        recipe("build_avionics", "Integrate avionics", "aerospace", "aero_components_plant", 40) {
            input("chip_logic", 500.0)
            input("pcb", 400.0)
            input("display_panel", 120.0)
            input("sensor_pack", 300.0)
            input("wiring_harness", 60.0)
            output("avionics_suite", 0.5)
            labor("engineer", 14)
            labor("technician", 10)
            energy = 1800.0
            tech = "aerospace_engineering"
        }
        recipe("build_jet_engine", "Build jet engines", "aerospace", "aero_components_plant", 60) {
            input("forgings", 600.0)
            input("titanium_alloy", 300.0)
            input("stainless_steel", 300.0)
            input("lubricants", 60.0)
            input("sensor_pack", 80.0)
            output("jet_engine", 0.5)
            labor("machinist", 16)
            labor("engineer", 8)
            energy = 2400.0
            tech = "aerospace_engineering"
        }
        recipe("build_aircraft_frame", "Build aircraft frames", "aerospace", "aircraft_factory", 120) {
            input("aluminum_sheet", 1200.0)
            input("composite_panels", 900.0)
            input("titanium_alloy", 500.0)
            input("fasteners", 2000.0)
            input("paint", 400.0)
            output("aircraft_frame", 1.8)
            labor("machinist", 40)
            labor("engineer", 10)
            energy = 5000.0
            tech = "aerospace_engineering"
        }
        recipe("assemble_light_aircraft", "Assemble light aircraft", "aerospace", "aircraft_factory", 40) {
            input("aluminum_sheet", 160.0)
            input("composite_panels", 80.0)
            input("fasteners", 200.0)
            input("avionics_basic", 1.0)
            input("engine_block", 2.0)
            input("paint", 20.0)
            output("light_aircraft", 1.2)
            labor("machinist", 20)
            labor("engineer", 4)
            energy = 1200.0
            tech = "aerospace_engineering"
        }
        recipe("assemble_jetliner", "Assemble jetliners", "aerospace", "aircraft_factory", 180) {
            input("aircraft_frame", 1.0)
            input("jet_engine", 2.0)
            input("avionics_suite", 1.0)
            input("landing_gear", 1.0)
            input("seat_set", 180.0)
            input("wiring_harness", 120.0)
            input("paint", 600.0)
            output("jetliner", 0.5)
            labor("machinist", 60)
            labor("engineer", 20)
            labor("technician", 20)
            energy = 8000.0
            tech = "aerospace_engineering"
        }
        recipe("build_avionics_basic", "Build basic avionics", "aerospace", "aero_components_plant", 15) {
            input("chip_logic", 30.0)
            input("pcb", 40.0)
            input("display_panel", 6.0)
            input("sensor_pack", 20.0)
            output("avionics_basic", 0.5)
            labor("engineer", 4)
            labor("technician", 4)
            energy = 500.0
            tech = "aerospace_engineering"
        }
        recipe("refine_titanium", "Refine titanium alloy", "aerospace", "aero_components_plant", 8) {
            input("stainless_steel", 100.0)
            input("nickel_ingot", 60.0)
            input("aluminum_ingot", 40.0)
            output("titanium_alloy", 105.0)
            labor("operator", 5)
            labor("engineer", 1)
            energy = 700.0
            tech = "aerospace_engineering"
        }
        recipe("build_machine_tools", "Build machine tools", "machinery", "machine_tool_works", 12) {
            input("castings", 220.0)
            input("forgings", 80.0)
            input("servo_drive", 10.0)
            input("controller_unit", 6.0)
            input("lubricants", 10.0)
            output("machine_tool", 1.0)
            labor("machinist", 12)
            labor("engineer", 3)
            energy = 400.0
        }
        recipe("build_conveyors", "Build conveyor systems", "machinery", "machine_tool_works", 8) {
            input("steel_beams", 60.0)
            input("electric_motor", 20.0)
            input("controller_unit", 4.0)
            input("steel_wire", 40.0)
            output("conveyor_system", 1.5)
            labor("machinist", 8)
            labor("electrician", 2)
            energy = 260.0
        }
        recipe("build_gearboxes", "Build gearboxes", "machinery", "machine_tool_works", 5) {
            input("forgings", 36.0)
            input("castings", 16.0)
            input("lubricants", 6.0)
            input("fasteners", 12.0)
            output("gearbox", 3.6)
            labor("machinist", 8)
            energy = 160.0
        }
        recipe("build_servo_drives", "Build servo drives", "robotics", "robotics_factory", 3) {
            input("electric_motor", 1.0)
            input("chip_logic", 1.0)
            input("sensor_pack", 1.0)
            input("pcb", 2.0)
            output("servo_drive", 7.0)
            labor("technician", 4)
            labor("engineer", 1)
            energy = 60.0
            tech = "industrial_robotics"
        }
        recipe("build_robot_arms", "Build robot arms", "robotics", "robotics_factory", 10) {
            input("servo_drive", 60.0)
            input("castings", 40.0)
            input("aluminum_sheet", 40.0)
            input("controller_unit", 10.0)
            input("sensor_pack", 20.0)
            output("robot_arm", 4.6)
            labor("technician", 10)
            labor("engineer", 4)
            energy = 400.0
            tech = "industrial_robotics"
        }
        recipe("build_industrial_robots", "Build industrial robots", "robotics", "robotics_factory", 12) {
            input("robot_arm", 18.0)
            input("controller_unit", 18.0)
            input("sensor_pack", 40.0)
            input("safety_cage", 1.0)
            output("industrial_robot", 9.0)
            labor("technician", 10)
            labor("engineer", 4)
            energy = 450.0
            tech = "industrial_robotics"
        }
        recipe("build_service_robots", "Build service robots", "robotics", "robotics_factory", 10) {
            input("servo_drive", 80.0)
            input("chip_logic", 60.0)
            input("sensor_pack", 80.0)
            input("battery_pack", 10.0)
            input("plastic_granules", 100.0)
            output("service_robot", 9.0)
            labor("technician", 10)
            labor("engineer", 5)
            energy = 380.0
            tech = "industrial_robotics"
        }
        recipe("build_delivery_drones", "Build delivery drones", "robotics", "robotics_factory", 6) {
            input("electric_motor", 40.0)
            input("chip_logic", 20.0)
            input("battery_pack", 10.0)
            input("carbon_fiber", 12.0)
            input("sensor_pack", 20.0)
            output("delivery_drone", 7.0)
            labor("technician", 6)
            labor("engineer", 2)
            energy = 220.0
            tech = "industrial_robotics"
        }
        recipe("build_turbine_blades", "Cast turbine blades", "machinery", "turbine_factory", 8) {
            input("composite_panels", 60.0)
            input("resins_epoxy", 40.0)
            output("turbine_blades", 12.0)
            labor("machinist", 8)
            labor("engineer", 2)
            energy = 300.0
        }
        recipe("build_wind_turbines", "Build wind turbines", "machinery", "turbine_factory", 25) {
            input("turbine_blades", 120.0)
            input("gearbox", 40.0)
            input("steel_beams", 900.0)
            input("electric_motor", 30.0)
            input("controller_unit", 24.0)
            output("wind_turbine", 1.0)
            labor("machinist", 20)
            labor("engineer", 6)
            energy = 1200.0
        }
        recipe("make_solar_panels", "Assemble solar panels", "machinery", "panel_plant", 3) {
            input("solar_cells", 60.0)
            input("aluminum_sheet", 14.0)
            input("glass", 30.0)
            input("copper_wire", 8.0)
            output("solar_panel", 95.0)
            labor("operator", 6)
            labor("technician", 1)
            energy = 120.0
        }

        tech("photolithography", "Photolithography", TechCategory.DIGITAL, 4, 12000000.0, 360) {
            requires("wafer_growth")
            unlocksRecipe("fab_basic_chips", "fab_logic_chips", "fab_memory_chips")
            unlocksFacility("semiconductor_fab")
        }
        tech("wafer_growth", "Wafer growth", TechCategory.MATERIALS, 3, 4000000.0, 240) {
            unlocksRecipe("grow_silicon", "slice_wafers")
            unlocksFacility("wafer_plant")
        }
        tech("ev_platform", "Electric vehicle platform", TechCategory.PRODUCTION, 3, 3500000.0, 220) {
            requires("battery_manufacturing")
            unlocksRecipe("assemble_ev")
        }
        tech("aerospace_engineering", "Aerospace engineering", TechCategory.PRODUCTION, 4, 20000000.0, 400) {
            unlocksRecipe("build_jet_engine", "assemble_jetliner")
            unlocksFacility("aircraft_factory", "aero_components_plant")
        }
        tech("industrial_robotics", "Industrial robotics", TechCategory.PRODUCTION, 3, 6000000.0, 260) {
            unlocksRecipe("build_robot_arms", "build_industrial_robots")
            unlocksFacility("robotics_factory")
        }
        tech("lean_manufacturing", "Lean manufacturing", TechCategory.MANAGEMENT, 2, 700000.0, 90) {
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "automotive")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "electronics")
        }
        tech("factory_automation", "Factory automation", TechCategory.PRODUCTION, 3, 1800000.0, 150) {
            requires("lean_manufacturing")
            effect(EffectKind.LABOR_SAVINGS, 0.12)
            effect(EffectKind.THROUGHPUT_BONUS, 0.06)
        }
        tech("prefab_construction", "Prefab construction", TechCategory.PRODUCTION, 2, 600000.0, 100) {
            effect(EffectKind.THROUGHPUT_BONUS, 0.1, industry = "building")
        }
    }
}

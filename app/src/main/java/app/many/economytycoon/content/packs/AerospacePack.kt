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

object AerospacePack {
    val pack: ContentPack = contentPack("aerospace", 1, 0) {
        industry("aero_materials", "Aerospace materials", IndustryFamily.AEROSPACE, "Titanium, aluminum-lithium, prepregs and thermal coatings")
        industry("aerostructures", "Aerostructures", IndustryFamily.AEROSPACE, "Wing boxes, fuselage sections, rotor systems and cabins")
        industry("propulsion_and_avionics", "Propulsion and avionics", IndustryFamily.AEROSPACE, "Turbine discs, engines, flight computers and radar")
        industry("aircraft_assembly", "Aircraft final assembly", IndustryFamily.AEROSPACE, "Business jets, regional turboprops, helicopters and survey drones")
        industry("space_systems", "Space systems", IndustryFamily.AEROSPACE, "Solar arrays, satellite buses, satellites and launch vehicles")

        good("titanium_ore", "Titanium ore", GoodCategory.RAW, 85.0, VolatilityClass.VOLATILE)
        good("titanium_sponge", "Titanium sponge", GoodCategory.INTERMEDIATE, 310.0, VolatilityClass.VOLATILE)
        good("titanium_mill_products", "Titanium mill products", GoodCategory.INTERMEDIATE, 520.0, VolatilityClass.VOLATILE)
        good("aluminum_lithium_sheet", "Aluminum-lithium sheet", GoodCategory.INTERMEDIATE, 210.0, VolatilityClass.STABLE)
        good("composite_prepreg", "Composite prepreg", GoodCategory.INTERMEDIATE, 340.0, VolatilityClass.STABLE)
        good("honeycomb_core", "Honeycomb core", GoodCategory.INTERMEDIATE, 120.0, VolatilityClass.STABLE)
        good("thermal_barrier_coating", "Thermal barrier coating", GoodCategory.INTERMEDIATE, 160.0, VolatilityClass.STABLE)
        good("aero_fasteners", "Aerospace fasteners", GoodCategory.INTERMEDIATE, 85.0, VolatilityClass.STABLE)
        good("forged_turbine_discs", "Forged turbine discs", GoodCategory.INDUSTRIAL, 8640.0, weight = 3000, volume = 4000)
        good("wing_box", "Wing box", GoodCategory.INDUSTRIAL, 537000.0, weight = 5000, volume = 8000)
        good("fuselage_section", "Fuselage section", GoodCategory.INDUSTRIAL, 388000.0, weight = 5000, volume = 8000)
        good("rotor_system", "Rotor system", GoodCategory.INDUSTRIAL, 461000.0, weight = 5000, volume = 8000)
        good("cabin_interior_module", "Cabin interior module", GoodCategory.INDUSTRIAL, 117000.0, weight = 5000, volume = 8000)
        good("flight_control_computer", "Flight control computer", GoodCategory.INDUSTRIAL, 35300.0, weight = 5000, volume = 8000)
        good("radar_unit", "Radar unit", GoodCategory.INDUSTRIAL, 81100.0, weight = 5000, volume = 8000)
        good("satcom_terminal", "Satcom terminal", GoodCategory.INDUSTRIAL, 34600.0, weight = 5000, volume = 8000)
        good("attitude_control_unit", "Attitude control unit", GoodCategory.INDUSTRIAL, 46000.0, weight = 5000, volume = 8000)
        good("solar_array", "Satellite solar array", GoodCategory.INDUSTRIAL, 116000.0, weight = 5000, volume = 8000)
        good("satellite_bus", "Satellite bus", GoodCategory.INDUSTRIAL, 435000.0, weight = 5000, volume = 8000)
        good("turboprop_engine", "Turboprop engine", GoodCategory.INDUSTRIAL, 393000.0, weight = 5000, volume = 8000)
        good("turboshaft_engine", "Turboshaft engine", GoodCategory.INDUSTRIAL, 296000.0, weight = 5000, volume = 8000)
        good("rocket_engine", "Rocket engine", GoodCategory.INDUSTRIAL, 942000.0, weight = 5000, volume = 8000)
        good("business_jet", "Business jet", GoodCategory.INDUSTRIAL, 9560000.0, weight = 5000, volume = 8000)
        good("regional_turboprop", "Regional turboprop", GoodCategory.INDUSTRIAL, 4620000.0, weight = 5000, volume = 8000)
        good("helicopter", "Helicopter", GoodCategory.INDUSTRIAL, 2170000.0, weight = 5000, volume = 8000)
        good("survey_uav", "Survey UAV", GoodCategory.INDUSTRIAL, 28500.0, weight = 5000, volume = 8000)
        good("communications_satellite", "Communications satellite", GoodCategory.INDUSTRIAL, 2080000.0, weight = 5000, volume = 8000)
        good("launch_vehicle", "Launch vehicle", GoodCategory.INDUSTRIAL, 5380000.0, weight = 5000, volume = 8000)

        role("aerospace_technician", "Aerospace technician", SkillBand.SPECIALIST, 150.0, 150)

        machine("vacuum_arc_remelter", "Vacuum arc remelter", 3000000.0, throughput = 1.1, maintenance = 220.0, tech = "titanium_metallurgy")
        machine("autoclave", "Composite autoclave", 2400000.0, throughput = 1.2, wearPerBatchPpm = 450, maintenance = 200.0, tech = "composite_layup")
        machine("five_axis_mill", "Five-axis mill", 1600000.0, throughput = 1.25, wearPerBatchPpm = 480, maintenance = 140.0, laborSavings = 0.08, tech = "aerostructures_design")
        machine("assembly_jig", "Precision assembly jig", 1200000.0, throughput = 1.15, wearPerBatchPpm = 400, maintenance = 100.0, tech = "advanced_aircraft_assembly")

        facility("titanium_mine", "Titanium mine", FacilityClass.MINE, IndustryFamily.AEROSPACE) {
            cost = 2800000.0; buildTicks = 100; lines = 2; storage = 40000.0; upkeep = 950.0
            energyPerLine = 60.0
            extracts = "titanium_ore"
            staff("miner", 3)
            staff("engineer", 1)
        }
        facility("titanium_works", "Titanium works", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 8800000.0; buildTicks = 130; lines = 2; storage = 10000.0; upkeep = 2600.0
            energyPerLine = 420.0
            tech = "titanium_metallurgy"
            staff("operator", 4)
            staff("engineer", 2)
        }
        facility("advanced_materials_plant", "Advanced materials plant", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 9600000.0; buildTicks = 130; lines = 3; storage = 10000.0; upkeep = 2800.0
            energyPerLine = 300.0
            tech = "composite_layup"
            staff("operator", 4)
            staff("aerospace_technician", 2)
            staff("engineer", 1)
        }
        facility("aerostructures_plant", "Aerostructures plant", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 36000000.0; buildTicks = 220; lines = 2; storage = 15000.0; upkeep = 8500.0
            energyPerLine = 520.0
            tech = "aerostructures_design"
            staff("machinist", 14)
            staff("aerospace_technician", 6)
            staff("engineer", 6)
        }
        facility("propulsion_works", "Propulsion works", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 42000000.0; buildTicks = 240; lines = 2; storage = 15000.0; upkeep = 9500.0
            energyPerLine = 600.0
            tech = "propulsion_engineering"
            staff("machinist", 12)
            staff("engineer", 8)
        }
        facility("avionics_works", "Avionics works", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 24000000.0; buildTicks = 180; lines = 3; storage = 20000.0; upkeep = 6200.0
            energyPerLine = 400.0
            tech = "avionics_integration"
            staff("engineer", 8)
            staff("technician", 8)
            staff("aerospace_technician", 4)
        }
        facility("final_assembly_hangar", "Final assembly hangar", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 90000000.0; buildTicks = 320; lines = 2; storage = 20000.0; upkeep = 18000.0
            energyPerLine = 800.0
            tech = "advanced_aircraft_assembly"
            staff("machinist", 24)
            staff("engineer", 12)
            staff("aerospace_technician", 10)
        }
        facility("space_systems_works", "Space systems works", FacilityClass.FACTORY, IndustryFamily.AEROSPACE) {
            cost = 70000000.0; buildTicks = 300; lines = 2; storage = 40000.0; upkeep = 15000.0
            energyPerLine = 700.0
            tech = "space_systems_engineering"
            staff("engineer", 14)
            staff("aerospace_technician", 10)
            staff("technician", 8)
        }

        recipe("mine_titanium", "Mine titanium ore", "aero_materials", "titanium_mine", 10) {
            input("diesel", 3830.0)
            input("explosives", 765.0)
            input("water", 4600.0)
            output("titanium_ore", 1970.0)
            labor("miner", 8)
            labor("engineer", 1)
            energy = 3710.0
        }
        recipe("reduce_titanium_sponge", "Reduce titanium sponge", "aero_materials", "titanium_works", 6) {
            input("titanium_ore", 1540.0)
            input("chlorine", 280.0)
            input("coal", 210.0)
            input("industrial_gases", 140.0)
            output("titanium_sponge", 695.0)
            waste("chemical_waste", 140.0)
            labor("operator", 4)
            labor("engineer", 2)
            energy = 2931.0
        }
        recipe("melt_titanium_mill_products", "Melt titanium mill products", "aero_materials", "titanium_works", 8) {
            input("titanium_sponge", 525.0)
            input("aluminum_ingot", 88.0)
            input("nickel_ingot", 35.0)
            output("titanium_mill_products", 535.0)
            labor("operator", 4)
            labor("engineer", 2)
            machine("vacuum_arc_remelter")
            energy = 4552.0
        }
        recipe("roll_aluminum_lithium", "Roll aluminum-lithium sheet", "aero_materials", "titanium_works", 5) {
            input("aluminum_ingot", 825.0)
            input("lithium_carbonate", 62.0)
            input("lubricants", 41.0)
            output("aluminum_lithium_sheet", 880.0)
            labor("operator", 4)
            labor("engineer", 1)
            energy = 4221.0
        }
        recipe("lay_composite_prepreg", "Lay composite prepreg", "aero_materials", "advanced_materials_plant", 5) {
            input("carbon_fiber", 240.0)
            input("resins_epoxy", 180.0)
            input("solvents", 24.0)
            output("composite_prepreg", 355.0)
            labor("operator", 4)
            labor("aerospace_technician", 2)
            machine("autoclave")
            energy = 1566.0
        }
        recipe("bond_honeycomb_core", "Bond honeycomb core", "aero_materials", "advanced_materials_plant", 4) {
            input("aluminum_sheet", 370.0)
            input("adhesives", 220.0)
            output("honeycomb_core", 855.0)
            labor("operator", 4)
            labor("aerospace_technician", 1)
            energy = 2333.0
        }
        recipe("spray_thermal_coating", "Spray thermal barrier coating", "aero_materials", "advanced_materials_plant", 4) {
            input("rare_earth_oxide", 71.0)
            input("silica_sand", 470.0)
            input("solvents", 140.0)
            output("thermal_barrier_coating", 605.0)
            labor("operator", 4)
            labor("aerospace_technician", 2)
            energy = 2131.0
        }
        recipe("forge_aero_fasteners", "Forge aerospace fasteners", "aero_materials", "advanced_materials_plant", 3) {
            input("titanium_mill_products", 89.0)
            input("lubricants", 30.0)
            output("aero_fasteners", 910.0)
            labor("operator", 4)
            labor("aerospace_technician", 1)
            energy = 1750.0
        }
        recipe("assemble_wing_boxes", "Assemble wing boxes", "aerostructures", "aerostructures_plant", 9) {
            input("aluminum_lithium_sheet", 1800.0)
            input("composite_prepreg", 900.0)
            input("honeycomb_core", 700.0)
            input("aero_fasteners", 800.0)
            output("wing_box", 2.0)
            labor("machinist", 14)
            labor("aerospace_technician", 6)
            labor("engineer", 6)
            machine("five_axis_mill")
            energy = 1999.0
        }
        recipe("assemble_fuselage_sections", "Assemble fuselage sections", "aerostructures", "aerostructures_plant", 6) {
            input("aluminum_lithium_sheet", 1500.0)
            input("composite_prepreg", 600.0)
            input("aero_fasteners", 900.0)
            input("paint", 200.0)
            output("fuselage_section", 2.0)
            labor("machinist", 14)
            labor("aerospace_technician", 6)
            labor("engineer", 6)
            machine("five_axis_mill")
            energy = 1801.0
        }
        recipe("build_rotor_systems", "Build rotor systems", "aerostructures", "aerostructures_plant", 11) {
            input("titanium_mill_products", 900.0)
            input("composite_prepreg", 900.0)
            input("gearbox", 60.0)
            input("aero_fasteners", 1200.0)
            output("rotor_system", 3.0)
            labor("machinist", 14)
            labor("aerospace_technician", 6)
            labor("engineer", 6)
            energy = 2399.0
            tech = "aerostructures_design"
        }
        recipe("outfit_cabin_modules", "Outfit cabin interior modules", "aerostructures", "aerostructures_plant", 8) {
            input("seat_set", 240.0)
            input("composite_panels", 480.0)
            input("wiring_harness", 360.0)
            input("upholstery_fabric", 960.0)
            input("technical_fabric", 240.0)
            output("cabin_interior_module", 8.0)
            labor("machinist", 14)
            labor("aerospace_technician", 6)
            labor("engineer", 6)
            energy = 1919.0
        }
        recipe("forge_turbine_discs", "Forge turbine discs", "propulsion_and_avionics", "propulsion_works", 8) {
            input("titanium_mill_products", 960.0)
            input("nickel_ingot", 670.0)
            input("forgings", 1630.0)
            output("forged_turbine_discs", 120.0)
            labor("machinist", 12)
            labor("engineer", 8)
            machine("vacuum_arc_remelter")
            energy = 3839.0
        }
        recipe("build_turboprop_engines", "Build turboprop engines", "propulsion_and_avionics", "propulsion_works", 12) {
            input("forged_turbine_discs", 96.0)
            input("titanium_mill_products", 720.0)
            input("thermal_barrier_coating", 120.0)
            input("sensor_pack", 240.0)
            input("lubricants", 72.0)
            output("turboprop_engine", 4.0)
            labor("machinist", 12)
            labor("engineer", 8)
            energy = 3599.0
        }
        recipe("build_turboshaft_engines", "Build turboshaft engines", "propulsion_and_avionics", "propulsion_works", 9) {
            input("forged_turbine_discs", 72.0)
            input("titanium_mill_products", 540.0)
            input("thermal_barrier_coating", 96.0)
            input("sensor_pack", 190.0)
            input("lubricants", 60.0)
            output("turboshaft_engine", 4.0)
            labor("machinist", 12)
            labor("engineer", 8)
            energy = 3117.0
            tech = "rotorcraft_dynamics"
        }
        recipe("build_rocket_engines", "Build rocket engines", "propulsion_and_avionics", "propulsion_works", 15) {
            input("forged_turbine_discs", 120.0)
            input("stainless_steel", 1440.0)
            input("thermal_barrier_coating", 360.0)
            input("hydrogen", 1200.0)
            input("sensor_pack", 360.0)
            output("rocket_engine", 2.0)
            labor("machinist", 12)
            labor("engineer", 8)
            energy = 7197.0
            tech = "propulsion_engineering"
        }
        recipe("build_flight_computers", "Build flight control computers", "propulsion_and_avionics", "avionics_works", 4) {
            input("chip_logic", 865.0)
            input("pcb", 650.0)
            input("sensor_pack", 430.0)
            input("controller_unit", 215.0)
            output("flight_control_computer", 6.0)
            labor("engineer", 8)
            labor("technician", 8)
            labor("aerospace_technician", 4)
            energy = 4319.0
        }
        recipe("build_radar_units", "Build radar units", "propulsion_and_avionics", "avionics_works", 9) {
            input("rf_modules", 2100.0)
            input("chip_logic", 2400.0)
            input("display_panel", 300.0)
            input("power_supply", 600.0)
            output("radar_unit", 6.0)
            labor("engineer", 8)
            labor("technician", 8)
            labor("aerospace_technician", 4)
            energy = 8998.0
        }
        recipe("build_satcom_terminals", "Build satcom terminals", "propulsion_and_avionics", "avionics_works", 5) {
            input("rf_modules", 1440.0)
            input("chip_logic", 720.0)
            input("power_supply", 290.0)
            input("aluminum_sheet", 430.0)
            output("satcom_terminal", 8.0)
            labor("engineer", 8)
            labor("technician", 8)
            labor("aerospace_technician", 4)
            energy = 5752.0
        }
        recipe("build_attitude_control", "Build attitude control units", "propulsion_and_avionics", "avionics_works", 7) {
            input("mems_sensors", 4480.0)
            input("controller_unit", 450.0)
            input("servo_drive", 225.0)
            input("chip_logic", 450.0)
            output("attitude_control_unit", 8.0)
            labor("engineer", 8)
            labor("technician", 8)
            labor("aerospace_technician", 4)
            energy = 4476.0
        }
        recipe("assemble_business_jets", "Assemble business jets", "aircraft_assembly", "final_assembly_hangar", 34) {
            input("wing_box", 1.0)
            input("fuselage_section", 1.0)
            input("jet_engine", 2.0)
            input("avionics_suite", 1.0)
            input("landing_gear", 1.0)
            input("cabin_interior_module", 1.0)
            input("flight_control_computer", 1.0)
            input("radar_unit", 1.0)
            input("paint", 300.0)
            output("business_jet", 1.0)
            labor("machinist", 24)
            labor("engineer", 12)
            labor("aerospace_technician", 10)
            machine("assembly_jig")
            energy = 3000.0
        }
        recipe("assemble_regional_turboprops", "Assemble regional turboprops", "aircraft_assembly", "final_assembly_hangar", 17) {
            input("wing_box", 2.0)
            input("fuselage_section", 2.0)
            input("turboprop_engine", 2.0)
            input("avionics_basic", 2.0)
            input("landing_gear", 1.0)
            input("cabin_interior_module", 2.0)
            input("flight_control_computer", 1.0)
            input("radar_unit", 1.0)
            input("paint", 400.0)
            output("regional_turboprop", 1.0)
            labor("machinist", 24)
            labor("engineer", 12)
            labor("aerospace_technician", 10)
            machine("assembly_jig")
            energy = 4001.0
        }
        recipe("assemble_helicopters", "Assemble helicopters", "aircraft_assembly", "final_assembly_hangar", 17) {
            input("rotor_system", 2.0)
            input("turboshaft_engine", 4.0)
            input("fuselage_section", 2.0)
            input("avionics_basic", 2.0)
            input("cabin_interior_module", 2.0)
            input("flight_control_computer", 2.0)
            input("paint", 240.0)
            output("helicopter", 2.0)
            labor("machinist", 24)
            labor("engineer", 12)
            labor("aerospace_technician", 10)
            machine("assembly_jig")
            energy = 3597.0
            tech = "rotorcraft_dynamics"
        }
        recipe("assemble_survey_uavs", "Assemble survey UAVs", "aircraft_assembly", "final_assembly_hangar", 4) {
            input("composite_prepreg", 1600.0)
            input("electric_motor", 510.0)
            input("sensor_pack", 1920.0)
            input("battery_pack", 255.0)
            input("controller_unit", 385.0)
            input("camera_module", 770.0)
            input("rf_modules", 255.0)
            output("survey_uav", 40.0)
            labor("machinist", 24)
            labor("engineer", 12)
            labor("aerospace_technician", 10)
            energy = 3839.0
        }
        recipe("assemble_solar_arrays", "Assemble satellite solar arrays", "space_systems", "space_systems_works", 12) {
            input("solar_cells", 9650.0)
            input("aluminum_lithium_sheet", 1200.0)
            input("copper_wire", 965.0)
            input("glass", 725.0)
            output("solar_array", 20.0)
            labor("engineer", 14)
            labor("aerospace_technician", 10)
            labor("technician", 8)
            energy = 6021.0
        }
        recipe("build_satellite_buses", "Build satellite buses", "space_systems", "space_systems_works", 5) {
            input("honeycomb_core", 120.0)
            input("composite_prepreg", 80.0)
            input("attitude_control_unit", 2.0)
            input("solar_array", 4.0)
            input("battery_pack", 12.0)
            input("flight_control_computer", 2.0)
            output("satellite_bus", 2.0)
            labor("engineer", 14)
            labor("aerospace_technician", 10)
            labor("technician", 8)
            energy = 601.0
        }
        recipe("integrate_comms_satellites", "Integrate communications satellites", "space_systems", "space_systems_works", 11) {
            input("satellite_bus", 2.0)
            input("satcom_terminal", 8.0)
            input("solar_array", 4.0)
            input("thermal_barrier_coating", 40.0)
            output("communications_satellite", 1.0)
            labor("engineer", 14)
            labor("aerospace_technician", 10)
            labor("technician", 8)
            energy = 1001.0
        }
        recipe("assemble_launch_vehicles", "Assemble launch vehicles", "space_systems", "space_systems_works", 28) {
            input("rocket_engine", 4.0)
            input("composite_prepreg", 400.0)
            input("aluminum_lithium_sheet", 450.0)
            input("flight_control_computer", 3.0)
            input("attitude_control_unit", 2.0)
            input("hydrogen", 150.0)
            output("launch_vehicle", 1.0)
            labor("engineer", 14)
            labor("aerospace_technician", 10)
            labor("technician", 8)
            energy = 900.0
            tech = "space_systems_engineering"
        }

        tech("titanium_metallurgy", "Titanium metallurgy", TechCategory.MATERIALS, 3, 4500000.0, 220) {
            requires("electric_arc_furnace")
            unlocksFacility("titanium_works")
            unlocksMachine("vacuum_arc_remelter")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "aero_materials")
        }
        tech("composite_layup", "Composite layup", TechCategory.MATERIALS, 4, 7000000.0, 260) {
            requires("aerospace_engineering")
            unlocksFacility("advanced_materials_plant")
            unlocksMachine("autoclave")
        }
        tech("aerostructures_design", "Aerostructures design", TechCategory.PRODUCTION, 4, 11000000.0, 300) {
            requires("aerospace_engineering", "composite_layup")
            unlocksRecipe("build_rotor_systems")
            unlocksFacility("aerostructures_plant")
            unlocksMachine("five_axis_mill")
        }
        tech("propulsion_engineering", "Propulsion engineering", TechCategory.PRODUCTION, 4, 14000000.0, 320) {
            requires("aerospace_engineering", "titanium_metallurgy")
            unlocksRecipe("build_rocket_engines")
            unlocksFacility("propulsion_works")
        }
        tech("avionics_integration", "Avionics integration", TechCategory.DIGITAL, 4, 10000000.0, 280) {
            requires("aerospace_engineering", "photolithography")
            unlocksFacility("avionics_works")
        }
        tech("advanced_aircraft_assembly", "Advanced aircraft assembly", TechCategory.PRODUCTION, 4, 16000000.0, 340) {
            requires("aerostructures_design", "propulsion_engineering", "avionics_integration")
            unlocksFacility("final_assembly_hangar")
            unlocksMachine("assembly_jig")
        }
        tech("space_systems_engineering", "Space systems engineering", TechCategory.PRODUCTION, 4, 18000000.0, 360) {
            requires("avionics_integration", "composite_layup")
            unlocksRecipe("assemble_launch_vehicles")
            unlocksFacility("space_systems_works")
        }
        tech("additive_manufacturing", "Additive manufacturing", TechCategory.PRODUCTION, 3, 3500000.0, 200) {
            requires("industrial_robotics")
            effect(EffectKind.INPUT_SAVINGS, 0.06, industry = "aerostructures")
            effect(EffectKind.INPUT_SAVINGS, 0.04, industry = "propulsion_and_avionics")
        }
        tech("flight_data_analytics", "Flight data analytics", TechCategory.DIGITAL, 3, 2800000.0, 160) {
            requires("cloud_infrastructure")
            effect(EffectKind.QUALITY_BONUS, 0.1, industry = "aircraft_assembly")
            effect(EffectKind.LABOR_SAVINGS, 0.05, industry = "aircraft_assembly")
        }
        tech("rotorcraft_dynamics", "Rotorcraft dynamics", TechCategory.PRODUCTION, 4, 12000000.0, 300) {
            requires("aerostructures_design")
            unlocksRecipe("assemble_helicopters", "build_turboshaft_engines")
            effect(EffectKind.YIELD_BONUS, 0.04, industry = "aircraft_assembly")
        }

        demand("business_jet", 0L, 0L, 1L, 0.7, incomeElasticity = 0.9, nonConsumers = 0.999)
        demand("regional_turboprop", 0L, 0L, 1L, 0.7, nonConsumers = 0.999)
        demand("helicopter", 0L, 0L, 1L, 0.8, incomeElasticity = 0.9, nonConsumers = 0.999)
        demand("survey_uav", 0L, 1L, 1L, 0.8, nonConsumers = 0.99)
        demand("communications_satellite", 0L, 0L, 1L, 0.5, incomeElasticity = 0.6, nonConsumers = 0.9995)
        demand("launch_vehicle", 0L, 0L, 1L, 0.5, incomeElasticity = 0.6, nonConsumers = 0.9995)
    }
}

"""Aerospace: titanium, advanced materials, structures, propulsion, avionics, business and regional aircraft, helicopters, satellites."""
from expgen import ExpSpec


def build():
    s = ExpSpec("aerospace", "AerospacePack")
    s.role("aerospace_technician", "Aerospace technician", "SPECIALIST", 150.0, 150)

    s.industry("aero_materials", "Aerospace materials", "AEROSPACE", "Titanium, aluminum-lithium, prepregs and thermal coatings")
    s.industry("aerostructures", "Aerostructures", "AEROSPACE", "Wing boxes, fuselage sections, rotor systems and cabins")
    s.industry("propulsion_and_avionics", "Propulsion and avionics", "AEROSPACE", "Turbine discs, engines, flight computers and radar")
    s.industry("aircraft_assembly", "Aircraft final assembly", "AEROSPACE", "Business jets, regional turboprops, helicopters and survey drones")
    s.industry("space_systems", "Space systems", "AEROSPACE", "Solar arrays, satellite buses, satellites and launch vehicles")

    s.add_goods("RAW", [("titanium_ore", "Titanium ore", 85, 0, "VOLATILE")])
    s.add_goods("INTERMEDIATE", [
        ("titanium_sponge", "Titanium sponge", 310, 0, "VOLATILE"), ("titanium_mill_products", "Titanium mill products", 520, 0, "VOLATILE"),
        ("aluminum_lithium_sheet", "Aluminum-lithium sheet", 210), ("composite_prepreg", "Composite prepreg", 340), ("honeycomb_core", "Honeycomb core", 120),
        ("thermal_barrier_coating", "Thermal barrier coating", 160), ("aero_fasteners", "Aerospace fasteners", 85),
    ], "STABLE")
    s.add_goods("INDUSTRIAL", [
        ("forged_turbine_discs", "Forged turbine discs", 12_000), ("wing_box", "Wing box", 1_150_000), ("fuselage_section", "Fuselage section", 900_000),
        ("rotor_system", "Rotor system", 520_000), ("cabin_interior_module", "Cabin interior module", 140_000), ("flight_control_computer", "Flight control computer", 62_000),
        ("radar_unit", "Radar unit", 88_000), ("satcom_terminal", "Satcom terminal", 41_000), ("attitude_control_unit", "Attitude control unit", 54_000),
        ("solar_array", "Satellite solar array", 120_000), ("satellite_bus", "Satellite bus", 650_000), ("turboprop_engine", "Turboprop engine", 780_000),
        ("turboshaft_engine", "Turboshaft engine", 640_000), ("rocket_engine", "Rocket engine", 1_900_000),
        ("business_jet", "Business jet", 9_800_000), ("regional_turboprop", "Regional turboprop", 14_000_000), ("helicopter", "Helicopter", 2_400_000),
        ("survey_uav", "Survey UAV", 180_000), ("communications_satellite", "Communications satellite", 28_000_000), ("launch_vehicle", "Launch vehicle", 36_000_000),
    ])

    s.fac("titanium_mine", "Titanium mine", "MINE", "AEROSPACE", 2_800_000, 100, 2, 950, 60.0, {"miner": 3, "engineer": 1}, 600, extracts="titanium_ore", storage=40_000)
    s.fac("titanium_works", "Titanium works", "FACTORY", "AEROSPACE", 8_800_000, 130, 2, 2_600, 420.0, {"operator": 4, "engineer": 2}, 800)
    s.fac("advanced_materials_plant", "Advanced materials plant", "FACTORY", "AEROSPACE", 9_600_000, 130, 3, 2_800, 300.0, {"operator": 4, "aerospace_technician": 2, "engineer": 1}, 850)
    s.fac("aerostructures_plant", "Aerostructures plant", "FACTORY", "AEROSPACE", 36_000_000, 220, 2, 8_500, 520.0, {"machinist": 14, "aerospace_technician": 6, "engineer": 6}, 1100)
    s.fac("propulsion_works", "Propulsion works", "FACTORY", "AEROSPACE", 42_000_000, 240, 2, 9_500, 600.0, {"machinist": 12, "engineer": 8}, 1150)
    s.fac("avionics_works", "Avionics works", "FACTORY", "AEROSPACE", 24_000_000, 180, 3, 6_200, 400.0, {"engineer": 8, "technician": 8, "aerospace_technician": 4}, 950)
    s.fac("final_assembly_hangar", "Final assembly hangar", "FACTORY", "AEROSPACE", 90_000_000, 320, 2, 18_000, 800.0, {"machinist": 24, "engineer": 12, "aerospace_technician": 10}, 1300)
    s.fac("space_systems_works", "Space systems works", "FACTORY", "AEROSPACE", 70_000_000, 300, 2, 15_000, 700.0, {"engineer": 14, "aerospace_technician": 10, "technician": 8}, 1300)

    s.machine("vacuum_arc_remelter", "Vacuum arc remelter", 3_000_000, thr=1.1, wear=500, maint=220.0)
    s.machine("autoclave", "Composite autoclave", 2_400_000, thr=1.2, wear=450, maint=200.0)
    s.machine("five_axis_mill", "Five-axis mill", 1_600_000, thr=1.25, wear=480, maint=140.0, labor_sav=0.08)
    s.machine("assembly_jig", "Precision assembly jig", 1_200_000, thr=1.15, wear=400, maint=100.0)

    R = s.rec
    R("mine_titanium", "Mine titanium ore", "aero_materials", "titanium_mine", 10, {"diesel": 100, "explosives": 20, "water": 120}, {"titanium_ore": 1}, {"miner": 8, "engineer": 1}, energy=100, margin=0.22)
    R("reduce_titanium_sponge", "Reduce titanium sponge", "aero_materials", "titanium_works", 6, {"titanium_ore": 220, "chlorine": 40, "coal": 30, "industrial_gases": 20}, {"titanium_sponge": 1}, {"operator": 4, "engineer": 2}, energy=420, margin=0.25, waste={"chemical_waste": 20})
    R("melt_titanium_mill_products", "Melt titanium mill products", "aero_materials", "titanium_works", 8, {"titanium_sponge": 60, "aluminum_ingot": 10, "nickel_ingot": 4}, {"titanium_mill_products": 1}, {"operator": 4, "engineer": 2}, energy=520, margin=0.26, machines=["vacuum_arc_remelter"])
    R("roll_aluminum_lithium", "Roll aluminum-lithium sheet", "aero_materials", "titanium_works", 5, {"aluminum_ingot": 40, "lithium_carbonate": 3, "lubricants": 2}, {"aluminum_lithium_sheet": 1}, {"operator": 4, "engineer": 1}, energy=300, margin=0.24)
    R("lay_composite_prepreg", "Lay composite prepreg", "aero_materials", "advanced_materials_plant", 5, {"carbon_fiber": 40, "resins_epoxy": 30, "solvents": 4}, {"composite_prepreg": 1}, {"operator": 4, "aerospace_technician": 2}, energy=260, margin=0.26, machines=["autoclave"])
    R("bond_honeycomb_core", "Bond honeycomb core", "aero_materials", "advanced_materials_plant", 4, {"aluminum_sheet": 20, "adhesives": 12}, {"honeycomb_core": 1}, {"operator": 4, "aerospace_technician": 1}, energy=150, margin=0.24)
    R("spray_thermal_coating", "Spray thermal barrier coating", "aero_materials", "advanced_materials_plant", 4, {"rare_earth_oxide": 3, "silica_sand": 20, "solvents": 6}, {"thermal_barrier_coating": 1}, {"operator": 4, "aerospace_technician": 2}, energy=200, margin=0.26)
    R("forge_aero_fasteners", "Forge aerospace fasteners", "aero_materials", "advanced_materials_plant", 3, {"titanium_mill_products": 3, "lubricants": 1}, {"aero_fasteners": 20}, {"operator": 4, "aerospace_technician": 1}, energy=110, margin=0.24)
    # Discrete goods: inputs and energy are per unit, qty is the batch size, and the reference price is fitted from the parts.
    L = {"machinist": 14, "aerospace_technician": 6, "engineer": 6}
    R("assemble_wing_boxes", "Assemble wing boxes", "aerostructures", "aerostructures_plant", 0, {"aluminum_lithium_sheet": 900, "composite_prepreg": 450, "honeycomb_core": 350, "aero_fasteners": 400}, {"wing_box": 1}, L, energy=1_000, margin=0.22, qty=2, machines=["five_axis_mill"], fit=True)
    R("assemble_fuselage_sections", "Assemble fuselage sections", "aerostructures", "aerostructures_plant", 0, {"aluminum_lithium_sheet": 750, "composite_prepreg": 300, "aero_fasteners": 450, "paint": 100}, {"fuselage_section": 1}, L, energy=900, margin=0.22, qty=2, machines=["five_axis_mill"], fit=True)
    R("build_rotor_systems", "Build rotor systems", "aerostructures", "aerostructures_plant", 0, {"titanium_mill_products": 150, "composite_prepreg": 150, "gearbox": 10, "aero_fasteners": 200}, {"rotor_system": 1}, L, energy=400, margin=0.22, qty=3, fit=True, scale=2)
    R("outfit_cabin_modules", "Outfit cabin interior modules", "aerostructures", "aerostructures_plant", 0, {"seat_set": 10, "composite_panels": 20, "wiring_harness": 15, "upholstery_fabric": 40, "technical_fabric": 10}, {"cabin_interior_module": 1}, L, energy=80, margin=0.22, qty=8, fit=True, scale=3)
    P = {"machinist": 12, "engineer": 8}
    R("forge_turbine_discs", "Forge turbine discs", "propulsion_and_avionics", "propulsion_works", 0, {"titanium_mill_products": 1, "nickel_ingot": 0.7, "forgings": 1.7}, {"forged_turbine_discs": 1}, P, energy=4, margin=0.24, qty=120, machines=["vacuum_arc_remelter"], fit=True, scale=8)
    R("build_turboprop_engines", "Build turboprop engines", "propulsion_and_avionics", "propulsion_works", 0, {"forged_turbine_discs": 8, "titanium_mill_products": 60, "thermal_barrier_coating": 10, "sensor_pack": 20, "lubricants": 6}, {"turboprop_engine": 1}, P, energy=300, margin=0.22, qty=4, fit=True, scale=3)
    R("build_turboshaft_engines", "Build turboshaft engines", "propulsion_and_avionics", "propulsion_works", 0, {"forged_turbine_discs": 6, "titanium_mill_products": 45, "thermal_barrier_coating": 8, "sensor_pack": 16, "lubricants": 5}, {"turboshaft_engine": 1}, P, energy=260, margin=0.22, qty=4, fit=True, scale=3)
    R("build_rocket_engines", "Build rocket engines", "propulsion_and_avionics", "propulsion_works", 0, {"forged_turbine_discs": 10, "stainless_steel": 120, "thermal_barrier_coating": 30, "hydrogen": 100, "sensor_pack": 30}, {"rocket_engine": 1}, P, energy=600, margin=0.24, qty=2, fit=True, scale=6)
    A = {"engineer": 8, "technician": 8, "aerospace_technician": 4}
    R("build_flight_computers", "Build flight control computers", "propulsion_and_avionics", "avionics_works", 0, {"chip_logic": 12, "pcb": 9, "sensor_pack": 6, "controller_unit": 3}, {"flight_control_computer": 1}, A, energy=60, margin=0.26, qty=6, fit=True, scale=12)
    R("build_radar_units", "Build radar units", "propulsion_and_avionics", "avionics_works", 0, {"rf_modules": 14, "chip_logic": 16, "display_panel": 2, "power_supply": 4}, {"radar_unit": 1}, A, energy=60, margin=0.26, qty=6, fit=True, scale=25)
    R("build_satcom_terminals", "Build satcom terminals", "propulsion_and_avionics", "avionics_works", 0, {"rf_modules": 10, "chip_logic": 5, "power_supply": 2, "aluminum_sheet": 3}, {"satcom_terminal": 1}, A, energy=40, margin=0.26, qty=8, fit=True, scale=18)
    R("build_attitude_control", "Build attitude control units", "propulsion_and_avionics", "avionics_works", 0, {"mems_sensors": 40, "controller_unit": 4, "servo_drive": 2, "chip_logic": 4}, {"attitude_control_unit": 1}, A, energy=40, margin=0.26, qty=8, fit=True, scale=14)
    H = {"machinist": 24, "engineer": 12, "aerospace_technician": 10}
    R("assemble_business_jets", "Assemble business jets", "aircraft_assembly", "final_assembly_hangar", 0, {"wing_box": 1, "fuselage_section": 1, "jet_engine": 2, "avionics_suite": 1, "landing_gear": 1, "cabin_interior_module": 1, "flight_control_computer": 1, "radar_unit": 1, "paint": 300}, {"business_jet": 1}, H, energy=3_000, margin=0.2, qty=1, machines=["assembly_jig"], fit=True)
    R("assemble_regional_turboprops", "Assemble regional turboprops", "aircraft_assembly", "final_assembly_hangar", 0, {"wing_box": 2, "fuselage_section": 2, "turboprop_engine": 2, "avionics_basic": 2, "landing_gear": 1, "cabin_interior_module": 2, "flight_control_computer": 1, "radar_unit": 1, "paint": 400}, {"regional_turboprop": 1}, H, energy=4_000, margin=0.2, qty=1, machines=["assembly_jig"], fit=True)
    R("assemble_helicopters", "Assemble helicopters", "aircraft_assembly", "final_assembly_hangar", 0, {"rotor_system": 1, "turboshaft_engine": 2, "fuselage_section": 1, "avionics_basic": 1, "cabin_interior_module": 1, "flight_control_computer": 1, "paint": 120}, {"helicopter": 1}, H, energy=1_800, margin=0.22, qty=2, machines=["assembly_jig"], fit=True)
    R("assemble_survey_uavs", "Assemble survey UAVs", "aircraft_assembly", "final_assembly_hangar", 0, {"composite_prepreg": 25, "electric_motor": 8, "sensor_pack": 30, "battery_pack": 4, "controller_unit": 6, "camera_module": 12, "rf_modules": 4}, {"survey_uav": 1}, H, energy=60, margin=0.22, qty=20, fit=True, scale=1.6)
    S = {"engineer": 14, "aerospace_technician": 10, "technician": 8}
    R("assemble_solar_arrays", "Assemble satellite solar arrays", "space_systems", "space_systems_works", 0, {"solar_cells": 80, "aluminum_lithium_sheet": 10, "copper_wire": 8, "glass": 6}, {"solar_array": 1}, S, energy=50, margin=0.24, qty=20, fit=True, scale=6)
    R("build_satellite_buses", "Build satellite buses", "space_systems", "space_systems_works", 0, {"honeycomb_core": 60, "composite_prepreg": 40, "attitude_control_unit": 1, "solar_array": 2, "battery_pack": 6, "flight_control_computer": 1}, {"satellite_bus": 1}, S, energy=300, margin=0.24, qty=2, fit=True)
    R("integrate_comms_satellites", "Integrate communications satellites", "space_systems", "space_systems_works", 0, {"satellite_bus": 1, "satcom_terminal": 4, "solar_array": 2, "thermal_barrier_coating": 20}, {"communications_satellite": 1}, S, energy=500, margin=0.24, qty=1, fit=True, scale=2)
    R("assemble_launch_vehicles", "Assemble launch vehicles", "space_systems", "space_systems_works", 0, {"rocket_engine": 4, "composite_prepreg": 400, "aluminum_lithium_sheet": 450, "flight_control_computer": 3, "attitude_control_unit": 2, "hydrogen": 150}, {"launch_vehicle": 1}, S, energy=900, margin=0.24, qty=1, fit=True)

    s.tech("titanium_metallurgy", "Titanium metallurgy", "MATERIALS", 3, 4_500_000, 220, requires=["electric_arc_furnace"],
           facilities=["titanium_works"], machines=["vacuum_arc_remelter"], effects=[("YIELD_BONUS", 0.05, "aero_materials", None)])
    s.tech("composite_layup", "Composite layup", "MATERIALS", 4, 7_000_000, 260, requires=["aerospace_engineering"],
           facilities=["advanced_materials_plant"], machines=["autoclave"])
    s.tech("aerostructures_design", "Aerostructures design", "PRODUCTION", 4, 11_000_000, 300, requires=["aerospace_engineering", "composite_layup"],
           facilities=["aerostructures_plant"], machines=["five_axis_mill"], recipes=["build_rotor_systems"])
    s.tech("propulsion_engineering", "Propulsion engineering", "PRODUCTION", 4, 14_000_000, 320, requires=["aerospace_engineering", "titanium_metallurgy"],
           facilities=["propulsion_works"], recipes=["build_rocket_engines"])
    s.tech("avionics_integration", "Avionics integration", "DIGITAL", 4, 10_000_000, 280, requires=["aerospace_engineering", "photolithography"],
           facilities=["avionics_works"])
    s.tech("advanced_aircraft_assembly", "Advanced aircraft assembly", "PRODUCTION", 4, 16_000_000, 340, requires=["aerostructures_design", "propulsion_engineering", "avionics_integration"],
           facilities=["final_assembly_hangar"], machines=["assembly_jig"])
    s.tech("space_systems_engineering", "Space systems engineering", "PRODUCTION", 4, 18_000_000, 360, requires=["avionics_integration", "composite_layup"],
           facilities=["space_systems_works"], recipes=["assemble_launch_vehicles"])
    s.tech("additive_manufacturing", "Additive manufacturing", "PRODUCTION", 3, 3_500_000, 200, requires=["industrial_robotics"],
           effects=[("INPUT_SAVINGS", 0.06, "aerostructures", None), ("INPUT_SAVINGS", 0.04, "propulsion_and_avionics", None)])
    s.tech("flight_data_analytics", "Flight data analytics", "DIGITAL", 3, 2_800_000, 160, requires=["cloud_infrastructure"],
           effects=[("QUALITY_BONUS", 0.10, "aircraft_assembly", None), ("LABOR_SAVINGS", 0.05, "aircraft_assembly", None)])
    s.tech("rotorcraft_dynamics", "Rotorcraft dynamics", "PRODUCTION", 4, 12_000_000, 300, requires=["aerostructures_design"],
           recipes=["assemble_helicopters", "build_turboshaft_engines"], effects=[("YIELD_BONUS", 0.04, "aircraft_assembly", None)])

    D = s.dem
    D("business_jet", 0, 0, 1, 0.7, 0.9, [], {}, 0.999)
    D("regional_turboprop", 0, 0, 1, 0.7, 0.8, [], {}, 0.999)
    D("helicopter", 0, 0, 1, 0.8, 0.9, [], {}, 0.999)
    D("survey_uav", 0, 1, 1, 0.8, 0.8, [], {}, 0.99)
    D("communications_satellite", 0, 0, 1, 0.5, 0.6, [], {}, 0.9995)
    D("launch_vehicle", 0, 0, 1, 0.5, 0.6, [], {}, 0.9995)
    return s

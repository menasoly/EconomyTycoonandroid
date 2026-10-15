from contentgen import Spec

def build():
    s = Spec("industry", "IndustryPack")
    s.industry("construction_materials", "Construction materials", "CONSTRUCTION", "Concrete, lumber products, prefabrication")
    s.industry("building", "Building construction", "CONSTRUCTION", "Residential and commercial construction")
    s.industry("automotive", "Automotive", "AUTOMOTIVE", "Vehicles and components")
    s.industry("aerospace", "Aerospace", "AEROSPACE", "Aircraft and components")
    s.industry("electronics", "Electronics", "ELECTRONICS", "Consumer and industrial electronics")
    s.industry("semiconductors", "Semiconductors", "SEMICONDUCTORS", "Wafer fabrication and chips")
    s.industry("robotics", "Robotics", "ROBOTICS", "Industrial and service robots")
    s.industry("machinery", "Machinery", "METALS", "Machine tools and equipment")

    # Construction products
    for gid, name, price, spoil in [
        ("concrete", "Ready-mix concrete", 22, 90000), ("rebar", "Rebar", 80, 0), ("lumber", "Lumber", 48, 0), ("plywood", "Plywood", 40, 0),
        ("prefab_panels", "Prefabricated panels", 160, 0), ("insulation", "Insulation", 26, 0), ("roofing_tiles", "Roofing tiles", 24, 0),
        ("asphalt", "Asphalt", 28, 20000), ("windows", "Window units", 120, 0), ("doors", "Doors", 85, 0), ("hvac_units", "HVAC units", 900, 0),
        ("plumbing_fixtures", "Plumbing fixtures", 140, 0), ("electrical_fittings", "Electrical fittings", 60, 0),
    ]:
        s.good(gid, name, "INTERMEDIATE", price, "STABLE", spoil)
    s.good("housing_unit", "Residential unit", "INDUSTRIAL", 220_000, "NORMAL", 0, 20_000, 50_000, tradable=False)
    s.good("office_floor", "Office floor", "INDUSTRIAL", 900_000, "NORMAL", 0, 20_000, 50_000, tradable=False)
    s.good("warehouse_hall", "Warehouse hall", "INDUSTRIAL", 650_000, "NORMAL", 0, 20_000, 50_000, tradable=False)
    # Electronics and semiconductors
    for gid, name, price, vol in [
        ("silicon_ingot", "Silicon ingot", 220, "NORMAL"), ("silicon_wafer", "Silicon wafer", 380, "NORMAL"),
        ("chip_basic", "Basic microchip", 6, "NORMAL"), ("chip_logic", "Logic chip", 80, "VOLATILE"), ("chip_memory", "Memory chip", 38, "VOLATILE"),
        ("pcb", "Printed circuit board", 14, "NORMAL"), ("capacitors", "Capacitors", 3, "STABLE"), ("display_panel", "Display panel", 90, "NORMAL"),
        ("sensor_pack", "Sensor pack", 28, "NORMAL"), ("wiring_harness", "Wiring harness", 60, "STABLE"), ("electric_motor", "Electric motor", 180, "NORMAL"),
        ("power_supply", "Power supply unit", 34, "NORMAL"), ("camera_module", "Camera module", 26, "NORMAL"), ("battery_pack", "Battery pack", 420, "NORMAL"),
        ("controller_unit", "Controller unit", 150, "NORMAL"),
    ]:
        s.good(gid, name, "INTERMEDIATE", price, vol)
    for gid, name, price in [
        ("smartphone", "Smartphone", 520), ("laptop", "Laptop computer", 780), ("television", "Television", 360), ("tablet", "Tablet", 380),
        ("refrigerator", "Refrigerator", 640), ("washing_machine", "Washing machine", 520), ("microwave", "Microwave oven", 110), ("game_console", "Game console", 330),
        ("headphones", "Headphones", 70), ("smartwatch", "Smartwatch", 210), ("router", "Network router", 85),
    ]:
        s.good(gid, name, "CONSUMER", price, "NORMAL")
    # Automotive, aerospace, machinery, robotics, energy equipment
    for gid, name, price, vol in [
        ("engine_block", "Engine", 2_200, "NORMAL"), ("transmission", "Transmission", 1_300, "NORMAL"), ("car_body", "Car body", 1_900, "NORMAL"),
        ("seat_set", "Seat set", 380, "NORMAL"), ("tires", "Tires", 90, "NORMAL"), ("brake_system", "Brake system", 260, "NORMAL"),
        ("suspension_kit", "Suspension kit", 340, "NORMAL"), ("auto_glass", "Automotive glass", 160, "NORMAL"), ("ev_drivetrain", "EV drivetrain", 3_100, "NORMAL"),
        ("aircraft_frame", "Aircraft frame", 1_400_000, "NORMAL"), ("jet_engine", "Jet engine", 2_600_000, "NORMAL"), ("avionics_suite", "Avionics suite", 900_000, "NORMAL"),
        ("composite_panels", "Composite panels", 1_100, "NORMAL"), ("carbon_fiber", "Carbon fiber", 260, "NORMAL"), ("landing_gear", "Landing gear", 480_000, "NORMAL"),
        ("robot_arm", "Robot arm", 14_000, "NORMAL"), ("servo_drive", "Servo drive", 420, "NORMAL"), ("machine_tool", "Machine tool", 62_000, "NORMAL"),
        ("conveyor_system", "Conveyor system", 18_000, "NORMAL"), ("turbine_blades", "Turbine blades", 8_000, "NORMAL"), ("gearbox", "Industrial gearbox", 3_400, "NORMAL"),
        ("solar_panel", "Solar panel", 190, "NORMAL"), ("wind_turbine", "Wind turbine", 1_400_000, "NORMAL"), ("industrial_robot", "Industrial robot", 38_000, "NORMAL"),
    ]:
        s.good(gid, name, "INDUSTRIAL", price, vol, 0, 3_000, 4_000)
    for gid, name, price in [
        ("car_compact", "Compact car", 19_000), ("car_sedan", "Sedan", 27_000), ("car_ev", "Electric car", 36_000), ("pickup_truck", "Pickup truck", 34_000),
        ("light_aircraft", "Light aircraft", 350_000), ("jetliner", "Jetliner", 95_000_000), ("delivery_drone", "Delivery drone", 4_200),
        ("service_robot", "Service robot", 9_000),
    ]:
        s.good(gid, name, "CONSUMER" if "car" in gid or gid == "pickup_truck" else "INDUSTRIAL", price, "NORMAL", 0, 5_000, 8_000)

    F = s.facility
    F("concrete_plant", "Concrete plant", "FACTORY", "CONSTRUCTION", 1_400_000, 55, 3, 30_000, 520, 60.0, {"operator": 2})
    F("sawmill", "Sawmill", "FACTORY", "CONSTRUCTION", 1_100_000, 50, 3, 80_000, 460, 60.0, {"operator": 2})
    F("prefab_factory", "Prefab factory", "FACTORY", "CONSTRUCTION", 2_200_000, 70, 3, 50_000, 800, 90.0, {"operator": 3, "machinist": 1})
    F("fittings_factory", "Building fittings factory", "FACTORY", "CONSTRUCTION", 1_600_000, 60, 3, 30_000, 620, 70.0, {"machinist": 2})
    F("construction_yard", "Construction yard", "OFFICE", "CONSTRUCTION", 900_000, 40, 6, 100_000, 700, 20.0, {"manager": 1, "clerk": 1})
    F("semiconductor_fab", "Semiconductor fab", "FACTORY", "SEMICONDUCTORS", 60_000_000, 280, 3, 20_000, 14_000, 900.0, {"engineer": 20, "technician": 20}, tech="photolithography")
    F("wafer_plant", "Wafer plant", "FACTORY", "SEMICONDUCTORS", 18_000_000, 180, 2, 20_000, 5_000, 500.0, {"operator": 6, "engineer": 4}, tech="wafer_growth")
    F("pcb_plant", "PCB plant", "FACTORY", "ELECTRONICS", 4_200_000, 90, 3, 20_000, 1_500, 140.0, {"operator": 4, "technician": 1})
    F("electronics_assembly", "Electronics assembly plant", "FACTORY", "ELECTRONICS", 7_500_000, 110, 4, 40_000, 2_200, 180.0, {"operator": 6, "technician": 2})
    F("appliance_factory", "Appliance factory", "FACTORY", "CONSUMER_GOODS", 6_000_000, 100, 3, 50_000, 1_800, 150.0, {"operator": 5, "machinist": 2})
    F("auto_parts_plant", "Auto parts plant", "FACTORY", "AUTOMOTIVE", 5_200_000, 100, 4, 60_000, 1_700, 160.0, {"machinist": 4, "operator": 2})
    F("engine_plant", "Engine plant", "FACTORY", "AUTOMOTIVE", 9_500_000, 130, 3, 40_000, 2_700, 220.0, {"machinist": 6, "engineer": 2})
    F("auto_assembly", "Automobile assembly plant", "FACTORY", "AUTOMOTIVE", 28_000_000, 180, 4, 90_000, 5_500, 400.0, {"operator": 12, "engineer": 4})
    F("aircraft_factory", "Aircraft factory", "FACTORY", "AEROSPACE", 120_000_000, 360, 2, 100_000, 22_000, 900.0, {"engineer": 30, "machinist": 30}, tech="aerospace_engineering")
    F("aero_components_plant", "Aerospace components plant", "FACTORY", "AEROSPACE", 38_000_000, 220, 3, 40_000, 8_000, 420.0, {"machinist": 12, "engineer": 6}, tech="aerospace_engineering")
    F("robotics_factory", "Robotics factory", "FACTORY", "ROBOTICS", 22_000_000, 160, 3, 30_000, 5_000, 300.0, {"engineer": 8, "technician": 6}, tech="industrial_robotics")
    F("machine_tool_works", "Machine tool works", "FACTORY", "METALS", 8_500_000, 120, 3, 40_000, 2_400, 200.0, {"machinist": 6, "engineer": 2})
    F("turbine_factory", "Turbine factory", "FACTORY", "RENEWABLE_ENERGY", 16_000_000, 150, 2, 40_000, 3_800, 240.0, {"machinist": 8, "engineer": 4})
    F("panel_plant", "Solar panel plant", "FACTORY", "RENEWABLE_ENERGY", 12_000_000, 130, 3, 30_000, 3_000, 260.0, {"operator": 5, "engineer": 2})

    R = s.recipe
    R("mix_concrete", "Mix concrete", "construction_materials", "concrete_plant", 1, {"cement": 120, "sand": 250, "gravel": 380, "water": 100}, {"concrete": 1}, {"operator": 3}, energy=30)
    R("saw_lumber", "Saw lumber", "construction_materials", "sawmill", 3, {"logs": 220}, {"lumber": 0.85, "plywood": 0.15}, {"operator": 4}, energy=60, waste={"organic_waste": 20})
    R("make_plywood", "Press plywood", "construction_materials", "sawmill", 3, {"logs": 180, "adhesives": 20}, {"plywood": 1}, {"operator": 3}, energy=70)
    R("make_rebar", "Roll rebar", "construction_materials", "fittings_factory", 3, {"steel": 120, "lubricants": 2}, {"rebar": 1}, {"operator": 3}, energy=90)
    R("make_prefab", "Build prefab panels", "construction_materials", "prefab_factory", 4, {"concrete": 80, "rebar": 25, "insulation": 30}, {"prefab_panels": 1}, {"operator": 4, "machinist": 1}, energy=90)
    R("make_insulation", "Make insulation", "construction_materials", "prefab_factory", 3, {"silica_sand": 90, "polypropylene": 20}, {"insulation": 1}, {"operator": 3}, energy=70)
    R("make_roofing_tiles", "Fire roofing tiles", "construction_materials", "fittings_factory", 3, {"clay": 220, "natural_gas": 20}, {"roofing_tiles": 1}, {"operator": 3}, energy=60)
    R("mix_asphalt", "Mix asphalt", "construction_materials", "concrete_plant", 1, {"bitumen": 60, "gravel": 260}, {"asphalt": 1}, {"operator": 3}, energy=40)
    R("make_windows", "Assemble windows", "construction_materials", "fittings_factory", 3, {"glass": 40, "aluminum_sheet": 12, "adhesives": 4}, {"windows": 1}, {"machinist": 3}, energy=50)
    R("make_doors", "Assemble doors", "construction_materials", "fittings_factory", 2, {"lumber": 30, "fasteners": 6, "paint": 4}, {"doors": 1}, {"machinist": 2}, energy=30)
    R("make_hvac", "Assemble HVAC units", "construction_materials", "fittings_factory", 5, {"sheet_metal_parts": 30, "copper_pipe": 12, "electric_motor": 4, "controller_unit": 2}, {"hvac_units": 1}, {"machinist": 4}, energy=60)
    R("make_plumbing", "Make plumbing fixtures", "construction_materials", "fittings_factory", 3, {"copper_pipe": 20, "stainless_steel": 6}, {"plumbing_fixtures": 1}, {"machinist": 3}, energy=40)
    R("make_electrical_fittings", "Make electrical fittings", "construction_materials", "fittings_factory", 3, {"copper_wire": 14, "plastic_granules": 20}, {"electrical_fittings": 1}, {"machinist": 3}, energy=40)
    R("build_housing", "Build housing", "building", "construction_yard", 90, {"concrete": 400, "rebar": 100, "lumber": 150, "bricks": 300, "windows": 40, "doors": 20, "roofing_tiles": 200, "insulation": 100, "plumbing_fixtures": 20, "electrical_fittings": 60}, {"housing_unit": 1}, {"laborer": 30, "machinist": 8, "electrician": 4, "manager": 1})
    R("build_office", "Build office floor", "building", "construction_yard", 150, {"concrete": 900, "rebar": 260, "steel_beams": 140, "glass": 220, "hvac_units": 8, "electrical_fittings": 120, "insulation": 160, "plumbing_fixtures": 30}, {"office_floor": 1}, {"laborer": 35, "machinist": 12, "electrician": 6, "manager": 2})
    R("build_warehouse", "Build warehouse hall", "building", "construction_yard", 100, {"concrete": 700, "steel_beams": 220, "steel_sheet": 160, "electrical_fittings": 80}, {"warehouse_hall": 1}, {"laborer": 28, "machinist": 8, "electrician": 3, "manager": 1})
    # Semiconductors and electronics
    R("grow_silicon", "Grow silicon ingots", "semiconductors", "wafer_plant", 6, {"silica_sand": 400, "coal": 80, "industrial_gases": 30}, {"silicon_ingot": 1}, {"operator": 4, "engineer": 2}, energy=700, tech="wafer_growth")
    R("slice_wafers", "Slice silicon wafers", "semiconductors", "wafer_plant", 3, {"silicon_ingot": 30, "industrial_gases": 8}, {"silicon_wafer": 1}, {"operator": 4, "engineer": 1}, energy=300, tech="wafer_growth")
    R("fab_basic_chips", "Fabricate basic chips", "semiconductors", "semiconductor_fab", 10, {"silicon_wafer": 10, "industrial_gases": 40, "solvents": 20}, {"chip_basic": 1}, {"engineer": 8, "technician": 10}, energy=1_800, tech="photolithography", waste={"chemical_waste": 20})
    R("fab_logic_chips", "Fabricate logic chips", "semiconductors", "semiconductor_fab", 20, {"silicon_wafer": 12, "industrial_gases": 60, "solvents": 40, "rare_earth_oxide": 1}, {"chip_logic": 1}, {"engineer": 12, "technician": 14}, energy=3_200, tech="photolithography", waste={"chemical_waste": 35})
    R("fab_memory_chips", "Fabricate memory chips", "semiconductors", "semiconductor_fab", 16, {"silicon_wafer": 12, "industrial_gases": 50, "solvents": 30}, {"chip_memory": 1}, {"engineer": 10, "technician": 12}, energy=2_600, tech="photolithography", waste={"chemical_waste": 28})
    R("make_pcb", "Print circuit boards", "electronics", "pcb_plant", 3, {"copper_ingot": 20, "resins_epoxy": 18, "glass": 8, "solvents": 6}, {"pcb": 1}, {"operator": 4, "technician": 1}, energy=90, waste={"chemical_waste": 6})
    R("make_capacitors", "Make capacitors", "electronics", "pcb_plant", 2, {"aluminum_ingot": 10, "tin_ingot": 2, "polypropylene": 4}, {"capacitors": 1}, {"operator": 3}, energy=40)
    R("make_display", "Make display panels", "electronics", "electronics_assembly", 5, {"glass": 50, "polypropylene": 10, "chip_basic": 30, "rare_earth_oxide": 0.5}, {"display_panel": 1}, {"operator": 6, "technician": 2}, energy=140)
    R("make_sensors", "Make sensor packs", "electronics", "electronics_assembly", 3, {"chip_basic": 30, "pcb": 10, "plastic_granules": 8}, {"sensor_pack": 1}, {"operator": 4, "technician": 1}, energy=80)
    R("make_camera_modules", "Make camera modules", "electronics", "electronics_assembly", 3, {"glass": 6, "chip_basic": 25, "plastic_granules": 8, "pcb": 6}, {"camera_module": 1}, {"operator": 4}, energy=70)
    R("make_power_supplies", "Make power supplies", "electronics", "electronics_assembly", 2, {"copper_wire": 16, "capacitors": 40, "pcb": 8, "plastic_granules": 8}, {"power_supply": 1}, {"operator": 3}, energy=60)
    R("make_wiring_harness", "Make wiring harnesses", "electronics", "auto_parts_plant", 2, {"copper_wire": 40, "plastic_granules": 16}, {"wiring_harness": 1}, {"operator": 4}, energy=40)
    R("make_motors", "Wind electric motors", "electronics", "auto_parts_plant", 3, {"copper_wire": 40, "steel_sheet": 30, "rare_earth_oxide": 1}, {"electric_motor": 1}, {"machinist": 4}, energy=80)
    R("make_battery_pack", "Assemble battery packs", "electronics", "electronics_assembly", 3, {"lithium_battery_cells": 14, "aluminum_sheet": 8, "wiring_harness": 1, "controller_unit": 1}, {"battery_pack": 1}, {"operator": 4, "technician": 1}, energy=90)
    R("make_controllers", "Make controller units", "electronics", "electronics_assembly", 3, {"chip_logic": 4, "pcb": 6, "capacitors": 30, "plastic_granules": 5}, {"controller_unit": 1}, {"operator": 4, "technician": 1}, energy=70)
    R("assemble_smartphone", "Assemble smartphones", "electronics", "electronics_assembly", 3, {"chip_logic": 40, "chip_memory": 50, "display_panel": 40, "camera_module": 80, "pcb": 40, "battery_pack": 0.0 or 3, "aluminum_sheet": 8, "packaging": 40}, {"smartphone": 1}, {"operator": 12, "technician": 3}, energy=160)
    R("assemble_laptop", "Assemble laptops", "electronics", "electronics_assembly", 3, {"chip_logic": 20, "chip_memory": 40, "display_panel": 20, "pcb": 20, "battery_pack": 3, "aluminum_sheet": 20, "packaging": 20}, {"laptop": 1}, {"operator": 10, "technician": 3}, energy=150)
    R("assemble_tablet", "Assemble tablets", "electronics", "electronics_assembly", 3, {"chip_logic": 20, "chip_memory": 30, "display_panel": 20, "pcb": 20, "battery_pack": 2, "aluminum_sheet": 10, "packaging": 20}, {"tablet": 1}, {"operator": 10, "technician": 3}, energy=130)
    R("assemble_tv", "Assemble televisions", "electronics", "electronics_assembly", 3, {"chip_logic": 10, "display_panel": 30, "power_supply": 30, "plastic_granules": 40, "packaging": 40}, {"television": 1}, {"operator": 8, "technician": 2}, energy=110)
    R("assemble_console", "Assemble game consoles", "electronics", "electronics_assembly", 3, {"chip_logic": 20, "chip_memory": 25, "pcb": 20, "power_supply": 20, "plastic_granules": 30, "packaging": 30}, {"game_console": 1}, {"operator": 8, "technician": 2}, energy=100)
    R("assemble_headphones", "Assemble headphones", "electronics", "electronics_assembly", 2, {"chip_basic": 60, "plastic_granules": 30, "copper_wire": 8, "packaging": 20}, {"headphones": 1}, {"operator": 5}, energy=40)
    R("assemble_smartwatch", "Assemble smartwatches", "electronics", "electronics_assembly", 3, {"chip_logic": 12, "sensor_pack": 30, "display_panel": 12, "battery_pack": 1, "packaging": 20}, {"smartwatch": 1}, {"operator": 6, "technician": 2}, energy=60)
    R("assemble_router", "Assemble routers", "electronics", "electronics_assembly", 2, {"chip_logic": 8, "pcb": 20, "power_supply": 20, "plastic_granules": 20, "packaging": 20}, {"router": 1}, {"operator": 5}, energy=40)
    R("build_refrigerators", "Build refrigerators", "electronics", "appliance_factory", 3, {"steel_sheet": 50, "copper_pipe": 8, "electric_motor": 10, "insulation": 20, "controller_unit": 2, "packaging": 20}, {"refrigerator": 1}, {"operator": 6, "machinist": 2}, energy=140)
    R("build_washing_machines", "Build washing machines", "electronics", "appliance_factory", 3, {"steel_sheet": 40, "electric_motor": 10, "controller_unit": 3, "plastic_granules": 16, "packaging": 20}, {"washing_machine": 1}, {"operator": 6, "machinist": 2}, energy=120)
    R("build_microwaves", "Build microwave ovens", "electronics", "appliance_factory", 2, {"steel_sheet": 20, "power_supply": 12, "controller_unit": 2, "packaging": 12}, {"microwave": 1}, {"operator": 5}, energy=70)
    # Automotive
    R("cast_engine_blocks", "Build engines", "automotive", "engine_plant", 5, {"castings": 60, "forgings": 20, "fasteners": 30, "lubricants": 6, "spare_parts": 6}, {"engine_block": 1}, {"machinist": 10, "engineer": 2}, energy=240)
    R("build_transmissions", "Build transmissions", "automotive", "engine_plant", 4, {"forgings": 28, "castings": 28, "fasteners": 16, "lubricants": 4}, {"transmission": 1}, {"machinist": 8, "engineer": 1}, energy=170)
    R("stamp_car_bodies", "Stamp car bodies", "automotive", "auto_parts_plant", 3, {"steel_sheet": 170, "paint": 16, "lubricants": 3}, {"car_body": 1}, {"machinist": 8, "operator": 4}, energy=200)
    R("make_seats", "Make seat sets", "automotive", "auto_parts_plant", 2, {"steel_sheet": 12, "plastic_granules": 16, "fabric_synthetic": 40, "foam": 14}, {"seat_set": 1}, {"operator": 6}, energy=60)
    R("make_tires", "Make tires", "automotive", "auto_parts_plant", 3, {"synthetic_rubber": 60, "steel_wire": 16, "carbon_black": 20}, {"tires": 1}, {"operator": 6}, energy=130)
    R("make_brakes", "Make brake systems", "automotive", "auto_parts_plant", 3, {"castings": 14, "steel_pipe": 10, "fasteners": 8}, {"brake_system": 1}, {"machinist": 6}, energy=80)
    R("make_suspension", "Make suspension kits", "automotive", "auto_parts_plant", 3, {"forgings": 14, "steel_sheet": 12, "fasteners": 8, "synthetic_rubber": 6}, {"suspension_kit": 1}, {"machinist": 6}, energy=80)
    R("make_auto_glass", "Cut automotive glass", "automotive", "auto_parts_plant", 2, {"glass": 40}, {"auto_glass": 1}, {"operator": 4}, energy=60)
    R("make_ev_drivetrain", "Build EV drivetrains", "automotive", "engine_plant", 5, {"electric_motor": 20, "controller_unit": 10, "forgings": 10, "wiring_harness": 8}, {"ev_drivetrain": 1}, {"machinist": 8, "engineer": 3}, energy=190)
    R("assemble_compact", "Assemble compact cars", "automotive", "auto_assembly", 4, {"engine_block": 12, "transmission": 12, "car_body": 12, "seat_set": 12, "tires": 60, "brake_system": 12, "suspension_kit": 12, "auto_glass": 12, "wiring_harness": 12, "paint": 20, "packaging": 20}, {"car_compact": 1}, {"operator": 24, "engineer": 3}, energy=700)
    R("assemble_sedan", "Assemble sedans", "automotive", "auto_assembly", 4, {"engine_block": 10, "transmission": 10, "car_body": 10, "seat_set": 10, "tires": 50, "brake_system": 10, "suspension_kit": 10, "auto_glass": 10, "wiring_harness": 10, "paint": 18, "packaging": 20}, {"car_sedan": 1}, {"operator": 24, "engineer": 3}, energy=700)
    R("assemble_pickup", "Assemble pickup trucks", "automotive", "auto_assembly", 4, {"engine_block": 8, "transmission": 8, "car_body": 8, "seat_set": 8, "tires": 40, "brake_system": 8, "suspension_kit": 8, "auto_glass": 8, "wiring_harness": 8, "paint": 16, "steel_beams": 40}, {"pickup_truck": 1}, {"operator": 22, "engineer": 3}, energy=650)
    R("assemble_ev", "Assemble electric cars", "automotive", "auto_assembly", 4, {"ev_drivetrain": 8, "battery_pack": 8, "car_body": 8, "seat_set": 8, "tires": 32, "brake_system": 8, "suspension_kit": 8, "auto_glass": 8, "wiring_harness": 8, "paint": 14}, {"car_ev": 1}, {"operator": 22, "engineer": 4}, energy=600, tech="ev_platform")
    # Aerospace
    R("lay_carbon_fiber", "Produce carbon fiber", "aerospace", "aero_components_plant", 6, {"polypropylene": 200, "natural_gas": 60}, {"carbon_fiber": 1}, {"operator": 4, "engineer": 1}, energy=500, tech="aerospace_engineering")
    R("cure_composites", "Cure composite panels", "aerospace", "aero_components_plant", 6, {"carbon_fiber": 40, "resins_epoxy": 30}, {"composite_panels": 1}, {"machinist": 6, "engineer": 1}, energy=420, tech="aerospace_engineering")
    R("build_landing_gear", "Build landing gear", "aerospace", "aero_components_plant", 30, {"forgings": 400, "stainless_steel": 150, "lubricants": 40}, {"landing_gear": 1}, {"machinist": 12, "engineer": 4}, energy=1_500, tech="aerospace_engineering")
    R("build_avionics", "Integrate avionics", "aerospace", "aero_components_plant", 40, {"chip_logic": 500, "pcb": 400, "display_panel": 120, "sensor_pack": 300, "wiring_harness": 60}, {"avionics_suite": 1}, {"engineer": 14, "technician": 10}, energy=1_800, tech="aerospace_engineering")
    R("build_jet_engine", "Build jet engines", "aerospace", "aero_components_plant", 60, {"forgings": 600, "titanium_alloy": 300, "stainless_steel": 300, "lubricants": 60, "sensor_pack": 80}, {"jet_engine": 1}, {"machinist": 16, "engineer": 8}, energy=2_400, tech="aerospace_engineering")
    R("build_aircraft_frame", "Build aircraft frames", "aerospace", "aircraft_factory", 120, {"aluminum_sheet": 1_200, "composite_panels": 900, "titanium_alloy": 500, "fasteners": 2_000, "paint": 400}, {"aircraft_frame": 1}, {"machinist": 40, "engineer": 10}, energy=5_000, tech="aerospace_engineering")
    R("assemble_light_aircraft", "Assemble light aircraft", "aerospace", "aircraft_factory", 40, {"aluminum_sheet": 160, "composite_panels": 80, "fasteners": 200, "avionics_basic": 1, "engine_block": 2, "paint": 20}, {"light_aircraft": 1}, {"machinist": 20, "engineer": 4}, energy=1_200, tech="aerospace_engineering")
    R("assemble_jetliner", "Assemble jetliners", "aerospace", "aircraft_factory", 180, {"aircraft_frame": 1, "jet_engine": 2, "avionics_suite": 1, "landing_gear": 1, "seat_set": 180, "wiring_harness": 120, "paint": 600}, {"jetliner": 1}, {"machinist": 60, "engineer": 20, "technician": 20}, energy=8_000, tech="aerospace_engineering")
    R("build_avionics_basic", "Build basic avionics", "aerospace", "aero_components_plant", 15, {"chip_logic": 30, "pcb": 40, "display_panel": 6, "sensor_pack": 20}, {"avionics_basic": 1}, {"engineer": 4, "technician": 4}, energy=500, tech="aerospace_engineering")
    R("refine_titanium", "Refine titanium alloy", "aerospace", "aero_components_plant", 8, {"stainless_steel": 100, "nickel_ingot": 60, "aluminum_ingot": 40}, {"titanium_alloy": 1}, {"operator": 5, "engineer": 1}, energy=700, tech="aerospace_engineering")
    # Machinery and robotics
    R("build_machine_tools", "Build machine tools", "machinery", "machine_tool_works", 12, {"castings": 220, "forgings": 80, "servo_drive": 10, "controller_unit": 6, "lubricants": 10}, {"machine_tool": 1}, {"machinist": 12, "engineer": 3}, energy=400)
    R("build_conveyors", "Build conveyor systems", "machinery", "machine_tool_works", 8, {"steel_beams": 60, "electric_motor": 20, "controller_unit": 4, "steel_wire": 40}, {"conveyor_system": 1}, {"machinist": 8, "electrician": 2}, energy=260)
    R("build_gearboxes", "Build gearboxes", "machinery", "machine_tool_works", 5, {"forgings": 36, "castings": 16, "lubricants": 6, "fasteners": 12}, {"gearbox": 1}, {"machinist": 8}, energy=160)
    R("build_servo_drives", "Build servo drives", "robotics", "robotics_factory", 3, {"electric_motor": 1, "chip_logic": 1, "sensor_pack": 1, "pcb": 2}, {"servo_drive": 1}, {"technician": 4, "engineer": 1}, energy=60, tech="industrial_robotics")
    R("build_robot_arms", "Build robot arms", "robotics", "robotics_factory", 10, {"servo_drive": 60, "castings": 40, "aluminum_sheet": 40, "controller_unit": 10, "sensor_pack": 20}, {"robot_arm": 1}, {"technician": 10, "engineer": 4}, energy=400, tech="industrial_robotics")
    R("build_industrial_robots", "Build industrial robots", "robotics", "robotics_factory", 12, {"robot_arm": 18, "controller_unit": 18, "sensor_pack": 40, "safety_cage": 0.0 or 1}, {"industrial_robot": 1}, {"technician": 10, "engineer": 4}, energy=450, tech="industrial_robotics")
    R("build_service_robots", "Build service robots", "robotics", "robotics_factory", 10, {"servo_drive": 80, "chip_logic": 60, "sensor_pack": 80, "battery_pack": 10, "plastic_granules": 100}, {"service_robot": 1}, {"technician": 10, "engineer": 5}, energy=380, tech="industrial_robotics")
    R("build_delivery_drones", "Build delivery drones", "robotics", "robotics_factory", 6, {"electric_motor": 40, "chip_logic": 20, "battery_pack": 10, "carbon_fiber": 12, "sensor_pack": 20}, {"delivery_drone": 1}, {"technician": 6, "engineer": 2}, energy=220, tech="industrial_robotics")
    R("build_turbine_blades", "Cast turbine blades", "machinery", "turbine_factory", 8, {"composite_panels": 60, "resins_epoxy": 40}, {"turbine_blades": 1}, {"machinist": 8, "engineer": 2}, energy=300)
    R("build_wind_turbines", "Build wind turbines", "machinery", "turbine_factory", 25, {"turbine_blades": 120, "gearbox": 40, "steel_beams": 900, "electric_motor": 30, "controller_unit": 24}, {"wind_turbine": 1}, {"machinist": 20, "engineer": 6}, energy=1_200)
    R("make_solar_panels", "Assemble solar panels", "machinery", "panel_plant", 3, {"solar_cells": 60, "aluminum_sheet": 14, "glass": 30, "copper_wire": 8}, {"solar_panel": 1}, {"operator": 6, "technician": 1}, energy=120)
    # extra goods referenced above
    s.good("fabric_synthetic", "Synthetic fabric", "INTERMEDIATE", 12, "NORMAL")
    s.good("foam", "Polyurethane foam", "INTERMEDIATE", 16, "NORMAL")
    s.good("carbon_black", "Carbon black", "INTERMEDIATE", 25, "NORMAL")
    s.good("titanium_alloy", "Titanium alloy", "INTERMEDIATE", 480, "VOLATILE")
    s.good("avionics_basic", "Basic avionics", "INDUSTRIAL", 120_000, "NORMAL")
    s.good("safety_cage", "Safety cage", "INDUSTRIAL", 900, "STABLE")
    # Technologies
    s.tech("photolithography", "Photolithography", "DIGITAL", 4, 12_000_000, 360, requires=["wafer_growth"], facilities=["semiconductor_fab"], recipes=["fab_basic_chips", "fab_logic_chips", "fab_memory_chips"])
    s.tech("wafer_growth", "Wafer growth", "MATERIALS", 3, 4_000_000, 240, facilities=["wafer_plant"], recipes=["grow_silicon", "slice_wafers"])
    s.tech("ev_platform", "Electric vehicle platform", "PRODUCTION", 3, 3_500_000, 220, requires=["battery_manufacturing"], recipes=["assemble_ev"])
    s.tech("aerospace_engineering", "Aerospace engineering", "PRODUCTION", 4, 20_000_000, 400, facilities=["aircraft_factory", "aero_components_plant"], recipes=["build_jet_engine", "assemble_jetliner"])
    s.tech("industrial_robotics", "Industrial robotics", "PRODUCTION", 3, 6_000_000, 260, facilities=["robotics_factory"], recipes=["build_robot_arms", "build_industrial_robots"])
    s.tech("lean_manufacturing", "Lean manufacturing", "MANAGEMENT", 2, 700_000, 90, effects=[("INPUT_SAVINGS", 0.04, "automotive", None), ("INPUT_SAVINGS", 0.04, "electronics", None)])
    s.tech("factory_automation", "Factory automation", "PRODUCTION", 3, 1_800_000, 150, requires=["lean_manufacturing"], effects=[("LABOR_SAVINGS", 0.12, None, None), ("THROUGHPUT_BONUS", 0.06, None, None)])
    s.tech("prefab_construction", "Prefab construction", "PRODUCTION", 2, 600_000, 100, effects=[("THROUGHPUT_BONUS", 0.10, "building", None)])
    return s

"""Electronics: passive and active components, displays and optics, board and computer assembly, connected devices."""
from expgen import ExpSpec


def build():
    s = ExpSpec("electronics", "ElectronicsPack")
    s.role("assembler", "Electronics assembler", "SKILLED", 85.0, 30)
    s.role("quality_inspector", "Quality inspector", "SKILLED", 100.0, 45)

    s.industry("electronic_components", "Electronic components", "ELECTRONICS", "Connectors, passives, crystals, LEDs, RF modules and MEMS sensors")
    s.industry("displays_and_optics", "Displays and optics", "ELECTRONICS", "OLED panels, lens assemblies and touch layers")
    s.industry("computing_hardware", "Computing hardware", "ELECTRONICS", "Motherboards, storage, graphics cards, PCs, servers and network gear")
    s.industry("smart_devices", "Connected devices", "ELECTRONICS", "Wearables, speakers, cameras, drones and smart-home devices")

    s.add_goods("INTERMEDIATE", [
        ("connectors", "Connectors", 2.4), ("inductors", "Inductors", 3.5), ("resistors", "Resistor networks", 0.6), ("quartz_crystals", "Quartz crystals", 8),
        ("solder_paste", "Solder paste", 45), ("led_chips", "LED chips", 1.2), ("flexible_pcb", "Flexible circuits", 22), ("mems_sensors", "MEMS sensors", 9),
        ("rf_modules", "RF modules", 24), ("heat_sinks", "Heat sinks", 6), ("speaker_drivers", "Speaker drivers", 6), ("microphone_modules", "Microphone modules", 5),
        ("oled_panel", "OLED panel", 140), ("lens_assembly", "Lens assembly", 12), ("touch_layer", "Touch layer", 18), ("motherboard", "Motherboard", 120),
        ("ssd_module", "SSD module", 55), ("graphics_card", "Graphics card", 340), ("small_battery", "Small battery cell", 4), ("keyboard_module", "Keyboard module", 14),
        ("cooling_module", "Cooling module", 22),
    ])
    s.add_goods("CONSUMER", [
        ("desktop_pc", "Desktop computer", 900), ("monitor", "Computer monitor", 220), ("ssd_drive", "SSD drive", 90), ("smart_speaker", "Smart speaker", 80),
        ("smart_bulb", "Smart bulb", 14), ("e_reader", "E-reader", 120), ("action_camera", "Action camera", 190), ("vr_headset", "VR headset", 380),
        ("consumer_drone", "Consumer drone", 450), ("smart_thermostat", "Smart thermostat", 130), ("wireless_earbuds", "Wireless earbuds", 95),
        ("fitness_band", "Fitness band", 60), ("power_bank", "Power bank", 28), ("security_camera", "Security camera", 70), ("keyboard", "Keyboard", 35),
    ])

    s.fac("component_works", "Component works", "FACTORY", "ELECTRONICS", 6_200_000, 100, 4, 1_900, 180.0, {"assembler": 3, "quality_inspector": 1}, 600)
    s.fac("microelectronics_plant", "Microelectronics plant", "FACTORY", "ELECTRONICS", 14_000_000, 140, 3, 4_200, 320.0, {"technician": 3, "engineer": 2, "quality_inspector": 2}, 850)
    s.fac("optics_display_plant", "Optics and display plant", "FACTORY", "ELECTRONICS", 11_000_000, 130, 3, 3_400, 280.0, {"technician": 3, "engineer": 1, "assembler": 3}, 780)
    s.fac("board_assembly_plant", "Board assembly plant", "FACTORY", "ELECTRONICS", 9_000_000, 120, 4, 2_800, 240.0, {"assembler": 6, "technician": 2, "quality_inspector": 1}, 700)
    s.fac("computer_assembly_plant", "Computer assembly plant", "FACTORY", "ELECTRONICS", 8_000_000, 110, 4, 2_500, 220.0, {"assembler": 6, "technician": 2}, 650)
    s.fac("smart_device_factory", "Connected device factory", "FACTORY", "ELECTRONICS", 7_000_000, 100, 4, 2_300, 200.0, {"assembler": 6, "technician": 2, "quality_inspector": 1}, 600)

    s.machine("smt_line", "Surface-mount line", 1_400_000, thr=1.3, wear=500, maint=110.0, labor_sav=0.1)
    s.machine("reflow_oven", "Reflow oven", 600_000, thr=1.15, wear=420, maint=50.0)
    s.machine("optics_bonder", "Optical bonding station", 900_000, thr=1.2, wear=450, maint=70.0)

    R = s.rec
    R("stamp_connectors", "Stamp connectors", "electronic_components", "component_works", 2, {"copper_wire": 14, "plastic_granules": 16, "tin_ingot": 1}, {"connectors": 1}, {"assembler": 3, "quality_inspector": 1}, energy=50, margin=0.22)
    R("wind_inductors", "Wind inductors", "electronic_components", "component_works", 2, {"copper_wire": 10, "rare_earth_oxide": 0.2, "plastic_granules": 6}, {"inductors": 1}, {"assembler": 3}, energy=40, margin=0.22)
    R("print_resistor_networks", "Print resistor networks", "electronic_components", "component_works", 2, {"nickel_ingot": 2, "clay": 16, "plastic_granules": 4}, {"resistors": 1}, {"assembler": 3}, energy=40, margin=0.22)
    R("grow_quartz_crystals", "Grow quartz crystals", "electronic_components", "component_works", 5, {"silica_sand": 90, "caustic_soda": 8, "industrial_gases": 10}, {"quartz_crystals": 1}, {"assembler": 2, "quality_inspector": 1}, energy=140, margin=0.26)
    R("mix_solder_paste", "Mix solder paste", "electronic_components", "component_works", 2, {"tin_ingot": 10, "resins_epoxy": 4, "solvents": 6}, {"solder_paste": 1}, {"assembler": 3}, energy=30, margin=0.24)
    R("dice_led_chips", "Dice LED chips", "electronic_components", "component_works", 3, {"silicon_wafer": 1, "rare_earth_oxide": 0.1, "industrial_gases": 6}, {"led_chips": 1}, {"assembler": 2, "quality_inspector": 1}, energy=90, margin=0.26)
    R("extrude_heat_sinks", "Extrude heat sinks", "electronic_components", "component_works", 2, {"aluminum_ingot": 14, "lubricants": 1}, {"heat_sinks": 1}, {"assembler": 3}, energy=70, margin=0.22)
    R("wind_speaker_drivers", "Wind speaker drivers", "electronic_components", "component_works", 2, {"copper_wire": 6, "rare_earth_oxide": 0.2, "steel_sheet": 3, "plastic_granules": 4}, {"speaker_drivers": 1}, {"assembler": 3}, energy=30, margin=0.22)
    R("assemble_microphones", "Assemble microphone modules", "electronic_components", "component_works", 2, {"mems_sensors": 6, "pcb": 3, "plastic_granules": 3}, {"microphone_modules": 1}, {"assembler": 3}, energy=30, margin=0.22)
    R("print_flexible_circuits", "Print flexible circuits", "electronic_components", "microelectronics_plant", 3, {"copper_ingot": 3, "resins_epoxy": 5, "polypropylene": 6, "solvents": 3}, {"flexible_pcb": 1}, {"technician": 3, "quality_inspector": 1}, energy=140, margin=0.26, machines=["reflow_oven"])
    R("etch_mems_sensors", "Etch MEMS sensors", "electronic_components", "microelectronics_plant", 4, {"silicon_wafer": 2, "chip_basic": 30, "industrial_gases": 12}, {"mems_sensors": 1}, {"technician": 4, "engineer": 1, "quality_inspector": 1}, energy=200, margin=0.28, waste={"chemical_waste": 6})
    R("assemble_rf_modules", "Assemble RF modules", "electronic_components", "microelectronics_plant", 4, {"chip_logic": 2, "quartz_crystals": 6, "inductors": 20, "pcb": 4}, {"rf_modules": 1}, {"technician": 4, "engineer": 1}, energy=160, margin=0.28)
    R("laminate_oled_panels", "Laminate OLED panels", "displays_and_optics", "optics_display_plant", 5, {"glass": 8, "flexible_pcb": 6, "led_chips": 40, "chip_basic": 20}, {"oled_panel": 1}, {"technician": 4, "engineer": 1, "assembler": 3}, energy=220, margin=0.28, machines=["optics_bonder"])
    R("grind_lens_assemblies", "Grind lens assemblies", "displays_and_optics", "optics_display_plant", 3, {"glass": 6, "plastic_granules": 4, "polycarbonate": 2}, {"lens_assembly": 1}, {"technician": 3, "assembler": 3}, energy=90, margin=0.24)
    R("coat_touch_layers", "Coat touch layers", "displays_and_optics", "optics_display_plant", 3, {"glass": 5, "flexible_pcb": 2, "silicone": 0.5}, {"touch_layer": 1}, {"technician": 3, "assembler": 3}, energy=100, margin=0.24)
    R("populate_motherboards", "Populate motherboards", "computing_hardware", "board_assembly_plant", 3, {"pcb": 6, "chip_logic": 1, "connectors": 20, "resistors": 120, "quartz_crystals": 2, "solder_paste": 1}, {"motherboard": 1}, {"assembler": 6, "technician": 2}, energy=120, margin=0.24, machines=["smt_line"])
    R("assemble_ssd_modules", "Assemble SSD modules", "computing_hardware", "board_assembly_plant", 3, {"chip_memory": 8, "controller_unit": 0.2, "pcb": 4, "solder_paste": 0.6}, {"ssd_module": 1}, {"assembler": 6, "technician": 2}, energy=100, margin=0.24)
    R("build_graphics_cards", "Build graphics cards", "computing_hardware", "board_assembly_plant", 4, {"chip_logic": 2, "chip_memory": 4, "heat_sinks": 6, "pcb": 4, "power_supply": 1}, {"graphics_card": 1}, {"assembler": 6, "technician": 2, "quality_inspector": 1}, energy=140, margin=0.26)
    R("press_small_batteries", "Press small battery cells", "computing_hardware", "board_assembly_plant", 2, {"lithium_battery_cells": 1, "aluminum_sheet": 2, "plastic_granules": 3}, {"small_battery": 40}, {"assembler": 6}, energy=60, margin=0.22)
    R("assemble_keyboard_modules", "Assemble keyboard modules", "computing_hardware", "board_assembly_plant", 2, {"pcb": 2, "connectors": 12, "plastic_granules": 14, "resistors": 40}, {"keyboard_module": 1}, {"assembler": 6}, energy=40, margin=0.22)
    R("build_cooling_modules", "Build cooling modules", "computing_hardware", "board_assembly_plant", 2, {"heat_sinks": 6, "electric_motor": 0.2, "copper_pipe": 1, "plastic_granules": 3}, {"cooling_module": 1}, {"assembler": 6}, energy=40, margin=0.22)
    R("assemble_desktop_pcs", "Assemble desktop computers", "computing_hardware", "computer_assembly_plant", 3, {"motherboard": 2, "ssd_module": 2, "graphics_card": 1, "cooling_module": 2, "keyboard_module": 2, "power_supply": 2, "plastic_housings": 4, "packaging": 4}, {"desktop_pc": 1}, {"assembler": 6, "technician": 2}, energy=100, margin=0.24)
    R("assemble_monitors", "Assemble monitors", "computing_hardware", "computer_assembly_plant", 3, {"display_panel": 4, "power_supply": 2, "plastic_housings": 4, "packaging": 4}, {"monitor": 1}, {"assembler": 6, "technician": 1}, energy=70, margin=0.24)
    R("package_ssd_drives", "Package SSD drives", "computing_hardware", "computer_assembly_plant", 2, {"ssd_module": 6, "plastic_housings": 8, "packaging": 4}, {"ssd_drive": 8}, {"assembler": 6}, energy=40, margin=0.22)
    R("assemble_keyboards", "Assemble keyboards", "computing_hardware", "computer_assembly_plant", 2, {"keyboard_module": 4, "plastic_housings": 4, "packaging": 4}, {"keyboard": 6}, {"assembler": 6}, energy=30, margin=0.22)
    R("assemble_servers", "Assemble servers", "computing_hardware", "computer_assembly_plant", 5, {"motherboard": 8, "ssd_module": 12, "chip_memory": 40, "cooling_module": 6, "power_supply": 6, "steel_sheet": 12}, {"server_hardware": 1}, {"assembler": 6, "technician": 3}, energy=160, margin=0.24)
    R("assemble_network_gear", "Assemble network equipment", "computing_hardware", "computer_assembly_plant", 4, {"motherboard": 4, "rf_modules": 4, "connectors": 60, "power_supply": 4, "steel_sheet": 8}, {"network_equipment": 1}, {"assembler": 6, "technician": 3}, energy=120, margin=0.24)
    R("assemble_smart_speakers", "Assemble smart speakers", "smart_devices", "smart_device_factory", 2, {"speaker_drivers": 3, "microphone_modules": 3, "rf_modules": 1, "pcb": 2, "plastic_housings": 2, "packaging": 2}, {"smart_speaker": 1}, {"assembler": 6, "technician": 1}, energy=50, margin=0.25)
    R("assemble_smart_bulbs", "Assemble smart bulbs", "smart_devices", "smart_device_factory", 2, {"led_chips": 24, "rf_modules": 0.3, "power_supply": 0.2, "plastic_housings": 2, "packaging": 2}, {"smart_bulb": 4}, {"assembler": 6}, energy=40, margin=0.25)
    R("assemble_e_readers", "Assemble e-readers", "smart_devices", "smart_device_factory", 3, {"touch_layer": 2, "oled_panel": 0.3, "flexible_pcb": 1, "small_battery": 2, "plastic_housings": 2, "packaging": 2}, {"e_reader": 1}, {"assembler": 6, "technician": 1}, energy=60, margin=0.25)
    R("assemble_action_cameras", "Assemble action cameras", "smart_devices", "smart_device_factory", 3, {"camera_module": 2, "lens_assembly": 2, "small_battery": 2, "mems_sensors": 2, "plastic_housings": 2, "packaging": 2}, {"action_camera": 1}, {"assembler": 6, "technician": 1}, energy=60, margin=0.25)
    R("assemble_vr_headsets", "Assemble VR headsets", "smart_devices", "smart_device_factory", 4, {"oled_panel": 1, "lens_assembly": 2, "mems_sensors": 4, "chip_logic": 1, "small_battery": 2, "plastic_housings": 2, "packaging": 2}, {"vr_headset": 1}, {"assembler": 6, "technician": 2}, energy=80, margin=0.26)
    R("assemble_consumer_drones", "Assemble consumer drones", "smart_devices", "smart_device_factory", 4, {"electric_motor": 1, "mems_sensors": 4, "rf_modules": 1, "camera_module": 1, "lens_assembly": 1, "small_battery": 6, "carbon_fiber": 0.5}, {"consumer_drone": 1}, {"assembler": 6, "technician": 2}, energy=70, margin=0.26)
    R("assemble_thermostats", "Assemble smart thermostats", "smart_devices", "smart_device_factory", 2, {"touch_layer": 1, "rf_modules": 1, "mems_sensors": 2, "pcb": 1, "plastic_housings": 2, "packaging": 2}, {"smart_thermostat": 1}, {"assembler": 6}, energy=40, margin=0.25)
    R("assemble_earbuds", "Assemble wireless earbuds", "smart_devices", "smart_device_factory", 2, {"speaker_drivers": 2, "microphone_modules": 2, "rf_modules": 1, "flexible_pcb": 1, "small_battery": 2, "packaging": 2}, {"wireless_earbuds": 1}, {"assembler": 6}, energy=40, margin=0.25)
    R("assemble_fitness_bands", "Assemble fitness bands", "smart_devices", "smart_device_factory", 2, {"mems_sensors": 3, "touch_layer": 1, "flexible_pcb": 1, "small_battery": 1, "silicone": 0.5, "packaging": 2}, {"fitness_band": 1}, {"assembler": 6}, energy=40, margin=0.25)
    R("assemble_power_banks", "Assemble power banks", "smart_devices", "smart_device_factory", 2, {"small_battery": 6, "connectors": 4, "pcb": 1, "plastic_housings": 2, "packaging": 2}, {"power_bank": 1}, {"assembler": 6}, energy=30, margin=0.24)
    R("assemble_security_cameras", "Assemble security cameras", "smart_devices", "smart_device_factory", 3, {"camera_module": 1, "lens_assembly": 1, "rf_modules": 1, "small_battery": 2, "plastic_housings": 2, "packaging": 2}, {"security_camera": 1}, {"assembler": 6}, energy=40, margin=0.25)

    s.tech("component_miniaturization", "Component miniaturization", "MATERIALS", 2, 1_100_000, 110, requires=["lean_manufacturing"],
           facilities=["component_works"], machines=["reflow_oven"], effects=[("YIELD_BONUS", 0.05, "electronic_components", None)])
    s.tech("quality_automation", "Automated optical inspection", "MANAGEMENT", 2, 750_000, 90, requires=["lean_manufacturing"],
           effects=[("QUALITY_BONUS", 0.12, "electronic_components", None), ("QUALITY_BONUS", 0.10, "computing_hardware", None)])
    s.tech("microelectronics_packaging", "Microelectronics packaging", "DIGITAL", 4, 6_000_000, 260, requires=["photolithography", "component_miniaturization"],
           facilities=["microelectronics_plant"], recipes=["assemble_rf_modules"])
    s.tech("display_technology", "Display technology", "DIGITAL", 3, 3_200_000, 180, requires=["wafer_growth"],
           facilities=["optics_display_plant"], machines=["optics_bonder"], effects=[("YIELD_BONUS", 0.05, "displays_and_optics", None)])
    s.tech("smt_automation", "Surface-mount automation", "PRODUCTION", 3, 2_200_000, 150, requires=["factory_automation"],
           facilities=["board_assembly_plant"], machines=["smt_line"], effects=[("LABOR_SAVINGS", 0.08, "computing_hardware", None)])
    s.tech("wireless_standards", "Wireless standards", "DIGITAL", 3, 2_000_000, 140, requires=["mobile_networks"],
           effects=[("THROUGHPUT_BONUS", 0.06, "smart_devices", None)])
    s.tech("iot_platforms", "Internet-of-things platforms", "DIGITAL", 3, 1_800_000, 140, requires=["wireless_standards", "cloud_infrastructure"],
           effects=[("INPUT_SAVINGS", 0.05, "smart_devices", None)])
    s.tech("vr_optics", "Virtual reality optics", "DIGITAL", 3, 2_400_000, 160, requires=["display_technology", "wireless_standards"],
           recipes=["assemble_vr_headsets"])

    D = s.dem
    D("desktop_pc", 1, 4, 9, 1.0, 1.2, ["laptop"], {}, 0.6)
    D("monitor", 1, 5, 10, 1.0, 1.1, [], {}, 0.6)
    D("ssd_drive", 4, 14, 26, 1.0, 1.0, [], {}, 0.5)
    D("smart_speaker", 5, 16, 30, 1.1, 1.2, [], {"WINTER": 1.5}, 0.5)
    D("smart_bulb", 20, 50, 90, 1.1, 1.1, [], {}, 0.4)
    D("e_reader", 2, 6, 12, 1.1, 1.2, ["tablet"], {}, 0.6)
    D("action_camera", 1, 4, 9, 1.2, 1.4, [], {"SUMMER": 1.5}, 0.7)
    D("vr_headset", 0, 2, 6, 1.3, 1.5, ["game_console"], {"WINTER": 1.6}, 0.8)
    D("consumer_drone", 0, 2, 5, 1.3, 1.5, [], {"SUMMER": 1.4}, 0.8)
    D("smart_thermostat", 2, 6, 12, 1.0, 1.0, [], {"WINTER": 1.3}, 0.6)
    D("wireless_earbuds", 10, 30, 60, 1.2, 1.3, ["headphones"], {"WINTER": 1.4}, 0.4)
    D("fitness_band", 6, 18, 36, 1.2, 1.3, ["smartwatch"], {"SPRING": 1.4}, 0.5)
    D("power_bank", 14, 32, 50, 1.0, 0.9, [], {}, 0.4)
    D("security_camera", 3, 9, 16, 1.0, 1.0, [], {}, 0.5)
    D("keyboard", 8, 20, 30, 0.9, 0.8, [], {}, 0.5)
    D("server_hardware", 0, 1, 1, 0.6, 0.6, [], {}, 0.97)
    D("network_equipment", 0, 1, 1, 0.6, 0.6, [], {}, 0.96)
    return s

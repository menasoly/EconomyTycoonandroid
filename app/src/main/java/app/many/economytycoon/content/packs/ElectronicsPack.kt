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

object ElectronicsPack {
    val pack: ContentPack = contentPack("electronics", 1, 0) {
        industry("electronic_components", "Electronic components", IndustryFamily.ELECTRONICS, "Connectors, passives, crystals, LEDs, RF modules and MEMS sensors")
        industry("displays_and_optics", "Displays and optics", IndustryFamily.ELECTRONICS, "OLED panels, lens assemblies and touch layers")
        industry("computing_hardware", "Computing hardware", IndustryFamily.ELECTRONICS, "Motherboards, storage, graphics cards, PCs, servers and network gear")
        industry("smart_devices", "Connected devices", IndustryFamily.ELECTRONICS, "Wearables, speakers, cameras, drones and smart-home devices")

        good("connectors", "Connectors", GoodCategory.INTERMEDIATE, 2.4)
        good("inductors", "Inductors", GoodCategory.INTERMEDIATE, 3.5)
        good("resistors", "Resistor networks", GoodCategory.INTERMEDIATE, 0.6)
        good("quartz_crystals", "Quartz crystals", GoodCategory.INTERMEDIATE, 8.0)
        good("solder_paste", "Solder paste", GoodCategory.INTERMEDIATE, 45.0)
        good("led_chips", "LED chips", GoodCategory.INTERMEDIATE, 1.2)
        good("flexible_pcb", "Flexible circuits", GoodCategory.INTERMEDIATE, 22.0)
        good("mems_sensors", "MEMS sensors", GoodCategory.INTERMEDIATE, 9.0)
        good("rf_modules", "RF modules", GoodCategory.INTERMEDIATE, 24.0)
        good("heat_sinks", "Heat sinks", GoodCategory.INTERMEDIATE, 6.0)
        good("speaker_drivers", "Speaker drivers", GoodCategory.INTERMEDIATE, 6.0)
        good("microphone_modules", "Microphone modules", GoodCategory.INTERMEDIATE, 5.0)
        good("oled_panel", "OLED panel", GoodCategory.INTERMEDIATE, 140.0)
        good("lens_assembly", "Lens assembly", GoodCategory.INTERMEDIATE, 12.0)
        good("touch_layer", "Touch layer", GoodCategory.INTERMEDIATE, 18.0)
        good("motherboard", "Motherboard", GoodCategory.INTERMEDIATE, 120.0)
        good("ssd_module", "SSD module", GoodCategory.INTERMEDIATE, 55.0)
        good("graphics_card", "Graphics card", GoodCategory.INTERMEDIATE, 340.0)
        good("small_battery", "Small battery cell", GoodCategory.INTERMEDIATE, 4.0)
        good("keyboard_module", "Keyboard module", GoodCategory.INTERMEDIATE, 14.0)
        good("cooling_module", "Cooling module", GoodCategory.INTERMEDIATE, 22.0)
        good("desktop_pc", "Desktop computer", GoodCategory.CONSUMER, 900.0)
        good("monitor", "Computer monitor", GoodCategory.CONSUMER, 220.0)
        good("ssd_drive", "SSD drive", GoodCategory.CONSUMER, 90.0)
        good("smart_speaker", "Smart speaker", GoodCategory.CONSUMER, 80.0)
        good("smart_bulb", "Smart bulb", GoodCategory.CONSUMER, 14.0)
        good("e_reader", "E-reader", GoodCategory.CONSUMER, 120.0)
        good("action_camera", "Action camera", GoodCategory.CONSUMER, 190.0)
        good("vr_headset", "VR headset", GoodCategory.CONSUMER, 380.0)
        good("consumer_drone", "Consumer drone", GoodCategory.CONSUMER, 450.0)
        good("smart_thermostat", "Smart thermostat", GoodCategory.CONSUMER, 130.0)
        good("wireless_earbuds", "Wireless earbuds", GoodCategory.CONSUMER, 95.0)
        good("fitness_band", "Fitness band", GoodCategory.CONSUMER, 60.0)
        good("power_bank", "Power bank", GoodCategory.CONSUMER, 28.0)
        good("security_camera", "Security camera", GoodCategory.CONSUMER, 70.0)
        good("keyboard", "Keyboard", GoodCategory.CONSUMER, 35.0)

        role("assembler", "Electronics assembler", SkillBand.SKILLED, 85.0, 30)
        role("quality_inspector", "Quality inspector", SkillBand.SKILLED, 100.0, 45)

        machine("smt_line", "Surface-mount line", 1400000.0, throughput = 1.3, maintenance = 110.0, laborSavings = 0.1, tech = "smt_automation")
        machine("reflow_oven", "Reflow oven", 600000.0, throughput = 1.15, wearPerBatchPpm = 420, maintenance = 50.0, tech = "component_miniaturization")
        machine("optics_bonder", "Optical bonding station", 900000.0, throughput = 1.2, wearPerBatchPpm = 450, maintenance = 70.0, tech = "display_technology")

        facility("component_works", "Component works", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 6200000.0; buildTicks = 100; lines = 4; storage = 180000.0; upkeep = 1900.0
            energyPerLine = 180.0
            tech = "component_miniaturization"
            staff("assembler", 3)
            staff("quality_inspector", 1)
        }
        facility("microelectronics_plant", "Microelectronics plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 14000000.0; buildTicks = 140; lines = 3; storage = 55000.0; upkeep = 4200.0
            energyPerLine = 320.0
            tech = "microelectronics_packaging"
            staff("technician", 3)
            staff("engineer", 2)
            staff("quality_inspector", 2)
        }
        facility("optics_display_plant", "Optics and display plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 11000000.0; buildTicks = 130; lines = 3; storage = 40000.0; upkeep = 3400.0
            energyPerLine = 280.0
            tech = "display_technology"
            staff("technician", 3)
            staff("engineer", 1)
            staff("assembler", 3)
        }
        facility("board_assembly_plant", "Board assembly plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 9000000.0; buildTicks = 120; lines = 4; storage = 55000.0; upkeep = 2800.0
            energyPerLine = 240.0
            tech = "smt_automation"
            staff("assembler", 6)
            staff("technician", 2)
            staff("quality_inspector", 1)
        }
        facility("computer_assembly_plant", "Computer assembly plant", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 8000000.0; buildTicks = 110; lines = 4; storage = 15000.0; upkeep = 2500.0
            energyPerLine = 220.0
            staff("assembler", 6)
            staff("technician", 2)
        }
        facility("smart_device_factory", "Connected device factory", FacilityClass.FACTORY, IndustryFamily.ELECTRONICS) {
            cost = 7000000.0; buildTicks = 100; lines = 4; storage = 40000.0; upkeep = 2300.0
            energyPerLine = 200.0
            staff("assembler", 6)
            staff("technician", 2)
            staff("quality_inspector", 1)
        }

        recipe("stamp_connectors", "Stamp connectors", "electronic_components", "component_works", 2) {
            input("copper_wire", 110.0)
            input("plastic_granules", 130.0)
            input("tin_ingot", 8.0)
            output("connectors", 14450.0)
            labor("assembler", 3)
            labor("quality_inspector", 1)
            energy = 400.0
        }
        recipe("wind_inductors", "Wind inductors", "electronic_components", "component_works", 2) {
            input("copper_wire", 125.0)
            input("rare_earth_oxide", 2.5)
            input("plastic_granules", 75.0)
            output("inductors", 10000.0)
            labor("assembler", 3)
            energy = 500.0
        }
        recipe("print_resistor_networks", "Print resistor networks", "electronic_components", "component_works", 2) {
            input("nickel_ingot", 68.0)
            input("clay", 545.0)
            input("plastic_granules", 135.0)
            output("resistors", 57850.0)
            labor("assembler", 3)
            energy = 811.0
        }
        recipe("grow_quartz_crystals", "Grow quartz crystals", "electronic_components", "component_works", 5) {
            input("silica_sand", 2670.0)
            input("caustic_soda", 235.0)
            input("industrial_gases", 295.0)
            output("quartz_crystals", 9500.0)
            labor("assembler", 2)
            labor("quality_inspector", 1)
            energy = 1707.0
        }
        recipe("mix_solder_paste", "Mix solder paste", "electronic_components", "component_works", 2) {
            input("tin_ingot", 94.0)
            input("resins_epoxy", 38.0)
            input("solvents", 57.0)
            output("solder_paste", 725.0)
            labor("assembler", 3)
            energy = 283.0
        }
        recipe("dice_led_chips", "Dice LED chips", "electronic_components", "component_works", 3) {
            input("silicon_wafer", 48.0)
            input("rare_earth_oxide", 4.8)
            input("industrial_gases", 290.0)
            output("led_chips", 37950.0)
            labor("assembler", 2)
            labor("quality_inspector", 1)
            energy = 1024.0
        }
        recipe("extrude_heat_sinks", "Extrude heat sinks", "electronic_components", "component_works", 2) {
            input("aluminum_ingot", 195.0)
            input("lubricants", 14.0)
            output("heat_sinks", 5850.0)
            labor("assembler", 3)
            energy = 811.0
        }
        recipe("wind_speaker_drivers", "Wind speaker drivers", "electronic_components", "component_works", 2) {
            input("copper_wire", 96.0)
            input("rare_earth_oxide", 3.2)
            input("steel_sheet", 48.0)
            input("plastic_granules", 64.0)
            output("speaker_drivers", 5800.0)
            labor("assembler", 3)
            energy = 480.0
        }
        recipe("assemble_microphones", "Assemble microphone modules", "electronic_components", "component_works", 2) {
            input("mems_sensors", 525.0)
            input("pcb", 265.0)
            input("plastic_granules", 265.0)
            output("microphone_modules", 7000.0)
            labor("assembler", 3)
            energy = 811.0
        }
        recipe("print_flexible_circuits", "Print flexible circuits", "electronic_components", "microelectronics_plant", 3) {
            input("copper_ingot", 150.0)
            input("resins_epoxy", 255.0)
            input("polypropylene", 305.0)
            input("solvents", 150.0)
            output("flexible_pcb", 4730.0)
            labor("technician", 3)
            labor("quality_inspector", 1)
            machine("reflow_oven")
            energy = 2350.0
        }
        recipe("etch_mems_sensors", "Etch MEMS sensors", "electronic_components", "microelectronics_plant", 4) {
            input("silicon_wafer", 130.0)
            input("chip_basic", 1950.0)
            input("industrial_gases", 780.0)
            output("mems_sensors", 14550.0)
            waste("chemical_waste", 390.0)
            labor("technician", 4)
            labor("engineer", 1)
            labor("quality_inspector", 1)
            energy = 2876.0
        }
        recipe("assemble_rf_modules", "Assemble RF modules", "electronic_components", "microelectronics_plant", 4) {
            input("chip_logic", 460.0)
            input("quartz_crystals", 1380.0)
            input("inductors", 4610.0)
            input("pcb", 920.0)
            output("rf_modules", 5450.0)
            labor("technician", 4)
            labor("engineer", 1)
            energy = 2887.0
            tech = "microelectronics_packaging"
        }
        recipe("laminate_oled_panels", "Laminate OLED panels", "displays_and_optics", "optics_display_plant", 5) {
            input("glass", 1190.0)
            input("flexible_pcb", 890.0)
            input("led_chips", 5950.0)
            input("chip_basic", 2970.0)
            output("oled_panel", 990.0)
            labor("technician", 4)
            labor("engineer", 1)
            labor("assembler", 3)
            machine("optics_bonder")
            energy = 3010.0
        }
        recipe("grind_lens_assemblies", "Grind lens assemblies", "displays_and_optics", "optics_display_plant", 3) {
            input("glass", 555.0)
            input("plastic_granules", 370.0)
            input("polycarbonate", 185.0)
            output("lens_assembly", 7850.0)
            labor("technician", 3)
            labor("assembler", 3)
            energy = 2142.0
        }
        recipe("coat_touch_layers", "Coat touch layers", "displays_and_optics", "optics_display_plant", 3) {
            input("glass", 955.0)
            input("flexible_pcb", 380.0)
            input("silicone", 96.0)
            output("touch_layer", 5250.0)
            labor("technician", 3)
            labor("assembler", 3)
            energy = 2142.0
        }
        recipe("populate_motherboards", "Populate motherboards", "computing_hardware", "board_assembly_plant", 3) {
            input("pcb", 660.0)
            input("chip_logic", 110.0)
            input("connectors", 2200.0)
            input("resistors", 13200.0)
            input("quartz_crystals", 220.0)
            input("solder_paste", 110.0)
            output("motherboard", 530.0)
            labor("assembler", 6)
            labor("technician", 2)
            machine("smt_line")
            energy = 1423.0
        }
        recipe("assemble_ssd_modules", "Assemble SSD modules", "computing_hardware", "board_assembly_plant", 3) {
            input("chip_memory", 730.0)
            input("controller_unit", 18.0)
            input("pcb", 365.0)
            input("solder_paste", 55.0)
            output("ssd_module", 1160.0)
            labor("assembler", 6)
            labor("technician", 2)
            energy = 1423.0
        }
        recipe("build_graphics_cards", "Build graphics cards", "computing_hardware", "board_assembly_plant", 4) {
            input("chip_logic", 210.0)
            input("chip_memory", 420.0)
            input("heat_sinks", 635.0)
            input("pcb", 420.0)
            input("power_supply", 105.0)
            output("graphics_card", 235.0)
            labor("assembler", 6)
            labor("technician", 2)
            labor("quality_inspector", 1)
            energy = 1733.0
        }
        recipe("press_small_batteries", "Press small battery cells", "computing_hardware", "board_assembly_plant", 2) {
            input("lithium_battery_cells", 48.0)
            input("aluminum_sheet", 97.0)
            input("plastic_granules", 145.0)
            output("small_battery", 11400.0)
            labor("assembler", 6)
            energy = 1051.0
        }
        recipe("assemble_keyboard_modules", "Assemble keyboard modules", "computing_hardware", "board_assembly_plant", 2) {
            input("pcb", 72.0)
            input("connectors", 430.0)
            input("plastic_granules", 500.0)
            input("resistors", 1440.0)
            output("keyboard_module", 3250.0)
            labor("assembler", 6)
            energy = 1051.0
        }
        recipe("build_cooling_modules", "Build cooling modules", "computing_hardware", "board_assembly_plant", 2) {
            input("heat_sinks", 460.0)
            input("electric_motor", 15.5)
            input("copper_pipe", 76.0)
            input("plastic_granules", 230.0)
            output("cooling_module", 2080.0)
            labor("assembler", 6)
            energy = 1051.0
        }
        recipe("assemble_desktop_pcs", "Assemble desktop computers", "computing_hardware", "computer_assembly_plant", 3) {
            input("motherboard", 83.0)
            input("ssd_module", 83.0)
            input("graphics_card", 41.0)
            input("cooling_module", 83.0)
            input("keyboard_module", 83.0)
            input("power_supply", 83.0)
            input("plastic_housings", 165.0)
            input("packaging", 165.0)
            output("desktop_pc", 67.0)
            labor("assembler", 6)
            labor("technician", 2)
            energy = 1337.0
        }
        recipe("assemble_monitors", "Assemble monitors", "computing_hardware", "computer_assembly_plant", 3) {
            input("display_panel", 310.0)
            input("power_supply", 155.0)
            input("plastic_housings", 310.0)
            input("packaging", 310.0)
            output("monitor", 270.0)
            labor("assembler", 6)
            labor("technician", 1)
            energy = 1347.0
        }
        recipe("package_ssd_drives", "Package SSD drives", "computing_hardware", "computer_assembly_plant", 2) {
            input("ssd_module", 415.0)
            input("plastic_housings", 550.0)
            input("packaging", 275.0)
            output("ssd_drive", 480.0)
            labor("assembler", 6)
            energy = 989.0
        }
        recipe("assemble_keyboards", "Assemble keyboards", "computing_hardware", "computer_assembly_plant", 2) {
            input("keyboard_module", 1200.0)
            input("plastic_housings", 1200.0)
            input("packaging", 1200.0)
            output("keyboard", 1230.0)
            labor("assembler", 6)
            energy = 989.0
        }
        recipe("assemble_servers", "Assemble servers", "computing_hardware", "computer_assembly_plant", 5) {
            input("motherboard", 105.0)
            input("ssd_module", 155.0)
            input("chip_memory", 520.0)
            input("cooling_module", 78.0)
            input("power_supply", 78.0)
            input("steel_sheet", 155.0)
            output("server_hardware", 24.0)
            labor("assembler", 6)
            labor("technician", 3)
            energy = 2083.0
        }
        recipe("assemble_network_gear", "Assemble network equipment", "computing_hardware", "computer_assembly_plant", 4) {
            input("motherboard", 115.0)
            input("rf_modules", 115.0)
            input("connectors", 1750.0)
            input("power_supply", 115.0)
            input("steel_sheet", 235.0)
            output("network_equipment", 44.0)
            labor("assembler", 6)
            labor("technician", 3)
            energy = 1771.0
        }
        recipe("assemble_smart_speakers", "Assemble smart speakers", "smart_devices", "smart_device_factory", 2) {
            input("speaker_drivers", 650.0)
            input("microphone_modules", 650.0)
            input("rf_modules", 215.0)
            input("pcb", 430.0)
            input("plastic_housings", 430.0)
            input("packaging", 430.0)
            output("smart_speaker", 460.0)
            labor("assembler", 6)
            labor("technician", 1)
            energy = 818.0
        }
        recipe("assemble_smart_bulbs", "Assemble smart bulbs", "smart_devices", "smart_device_factory", 2) {
            input("led_chips", 8950.0)
            input("rf_modules", 110.0)
            input("power_supply", 75.0)
            input("plastic_housings", 745.0)
            input("packaging", 745.0)
            output("smart_bulb", 2630.0)
            labor("assembler", 6)
            energy = 824.0
        }
        recipe("assemble_e_readers", "Assemble e-readers", "smart_devices", "smart_device_factory", 3) {
            input("touch_layer", 525.0)
            input("oled_panel", 79.0)
            input("flexible_pcb", 265.0)
            input("small_battery", 525.0)
            input("plastic_housings", 525.0)
            input("packaging", 525.0)
            output("e_reader", 460.0)
            labor("assembler", 6)
            labor("technician", 1)
            energy = 1226.0
        }
        recipe("assemble_action_cameras", "Assemble action cameras", "smart_devices", "smart_device_factory", 3) {
            input("camera_module", 555.0)
            input("lens_assembly", 555.0)
            input("small_battery", 555.0)
            input("mems_sensors", 555.0)
            input("plastic_housings", 555.0)
            input("packaging", 555.0)
            output("action_camera", 290.0)
            labor("assembler", 6)
            labor("technician", 1)
            energy = 1226.0
        }
        recipe("assemble_vr_headsets", "Assemble VR headsets", "smart_devices", "smart_device_factory", 4) {
            input("oled_panel", 135.0)
            input("lens_assembly", 275.0)
            input("mems_sensors", 545.0)
            input("chip_logic", 135.0)
            input("small_battery", 275.0)
            input("plastic_housings", 275.0)
            input("packaging", 275.0)
            output("vr_headset", 185.0)
            labor("assembler", 6)
            labor("technician", 2)
            energy = 1557.0
            tech = "vr_optics"
        }
        recipe("assemble_consumer_drones", "Assemble consumer drones", "smart_devices", "smart_device_factory", 4) {
            input("electric_motor", 96.0)
            input("mems_sensors", 385.0)
            input("rf_modules", 96.0)
            input("camera_module", 96.0)
            input("lens_assembly", 96.0)
            input("small_battery", 575.0)
            input("carbon_fiber", 48.0)
            output("consumer_drone", 160.0)
            labor("assembler", 6)
            labor("technician", 2)
            energy = 1557.0
        }
        recipe("assemble_thermostats", "Assemble smart thermostats", "smart_devices", "smart_device_factory", 2) {
            input("touch_layer", 245.0)
            input("rf_modules", 245.0)
            input("mems_sensors", 490.0)
            input("pcb", 245.0)
            input("plastic_housings", 490.0)
            input("packaging", 490.0)
            output("smart_thermostat", 285.0)
            labor("assembler", 6)
            energy = 824.0
        }
        recipe("assemble_earbuds", "Assemble wireless earbuds", "smart_devices", "smart_device_factory", 2) {
            input("speaker_drivers", 535.0)
            input("microphone_modules", 535.0)
            input("rf_modules", 270.0)
            input("flexible_pcb", 270.0)
            input("small_battery", 535.0)
            input("packaging", 535.0)
            output("wireless_earbuds", 390.0)
            labor("assembler", 6)
            energy = 824.0
        }
        recipe("assemble_fitness_bands", "Assemble fitness bands", "smart_devices", "smart_device_factory", 2) {
            input("mems_sensors", 360.0)
            input("touch_layer", 120.0)
            input("flexible_pcb", 120.0)
            input("small_battery", 120.0)
            input("silicone", 60.0)
            input("packaging", 240.0)
            output("fitness_band", 615.0)
            labor("assembler", 6)
            energy = 824.0
        }
        recipe("assemble_power_banks", "Assemble power banks", "smart_devices", "smart_device_factory", 2) {
            input("small_battery", 2160.0)
            input("connectors", 1440.0)
            input("pcb", 360.0)
            input("plastic_housings", 720.0)
            input("packaging", 720.0)
            output("power_bank", 1360.0)
            labor("assembler", 6)
            energy = 859.0
        }
        recipe("assemble_security_cameras", "Assemble security cameras", "smart_devices", "smart_device_factory", 3) {
            input("camera_module", 385.0)
            input("lens_assembly", 385.0)
            input("rf_modules", 385.0)
            input("small_battery", 765.0)
            input("plastic_housings", 765.0)
            input("packaging", 765.0)
            output("security_camera", 795.0)
            labor("assembler", 6)
            energy = 1235.0
        }

        tech("component_miniaturization", "Component miniaturization", TechCategory.MATERIALS, 2, 1100000.0, 110) {
            requires("lean_manufacturing")
            unlocksFacility("component_works")
            unlocksMachine("reflow_oven")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "electronic_components")
        }
        tech("quality_automation", "Automated optical inspection", TechCategory.MANAGEMENT, 2, 750000.0, 90) {
            requires("lean_manufacturing")
            effect(EffectKind.QUALITY_BONUS, 0.12, industry = "electronic_components")
            effect(EffectKind.QUALITY_BONUS, 0.1, industry = "computing_hardware")
        }
        tech("microelectronics_packaging", "Microelectronics packaging", TechCategory.DIGITAL, 4, 6000000.0, 260) {
            requires("photolithography", "component_miniaturization")
            unlocksRecipe("assemble_rf_modules")
            unlocksFacility("microelectronics_plant")
        }
        tech("display_technology", "Display technology", TechCategory.DIGITAL, 3, 3200000.0, 180) {
            requires("wafer_growth")
            unlocksFacility("optics_display_plant")
            unlocksMachine("optics_bonder")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "displays_and_optics")
        }
        tech("smt_automation", "Surface-mount automation", TechCategory.PRODUCTION, 3, 2200000.0, 150) {
            requires("factory_automation")
            unlocksFacility("board_assembly_plant")
            unlocksMachine("smt_line")
            effect(EffectKind.LABOR_SAVINGS, 0.08, industry = "computing_hardware")
        }
        tech("wireless_standards", "Wireless standards", TechCategory.DIGITAL, 3, 2000000.0, 140) {
            requires("mobile_networks")
            effect(EffectKind.THROUGHPUT_BONUS, 0.06, industry = "smart_devices")
        }
        tech("iot_platforms", "Internet-of-things platforms", TechCategory.DIGITAL, 3, 1800000.0, 140) {
            requires("wireless_standards", "cloud_infrastructure")
            effect(EffectKind.INPUT_SAVINGS, 0.05, industry = "smart_devices")
        }
        tech("vr_optics", "Virtual reality optics", TechCategory.DIGITAL, 3, 2400000.0, 160) {
            requires("display_technology", "wireless_standards")
            unlocksRecipe("assemble_vr_headsets")
        }

        demand("desktop_pc", 1L, 4L, 9L, 1.0, incomeElasticity = 1.2, substitutes = listOf("laptop"), nonConsumers = 0.6)
        demand("monitor", 1L, 5L, 10L, 1.0, incomeElasticity = 1.1, nonConsumers = 0.6)
        demand("ssd_drive", 4L, 14L, 26L, 1.0, incomeElasticity = 1.0, nonConsumers = 0.5)
        demand("smart_speaker", 5L, 16L, 30L, 1.1, incomeElasticity = 1.2, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.5), nonConsumers = 0.5)
        demand("smart_bulb", 20L, 50L, 90L, 1.1, incomeElasticity = 1.1, nonConsumers = 0.4)
        demand("e_reader", 2L, 6L, 12L, 1.1, incomeElasticity = 1.2, substitutes = listOf("tablet"), nonConsumers = 0.6)
        demand("action_camera", 1L, 4L, 9L, 1.2, incomeElasticity = 1.4, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.5), nonConsumers = 0.7)
        demand("vr_headset", 0L, 2L, 6L, 1.3, incomeElasticity = 1.5, substitutes = listOf("game_console"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.6), nonConsumers = 0.8)
        demand("consumer_drone", 0L, 2L, 5L, 1.3, incomeElasticity = 1.5, seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.4), nonConsumers = 0.8)
        demand("smart_thermostat", 2L, 6L, 12L, 1.0, incomeElasticity = 1.0, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.3), nonConsumers = 0.6)
        demand("wireless_earbuds", 10L, 30L, 60L, 1.2, incomeElasticity = 1.3, substitutes = listOf("headphones"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.4), nonConsumers = 0.4)
        demand("fitness_band", 6L, 18L, 36L, 1.2, incomeElasticity = 1.3, substitutes = listOf("smartwatch"), seasons = mapOf(app.many.economytycoon.core.Season.SPRING to 1.4), nonConsumers = 0.5)
        demand("power_bank", 14L, 32L, 50L, 1.0, incomeElasticity = 0.9, nonConsumers = 0.4)
        demand("security_camera", 3L, 9L, 16L, 1.0, incomeElasticity = 1.0, nonConsumers = 0.5)
        demand("keyboard", 8L, 20L, 30L, 0.9, nonConsumers = 0.5)
        demand("server_hardware", 0L, 1L, 1L, 0.6, incomeElasticity = 0.6, nonConsumers = 0.97)
        demand("network_equipment", 0L, 1L, 1L, 0.6, incomeElasticity = 0.6, nonConsumers = 0.96)
    }
}

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

object MedicalDevicesPack {
    val pack: ContentPack = contentPack("medical_devices", 1, 0) {
        industry("medical_consumables", "Medical consumables", IndustryFamily.HEALTHCARE, "Gloves, masks, syringes, infusion sets, dressings and catheters")
        industry("implants_and_surgical", "Implants and surgical instruments", IndustryFamily.HEALTHCARE, "Surgical steel, instrument sets, orthopedic implants and stents")
        industry("patient_monitoring", "Patient monitoring and diagnostics", IndustryFamily.HEALTHCARE, "Pacemakers, monitors, pumps, ventilators and home diagnostics")
        industry("diagnostic_imaging", "Diagnostic imaging", IndustryFamily.HEALTHCARE, "Ultrasound, X-ray and magnetic resonance systems")
        industry("hospital_equipment", "Hospital equipment", IndustryFamily.HEALTHCARE, "Beds, wheelchairs, sterilizers and equipment kits")

        good("medical_grade_polymer", "Medical-grade polymer", GoodCategory.INTERMEDIATE, 90.0)
        good("nitrile_latex", "Nitrile latex", GoodCategory.INTERMEDIATE, 70.0)
        good("sterile_packaging_film", "Sterile packaging film", GoodCategory.INTERMEDIATE, 18.0)
        good("surgical_steel", "Surgical steel", GoodCategory.INTERMEDIATE, 260.0)
        good("implant_titanium", "Implant-grade titanium", GoodCategory.INTERMEDIATE, 780.0)
        good("bioceramic_powder", "Bioceramic powder", GoodCategory.INTERMEDIATE, 150.0)
        good("electrode_sets", "Electrode sets", GoodCategory.INTERMEDIATE, 12.0)
        good("superconducting_wire", "Superconducting wire", GoodCategory.INTERMEDIATE, 340.0)
        good("magnet_assembly", "Magnet assembly", GoodCategory.INTERMEDIATE, 35400.0, weight = 3000, volume = 4000)
        good("ultrasonic_transducer", "Ultrasonic transducer", GoodCategory.INTERMEDIATE, 134.0)
        good("x_ray_tube", "X-ray tube", GoodCategory.INTERMEDIATE, 1890.0, weight = 3000, volume = 4000)
        good("detector_array", "Detector array", GoodCategory.INTERMEDIATE, 1140.0, weight = 3000, volume = 4000)
        good("hospital_beds", "Hospital beds", GoodCategory.INDUSTRIAL, 3200.0, weight = 3000, volume = 4000)
        good("patient_monitor", "Patient monitor", GoodCategory.INDUSTRIAL, 6800.0, weight = 3000, volume = 4000)
        good("infusion_pump", "Infusion pump", GoodCategory.INDUSTRIAL, 2400.0, weight = 3000, volume = 4000)
        good("ventilator", "Ventilator", GoodCategory.INDUSTRIAL, 18000.0, weight = 3000, volume = 4000)
        good("ultrasound_scanner", "Ultrasound scanner", GoodCategory.INDUSTRIAL, 4300.0, weight = 5000, volume = 8000)
        good("xray_unit", "X-ray unit", GoodCategory.INDUSTRIAL, 17100.0, weight = 5000, volume = 8000)
        good("mri_scanner", "MRI scanner", GoodCategory.INDUSTRIAL, 206000.0, weight = 5000, volume = 8000)
        good("surgical_instrument_set", "Surgical instrument set", GoodCategory.INDUSTRIAL, 1800.0)
        good("orthopedic_implants", "Orthopedic implants", GoodCategory.INDUSTRIAL, 3200.0, weight = 3000, volume = 4000)
        good("cardiac_stents", "Cardiac stents", GoodCategory.INDUSTRIAL, 1100.0)
        good("pacemaker", "Pacemaker", GoodCategory.INDUSTRIAL, 6200.0, weight = 3000, volume = 4000)
        good("sterilizer_unit", "Sterilizer unit", GoodCategory.INDUSTRIAL, 14000.0, weight = 3000, volume = 4000)
        good("nitrile_gloves", "Nitrile gloves", GoodCategory.CONSUMER, 8.0)
        good("surgical_masks", "Surgical masks", GoodCategory.CONSUMER, 5.0)
        good("disposable_syringes", "Disposable syringes", GoodCategory.CONSUMER, 12.0)
        good("iv_sets", "Infusion sets", GoodCategory.CONSUMER, 4.0)
        good("wound_dressings", "Wound dressings", GoodCategory.CONSUMER, 6.0)
        good("catheters", "Catheters", GoodCategory.CONSUMER, 9.0)
        good("surgical_gowns", "Surgical gowns", GoodCategory.CONSUMER, 14.0)
        good("glucose_strips", "Glucose test strips", GoodCategory.CONSUMER, 22.0)
        good("glucose_monitor", "Glucose monitor", GoodCategory.CONSUMER, 85.0)
        good("blood_pressure_monitor", "Blood pressure monitor", GoodCategory.CONSUMER, 60.0)
        good("pulse_oximeter", "Pulse oximeter", GoodCategory.CONSUMER, 35.0)
        good("digital_thermometer", "Digital thermometer", GoodCategory.CONSUMER, 14.0)
        good("hearing_aids", "Hearing aids", GoodCategory.CONSUMER, 900.0)
        good("wheelchairs", "Wheelchairs", GoodCategory.CONSUMER, 420.0)

        role("clinical_engineer", "Clinical engineer", SkillBand.SPECIALIST, 155.0, 150)

        machine("cleanroom_molding_line", "Cleanroom molding line", 900000.0, throughput = 1.25, wearPerBatchPpm = 420, maintenance = 75.0, tech = "sterile_manufacturing")
        machine("swiss_lathe", "Swiss-type lathe", 850000.0, throughput = 1.2, wearPerBatchPpm = 450, maintenance = 70.0, laborSavings = 0.06, tech = "implant_manufacturing")
        machine("cryostat_line", "Cryostat line", 2200000.0, throughput = 1.1, maintenance = 180.0, tech = "medical_imaging")

        facility("medical_polymer_works", "Medical polymer works", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 4600000.0; buildTicks = 90; lines = 4; storage = 10000.0; upkeep = 1500.0
            energyPerLine = 150.0
            tech = "biocompatible_materials"
            staff("operator", 4)
            staff("lab_technician", 1)
        }
        facility("disposables_plant", "Medical disposables plant", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 5800000.0; buildTicks = 100; lines = 4; storage = 30000.0; upkeep = 1800.0
            energyPerLine = 180.0
            tech = "sterile_manufacturing"
            staff("operator", 5)
            staff("quality_inspector", 1)
        }
        facility("surgical_works", "Surgical and implant works", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 7400000.0; buildTicks = 110; lines = 3; storage = 10000.0; upkeep = 2200.0
            energyPerLine = 200.0
            tech = "implant_manufacturing"
            staff("machinist", 4)
            staff("clinical_engineer", 1)
        }
        facility("electromedical_works", "Electromedical works", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 12000000.0; buildTicks = 140; lines = 3; storage = 20000.0; upkeep = 3600.0
            energyPerLine = 280.0
            tech = "electromedical_design"
            staff("technician", 4)
            staff("clinical_engineer", 2)
            staff("assembler", 3)
        }
        facility("diagnostics_works", "Diagnostics works", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 9400000.0; buildTicks = 130; lines = 3; storage = 40000.0; upkeep = 2900.0
            energyPerLine = 220.0
            tech = "point_of_care_diagnostics"
            staff("technician", 3)
            staff("lab_technician", 3)
            staff("assembler", 2)
        }
        facility("imaging_systems_plant", "Imaging systems plant", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 38000000.0; buildTicks = 220; lines = 2; storage = 110000.0; upkeep = 8800.0
            energyPerLine = 520.0
            tech = "medical_imaging"
            staff("engineer", 10)
            staff("clinical_engineer", 6)
            staff("technician", 8)
        }
        facility("hospital_equipment_works", "Hospital equipment works", FacilityClass.FACTORY, IndustryFamily.HEALTHCARE) {
            cost = 6800000.0; buildTicks = 110; lines = 3; storage = 15000.0; upkeep = 2000.0
            energyPerLine = 190.0
            tech = "hospital_logistics"
            staff("machinist", 3)
            staff("operator", 4)
            staff("clinical_engineer", 1)
        }

        recipe("compound_medical_polymer", "Compound medical-grade polymer", "medical_consumables", "medical_polymer_works", 3) {
            input("polycarbonate", 140.0)
            input("silicone", 34.0)
            input("polypropylene", 105.0)
            output("medical_grade_polymer", 470.0)
            labor("operator", 4)
            labor("lab_technician", 1)
            energy = 413.0
        }
        recipe("compound_nitrile_latex", "Compound nitrile latex", "medical_consumables", "medical_polymer_works", 3) {
            input("synthetic_rubber", 400.0)
            input("zinc_oxide", 48.0)
            input("sulfur", 32.0)
            input("water", 965.0)
            output("nitrile_latex", 640.0)
            labor("operator", 4)
            energy = 723.0
        }
        recipe("extrude_packaging_film", "Extrude sterile packaging film", "medical_consumables", "medical_polymer_works", 2) {
            input("polyethylene", 280.0)
            input("aluminum_sheet", 38.0)
            output("sterile_packaging_film", 1670.0)
            labor("operator", 4)
            energy = 658.0
        }
        recipe("sinter_bioceramic", "Sinter bioceramic powder", "implants_and_surgical", "medical_polymer_works", 4) {
            input("alumina", 450.0)
            input("rare_earth_oxide", 6.0)
            input("industrial_gases", 90.0)
            output("bioceramic_powder", 350.0)
            labor("operator", 4)
            labor("lab_technician", 1)
            energy = 1146.0
        }
        recipe("dip_nitrile_gloves", "Dip nitrile gloves", "medical_consumables", "disposables_plant", 2) {
            input("nitrile_latex", 260.0)
            input("sterile_packaging_film", 52.0)
            input("water", 870.0)
            output("nitrile_gloves", 4110.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            machine("cleanroom_molding_line")
            energy = 609.0
        }
        recipe("fold_surgical_masks", "Fold surgical masks", "medical_consumables", "disposables_plant", 2) {
            input("nonwoven_fabric", 355.0)
            input("polypropylene", 105.0)
            input("sterile_packaging_film", 71.0)
            output("surgical_masks", 6600.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            energy = 732.0
        }
        recipe("mold_disposable_syringes", "Mold disposable syringes", "medical_consumables", "disposables_plant", 2) {
            input("medical_grade_polymer", 145.0)
            input("stainless_steel", 22.0)
            input("silicone", 7.0)
            input("sterile_packaging_film", 36.0)
            output("disposable_syringes", 2580.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            machine("cleanroom_molding_line")
            energy = 506.0
        }
        recipe("assemble_iv_sets", "Assemble infusion sets", "medical_consumables", "disposables_plant", 2) {
            input("medical_grade_polymer", 170.0)
            input("sterile_packaging_film", 74.0)
            input("plastic_granules", 74.0)
            output("iv_sets", 8200.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            energy = 614.0
        }
        recipe("make_wound_dressings", "Make wound dressings", "medical_consumables", "disposables_plant", 2) {
            input("cotton_fabric", 205.0)
            input("adhesives", 41.0)
            input("sterile_packaging_film", 62.0)
            output("wound_dressings", 5450.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            energy = 415.0
        }
        recipe("extrude_catheters", "Extrude catheters", "medical_consumables", "disposables_plant", 3) {
            input("medical_grade_polymer", 205.0)
            input("silicone", 45.0)
            input("sterile_packaging_film", 56.0)
            output("catheters", 5200.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            energy = 788.0
        }
        recipe("sew_surgical_gowns", "Sew surgical gowns", "medical_consumables", "disposables_plant", 2) {
            input("nonwoven_fabric", 470.0)
            input("sterile_packaging_film", 98.0)
            input("fasteners", 39.0)
            output("surgical_gowns", 2350.0)
            labor("operator", 5)
            labor("quality_inspector", 1)
            energy = 732.0
        }
        recipe("refine_surgical_steel", "Refine surgical steel", "implants_and_surgical", "surgical_works", 4) {
            input("stainless_steel", 290.0)
            input("nickel_ingot", 29.0)
            input("industrial_gases", 58.0)
            output("surgical_steel", 360.0)
            labor("machinist", 4)
            labor("clinical_engineer", 1)
            energy = 1552.0
        }
        recipe("process_implant_titanium", "Process implant-grade titanium", "implants_and_surgical", "surgical_works", 4) {
            input("titanium_mill_products", 105.0)
            input("industrial_gases", 105.0)
            output("implant_titanium", 120.0)
            labor("machinist", 4)
            labor("clinical_engineer", 1)
            machine("swiss_lathe")
            energy = 1935.0
        }
        recipe("grind_instrument_sets", "Grind surgical instrument sets", "implants_and_surgical", "surgical_works", 5) {
            input("surgical_steel", 235.0)
            input("sterile_packaging_film", 155.0)
            input("plastic_housings", 235.0)
            output("surgical_instrument_set", 61.0)
            labor("machinist", 4)
            labor("clinical_engineer", 1)
            energy = 2441.0
        }
        recipe("machine_orthopedic_implants", "Machine orthopedic implants", "implants_and_surgical", "surgical_works", 6) {
            input("implant_titanium", 72.0)
            input("bioceramic_powder", 96.0)
            input("sterile_packaging_film", 96.0)
            output("orthopedic_implants", 39.0)
            labor("machinist", 4)
            labor("clinical_engineer", 1)
            machine("swiss_lathe")
            energy = 2713.0
            tech = "implant_manufacturing"
        }
        recipe("form_cardiac_stents", "Form cardiac stents", "implants_and_surgical", "surgical_works", 4) {
            input("implant_titanium", 48.0)
            input("medical_grade_polymer", 97.0)
            input("sterile_packaging_film", 97.0)
            output("cardiac_stents", 75.0)
            labor("machinist", 4)
            labor("clinical_engineer", 1)
            energy = 1809.0
            tech = "implant_manufacturing"
        }
        recipe("print_electrode_sets", "Print electrode sets", "patient_monitoring", "electromedical_works", 2) {
            input("copper_wire", 120.0)
            input("adhesives", 185.0)
            input("sterile_packaging_film", 185.0)
            input("plastic_granules", 185.0)
            output("electrode_sets", 5600.0)
            labor("technician", 4)
            labor("assembler", 3)
            energy = 1530.0
        }
        recipe("assemble_patient_monitors", "Assemble patient monitors", "patient_monitoring", "electromedical_works", 4) {
            input("display_panel", 240.0)
            input("sensor_pack", 475.0)
            input("electrode_sets", 1430.0)
            input("controller_unit", 95.0)
            input("power_supply", 240.0)
            output("patient_monitor", 18.5)
            labor("technician", 4)
            labor("clinical_engineer", 2)
            labor("assembler", 3)
            energy = 2784.0
        }
        recipe("assemble_infusion_pumps", "Assemble infusion pumps", "patient_monitoring", "electromedical_works", 4) {
            input("electric_motor", 210.0)
            input("controller_unit", 125.0)
            input("sensor_pack", 210.0)
            input("plastic_housings", 845.0)
            input("power_supply", 210.0)
            output("infusion_pump", 53.0)
            labor("technician", 4)
            labor("clinical_engineer", 2)
            labor("assembler", 3)
            energy = 2784.0
        }
        recipe("assemble_ventilators", "Assemble ventilators", "patient_monitoring", "electromedical_works", 6) {
            input("electric_motor", 170.0)
            input("controller_unit", 85.0)
            input("sensor_pack", 255.0)
            input("display_panel", 42.0)
            input("medical_grade_polymer", 510.0)
            input("power_supply", 85.0)
            output("ventilator", 10.0)
            labor("technician", 4)
            labor("clinical_engineer", 2)
            labor("assembler", 3)
            energy = 3865.0
        }
        recipe("assemble_pacemakers", "Assemble pacemakers", "patient_monitoring", "electromedical_works", 6) {
            input("chip_logic", 290.0)
            input("small_battery", 875.0)
            input("implant_titanium", 73.0)
            input("electrode_sets", 585.0)
            input("sterile_packaging_film", 290.0)
            output("pacemaker", 27.0)
            labor("technician", 4)
            labor("clinical_engineer", 2)
            labor("assembler", 3)
            energy = 3596.0
        }
        recipe("print_glucose_strips", "Print glucose test strips", "patient_monitoring", "diagnostics_works", 2) {
            input("purified_protein", 16.5)
            input("copper_wire", 41.0)
            input("plastic_granules", 165.0)
            input("sterile_packaging_film", 165.0)
            output("glucose_strips", 2530.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 1201.0
        }
        recipe("assemble_glucose_monitors", "Assemble glucose monitors", "patient_monitoring", "diagnostics_works", 2) {
            input("mems_sensors", 1210.0)
            input("display_panel", 91.0)
            input("small_battery", 910.0)
            input("plastic_housings", 1820.0)
            input("packaging", 910.0)
            output("glucose_monitor", 690.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 1297.0
        }
        recipe("assemble_bp_monitors", "Assemble blood pressure monitors", "patient_monitoring", "diagnostics_works", 2) {
            input("mems_sensors", 990.0)
            input("display_panel", 99.0)
            input("small_battery", 990.0)
            input("plastic_housings", 1980.0)
            input("packaging", 990.0)
            output("blood_pressure_monitor", 980.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 1297.0
        }
        recipe("assemble_pulse_oximeters", "Assemble pulse oximeters", "patient_monitoring", "diagnostics_works", 2) {
            input("mems_sensors", 1040.0)
            input("led_chips", 6250.0)
            input("small_battery", 1040.0)
            input("plastic_housings", 2080.0)
            input("packaging", 1040.0)
            output("pulse_oximeter", 1680.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 1297.0
        }
        recipe("assemble_thermometers", "Assemble digital thermometers", "patient_monitoring", "diagnostics_works", 2) {
            input("mems_sensors", 495.0)
            input("chip_basic", 2970.0)
            input("small_battery", 495.0)
            input("plastic_housings", 1480.0)
            input("packaging", 990.0)
            output("digital_thermometer", 4200.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 1297.0
        }
        recipe("assemble_hearing_aids", "Assemble hearing aids", "patient_monitoring", "diagnostics_works", 4) {
            input("microphone_modules", 690.0)
            input("speaker_drivers", 690.0)
            input("chip_logic", 230.0)
            input("small_battery", 460.0)
            input("medical_grade_polymer", 345.0)
            input("packaging", 230.0)
            output("hearing_aids", 115.0)
            labor("technician", 3)
            labor("lab_technician", 3)
            labor("assembler", 2)
            energy = 2236.0
        }
        recipe("wind_superconducting_wire", "Draw superconducting wire", "diagnostic_imaging", "imaging_systems_plant", 5) {
            input("copper_wire", 1280.0)
            input("tin_ingot", 640.0)
            input("nickel_ingot", 190.0)
            input("industrial_gases", 960.0)
            output("superconducting_wire", 1600.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 6403.0
        }
        recipe("assemble_magnets", "Assemble magnet assemblies", "diagnostic_imaging", "imaging_systems_plant", 9) {
            input("rare_earth_oxide", 240.0)
            input("steel", 3000.0)
            input("superconducting_wire", 1200.0)
            output("magnet_assembly", 30.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            machine("cryostat_line")
            energy = 2998.0
        }
        recipe("bond_ultrasonic_transducers", "Bond ultrasonic transducers", "diagnostic_imaging", "imaging_systems_plant", 4) {
            input("ceramic_tiles", 3850.0)
            input("copper_wire", 640.0)
            input("silicone", 320.0)
            input("medical_grade_polymer", 640.0)
            output("ultrasonic_transducer", 3200.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 6421.0
        }
        recipe("build_x_ray_tubes", "Build X-ray tubes", "diagnostic_imaging", "imaging_systems_plant", 4) {
            input("stainless_steel", 965.0)
            input("tin_ingot", 240.0)
            input("glass", 720.0)
            input("industrial_gases", 965.0)
            output("x_ray_tube", 240.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 9626.0
        }
        recipe("assemble_detector_arrays", "Assemble detector arrays", "diagnostic_imaging", "imaging_systems_plant", 4) {
            input("chip_basic", 29350.0)
            input("mems_sensors", 3910.0)
            input("pcb", 1960.0)
            input("glass", 980.0)
            output("detector_array", 400.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 10051.0
        }
        recipe("assemble_ultrasound_scanners", "Assemble ultrasound scanners", "diagnostic_imaging", "imaging_systems_plant", 6) {
            input("ultrasonic_transducer", 1280.0)
            input("display_panel", 160.0)
            input("chip_logic", 3200.0)
            input("controller_unit", 160.0)
            input("power_supply", 160.0)
            input("plastic_housings", 640.0)
            output("ultrasound_scanner", 160.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 6401.0
        }
        recipe("assemble_xray_units", "Assemble X-ray units", "diagnostic_imaging", "imaging_systems_plant", 5) {
            input("x_ray_tube", 64.0)
            input("detector_array", 64.0)
            input("display_panel", 64.0)
            input("controller_unit", 64.0)
            input("steel_sheet", 1920.0)
            input("power_supply", 64.0)
            output("xray_unit", 32.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            energy = 3839.0
        }
        recipe("assemble_mri_scanners", "Assemble MRI scanners", "diagnostic_imaging", "imaging_systems_plant", 6) {
            input("magnet_assembly", 4.0)
            input("detector_array", 8.0)
            input("superconducting_wire", 800.0)
            input("display_panel", 12.0)
            input("controller_unit", 16.0)
            input("steel", 2400.0)
            output("mri_scanner", 4.0)
            labor("engineer", 10)
            labor("clinical_engineer", 6)
            labor("technician", 8)
            machine("cryostat_line")
            energy = 6009.0
            tech = "medical_imaging"
        }
        recipe("assemble_hospital_beds", "Assemble hospital beds", "hospital_equipment", "hospital_equipment_works", 3) {
            input("steel_sheet", 375.0)
            input("electric_motor", 25.0)
            input("controller_unit", 6.5)
            input("foam", 75.0)
            input("plastic_granules", 100.0)
            output("hospital_beds", 23.0)
            labor("machinist", 3)
            labor("operator", 4)
            labor("clinical_engineer", 1)
            energy = 1130.0
        }
        recipe("assemble_wheelchairs", "Assemble wheelchairs", "hospital_equipment", "hospital_equipment_works", 3) {
            input("aluminum_sheet", 215.0)
            input("tires", 53.0)
            input("fasteners", 105.0)
            input("upholstery_fabric", 80.0)
            output("wheelchairs", 165.0)
            labor("machinist", 3)
            labor("operator", 4)
            labor("clinical_engineer", 1)
            energy = 1500.0
        }
        recipe("assemble_sterilizers", "Assemble sterilizer units", "hospital_equipment", "hospital_equipment_works", 5) {
            input("stainless_steel", 285.0)
            input("controller_unit", 29.0)
            input("hydrogen_peroxide", 86.0)
            input("copper_pipe", 57.0)
            input("power_supply", 29.0)
            output("sterilizer_unit", 8.0)
            labor("machinist", 3)
            labor("operator", 4)
            labor("clinical_engineer", 1)
            energy = 1717.0
        }
        recipe("assemble_medical_equipment", "Assemble general medical equipment", "hospital_equipment", "hospital_equipment_works", 4) {
            input("hospital_beds", 7.5)
            input("patient_monitor", 7.5)
            input("infusion_pump", 7.5)
            input("sterilizer_unit", 1.5)
            output("medical_equipment", 40.0)
            labor("machinist", 3)
            labor("operator", 4)
            labor("clinical_engineer", 1)
            energy = 439.0
        }
        recipe("pack_equipment_kits", "Pack medical equipment kits", "hospital_equipment", "hospital_equipment_works", 3) {
            input("surgical_instrument_set", 14.0)
            input("iv_sets", 1850.0)
            input("infusion_pump", 9.5)
            input("wound_dressings", 2780.0)
            output("medical_equipment_kit", 115.0)
            labor("machinist", 3)
            labor("operator", 4)
            labor("clinical_engineer", 1)
            energy = 1850.0
        }

        tech("sterile_manufacturing", "Sterile manufacturing", TechCategory.PRODUCTION, 2, 1200000.0, 110) {
            requires("clean_room_packaging")
            unlocksFacility("disposables_plant")
            unlocksMachine("cleanroom_molding_line")
            effect(EffectKind.QUALITY_BONUS, 0.1, industry = "medical_consumables")
        }
        tech("biocompatible_materials", "Biocompatible materials", TechCategory.MATERIALS, 3, 2200000.0, 150) {
            requires("polymer_engineering")
            unlocksFacility("medical_polymer_works")
        }
        tech("implant_manufacturing", "Implant manufacturing", TechCategory.MATERIALS, 3, 3000000.0, 180) {
            requires("biocompatible_materials", "precision_rolling")
            unlocksRecipe("machine_orthopedic_implants", "form_cardiac_stents")
            unlocksFacility("surgical_works")
            unlocksMachine("swiss_lathe")
        }
        tech("electromedical_design", "Electromedical design", TechCategory.DIGITAL, 3, 2600000.0, 170) {
            requires("lean_manufacturing", "component_miniaturization")
            unlocksFacility("electromedical_works")
        }
        tech("point_of_care_diagnostics", "Point-of-care diagnostics", TechCategory.DIGITAL, 3, 2200000.0, 150) {
            requires("telehealth", "component_miniaturization")
            unlocksFacility("diagnostics_works")
            effect(EffectKind.YIELD_BONUS, 0.05, industry = "patient_monitoring")
        }
        tech("medical_imaging", "Medical imaging", TechCategory.DIGITAL, 4, 9000000.0, 280) {
            requires("display_technology", "electromedical_design")
            unlocksRecipe("assemble_mri_scanners")
            unlocksFacility("imaging_systems_plant")
            unlocksMachine("cryostat_line")
        }
        tech("hospital_logistics", "Hospital logistics", TechCategory.MANAGEMENT, 2, 900000.0, 100) {
            requires("hospital_administration")
            unlocksFacility("hospital_equipment_works")
            effect(EffectKind.TRANSPORT_SAVINGS, 0.04)
        }
        tech("remote_monitoring", "Remote patient monitoring", TechCategory.DIGITAL, 3, 2000000.0, 140) {
            requires("telehealth", "iot_platforms")
            effect(EffectKind.THROUGHPUT_BONUS, 0.06, industry = "patient_monitoring")
        }
        tech("device_regulatory_compliance", "Device regulatory compliance", TechCategory.MANAGEMENT, 2, 700000.0, 90) {
            requires("hospital_administration")
            effect(EffectKind.QUALITY_BONUS, 0.12, industry = "implants_and_surgical")
            effect(EffectKind.QUALITY_BONUS, 0.08, industry = "diagnostic_imaging")
        }

        demand("nitrile_gloves", 60L, 90L, 100L, 0.4, incomeElasticity = 0.3)
        demand("surgical_masks", 90L, 120L, 130L, 0.4, incomeElasticity = 0.3, substitutes = listOf("face_masks"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.4))
        demand("disposable_syringes", 40L, 60L, 70L, 0.3, incomeElasticity = 0.2)
        demand("iv_sets", 60L, 80L, 90L, 0.3, incomeElasticity = 0.2)
        demand("wound_dressings", 80L, 100L, 110L, 0.4, incomeElasticity = 0.2, substitutes = listOf("bandages"))
        demand("catheters", 20L, 30L, 34L, 0.3, incomeElasticity = 0.2)
        demand("surgical_gowns", 14L, 20L, 22L, 0.4, incomeElasticity = 0.3)
        demand("glucose_strips", 40L, 60L, 64L, 0.2, incomeElasticity = 0.1, nonConsumers = 0.8)
        demand("glucose_monitor", 1L, 3L, 5L, 0.5, incomeElasticity = 0.4, nonConsumers = 0.9)
        demand("blood_pressure_monitor", 2L, 5L, 8L, 0.7, incomeElasticity = 0.6, nonConsumers = 0.8)
        demand("pulse_oximeter", 3L, 7L, 10L, 0.8, incomeElasticity = 0.7, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.3), nonConsumers = 0.8)
        demand("digital_thermometer", 12L, 18L, 20L, 0.6, incomeElasticity = 0.4, seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.4), nonConsumers = 0.5)
        demand("hearing_aids", 0L, 1L, 2L, 0.5, incomeElasticity = 0.7, nonConsumers = 0.97)
        demand("wheelchairs", 1L, 2L, 3L, 0.5, incomeElasticity = 0.4, nonConsumers = 0.96)
        demand("hospital_beds", 0L, 1L, 1L, 0.5, incomeElasticity = 0.4, nonConsumers = 0.98)
        demand("ventilator", 0L, 1L, 1L, 0.4, incomeElasticity = 0.3, nonConsumers = 0.99)
        demand("ultrasound_scanner", 0L, 1L, 1L, 0.5, incomeElasticity = 0.5, nonConsumers = 0.99)
        demand("xray_unit", 0L, 0L, 1L, 0.5, incomeElasticity = 0.5, nonConsumers = 0.999)
        demand("mri_scanner", 0L, 0L, 1L, 0.5, incomeElasticity = 0.5, nonConsumers = 0.9995)
        demand("orthopedic_implants", 1L, 2L, 3L, 0.4, incomeElasticity = 0.4, nonConsumers = 0.9)
        demand("cardiac_stents", 1L, 2L, 3L, 0.3, incomeElasticity = 0.3, nonConsumers = 0.9)
        demand("pacemaker", 0L, 1L, 1L, 0.3, incomeElasticity = 0.3, nonConsumers = 0.97)
        demand("surgical_instrument_set", 0L, 1L, 1L, 0.4, incomeElasticity = 0.3, nonConsumers = 0.97)
    }
}

"""Medical devices: polymers, disposables, implants and instruments, electromedical devices, diagnostics, imaging, hospital equipment."""
from expgen import ExpSpec


def build():
    s = ExpSpec("medical_devices", "MedicalDevicesPack")
    s.role("clinical_engineer", "Clinical engineer", "SPECIALIST", 155.0, 150)

    s.industry("medical_consumables", "Medical consumables", "HEALTHCARE", "Gloves, masks, syringes, infusion sets, dressings and catheters")
    s.industry("implants_and_surgical", "Implants and surgical instruments", "HEALTHCARE", "Surgical steel, instrument sets, orthopedic implants and stents")
    s.industry("patient_monitoring", "Patient monitoring and diagnostics", "HEALTHCARE", "Pacemakers, monitors, pumps, ventilators and home diagnostics")
    s.industry("diagnostic_imaging", "Diagnostic imaging", "HEALTHCARE", "Ultrasound, X-ray and magnetic resonance systems")
    s.industry("hospital_equipment", "Hospital equipment", "HEALTHCARE", "Beds, wheelchairs, sterilizers and equipment kits")

    s.add_goods("INTERMEDIATE", [
        ("medical_grade_polymer", "Medical-grade polymer", 90), ("nitrile_latex", "Nitrile latex", 70), ("sterile_packaging_film", "Sterile packaging film", 18),
        ("surgical_steel", "Surgical steel", 260), ("implant_titanium", "Implant-grade titanium", 780), ("bioceramic_powder", "Bioceramic powder", 150),
        ("electrode_sets", "Electrode sets", 12), ("superconducting_wire", "Superconducting wire", 320), ("magnet_assembly", "Magnet assembly", 2_200),
        ("ultrasonic_transducer", "Ultrasonic transducer", 140), ("x_ray_tube", "X-ray tube", 3_800), ("detector_array", "Detector array", 5_200),
    ])
    s.add_goods("INDUSTRIAL", [
        ("hospital_beds", "Hospital beds", 3_200), ("patient_monitor", "Patient monitor", 6_800), ("infusion_pump", "Infusion pump", 2_400), ("ventilator", "Ventilator", 18_000),
        ("ultrasound_scanner", "Ultrasound scanner", 42_000), ("xray_unit", "X-ray unit", 120_000), ("mri_scanner", "MRI scanner", 1_500_000),
        ("surgical_instrument_set", "Surgical instrument set", 1_800), ("orthopedic_implants", "Orthopedic implants", 3_200), ("cardiac_stents", "Cardiac stents", 1_100),
        ("pacemaker", "Pacemaker", 6_200), ("sterilizer_unit", "Sterilizer unit", 14_000),
    ])
    s.add_goods("CONSUMER", [
        ("nitrile_gloves", "Nitrile gloves", 8), ("surgical_masks", "Surgical masks", 5), ("disposable_syringes", "Disposable syringes", 12), ("iv_sets", "Infusion sets", 4),
        ("wound_dressings", "Wound dressings", 6), ("catheters", "Catheters", 9), ("surgical_gowns", "Surgical gowns", 14), ("glucose_strips", "Glucose test strips", 22),
        ("glucose_monitor", "Glucose monitor", 85), ("blood_pressure_monitor", "Blood pressure monitor", 60), ("pulse_oximeter", "Pulse oximeter", 35),
        ("digital_thermometer", "Digital thermometer", 14), ("hearing_aids", "Hearing aids", 900), ("wheelchairs", "Wheelchairs", 420),
    ])

    s.fac("medical_polymer_works", "Medical polymer works", "FACTORY", "HEALTHCARE", 4_600_000, 90, 4, 1_500, 150.0, {"operator": 4, "lab_technician": 1}, 520)
    s.fac("disposables_plant", "Medical disposables plant", "FACTORY", "HEALTHCARE", 5_800_000, 100, 4, 1_800, 180.0, {"operator": 5, "quality_inspector": 1}, 560)
    s.fac("surgical_works", "Surgical and implant works", "FACTORY", "HEALTHCARE", 7_400_000, 110, 3, 2_200, 200.0, {"machinist": 4, "clinical_engineer": 1}, 680)
    s.fac("electromedical_works", "Electromedical works", "FACTORY", "HEALTHCARE", 12_000_000, 140, 3, 3_600, 280.0, {"technician": 4, "clinical_engineer": 2, "assembler": 3}, 800)
    s.fac("diagnostics_works", "Diagnostics works", "FACTORY", "HEALTHCARE", 9_400_000, 130, 3, 2_900, 220.0, {"technician": 3, "lab_technician": 3, "assembler": 2}, 650)
    s.fac("imaging_systems_plant", "Imaging systems plant", "FACTORY", "HEALTHCARE", 38_000_000, 220, 2, 8_800, 520.0, {"engineer": 10, "clinical_engineer": 6, "technician": 8}, 1100)
    s.fac("hospital_equipment_works", "Hospital equipment works", "FACTORY", "HEALTHCARE", 6_800_000, 110, 3, 2_000, 190.0, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, 600)

    s.machine("cleanroom_molding_line", "Cleanroom molding line", 900_000, thr=1.25, wear=420, maint=75.0)
    s.machine("swiss_lathe", "Swiss-type lathe", 850_000, thr=1.2, wear=450, maint=70.0, labor_sav=0.06)
    s.machine("cryostat_line", "Cryostat line", 2_200_000, thr=1.1, wear=500, maint=180.0)

    R = s.rec
    R("compound_medical_polymer", "Compound medical-grade polymer", "medical_consumables", "medical_polymer_works", 3, {"polycarbonate": 40, "silicone": 10, "polypropylene": 30}, {"medical_grade_polymer": 1}, {"operator": 4, "lab_technician": 1}, energy=120, margin=0.24)
    R("compound_nitrile_latex", "Compound nitrile latex", "medical_consumables", "medical_polymer_works", 3, {"synthetic_rubber": 50, "zinc_oxide": 6, "sulfur": 4, "water": 120}, {"nitrile_latex": 1}, {"operator": 4}, energy=90, margin=0.22)
    R("extrude_packaging_film", "Extrude sterile packaging film", "medical_consumables", "medical_polymer_works", 2, {"polyethylene": 30, "aluminum_sheet": 4}, {"sterile_packaging_film": 1}, {"operator": 4}, energy=70, margin=0.22)
    R("sinter_bioceramic", "Sinter bioceramic powder", "implants_and_surgical", "medical_polymer_works", 4, {"alumina": 60, "rare_earth_oxide": 0.8, "industrial_gases": 12}, {"bioceramic_powder": 1}, {"operator": 4, "lab_technician": 1}, energy=180, margin=0.26)
    R("dip_nitrile_gloves", "Dip nitrile gloves", "medical_consumables", "disposables_plant", 2, {"nitrile_latex": 30, "sterile_packaging_film": 6, "water": 100}, {"nitrile_gloves": 400}, {"operator": 5, "quality_inspector": 1}, energy=70, margin=0.24, machines=["cleanroom_molding_line"])
    R("fold_surgical_masks", "Fold surgical masks", "medical_consumables", "disposables_plant", 2, {"nonwoven_fabric": 20, "polypropylene": 6, "sterile_packaging_film": 4}, {"surgical_masks": 600}, {"operator": 5, "quality_inspector": 1}, energy=50, margin=0.24)
    R("mold_disposable_syringes", "Mold disposable syringes", "medical_consumables", "disposables_plant", 2, {"medical_grade_polymer": 20, "stainless_steel": 3, "silicone": 1, "sterile_packaging_film": 5}, {"disposable_syringes": 200}, {"operator": 5, "quality_inspector": 1}, energy=70, margin=0.26, machines=["cleanroom_molding_line"])
    R("assemble_iv_sets", "Assemble infusion sets", "medical_consumables", "disposables_plant", 2, {"medical_grade_polymer": 14, "sterile_packaging_film": 6, "plastic_granules": 6}, {"iv_sets": 600}, {"operator": 5, "quality_inspector": 1}, energy=50, margin=0.24)
    R("make_wound_dressings", "Make wound dressings", "medical_consumables", "disposables_plant", 2, {"cotton_fabric": 20, "adhesives": 4, "sterile_packaging_film": 6}, {"wound_dressings": 500}, {"operator": 5, "quality_inspector": 1}, energy=40, margin=0.24)
    R("extrude_catheters", "Extrude catheters", "medical_consumables", "disposables_plant", 3, {"medical_grade_polymer": 18, "silicone": 4, "sterile_packaging_film": 5}, {"catheters": 300}, {"operator": 5, "quality_inspector": 1}, energy=70, margin=0.26)
    R("sew_surgical_gowns", "Sew surgical gowns", "medical_consumables", "disposables_plant", 2, {"nonwoven_fabric": 24, "sterile_packaging_film": 5, "fasteners": 2}, {"surgical_gowns": 160}, {"operator": 5, "quality_inspector": 1}, energy=40, margin=0.24)
    R("refine_surgical_steel", "Refine surgical steel", "implants_and_surgical", "surgical_works", 4, {"stainless_steel": 30, "nickel_ingot": 3, "industrial_gases": 6}, {"surgical_steel": 1}, {"machinist": 4, "clinical_engineer": 1}, energy=160, margin=0.24)
    R("process_implant_titanium", "Process implant-grade titanium", "implants_and_surgical", "surgical_works", 4, {"titanium_mill_products": 10, "industrial_gases": 10}, {"implant_titanium": 1}, {"machinist": 4, "clinical_engineer": 1}, energy=180, margin=0.24, machines=["swiss_lathe"])
    R("grind_instrument_sets", "Grind surgical instrument sets", "implants_and_surgical", "surgical_works", 5, {"surgical_steel": 6, "sterile_packaging_film": 4, "plastic_housings": 6}, {"surgical_instrument_set": 1}, {"machinist": 4, "clinical_engineer": 1}, energy=140, margin=0.26)
    R("machine_orthopedic_implants", "Machine orthopedic implants", "implants_and_surgical", "surgical_works", 6, {"implant_titanium": 3, "bioceramic_powder": 4, "sterile_packaging_film": 4}, {"orthopedic_implants": 1}, {"machinist": 4, "clinical_engineer": 1}, energy=160, margin=0.28, machines=["swiss_lathe"])
    R("form_cardiac_stents", "Form cardiac stents", "implants_and_surgical", "surgical_works", 4, {"implant_titanium": 1, "medical_grade_polymer": 2, "sterile_packaging_film": 2}, {"cardiac_stents": 1}, {"machinist": 4, "clinical_engineer": 1}, energy=120, margin=0.28)
    R("print_electrode_sets", "Print electrode sets", "patient_monitoring", "electromedical_works", 2, {"copper_wire": 2, "adhesives": 3, "sterile_packaging_film": 3, "plastic_granules": 3}, {"electrode_sets": 1}, {"technician": 4, "assembler": 3}, energy=50, margin=0.24)
    R("assemble_patient_monitors", "Assemble patient monitors", "patient_monitoring", "electromedical_works", 4, {"display_panel": 1, "sensor_pack": 2, "electrode_sets": 6, "controller_unit": 0.4, "power_supply": 1}, {"patient_monitor": 1}, {"technician": 4, "clinical_engineer": 2, "assembler": 3}, energy=120, margin=0.26)
    R("assemble_infusion_pumps", "Assemble infusion pumps", "patient_monitoring", "electromedical_works", 4, {"electric_motor": 1, "controller_unit": 0.6, "sensor_pack": 1, "plastic_housings": 4, "power_supply": 1}, {"infusion_pump": 1}, {"technician": 4, "clinical_engineer": 2, "assembler": 3}, energy=100, margin=0.26)
    R("assemble_ventilators", "Assemble ventilators", "patient_monitoring", "electromedical_works", 6, {"electric_motor": 2, "controller_unit": 1, "sensor_pack": 3, "display_panel": 0.5, "medical_grade_polymer": 6, "power_supply": 1}, {"ventilator": 1}, {"technician": 4, "clinical_engineer": 2, "assembler": 3}, energy=180, margin=0.28)
    R("assemble_pacemakers", "Assemble pacemakers", "patient_monitoring", "electromedical_works", 6, {"chip_logic": 4, "small_battery": 12, "implant_titanium": 1, "electrode_sets": 8, "sterile_packaging_film": 4}, {"pacemaker": 1}, {"technician": 4, "clinical_engineer": 2, "assembler": 3}, energy=140, margin=0.3)
    R("print_glucose_strips", "Print glucose test strips", "patient_monitoring", "diagnostics_works", 2, {"purified_protein": 0.4, "copper_wire": 1, "plastic_granules": 4, "sterile_packaging_film": 4}, {"glucose_strips": 400}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=50, margin=0.28)
    R("assemble_glucose_monitors", "Assemble glucose monitors", "patient_monitoring", "diagnostics_works", 2, {"mems_sensors": 4, "display_panel": 0.3, "small_battery": 3, "plastic_housings": 6, "packaging": 3}, {"glucose_monitor": 1}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=40, margin=0.26)
    R("assemble_bp_monitors", "Assemble blood pressure monitors", "patient_monitoring", "diagnostics_works", 2, {"mems_sensors": 3, "display_panel": 0.3, "small_battery": 3, "plastic_housings": 6, "packaging": 3}, {"blood_pressure_monitor": 1}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=40, margin=0.26)
    R("assemble_pulse_oximeters", "Assemble pulse oximeters", "patient_monitoring", "diagnostics_works", 2, {"mems_sensors": 2, "led_chips": 12, "small_battery": 2, "plastic_housings": 4, "packaging": 2}, {"pulse_oximeter": 1}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=30, margin=0.26)
    R("assemble_thermometers", "Assemble digital thermometers", "patient_monitoring", "diagnostics_works", 2, {"mems_sensors": 1, "chip_basic": 6, "small_battery": 1, "plastic_housings": 3, "packaging": 2}, {"digital_thermometer": 1}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=30, margin=0.26)
    R("assemble_hearing_aids", "Assemble hearing aids", "patient_monitoring", "diagnostics_works", 4, {"microphone_modules": 6, "speaker_drivers": 6, "chip_logic": 2, "small_battery": 4, "medical_grade_polymer": 3, "packaging": 2}, {"hearing_aids": 1}, {"technician": 3, "lab_technician": 3, "assembler": 2}, energy=60, margin=0.3)
    # Discrete goods: inputs and energy are per unit, qty is the batch size, and the reference price is fitted from the parts.
    I = {"engineer": 10, "clinical_engineer": 6, "technician": 8}
    R("wind_superconducting_wire", "Draw superconducting wire", "diagnostic_imaging", "imaging_systems_plant", 0, {"copper_wire": 0.8, "tin_ingot": 0.4, "nickel_ingot": 0.12, "industrial_gases": 0.6}, {"superconducting_wire": 1}, I, energy=4, margin=0.26, qty=400, fit=True)
    R("assemble_magnets", "Assemble magnet assemblies", "diagnostic_imaging", "imaging_systems_plant", 0, {"rare_earth_oxide": 8, "steel": 100, "superconducting_wire": 40}, {"magnet_assembly": 1}, I, energy=100, margin=0.26, qty=30, machines=["cryostat_line"], fit=True)
    R("bond_ultrasonic_transducers", "Bond ultrasonic transducers", "diagnostic_imaging", "imaging_systems_plant", 0, {"ceramic_tiles": 1.2, "copper_wire": 0.2, "silicone": 0.1, "medical_grade_polymer": 0.2}, {"ultrasonic_transducer": 1}, I, energy=2, margin=0.26, qty=400, fit=True)
    R("build_x_ray_tubes", "Build X-ray tubes", "diagnostic_imaging", "imaging_systems_plant", 0, {"stainless_steel": 4, "tin_ingot": 1, "glass": 3, "industrial_gases": 4}, {"x_ray_tube": 1}, I, energy=40, margin=0.26, qty=60, fit=True)
    R("assemble_detector_arrays", "Assemble detector arrays", "diagnostic_imaging", "imaging_systems_plant", 0, {"chip_basic": 60, "mems_sensors": 8, "pcb": 4, "glass": 2}, {"detector_array": 1}, I, energy=40, margin=0.26, qty=50, fit=True)
    R("assemble_ultrasound_scanners", "Assemble ultrasound scanners", "diagnostic_imaging", "imaging_systems_plant", 0, {"ultrasonic_transducer": 8, "display_panel": 1, "chip_logic": 20, "controller_unit": 1, "power_supply": 1, "plastic_housings": 4}, {"ultrasound_scanner": 1}, I, energy=40, margin=0.26, qty=20, fit=True)
    R("assemble_xray_units", "Assemble X-ray units", "diagnostic_imaging", "imaging_systems_plant", 0, {"x_ray_tube": 2, "detector_array": 2, "display_panel": 2, "controller_unit": 2, "steel_sheet": 60, "power_supply": 2}, {"xray_unit": 1}, I, energy=120, margin=0.24, qty=8, fit=True)
    R("assemble_mri_scanners", "Assemble MRI scanners", "diagnostic_imaging", "imaging_systems_plant", 0, {"magnet_assembly": 1, "detector_array": 2, "superconducting_wire": 200, "display_panel": 3, "controller_unit": 4, "steel": 600}, {"mri_scanner": 1}, I, energy=1_500, margin=0.22, qty=1, machines=["cryostat_line"], fit=True)
    R("assemble_hospital_beds", "Assemble hospital beds", "hospital_equipment", "hospital_equipment_works", 3, {"steel_sheet": 30, "electric_motor": 2, "controller_unit": 0.5, "foam": 6, "plastic_granules": 8}, {"hospital_beds": 1}, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, energy=90, margin=0.24)
    R("assemble_wheelchairs", "Assemble wheelchairs", "hospital_equipment", "hospital_equipment_works", 3, {"aluminum_sheet": 8, "tires": 2, "fasteners": 4, "upholstery_fabric": 3}, {"wheelchairs": 1}, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, energy=60, margin=0.26)
    R("assemble_sterilizers", "Assemble sterilizer units", "hospital_equipment", "hospital_equipment_works", 5, {"stainless_steel": 20, "controller_unit": 2, "hydrogen_peroxide": 6, "copper_pipe": 4, "power_supply": 2}, {"sterilizer_unit": 1}, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, energy=120, margin=0.26)
    R("assemble_medical_equipment", "Assemble general medical equipment", "hospital_equipment", "hospital_equipment_works", 4, {"hospital_beds": 1, "patient_monitor": 1, "infusion_pump": 1, "sterilizer_unit": 0.2}, {"medical_equipment": 1}, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, energy=60, margin=0.16)
    R("pack_equipment_kits", "Pack medical equipment kits", "hospital_equipment", "hospital_equipment_works", 3, {"surgical_instrument_set": 0.3, "iv_sets": 40, "infusion_pump": 0.2, "wound_dressings": 60}, {"medical_equipment_kit": 1}, {"machinist": 3, "operator": 4, "clinical_engineer": 1}, energy=40, margin=0.16)

    s.tech("sterile_manufacturing", "Sterile manufacturing", "PRODUCTION", 2, 1_200_000, 110, requires=["clean_room_packaging"],
           facilities=["disposables_plant"], machines=["cleanroom_molding_line"], effects=[("QUALITY_BONUS", 0.10, "medical_consumables", None)])
    s.tech("biocompatible_materials", "Biocompatible materials", "MATERIALS", 3, 2_200_000, 150, requires=["polymer_engineering"],
           facilities=["medical_polymer_works"])
    s.tech("implant_manufacturing", "Implant manufacturing", "MATERIALS", 3, 3_000_000, 180, requires=["biocompatible_materials", "precision_rolling"],
           facilities=["surgical_works"], machines=["swiss_lathe"], recipes=["machine_orthopedic_implants", "form_cardiac_stents"])
    s.tech("electromedical_design", "Electromedical design", "DIGITAL", 3, 2_600_000, 170, requires=["lean_manufacturing", "component_miniaturization"],
           facilities=["electromedical_works"])
    s.tech("point_of_care_diagnostics", "Point-of-care diagnostics", "DIGITAL", 3, 2_200_000, 150, requires=["telehealth", "component_miniaturization"],
           facilities=["diagnostics_works"], effects=[("YIELD_BONUS", 0.05, "patient_monitoring", None)])
    s.tech("medical_imaging", "Medical imaging", "DIGITAL", 4, 9_000_000, 280, requires=["display_technology", "electromedical_design"],
           facilities=["imaging_systems_plant"], machines=["cryostat_line"], recipes=["assemble_mri_scanners"])
    s.tech("hospital_logistics", "Hospital logistics", "MANAGEMENT", 2, 900_000, 100, requires=["hospital_administration"],
           facilities=["hospital_equipment_works"], effects=[("TRANSPORT_SAVINGS", 0.04, None, None)])
    s.tech("remote_monitoring", "Remote patient monitoring", "DIGITAL", 3, 2_000_000, 140, requires=["telehealth", "iot_platforms"],
           effects=[("THROUGHPUT_BONUS", 0.06, "patient_monitoring", None)])
    s.tech("device_regulatory_compliance", "Device regulatory compliance", "MANAGEMENT", 2, 700_000, 90, requires=["hospital_administration"],
           effects=[("QUALITY_BONUS", 0.12, "implants_and_surgical", None), ("QUALITY_BONUS", 0.08, "diagnostic_imaging", None)])

    D = s.dem
    D("nitrile_gloves", 60, 90, 100, 0.4, 0.3)
    D("surgical_masks", 90, 120, 130, 0.4, 0.3, ["face_masks"], {"WINTER": 1.4})
    D("disposable_syringes", 40, 60, 70, 0.3, 0.2)
    D("iv_sets", 60, 80, 90, 0.3, 0.2)
    D("wound_dressings", 80, 100, 110, 0.4, 0.2, ["bandages"])
    D("catheters", 20, 30, 34, 0.3, 0.2)
    D("surgical_gowns", 14, 20, 22, 0.4, 0.3)
    D("glucose_strips", 40, 60, 64, 0.2, 0.1, [], {}, 0.8)
    D("glucose_monitor", 1, 3, 5, 0.5, 0.4, [], {}, 0.9)
    D("blood_pressure_monitor", 2, 5, 8, 0.7, 0.6, [], {}, 0.8)
    D("pulse_oximeter", 3, 7, 10, 0.8, 0.7, [], {"WINTER": 1.3}, 0.8)
    D("digital_thermometer", 12, 18, 20, 0.6, 0.4, [], {"WINTER": 1.4}, 0.5)
    D("hearing_aids", 0, 1, 2, 0.5, 0.7, [], {}, 0.97)
    D("wheelchairs", 1, 2, 3, 0.5, 0.4, [], {}, 0.96)
    D("hospital_beds", 0, 1, 1, 0.5, 0.4, [], {}, 0.98)
    D("ventilator", 0, 1, 1, 0.4, 0.3, [], {}, 0.99)
    D("ultrasound_scanner", 0, 1, 1, 0.5, 0.5, [], {}, 0.99)
    D("xray_unit", 0, 0, 1, 0.5, 0.5, [], {}, 0.999)
    D("mri_scanner", 0, 0, 1, 0.5, 0.5, [], {}, 0.9995)
    D("orthopedic_implants", 1, 2, 3, 0.4, 0.4, [], {}, 0.9)
    D("cardiac_stents", 1, 2, 3, 0.3, 0.3, [], {}, 0.9)
    D("pacemaker", 0, 1, 1, 0.3, 0.3, [], {}, 0.97)
    D("surgical_instrument_set", 0, 1, 1, 0.4, 0.3, [], {}, 0.97)
    return s

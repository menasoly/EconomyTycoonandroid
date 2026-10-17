"""Pharmaceuticals: botanicals, fermentation and biologics, sterile fill-finish, formulations and packaging."""
from expgen import ExpSpec


def build():
    s = ExpSpec("pharmaceuticals", "PharmaceuticalsPack")
    s.role("biotechnologist", "Biotechnologist", "EXPERT", 230.0, 300)
    s.role("lab_technician", "Laboratory technician", "SKILLED", 110.0, 60)

    s.industry("biologics", "Biologics", "PHARMACEUTICALS", "Fermentation, protein purification and injectable biologics")
    s.industry("specialty_pharma", "Specialty pharmaceuticals", "PHARMACEUTICALS", "Botanicals, hormones and branded formulations")
    s.industry("pharma_packaging", "Pharmaceutical packaging", "PHARMACEUTICALS", "Sterile containers, blister packs and cold-chain shippers")

    s.add_goods("RAW", [("medicinal_herbs", "Medicinal herbs", 38, 4000)])
    s.add_goods("INTERMEDIATE", [
        ("herbal_extract", "Herbal extract", 120), ("culture_media", "Culture media", 85), ("fermentation_broth", "Fermentation broth", 150),
        ("purified_protein", "Purified protein", 900), ("antibody_bulk", "Antibody bulk", 4200), ("insulin_bulk", "Insulin bulk", 2600),
        ("peptide_api", "Peptide active ingredient", 1800), ("steroid_api", "Steroid active ingredient", 1500),
        ("sterile_diluent", "Sterile diluent", 25), ("adjuvant", "Vaccine adjuvant", 700), ("gelatin", "Pharmaceutical gelatin", 40),
        ("capsule_shells", "Capsule shells", 14), ("sterile_vials", "Sterile vials", 9), ("prefilled_syringes", "Prefilled syringes", 7),
        ("blister_packs", "Blister packs", 3), ("cold_chain_boxes", "Cold-chain shippers", 18),
    ])
    s.add_goods("CONSUMER", [
        ("insulin_pens", "Insulin pens", 55), ("mab_therapy", "Antibody therapy dose", 900), ("flu_vaccine", "Seasonal flu vaccine", 22),
        ("allergy_tablets", "Allergy tablets", 9), ("herbal_supplements", "Herbal supplements", 15), ("cough_syrup", "Cough syrup", 8),
        ("topical_ointment", "Topical ointment", 7), ("hormone_therapy", "Hormone therapy", 60), ("eye_drops", "Eye drops", 11),
        ("antiviral_course", "Antiviral course", 85),
    ])

    s.fac("herb_farm", "Medicinal herb farm", "FARM", "PHARMACEUTICALS", 450_000, 50, 3, 180, 3.0, {"farmhand": 2}, 420, storage=40_000)
    s.fac("botanical_extraction_lab", "Botanical extraction lab", "LABORATORY", "PHARMACEUTICALS", 3_200_000, 90, 3, 1_100, 120.0, {"lab_technician": 2, "chemist": 1}, 600)
    s.fac("fermentation_plant", "Fermentation plant", "FACTORY", "PHARMACEUTICALS", 12_000_000, 150, 3, 3_800, 300.0, {"biotechnologist": 2, "operator": 4}, 800)
    s.fac("biologics_plant", "Biologics plant", "LABORATORY", "PHARMACEUTICALS", 26_000_000, 200, 2, 7_500, 420.0, {"biotechnologist": 4, "lab_technician": 6}, 1000)
    s.fac("fill_finish_plant", "Sterile fill-finish plant", "LABORATORY", "PHARMACEUTICALS", 16_000_000, 170, 3, 4_600, 260.0, {"lab_technician": 4, "biotechnologist": 1}, 800)
    s.fac("formulation_plant", "Formulation plant", "FACTORY", "PHARMACEUTICALS", 8_500_000, 120, 4, 2_400, 180.0, {"operator": 4, "lab_technician": 2}, 650)
    s.fac("pharma_packaging_plant", "Pharmaceutical packaging plant", "FACTORY", "PHARMACEUTICALS", 5_200_000, 100, 4, 1_500, 150.0, {"operator": 4, "machinist": 1}, 520)

    s.machine("bioreactor", "Stainless bioreactor", 1_200_000, thr=1.25, wear=600, maint=120.0)
    s.machine("lyophilizer", "Freeze dryer", 900_000, thr=1.0, wear=500, maint=90.0)
    s.machine("filling_isolator", "Aseptic filling isolator", 700_000, thr=1.15, wear=450, maint=70.0, labor_sav=0.1)
    s.machine("blister_line", "Blister packaging line", 350_000, thr=1.3, wear=400, maint=35.0)

    R = s.rec
    R("grow_medicinal_herbs", "Grow medicinal herbs", "specialty_pharma", "herb_farm", 12, {"seeds": 6, "fertilizer": 40, "water": 300}, {"medicinal_herbs": 1}, {"farmhand": 6}, energy=20, margin=0.18)
    R("extract_herbs", "Extract herbal actives", "specialty_pharma", "botanical_extraction_lab", 4, {"medicinal_herbs": 150, "ethanol": 40, "water": 120}, {"herbal_extract": 1}, {"lab_technician": 3, "chemist": 1}, energy=90, margin=0.3)
    R("prepare_adjuvant", "Prepare vaccine adjuvant", "specialty_pharma", "botanical_extraction_lab", 3, {"herbal_extract": 20, "excipients": 14, "glycerin": 6}, {"adjuvant": 1}, {"lab_technician": 3}, energy=50, margin=0.35)
    R("isolate_steroid_api", "Isolate steroid ingredient", "specialty_pharma", "botanical_extraction_lab", 6, {"herbal_extract": 40, "solvents": 30, "sulfuric_acid": 4}, {"steroid_api": 1}, {"lab_technician": 3, "chemist": 1}, energy=110, margin=0.35, waste={"chemical_waste": 10})
    R("render_gelatin", "Render pharmaceutical gelatin", "specialty_pharma", "botanical_extraction_lab", 3, {"hides": 70, "lime": 10, "water": 200}, {"gelatin": 1}, {"lab_technician": 2}, energy=60, margin=0.25, waste={"organic_waste": 14})

    R("prepare_culture_media", "Prepare culture media", "biologics", "fermentation_plant", 2, {"sugar": 60, "soybeans": 40, "water": 200}, {"culture_media": 1}, {"operator": 4}, energy=60, margin=0.25)
    R("ferment_broth", "Ferment cell broth", "biologics", "fermentation_plant", 8, {"culture_media": 30, "industrial_gases": 30, "water": 200}, {"fermentation_broth": 1}, {"biotechnologist": 2, "operator": 3}, energy=200, margin=0.3, machines=["bioreactor"])
    R("purify_protein", "Purify protein", "biologics", "fermentation_plant", 5, {"fermentation_broth": 60, "solvents": 30, "activated_carbon": 10}, {"purified_protein": 1}, {"biotechnologist": 2, "lab_technician": 4}, energy=120, margin=0.35, waste={"chemical_waste": 14})

    R("produce_antibody_bulk", "Produce antibody bulk", "biologics", "biologics_plant", 12, {"purified_protein": 12, "culture_media": 20, "excipients": 10}, {"antibody_bulk": 1}, {"biotechnologist": 4, "lab_technician": 4}, energy=260, margin=0.4)
    R("produce_insulin_bulk", "Produce insulin bulk", "biologics", "biologics_plant", 8, {"purified_protein": 14, "zinc_ingot": 2, "glycerin": 6}, {"insulin_bulk": 1}, {"biotechnologist": 3, "lab_technician": 4}, energy=200, margin=0.38)
    R("synthesize_peptide_api", "Synthesize peptide ingredient", "biologics", "biologics_plant", 8, {"active_ingredient": 3, "solvents": 40, "ethanol": 20}, {"peptide_api": 1}, {"biotechnologist": 3, "lab_technician": 3}, energy=180, margin=0.35, waste={"chemical_waste": 16})

    R("blow_sterile_vials", "Blow sterile vials", "pharma_packaging", "pharma_packaging_plant", 2, {"glass": 8, "industrial_gases": 6}, {"sterile_vials": 40}, {"operator": 4}, energy=70, margin=0.2)
    R("mold_syringes", "Mold prefilled syringes", "pharma_packaging", "pharma_packaging_plant", 2, {"plastic_granules": 20, "stainless_steel": 2, "silicone": 1}, {"prefilled_syringes": 60}, {"operator": 4, "machinist": 1}, energy=60, margin=0.22)
    R("form_blister_packs", "Form blister packs", "pharma_packaging", "pharma_packaging_plant", 2, {"polypropylene": 16, "aluminum_sheet": 10}, {"blister_packs": 400}, {"operator": 4}, energy=50, margin=0.2, machines=["blister_line"])
    R("build_cold_boxes", "Build cold-chain shippers", "pharma_packaging", "pharma_packaging_plant", 2, {"polyethylene": 24, "insulation": 12, "packaging": 6}, {"cold_chain_boxes": 20}, {"operator": 4}, energy=40, margin=0.2)
    R("treat_sterile_diluent", "Treat sterile diluent", "pharma_packaging", "pharma_packaging_plant", 2, {"water": 400, "industrial_gases": 20}, {"sterile_diluent": 1}, {"operator": 3}, energy=60, margin=0.2)

    R("fill_insulin_pens", "Fill insulin pens", "biologics", "fill_finish_plant", 4, {"insulin_bulk": 3, "prefilled_syringes": 800, "plastic_housings": 500, "packaging": 200}, {"insulin_pens": 1}, {"lab_technician": 4, "biotechnologist": 1}, energy=120, margin=0.32, machines=["filling_isolator"])
    R("fill_mab_therapy", "Fill antibody therapy doses", "biologics", "fill_finish_plant", 5, {"antibody_bulk": 3, "sterile_vials": 400, "sterile_diluent": 200, "cold_chain_boxes": 60}, {"mab_therapy": 1}, {"lab_technician": 4, "biotechnologist": 2}, energy=150, margin=0.3, machines=["lyophilizer", "filling_isolator"])
    R("fill_flu_vaccine", "Fill flu vaccine", "biologics", "fill_finish_plant", 4, {"purified_protein": 5, "adjuvant": 6, "sterile_vials": 500, "sterile_diluent": 300, "cold_chain_boxes": 50}, {"flu_vaccine": 1}, {"lab_technician": 4, "biotechnologist": 1}, energy=130, margin=0.3, machines=["lyophilizer"])

    R("make_capsule_shells", "Mold capsule shells", "specialty_pharma", "formulation_plant", 2, {"gelatin": 14, "glycerin": 4, "water": 20}, {"capsule_shells": 60}, {"operator": 4}, energy=30, margin=0.22)
    R("press_allergy_tablets", "Press allergy tablets", "specialty_pharma", "formulation_plant", 3, {"active_ingredient": 1, "excipients": 12, "blister_packs": 300, "packaging": 20}, {"allergy_tablets": 1}, {"operator": 4, "lab_technician": 2}, energy=70, margin=0.3)
    R("fill_herbal_supplements", "Fill herbal supplements", "specialty_pharma", "formulation_plant", 3, {"herbal_extract": 20, "capsule_shells": 400, "plastic_housings": 120, "packaging": 20}, {"herbal_supplements": 1}, {"operator": 4}, energy=50, margin=0.3)
    R("blend_cough_syrup", "Blend cough syrup", "specialty_pharma", "formulation_plant", 2, {"herbal_extract": 14, "sugar": 60, "glycerin": 10, "glass_vials": 100}, {"cough_syrup": 1}, {"operator": 4}, energy=40, margin=0.28)
    R("compound_ointment", "Compound topical ointment", "specialty_pharma", "formulation_plant", 2, {"steroid_api": 2, "zinc_oxide": 10, "vegetable_oil": 20, "plastic_housings": 150}, {"topical_ointment": 1}, {"operator": 4, "lab_technician": 1}, energy=40, margin=0.3)
    R("compound_hormone_therapy", "Compound hormone therapy", "specialty_pharma", "formulation_plant", 4, {"steroid_api": 4, "excipients": 10, "blister_packs": 200}, {"hormone_therapy": 1}, {"operator": 4, "lab_technician": 2}, energy=70, margin=0.34)
    R("fill_eye_drops", "Fill eye drops", "specialty_pharma", "formulation_plant", 2, {"sterile_diluent": 200, "excipients": 4, "plastic_housings": 300, "glycerin": 6}, {"eye_drops": 1}, {"operator": 4, "lab_technician": 1}, energy=50, margin=0.28)
    R("formulate_antiviral", "Formulate antiviral course", "specialty_pharma", "formulation_plant", 4, {"peptide_api": 3, "excipients": 10, "blister_packs": 250, "packaging": 20}, {"antiviral_course": 1}, {"operator": 4, "lab_technician": 2}, energy=80, margin=0.34)

    s.tech("botanical_extraction", "Botanical extraction", "MATERIALS", 2, 700_000, 100, facilities=["botanical_extraction_lab"],
           effects=[("YIELD_BONUS", 0.05, "specialty_pharma", None)])
    s.tech("biotech_fermentation", "Biotech fermentation", "MATERIALS", 3, 3_200_000, 200, requires=["pharmaceutical_synthesis"],
           facilities=["fermentation_plant"], machines=["bioreactor"])
    s.tech("monoclonal_engineering", "Monoclonal engineering", "MATERIALS", 4, 9_000_000, 300, requires=["biotech_fermentation"],
           facilities=["biologics_plant"], recipes=["produce_antibody_bulk", "produce_insulin_bulk"])
    s.tech("aseptic_fill_finish", "Aseptic fill-finish", "PRODUCTION", 3, 2_800_000, 180, requires=["clean_room_packaging"],
           facilities=["fill_finish_plant"], machines=["lyophilizer", "filling_isolator"])
    s.tech("continuous_manufacturing", "Continuous manufacturing", "PRODUCTION", 3, 2_400_000, 160, requires=["lean_manufacturing", "aseptic_fill_finish"],
           machines=["blister_line"], effects=[("INPUT_SAVINGS", 0.05, "specialty_pharma", None), ("LABOR_SAVINGS", 0.06, "biologics", None)])
    s.tech("cold_chain_logistics", "Cold-chain logistics", "LOGISTICS", 2, 800_000, 100, requires=["route_optimization"],
           effects=[("TRANSPORT_SAVINGS", 0.06, None, None), ("STORAGE_BONUS", 0.05, None, None)])
    s.tech("process_analytics", "Process analytics", "DIGITAL", 3, 1_600_000, 140, requires=["biotech_fermentation"],
           effects=[("QUALITY_BONUS", 0.20, "biologics", None)])
    s.tech("personalized_medicine", "Personalized medicine", "MATERIALS", 4, 8_000_000, 300, requires=["monoclonal_engineering", "process_analytics"],
           recipes=["formulate_antiviral", "compound_hormone_therapy"], effects=[("YIELD_BONUS", 0.08, "biologics", None)])

    D = s.dem
    D("insulin_pens", 20, 35, 40, 0.15, 0.1, [], {}, 0.9)
    D("mab_therapy", 0, 1, 2, 0.2, 0.3, [], {}, 0.95)
    D("flu_vaccine", 80, 120, 140, 0.3, 0.3, [], {"AUTUMN": 2.2, "SUMMER": 0.3}, 0.4)
    D("allergy_tablets", 60, 100, 130, 0.5, 0.5, ["antiviral_course"], {"SPRING": 2.0, "WINTER": 0.5}, 0.5)
    D("herbal_supplements", 60, 140, 220, 1.0, 1.2, ["vitamins"], {}, 0.5)
    D("cough_syrup", 80, 100, 100, 0.4, 0.2, [], {"WINTER": 2.0, "SUMMER": 0.4})
    D("topical_ointment", 90, 110, 110, 0.4, 0.2)
    D("hormone_therapy", 30, 50, 60, 0.3, 0.2, [], {}, 0.7)
    D("eye_drops", 40, 60, 70, 0.5, 0.4, [], {}, 0.6)
    D("antiviral_course", 20, 30, 35, 0.3, 0.2, [], {"WINTER": 1.6}, 0.7)
    return s

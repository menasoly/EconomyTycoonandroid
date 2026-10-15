from contentgen import Spec

def build():
    s = Spec("consumer", "ConsumerPack")
    s.industry("textiles", "Textiles", "TEXTILES", "Spinning, weaving and dyeing")
    s.industry("apparel", "Apparel and footwear", "TEXTILES", "Clothing and shoes")
    s.industry("furniture", "Furniture", "FURNITURE", "Household and office furniture")
    s.industry("household", "Household goods", "CONSUMER_GOODS", "Cleaning, kitchenware, toys and personal care")
    s.industry("pharma", "Pharmaceuticals", "PHARMACEUTICALS", "Medicines and medical supplies")

    for gid, name, price, spoil in [
        ("cotton_yarn", "Cotton yarn", 62, 0), ("cotton_fabric", "Cotton fabric", 90, 0), ("leather", "Tanned leather", 58, 0),
        ("dyed_fabric", "Dyed fabric", 110, 0), ("synthetic_yarn", "Synthetic yarn", 40, 0), ("rubber_soles", "Rubber soles", 6, 0),
        ("wooden_frames", "Wooden furniture frames", 70, 0), ("upholstery_fabric", "Upholstery fabric", 52, 0),
        ("active_ingredient", "Active ingredient", 900, 0), ("excipients", "Excipients", 60, 0), ("glass_vials", "Glass vials", 2, 0),
        ("plastic_housings", "Plastic housings", 5, 0),
        ("cosmetic_base", "Cosmetic base", 30, 5000), ("toy_parts", "Toy parts", 4, 0), ("wood_pulp", "Wood pulp", 30, 0),
    ]:
        s.good(gid, name, "INTERMEDIATE", price, "NORMAL", spoil)
    for gid, name, price in [
        ("tshirt", "T-shirt", 14), ("jeans", "Jeans", 38), ("jacket", "Jacket", 85), ("dress", "Dress", 52), ("shoes", "Shoes", 62),
        ("sneakers", "Sneakers", 75), ("handbag", "Leather handbag", 120), ("bedding_set", "Bedding set", 48), ("towels", "Towels", 16),
        ("sofa", "Sofa", 640), ("dining_table", "Dining table", 380), ("chair", "Chair", 85), ("bed_frame", "Bed frame", 420),
        ("wardrobe", "Wardrobe", 520), ("office_desk", "Office desk", 310), ("office_chair", "Office chair", 190),
        ("detergent", "Laundry detergent", 9), ("dish_soap", "Dish soap", 4), ("shampoo", "Shampoo", 7), ("toothpaste", "Toothpaste", 3),
        ("cosmetics", "Cosmetics", 22), ("toys", "Toys", 18), ("kitchenware", "Kitchenware set", 55), ("paper_goods", "Paper goods", 5),
        ("painkillers", "Painkillers", 6), ("antibiotics", "Antibiotics", 18), ("vaccines", "Vaccines", 32), ("vitamins", "Vitamins", 12),
        ("bandages", "Bandages", 4), ("diagnostic_kits", "Diagnostic kits", 28), ("chronic_meds", "Chronic medication", 40),
    ]:
        s.good(gid, name, "CONSUMER", price, "NORMAL")

    F = s.facility
    F("spinning_mill", "Spinning mill", "FACTORY", "TEXTILES", 2_800_000, 75, 3, 40_000, 900, 110.0, {"operator": 4})
    F("weaving_mill", "Weaving mill", "FACTORY", "TEXTILES", 3_200_000, 80, 3, 40_000, 1_000, 120.0, {"operator": 5})
    F("dye_house", "Dye house", "FACTORY", "TEXTILES", 2_400_000, 70, 2, 30_000, 900, 100.0, {"operator": 3})
    F("tannery", "Tannery", "FACTORY", "TEXTILES", 1_800_000, 60, 2, 30_000, 700, 70.0, {"operator": 3})
    F("garment_factory", "Garment factory", "FACTORY", "TEXTILES", 2_600_000, 70, 5, 40_000, 950, 80.0, {"operator": 4, "clerk": 1})
    F("shoe_factory", "Shoe factory", "FACTORY", "TEXTILES", 2_500_000, 70, 3, 30_000, 900, 80.0, {"operator": 4})
    F("furniture_factory", "Furniture factory", "FACTORY", "FURNITURE", 3_000_000, 80, 4, 60_000, 1_000, 90.0, {"machinist": 3, "operator": 2})
    F("pulp_mill", "Pulp mill", "FACTORY", "CONSUMER_GOODS", 3_800_000, 85, 2, 60_000, 1_200, 140.0, {"operator": 4})
    F("household_factory", "Household goods factory", "FACTORY", "CONSUMER_GOODS", 3_400_000, 80, 4, 50_000, 1_100, 100.0, {"operator": 4})
    F("pharma_plant", "Pharmaceutical plant", "LABORATORY", "PHARMACEUTICALS", 14_000_000, 160, 3, 20_000, 4_200, 260.0, {"scientist": 4, "technician": 6}, tech="pharmaceutical_synthesis")
    F("api_plant", "Active ingredient plant", "LABORATORY", "PHARMACEUTICALS", 18_000_000, 180, 2, 15_000, 5_000, 320.0, {"scientist": 6, "operator": 6}, tech="pharmaceutical_synthesis")

    R = s.recipe
    R("spin_cotton", "Spin cotton yarn", "textiles", "spinning_mill", 3, {"cotton": 110}, {"cotton_yarn": 1}, {"operator": 4}, energy=60)
    R("spin_synthetic", "Spin synthetic yarn", "textiles", "spinning_mill", 3, {"polyethylene": 40, "polypropylene": 40, "pigments": 2}, {"synthetic_yarn": 1}, {"operator": 4}, energy=80)
    R("weave_cotton", "Weave cotton fabric", "textiles", "weaving_mill", 3, {"cotton_yarn": 70}, {"cotton_fabric": 1}, {"operator": 5}, energy=70)
    R("weave_synthetic", "Weave synthetic fabric", "textiles", "weaving_mill", 3, {"synthetic_yarn": 60}, {"fabric_synthetic": 2}, {"operator": 5}, energy=70)
    R("dye_fabric", "Dye fabric", "textiles", "dye_house", 2, {"cotton_fabric": 70, "pigments": 6, "water": 400}, {"dyed_fabric": 1}, {"operator": 3}, energy=60, waste={"chemical_waste": 12})
    R("weave_upholstery", "Weave upholstery fabric", "textiles", "weaving_mill", 3, {"dyed_fabric": 20, "synthetic_yarn": 30}, {"upholstery_fabric": 1}, {"operator": 5}, energy=70)
    R("tan_leather", "Tan leather", "textiles", "tannery", 5, {"hides": 90, "sulfuric_acid": 8, "water": 500}, {"leather": 1}, {"operator": 3}, energy=50, waste={"chemical_waste": 15})
    R("sew_tshirts", "Sew T-shirts", "apparel", "garment_factory", 2, {"cotton_fabric": 12, "packaging": 8}, {"tshirt": 1}, {"operator": 4}, energy=20)
    R("sew_jeans", "Sew jeans", "apparel", "garment_factory", 2, {"dyed_fabric": 22, "fasteners": 4, "packaging": 8}, {"jeans": 1}, {"operator": 4}, energy=24)
    R("sew_jackets", "Sew jackets", "apparel", "garment_factory", 3, {"dyed_fabric": 30, "fabric_synthetic": 20, "fasteners": 6, "packaging": 8}, {"jacket": 1}, {"operator": 4}, energy=30)
    R("sew_dresses", "Sew dresses", "apparel", "garment_factory", 3, {"dyed_fabric": 28, "packaging": 8}, {"dress": 1}, {"operator": 4}, energy=24)
    R("make_bedding", "Sew bedding sets", "apparel", "garment_factory", 2, {"cotton_fabric": 30, "packaging": 8}, {"bedding_set": 1}, {"operator": 4}, energy=20)
    R("make_towels", "Sew towels", "apparel", "garment_factory", 2, {"cotton_fabric": 20, "packaging": 6}, {"towels": 1}, {"operator": 4}, energy=20)
    R("make_rubber_soles", "Mold rubber soles", "apparel", "shoe_factory", 2, {"synthetic_rubber": 40, "carbon_black": 8}, {"rubber_soles": 8}, {"operator": 3}, energy=40)
    R("make_shoes", "Make leather shoes", "apparel", "shoe_factory", 3, {"leather": 20, "rubber_soles": 4, "adhesives": 3, "packaging": 8}, {"shoes": 1}, {"operator": 4}, energy=30)
    R("make_sneakers", "Make sneakers", "apparel", "shoe_factory", 3, {"fabric_synthetic": 12, "rubber_soles": 4, "foam": 5, "adhesives": 3, "packaging": 8}, {"sneakers": 1}, {"operator": 4}, energy=30)
    R("make_handbags", "Make handbags", "apparel", "shoe_factory", 3, {"leather": 28, "fasteners": 6, "packaging": 8}, {"handbag": 1}, {"operator": 4}, energy=25)
    R("make_wooden_frames", "Build wooden frames", "furniture", "furniture_factory", 3, {"lumber": 40, "plywood": 10, "fasteners": 8}, {"wooden_frames": 1}, {"machinist": 3, "operator": 1}, energy=50)
    R("make_sofas", "Assemble sofas", "furniture", "furniture_factory", 4, {"wooden_frames": 3, "foam": 40, "upholstery_fabric": 12, "packaging": 12}, {"sofa": 1}, {"operator": 3}, energy=40)
    R("make_tables", "Build dining tables", "furniture", "furniture_factory", 3, {"lumber": 40, "fasteners": 6, "paint": 3, "packaging": 10}, {"dining_table": 1}, {"machinist": 3}, energy=40)
    R("make_chairs", "Build chairs", "furniture", "furniture_factory", 2, {"lumber": 10, "fasteners": 4, "foam": 6, "upholstery_fabric": 2, "packaging": 4}, {"chair": 1}, {"machinist": 2}, energy=20)
    R("make_beds", "Build bed frames", "furniture", "furniture_factory", 3, {"wooden_frames": 3, "plywood": 14, "fasteners": 8, "packaging": 12}, {"bed_frame": 1}, {"machinist": 3}, energy=40)
    R("make_wardrobes", "Build wardrobes", "furniture", "furniture_factory", 4, {"plywood": 40, "lumber": 20, "fasteners": 12, "paint": 3, "packaging": 14}, {"wardrobe": 1}, {"machinist": 3}, energy=40)
    R("make_office_desks", "Build office desks", "furniture", "furniture_factory", 3, {"plywood": 30, "steel_sheet": 14, "fasteners": 8, "packaging": 10}, {"office_desk": 1}, {"machinist": 3}, energy=40)
    R("make_office_chairs", "Build office chairs", "furniture", "furniture_factory", 3, {"steel_sheet": 6, "foam": 8, "fabric_synthetic": 8, "plastic_granules": 12, "packaging": 6}, {"office_chair": 1}, {"machinist": 3}, energy=30)
    R("pack_detergent", "Package laundry detergent", "household", "household_factory", 2, {"detergent_base": 8, "plastic_granules": 6, "packaging": 6}, {"detergent": 16}, {"operator": 3}, energy=20)
    R("pack_dish_soap", "Package dish soap", "household", "household_factory", 2, {"detergent_base": 4, "plastic_granules": 4, "packaging": 4}, {"dish_soap": 16}, {"operator": 3}, energy=20)
    R("mix_cosmetic_base", "Mix cosmetic base", "household", "household_factory", 2, {"vegetable_oil": 20, "ethanol": 10, "pigments": 2}, {"cosmetic_base": 1}, {"operator": 3, "technician": 1}, energy=40)
    R("pack_shampoo", "Package shampoo", "household", "household_factory", 2, {"cosmetic_base": 6, "detergent_base": 4, "plastic_granules": 5, "packaging": 5}, {"shampoo": 16}, {"operator": 3}, energy=20)
    R("pack_cosmetics", "Package cosmetics", "household", "household_factory", 2, {"cosmetic_base": 6, "plastic_granules": 3, "packaging": 6}, {"cosmetics": 14}, {"operator": 3}, energy=20)
    R("pack_toothpaste", "Package toothpaste", "household", "household_factory", 2, {"cosmetic_base": 3, "limestone": 14, "plastic_granules": 3, "packaging": 4}, {"toothpaste": 24}, {"operator": 3}, energy=20)
    R("mold_toy_parts", "Mold toy parts", "household", "household_factory", 2, {"plastic_granules": 30, "pigments": 2}, {"toy_parts": 10}, {"operator": 3}, energy=50)
    R("assemble_toys", "Assemble toys", "household", "household_factory", 2, {"toy_parts": 6, "packaging": 6, "fabric_synthetic": 2}, {"toys": 4}, {"operator": 4}, energy=20)
    R("make_kitchenware", "Make kitchenware", "household", "household_factory", 3, {"stainless_steel": 14, "plastic_granules": 6, "packaging": 8}, {"kitchenware": 1}, {"operator": 4, "machinist": 1}, energy=40)
    R("pulp_logs", "Pulp logs", "household", "pulp_mill", 3, {"logs": 90, "water": 500}, {"wood_pulp": 1}, {"operator": 4}, energy=120, waste={"organic_waste": 20})
    R("make_paper_goods", "Convert paper goods", "household", "household_factory", 2, {"wood_pulp": 30, "packaging": 4}, {"paper_goods": 14}, {"operator": 3}, energy=30)
    R("make_plastic_housings", "Mold plastic housings", "household", "household_factory", 2, {"plastic_granules": 20, "pigments": 1}, {"plastic_housings": 8}, {"operator": 3}, energy=40)
    R("synthesize_api", "Synthesize active ingredient", "pharma", "api_plant", 10, {"ethanol": 120, "solvents": 100, "sugar": 80, "sulfuric_acid": 20}, {"active_ingredient": 1}, {"scientist": 3, "operator": 4}, energy=300, waste={"chemical_waste": 40}, tech="pharmaceutical_synthesis")
    R("make_excipients", "Make excipients", "pharma", "pharma_plant", 3, {"sugar": 60, "limestone": 20, "water": 100}, {"excipients": 1}, {"technician": 3}, energy=60, tech="pharmaceutical_synthesis")
    R("make_glass_vials", "Mold glass vials", "pharma", "pharma_plant", 2, {"glass": 6, "packaging": 1}, {"glass_vials": 40}, {"technician": 3}, energy=70, tech="pharmaceutical_synthesis")
    R("press_painkillers", "Press painkillers", "pharma", "pharma_plant", 3, {"active_ingredient": 2, "excipients": 8, "plastic_granules": 8, "packaging": 6}, {"painkillers": 600}, {"technician": 5, "scientist": 1}, energy=80, tech="pharmaceutical_synthesis")
    R("make_antibiotics", "Make antibiotics", "pharma", "pharma_plant", 4, {"active_ingredient": 3, "excipients": 8, "glass_vials": 40, "packaging": 6}, {"antibiotics": 300}, {"technician": 5, "scientist": 1}, energy=100, tech="pharmaceutical_synthesis")
    R("make_vaccines", "Make vaccines", "pharma", "pharma_plant", 6, {"active_ingredient": 4, "excipients": 6, "glass_vials": 120, "packaging": 8}, {"vaccines": 200}, {"technician": 6, "scientist": 2}, energy=140, tech="pharmaceutical_synthesis")
    R("make_vitamins", "Make vitamins", "pharma", "pharma_plant", 3, {"active_ingredient": 1, "excipients": 10, "plastic_granules": 6, "packaging": 6}, {"vitamins": 700}, {"technician": 4}, energy=60, tech="pharmaceutical_synthesis")
    R("make_bandages", "Make bandages", "pharma", "pharma_plant", 2, {"cotton_fabric": 10, "adhesives": 2, "packaging": 8}, {"bandages": 400}, {"technician": 3}, energy=30, tech="pharmaceutical_synthesis")
    R("make_diagnostic_kits", "Make diagnostic kits", "pharma", "pharma_plant", 4, {"active_ingredient": 1, "sensor_pack": 4, "plastic_housings": 80, "packaging": 6}, {"diagnostic_kits": 90}, {"technician": 5, "scientist": 1}, energy=90, tech="pharmaceutical_synthesis")
    R("make_chronic_meds", "Make chronic medication", "pharma", "pharma_plant", 4, {"active_ingredient": 3, "excipients": 8, "plastic_housings": 20, "packaging": 6}, {"chronic_meds": 260}, {"technician": 5, "scientist": 1}, energy=100, tech="pharmaceutical_synthesis")

    s.tech("pharmaceutical_synthesis", "Pharmaceutical synthesis", "MATERIALS", 3, 5_000_000, 240, facilities=["pharma_plant", "api_plant"])
    s.tech("automated_looms", "Automated looms", "PRODUCTION", 2, 900_000, 110, effects=[("THROUGHPUT_BONUS", 0.10, "textiles", None), ("LABOR_SAVINGS", 0.06, "textiles", None)])
    s.tech("cad_furniture", "Computer-aided furniture design", "DIGITAL", 2, 650_000, 90, effects=[("INPUT_SAVINGS", 0.05, "furniture", None)])
    s.tech("clean_room_packaging", "Clean-room packaging", "PRODUCTION", 3, 2_200_000, 150, requires=["pharmaceutical_synthesis"], effects=[("QUALITY_BONUS", 0.30, "pharma", None)])
    s.tech("bulk_fulfillment", "Bulk fulfillment", "LOGISTICS", 2, 700_000, 100, effects=[("STORAGE_BONUS", 0.12, None, None)])

    D = s.dem
    D("tshirt", 900, 1100, 1200, 0.9, 0.9, ["jeans"])
    D("jeans", 400, 480, 520, 0.9, 1.0, ["tshirt"])
    D("jacket", 150, 260, 330, 1.0, 1.1, [], {"WINTER": 1.8, "SUMMER": 0.4})
    D("dress", 180, 300, 420, 1.1, 1.2)
    D("shoes", 240, 340, 420, 0.9, 0.9, ["sneakers"])
    D("sneakers", 200, 320, 420, 1.0, 1.0, ["shoes"])
    D("handbag", 20, 80, 220, 1.3, 1.5)
    D("bedding_set", 40, 70, 90, 0.8, 0.8)
    D("towels", 120, 150, 170, 0.7, 0.6)
    D("sofa", 6, 14, 30, 1.2, 1.4)
    D("dining_table", 4, 9, 20, 1.2, 1.4)
    D("chair", 40, 80, 150, 1.0, 1.1)
    D("bed_frame", 8, 16, 30, 1.1, 1.3)
    D("wardrobe", 6, 12, 22, 1.1, 1.3)
    D("office_desk", 3, 8, 14, 1.0, 1.0)
    D("office_chair", 4, 9, 16, 1.0, 1.0)
    D("detergent", 350, 380, 400, 0.4, 0.2)
    D("dish_soap", 280, 300, 320, 0.4, 0.2)
    D("shampoo", 240, 300, 340, 0.5, 0.4)
    D("toothpaste", 180, 190, 200, 0.3, 0.1)
    D("cosmetics", 60, 160, 300, 1.0, 1.3, [], {}, 0.35)
    D("toys", 100, 180, 260, 1.0, 1.1, [], {"WINTER": 1.5, "SUMMER": 0.9})
    D("kitchenware", 4, 8, 14, 0.9, 0.9)
    D("paper_goods", 300, 340, 360, 0.5, 0.3)
    D("painkillers", 500, 560, 560, 0.3, 0.1)
    D("antibiotics", 70, 80, 85, 0.25, 0.1, [], {"WINTER": 1.4})
    D("vaccines", 40, 60, 70, 0.3, 0.2, [], {"AUTUMN": 1.5})
    D("vitamins", 120, 260, 360, 0.8, 0.9, [], {}, 0.3)
    D("bandages", 160, 180, 190, 0.3, 0.1)
    D("diagnostic_kits", 18, 26, 30, 0.4, 0.2)
    D("chronic_meds", 120, 160, 170, 0.2, 0.1, [], {}, 0.55)
    return s

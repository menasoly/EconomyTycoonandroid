"""Textiles: man-made and wool fibers, knitting, technical fabrics, apparel and home textiles."""
from expgen import ExpSpec


def build():
    s = ExpSpec("textiles", "TextilesPack")
    s.role("textile_technician", "Textile technician", "SKILLED", 88.0, 40)

    s.industry("fibers_and_yarns", "Fibers and yarns", "TEXTILES", "Viscose, polyester, nylon and wool spinning")
    s.industry("technical_textiles", "Technical textiles", "TEXTILES", "Nonwovens, airbag fabric, filter cloth and geotextiles")
    s.industry("knitwear_and_sportswear", "Knitwear and sportswear", "TEXTILES", "Knitted fabrics, socks, sweaters, suits and activewear")
    s.industry("home_textiles", "Home and outdoor textiles", "TEXTILES", "Curtains, carpets, blankets and camping gear")

    s.add_goods("INTERMEDIATE", [
        ("viscose_fiber", "Viscose fiber", 48), ("polyester_yarn", "Polyester yarn", 44), ("nylon_yarn", "Nylon yarn", 58), ("wool_yarn", "Wool yarn", 78),
        ("blended_yarn", "Blended yarn", 56), ("knit_fabric", "Knit fabric", 70), ("denim_fabric", "Denim fabric", 84), ("wool_fabric", "Wool fabric", 118),
        ("nonwoven_fabric", "Nonwoven fabric", 36), ("bleached_fabric", "Bleached fabric", 98), ("felt", "Wool felt", 64), ("technical_fabric", "Technical fabric", 150),
    ])
    s.add_goods("INDUSTRIAL", [("geotextile", "Geotextile", 62), ("filter_cloth", "Filter cloth", 55), ("airbag_fabric", "Airbag fabric", 180)], "STABLE")
    s.add_goods("CONSUMER", [
        ("socks", "Socks", 5), ("sweater", "Sweater", 58), ("suit", "Business suit", 190), ("activewear", "Activewear", 48), ("coat", "Wool coat", 140),
        ("workwear", "Workwear", 32), ("face_masks", "Face masks", 3), ("curtains", "Curtains", 36), ("carpet", "Carpet", 210), ("blanket", "Wool blanket", 44),
        ("tent", "Camping tent", 180), ("sleeping_bag", "Sleeping bag", 95), ("umbrella", "Umbrella", 22), ("scarf", "Scarf", 18),
    ])

    s.fac("fiber_plant", "Fiber plant", "FACTORY", "TEXTILES", 6_400_000, 100, 3, 1_900, 220.0, {"operator": 4, "chemist": 1}, 700)
    s.fac("wool_mill", "Wool mill", "FACTORY", "TEXTILES", 3_100_000, 80, 3, 1_000, 100.0, {"operator": 4}, 560)
    s.fac("knitting_mill", "Knitting mill", "FACTORY", "TEXTILES", 3_600_000, 85, 4, 1_100, 110.0, {"operator": 4, "textile_technician": 1}, 600)
    s.fac("technical_textile_works", "Technical textile works", "FACTORY", "TEXTILES", 7_400_000, 120, 3, 2_200, 200.0, {"operator": 4, "textile_technician": 2, "engineer": 1}, 800)
    s.fac("apparel_plant", "Apparel plant", "FACTORY", "TEXTILES", 3_000_000, 70, 5, 1_000, 70.0, {"operator": 5, "textile_technician": 1}, 520)
    s.fac("home_textile_factory", "Home textile factory", "FACTORY", "TEXTILES", 3_400_000, 80, 4, 1_050, 90.0, {"operator": 4, "textile_technician": 1}, 560)

    s.machine("filament_spinner", "Filament spinner", 780_000, thr=1.25, wear=450, maint=60.0)
    s.machine("circular_knitter", "Circular knitting machine", 520_000, thr=1.3, wear=400, maint=40.0, labor_sav=0.08)
    s.machine("tufting_machine", "Carpet tufting machine", 480_000, thr=1.2, wear=420, maint=38.0)

    R = s.rec
    R("spin_viscose", "Spin viscose fiber", "fibers_and_yarns", "fiber_plant", 4, {"wood_pulp": 80, "caustic_soda": 20, "sulfur": 8, "water": 200}, {"viscose_fiber": 1}, {"operator": 4, "chemist": 1}, energy=110, margin=0.2, waste={"chemical_waste": 12})
    R("extrude_polyester", "Extrude polyester yarn", "fibers_and_yarns", "fiber_plant", 3, {"pet_resin": 70, "pigments": 2}, {"polyester_yarn": 1}, {"operator": 4}, energy=140, margin=0.2, machines=["filament_spinner"])
    R("extrude_nylon_yarn", "Extrude nylon yarn", "fibers_and_yarns", "fiber_plant", 3, {"nylon_resin": 60, "pigments": 2}, {"nylon_yarn": 1}, {"operator": 4}, energy=150, margin=0.22, machines=["filament_spinner"])
    R("make_nonwoven", "Bond nonwoven fabric", "technical_textiles", "fiber_plant", 2, {"polypropylene": 70, "adhesives": 4}, {"nonwoven_fabric": 1}, {"operator": 4}, energy=120, margin=0.2)
    R("spin_wool_yarn", "Spin wool yarn", "fibers_and_yarns", "wool_mill", 3, {"wool": 100, "water": 150}, {"wool_yarn": 1}, {"operator": 4}, energy=60, margin=0.2, waste={"organic_waste": 8})
    R("weave_wool_fabric", "Weave wool fabric", "fibers_and_yarns", "wool_mill", 3, {"wool_yarn": 70, "pigments": 3}, {"wool_fabric": 1}, {"operator": 4}, energy=60, margin=0.2)
    R("press_felt", "Press wool felt", "fibers_and_yarns", "wool_mill", 2, {"wool": 90, "adhesives": 4, "water": 100}, {"felt": 1}, {"operator": 4}, energy=50, margin=0.22)
    R("blend_yarn", "Blend yarn", "knitwear_and_sportswear", "knitting_mill", 2, {"viscose_fiber": 40, "polyester_yarn": 40}, {"blended_yarn": 1}, {"operator": 4}, energy=50, margin=0.2)
    R("knit_fabric", "Knit fabric", "knitwear_and_sportswear", "knitting_mill", 3, {"blended_yarn": 60, "polyester_yarn": 20}, {"knit_fabric": 1}, {"operator": 4, "textile_technician": 1}, energy=70, margin=0.22, machines=["circular_knitter"])
    R("weave_denim", "Weave denim", "knitwear_and_sportswear", "knitting_mill", 3, {"cotton_yarn": 70, "pigments": 5}, {"denim_fabric": 1}, {"operator": 4}, energy=60, margin=0.2)
    R("bleach_cotton_fabric", "Bleach cotton fabric", "knitwear_and_sportswear", "knitting_mill", 2, {"cotton_fabric": 70, "hydrogen_peroxide": 6, "water": 300}, {"bleached_fabric": 1}, {"operator": 4}, energy=50, margin=0.22, waste={"chemical_waste": 8})
    R("weave_technical_fabric", "Weave technical fabric", "technical_textiles", "technical_textile_works", 4, {"nylon_yarn": 60, "silicone": 4}, {"technical_fabric": 1}, {"operator": 4, "textile_technician": 2}, energy=110, margin=0.26)
    R("weave_airbag_fabric", "Weave airbag fabric", "technical_textiles", "technical_textile_works", 4, {"nylon_yarn": 70, "silicone": 6}, {"airbag_fabric": 1}, {"operator": 4, "textile_technician": 2, "engineer": 1}, energy=120, margin=0.28)
    R("make_filter_cloth", "Make filter cloth", "technical_textiles", "technical_textile_works", 3, {"nonwoven_fabric": 40, "polyester_yarn": 12}, {"filter_cloth": 1}, {"operator": 4, "textile_technician": 1}, energy=80, margin=0.24)
    R("make_geotextile", "Make geotextile", "technical_textiles", "technical_textile_works", 3, {"nonwoven_fabric": 60, "polypropylene": 14}, {"geotextile": 1}, {"operator": 4, "textile_technician": 1}, energy=80, margin=0.24)
    R("knit_socks", "Knit socks", "knitwear_and_sportswear", "apparel_plant", 2, {"nylon_yarn": 8, "blended_yarn": 10, "packaging": 6}, {"socks": 1}, {"operator": 5}, energy=22, margin=0.25)
    R("knit_sweaters", "Knit sweaters", "knitwear_and_sportswear", "apparel_plant", 3, {"wool_yarn": 14, "blended_yarn": 6, "packaging": 8}, {"sweater": 1}, {"operator": 5}, energy=26, margin=0.25)
    R("tailor_suits", "Tailor business suits", "knitwear_and_sportswear", "apparel_plant", 4, {"wool_fabric": 18, "dyed_fabric": 6, "fasteners": 6, "packaging": 8}, {"suit": 1}, {"operator": 5, "textile_technician": 1}, energy=30, margin=0.28)
    R("sew_activewear", "Sew activewear", "knitwear_and_sportswear", "apparel_plant", 2, {"knit_fabric": 12, "nylon_yarn": 4, "packaging": 6}, {"activewear": 1}, {"operator": 5}, energy=24, margin=0.25)
    R("sew_coats", "Sew wool coats", "knitwear_and_sportswear", "apparel_plant", 4, {"wool_fabric": 22, "fabric_synthetic": 8, "fasteners": 8, "packaging": 8}, {"coat": 1}, {"operator": 5, "textile_technician": 1}, energy=30, margin=0.26)
    R("sew_workwear", "Sew workwear", "knitwear_and_sportswear", "apparel_plant", 2, {"denim_fabric": 14, "fasteners": 4, "packaging": 6}, {"workwear": 1}, {"operator": 5}, energy=24, margin=0.24)
    R("fold_face_masks", "Fold face masks", "technical_textiles", "apparel_plant", 2, {"nonwoven_fabric": 12, "polyester_yarn": 2, "packaging": 6}, {"face_masks": 40}, {"operator": 5}, energy=20, margin=0.24)
    R("make_curtains", "Sew curtains", "home_textiles", "home_textile_factory", 2, {"bleached_fabric": 12, "fasteners": 2, "packaging": 6}, {"curtains": 1}, {"operator": 4}, energy=20, margin=0.25)
    R("tuft_carpets", "Tuft carpets", "home_textiles", "home_textile_factory", 4, {"felt": 14, "polyester_yarn": 30, "adhesives": 4}, {"carpet": 1}, {"operator": 4, "textile_technician": 1}, energy=60, margin=0.26, machines=["tufting_machine"])
    R("weave_blankets", "Weave wool blankets", "home_textiles", "home_textile_factory", 3, {"wool_fabric": 12, "packaging": 6}, {"blanket": 1}, {"operator": 4}, energy=30, margin=0.24)
    R("sew_tents", "Sew camping tents", "home_textiles", "home_textile_factory", 4, {"technical_fabric": 14, "aluminum_sheet": 4, "fasteners": 6, "packaging": 8}, {"tent": 1}, {"operator": 4, "textile_technician": 1}, energy=36, margin=0.26)
    R("sew_sleeping_bags", "Sew sleeping bags", "home_textiles", "home_textile_factory", 3, {"technical_fabric": 6, "foam": 8, "fasteners": 3, "packaging": 6}, {"sleeping_bag": 1}, {"operator": 4}, energy=26, margin=0.26)
    R("assemble_umbrellas", "Assemble umbrellas", "home_textiles", "home_textile_factory", 2, {"technical_fabric": 2, "steel_wire": 2, "plastic_granules": 2, "packaging": 2}, {"umbrella": 1}, {"operator": 4}, energy=16, margin=0.26)
    R("knit_scarves", "Knit scarves", "home_textiles", "home_textile_factory", 2, {"wool_yarn": 4, "blended_yarn": 3, "packaging": 3}, {"scarf": 1}, {"operator": 4}, energy=14, margin=0.26)

    s.tech("synthetic_fibers", "Synthetic fibers", "MATERIALS", 2, 1_200_000, 120, requires=["catalytic_cracking"],
           facilities=["fiber_plant"], machines=["filament_spinner"])
    s.tech("technical_textiles", "Technical textiles", "MATERIALS", 3, 2_600_000, 170, requires=["synthetic_fibers"],
           facilities=["technical_textile_works"], recipes=["weave_airbag_fabric"])
    s.tech("seamless_knitting", "Seamless knitting", "PRODUCTION", 2, 1_000_000, 110, requires=["automated_looms"],
           machines=["circular_knitter"], effects=[("THROUGHPUT_BONUS", 0.08, "knitwear_and_sportswear", None)])
    s.tech("textile_automation", "Textile automation", "PRODUCTION", 3, 1_900_000, 150, requires=["automated_looms", "factory_automation"],
           machines=["tufting_machine"], effects=[("LABOR_SAVINGS", 0.10, "home_textiles", None), ("LABOR_SAVINGS", 0.08, "knitwear_and_sportswear", None)])
    s.tech("sustainable_fibers", "Sustainable fibers", "MATERIALS", 3, 1_600_000, 140, requires=["green_chemistry", "synthetic_fibers"],
           effects=[("INPUT_SAVINGS", 0.06, "technical_textiles", None), ("INPUT_SAVINGS", 0.04, "fibers_and_yarns", None)])
    s.tech("digital_pattern_making", "Digital pattern making", "DIGITAL", 2, 700_000, 90, requires=["cad_furniture"],
           effects=[("INPUT_SAVINGS", 0.05, "knitwear_and_sportswear", None), ("INPUT_SAVINGS", 0.04, "home_textiles", None)])

    D = s.dem
    D("socks", 900, 1000, 1000, 0.5, 0.3)
    D("sweater", 90, 160, 210, 1.0, 1.0, ["coat"], {"WINTER": 2.0, "SUMMER": 0.3})
    D("suit", 10, 40, 110, 1.2, 1.4, [], {}, 0.5)
    D("activewear", 80, 200, 300, 1.1, 1.2, ["tshirt"], {"SUMMER": 1.3})
    D("coat", 40, 90, 140, 1.0, 1.1, ["jacket"], {"WINTER": 2.4, "SUMMER": 0.1})
    D("workwear", 50, 70, 70, 0.6, 0.3, [], {}, 0.4)
    D("face_masks", 120, 160, 180, 0.5, 0.3, [], {"WINTER": 1.4}, 0.2)
    D("curtains", 30, 50, 70, 0.8, 0.8)
    D("carpet", 4, 10, 22, 1.1, 1.3)
    D("blanket", 40, 60, 70, 0.7, 0.6, [], {"WINTER": 1.8, "SUMMER": 0.4})
    D("tent", 2, 6, 12, 1.2, 1.4, [], {"SUMMER": 2.0, "WINTER": 0.2}, 0.6)
    D("sleeping_bag", 3, 8, 14, 1.1, 1.2, [], {"SUMMER": 1.8, "WINTER": 0.4}, 0.6)
    D("umbrella", 30, 40, 40, 0.8, 0.5, [], {"SPRING": 1.6, "AUTUMN": 1.6, "SUMMER": 0.6})
    D("scarf", 60, 90, 120, 1.0, 1.0, [], {"WINTER": 2.4, "SUMMER": 0.1})
    D("geotextile", 6, 8, 8, 0.6, 0.4)
    D("filter_cloth", 8, 10, 10, 0.5, 0.3)
    return s

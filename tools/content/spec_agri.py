from contentgen import Spec

def build():
    s = Spec("agriculture", "AgriculturePack")
    s.industry("crops", "Crop farming", "AGRICULTURE", "Field crops, fruit and vegetables")
    s.industry("livestock", "Livestock", "AGRICULTURE", "Cattle, hogs, poultry and dairy")
    s.industry("forestry", "Forestry", "AGRICULTURE", "Timber and managed woodland")
    s.industry("plantation", "Plantations", "AGRICULTURE", "Coffee, cocoa, tea and cotton")

    # Raw goods
    for gid, name, price, spoil, vol in [
        ("wheat", "Wheat", 20, 0, "NORMAL"), ("corn", "Corn", 16, 0, "NORMAL"), ("rice", "Rice", 30, 0, "NORMAL"),
        ("soybeans", "Soybeans", 28, 0, "NORMAL"), ("sugar_beet", "Sugar beet", 12, 3000, "NORMAL"),
        ("potatoes", "Potatoes", 14, 4000, "NORMAL"), ("tomatoes", "Tomatoes", 25, 15000, "NORMAL"),
        ("apples", "Apples", 22, 8000, "NORMAL"), ("grapes", "Grapes", 35, 12000, "NORMAL"),
        ("cotton", "Raw cotton", 40, 0, "VOLATILE"), ("coffee_beans", "Coffee beans", 90, 0, "VOLATILE"),
        ("cocoa_beans", "Cocoa beans", 85, 0, "VOLATILE"), ("tea_leaves", "Tea leaves", 70, 2000, "NORMAL"),
        ("raw_milk", "Raw milk", 18, 40000, "NORMAL"), ("eggs", "Eggs", 12, 10000, "NORMAL"),
        ("cattle", "Cattle", 400, 0, "NORMAL"), ("hogs", "Hogs", 160, 0, "NORMAL"), ("poultry", "Poultry", 8, 0, "NORMAL"),
        ("wool", "Raw wool", 55, 0, "NORMAL"), ("logs", "Logs", 35, 0, "NORMAL"),
    ]:
        s.good(gid, name, "RAW", price, vol, spoil)
    s.good("animal_feed", "Animal feed", "INTERMEDIATE", 15, "NORMAL", 1000)
    s.good("fertilizer", "Fertilizer", "INDUSTRIAL", 25, "NORMAL")
    s.good("seeds", "Seed stock", "INDUSTRIAL", 40, "STABLE")

    # Facilities
    F = s.facility
    F("grain_farm", "Grain farm", "FARM", "AGRICULTURE", 250_000, 45, 4, 40_000, 150, 2.0, {"farmhand": 2})
    F("vegetable_farm", "Vegetable farm", "FARM", "AGRICULTURE", 220_000, 40, 4, 25_000, 140, 2.0, {"farmhand": 2})
    F("orchard", "Orchard", "FARM", "AGRICULTURE", 300_000, 60, 3, 25_000, 160, 1.0, {"farmhand": 2})
    F("vineyard", "Vineyard", "FARM", "AGRICULTURE", 340_000, 60, 3, 20_000, 170, 1.0, {"farmhand": 2})
    F("plantation", "Plantation", "FARM", "AGRICULTURE", 380_000, 70, 3, 25_000, 190, 1.0, {"farmhand": 3})
    F("dairy_farm", "Dairy farm", "FARM", "AGRICULTURE", 420_000, 60, 3, 30_000, 260, 3.0, {"farmhand": 3})
    F("cattle_ranch", "Cattle ranch", "FARM", "AGRICULTURE", 450_000, 60, 3, 50_000, 240, 1.0, {"farmhand": 3})
    F("hog_farm", "Hog farm", "FARM", "AGRICULTURE", 330_000, 50, 3, 30_000, 210, 2.0, {"farmhand": 3})
    F("poultry_farm", "Poultry farm", "FARM", "AGRICULTURE", 280_000, 45, 3, 20_000, 200, 3.0, {"farmhand": 2})
    F("sheep_ranch", "Sheep ranch", "FARM", "AGRICULTURE", 260_000, 45, 2, 20_000, 150, 1.0, {"farmhand": 2})
    F("forestry_camp", "Forestry camp", "FARM", "AGRICULTURE", 310_000, 50, 3, 60_000, 180, 1.0, {"laborer": 2})
    F("greenhouse", "Greenhouse complex", "FARM", "AGRICULTURE", 520_000, 55, 4, 20_000, 300, 6.0, {"farmhand": 2, "technician": 1})

    R = s.recipe
    # Field crops
    R("grow_wheat", "Grow wheat", "crops", "grain_farm", 60, {"fertilizer": 40, "water": 300, "diesel": 25, "seeds": 20}, {"wheat": 1}, {"farmhand": 3})
    R("grow_corn", "Grow corn", "crops", "grain_farm", 70, {"fertilizer": 45, "water": 350, "diesel": 25, "seeds": 18}, {"corn": 1}, {"farmhand": 3})
    R("grow_rice", "Grow rice", "crops", "grain_farm", 80, {"fertilizer": 35, "water": 900, "diesel": 20, "seeds": 25}, {"rice": 1}, {"farmhand": 4})
    R("grow_soybeans", "Grow soybeans", "crops", "grain_farm", 65, {"fertilizer": 25, "water": 280, "diesel": 22, "seeds": 20}, {"soybeans": 1}, {"farmhand": 3})
    R("grow_sugar_beet", "Grow sugar beet", "crops", "grain_farm", 75, {"fertilizer": 50, "water": 400, "diesel": 28, "seeds": 15}, {"sugar_beet": 1}, {"farmhand": 3})
    R("grow_potatoes", "Grow potatoes", "crops", "vegetable_farm", 55, {"fertilizer": 40, "water": 300, "diesel": 22, "seeds": 30}, {"potatoes": 1}, {"farmhand": 3})
    R("grow_tomatoes", "Grow tomatoes", "crops", "vegetable_farm", 50, {"fertilizer": 35, "water": 450, "diesel": 15, "seeds": 20}, {"tomatoes": 1}, {"farmhand": 3})
    R("grow_greenhouse_tomatoes", "Greenhouse tomatoes", "crops", "greenhouse", 40, {"fertilizer": 30, "water": 350, "seeds": 20}, {"tomatoes": 1}, {"farmhand": 2, "technician": 1}, energy=120)
    R("harvest_apples", "Harvest apples", "crops", "orchard", 60, {"fertilizer": 20, "water": 250, "diesel": 15}, {"apples": 1}, {"farmhand": 3})
    R("harvest_grapes", "Harvest grapes", "crops", "vineyard", 65, {"fertilizer": 20, "water": 220, "diesel": 15}, {"grapes": 1}, {"farmhand": 3})
    R("grow_cotton", "Grow cotton", "plantation", "plantation", 80, {"fertilizer": 45, "water": 500, "diesel": 25, "seeds": 20}, {"cotton": 1}, {"farmhand": 4})
    R("grow_coffee", "Grow coffee", "plantation", "plantation", 90, {"fertilizer": 30, "water": 400, "diesel": 15}, {"coffee_beans": 1}, {"farmhand": 4})
    R("grow_cocoa", "Grow cocoa", "plantation", "plantation", 90, {"fertilizer": 30, "water": 450, "diesel": 15}, {"cocoa_beans": 1}, {"farmhand": 4})
    R("grow_tea", "Grow tea", "plantation", "plantation", 70, {"fertilizer": 28, "water": 380, "diesel": 12}, {"tea_leaves": 1}, {"farmhand": 3})
    # Livestock
    R("raise_cattle", "Raise cattle", "livestock", "cattle_ranch", 120, {"animal_feed": 500, "water": 700, "diesel": 20}, {"cattle": 1}, {"farmhand": 3})
    R("raise_hogs", "Raise hogs", "livestock", "hog_farm", 90, {"animal_feed": 420, "water": 500}, {"hogs": 1}, {"farmhand": 3}, energy=40)
    R("raise_poultry", "Raise poultry", "livestock", "poultry_farm", 45, {"animal_feed": 260, "water": 260}, {"poultry": 1}, {"farmhand": 2}, energy=60)
    R("collect_eggs", "Collect eggs", "livestock", "poultry_farm", 10, {"animal_feed": 90, "water": 80}, {"eggs": 1}, {"farmhand": 2}, energy=25)
    R("milk_cows", "Milk cows", "livestock", "dairy_farm", 10, {"animal_feed": 120, "water": 160}, {"raw_milk": 1}, {"farmhand": 3}, energy=45)
    R("shear_sheep", "Shear sheep", "livestock", "sheep_ranch", 30, {"animal_feed": 90, "water": 120}, {"wool": 1}, {"farmhand": 2})
    # Forestry
    R("fell_timber", "Fell timber", "forestry", "forestry_camp", 30, {"diesel": 30}, {"logs": 1}, {"laborer": 4}, waste={"organic_waste": 8})
    # Feed and seeds
    R("mix_feed_corn", "Mix feed from corn", "livestock", "dairy_farm", 5, {"corn": 90, "soybeans": 30, "water": 20}, {"animal_feed": 1}, {"farmhand": 1}, energy=18)
    R("breed_seeds", "Breed seed stock", "crops", "grain_farm", 20, {"fertilizer": 30, "water": 120, "diesel": 8}, {"seeds": 1}, {"farmhand": 2, "technician": 1}, energy=10)
    return s

from contentgen import Spec

def build():
    s = Spec("food", "FoodPack")
    s.industry("milling", "Milling and baking", "FOOD", "Flour, bread and pasta")
    s.industry("dairy", "Dairy processing", "FOOD", "Milk products")
    s.industry("meat", "Meat processing", "FOOD", "Slaughter and packing")
    s.industry("beverages", "Beverages", "FOOD", "Soft drinks, coffee, tea, wine")
    s.industry("confectionery", "Confectionery", "FOOD", "Sugar, chocolate and snacks")
    s.industry("preserved", "Preserved food", "FOOD", "Canning and frozen")

    # intermediates and consumer goods
    for gid, name, cat, price, spoil in [
        ("flour", "Flour", "INTERMEDIATE", 28, 500), ("sugar", "Sugar", "INTERMEDIATE", 34, 0),
        ("vegetable_oil", "Vegetable oil", "INTERMEDIATE", 45, 500), ("rice_polished", "Polished rice", "INTERMEDIATE", 42, 0),
        ("beef", "Beef", "INTERMEDIATE", 70, 30000), ("pork", "Pork", "INTERMEDIATE", 55, 30000),
        ("chicken_meat", "Chicken meat", "INTERMEDIATE", 38, 35000), ("hides", "Hides", "INTERMEDIATE", 20, 2000),
        ("cocoa_mass", "Cocoa mass", "INTERMEDIATE", 120, 0), ("coffee_roasted", "Roasted coffee", "INTERMEDIATE", 130, 500),
        ("grape_must", "Grape must", "INTERMEDIATE", 50, 10000), ("fruit_juice_conc", "Juice concentrate", "INTERMEDIATE", 48, 3000),
        ("bread", "Bread", "CONSUMER", 6, 40000), ("pasta", "Pasta", "CONSUMER", 5, 0),
        ("cheese", "Cheese", "CONSUMER", 24, 6000), ("butter", "Butter", "CONSUMER", 20, 5000),
        ("yogurt", "Yogurt", "CONSUMER", 8, 20000), ("fresh_milk", "Bottled milk", "CONSUMER", 6, 50000),
        ("sausages", "Sausages", "CONSUMER", 15, 20000), ("canned_vegetables", "Canned vegetables", "CONSUMER", 5, 100),
        ("canned_fruit", "Canned fruit", "CONSUMER", 6, 100), ("frozen_meals", "Frozen meals", "CONSUMER", 10, 500),
        ("chocolate", "Chocolate", "CONSUMER", 14, 500), ("candy", "Candy", "CONSUMER", 7, 200),
        ("potato_chips", "Potato chips", "CONSUMER", 6, 1500), ("soft_drinks", "Soft drinks", "CONSUMER", 3, 0),
        ("bottled_juice", "Bottled juice", "CONSUMER", 5, 6000), ("wine", "Wine", "CONSUMER", 25, 0),
        ("coffee_packaged", "Packaged coffee", "CONSUMER", 18, 500), ("tea_packaged", "Packaged tea", "CONSUMER", 12, 300),
        ("breakfast_cereal", "Breakfast cereal", "CONSUMER", 7, 200), ("cooking_oil_bottled", "Bottled cooking oil", "CONSUMER", 9, 200),
        ("packaged_rice", "Packaged rice", "CONSUMER", 8, 0),
    ]:
        s.good(gid, name, cat, price, "NORMAL", spoil)

    F = s.facility
    F("flour_mill", "Flour mill", "FACTORY", "FOOD", 480_000, 50, 3, 40_000, 260, 20.0, {"operator": 1})
    F("bakery", "Industrial bakery", "FACTORY", "FOOD", 360_000, 40, 4, 12_000, 220, 25.0, {"cook": 1})
    F("dairy_plant", "Dairy plant", "FACTORY", "FOOD", 640_000, 55, 3, 18_000, 330, 35.0, {"operator": 1})
    F("slaughterhouse", "Meat packing plant", "FACTORY", "FOOD", 700_000, 55, 3, 25_000, 360, 40.0, {"operator": 1})
    F("sugar_refinery", "Sugar refinery", "FACTORY", "FOOD", 800_000, 60, 2, 50_000, 380, 60.0, {"operator": 2})
    F("oil_press", "Oil press", "FACTORY", "FOOD", 450_000, 45, 2, 20_000, 240, 30.0, {"operator": 1})
    F("cannery", "Cannery", "FACTORY", "FOOD", 520_000, 45, 3, 30_000, 270, 28.0, {"operator": 1})
    F("food_factory", "Food factory", "FACTORY", "FOOD", 600_000, 50, 4, 25_000, 300, 30.0, {"operator": 1})
    F("confectionery_plant", "Confectionery plant", "FACTORY", "FOOD", 580_000, 50, 3, 20_000, 290, 28.0, {"operator": 1})
    F("bottling_plant", "Bottling plant", "FACTORY", "FOOD", 620_000, 50, 4, 30_000, 310, 32.0, {"operator": 1, "technician": 1})
    F("winery", "Winery", "FACTORY", "FOOD", 540_000, 55, 2, 30_000, 280, 12.0, {"cook": 1})
    F("roastery", "Coffee roastery", "FACTORY", "FOOD", 360_000, 40, 2, 15_000, 210, 14.0, {"operator": 1})

    R = s.recipe
    R("mill_flour", "Mill flour", "milling", "flour_mill", 3, {"wheat": 120, "packaging": 4}, {"flour": 1}, {"operator": 2}, energy=18)
    R("press_oil_soy", "Press soybean oil", "milling", "oil_press", 4, {"soybeans": 160, "packaging": 6}, {"vegetable_oil": 1}, {"operator": 2}, energy=22)
    R("polish_rice", "Polish rice", "milling", "flour_mill", 3, {"rice": 110, "packaging": 3}, {"rice_polished": 1}, {"operator": 2}, energy=12)
    R("bake_bread", "Bake bread", "milling", "bakery", 2, {"flour": 60, "water": 30, "packaging": 6, "sugar": 3}, {"bread": 1}, {"cook": 3}, energy=30)
    R("make_pasta", "Make pasta", "milling", "food_factory", 2, {"flour": 70, "water": 25, "packaging": 8}, {"pasta": 1}, {"operator": 2}, energy=26)
    R("make_cereal", "Make breakfast cereal", "milling", "food_factory", 3, {"corn": 90, "sugar": 10, "packaging": 12}, {"breakfast_cereal": 1}, {"operator": 2}, energy=28)
    R("package_rice", "Package rice", "milling", "food_factory", 1, {"rice_polished": 100, "packaging": 12}, {"packaged_rice": 1}, {"operator": 1, "laborer": 1}, energy=6)
    R("bottle_cooking_oil", "Bottle cooking oil", "milling", "bottling_plant", 1, {"vegetable_oil": 80, "packaging": 10}, {"cooking_oil_bottled": 1}, {"operator": 1, "laborer": 1}, energy=8)
    R("process_milk", "Bottle fresh milk", "dairy", "dairy_plant", 1, {"raw_milk": 400, "packaging": 20}, {"fresh_milk": 1}, {"operator": 2}, energy=22)
    R("make_cheese", "Make cheese", "dairy", "dairy_plant", 6, {"raw_milk": 500, "packaging": 12}, {"cheese": 1}, {"operator": 2}, energy=30)
    R("churn_butter", "Churn butter", "dairy", "dairy_plant", 2, {"raw_milk": 420, "packaging": 10}, {"butter": 1}, {"operator": 2}, energy=22)
    R("culture_yogurt", "Culture yogurt", "dairy", "dairy_plant", 3, {"raw_milk": 300, "sugar": 12, "packaging": 25}, {"yogurt": 1}, {"operator": 2}, energy=24)
    R("slaughter_cattle", "Process cattle", "meat", "slaughterhouse", 3, {"cattle": 20, "packaging": 25, "water": 200}, {"beef": 1, "hides": 0.15}, {"operator": 4}, energy=45, waste={"organic_waste": 90})
    R("slaughter_hogs", "Process hogs", "meat", "slaughterhouse", 3, {"hogs": 30, "packaging": 25, "water": 200}, {"pork": 1}, {"operator": 4}, energy=45, waste={"organic_waste": 80})
    R("process_poultry", "Process poultry", "meat", "slaughterhouse", 2, {"poultry": 400, "packaging": 30, "water": 150}, {"chicken_meat": 1}, {"operator": 3}, energy=35, waste={"organic_waste": 60})
    R("make_sausages", "Make sausages", "meat", "food_factory", 2, {"pork": 70, "beef": 20, "packaging": 18, "water": 20}, {"sausages": 1}, {"operator": 3}, energy=26)
    R("refine_sugar", "Refine sugar", "confectionery", "sugar_refinery", 4, {"sugar_beet": 600, "water": 400, "packaging": 8}, {"sugar": 1}, {"operator": 3}, energy=85, waste={"organic_waste": 40})
    R("grind_cocoa", "Grind cocoa", "confectionery", "confectionery_plant", 3, {"cocoa_beans": 100, "packaging": 4}, {"cocoa_mass": 1}, {"operator": 2}, energy=35)
    R("make_chocolate", "Make chocolate", "confectionery", "confectionery_plant", 3, {"cocoa_mass": 40, "sugar": 50, "raw_milk": 40, "packaging": 20}, {"chocolate": 1}, {"operator": 3}, energy=40)
    R("make_candy", "Make candy", "confectionery", "confectionery_plant", 2, {"sugar": 90, "packaging": 18}, {"candy": 1}, {"operator": 2}, energy=22)
    R("fry_chips", "Fry potato chips", "confectionery", "food_factory", 2, {"potatoes": 220, "vegetable_oil": 20, "packaging": 24}, {"potato_chips": 1}, {"operator": 3}, energy=40)
    R("can_vegetables", "Can vegetables", "preserved", "cannery", 2, {"tomatoes": 220, "packaging": 40, "water": 60}, {"canned_vegetables": 1}, {"operator": 3}, energy=30)
    R("can_fruit", "Can fruit", "preserved", "cannery", 2, {"apples": 200, "sugar": 20, "packaging": 40}, {"canned_fruit": 1}, {"operator": 3}, energy=30)
    R("make_frozen_meals", "Make frozen meals", "preserved", "food_factory", 3, {"chicken_meat": 40, "rice_polished": 40, "tomatoes": 40, "packaging": 60}, {"frozen_meals": 1}, {"operator": 3, "cook": 1}, energy=55)
    R("press_juice", "Press fruit juice", "beverages", "bottling_plant", 2, {"apples": 260, "water": 40}, {"fruit_juice_conc": 1}, {"operator": 2}, energy=26)
    R("bottle_juice", "Bottle juice", "beverages", "bottling_plant", 1, {"fruit_juice_conc": 60, "water": 300, "packaging": 60}, {"bottled_juice": 1}, {"operator": 2}, energy=24)
    R("brew_soft_drinks", "Produce soft drinks", "beverages", "bottling_plant", 1, {"sugar": 60, "water": 500, "packaging": 80}, {"soft_drinks": 1}, {"operator": 2}, energy=26)
    R("press_grapes", "Press grapes", "beverages", "winery", 4, {"grapes": 260, "water": 20}, {"grape_must": 1}, {"cook": 2}, energy=8)
    R("ferment_wine", "Ferment wine", "beverages", "winery", 30, {"grape_must": 60, "packaging": 25}, {"wine": 1}, {"cook": 2}, energy=15)
    R("roast_coffee", "Roast coffee", "beverages", "roastery", 2, {"coffee_beans": 150, "packaging": 6}, {"coffee_roasted": 1}, {"operator": 2}, energy=26, waste={"organic_waste": 10})
    R("package_coffee", "Package coffee", "beverages", "roastery", 1, {"coffee_roasted": 50, "packaging": 30}, {"coffee_packaged": 1}, {"operator": 1, "laborer": 1}, energy=8)
    R("package_tea", "Package tea", "beverages", "roastery", 2, {"tea_leaves": 120, "packaging": 40}, {"tea_packaged": 1}, {"operator": 2}, energy=10)
    # Demand
    s.dem("bread", 380, 320, 260, 0.35, 0.3, ["pasta", "packaged_rice"])
    s.dem("pasta", 150, 140, 110, 0.5, 0.4, ["bread", "packaged_rice"])
    s.dem("packaged_rice", 160, 120, 90, 0.5, 0.3, ["pasta", "bread"])
    s.dem("fresh_milk", 260, 240, 220, 0.45, 0.4, ["yogurt"], {"SUMMER": 1.1})
    s.dem("cheese", 55, 75, 95, 0.8, 0.9, ["butter"])
    s.dem("butter", 40, 48, 55, 0.7, 0.6, ["cooking_oil_bottled"])
    s.dem("yogurt", 70, 95, 110, 0.9, 0.8, ["fresh_milk"], {"SUMMER": 1.2, "WINTER": 0.9})
    s.dem("sausages", 60, 70, 75, 0.9, 0.7, ["frozen_meals"])
    s.dem("canned_vegetables", 80, 70, 55, 0.6, 0.3)
    s.dem("canned_fruit", 50, 55, 55, 0.8, 0.6)
    s.dem("frozen_meals", 35, 70, 95, 1.1, 1.1, ["canned_vegetables"])
    s.dem("chocolate", 30, 55, 75, 1.2, 1.1, ["candy"], {"WINTER": 1.2})
    s.dem("candy", 55, 60, 50, 1.2, 0.8, ["chocolate"])
    s.dem("potato_chips", 45, 60, 55, 1.2, 0.8)
    s.dem("soft_drinks", 220, 260, 240, 1.0, 0.6, ["bottled_juice"], {"SUMMER": 1.35, "WINTER": 0.8})
    s.dem("bottled_juice", 70, 120, 150, 1.1, 1.0, ["soft_drinks"], {"SUMMER": 1.25})
    s.dem("wine", 8, 30, 70, 1.3, 1.5, [], {}, 0.35)
    s.dem("coffee_packaged", 35, 55, 65, 0.7, 0.8, ["tea_packaged"])
    s.dem("tea_packaged", 25, 30, 32, 0.8, 0.6, ["coffee_packaged"])
    s.dem("breakfast_cereal", 45, 55, 50, 1.0, 0.7, ["bread"])
    s.dem("cooking_oil_bottled", 45, 50, 50, 0.5, 0.4, ["butter"])
    return s

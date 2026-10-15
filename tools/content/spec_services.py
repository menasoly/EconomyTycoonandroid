from contentgen import Spec

def build():
    s = Spec("services", "ServicesPack")
    s.industry("hospitality", "Hospitality", "HOSPITALITY", "Restaurants and hotels")
    s.industry("retail", "Retail", "RETAIL", "Stores and supermarkets")
    s.industry("ecommerce", "E-commerce", "ECOMMERCE", "Online fulfillment")
    s.industry("healthcare", "Healthcare", "HEALTHCARE", "Clinics and hospitals")
    s.industry("software", "Software", "SOFTWARE", "Software products and cloud")
    s.industry("telecom", "Telecommunications", "TELECOM", "Mobile and broadband")
    s.industry("media", "Media", "MEDIA", "Streaming and publishing")
    s.industry("entertainment", "Entertainment", "ENTERTAINMENT", "Games and venues")
    s.industry("transport", "Transportation", "TRANSPORTATION", "Passenger and freight carriage")
    s.industry("realestate", "Real estate", "REAL_ESTATE", "Property management")
    s.industry("finance_services", "Financial services", "FINANCIAL_SERVICES", "Banking and insurance")

    for gid, name, price in [
        ("restaurant_meal", "Restaurant meal", 18), ("fast_food_meal", "Fast food meal", 8), ("coffee_cup", "Coffee", 4), ("hotel_night", "Hotel night", 120),
        ("grocery_basket", "Grocery service", 1), ("clinic_visit", "Clinic visit", 90), ("hospital_stay", "Hospital day", 900), ("mobile_plan", "Mobile plan month", 30),
        ("broadband_plan", "Broadband plan month", 45), ("streaming_sub", "Streaming subscription", 12), ("cinema_ticket", "Cinema ticket", 13),
        ("game_license", "Game license", 40), ("software_license", "Software license", 120), ("cloud_compute", "Cloud compute hour", 2),
        ("passenger_trip", "Passenger trip", 25), ("air_ticket", "Air ticket", 220), ("parcel_delivery", "Parcel delivery", 6),
        ("rent_apartment", "Apartment rent month", 900), ("rent_office", "Office rent month", 4_000), ("bank_service", "Banking service", 5), ("insurance_policy", "Insurance policy month", 60),
    ]:
        s.good(gid, name, "SERVICE", price, "STABLE", 0, 0, 0, tradable=False)
    for gid, name, price in [("ad_impressions", "Ad impressions", 2), ("app_content", "Content library", 80), ("server_hardware", "Server hardware", 4_200), ("network_equipment", "Network equipment", 1_800), ("medical_equipment", "Medical equipment", 3_500)]:
        s.good(gid, name, "INDUSTRIAL", price, "NORMAL")

    F = s.facility
    F("restaurant", "Restaurant", "STORE", "HOSPITALITY", 650_000, 40, 3, 6_000, 480, 25.0, {"cook": 3, "cashier": 1})
    F("fast_food_outlet", "Fast food outlet", "STORE", "HOSPITALITY", 420_000, 35, 3, 5_000, 360, 22.0, {"cook": 2, "cashier": 2})
    F("cafe", "Cafe", "STORE", "HOSPITALITY", 260_000, 30, 2, 2_500, 220, 12.0, {"cook": 1, "cashier": 1})
    F("hotel", "Hotel", "HOTEL", "HOSPITALITY", 12_000_000, 160, 6, 20_000, 4_000, 120.0, {"clerk": 4, "laborer": 6, "manager": 1})
    F("supermarket", "Supermarket", "STORE", "RETAIL", 3_200_000, 70, 4, 60_000, 1_300, 90.0, {"cashier": 6, "sales": 4, "manager": 1})
    F("department_store", "Department store", "STORE", "RETAIL", 6_500_000, 90, 6, 100_000, 2_400, 140.0, {"sales": 14, "cashier": 6, "manager": 2})
    F("electronics_store", "Electronics store", "STORE", "RETAIL", 2_400_000, 60, 3, 30_000, 1_000, 60.0, {"sales": 6, "cashier": 2})
    F("pharmacy", "Pharmacy", "STORE", "RETAIL", 1_100_000, 45, 2, 12_000, 520, 30.0, {"sales": 3, "cashier": 1})
    F("fulfillment_center", "Fulfillment center", "WAREHOUSE", "ECOMMERCE", 14_000_000, 140, 4, 400_000, 4_200, 200.0, {"laborer": 30, "clerk": 4, "manager": 2}, tech="ecommerce_platform")
    F("clinic", "Clinic", "CLINIC", "HEALTHCARE", 3_500_000, 80, 4, 8_000, 1_600, 50.0, {"physician": 2, "nurse": 3, "clerk": 1})
    F("hospital", "Hospital", "CLINIC", "HEALTHCARE", 55_000_000, 260, 10, 60_000, 16_000, 400.0, {"physician": 20, "nurse": 40, "clerk": 6}, tech="hospital_administration")
    F("data_center", "Data center", "DATA_CENTER", "SOFTWARE", 28_000_000, 180, 4, 30_000, 8_000, 900.0, {"technician": 8, "engineer": 4}, tech="cloud_infrastructure")
    F("software_studio", "Software studio", "OFFICE", "SOFTWARE", 1_800_000, 60, 6, 6_000, 1_300, 30.0, {"manager": 1, "clerk": 1})
    F("telecom_hub", "Telecom hub", "OFFICE", "TELECOM", 22_000_000, 150, 4, 20_000, 6_500, 600.0, {"engineer": 6, "technician": 10}, tech="mobile_networks")
    F("media_studio", "Media studio", "STUDIO", "MEDIA", 8_000_000, 110, 4, 15_000, 3_000, 120.0, {"designer": 4, "clerk": 2})
    F("game_studio", "Game studio", "STUDIO", "ENTERTAINMENT", 4_800_000, 90, 5, 8_000, 2_200, 60.0, {"designer": 3, "manager": 1})
    F("cinema", "Cinema", "STUDIO", "ENTERTAINMENT", 5_200_000, 90, 4, 10_000, 1_800, 90.0, {"cashier": 3, "laborer": 3})
    F("bus_terminal", "Bus terminal", "TERMINAL", "TRANSPORTATION", 6_000_000, 90, 4, 20_000, 1_900, 60.0, {"clerk": 3, "driver": 6})
    F("airport_terminal", "Airport terminal", "TERMINAL", "TRANSPORTATION", 140_000_000, 400, 6, 100_000, 30_000, 1_200.0, {"clerk": 40, "pilot": 12, "manager": 6}, tech="commercial_aviation")
    F("courier_depot", "Courier depot", "WAREHOUSE", "LOGISTICS", 4_000_000, 70, 4, 80_000, 1_600, 60.0, {"driver": 8, "clerk": 2})
    F("apartment_block", "Apartment block", "OFFICE", "REAL_ESTATE", 9_000_000, 160, 6, 5_000, 1_800, 80.0, {"laborer": 3, "clerk": 1})
    F("office_tower", "Office tower", "OFFICE", "REAL_ESTATE", 60_000_000, 300, 8, 20_000, 9_000, 500.0, {"laborer": 10, "clerk": 4, "manager": 1})
    F("bank_branch", "Bank branch", "BANK_BRANCH", "FINANCIAL_SERVICES", 2_600_000, 60, 3, 4_000, 1_100, 40.0, {"clerk": 4, "analyst": 1, "manager": 1})
    F("insurance_office", "Insurance office", "OFFICE", "FINANCIAL_SERVICES", 3_000_000, 60, 4, 4_000, 1_200, 40.0, {"clerk": 4, "analyst": 2})

    R = s.recipe
    R("serve_restaurant", "Serve restaurant meals", "hospitality", "restaurant", 1, {"bread": 40, "beef": 12, "tomatoes": 20, "cooking_oil_bottled": 6, "packaging": 8}, {"restaurant_meal": 80}, {"cook": 3, "cashier": 1}, energy=40, fixed=True, margin=0.28)
    R("serve_fast_food", "Serve fast food", "hospitality", "fast_food_outlet", 1, {"bread": 36, "chicken_meat": 10, "potatoes": 20, "cooking_oil_bottled": 6, "packaging": 24}, {"fast_food_meal": 160}, {"cook": 2, "cashier": 2}, energy=36, fixed=True, margin=0.28)
    R("brew_coffee", "Brew coffee", "hospitality", "cafe", 1, {"coffee_packaged": 8, "fresh_milk": 40, "sugar": 4, "packaging": 20}, {"coffee_cup": 220}, {"cook": 1, "cashier": 1}, energy=16, fixed=True, margin=0.35)
    R("run_hotel", "Run hotel rooms", "hospitality", "hotel", 1, {"towels": 6, "bedding_set": 4, "detergent": 20, "dish_soap": 6, "paper_goods": 30}, {"hotel_night": 120}, {"clerk": 4, "laborer": 6}, energy=240, fixed=True, margin=0.3)
    R("run_supermarket", "Supermarket service", "retail", "supermarket", 1, {"packaging": 80, "paper_goods": 40, "office_supplies": 6}, {"grocery_basket": 6_000}, {"cashier": 6, "sales": 4}, energy=120, fixed=True, margin=0.22)
    R("treat_clinic", "Treat clinic patients", "healthcare", "clinic", 1, {"bandages": 40, "painkillers": 40, "diagnostic_kits": 6, "medical_equipment": 0.01}, {"clinic_visit": 60}, {"physician": 2, "nurse": 3}, energy=60, fixed=True, margin=0.2)
    R("run_hospital", "Run hospital wards", "healthcare", "hospital", 1, {"bandages": 200, "painkillers": 200, "antibiotics": 100, "vaccines": 20, "medical_equipment": 0.05}, {"hospital_stay": 90}, {"physician": 20, "nurse": 40}, energy=800, fixed=True, margin=0.2)
    R("sell_licenses", "Develop and sell software licenses", "software", "software_studio", 5, {"office_supplies": 8, "laptop": 0.2}, {"software_license": 400}, {"programmer": 6, "manager": 1}, energy=20, fixed=True, margin=0.45)
    R("host_cloud", "Host cloud compute", "software", "data_center", 1, {"server_hardware": 0.2, "spare_parts": 20, "electricity": 900}, {"cloud_compute": 40_000}, {"technician": 8, "engineer": 4}, energy=900, fixed=True, margin=0.35, tech="cloud_infrastructure")
    R("sell_mobile_plans", "Sell mobile plans", "telecom", "telecom_hub", 1, {"network_equipment": 0.05, "spare_parts": 20}, {"mobile_plan": 4_000}, {"engineer": 6, "technician": 10}, energy=600, fixed=True, margin=0.35, tech="mobile_networks")
    R("sell_broadband", "Sell broadband plans", "telecom", "telecom_hub", 1, {"network_equipment": 0.08, "spare_parts": 20}, {"broadband_plan": 2_500}, {"engineer": 4, "technician": 8}, energy=500, fixed=True, margin=0.3, tech="mobile_networks")
    R("stream_media", "Produce streaming content", "media", "media_studio", 5, {"app_content": 6, "server_hardware": 0.05}, {"streaming_sub": 18_000}, {"designer": 4, "clerk": 2}, energy=120, fixed=True, margin=0.3)
    R("publish_games", "Publish games", "entertainment", "game_studio", 5, {"laptop": 0.2, "office_supplies": 6}, {"game_license": 1_500}, {"designer": 3, "programmer": 3, "manager": 1}, energy=40, fixed=True, margin=0.4)
    R("show_films", "Show films", "entertainment", "cinema", 1, {"soft_drinks": 100, "potato_chips": 80, "candy": 60, "electricity": 80}, {"cinema_ticket": 600}, {"cashier": 3, "laborer": 3}, energy=90, fixed=True, margin=0.3)
    R("run_buses", "Run city buses", "transport", "bus_terminal", 1, {"diesel": 400, "tires": 2, "spare_parts": 20}, {"passenger_trip": 4_000}, {"driver": 6, "clerk": 3}, energy=40, fixed=True, margin=0.2)
    R("fly_passengers", "Operate flights", "transport", "airport_terminal", 1, {"kerosene": 4_000, "spare_parts": 200, "restaurant_meal": 400}, {"air_ticket": 2_400}, {"pilot": 12, "clerk": 40}, energy=1_200, fixed=True, margin=0.15, tech="commercial_aviation")
    R("deliver_parcels", "Deliver parcels", "ecommerce", "courier_depot", 1, {"diesel": 250, "packaging": 300, "pallets": 20}, {"parcel_delivery": 6_000}, {"driver": 8, "clerk": 2}, energy=60, fixed=True, margin=0.22)
    R("fulfill_orders", "Fulfill online orders", "ecommerce", "fulfillment_center", 1, {"packaging": 600, "pallets": 40, "conveyor_system": 0.002}, {"parcel_delivery": 12_000}, {"laborer": 30, "clerk": 4}, energy=200, fixed=True, margin=0.2, tech="ecommerce_platform")
    R("rent_apartments", "Rent apartments", "realestate", "apartment_block", 1, {"detergent": 10, "paper_goods": 10, "spare_parts": 6}, {"rent_apartment": 120}, {"laborer": 3, "clerk": 1}, energy=80, fixed=True, margin=0.3)
    R("rent_offices", "Rent offices", "realestate", "office_tower", 1, {"paper_goods": 40, "spare_parts": 30, "detergent": 20}, {"rent_office": 400}, {"laborer": 10, "clerk": 4}, energy=500, fixed=True, margin=0.3)
    R("provide_banking", "Provide banking services", "finance_services", "bank_branch", 1, {"office_supplies": 20, "paper_goods": 20}, {"bank_service": 2_000}, {"clerk": 4, "analyst": 1}, energy=40, fixed=True, margin=0.4)
    R("write_policies", "Write insurance policies", "finance_services", "insurance_office", 1, {"office_supplies": 20, "paper_goods": 20}, {"insurance_policy": 900}, {"clerk": 4, "analyst": 2}, energy=40, fixed=True, margin=0.4)
    s.good("medical_equipment_kit", "Medical equipment kit", "INDUSTRIAL", 900, "NORMAL")

    s.tech("ecommerce_platform", "E-commerce platform", "RETAIL", 2, 1_200_000, 120, facilities=["fulfillment_center"], recipes=["fulfill_orders"])
    s.tech("hospital_administration", "Hospital administration", "MANAGEMENT", 3, 4_000_000, 200, facilities=["hospital"], recipes=["run_hospital"])
    s.tech("cloud_infrastructure", "Cloud infrastructure", "DIGITAL", 3, 6_000_000, 240, facilities=["data_center"], recipes=["host_cloud"])
    s.tech("mobile_networks", "Mobile networks", "DIGITAL", 3, 8_000_000, 260, facilities=["telecom_hub"], recipes=["sell_mobile_plans", "sell_broadband"])
    s.tech("commercial_aviation", "Commercial aviation", "LOGISTICS", 4, 22_000_000, 420, requires=["aerospace_engineering"], facilities=["airport_terminal"], recipes=["fly_passengers"])
    s.tech("loyalty_programs", "Loyalty programs", "RETAIL", 1, 250_000, 60, effects=[("YIELD_BONUS", 0.03, "retail", None)])
    s.tech("point_of_sale", "Point-of-sale systems", "RETAIL", 2, 450_000, 80, requires=["loyalty_programs"], effects=[("LABOR_SAVINGS", 0.08, "retail", None)])
    s.tech("route_optimization", "Route optimization", "LOGISTICS", 2, 800_000, 100, effects=[("TRANSPORT_SAVINGS", 0.08, None, None)])
    s.tech("telehealth", "Telehealth", "DIGITAL", 2, 900_000, 100, effects=[("THROUGHPUT_BONUS", 0.10, "healthcare", None)])

    D = s.dem
    D("restaurant_meal", 40, 120, 220, 1.1, 1.3, ["fast_food_meal"])
    D("fast_food_meal", 160, 200, 180, 0.8, 0.5, ["restaurant_meal"])
    D("coffee_cup", 120, 260, 320, 0.7, 0.6)
    D("hotel_night", 2, 6, 14, 1.2, 1.5, [], {"SUMMER": 1.6, "WINTER": 0.8})
    D("grocery_basket", 700, 800, 820, 0.2, 0.2)
    D("clinic_visit", 30, 40, 44, 0.3, 0.2)
    D("hospital_stay", 2, 3, 3, 0.2, 0.2)
    D("mobile_plan", 700, 900, 1000, 0.5, 0.3)
    D("broadband_plan", 400, 700, 900, 0.6, 0.5)
    D("streaming_sub", 180, 420, 600, 0.9, 0.9)
    D("cinema_ticket", 30, 60, 90, 1.0, 1.0, [], {"SUMMER": 1.3})
    D("game_license", 20, 50, 80, 1.0, 1.0)
    D("passenger_trip", 160, 120, 90, 0.8, -0.2)
    D("air_ticket", 2, 10, 40, 1.3, 1.6, [], {"SUMMER": 1.5})
    D("parcel_delivery", 40, 120, 220, 0.8, 1.0)
    D("rent_apartment", 300, 330, 340, 0.4, 0.4)
    return s

package app.many.economytycoon.content

/** Industry families supported by the shared engines. Families are data groupings, not code paths. */
enum class IndustryFamily(val displayName: String) {
    AGRICULTURE("Agriculture"),
    MINING("Mining"),
    ENERGY("Energy"),
    OIL_AND_GAS("Oil and gas"),
    CHEMICALS("Chemicals"),
    METALS("Metals"),
    CONSTRUCTION("Construction"),
    AUTOMOTIVE("Automotive"),
    AEROSPACE("Aerospace"),
    ELECTRONICS("Electronics"),
    SEMICONDUCTORS("Semiconductor manufacturing"),
    FOOD("Food production"),
    PHARMACEUTICALS("Pharmaceuticals"),
    TEXTILES("Textiles"),
    CONSUMER_GOODS("Consumer goods"),
    FURNITURE("Furniture"),
    TRANSPORTATION("Transportation"),
    LOGISTICS("Logistics"),
    RETAIL("Retail"),
    ECOMMERCE("E-commerce"),
    HOSPITALITY("Hospitality"),
    ENTERTAINMENT("Entertainment"),
    MEDIA("Media"),
    TELECOM("Telecommunications"),
    SOFTWARE("Software"),
    FINANCIAL_SERVICES("Financial services"),
    HEALTHCARE("Healthcare"),
    REAL_ESTATE("Real estate"),
    ROBOTICS("Robotics"),
    RENEWABLE_ENERGY("Renewable energy"),
}

enum class GoodCategory {
    /** Extracted or grown from deposits and land. */
    RAW,
    /** Output of one recipe, input to another. */
    INTERMEDIATE,
    /** Sold to consumers. */
    CONSUMER,
    /** Sold to businesses as capital or supplies. */
    INDUSTRIAL,
    /** Energy carriers such as electricity or fuel. */
    ENERGY,
    /** Capacity-based service output that cannot be stored. */
    SERVICE,
    /** Byproducts that cost money to dispose of. */
    WASTE,
}

/** How volatile a good's price tends to be; controls the per-tick price move cap and noise. */
enum class VolatilityClass(val maxMovePpm: Long, val noisePpm: Long) {
    STABLE(20_000, 2_000),
    NORMAL(50_000, 6_000),
    VOLATILE(100_000, 15_000),
    SPECULATIVE(180_000, 30_000),
}

enum class TransportMode { ROAD, RAIL, SEA, AIR }

/** Income bands used by demand and workforce models. */
enum class IncomeBand { LOW, MIDDLE, HIGH }

/** Skill band of workers; higher bands cost more and produce more. */
enum class SkillBand { UNSKILLED, SKILLED, SPECIALIST, EXPERT }

enum class FacilityClass {
    FARM, MINE, WELL, POWER_PLANT, FACTORY, REFINERY, LABORATORY, WAREHOUSE,
    STORE, OFFICE, DATA_CENTER, HOTEL, PORT, TERMINAL, CLINIC, STUDIO, BANK_BRANCH,
}

enum class TechCategory { PRODUCTION, LOGISTICS, ENERGY, MATERIALS, DIGITAL, MANAGEMENT, RETAIL, FINANCE }

enum class EffectKind {
    /** Multiplies yield of matching recipes. */
    YIELD_BONUS,
    /** Reduces input consumption of matching recipes. */
    INPUT_SAVINGS,
    /** Reduces energy use. */
    ENERGY_SAVINGS,
    /** Increases line throughput. */
    THROUGHPUT_BONUS,
    /** Reduces defect rate. */
    QUALITY_BONUS,
    /** Reduces labor requirement. */
    LABOR_SAVINGS,
    /** Reduces transport cost. */
    TRANSPORT_SAVINGS,
    /** Increases storage capacity. */
    STORAGE_BONUS,
}

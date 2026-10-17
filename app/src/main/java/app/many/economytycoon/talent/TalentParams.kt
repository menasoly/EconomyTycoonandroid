package app.many.economytycoon.talent

/** Every tunable of the talent module in one place, so a scenario can change them together. */
data class TalentParams(
    val market: MarketParams = MarketParams(),
    val dynamics: SkillDynamicsParams = SkillDynamicsParams(),
    val education: EducationParams = EducationParams(),
    val training: TrainingParams = TrainingParams(),
    val migration: MigrationParams = MigrationParams(),
    val poaching: PoachParams = PoachParams(),
    val brand: BrandParams = BrandParams(),
    val careers: CareerParams = CareerParams(),
    val succession: SuccessionParams = SuccessionParams(),
    val providers: ProviderParams = ProviderParams(),
)

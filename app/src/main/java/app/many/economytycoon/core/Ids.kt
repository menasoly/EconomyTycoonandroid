package app.many.economytycoon.core

/**
 * Strongly typed identifiers. Runtime ids are 64-bit integers allocated by [IdAllocator];
 * content ids are stable string keys defined in content packs.
 */
@JvmInline value class CompanyId(val value: Long) : Comparable<CompanyId> {
    override fun compareTo(other: CompanyId) = value.compareTo(other.value)
    override fun toString() = "Company#$value"
}

@JvmInline value class PlayerId(val value: Long) : Comparable<PlayerId> {
    override fun compareTo(other: PlayerId) = value.compareTo(other.value)
    override fun toString() = "Player#$value"
}

@JvmInline value class GroupId(val value: Long) : Comparable<GroupId> {
    override fun compareTo(other: GroupId) = value.compareTo(other.value)
    override fun toString() = "Group#$value"
}

@JvmInline value class FacilityId(val value: Long) : Comparable<FacilityId> {
    override fun compareTo(other: FacilityId) = value.compareTo(other.value)
    override fun toString() = "Facility#$value"
}

@JvmInline value class LineId(val value: Long) : Comparable<LineId> {
    override fun compareTo(other: LineId) = value.compareTo(other.value)
    override fun toString() = "Line#$value"
}

@JvmInline value class MachineId(val value: Long) : Comparable<MachineId> {
    override fun compareTo(other: MachineId) = value.compareTo(other.value)
    override fun toString() = "Machine#$value"
}

@JvmInline value class WarehouseId(val value: Long) : Comparable<WarehouseId> {
    override fun compareTo(other: WarehouseId) = value.compareTo(other.value)
    override fun toString() = "Warehouse#$value"
}

@JvmInline value class ProjectId(val value: Long) : Comparable<ProjectId> {
    override fun compareTo(other: ProjectId) = value.compareTo(other.value)
    override fun toString() = "Project#$value"
}

@JvmInline value class RegionId(val value: Long) : Comparable<RegionId> {
    override fun compareTo(other: RegionId) = value.compareTo(other.value)
    override fun toString() = "Region#$value"
}

@JvmInline value class CountryId(val value: Long) : Comparable<CountryId> {
    override fun compareTo(other: CountryId) = value.compareTo(other.value)
    override fun toString() = "Country#$value"
}

@JvmInline value class CityId(val value: Long) : Comparable<CityId> {
    override fun compareTo(other: CityId) = value.compareTo(other.value)
    override fun toString() = "City#$value"
}

@JvmInline value class ParcelId(val value: Long) : Comparable<ParcelId> {
    override fun compareTo(other: ParcelId) = value.compareTo(other.value)
    override fun toString() = "Parcel#$value"
}

@JvmInline value class SegmentId(val value: Long) : Comparable<SegmentId> {
    override fun compareTo(other: SegmentId) = value.compareTo(other.value)
    override fun toString() = "Segment#$value"
}

@JvmInline value class DepositId(val value: Long) : Comparable<DepositId> {
    override fun compareTo(other: DepositId) = value.compareTo(other.value)
    override fun toString() = "Deposit#$value"
}

@JvmInline value class MarketId(val value: Long) : Comparable<MarketId> {
    override fun compareTo(other: MarketId) = value.compareTo(other.value)
    override fun toString() = "Market#$value"
}

@JvmInline value class OrderId(val value: Long) : Comparable<OrderId> {
    override fun compareTo(other: OrderId) = value.compareTo(other.value)
    override fun toString() = "Order#$value"
}

@JvmInline value class TradeId(val value: Long) : Comparable<TradeId> {
    override fun compareTo(other: TradeId) = value.compareTo(other.value)
    override fun toString() = "Trade#$value"
}

@JvmInline value class AccountId(val value: Long) : Comparable<AccountId> {
    override fun compareTo(other: AccountId) = value.compareTo(other.value)
    override fun toString() = "Account#$value"
}

@JvmInline value class TransactionId(val value: Long) : Comparable<TransactionId> {
    override fun compareTo(other: TransactionId) = value.compareTo(other.value)
    override fun toString() = "Tx#$value"
}

@JvmInline value class LoanId(val value: Long) : Comparable<LoanId> {
    override fun compareTo(other: LoanId) = value.compareTo(other.value)
    override fun toString() = "Loan#$value"
}

@JvmInline value class SecurityId(val value: Long) : Comparable<SecurityId> {
    override fun compareTo(other: SecurityId) = value.compareTo(other.value)
    override fun toString() = "Security#$value"
}

@JvmInline value class ShipmentId(val value: Long) : Comparable<ShipmentId> {
    override fun compareTo(other: ShipmentId) = value.compareTo(other.value)
    override fun toString() = "Shipment#$value"
}

@JvmInline value class VehicleId(val value: Long) : Comparable<VehicleId> {
    override fun compareTo(other: VehicleId) = value.compareTo(other.value)
    override fun toString() = "Vehicle#$value"
}

@JvmInline value class ContractId(val value: Long) : Comparable<ContractId> {
    override fun compareTo(other: ContractId) = value.compareTo(other.value)
    override fun toString() = "Contract#$value"
}

@JvmInline value class PolicyId(val value: Long) : Comparable<PolicyId> {
    override fun compareTo(other: PolicyId) = value.compareTo(other.value)
    override fun toString() = "Policy#$value"
}

@JvmInline value class TenderId(val value: Long) : Comparable<TenderId> {
    override fun compareTo(other: TenderId) = value.compareTo(other.value)
    override fun toString() = "Tender#$value"
}

@JvmInline value class GrantId(val value: Long) : Comparable<GrantId> {
    override fun compareTo(other: GrantId) = value.compareTo(other.value)
    override fun toString() = "Grant#$value"
}

@JvmInline value class CohortId(val value: Long) : Comparable<CohortId> {
    override fun compareTo(other: CohortId) = value.compareTo(other.value)
    override fun toString() = "Cohort#$value"
}

@JvmInline value class DepartmentId(val value: Long) : Comparable<DepartmentId> {
    override fun compareTo(other: DepartmentId) = value.compareTo(other.value)
    override fun toString() = "Department#$value"
}

@JvmInline value class ProductId(val value: Long) : Comparable<ProductId> {
    override fun compareTo(other: ProductId) = value.compareTo(other.value)
    override fun toString() = "Product#$value"
}

@JvmInline value class EventId(val value: Long) : Comparable<EventId> {
    override fun compareTo(other: EventId) = value.compareTo(other.value)
    override fun toString() = "Event#$value"
}

@JvmInline value class AgentId(val value: Long) : Comparable<AgentId> {
    override fun compareTo(other: AgentId) = value.compareTo(other.value)
    override fun toString() = "Agent#$value"
}

@JvmInline value class UnionId(val value: Long) : Comparable<UnionId> {
    override fun compareTo(other: UnionId) = value.compareTo(other.value)
    override fun toString() = "Union#$value"
}

@JvmInline value class BondId(val value: Long) : Comparable<BondId> {
    override fun compareTo(other: BondId) = value.compareTo(other.value)
    override fun toString() = "Bond#$value"
}

@JvmInline value class RegulationId(val value: Long) : Comparable<RegulationId> {
    override fun compareTo(other: RegulationId) = value.compareTo(other.value)
    override fun toString() = "Regulation#$value"
}

/** Stable content keys. The string is the identity and must never change once released. */
@JvmInline value class GoodId(val key: String) : Comparable<GoodId> {
    override fun compareTo(other: GoodId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class RecipeId(val key: String) : Comparable<RecipeId> {
    override fun compareTo(other: RecipeId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class FacilityTypeId(val key: String) : Comparable<FacilityTypeId> {
    override fun compareTo(other: FacilityTypeId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class IndustryId(val key: String) : Comparable<IndustryId> {
    override fun compareTo(other: IndustryId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class TechId(val key: String) : Comparable<TechId> {
    override fun compareTo(other: TechId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class RoleId(val key: String) : Comparable<RoleId> {
    override fun compareTo(other: RoleId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class MachineTypeId(val key: String) : Comparable<MachineTypeId> {
    override fun compareTo(other: MachineTypeId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class VehicleTypeId(val key: String) : Comparable<VehicleTypeId> {
    override fun compareTo(other: VehicleTypeId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class CurrencyId(val key: String) : Comparable<CurrencyId> {
    override fun compareTo(other: CurrencyId) = key.compareTo(other.key)
    override fun toString() = key
}

@JvmInline value class FuturesId(val value: Long) : Comparable<FuturesId> {
    override fun compareTo(other: FuturesId) = value.compareTo(other.value)
    override fun toString() = "Future#$value"
}

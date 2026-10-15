package app.many.economytycoon.core

/**
 * Deterministic id allocator. Ids are never reused. One allocator exists per entity kind so that
 * adding a new entity kind never shifts the ids of another.
 */
class IdAllocator(private var next: Long = 1L) {
    fun allocate(): Long = next++

    /** Next value that [allocate] will return; persisted with snapshots. */
    fun peek(): Long = next

    fun restore(nextValue: Long) {
        require(nextValue >= 1L) { "next id must be positive" }
        next = nextValue
    }
}

/** Named allocators for every runtime entity kind. */
class IdAllocators {
    private val byKind = LinkedHashMap<String, IdAllocator>()

    private fun of(kind: String) = byKind.getOrPut(kind) { IdAllocator() }

    fun company() = CompanyId(of("company").allocate())
    fun group() = GroupId(of("group").allocate())
    fun facility() = FacilityId(of("facility").allocate())
    fun line() = LineId(of("line").allocate())
    fun machine() = MachineId(of("machine").allocate())
    fun warehouse() = WarehouseId(of("warehouse").allocate())
    fun project() = ProjectId(of("project").allocate())
    fun region() = RegionId(of("region").allocate())
    fun country() = CountryId(of("country").allocate())
    fun city() = CityId(of("city").allocate())
    fun parcel() = ParcelId(of("parcel").allocate())
    fun segment() = SegmentId(of("segment").allocate())
    fun deposit() = DepositId(of("deposit").allocate())
    fun market() = MarketId(of("market").allocate())
    fun order() = OrderId(of("order").allocate())
    fun trade() = TradeId(of("trade").allocate())
    fun account() = AccountId(of("account").allocate())
    fun transaction() = TransactionId(of("transaction").allocate())
    fun loan() = LoanId(of("loan").allocate())
    fun security() = SecurityId(of("security").allocate())
    fun shipment() = ShipmentId(of("shipment").allocate())
    fun vehicle() = VehicleId(of("vehicle").allocate())
    fun contract() = ContractId(of("contract").allocate())
    fun policy() = PolicyId(of("policy").allocate())
    fun tender() = TenderId(of("tender").allocate())
    fun grant() = GrantId(of("grant").allocate())
    fun cohort() = CohortId(of("cohort").allocate())
    fun department() = DepartmentId(of("department").allocate())
    fun product() = ProductId(of("product").allocate())
    fun event() = EventId(of("event").allocate())
    fun agent() = AgentId(of("agent").allocate())
    fun regulation() = RegulationId(of("regulation").allocate())
    fun bond() = BondId(of("bond").allocate())
    fun union() = UnionId(of("union").allocate())
    fun future() = FuturesId(of("future").allocate())

    /** Snapshot of counters in a stable order, for persistence. */
    fun snapshot(): Map<String, Long> = byKind.toSortedMap().mapValues { it.value.peek() }

    fun restore(counters: Map<String, Long>) {
        byKind.clear()
        for ((kind, next) in counters.toSortedMap()) {
            byKind[kind] = IdAllocator(next)
        }
    }
}

# Command Catalog

Commands are the only way players and AI change simulation state. Each is validated, then applied in the command application phase (3) in total order (tick, issuer class, sequence).

## Envelope

`Command(id, issuer: CompanyId|PlayerId, tick, sequence, payload)`. Result: `Accepted(effects)` or `Rejected(rule, message)`.

## Catalog

| Command | Module | Preconditions (rejection rule) | Effects |
|---|---|---|---|
| FoundCompany | companies | Capital available; name unique; region allows | Creates company and accounts, posts capital |
| CreateSubsidiary | companies | Parent solvent; ownership acyclic | New company linked to parent |
| Acquire | companies | Target valid; funds reserved; antitrust not violated | Ownership transfer via ledger |
| SetObjective | companies | Valid objective | Updates strategy goals |
| BuildFacility | production | Land owned or leased; funds; permits; technology | Starts construction project, reserves funds |
| ConfigureLine | production | Recipe unlocked; facility type matches | Sets recipe and priority |
| StartMaintenance | production | Funds; machine exists | Schedules maintenance |
| UpgradeMachine | production | Technology; funds | Replaces machine on completion |
| PlaceOrder | markets | Reservation succeeds (goods or funds); within rate limit | Order enters book |
| CancelOrder | markets | Issuer owns order | Releases reservation |
| SetPrice | markets | Within allowed range | Updates listing price |
| SetMarketing | markets | Funds | Allocates campaign budget |
| ProposeContract | logistics | Counterparty valid; terms bounded | Offer created; accepted contracts schedule deliveries |
| AssignRoute | logistics | Vehicle and route valid | Sets shipment routing |
| BuyVehicle | logistics | Funds | Adds vehicle |
| HireWorkers | workforce | Budget; labor supply; facility capacity | Opens positions |
| SetWage | workforce | At or above legal minimum | Updates cohort wage |
| StartTraining | workforce | Funds | Raises skill over time |
| Layoff | workforce | Severance funded | Reduces cohort, posts severance |
| TakeLoan | finance | Credit limit; collateral | Posts loan and deposit |
| RepayLoan | finance | Funds | Reduces principal |
| IssueShares | finance | Governance allows | Posts equity |
| PayDividend | finance | Retained earnings and cash | Posts payout |
| StartResearch | research | Funds; prerequisites | Creates project |
| BuyParcel / LeaseParcel | world | Funds; zoning | Transfers or leases parcel |

## Rules

- A command never carries computed balances or results; only intent.
- AI commands use the same types and validation as player commands.
- Rejections are data (typed rule ids) so UI and AI can react.

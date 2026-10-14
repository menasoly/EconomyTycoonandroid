# Screen Specifications

Every screen follows the template. Common rules: TalkBack labels for every control and chart (with data table alternative), 48dp touch targets, text scaling to 200%, color-independent encoding, reduced-motion support. State owners hold screen state only; authoritative data comes from session read models. Commands go through the session and rejections are displayed using the shared rejection presenter.

## 1. Dashboard

- Purpose: Show company health at a glance and the next actions.
- User story: As a player I see cash, profit trend and alerts so I know what needs attention.
- UI components: KPI tiles, alert list, quick actions, mini charts
- State ownership: DashboardViewModel (selected company, alert filter)
- Route: `dashboard` (identifier `dashboard`)
- Interactions: Dismiss alert, jump to related screen
- Validation: none
- Empty state: No company yet: prompt to found one
- Loading state: Skeleton tiles
- Error state: Read model stale: show last tick and retry
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Two-pane: KPIs left, alerts right
- Performance: Throttle KPI updates to once per UI second

## 2. Company overview

- Purpose: Show structure, finances and operations of one company.
- User story: As a player I inspect divisions and drill into problem areas.
- UI components: Header card, division list, ratio chips, tabs
- State ownership: CompanyViewModel
- Route: `companies/{id}` (identifier `companies/{id}`)
- Interactions: Switch company, open division
- Validation: none
- Empty state: No divisions: show create-first-facility CTA
- Loading state: Skeleton list
- Error state: Company removed: navigate back with message
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Lazy lists, diff-based updates

## 3. Business creation wizard

- Purpose: Create a company step by step.
- User story: As a player I choose industry, region and funding and see the plan before confirming.
- UI components: Stepper, option cards, cost summary
- State ownership: WizardViewModel (draft only)
- Route: `companies/new` (identifier `companies/new`)
- Interactions: Next/back, change option, confirm
- Validation: Funds sufficient; name unique; region permits industry
- Empty state: Nothing selected: disable next
- Loading state: Spinner on confirm only
- Error state: Show rejection rule from FoundCompany
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Summary side panel
- Performance: Static; no heavy lists

## 4. Factory construction

- Purpose: Place and configure a new facility.
- User story: As a player I pick type and location and see cost, time and staffing before building.
- UI components: Map or list picker, option panel, cost breakdown
- State ownership: ConstructionViewModel
- Route: `operations/build` (identifier `operations/build`)
- Interactions: Select site, choose type, confirm
- Validation: Land access, permits, funds, technology
- Empty state: No sites available: explain how to acquire land
- Loading state: Map tiles loading
- Error state: BuildFacility rejection with fix hint
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Map plus side panel
- Performance: Viewport-limited map queries

## 5. Factory operations

- Purpose: Monitor and tune one facility.
- User story: As a player I see bottlenecks and fix them quickly.
- UI components: Line cards with status, utilization bars, input/output stocks, alerts
- State ownership: FacilityViewModel
- Route: `operations/facility/{id}` (identifier `operations/facility/{id}`)
- Interactions: Open line, change priority, start maintenance
- Validation: Priority values within range
- Empty state: No lines: CTA to add a line
- Loading state: Skeleton cards
- Error state: Blocked cause explained per line
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Per-line state diffing

## 6. Production-line editor

- Purpose: Configure a line's recipe, staffing and schedule.
- User story: As a player I choose a recipe and see projected output and margin.
- UI components: Recipe picker, staffing sliders, forecast panel
- State ownership: LineEditorViewModel (draft)
- Route: `operations/line/{id}` (identifier `operations/line/{id}`)
- Interactions: Pick recipe, set staffing, apply
- Validation: Recipe unlocked; staffing within limits
- Empty state: No recipes unlocked: link to research
- Loading state: Skeleton
- Error state: Rejection shown inline
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Canvas plus inspector
- Performance: Forecast computed off main thread

## 7. Supply-chain visualization

- Purpose: Show goods flows between facilities and markets.
- User story: As a player I find breaks in my supply chain.
- UI components: Flow graph, node details, filters
- State ownership: SupplyViewModel
- Route: `operations/supply` (identifier `operations/supply`)
- Interactions: Pan, zoom, select node, filter good
- Validation: none
- Empty state: No flows: explain why
- Loading state: Graph layout progress
- Error state: Layout failed: fall back to list
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Full-width canvas
- Performance: Cap visible nodes, aggregate beyond

## 8. Logistics map

- Purpose: Show routes, vehicles and shipments on a map.
- User story: As a player I spot congestion and delays.
- UI components: Map, route layers, shipment list
- State ownership: LogisticsViewModel
- Route: `world/logistics` (identifier `world/logistics`)
- Interactions: Select route, filter mode, assign vehicle
- Validation: Vehicle capacity respected
- Empty state: No vehicles: CTA to buy
- Loading state: Map tiles loading
- Error state: Route unavailable: show disruption
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Map plus list
- Performance: Sampled markers, viewport culling

## 9. Employee management

- Purpose: Manage cohorts, hiring and wages.
- User story: As a player I balance staffing, cost and morale.
- UI components: Cohort table, hiring controls, morale indicators
- State ownership: WorkforceViewModel
- Route: `operations/workforce` (identifier `operations/workforce`)
- Interactions: Hire, change wage, train
- Validation: Wage >= legal minimum; budget
- Empty state: No employees: CTA to hire
- Loading state: Skeleton table
- Error state: Shortage message with market wage hint
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Paged tables

## 10. Financial dashboard

- Purpose: Show statements, cash flow, debt and forecasts.
- User story: As a player I understand profit, cash and risk.
- UI components: Statement tabs, charts, ratio table, debt schedule
- State ownership: FinanceViewModel
- Route: `finance` (identifier `finance`)
- Interactions: Switch period, drill into account
- Validation: none
- Empty state: No history: show first-period notice
- Loading state: Skeleton charts
- Error state: Statement missing: rebuild projection
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Multi-chart grid
- Performance: Statements cached per period

## 11. Market analysis

- Purpose: Analyze price, volume and competition for a good.
- User story: As a player I decide where and at what price to sell.
- UI components: Price chart, order book summary, competitor table, forecast
- State ownership: MarketViewModel
- Route: `markets/{good}` (identifier `markets/{good}`)
- Interactions: Change range, compare regions
- Validation: none
- Empty state: No trades yet: explain
- Loading state: Skeleton chart
- Error state: Data gap marker
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Chart plus book
- Performance: Downsample series

## 12. Pricing management

- Purpose: Set and automate prices.
- User story: As a player I set prices and rules and preview impact.
- UI components: Price table, rule editor, demand preview
- State ownership: PricingViewModel (draft)
- Route: `markets/pricing` (identifier `markets/pricing`)
- Interactions: Edit price, add rule, apply
- Validation: Within allowed range
- Empty state: No products: CTA to develop one
- Loading state: Skeleton
- Error state: SetPrice rejection
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Table plus preview
- Performance: Debounced previews

## 13. Product development

- Purpose: Design products and specifications.
- User story: As a player I trade off quality, cost and target segment.
- UI components: Spec sliders, cost and quality preview, segment fit
- State ownership: ProductViewModel (draft)
- Route: `research/products` (identifier `research/products`)
- Interactions: Adjust specs, launch
- Validation: Technology unlocked; funds
- Empty state: No technology: link to research
- Loading state: Skeleton
- Error state: Launch rejection
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Wizard with preview
- Performance: Compute preview off main thread

## 14. Research laboratory

- Purpose: Manage technologies and projects.
- User story: As a player I plan research towards goals.
- UI components: Tech tree canvas, project list, budget control
- State ownership: ResearchViewModel
- Route: `research` (identifier `research`)
- Interactions: Start project, set budget
- Validation: Prerequisites met
- Empty state: No projects: highlight next options
- Loading state: Tree layout progress
- Error state: Rejection inline
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Tree canvas plus inspector
- Performance: Virtualize large trees

## 15. Global economic map

- Purpose: Explore regions with overlays.
- User story: As a player I find markets and risks.
- UI components: Map, overlay selector, region card
- State ownership: WorldViewModel
- Route: `world` (identifier `world`)
- Interactions: Pan, zoom, pick overlay, select region
- Validation: none
- Empty state: No overlay data: explain
- Loading state: Tile loading
- Error state: Overlay failed: retry
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Full map
- Performance: Level-of-detail rendering

## 16. Competitor intelligence

- Purpose: Compare rivals and rankings.
- User story: As a player I learn what rivals do.
- UI components: Ranking table, rival cards, trend charts
- State ownership: CompetitorViewModel
- Route: `competitors` (identifier `competitors`)
- Interactions: Select rival, compare
- Validation: Visibility rules limit data
- Empty state: No rivals visible: explain
- Loading state: Skeleton
- Error state: Data limited notice
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Paged lists

## 17. Corporate acquisitions

- Purpose: Evaluate and bid for companies.
- User story: As a player I value a target and structure a deal.
- UI components: Target list, valuation panel, bid editor
- State ownership: DealViewModel
- Route: `companies/deals` (identifier `companies/deals`)
- Interactions: Open target, set bid, submit
- Validation: Funds; antitrust check
- Empty state: No targets: explain criteria
- Loading state: Skeleton
- Error state: Acquire rejection
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Valuation off main thread

## 18. Stock exchange

- Purpose: Trade shares and monitor listings.
- User story: As a player I buy, sell and issue shares.
- UI components: Ticker list, chart, order ticket
- State ownership: ExchangeViewModel
- Route: `markets/exchange` (identifier `markets/exchange`)
- Interactions: Place order, issue shares
- Validation: Funds, position limits
- Empty state: No listings: explain IPO
- Loading state: Skeleton
- Error state: PlaceOrder rejection
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Chart plus ticket
- Performance: Throttle ticks

## 19. Government policies

- Purpose: View policies and regulations.
- User story: As a player I anticipate regulatory changes.
- UI components: Policy list, effect summaries, timeline
- State ownership: PolicyViewModel
- Route: `world/policy` (identifier `world/policy`)
- Interactions: Filter by region and topic
- Validation: none
- Empty state: No policies: explain
- Loading state: Skeleton
- Error state: Data missing notice
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: List-detail
- Performance: Paged lists

## 20. Economic news

- Purpose: Explain events and their effects.
- User story: As a player I understand what changed and why.
- UI components: Feed, event cards, related charts
- State ownership: NewsViewModel
- Route: `news` (identifier `news`)
- Interactions: Open event, filter
- Validation: none
- Empty state: No news: calm period message
- Loading state: Skeleton
- Error state: Feed unavailable: retry
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Two-pane
- Performance: Paged feed

## 21. Simulation controls

- Purpose: Control speed, pause and saving.
- User story: As a player I pause, speed up and save.
- UI components: Speed buttons, tick/date display, save button
- State ownership: SessionControlsViewModel
- Route: `persistent bar` (identifier `session/controls`)
- Interactions: Set speed, pause, save
- Validation: none
- Empty state: n/a
- Loading state: Saving indicator
- Error state: Save failure banner with retry
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Persistent bar
- Performance: Minimal recomposition

## 22. Scenario selection

- Purpose: Choose how to start.
- User story: As a player I pick a scenario or sandbox settings.
- UI components: Scenario cards, difficulty, seed input
- State ownership: ScenarioViewModel
- Route: `scenarios` (identifier `scenarios`)
- Interactions: Select, configure, start
- Validation: Seed numeric; settings in range
- Empty state: No scenarios installed: show built-ins
- Loading state: Skeleton
- Error state: Content missing: explain
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Grid
- Performance: Lazy grid

## 23. Historical analytics

- Purpose: Explore time series.
- User story: As a player I compare metrics over time.
- UI components: Chart builder, series picker, range selector, table view
- State ownership: AnalyticsViewModel
- Route: `analytics` (identifier `analytics`)
- Interactions: Choose series, range, compare
- Validation: Series limit
- Empty state: No history: explain
- Loading state: Skeleton
- Error state: Query failure retry
- Accessibility: common rules above plus screen-specific labels for primary controls
- Tablet adaptation: Multi-chart
- Performance: Downsampling, paging

# UI/UX Specification

## Principles

Fast to scan, deep on demand, and explainable: every number can be traced to its causes. Phone first, tablet adapted.

## Navigation

Ten top-level destinations: Dashboard, Companies, World, Markets, Operations, Finance, Research, Competitors, Analytics, Profile and Settings.

## Screen catalog

Each screen follows the template: purpose, user stories, components, state ownership, routes, interactions, validation, empty/loading/error states, accessibility, tablet adaptation, performance notes. The table gives the core definition; detailed specs are written per screen when the screen task starts.

| Screen | Destination | Purpose | State owner | Tablet adaptation |
|---|---|---|---|---|
| Dashboard | Dashboard | KPIs, alerts, quick actions | DashboardViewModel | Two-pane with alerts |
| Company overview | Companies | Company health, divisions | CompanyViewModel | List-detail |
| Business creation wizard | Companies | Create a company step by step | WizardViewModel | Side summary |
| Factory construction | Operations | Place and configure facilities | ConstructionViewModel | Map plus panel |
| Factory operations | Operations | Lines, status, bottlenecks | FacilityViewModel | List-detail |
| Production-line editor | Operations | Configure recipe, staffing, priority | LineEditorViewModel | Canvas plus inspector |
| Supply-chain visualization | Operations | Flow graph of goods | SupplyViewModel | Large canvas |
| Logistics map | World | Routes and shipments | LogisticsViewModel | Full map |
| Employee management | Operations | Cohorts, hiring, wages | WorkforceViewModel | List-detail |
| Financial dashboard | Finance | Statements, cash, debt | FinanceViewModel | Multi-chart |
| Market analysis | Markets | Prices, volumes, forecasts | MarketViewModel | Chart plus book |
| Pricing management | Markets | Set prices and strategies | PricingViewModel | Table plus preview |
| Product development | Research | Design products | ProductViewModel | Wizard |
| Research laboratory | Research | Tech tree and projects | ResearchViewModel | Tree canvas |
| Global economic map | World | Overlays, regions | WorldViewModel | Full map |
| Competitor intelligence | Competitors | Rival stats, rankings | CompetitorViewModel | List-detail |
| Corporate acquisitions | Companies | Bids and deals | DealViewModel | List-detail |
| Stock exchange | Markets | Trade shares | ExchangeViewModel | Chart plus book |
| Government policies | World | Policies and regulations | PolicyViewModel | List-detail |
| Economic news | Dashboard | Events feed | NewsViewModel | Two-pane |
| Simulation controls | Dashboard | Speed, pause, save | SessionControls | Persistent bar |
| Scenario selection | Profile | Start scenarios | ScenarioViewModel | Grid |
| Historical analytics | Analytics | Time-series exploration | AnalyticsViewModel | Multi-chart |

## Cross-cutting UX rules

- Loading: skeletons for read-model gaps; the simulation never shows a blocking spinner.
- Errors: command rejections show the violated rule in plain language with a fix hint.
- Empty states explain what to do next.
- Accessibility: TalkBack labels, 48dp targets, text scaling to 200%, color-independent encoding (patterns and labels), reduced motion setting.
- Charts: data tables as alternative, consistent palette tokens from the design system.

## Design system

Material 3 foundation with a game theme layer: tokens for color, type, spacing, elevation; shared components (KPI tile, data table, chart frame, command button with confirmation).

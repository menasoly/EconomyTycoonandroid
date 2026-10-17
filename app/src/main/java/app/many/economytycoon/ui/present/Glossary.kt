package app.many.economytycoon.ui.present

/** One word or phrase the game uses, with what it means here. */
data class GlossaryTerm(
    val id: String,
    val term: String,
    val definition: String,
    /** Ids of related terms. */
    val seeAlso: List<String> = emptyList(),
    val group: String,
)

/** The terms the screens use, explained in the game's own units: days, 360-day years and money without cents. */
object Glossary {
    const val ACCOUNTS = "Accounts"
    const val RATIOS = "Ratios"
    const val CREDIT = "Credit and debt"
    const val MARKETS = "Markets"
    const val PRODUCTION = "Production"
    const val PEOPLE = "People"
    const val OWNERSHIP = "Ownership"
    const val WORLD = "World and policy"
    const val TOGETHER = "Playing together"

    val terms: List<GlossaryTerm> = listOf(
        GlossaryTerm("revenue", "Revenue", "Money earned from selling goods and services before any cost is taken off.", listOf("gross-profit", "net-income"), ACCOUNTS),
        GlossaryTerm("cogs", "Cost of goods sold", "What the goods you sold cost to make or buy: inputs, and the labour and energy that went into them.", listOf("gross-profit"), ACCOUNTS),
        GlossaryTerm("gross-profit", "Gross profit", "Revenue minus the cost of goods sold. It shows whether what you sell earns more than it costs to make.", listOf("revenue", "cogs", "gross-margin"), ACCOUNTS),
        GlossaryTerm("ebitda", "EBITDA", "Earnings before interest, tax, depreciation and amortisation: profit from running the business, before financing and wear on assets.", listOf("depreciation", "ebit"), ACCOUNTS),
        GlossaryTerm("ebit", "Operating profit (EBIT)", "Earnings before interest and tax. EBITDA less depreciation.", listOf("ebitda", "net-income"), ACCOUNTS),
        GlossaryTerm("depreciation", "Depreciation", "The part of a building's or machine's cost charged against each period's profit as it wears out. No cash leaves when it is charged.", listOf("fixed-assets"), ACCOUNTS),
        GlossaryTerm("net-income", "Net income", "What is left after every cost, interest and tax. Also called profit or earnings.", listOf("revenue", "net-margin"), ACCOUNTS),
        GlossaryTerm("fixed-assets", "Fixed assets", "Buildings, land improvements and machines that you keep for years. Shown at cost less depreciation.", listOf("depreciation"), ACCOUNTS),
        GlossaryTerm("receivables", "Receivables", "Money customers owe you for goods already delivered.", listOf("payables"), ACCOUNTS),
        GlossaryTerm("payables", "Payables", "Money you owe suppliers for goods already received.", listOf("receivables"), ACCOUNTS),
        GlossaryTerm("equity", "Equity", "What would be left for the owners if everything were sold and every debt paid: assets minus liabilities.", listOf("net-worth", "retained-earnings"), ACCOUNTS),
        GlossaryTerm("net-worth", "Net worth", "Total assets minus total liabilities. The same as equity; the number the leaderboard ranks by.", listOf("equity"), ACCOUNTS),
        GlossaryTerm("retained-earnings", "Retained earnings", "Profit the company has kept instead of paying out as dividends.", listOf("dividend", "equity"), ACCOUNTS),
        GlossaryTerm("cash-flow", "Cash flow", "Money actually moving in and out. A company can be profitable and still run short of cash if customers pay late or stock piles up.", listOf("runway"), ACCOUNTS),
        GlossaryTerm("runway", "Runway", "How many days of spending the cash in hand would cover if nothing more came in.", listOf("cash-flow"), ACCOUNTS),
        GlossaryTerm("dividend", "Dividend", "Profit paid out to shareholders. Money paid out is no longer available to grow the company.", listOf("retained-earnings"), OWNERSHIP),

        GlossaryTerm("gross-margin", "Gross margin", "Gross profit as a share of revenue. A high figure means each sale leaves a lot after the cost of the goods.", listOf("gross-profit", "net-margin"), RATIOS),
        GlossaryTerm("net-margin", "Net margin", "Net income as a share of revenue: how much of each unit sold is kept as profit.", listOf("net-income", "gross-margin"), RATIOS),
        GlossaryTerm("roe", "Return on equity", "Net income as a share of equity. It shows how hard the owners' money is working.", listOf("equity", "roa"), RATIOS),
        GlossaryTerm("roa", "Return on assets", "Net income as a share of total assets, whoever funded them.", listOf("roe"), RATIOS),
        GlossaryTerm("current-ratio", "Current ratio", "What you own that turns to cash within a year, divided by what you owe within a year. Below 1 means bills may outrun cash.", listOf("cash-ratio"), RATIOS),
        GlossaryTerm("cash-ratio", "Cash ratio", "Cash alone against bills due within a year; the strictest test of liquidity.", listOf("current-ratio"), RATIOS),
        GlossaryTerm("gearing", "Debt to equity", "Borrowed money against the owners' money. High gearing multiplies gains and losses and makes lenders nervous.", listOf("equity", "interest-cover"), RATIOS),
        GlossaryTerm("interest-cover", "Interest cover", "Operating profit divided by interest paid. It shows how many times over the company could pay its interest.", listOf("ebit", "gearing"), RATIOS),
        GlossaryTerm("equity-ratio", "Equity ratio", "Equity as a share of total assets: how much of the company the owners really own.", listOf("equity", "gearing"), RATIOS),
        GlossaryTerm("break-even", "Break-even", "The sales at which revenue just covers fixed and variable costs. Below it the company loses money.", listOf("net-margin"), RATIOS),

        GlossaryTerm("credit-rating", "Credit rating", "A grade from AAA to D that lenders give a borrower. It sets the interest rate and how much you may borrow.", listOf("spread", "default"), CREDIT),
        GlossaryTerm("spread", "Spread", "The extra interest a borrower pays over the base rate, according to its credit rating.", listOf("credit-rating", "base-rate"), CREDIT),
        GlossaryTerm("base-rate", "Base rate", "The central bank's policy rate, from which every loan rate is built. It moves with inflation and the state of the economy.", listOf("spread", "inflation"), CREDIT),
        GlossaryTerm("default", "Default", "Failing to keep up with loan payments. Three missed payments put a loan in default and the rating at D.", listOf("credit-rating", "administration"), CREDIT),
        GlossaryTerm("administration", "Administration", "A company that cannot pay its debts is taken over by administrators who try to rescue it or sell it off. Lenders are paid first, owners last.", listOf("default"), CREDIT),
        GlossaryTerm("arrears", "Arrears", "Bills that are overdue. The larger they are against revenue, the more they hurt the credit rating.", listOf("credit-rating"), CREDIT),
        GlossaryTerm("bond", "Government bond", "A loan to a government that pays a fixed coupon until it matures. Prices fall when rates rise.", listOf("base-rate", "yield"), CREDIT),
        GlossaryTerm("yield", "Yield", "The yearly return on a bond at today's price, as a share of that price.", listOf("bond"), CREDIT),
        GlossaryTerm("deposit-beta", "Deposit beta", "How much of a change in the policy rate a bank passes on to its depositors. A low beta keeps funding cheap when rates rise.", listOf("base-rate", "nim"), CREDIT),
        GlossaryTerm("cet1", "CET1 ratio", "A bank's common equity as a share of its risk-weighted assets. It is the first cushion against losses, and supervisors step in when it falls too low.", listOf("rwa", "stress-test"), CREDIT),
        GlossaryTerm("lcr", "Liquidity coverage ratio (LCR)", "Cash and easily sold securities against the money that could leave in a month of stress. Below 100% the bank could run short.", listOf("bank-run"), CREDIT),
        GlossaryTerm("nim", "Net interest margin", "What a bank earns on its loans and securities less what it pays on deposits and funding, as a share of its earning assets.", listOf("deposit-beta"), CREDIT),
        GlossaryTerm("bank-run", "Bank run", "Depositors who lose faith take their money out at once. Even a sound bank can fail if it cannot sell assets fast enough to pay them.", listOf("lcr", "guarantee-fund"), CREDIT),
        GlossaryTerm("guarantee-fund", "Guarantee fund", "A fund, paid for by the banks, that repays insured deposits when a bank fails. It is why most savers do not join a run.", listOf("bank-run", "resolution"), CREDIT),
        GlossaryTerm("resolution", "Resolution", "How the authorities deal with a failing bank: rescue it, merge it into another or wind it up, with the owners and lenders bearing the loss first.", listOf("guarantee-fund"), CREDIT),
        GlossaryTerm("stress-test", "Stress test", "A trial of what a recession or rate shock would do to every bank's capital. A bank that falls below the minimum fails the test.", listOf("cet1"), CREDIT),
        GlossaryTerm("rwa", "Risk-weighted assets", "A bank's assets counted by how risky they are: government bonds count for little, business loans for much more. Capital is measured against this total.", listOf("cet1"), CREDIT),

        GlossaryTerm("tick", "Day (tick)", "The game's unit of time. One tick is one day; a month is 30 days and a year 360.", emptyList(), WORLD),
        GlossaryTerm("inflation", "Inflation", "How fast the general level of prices is rising, from the consumer price index. Wages and costs follow it.", listOf("cpi", "base-rate"), WORLD),
        GlossaryTerm("cpi", "Consumer price index", "The cost of a typical basket of goods that households buy, set to 100 at the start.", listOf("inflation"), WORLD),
        GlossaryTerm("tariff", "Tariff", "A tax on goods that cross a border. It is added to the cost of shipping them.", listOf("sales-tax"), WORLD),
        GlossaryTerm("sales-tax", "Sales tax", "A share of each sale collected for the government where the sale happens.", listOf("tariff", "corporate-tax"), WORLD),
        GlossaryTerm("corporate-tax", "Corporate tax", "A share of profit paid to the government, assessed every 90 days.", listOf("sales-tax"), WORLD),
        GlossaryTerm("grant", "Grant", "Money a government pays for growth it wants, such as new jobs or new facilities in a favoured industry.", listOf("tender"), WORLD),
        GlossaryTerm("tender", "Tender", "A government's request for goods at a price. The lowest bid from a company that can deliver wins.", listOf("grant"), WORLD),
        GlossaryTerm("carbon-price", "Carbon price", "What a tonne of carbon dioxide costs a company, whether as a tax, a levy or the price of a permit.", listOf("cap-and-trade", "permit", "abatement"), WORLD),
        GlossaryTerm("cap-and-trade", "Cap and trade", "The government sets a limit on emissions and issues permits. Companies surrender one per tonne and trade the spares.", listOf("permit", "auction", "stability-reserve"), WORLD),
        GlossaryTerm("permit", "Permit (allowance)", "The right to release one tonne of carbon dioxide in a given year.", listOf("vintage", "free-allocation", "banking"), WORLD),
        GlossaryTerm("vintage", "Vintage", "The year a permit is for. Old permits lapse after a few years if they are not used.", listOf("permit", "banking"), WORLD),
        GlossaryTerm("free-allocation", "Free allocation", "Permits given away, usually to protect industry while a carbon policy is phased in.", listOf("permit", "auction"), WORLD),
        GlossaryTerm("auction", "Permit auction", "The quarterly sale of new permits at one clearing price that every winning bidder pays.", listOf("permit", "cap-and-trade"), WORLD),
        GlossaryTerm("stability-reserve", "Stability reserve", "Permits held back when there are plenty and released when the price spikes, to steady the market.", listOf("cap-and-trade"), WORLD),
        GlossaryTerm("banking", "Banking and borrowing", "Banking keeps spare permits for later years; borrowing uses next year's permits early, at a cost.", listOf("permit", "vintage"), WORLD),
        GlossaryTerm("offset", "Offset (carbon credit)", "A tonne avoided or removed elsewhere, accepted against an obligation up to a limit.", listOf("carbon-price", "abatement"), WORLD),
        GlossaryTerm("abatement", "Abatement", "Cutting your own emissions. The marginal cost is the price of each tonne saved.", listOf("mac-curve", "carbon-price", "offset"), WORLD),
        GlossaryTerm("mac-curve", "MAC curve", "Abatement options ranked by cost per tonne saved. Those cheaper than the carbon price pay for themselves.", listOf("abatement"), WORLD),
        GlossaryTerm("border-adjustment", "Carbon border adjustment", "A charge on the carbon embedded in imports, so goods made where carbon is free cannot undercut local producers.", listOf("carbon-price", "tariff"), WORLD),
        GlossaryTerm("carbon-rating", "Carbon rating", "A grade of a company's emissions record. A poor grade makes lenders charge more.", listOf("carbon-price"), WORLD),
        GlossaryTerm("patent", "Patent", "A right, granted country by country, to stop others using an invention for a number of years. It is public, it costs fees every year, and it can be challenged in court.", listOf("filing", "trade-secret", "licence", "royalty"), WORLD),
        GlossaryTerm("filing", "Filing", "An application for a patent in one or more countries. The office examines it, may narrow it or refuse it, and charges fees whether or not it grants.", listOf("patent", "patent-box"), WORLD),
        GlossaryTerm("trade-secret", "Trade secret", "An invention kept private instead of patented. It lasts as long as nobody leaks or steals it, so it suits things that are hard to copy from the product.", listOf("patent", "design-around"), WORLD),
        GlossaryTerm("licence", "Licence", "Permission to use someone's patent on agreed terms, for a fee or a share of sales. A licensed product is not infringing and cannot be barred by that patent.", listOf("royalty", "cross-licence", "patent"), WORLD),
        GlossaryTerm("royalty", "Royalty", "What a licensee pays the owner of a patent, usually a share of the sales that use it. Several licences on one product add up to a royalty stack.", listOf("licence", "frand"), WORLD),
        GlossaryTerm("patent-pool", "Pool", "Patent owners who put their patents into one bundle and licence it together. A pool saves each buyer from negotiating with every owner.", listOf("licence", "standard-essential", "cross-licence"), WORLD),
        GlossaryTerm("cross-licence", "Cross-licence", "Two companies each license their patents to the other, often with little or no money changing hands. It ends a standoff when both hold patents the other needs.", listOf("licence", "patent-pool"), WORLD),
        GlossaryTerm("frand", "FRAND", "A promise to license a standard-essential patent on fair, reasonable and non-discriminatory terms. It caps what the owner may ask for.", listOf("standard-essential", "royalty"), WORLD),
        GlossaryTerm("standard-essential", "Standard-essential patent", "A patent that anyone following an industry standard must use. Owners are expected to pledge FRAND terms in return for the standard adopting their invention.", listOf("frand", "patent-pool"), WORLD),
        GlossaryTerm("compulsory-licence", "Compulsory licence", "A licence a government forces on a patent owner, for example in a health emergency or when the owner does not work the invention in the country. The owner is paid a set royalty.", listOf("patent", "licence"), WORLD),
        GlossaryTerm("injunction", "Injunction", "A court order that bars an infringer from selling a product that uses a patent. Production of the product stops until the order ends or a licence is agreed.", listOf("patent", "design-around"), WORLD),
        GlossaryTerm("design-around", "Design around", "Changing a product so that it no longer falls inside a patent's claims. It takes time and money, may fail, and usually leaves the product dearer or a little worse.", listOf("patent", "injunction"), WORLD),
        GlossaryTerm("patent-box", "Patent box", "A lower rate of tax on income that comes from patents, offered by some governments to keep inventors at home. Research credits work alongside it.", listOf("patent", "filing"), WORLD),
        GlossaryTerm("npe", "Non-practising entity", "A holder of patents that makes nothing and earns by licensing or suing. Its demands are priced just under what a defence would cost.", listOf("patent", "injunction", "royalty"), WORLD),

        GlossaryTerm("reference-price", "Reference price", "The usual price of a good when supply and demand are in balance. Prices drift around it.", listOf("spread-market", "shortage"), MARKETS),
        GlossaryTerm("spread-market", "Bid and ask", "The highest price a buyer offers and the lowest price a seller asks. A trade happens when they meet.", listOf("reference-price"), MARKETS),
        GlossaryTerm("shortage", "Shortage", "More wanted than is on sale. Prices rise until new supply arrives.", listOf("glut"), MARKETS),
        GlossaryTerm("glut", "Glut", "More on sale than anyone wants. Prices fall and unsold stock builds up.", listOf("shortage"), MARKETS),
        GlossaryTerm("hhi", "Concentration (HHI)", "A measure of how few sellers share a market, from near 0 for many equal sellers to 10,000 for one. Above 2,500 is concentrated.", listOf("market-share"), MARKETS),
        GlossaryTerm("market-share", "Market share", "A company's part of all that is made of a good. The game measures it by production capacity.", listOf("hhi"), MARKETS),
        GlossaryTerm("future", "Futures contract", "An agreement to buy or sell a good at a fixed price on a later date. Margin is held to cover losses.", listOf("margin"), MARKETS),
        GlossaryTerm("margin", "Margin", "Cash held against a futures position. If losses eat it, you must add more or the position is closed.", listOf("future"), MARKETS),
        GlossaryTerm("awareness", "Awareness", "The share of buyers in a place who have heard of your brand for a product. Advertising raises it and it fades when the money stops.", listOf("loyalty", "reach"), MARKETS),
        GlossaryTerm("loyalty", "Loyalty", "The share of aware buyers who prefer your brand and come back. It grows only from awareness and follows the quality you deliver.", listOf("awareness", "payback-campaign"), MARKETS),
        GlossaryTerm("reach", "Reach", "The share of the people you target that a campaign puts its message in front of each day. It flattens as the daily budget grows.", listOf("awareness", "share-of-voice"), MARKETS),
        GlossaryTerm("share-of-voice", "Share of voice", "Your part of all the advertising pressure for a product in a place. A louder rival blunts every message you send.", listOf("reach"), MARKETS),
        GlossaryTerm("wear-out", "Creative wear-out", "How tired people have grown of an advertisement. A worn creative works at a fraction of its strength until it is replaced.", listOf("ab-test"), MARKETS),
        GlossaryTerm("ab-test", "A/B test", "A trial of a new advertisement against the current one on the same campaign. The better creative is kept when enough people have responded.", listOf("wear-out"), MARKETS),
        GlossaryTerm("payback-campaign", "Campaign payback", "The day on which the extra profit an advertising campaign brought in has covered everything it cost.", listOf("awareness"), MARKETS),

        GlossaryTerm("recipe", "Recipe", "A set of inputs, labour and energy that a production line turns into outputs over a number of days.", listOf("line", "utilisation"), PRODUCTION),
        GlossaryTerm("line", "Production line", "One slot in a facility that runs one recipe at a time.", listOf("recipe", "utilisation"), PRODUCTION),
        GlossaryTerm("utilisation", "Utilisation", "The share of time a line actually ran. Missing inputs, staff or power pull it down.", listOf("line"), PRODUCTION),
        GlossaryTerm("yield-rate", "Yield rate", "The share of a batch that comes out good. Machines, quality assurance and skilled staff raise it.", listOf("recipe"), PRODUCTION),
        GlossaryTerm("upkeep", "Upkeep", "What a facility costs every day just to stay open, whether or not it produces.", listOf("depreciation"), PRODUCTION),
        GlossaryTerm("blueprint", "Blueprint", "A saved set-up of a facility: its lines, recipes, machines, staffing and trading rules, kept so it can be applied to another building.", listOf("blueprint-code", "pending-build"), PRODUCTION),
        GlossaryTerm("blueprint-code", "Blueprint code", "A block of text that holds a blueprint and a checksum, so a set-up can be copied to another game or shared.", listOf("blueprint", "library-folder"), PRODUCTION),
        GlossaryTerm("library-folder", "Library folder", "A named group in the blueprint library, such as bakeries or mines, used to keep many blueprints tidy.", listOf("blueprint"), PRODUCTION),
        GlossaryTerm("pending-build", "Pending build", "A new building ordered from a blueprint that is still under construction; its set-up waits until the building stands.", listOf("blueprint"), PRODUCTION),
        GlossaryTerm("payback", "Payback", "How long a facility takes to earn back what it cost to build.", listOf("upkeep"), PRODUCTION),
        GlossaryTerm("recipe-margin", "Margin (recipe)", "Profit on one batch as a share of what the batch costs to make, shown as lowest, middle and highest across the prices a market can reach.", listOf("payback", "gross-margin"), PRODUCTION),
        GlossaryTerm("deposit", "Deposit", "A place in a region where a raw good such as ore or gypsum can be mined; regions differ in which deposits they hold and how rich they are.", listOf("recipe-margin"), PRODUCTION),

        GlossaryTerm("morale", "Morale", "How content the staff are. Low morale lowers output and, with unpaid wages, leads to strikes.", listOf("union"), PEOPLE),
        GlossaryTerm("union", "Union", "A body that bargains for workers. It can make demands about pay and conditions and call strikes when refused.", listOf("morale"), PEOPLE),
        GlossaryTerm("minimum-wage", "Minimum wage", "The lowest wage the law allows in a country. Offers below it are refused.", listOf("morale"), PEOPLE),

        GlossaryTerm("control", "Control", "Holding more than half the votes in a company. A controlled company is part of your group.", listOf("group", "takeover"), OWNERSHIP),
        GlossaryTerm("group", "Group", "A company and all the companies it controls. Group figures count each by the share you own.", listOf("control"), OWNERSHIP),
        GlossaryTerm("takeover", "Takeover", "Buying enough of a company's shares to control it. Offers above fair value are likely to be accepted.", listOf("control"), OWNERSHIP),
        GlossaryTerm("float", "Float", "The share of a listed company's stock that the public may buy.", listOf("dividend"), OWNERSHIP),
        GlossaryTerm("host", "Host", "The player whose device runs the game for everyone and decides the order in which orders are carried out.", listOf("lockstep"), TOGETHER),
        GlossaryTerm("lockstep", "Lockstep", "Everybody plays the same game by carrying out the same orders in the same order, so only orders travel, not the whole economy.", listOf("host", "command-delay"), TOGETHER),
        GlossaryTerm("command-delay", "Command delay", "The few ticks between giving an order and it taking effect, which gives the host time to pass it on to everyone.", listOf("lockstep"), TOGETHER),
        GlossaryTerm("desync", "Out of step", "When one player's game stops matching the host's. The host notices within ten ticks and sends a fresh copy.", listOf("lockstep", "host"), TOGETHER),
        GlossaryTerm("escrow", "Escrow", "A holding account run by the host. Money and goods in a trade wait there until both sides have kept their promise.", listOf("host"), TOGETHER),
        GlossaryTerm("season", "Season", "A fixed stretch of the game that is scored from net worth growth, income and solvency.", listOf("net-worth"), TOGETHER),
        GlossaryTerm("spectator", "Spectator", "Someone who watches a game without running a company.", emptyList(), TOGETHER),
        GlossaryTerm("resume", "Resume", "Rejoining after a lost connection with a signed proof, keeping your company.", listOf("host"), TOGETHER),
        GlossaryTerm("link-quality", "Connection quality", "How good a player's link to the host is, from ping time and lost messages.", listOf("desync"), TOGETHER),
    ) + app.many.economytycoon.energygrid.GridGlossary.entries.map { GlossaryTerm("grid-${it.key}", it.term, it.text, emptyList(), WORLD) } +
        app.many.economytycoon.tourism.TourismGlossary.entries.map { GlossaryTerm("tourism-${it.key}", it.term, it.text, emptyList(), WORLD) } + listOf(
        GlossaryTerm("urban-far", "Floor area ratio", "Floor space of a building divided by the land it stands on. A ratio of 2 allows two square metres of floor for every square metre of ground.", listOf("urban-zoning", "urban-variance"), WORLD),
        GlossaryTerm("urban-zoning", "Zoning", "What the council allows on a piece of land: homes, shops, factories, a mix, parkland or public buildings, and how dense it may be.", listOf("urban-far", "urban-variance"), WORLD),
        GlossaryTerm("urban-variance", "Variance", "A petition to build denser than the zone allows without changing the zone itself.", listOf("urban-zoning", "urban-far"), WORLD),
        GlossaryTerm("urban-gentrification", "Gentrification", "Rising rents in a poorer district push out the people who live there. Tenant protection and public housing slow it, and anger builds until it breaks out.", listOf("urban-liveability"), WORLD),
        GlossaryTerm("urban-bpr", "BPR delay", "How a road slows down as it fills. Travel time climbs gently until the road is nearly full, then sharply.", listOf("urban-mode-share"), WORLD),
        GlossaryTerm("urban-mode-share", "Mode share", "The share of trips made by car, by public transport and on foot. Faster, cheaper transit takes trips from the car.", listOf("urban-bpr"), WORLD),
        GlossaryTerm("urban-concession", "Concession", "A deal in which a company builds or runs a road or transit line for a city for a fixed term, and is paid by tolls, fares or the city.", listOf("urban-availability-payment"), WORLD),
        GlossaryTerm("urban-availability-payment", "Availability payment", "A monthly sum the city pays a concessionaire for keeping an asset open and in good order, whatever the traffic.", listOf("urban-concession", "urban-availability"), WORLD),
        GlossaryTerm("urban-availability", "Availability", "How much of the time a road or line was open and in good order. Poor availability cuts the city's payments.", listOf("urban-availability-payment"), WORLD),
        GlossaryTerm("urban-liveability", "Liveability", "A city's score from housing, travel, safety, services, environment and public finances. It decides how the city ranks.", listOf("urban-gentrification"), WORLD),
    ) + listOf(
        GlossaryTerm("skill-level", "Skill level", "How well a person does a job, from untrained to expert. It builds with training and experience and fades when the skill goes unused.", listOf("talent-skill-match"), PEOPLE),
        GlossaryTerm("talent-skill-match", "Skill match", "How closely a team's skills fit what the job asks. A good match raises output; a poor one cuts it.", listOf("skill-level"), PEOPLE),
        GlossaryTerm("talent-shortage", "Skills shortage", "Too few people with a skill in a region, or people who lack it. Wages for that skill climb until schools and migration catch up.", listOf("talent-wage-premium"), PEOPLE),
        GlossaryTerm("talent-wage-premium", "Wage premium", "The extra pay a scarce or high skill earns over the going wage for the job.", listOf("talent-shortage"), PEOPLE),
        GlossaryTerm("talent-intake", "Intake", "The number of students a school or course takes in each year.", listOf("talent-accreditation"), PEOPLE),
        GlossaryTerm("talent-accreditation", "Accreditation", "A rating of a training provider's quality. A higher rating draws more students and lets it charge more.", listOf("talent-intake"), PEOPLE),
        GlossaryTerm("talent-bond", "Training bond", "An agreement that a trainee stays for a set time after a course paid for by the employer.", listOf("talent-clawback"), PEOPLE),
        GlossaryTerm("talent-clawback", "Clawback", "Money charged back when a trainee leaves before the bond ends.", listOf("talent-bond"), PEOPLE),
        GlossaryTerm("talent-non-compete", "Non-compete", "A promise not to hire from a rival for a set time. Breaking it costs a penalty.", listOf("talent-counter-offer"), PEOPLE),
        GlossaryTerm("talent-counter-offer", "Counter-offer", "A raise offered to people who have been approached by a rival, to keep them.", listOf("talent-non-compete"), PEOPLE),
        GlossaryTerm("talent-employer-brand", "Employer brand", "How attractive a company is as a place to work. It builds slowly with pay, training and recruiting campaigns.", emptyList(), PEOPLE),
        GlossaryTerm("talent-ladder", "Career ladder", "The steps a person can climb inside a company, each with its own pay and requirements.", listOf("talent-rung"), PEOPLE),
        GlossaryTerm("talent-rung", "Rung", "One step on a career ladder, with its own pay and what it asks of a person.", listOf("talent-ladder"), PEOPLE),
        GlossaryTerm("talent-key-position", "Key position", "A post whose loss hurts the company until it is filled.", listOf("talent-successor"), PEOPLE),
        GlossaryTerm("talent-successor", "Successor", "The person named to take over a key position when it falls empty.", listOf("talent-key-position"), PEOPLE),
        GlossaryTerm("talent-brain-drain", "Brain drain", "Skilled people leaving a country for better pay or conditions abroad.", listOf("talent-diaspora"), PEOPLE),
        GlossaryTerm("talent-diaspora", "Diaspora", "People who have left a country and keep ties to it. Their ties make later migration easier.", listOf("talent-brain-drain"), PEOPLE),
    )

    private val byId: Map<String, GlossaryTerm> = terms.associateBy { it.id }

    fun byId(id: String): GlossaryTerm? = byId[id]

    fun groups(): List<String> = terms.map { it.group }.distinct()

    fun inGroup(group: String): List<GlossaryTerm> = terms.filter { it.group == group }.sortedBy { it.term.lowercase() }

    /** Terms linked from [term], in the order listed, skipping any that do not exist. */
    fun related(term: GlossaryTerm): List<GlossaryTerm> = term.seeAlso.mapNotNull { byId[it] }

    /** Terms that link to [term], so reading can run both ways. */
    fun mentionedBy(term: GlossaryTerm): List<GlossaryTerm> = terms.filter { term.id in it.seeAlso }

    /**
     * Terms matching every word typed. Titles that start with the query come first, then titles that contain it,
     * then matches in the definition. An empty query lists everything alphabetically.
     */
    fun search(query: String): List<GlossaryTerm> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return terms.sortedBy { it.term.lowercase() }
        fun rank(t: GlossaryTerm): Int? {
            val title = t.term.lowercase()
            val text = title + " " + t.definition.lowercase()
            if (!words.all { text.contains(it) }) return null
            return when {
                title.startsWith(words.joinToString(" ")) -> 0
                words.all { title.contains(it) } -> 1
                else -> 2
            }
        }
        return terms.mapNotNull { t -> rank(t)?.let { t to it } }.sortedWith(compareBy({ it.second }, { it.first.term.lowercase() })).map { it.first }
    }
}

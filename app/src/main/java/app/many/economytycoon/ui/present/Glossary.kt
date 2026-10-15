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

        GlossaryTerm("tick", "Day (tick)", "The game's unit of time. One tick is one day; a month is 30 days and a year 360.", emptyList(), WORLD),
        GlossaryTerm("inflation", "Inflation", "How fast the general level of prices is rising, from the consumer price index. Wages and costs follow it.", listOf("cpi", "base-rate"), WORLD),
        GlossaryTerm("cpi", "Consumer price index", "The cost of a typical basket of goods that households buy, set to 100 at the start.", listOf("inflation"), WORLD),
        GlossaryTerm("tariff", "Tariff", "A tax on goods that cross a border. It is added to the cost of shipping them.", listOf("sales-tax"), WORLD),
        GlossaryTerm("sales-tax", "Sales tax", "A share of each sale collected for the government where the sale happens.", listOf("tariff", "corporate-tax"), WORLD),
        GlossaryTerm("corporate-tax", "Corporate tax", "A share of profit paid to the government, assessed every 90 days.", listOf("sales-tax"), WORLD),
        GlossaryTerm("grant", "Grant", "Money a government pays for growth it wants, such as new jobs or new facilities in a favoured industry.", listOf("tender"), WORLD),
        GlossaryTerm("tender", "Tender", "A government's request for goods at a price. The lowest bid from a company that can deliver wins.", listOf("grant"), WORLD),

        GlossaryTerm("reference-price", "Reference price", "The usual price of a good when supply and demand are in balance. Prices drift around it.", listOf("spread-market", "shortage"), MARKETS),
        GlossaryTerm("spread-market", "Bid and ask", "The highest price a buyer offers and the lowest price a seller asks. A trade happens when they meet.", listOf("reference-price"), MARKETS),
        GlossaryTerm("shortage", "Shortage", "More wanted than is on sale. Prices rise until new supply arrives.", listOf("glut"), MARKETS),
        GlossaryTerm("glut", "Glut", "More on sale than anyone wants. Prices fall and unsold stock builds up.", listOf("shortage"), MARKETS),
        GlossaryTerm("hhi", "Concentration (HHI)", "A measure of how few sellers share a market, from near 0 for many equal sellers to 10,000 for one. Above 2,500 is concentrated.", listOf("market-share"), MARKETS),
        GlossaryTerm("market-share", "Market share", "A company's part of all that is made of a good. The game measures it by production capacity.", listOf("hhi"), MARKETS),
        GlossaryTerm("future", "Futures contract", "An agreement to buy or sell a good at a fixed price on a later date. Margin is held to cover losses.", listOf("margin"), MARKETS),
        GlossaryTerm("margin", "Margin", "Cash held against a futures position. If losses eat it, you must add more or the position is closed.", listOf("future"), MARKETS),

        GlossaryTerm("recipe", "Recipe", "A set of inputs, labour and energy that a production line turns into outputs over a number of days.", listOf("line", "utilisation"), PRODUCTION),
        GlossaryTerm("line", "Production line", "One slot in a facility that runs one recipe at a time.", listOf("recipe", "utilisation"), PRODUCTION),
        GlossaryTerm("utilisation", "Utilisation", "The share of time a line actually ran. Missing inputs, staff or power pull it down.", listOf("line"), PRODUCTION),
        GlossaryTerm("yield-rate", "Yield rate", "The share of a batch that comes out good. Machines, quality assurance and skilled staff raise it.", listOf("recipe"), PRODUCTION),
        GlossaryTerm("upkeep", "Upkeep", "What a facility costs every day just to stay open, whether or not it produces.", listOf("depreciation"), PRODUCTION),
        GlossaryTerm("payback", "Payback", "How long a facility takes to earn back what it cost to build.", listOf("upkeep"), PRODUCTION),

        GlossaryTerm("morale", "Morale", "How content the staff are. Low morale lowers output and, with unpaid wages, leads to strikes.", listOf("union"), PEOPLE),
        GlossaryTerm("union", "Union", "A body that bargains for workers. It can make demands about pay and conditions and call strikes when refused.", listOf("morale"), PEOPLE),
        GlossaryTerm("minimum-wage", "Minimum wage", "The lowest wage the law allows in a country. Offers below it are refused.", listOf("morale"), PEOPLE),

        GlossaryTerm("control", "Control", "Holding more than half the votes in a company. A controlled company is part of your group.", listOf("group", "takeover"), OWNERSHIP),
        GlossaryTerm("group", "Group", "A company and all the companies it controls. Group figures count each by the share you own.", listOf("control"), OWNERSHIP),
        GlossaryTerm("takeover", "Takeover", "Buying enough of a company's shares to control it. Offers above fair value are likely to be accepted.", listOf("control"), OWNERSHIP),
        GlossaryTerm("float", "Float", "The share of a listed company's stock that the public may buy.", listOf("dividend"), OWNERSHIP),
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

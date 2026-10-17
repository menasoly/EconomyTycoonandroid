package app.many.economytycoon.ui.present

/** One page of the in-game guide. */
data class HelpTopic(val id: String, val title: String, val summary: String, val paragraphs: List<String>)

object HelpContent {
    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            "start", "Getting started", "Found a company, build something, sell what it makes.",
            listOf(
                "You begin with a company and some cash. Open the Operations tab and choose Build to see what you can afford. Farms and mines make raw goods; workshops and factories turn them into more valuable ones.",
                "A new facility needs a recipe on at least one production line and people to run it. Open the facility, pick a recipe for a line, then set how many staff each role needs. Production starts once inputs are in stock and staff are in place.",
                "Finished goods sit in the facility's storage. Sell them from the Market tab, or add an automation rule so surplus is sold without you.",
            ),
        ),
        HelpTopic(
            "time", "Time and speed", "A day is one tick; a month is 30 days.",
            listOf(
                "The game runs in days. The buttons above the screen pause the game or run it at three speeds. Everything you do while paused takes effect when the next day begins.",
                "Wages, rent, loan payments and taxes are settled every day. Some bills are bigger at the end of a month, when statements are closed and taxes are worked out.",
                "If something serious happens, such as running out of cash, the game can pause by itself. You can turn that off in Settings.",
            ),
        ),
        HelpTopic(
            "production", "Production", "Lines, recipes, machines and condition.",
            listOf(
                "Each production line runs one recipe at a time. A recipe turns input goods, labour and energy into output goods over a number of days. More lines let a facility run more recipes at once.",
                "Machines make lines faster or cheaper to run. Condition drops with use; pay for maintenance to slow the decline. A run-down building produces less and eventually breaks down.",
                "When a line is blocked, the facility list says why: usually missing inputs, missing staff, full storage or no power.",
            ),
        ),
        HelpTopic(
            "markets", "Markets and prices", "Supply, demand and how prices move.",
            listOf(
                "Every region has its own market for each good. Buyers and sellers post orders; trades happen where a bid meets an ask. Prices drift towards where supply and demand balance.",
                "A shortage pushes the price up and rewards producers; a glut pushes it down. The market board shows both, along with how far each price is from its normal level.",
                "Selling in a region where the good is scarce earns more, but moving it there costs freight. Compare regions on a good's page before you ship.",
            ),
        ),
        HelpTopic(
            "rules", "Automation rules", "Keep selling and stocking without clicking.",
            listOf(
                "A selling rule offers surplus stock of a good for sale every day. You choose the pricing style, how much to keep back, the largest order and the lowest margin you will accept.",
                "A buying rule keeps a facility stocked with an input. It tops stock up to a target and never pays more than the multiple of the market price that you set.",
                "Rules are reviewed every day. Remove or switch off a rule when you no longer want it to act.",
            ),
        ),
        HelpTopic(
            "people", "People", "Wages, morale and strikes.",
            listOf(
                "Each role at each facility is hired separately. Wages below the market rate make hiring slow and morale low. Wages below the legal minimum are not allowed.",
                "Benefits and the human resources budget raise morale. Low morale and unpaid wages lead to strikes, and strikers do not work.",
                "Skilled roles take longer to fill. Training raises skill over time but costs money.",
            ),
        ),
        HelpTopic(
            "finance", "Money and credit", "Statements, loans and ratios.",
            listOf(
                "The Finance tab shows how much you earned and spent, what you own and owe, and where your cash went. The three statements always agree with each other.",
                "Banks lend according to your credit rating, your earnings and your net worth. A loan is paid back in instalments with interest; missing instalments damages your rating and can end in default.",
                "A game with a banking sector also has commercial banks. Computer-run banks compete for deposits and loans; a company can found its own, set its rates and standards, and watch capital, liquidity and depositor confidence. A bank that loses depositors' trust can fail.",
                "If liabilities exceed what the company can pay, it enters administration. A company that is wound up pays its lenders first and owners last.",
            ),
        ),
        HelpTopic(
            "logistics", "Logistics", "Moving goods between places.",
            listOf(
                "Goods move between your facilities and warehouses by road, rail, sea or air. Longer trips cost more and take longer; border crossings add tariffs.",
                "Owning vehicles is cheaper per trip than hiring hauliers, but they cost money to buy and to keep in repair. Idle vehicles still wear out.",
                "Warehouses store goods near customers. Rent is charged every day, whether or not the warehouse is full.",
            ),
        ),
        HelpTopic(
            "blueprints", "Blueprints", "Save a facility's set-up and reuse it.",
            listOf(
                "Open one of your facilities and choose Save as blueprint. The lines, recipes, machines, staffing and trading rules are kept in your blueprint library.",
                "To reuse one, open another facility and choose Apply a blueprint, or choose Build from a blueprint on the build screen. You see what will be done and what it costs before anything happens.",
                "A new building takes days to finish. Its set-up waits as a pending build, and the facility screen offers it once the building stands. Blueprints can be copied out as a code and pasted into another game.",
            ),
        ),
        HelpTopic(
            "marketing", "Marketing", "Advertising, promotions and your brand.",
            listOf(
                "Book a campaign for a product in a place: choose a channel, a daily budget and how long it runs. Reach grows with the budget but flattens, so a second channel often adds more than a bigger first one.",
                "Awareness rises while a campaign runs and fades once it stops. Loyalty follows awareness and the quality you deliver. A well-known, well-liked brand draws more buyers to the whole category and can ask a higher price.",
                "Advertisements wear out; renew them when the screen says so, or test a new one against the old. The marketing budget in your policies pays for campaigns first, and only what is left over is general marketing. Unpaid bills suspend a campaign.",
            ),
        ),
        HelpTopic(
            "electricity", "Electricity", "What sets the price, why reserve matters and what to do in a blackout.",
            listOf(
                "Each region has its own grid. Every hour the cheapest plants are called on first, and the dearest plant needed sets the price for all of them. Wind and sun cost almost nothing to run, so they push the price down when they blow and shine; gas and coal set it when they do not.",
                "Reserve is the reliable capacity above the peak. When it runs thin the price climbs towards the cap, and when it runs out customers are cut off in a set order: interruptible industry first, then shops, then factories, then homes. A factory that loses power produces less and wears faster.",
                "A power agreement fixes the price between a seller with plant and a buyer with a connection; the pool still moves the energy and the two settle the difference each month. In a blackout, ask for an interruptible contract on loads that can wait, sign an agreement before the peak, or build or buy plant of your own.",
            ),
        ),
        HelpTopic(
            "tourism", "Tourism", "What draws visitors, why price and occupancy pull apart, and how operators and allotments work.",
            listOf(
                "Visitors choose a town for its safety, access, culture, nature and nightlife, the weather and the season, and what it costs. Each kind of visitor weighs these differently: budget travellers look at price, families at safety, business travellers at access, luxury travellers at comfort. Reviews and marketing build a town's name slowly.",
                "A full house is not always a good house. A low rate fills rooms and still earns less; a high rate earns more per room and leaves some empty. Dynamic pricing raises the rate for nights that fill early and cuts it for nights that stay empty. Overbooking covers cancellations, but guests who turn up with no room are walked to another hotel at your cost.",
                "Online platforms and agencies send guests for a commission, so a single channel leaves rooms empty and several cost margin. A tour operator buys rooms in advance at a net rate: an allotment sets rooms aside, rooms the operator has not sold go back to you by the release date, and a committed share is paid for either way.",
            ),
        ),
        HelpTopic(
            "cities", "Cities and planning", "Districts, land value and rents, the council, transport, the city budget and concessions.",
            listOf(
                "Every city is planned as nine districts. Each is zoned for homes, shops, factories, a mix, parkland or public buildings, from the land the city really has. Land is worth most where people can reach many jobs and homes quickly, and least beside noise, pollution and crime. Rents follow land value and how full the housing is; the Cities screen shows each district and a map of any measure.",
                "A rezoning or density petition goes to a council of nine. Planners, the neighbours and each member's leanings decide the vote, and a company can put money behind its own petition, but lobbying that comes to light throws the petition out. The Council and petitions screen lists the docket, and the city's concessions open for bids there.",
                "Transport shows the roads and lines with their load and fares; a full road slows sharply, and a line that runs often and cheaply takes trips from the car. The City budget screen sets taxes, service levels, works and debt. The player whose headquarters stands in a city runs it; every other city has a computer mayor. Housing, rents and commutes in a planned city feed back into its population and the price of its land.",
            ),
        ),
        HelpTopic(
            "multiplayer", "Playing with others", "The lobby, the connection banner, trades and what happens when the link drops.",
            listOf(
                "Open Play with others from the More menu. In the lobby, mark yourself ready; the host starts the game once everyone is. Anyone who would rather not run a company can watch instead. Everybody then plays the same economy: orders travel between devices, not the whole game.",
                "An order you give takes a moment to take effect, a few ticks, because the host has to put it in order with everyone else's and pass it on. The banner at the top shows how well you are connected. If your game ever drifts from the host's, the host notices within ten ticks and sends you a fresh copy.",
                "If the connection is lost, the game keeps your company for 60 seconds while you reconnect, and you rejoin with the same seat. Money trades go through the host's escrow account: each side's money waits there until both have kept their promise, and an offer nobody answers runs out of time and is returned.",
            ),
        ),
        HelpTopic(
            "research", "Research", "Unlock better machines, recipes and buildings.",
            listOf(
                "Spend research points to unlock technologies. Each has a cost, a minimum time and prerequisites. Unlocked technology can open new facility types, recipes and machines, or improve what you already have.",
                "Put money into the research department to earn points faster. You can queue several technologies so research never goes idle.",
                "Other companies can license technology to you for a fee, and the other way around.",
            ),
        ),
        HelpTopic(
            "rivals", "Rivals and takeovers", "Competing with and buying other companies.",
            listOf(
                "Rival companies run themselves. Some cut prices, some build premium brands, some expand fast. Their pages show how they are doing.",
                "You can bid for a rival's shares. A bid above fair value is likely to be accepted; a bid below is likely to be refused. Holding most of a company's votes gives you control of it.",
                "Rivals can bid for you too. Incoming offers appear in the alerts list and on the Rivals screen.",
            ),
        ),
        HelpTopic(
            "carbon", "Carbon", "Emissions, permits, offsets and the cost of cutting them.",
            listOf(
                "Why it matters. Governments begin to price carbon dioxide: a tax per tonne, a standard that fines emissions above a benchmark, or a cap with permits. Your facilities report what they burn and make every day, and the Carbon screen under Finance shows the tonnes, the bill at today's price and the deadline.",
                "Permits and prices. In a cap-and-trade country the government sells permits at quarterly auctions and companies trade the rest. Hand in one permit per tonne before the deadline after the year ends. Spare permits can be kept for later years, but they lapse after a few years; a stability reserve steadies the price.",
                "Offsets. Credits for tonnes avoided elsewhere can cover a limited share of what you owe, often at a lower price than a permit. Buy them from the open market, or fund a project and receive the credits as it delivers.",
                "Cutting emissions. A retrofit, a fuel switch or carbon capture at a facility lowers the tonnes it reports; a retrofit also lowers the fuel it burns. Each measure has a cost per tonne saved. If that is below the carbon price it pays for itself.",
                "Border adjustment. Some countries charge importers for the carbon embedded in goods from countries that price carbon less, and may refund exporters. It follows the goods that cross the border in your trades.",
            ),
        ),
        HelpTopic(
            "ip", "Protecting what you research", "Patents, trade secrets and licences.",
            listOf(
                "Patent or secret. A patent is public, lasts a fixed term and can be licensed or enforced in court, but it costs fees and may be narrowed or refused. A secret costs little and gives an edge until someone leaks or steals it, so it suits what rivals cannot copy from your products.",
                "Filing. After you research a technology, open Patents and secrets from the More menu. The screen compares the two ways of protecting it and shows the fees. A patent is granted country by country, so file where you sell and where rivals make the product.",
                "Licences and royalties. Selling a product that uses someone else's patent without a licence invites a suit, and a court can bar the sales. You can offer licences on your own patents, form pools and cross-license with rivals. Royalties are settled with your other bills.",
                "Disputes. Accuse a company that uses your patent, settle, or go to court. Outside investors that make nothing also buy patents and send demands; compare the price of settling with the cost of a defence before you decide.",
            ),
        ),
        HelpTopic(
            "saving", "Saving", "Slots, autosave and backups.",
            listOf(
                "The game saves automatically at the interval you choose in Settings and when you leave to the menu. You can also save to a named slot at any time from the Saves screen.",
                "Each save keeps a backup of the previous one. If a save is damaged, the game offers the backup instead.",
                "Saves record every command you issued, so a loaded game continues exactly as it would have.",
            ),
        ),
    )

    fun byId(id: String): HelpTopic? = topics.firstOrNull { it.id == id }

    /** Topics whose title, summary or text mention every word typed. */
    fun search(query: String): List<HelpTopic> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return topics
        return topics.filter { t ->
            val haystack = (t.title + " " + t.summary + " " + t.paragraphs.joinToString(" ")).lowercase()
            words.all { haystack.contains(it) }
        }
    }
}

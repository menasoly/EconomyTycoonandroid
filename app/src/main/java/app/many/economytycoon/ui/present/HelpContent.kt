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

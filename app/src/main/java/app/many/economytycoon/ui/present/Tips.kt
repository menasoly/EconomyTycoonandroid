package app.many.economytycoon.ui.present

import app.many.economytycoon.ui.nav.Route

/** A short piece of advice about playing well, optionally with the screen where it can be acted on. */
data class Tip(val id: String, val text: String, val area: String, val route: Route? = null)

/** Advice that rotates weekly on the overview and is also listed in full. */
object Tips {
    /** A tip stays up this many days before the next one replaces it. */
    const val DAYS_PER_TIP = 7

    val all: List<Tip> = listOf(
        Tip("sell-rules", "Set a sell rule on every facility that makes something. Stock that nobody lists for sale earns nothing.", "Trading", Route.Market),
        Tip("buy-rules", "A buy rule keeps a line running when an input would otherwise run out. Set a ceiling so a price spike cannot drain your cash.", "Trading", Route.Market),
        Tip("reference-price", "The normal price of a good is a guide, not a promise. If the market sits far below it, producers are losing money.", "Trading", Route.Market),
        Tip("sectors", "Look at who makes what before you build. A sector with no producers and steady demand is an opening.", "Strategy", Route.Sectors),
        Tip("outlook", "Run the outlook before a big purchase. It shows how your cash would move over the next months if you did it.", "Strategy", Route.Outlook),
        Tip("small-start", "Two small facilities that feed each other beat one large one that waits on the market for its inputs.", "Strategy"),
        Tip("quality", "Higher quality raises the price you can ask but costs more to keep up. It pays most on goods people buy often.", "Production"),
        Tip("maintenance", "Machines wear out. A little maintenance each month is cheaper than a breakdown that stops the line.", "Production"),
        Tip("waste", "Waste left on site piles up and draws fines. Dispose of it before it reaches the limit.", "Production"),
        Tip("storage", "Full storage stops a line even when it could run. Expand storage or ship stock out before it fills.", "Production"),
        Tip("staffing", "A facility that is short of staff runs below its rated output. Check the people tab when output drops.", "People", Route.People),
        Tip("wages", "Paying above the going wage keeps morale up and staff from leaving, at a price. Raise wages where quality of work matters most.", "People", Route.People),
        Tip("training", "Training lifts skill over months, not days. Start it early in places where you plan to stay.", "People", Route.People),
        Tip("research", "Research takes time and money before anything happens. Pick the technology that unlocks what you already want to build.", "Research", Route.Research),
        Tip("loans", "Borrow for things that earn more than the interest costs. Borrowing to cover losses only delays the problem.", "Finance", Route.Finance),
        Tip("credit", "Paying on time raises your credit rating, which lowers what the next loan costs. One missed payment is expensive for a long time.", "Finance", Route.Credit),
        Tip("cash-floor", "Keep a cash cushion above a month of costs. The coming-up list shows what is due and when.", "Finance", Route.Agenda),
        Tip("dividends", "Paying a dividend leaves less to grow with. Pay one when you have more cash than good uses for it.", "Finance"),
        Tip("statements", "Read the cash flow statement, not only profit. Growth can use up cash while the profit line looks fine.", "Finance", Route.Statements),
        Tip("contracts", "A supply contract trades some upside for certainty. It suits goods whose price swings a lot, if you can deliver on time.", "Trading", Route.Contracts),
        Tip("tenders", "Government tenders pay a fair price for a large amount at once. Check you can deliver before you bid; a failure costs a penalty.", "Trading", Route.Tenders),
        Tip("grants", "Apply for a grant while it is open. What you add after applying is counted, so expand after you apply, not before.", "Government", Route.Grants),
        Tip("insurance", "Insure the facilities you could not afford to lose. Cover costs little against a fire or a flood that ends a company.", "Risk", Route.Insurance),
        Tip("laws", "Laws change taxes, wages and pollution rules. Read the government screen before an election.", "Government", Route.Government),
        Tip("logistics", "Distance adds cost and time. Ship from the nearest source, and keep a warehouse close to where you sell most.", "Logistics", Route.Logistics),
        Tip("vehicles", "Your own vehicles cost less per trip than hiring when they are busy, and more when they sit idle.", "Logistics", Route.Logistics),
        Tip("exchange", "Owning shares in a supplier or customer gives you a say and a share of their profit. Check their books first.", "Ownership", Route.Exchange),
        Tip("subsidiaries", "A subsidiary has its own books. The group view adds them up by the share you own.", "Ownership", Route.Group),
        Tip("rivals", "Watch the rival that grows fastest. Their move into your market is easier to meet early.", "Strategy", Route.Rivals),
        Tip("years", "Compare each year with the one before in the yearly reports. A flat year in a growing market is a loss in disguise.", "Finance", Route.Reports),
        Tip("saves", "Save before a risky decision. A quick save takes a moment and can be loaded from the saves screen.", "Playing", Route.Saves),
        Tip("speed", "Slow the game down when several things are happening. Pause on critical alerts is on by default for a reason.", "Playing", Route.Settings),
        Tip("agenda", "The coming-up list sorts loan payments, deliveries and deadlines by date, so you can see a cash squeeze weeks before it arrives.", "Finance", Route.Agenda),
        Tip("price-behaviour", "A good whose price swings widely is risky to contract for at a fixed price. Check how it behaves on the good screen first.", "Trading", Route.Market),
        Tip("glossary", "Unsure what a word on a screen means? The glossary under help explains each one in the game's own terms.", "Playing", Route.Glossary),
        Tip("blueprints", "Set up one good bakery, save it as a blueprint, and build the next one in one step.", "Production", Route.Blueprints),
        Tip("marketing", "Awareness builds with steady spending and fades when it stops. A modest campaign that runs for weeks beats a big one that ends in days.", "Marketing", Route.Marketing),
        Tip("electricity", "A short grid sells at scarcity prices and cuts customers off in order. Own a plant, or sign a power agreement, before the peak.", "World", Route.Electricity),
        Tip("tourism", "Visitors book weeks ahead. Price by season, watch the review score, and do not rely on one channel.", "World", Route.Tourism),
        Tip("cities", "Rents climb where homes run short. Rezone for more housing, or add a transit line, before the poorest districts are priced out.", "World", Route.Urban),
        Tip("carbon-deadline", "Check the carbon deadline before the year ends. Missing it costs a fine on every tonne you could not cover.", "Government", Route.Carbon),
        Tip("carbon-lapse", "Spare permits lapse after a few years. Use them, or sell what you will not need, before they expire.", "Government", Route.Carbon),
        Tip("banking", "A bank earns the gap between what it lends at and what it pays. Keep capital above the buffer and do not lend more than your deposits can support when depositors get nervous.", "Finance", Route.Banking),
        Tip("ip-protect", "A new technology can be patented or kept secret. A patent lasts and can earn licence fees; a secret is free until it leaks.", "Research", Route.Ip),
        Tip("ip-renew", "Patents need a renewal fee every year. Turn on automatic renewal for the ones that matter, or they lapse for good.", "Research", Route.Ip),
        Tip("talent-train", "A trained team produces more than a cheap one. Train where the skill match is weakest, and tie trainees in with a bond so the investment stays.", "People", Route.TalentTraining),
        Tip("talent-poach", "Rivals hunt the people you trained. Keep pay close to the market and answer offers before they settle.", "People", Route.TalentRecruiting),
        Tip("carbon-cheapest", "The cheapest tonne is the one you do not emit. Compare each measure's cost per tonne with the carbon price.", "Government", Route.Carbon),
        Tip("workshop-test", "Test a scenario before sharing it. If one kind of player wins in a week, or none can, the goals or the start need work.", "World", Route.Workshop),
    )

    fun byId(id: String): Tip? = all.firstOrNull { it.id == id }

    fun areas(): List<String> = all.map { it.area }.distinct()

    fun inArea(area: String): List<Tip> = all.filter { it.area == area }

    /** The tip for a game day. Changes every [DAYS_PER_TIP] days and cycles through the list in a fixed order. */
    fun forDay(day: Long, offset: Int = 0): Tip {
        val slot = (day.coerceAtLeast(0L) / DAYS_PER_TIP).toInt()
        return all[Math.floorMod(slot + offset, all.size)]
    }

    /** The tip for [day] followed by the next ones, for a "more tips" list. */
    fun upcoming(day: Long, count: Int): List<Tip> = List(count.coerceIn(0, all.size)) { forDay(day, it) }
}

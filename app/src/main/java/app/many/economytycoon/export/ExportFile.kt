package app.many.economytycoon.export

/** What can be exported. */
enum class ExportKind(val label: String, val stem: String, val blurb: String) {
    INCOME_MONTH("Income, last 30 days", "income-30d", "Revenue, costs and profit over the last month."),
    INCOME_YEAR("Income, last 360 days", "income-360d", "Revenue, costs and profit over the last year."),
    BALANCE("Balance sheet", "balance-sheet", "Assets, liabilities and equity as they stand."),
    YEARLY("Yearly results", "yearly", "One row per year with revenue, profit and change."),
    HISTORY("Company history", "history", "Cash, net worth, revenue and staff, sampled over time."),
    ACTIVITY("Activity", "activity", "Everything you have ordered, newest first."),
    SECTORS("Who makes what", "sectors", "Producers, shares and prices for every good in play."),
    PRICES("Market prices", "prices", "Every good with its price, reference price and volume."),
}

/** A finished export: the text, ready to share or save, with a name that says what it is and when it was taken. */
class ExportFile(val kind: ExportKind, val fileName: String, val text: String, val rows: Int) {
    val mime: String get() = "text/csv"
    val isEmpty: Boolean get() = rows == 0

    companion object {
        /** Lower-case words joined by hyphens, safe in a file name. */
        fun slug(name: String): String {
            val s = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
            return s.ifEmpty { "company" }
        }

        fun nameFor(company: String, kind: ExportKind, day: Long): String = "${slug(company)}-${kind.stem}-day${day}.csv"
    }
}

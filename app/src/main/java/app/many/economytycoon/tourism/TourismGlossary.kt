package app.many.economytycoon.tourism

/** Plain-language entries for the tourism screens and help pages. */
object TourismGlossary {
    data class Entry(val key: String, val term: String, val text: String)

    val entries: List<Entry> = listOf(
        Entry("occupancy", "Occupancy", "Rooms sold as a share of rooms available. A full house is not always a good house: a low price can fill rooms and still earn less."),
        Entry("adr", "Average daily rate", "Room revenue divided by rooms sold. It tells you what a typical guest paid for a night."),
        Entry("revpar", "Revenue per available room", "Room revenue divided by rooms available, which is the same as occupancy times average daily rate. It is the number hoteliers steer by."),
        Entry("dynamic-pricing", "Dynamic pricing", "The rate moves with the booking pace: nights that fill early cost more, nights that stay empty get cheaper, within a floor and a ceiling."),
        Entry("overbooking", "Overbooking", "Selling more rooms than there are, because some guests cancel or do not come. Too much and guests are walked to other hotels at your cost."),
        Entry("cancellation", "Cancellation terms", "Flexible rates are refunded and cancelled often. Non-refundable rates are cheaper for the guest, keep the money when plans change, and are cancelled rarely."),
        Entry("allotment", "Allotment", "Rooms a hotel sets aside for a tour operator at a net rate. Rooms the operator has not sold by the release date return to the hotel."),
        Entry("release-date", "Release date", "The number of days before the night when unsold allotment goes back on general sale."),
        Entry("commitment", "Committed rooms", "The share of an allotment the operator pays for whether or not it sells it."),
        Entry("commission", "Commission", "The share of the room price an agency or booking platform keeps for sending a guest."),
        Entry("seasonality", "Seasonality", "Demand rises and falls with the weather and the holidays of the markets visitors come from. Staff and prices should follow it."),
        Entry("segments", "Visitor segments", "Budget, family, business and luxury visitors want different things: price, safety, shops and nightlife, comfort. Each weighs a destination differently."),
        Entry("reputation", "Reputation", "What past guests say. Ratings move slowly, and a destination's name follows the properties and attractions in it."),
        Entry("tourism-levy", "Tourism levy", "A charge on each guest-night paid to the government. It brings in money and makes the destination feel dearer."),
        Entry("local-spending", "Local spending", "Visitors spend on food, shopping, transport and entertainment outside their hotel. It lifts demand for local businesses beyond what residents buy."),
        Entry("queue", "Queue satisfaction", "Long waits at an attraction spoil the day. Capacity and ticket price decide whether visitors queue, pay, or turn away."),
        Entry("shock", "Shocks", "Disease, strikes, safety scares, storms and transport failures cut visitor numbers; festivals and conferences raise them."),
    )

    fun find(key: String): Entry? = entries.firstOrNull { it.key == key }
}

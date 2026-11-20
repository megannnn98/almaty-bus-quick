package kz.example.busroutequick

data class RecentRoute(val transport: Transport, val number: String)

/** Newest first; routes are unique by both transport type and number. */
object RouteHistory {
    private const val LIMIT = 6
    private val numberPattern = Regex("[0-9]{1,3}")

    fun record(existing: List<RecentRoute>, opened: RecentRoute): List<RecentRoute> {
        if (!numberPattern.matches(opened.number)) return existing.take(LIMIT)
        return (listOf(opened) + existing.filter { it != opened }).distinct().take(LIMIT)
    }

    fun encode(routes: List<RecentRoute>): String =
        routes.take(LIMIT).joinToString(",") {
            "${if (it.transport == Transport.BUS) "b" else "t"}${it.number}"
        }

    fun decode(value: String?): List<RecentRoute> =
        value.orEmpty().split(",").mapNotNull { token ->
            if (token.length < 2) return@mapNotNull null
            val transport = when (token.first()) {
                'b' -> Transport.BUS
                't' -> Transport.TROLLEYBUS
                else -> return@mapNotNull null
            }
            val number = token.drop(1)
            if (!numberPattern.matches(number)) return@mapNotNull null
            RecentRoute(transport, number)
        }.distinct().take(LIMIT)
}

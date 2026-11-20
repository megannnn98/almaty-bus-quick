package kz.example.busroutequick

import java.net.URLEncoder

enum class Transport(val caption: String, val searchWord: String) {
    BUS("Автобус", "автобус"),
    TROLLEYBUS("Троллейбус", "троллейбус")
}

data class RouteEntry(
    val transport: Transport,
    val number: String,
    val url: String,
    val description: String
)

data class RouteTarget(
    val url: String,
    val isDirectRoute: Boolean,
    val description: String
)

object RouteCatalog {
    private val numberPattern = Regex("[1-9][0-9]{0,2}") // 1..999, no leading zero
    private val directUrlPattern = Regex("https://2gis\\.kz/almaty/route/[0-9]+")

    /** Строгий формат TSV, чтобы URL не подменялся непроверенным адресом. */
    fun parseTsv(input: String): List<RouteEntry> = input.lineSequence()
        .drop(1) // header
        .filter { it.isNotBlank() && !it.startsWith('#') }
        .mapNotNull { line ->
            val columns = line.split('\t')
            if (columns.size != 4) return@mapNotNull null
            val (kind, number, url, description) = columns
            val transport = when (kind) {
                "bus" -> Transport.BUS
                "trolleybus" -> Transport.TROLLEYBUS
                else -> null
            } ?: return@mapNotNull null
            if (!numberPattern.matches(number) || !directUrlPattern.matches(url)) {
                return@mapNotNull null
            }
            RouteEntry(transport, number, url, description)
        }
        .toList()

    /** Неизвестный номер — поиск, не фиктивная ссылка на чужой маршрут. */
    fun resolve(transport: Transport, number: String, routes: List<RouteEntry>): RouteTarget? {
        if (!numberPattern.matches(number)) return null
        val route = routes.firstOrNull { it.transport == transport && it.number == number }
        if (route != null) return RouteTarget(route.url, true, route.description)

        val query = URLEncoder.encode("${transport.searchWord} $number", "UTF-8")
            .replace("+", "%20")
        return RouteTarget("https://2gis.kz/almaty/search/$query", false, "Поиск в 2ГИС")
    }
}

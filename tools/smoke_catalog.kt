import kz.example.busroutequick.RouteCatalog
import kz.example.busroutequick.Transport
import java.io.File

fun main(args: Array<String>) {
    val routes = RouteCatalog.parseTsv(File(args.single()).readText())
    check(routes.size >= 6)
    val known = RouteCatalog.resolve(Transport.BUS, "12", routes)!!
    check(known.isDirectRoute)
    check(known.url.endsWith("9430017310195922"))
    val unknown = RouteCatalog.resolve(Transport.BUS, "158", routes)!!
    check(!unknown.isDirectRoute && unknown.url.contains("/search/"))
    check(!RouteCatalog.resolve(Transport.TROLLEYBUS, "12", routes)!!.isDirectRoute)
    check(RouteCatalog.resolve(Transport.BUS, "12abc", routes) == null)
    println("OK: ${routes.size} direct route(s), lookup and fallback smoke tests passed")
}

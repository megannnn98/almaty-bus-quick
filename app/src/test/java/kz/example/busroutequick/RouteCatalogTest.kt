package kz.example.busroutequick

import org.junit.Assert.*
import org.junit.Test

class RouteCatalogTest {
    private val data = """kind	number	url	description
bus	12	https://2gis.kz/almaty/route/9430017310195922	Медеу
bus	79	https://evil.example/a	подмена
""".trimIndent().replace("\\t", "\t")

    @Test fun `known bus opens exact 2gis route`() {
        val target = RouteCatalog.resolve(Transport.BUS, "12", RouteCatalog.parseTsv(data))!!
        assertTrue(target.isDirectRoute)
        assertEquals("https://2gis.kz/almaty/route/9430017310195922", target.url)
    }

    @Test fun `unknown bus opens search and not a different route`() {
        val target = RouteCatalog.resolve(Transport.BUS, "100", RouteCatalog.parseTsv(data))!!
        assertFalse(target.isDirectRoute)
        assertTrue(target.url.contains("/search/"))
    }

    @Test fun `bus and trolleybus numbers do not conflict`() {
        val target = RouteCatalog.resolve(Transport.TROLLEYBUS, "12", RouteCatalog.parseTsv(data))!!
        assertFalse(target.isDirectRoute)
    }

    @Test fun `rejects invalid numbers and untrusted urls`() {
        assertNull(RouteCatalog.resolve(Transport.BUS, "12a", emptyList()))
        assertNull(RouteCatalog.resolve(Transport.BUS, "", emptyList()))
        assertEquals(1, RouteCatalog.parseTsv(data).size)
    }
}

package kz.example.busroutequick

import org.junit.Assert.*
import org.junit.Test

class RouteHistoryTest {
    private fun bus(number: String) = RecentRoute(Transport.BUS, number)
    private fun trolley(number: String) = RecentRoute(Transport.TROLLEYBUS, number)

    @Test fun `moves reused route to front without duplicates`() {
        val result = RouteHistory.record(listOf(bus("12"), bus("3")), bus("3"))
        assertEquals(listOf(bus("3"), bus("12")), result)
    }

    @Test fun `keeps exactly six latest routes across both modes`() {
        var history = emptyList<RecentRoute>()
        for (i in 1..10) history = RouteHistory.record(history, bus("$i"))
        history = RouteHistory.record(history, trolley("10"))
        assertEquals(6, history.size)
        assertEquals(trolley("10"), history.first())
        assertEquals(bus("10"), history[1])
        assertFalse(history.contains(bus("4")))
    }

    @Test fun `stored history survives roundtrip`() {
        val original = listOf(bus("12"), trolley("12"), bus("79"))
        assertEquals(original, RouteHistory.decode(RouteHistory.encode(original)))
    }

    @Test fun `ignores malformed values and duplicates`() {
        assertEquals(listOf(bus("12"), trolley("8")),
            RouteHistory.decode("b12,invalid,t8,b12,t9999"))
        assertEquals(emptyList<RecentRoute>(), RouteHistory.decode(null))
        assertEquals(emptyList<RecentRoute>(), RouteHistory.record(emptyList(), bus("abc")))
    }

    @Test fun `drops zero and leading-zero numbers`() {
        assertEquals(listOf(bus("10")), RouteHistory.decode("b0,t05,b10"))
        assertEquals(listOf(bus("12")), RouteHistory.record(listOf(bus("12")), bus("0")))
    }
}

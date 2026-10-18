package com.example.skywallpaper

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class SolarClockTest {
    private val beijing = GeoPoint(39.90, 116.40)
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    @Test fun morningAndEveningUseDirectionAndRequestedBounds() {
        assertEquals(SkyPhase.NIGHT, SolarClock.phase(SunPosition(-6.001, true)))
        assertEquals(SkyPhase.DAWN, SolarClock.phase(SunPosition(-6.0, true)))
        assertEquals(SkyPhase.DAWN, SolarClock.phase(SunPosition(9.999, true)))
        assertEquals(SkyPhase.DAY, SolarClock.phase(SunPosition(10.0, true)))
        assertEquals(SkyPhase.DAY, SolarClock.phase(SunPosition(10.001, false)))
        assertEquals(SkyPhase.DUSK, SolarClock.phase(SunPosition(10.0, false)))
        assertEquals(SkyPhase.DUSK, SolarClock.phase(SunPosition(-6.0, false)))
        assertEquals(SkyPhase.NIGHT, SolarClock.phase(SunPosition(-6.001, false)))
    }

    @Test fun systemTimezoneCannotChangeGeometryPhaseEventsOrTransition() {
        val original = TimeZone.getDefault()
        val position = SolarClock.position(now, beijing)
        val phase = SolarClock.phase(now, beijing)
        val events = SolarClock.events(now, beijing)
        val transition = SolarClock.nextTransition(now, beijing)
        try {
            for (zone in listOf("UTC", "Asia/Shanghai", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Honolulu")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(position, SolarClock.position(now, beijing))
                assertEquals(phase, SolarClock.phase(now, beijing))
                assertEquals(events, SolarClock.events(now, beijing))
                assertEquals(transition, SolarClock.nextTransition(now, beijing))
            }
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun equinoxAtEquatorHasNearOverheadNoonAndMidnightBelowHorizon() {
        val point = GeoPoint(0.0, 0.0)
        assertTrue(SolarClock.position(Instant.parse("2026-03-20T12:07:00Z"), point).elevation > 89)
        assertTrue(SolarClock.position(Instant.parse("2026-03-20T00:07:00Z"), point).elevation < -89)
    }

    @Test fun beijingDayHasSixOrderedCrossingsAtCorrectElevations() {
        val events = SolarClock.events(now, beijing)
        assertEquals(SolarEventType.entries.toList(), events.map { it.type })
        val targets = listOf(-6.0, -0.833, 10.0, 10.0, -0.833, -6.0)
        events.zip(targets).forEach { (event, elevation) ->
            assertEquals(elevation, SolarClock.position(event.instant, beijing).elevation, 0.01)
        }
        events.zipWithNext().forEach { (a, b) -> assertTrue(a.instant < b.instant) }
    }

    @Test fun nextTransitionIsActuallyAChangeWithinOneSecond() {
        for (event in SolarClock.events(now, beijing).filter { it.type !in listOf(SolarEventType.SUNRISE, SolarEventType.SUNSET) }) {
            val next = SolarClock.nextTransition(event.instant.minusSeconds(60), beijing)!!
            assertEquals(event.instant, next.instant)
            assertNotEquals(SolarClock.phase(next.instant.minusSeconds(1), beijing), next.phase)
        }
    }

    @Test fun polesDoNotInventSunriseOrSunset() {
        val solstice = Instant.parse("2026-06-21T12:00:00Z")
        val north = GeoPoint(90.0, 0.0)
        val south = GeoPoint(-90.0, 0.0)
        assertEquals(SkyPhase.DAY, SolarClock.phase(solstice, north))
        assertEquals(SkyPhase.NIGHT, SolarClock.phase(solstice, south))
        assertTrue(SolarClock.events(solstice, north).isEmpty())
        assertTrue(SolarClock.events(solstice, south).isEmpty())
        assertNull(SolarClock.nextTransition(solstice, north))
        assertNull(SolarClock.nextTransition(solstice, south))
    }

    @Test fun dateLineAndSouthernHemisphereAreFiniteAndEventsStayInGeographicDay() {
        for (point in listOf(GeoPoint(-33.87, 151.21), GeoPoint(0.0, 180.0), GeoPoint(0.0, -180.0), GeoPoint(64.15, -21.94))) {
            val start = SolarClock.dayStart(now, point)
            assertTrue(SolarClock.position(now, point).elevation.isFinite())
            SolarClock.events(now, point).forEach { assertTrue(it.instant >= start && it.instant <= start.plusSeconds(86400)) }
        }
    }

    @Test fun invalidCoordinatesAreRejected() {
        for ((lat, lon) in listOf(91.0 to 0.0, 0.0 to -181.0, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { GeoPoint(lat, lon) }
        }
    }
}

package com.example.skywallpaper

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import kotlin.math.abs

class GradientModelTest {
    private val beijing = GeoPoint(39.9, 116.4)
    private val now = Instant.parse("2026-10-18T08:00:00Z")

    @Test fun unequalStopCountsPreserveBothEndpointsAndStayOrdered() {
        val a = SkyGradient.default(SkyPhase.DAWN)
        val b = SkyGradient(listOf(GradientStop(0xFF123456.toInt(), .05f), GradientStop(0xFF224466.toInt(), .2f),
            GradientStop(0xFF8899AA.toInt(), .7f), GradientStop(0xFFEEDDDD.toInt(), .95f)),
            start = GradientPoint(.1f, .2f), end = GradientPoint(.8f, .9f), center = GradientPoint(.2f, .7f), radius = .6f)
        assertEquals(GradientFrame.from(a), GradientInterpolation.frame(a, b, 0f))
        assertEquals(GradientFrame.from(b), GradientInterpolation.frame(a, b, 1f))
        val middle = GradientInterpolation.frame(a, b, .5f)
        assertTrue(middle.stops.zipWithNext().all { (x, y) -> x.position < y.position })
        assertEquals(.3f, middle.start.x, .00001f)
        assertEquals(.275f, middle.center.x, .00001f)
        assertEquals(.8f, middle.radius, .00001f)
        // New stop ranks must reproduce the original color function as t approaches either node.
        for (i in 0..100) {
            val position = i / 100f
            for ((endpoint, frame) in listOf(a to GradientInterpolation.frame(a, b, .000001f), b to GradientInterpolation.frame(a, b, .999999f))) {
                val expected = sample(endpoint.stops, position)
                val actual = sample(frame.stops, position)
                for (shift in listOf(0, 8, 16)) assertTrue(abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= 2)
            }
        }
    }

    @Test fun everyNormalDayHasFourRealPhaseMidpoints() {
        val anchors = SolarGradientClock.anchors(now, beijing)
        val today = SolarClock.dayStart(now, beijing)
        val phases = anchors.filter { it.instant >= today && it.instant < today.plusSeconds(86400) }.map { it.phase }.toSet()
        assertEquals(SkyPhase.entries.toSet(), phases)
        anchors.forEach { anchor ->
            assertEquals(anchor.phase, SolarClock.phase(anchor.instant, beijing))
            val blend = SolarGradientClock.blend(anchor.instant, anchors, anchor.phase)
            assertEquals(anchor.phase, blend.from)
            assertEquals(0f, blend.fraction, 0f)
        }
    }

    @Test fun interpolationIsContinuousAcrossMidnightAndMidpoints() {
        val boundary = SolarClock.dayStart(now, beijing).plusSeconds(86400)
        val before = SolarGradientClock.blend(boundary.minusMillis(1), SolarGradientClock.anchors(boundary.minusMillis(1), beijing), SkyPhase.NIGHT)
        val after = SolarGradientClock.blend(boundary.plusMillis(1), SolarGradientClock.anchors(boundary.plusMillis(1), beijing), SkyPhase.NIGHT)
        assertEquals(before.from, after.from)
        assertEquals(before.to, after.to)
        assertEquals(before.fraction, after.fraction, .00001f)
        val anchors = SolarGradientClock.anchors(now, beijing)
        anchors.drop(1).dropLast(1).forEach { anchor ->
            val a = SolarGradientClock.blend(anchor.instant.minusMillis(1), anchors, anchor.phase)
            val b = SolarGradientClock.blend(anchor.instant.plusMillis(1), anchors, anchor.phase)
            assertEquals(anchor.phase, a.to)
            assertEquals(anchor.phase, b.from)
            assertEquals(1f, a.fraction, .00001f)
            assertEquals(0f, b.fraction, .00001f)
        }
    }

    @Test fun polarDayAndNightHoldTheActualSkyAndTimezoneNeverChangesNodes() {
        val north = GeoPoint(90.0, 0.0)
        val solstice = Instant.parse("2026-06-21T12:00:00Z")
        assertTrue(SolarGradientClock.anchors(solstice, north).isEmpty())
        assertEquals(GradientBlend(SkyPhase.DAY, SkyPhase.DAY, 0f), SolarGradientClock.blend(solstice, emptyList(), SkyPhase.DAY))
        val original = TimeZone.getDefault()
        val expected = SolarGradientClock.anchors(now, beijing)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(expected, SolarGradientClock.anchors(now, beijing))
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun highLatitudeNoonDirectionChangeIsIncludedWithoutTenDegreeCrossing() {
        val point = GeoPoint(69.65, 18.96)
        val winter = Instant.parse("2026-11-01T12:00:00Z")
        val anchors = SolarGradientClock.anchors(winter, point)
        assertTrue(anchors.any { it.phase == SkyPhase.DAWN })
        assertTrue(anchors.any { it.phase == SkyPhase.DUSK })
        anchors.forEach { assertEquals(it.phase, SolarClock.phase(it.instant, point)) }
    }

    @Test fun invalidGeometryAndUnorderedStopsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GradientPoint(Float.NaN, .5f) }
        assertThrows(IllegalArgumentException::class.java) { GradientPoint(1.01f, .5f) }
        assertThrows(IllegalArgumentException::class.java) { SkyGradient(listOf(GradientStop(0, .5f), GradientStop(0, .5f))) }
        assertThrows(IllegalArgumentException::class.java) { SkyGradient.default(SkyPhase.DAY).copy(radius = 0f) }
    }

    @Test fun defaultSweepPalettesAndTheirInterpolationsHaveNoColorSeam() {
        val a = SkyGradient.default(SkyPhase.DAWN, GradientType.SWEEP)
        val b = SkyGradient.default(SkyPhase.DAY, GradientType.SWEEP)
        for (t in listOf(0f, .25f, .5f, .75f, 1f)) {
            val frame = GradientInterpolation.frame(a, b, t)
            assertEquals(frame.stops.first().color, frame.stops.last().color)
            assertEquals(0f, frame.stops.first().position, 0f)
            assertEquals(1f, frame.stops.last().position, 0f)
        }
    }

    private fun sample(stops: List<GradientStop>, x: Float): Int {
        val i = stops.indexOfFirst { it.position >= x }
        if (i < 0) return stops.last().color
        if (i == 0) return stops.first().color
        val a = stops[i - 1]; val b = stops[i]
        return GradientInterpolation.color(a.color, b.color, (x - a.position) / (b.position - a.position))
    }
}

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
            val blend = blend(anchor.instant, anchors)
            assertEquals(anchor.phase, blend.from)
            assertEquals(0f, blend.fraction, 0f)
        }
    }

    @Test fun interpolationIsContinuousAcrossMidnightAndMidpoints() {
        val boundary = SolarClock.dayStart(now, beijing).plusSeconds(86400)
        val before = blend(boundary.minusMillis(1), SolarGradientClock.anchors(boundary.minusMillis(1), beijing))
        val after = blend(boundary.plusMillis(1), SolarGradientClock.anchors(boundary.plusMillis(1), beijing))
        assertEquals(before.from, after.from)
        assertEquals(before.to, after.to)
        assertEquals(before.fraction, after.fraction, .00001f)
        val anchors = SolarGradientClock.anchors(now, beijing)
        anchors.drop(1).dropLast(1).forEach { anchor ->
            val a = GradientInterpolation.frame(blend(anchor.instant.minusMillis(1), anchors)) { SkyGradient.default(it) }
            val b = GradientInterpolation.frame(blend(anchor.instant.plusMillis(1), anchors)) { SkyGradient.default(it) }
            assertFramesClose(a, b)
        }
    }

    @Test fun polarDayAndNightHoldTheActualSkyAndTimezoneNeverChangesNodes() {
        val north = GeoPoint(90.0, 0.0)
        val solstice = Instant.parse("2026-06-21T12:00:00Z")
        assertTrue(SolarGradientClock.anchors(solstice, north).isEmpty())
        assertEquals(GradientBlend(SkyPhase.DAY, SkyPhase.DAY, 0f),
            SolarGradientClock.blend(solstice, emptyList(), SkyPhase.DAY, SolarClock.position(solstice, north).elevation))
        val winter = Instant.parse("2026-12-21T12:00:00Z")
        assertEquals(1f, SolarGradientClock.blend(winter, emptyList(), SkyPhase.NIGHT,
            SolarClock.position(winter, north).elevation).nightFraction, 0f)
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

    @Test fun daylightMidpointInterpolationKeepsItsOriginalTiming() {
        val anchors = SolarGradientClock.anchors(now, beijing)
        anchors.zipWithNext().filter { (a, b) ->
            a.phase == SkyPhase.DAWN && b.phase == SkyPhase.DAY || a.phase == SkyPhase.DAY && b.phase == SkyPhase.DUSK
        }.forEach { (a, b) ->
            for (t in listOf(.25f, .5f, .75f)) {
                val instant = a.instant.plusMillis(((b.instant.toEpochMilli() - a.instant.toEpochMilli()) * t).toLong())
                val actual = blend(instant, anchors)
                assertEquals(a.phase, actual.from)
                assertEquals(b.phase, actual.to)
                assertEquals(t * t * (3 - 2 * t), actual.fraction, .00001f)
                assertEquals(0f, actual.nightFraction, 0f)
            }
        }
    }

    @Test fun nightTransitionsUseGeometricHorizonAndTwilightLimitWithCustomGeometry() {
        val anchors = SolarGradientClock.anchors(now, beijing)
        val events = SolarClock.events(now, beijing)
        for ((event, phase) in listOf(SolarEventType.DAWN_START to SkyPhase.DAWN, SolarEventType.NIGHT_START to SkyPhase.DUSK)) {
            val instant = events.first { it.type == event }.instant
            for (type in GradientType.entries) {
                val preset = SkyGradient.default(phase, type)
                val night = SkyGradient(listOf(GradientStop(0xFF001122.toInt(), .1f), GradientStop(0xFF556677.toInt(), .9f)),
                    start = GradientPoint(.1f, .2f), end = GradientPoint(.8f, .9f), center = GradientPoint(.7f, .3f), radius = 1.7f)
                fun frame(elevation: Double) = GradientInterpolation.frame(SolarGradientClock.blend(instant, anchors, phase, elevation)) {
                    if (it == SkyPhase.NIGHT) night else SkyGradient.default(it, type)
                }
                assertEquals(GradientFrame.from(preset), frame(0.0))
                assertEquals(GradientFrame.from(night), frame(-6.0))
                assertFramesClose(GradientInterpolation.frame(preset, night, .5f), frame(-3.0))
                val apparentSunrise = SolarGradientClock.blend(instant, anchors, phase, -.833)
                assertTrue(apparentSunrise.nightFraction > 0f && apparentSunrise.nightFraction < 1f)
            }
        }
    }

    @Test fun twilightStaysContinuousAtHorizonAndAtHighLatitudeBelowHorizonNoon() {
        for ((point, day) in listOf(beijing to now, GeoPoint(69.65, 18.96) to Instant.parse("2026-12-15T12:00:00Z"))) {
            val anchors = SolarGradientClock.anchors(day, point)
            val start = SolarClock.dayStart(day, point)
            // One-minute steps include 0° and −6° crossings and the rising/falling direction change.
            var previous = GradientInterpolation.frame(blend(start, anchors, point)) { SkyGradient.default(it) }
            repeat(1440) { minute ->
                val instant = start.plusSeconds((minute + 1) * 60L)
                val blend = blend(instant, anchors, point)
                val next = GradientInterpolation.frame(blend) { SkyGradient.default(it) }
                for (x in 0..20) {
                    val a = sample(previous.stops, x / 20f)
                    val b = sample(next.stops, x / 20f)
                    for (shift in listOf(0, 8, 16)) assertTrue(abs((a ushr shift and 255) - (b ushr shift and 255)) <= 14)
                }
                if (point != beijing) assertTrue("Polar twilight must retain night color", blend.nightFraction > 0f)
                previous = next
            }
        }
    }

    private fun blend(instant: Instant, anchors: List<GradientAnchor>, point: GeoPoint = beijing) =
        SolarGradientClock.blend(instant, anchors, SolarClock.phase(instant, point), SolarClock.position(instant, point).elevation)

    private fun assertFramesClose(a: GradientFrame, b: GradientFrame) {
        assertEquals(a.start.x, b.start.x, .00001f)
        assertEquals(a.start.y, b.start.y, .00001f)
        assertEquals(a.end.x, b.end.x, .00001f)
        assertEquals(a.end.y, b.end.y, .00001f)
        assertEquals(a.center.x, b.center.x, .00001f)
        assertEquals(a.center.y, b.center.y, .00001f)
        assertEquals(a.radius, b.radius, .00001f)
        for (i in 0..100) {
            val expected = sample(a.stops, i / 100f)
            val actual = sample(b.stops, i / 100f)
            for (shift in listOf(0, 8, 16)) assertTrue(abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= 1)
        }
    }

    @Test fun invalidGeometryAndUnorderedStopsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GradientPoint(Float.NaN, .5f) }
        assertThrows(IllegalArgumentException::class.java) { GradientPoint(1.01f, .5f) }
        assertThrows(IllegalArgumentException::class.java) { SkyGradient(listOf(GradientStop(0, .5f), GradientStop(0, .5f))) }
        assertThrows(IllegalArgumentException::class.java) { SkyGradient.default(SkyPhase.DAY).copy(radius = 0f) }
    }

    @Test fun radiusAcceptsFourHundredPercentAndInterpolatesWithoutClamping() {
        val minimum = SkyGradient.default(SkyPhase.DAWN, GradientType.RADIAL).copy(radius = .1f)
        val maximum = SkyGradient.default(SkyPhase.DAY, GradientType.RADIAL).copy(radius = 4f)
        assertEquals(4f, maximum.radius, 0f)
        assertEquals(2.05f, GradientInterpolation.frame(minimum, maximum, .5f).radius, .00001f)
        assertEquals(4f, GradientInterpolation.frame(minimum, maximum, 1f).radius, 0f)
        for (invalid in listOf(.099f, 4.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { maximum.copy(radius = invalid) }
        }
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

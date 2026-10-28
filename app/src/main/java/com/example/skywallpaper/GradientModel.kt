package com.example.skywallpaper

import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToInt

enum class WallpaperMode { IMAGE, GRADIENT }
enum class GradientType { LINEAR, RADIAL, SWEEP }

data class GradientPoint(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) }
}

data class GradientStop(val color: Int, val position: Float) {
    init { require(position.isFinite() && position in 0f..1f) }
}

data class SkyGradient(
    val stops: List<GradientStop>,
    val start: GradientPoint = GradientPoint(.5f, 0f),
    val end: GradientPoint = GradientPoint(.5f, 1f),
    val center: GradientPoint = GradientPoint(.5f, .5f),
    val radius: Float = 1f,
) {
    init {
        require(stops.size in 2..6)
        require(stops.zipWithNext().all { (a, b) -> b.position - a.position >= .009f })
        require(radius.isFinite() && radius in radiusRange)
    }

    companion object {
        val radiusRange = .1f..4f

        fun default(phase: SkyPhase, type: GradientType = GradientType.LINEAR): SkyGradient {
            return GradientDefaults.get(phase, type)
        }
    }
}

/** Interpolate corresponding stop ranks, retaining every knot even with unequal stop counts. */
object GradientInterpolation {
    fun frame(blend: GradientBlend, gradient: (SkyPhase) -> SkyGradient): GradientFrame {
        if (blend.nightFraction == 1f) return GradientFrame.from(gradient(SkyPhase.NIGHT))
        val twilight = if (blend.from == blend.to) GradientFrame.from(gradient(blend.from))
            else frame(gradient(blend.from), gradient(blend.to), blend.fraction)
        val daylight = if (blend.dayFraction == 0f) twilight
            else frame(twilight, GradientFrame.from(gradient(SkyPhase.DAY)), blend.dayFraction)
        return if (blend.nightFraction == 0f) daylight
            else frame(daylight, GradientFrame.from(gradient(SkyPhase.NIGHT)), blend.nightFraction)
    }

    fun frame(from: SkyGradient, to: SkyGradient, fraction: Float): GradientFrame =
        frame(GradientFrame.from(from), GradientFrame.from(to), fraction)

    private fun frame(from: GradientFrame, to: GradientFrame, fraction: Float): GradientFrame {
        val t = fraction.coerceIn(0f, 1f)
        if (t == 0f) return from
        if (t == 1f) return to
        val ranks = (from.stops.indices.map { it.toFloat() / (from.stops.size - 1) } +
            to.stops.indices.map { it.toFloat() / (to.stops.size - 1) }).distinct().sorted()
        return GradientFrame(ranks.map { rank ->
            val a = sampleRank(from.stops, rank)
            val b = sampleRank(to.stops, rank)
            GradientStop(color(a.color, b.color, t), lerp(a.position, b.position, t))
        }, point(from.start, to.start, t), point(from.end, to.end, t), point(from.center, to.center, t), lerp(from.radius, to.radius, t))
    }

    private fun sampleRank(stops: List<GradientStop>, rank: Float): GradientStop {
        val index = rank * (stops.size - 1)
        val low = floor(index).toInt().coerceAtMost(stops.lastIndex)
        val high = (low + 1).coerceAtMost(stops.lastIndex)
        val t = index - low
        return GradientStop(color(stops[low].color, stops[high].color, t), lerp(stops[low].position, stops[high].position, t))
    }

    internal fun color(a: Int, b: Int, t: Float): Int = (0xFF shl 24) or
        ((lerp((a ushr 16 and 255).toFloat(), (b ushr 16 and 255).toFloat(), t).roundToInt()) shl 16) or
        ((lerp((a ushr 8 and 255).toFloat(), (b ushr 8 and 255).toFloat(), t).roundToInt()) shl 8) or
        lerp((a and 255).toFloat(), (b and 255).toFloat(), t).roundToInt()

    private fun point(a: GradientPoint, b: GradientPoint, t: Float) = GradientPoint(lerp(a.x, b.x, t), lerp(a.y, b.y, t))
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

data class GradientFrame(val stops: List<GradientStop>, val start: GradientPoint, val end: GradientPoint,
                         val center: GradientPoint, val radius: Float) {
    companion object {
        fun from(gradient: SkyGradient) = GradientFrame(gradient.stops, gradient.start, gradient.end, gradient.center, gradient.radius)
    }
}

data class GradientAnchor(val instant: Instant, val phase: SkyPhase)
data class GradientBlend(val from: SkyPhase, val to: SkyPhase, val fraction: Float, val nightFraction: Float = 0f, val dayFraction: Float = 0f)

/** Midpoints of actual solar phase spans, including the night that crosses a geographic date. */
object SolarGradientClock {
    const val DAY_ELEVATION = 40.0

    /** A representative instant for a gallery preset under the elevation-based blend. */
    fun previewInstant(now: Instant, point: GeoPoint, phase: SkyPhase): Instant? {
        val start = SolarClock.dayStart(now, point)
        if (phase == SkyPhase.DAY || phase == SkyPhase.NIGHT) return anchors(now, point).firstOrNull {
            it.phase == phase && it.instant >= start && it.instant < start.plusSeconds(86400)
        }?.instant
        val rising = phase == SkyPhase.DAWN
        var left = start
        var previous = SolarClock.position(left, point).elevation
        repeat(288) {
            val right = left.plusSeconds(300)
            val next = SolarClock.position(right, point).elevation
            if (if (rising) previous < 0 && next >= 0 else previous >= 0 && next < 0) {
                var lo = left.epochSecond
                var hi = right.epochSecond
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if ((SolarClock.position(Instant.ofEpochSecond(mid), point).elevation >= 0) == rising) hi = mid else lo = mid
                }
                return Instant.ofEpochSecond(hi)
            }
            previous = next
            left = right
        }
        return null
    }

    fun anchors(now: Instant, point: GeoPoint, dayElevation: Double = DAY_ELEVATION): List<GradientAnchor> {
        val start = SolarClock.dayStart(now, point).minusSeconds(3 * 86400L)
        val end = start.plusSeconds(7 * 86400L)
        val changes = mutableListOf<Pair<Instant, SkyPhase>>()
        var left = start
        fun phaseAt(instant: Instant) = SolarClock.phase(SolarClock.position(instant, point), dayElevation)
        var phase = phaseAt(left)
        while (left < end) {
            val right = left.plusSeconds(300)
            val next = phaseAt(right)
            if (next != phase) {
                var lo = left.epochSecond
                var hi = right.epochSecond
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if (phaseAt(Instant.ofEpochSecond(mid)) == phase) lo = mid else hi = mid
                }
                changes += Instant.ofEpochSecond(hi) to next
            }
            phase = next
            left = right
        }
        return changes.zipWithNext().map { (a, b) ->
            GradientAnchor(a.first.plusMillis((b.first.toEpochMilli() - a.first.toEpochMilli()) / 2), a.second)
        }
    }

    fun blend(now: Instant, anchors: List<GradientAnchor>, sun: SunPosition): GradientBlend {
        val elevation = sun.elevation
        require(elevation.isFinite())
        // Geometric 0°, rather than the -0.833° threshold used by the sunrise/set labels.
        val night = smoothstep((-elevation / 6.0).toFloat().coerceIn(0f, 1f))
        if (night == 1f) return GradientBlend(SkyPhase.NIGHT, SkyPhase.NIGHT, 0f, night)
        val day = smoothstep((elevation / DAY_ELEVATION).toFloat().coerceIn(0f, 1f))
        if (day == 1f) return GradientBlend(SkyPhase.DAY, SkyPhase.DAY, 0f)
        val twilight = if (sun.rising) SkyPhase.DAWN else SkyPhase.DUSK
        val right = anchors.indexOfFirst { it.instant > now }
        if (right > 0) {
            val a = anchors[right - 1]
            val b = anchors[right]
            // The sun may turn around without reaching 40° or −6°.
            // Blend the adjacent twilight palettes through that turn, avoiding a noon/midnight
            // jump. Their day/night contribution still follows actual solar elevation.
            if (setOf(a.phase, b.phase) == setOf(SkyPhase.DAWN, SkyPhase.DUSK)) {
                val t = ((now.toEpochMilli() - a.instant.toEpochMilli()).toDouble() /
                    (b.instant.toEpochMilli() - a.instant.toEpochMilli())).toFloat().coerceIn(0f, 1f)
                return GradientBlend(a.phase, b.phase, smoothstep(t), night, day)
            }
        }
        return GradientBlend(twilight, twilight, 0f, night, day)
    }

    private fun smoothstep(t: Float) = t * t * (3f - 2f * t)
}

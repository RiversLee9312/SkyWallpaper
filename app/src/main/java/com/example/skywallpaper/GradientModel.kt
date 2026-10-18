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
        require(radius.isFinite() && radius in .1f..2f)
    }

    companion object {
        fun default(phase: SkyPhase, type: GradientType = GradientType.LINEAR): SkyGradient {
            val colors = when (phase) {
                SkyPhase.DAWN -> listOf(0xFF697CB4, 0xFFE9B2BA, 0xFFFFD9AA)
                SkyPhase.DAY -> listOf(0xFF277BCE, 0xFF86C5ED, 0xFFDCEEF6)
                SkyPhase.DUSK -> listOf(0xFF55518F, 0xFFD481A1, 0xFFF4B777)
                SkyPhase.NIGHT -> listOf(0xFF0C142B, 0xFF1C2C50, 0xFF34486C)
            }
            val palette = if (type == GradientType.SWEEP) colors + colors.first() else colors
            return SkyGradient(palette.mapIndexed { i, c -> GradientStop(c.toInt(), i.toFloat() / (palette.size - 1)) },
                center = when (phase) {
                    SkyPhase.DAWN -> GradientPoint(.35f, .7f)
                    SkyPhase.DAY -> GradientPoint(.65f, .25f)
                    SkyPhase.DUSK -> GradientPoint(.65f, .7f)
                    SkyPhase.NIGHT -> GradientPoint(.5f, .2f)
                })
        }
    }
}

/** Interpolate corresponding stop ranks, retaining every knot even with unequal stop counts. */
object GradientInterpolation {
    fun frame(from: SkyGradient, to: SkyGradient, fraction: Float): GradientFrame {
        val t = fraction.coerceIn(0f, 1f)
        if (t == 0f) return GradientFrame.from(from)
        if (t == 1f) return GradientFrame.from(to)
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
data class GradientBlend(val from: SkyPhase, val to: SkyPhase, val fraction: Float)

/** Midpoints of actual solar phase spans, including the night that crosses a geographic date. */
object SolarGradientClock {
    fun anchors(now: Instant, point: GeoPoint): List<GradientAnchor> {
        val start = SolarClock.dayStart(now, point).minusSeconds(3 * 86400L)
        val end = start.plusSeconds(7 * 86400L)
        val changes = mutableListOf<Pair<Instant, SkyPhase>>()
        var left = start
        var phase = SolarClock.phase(left, point)
        while (left < end) {
            val right = left.plusSeconds(300)
            val next = SolarClock.phase(right, point)
            if (next != phase) {
                var lo = left.epochSecond
                var hi = right.epochSecond
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if (SolarClock.phase(Instant.ofEpochSecond(mid), point) == phase) lo = mid else hi = mid
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

    fun blend(now: Instant, anchors: List<GradientAnchor>, fallback: SkyPhase): GradientBlend {
        val right = anchors.indexOfFirst { it.instant > now }
        if (right <= 0) return GradientBlend(fallback, fallback, 0f)
        val a = anchors[right - 1]
        val b = anchors[right]
        val t = ((now.toEpochMilli() - a.instant.toEpochMilli()).toDouble() /
            (b.instant.toEpochMilli() - a.instant.toEpochMilli())).toFloat().coerceIn(0f, 1f)
        // Zero slope at each midpoint avoids a change in velocity at the interpolation nodes.
        return GradientBlend(a.phase, b.phase, t * t * (3f - 2f * t))
    }
}

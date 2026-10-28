package com.example.skywallpaper

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

enum class SkyPhase(val symbol: String) {
    DAWN("☀"), DAY("☁"), DUSK("◒"), NIGHT("☾")
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}
data class SunPosition(val elevation: Double, val rising: Boolean)
data class SkyTransition(val instant: Instant, val phase: SkyPhase)
enum class SolarEventType { DAWN_START, SUNRISE, DAY_START, DUSK_START, SUNSET, NIGHT_START }
data class SolarEvent(val type: SolarEventType, val instant: Instant)

/** NOAA/Meeus solar geometry. All inputs and results are absolute UTC instants.
 * Geometric center altitude; sunrise/set use -0.833° for standard refraction + solar radius.
 * No Calendar, device timezone, network API, or civil clock enters these calculations. */
object SolarClock {
    private fun sinDeg(x: Double) = sin(Math.toRadians(x))
    private fun cosDeg(x: Double) = cos(Math.toRadians(x))
    private fun normalize(x: Double) = ((x % 360) + 360) % 360

    fun position(instant: Instant, point: GeoPoint): SunPosition {
        val jd = instant.epochSecond / 86400.0 + instant.nano / 8.64e13 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l = normalize(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sinDeg(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sinDeg(2 * m) * (0.019993 - 0.000101 * t) + sinDeg(3 * m) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l + c - 0.00569 - 0.00478 * sinDeg(omega)
        val epsilon = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60 +
            0.00256 * cosDeg(omega)
        val declination = Math.toDegrees(asin(sinDeg(epsilon) * sinDeg(lambda)))
        val y = tan(Math.toRadians(epsilon / 2)).pow(2)
        val eq = 4 * Math.toDegrees(y * sinDeg(2 * l) - 2 * e * sinDeg(m) +
            4 * e * y * sinDeg(m) * cosDeg(2 * l) - 0.5 * y * y * sinDeg(4 * l) -
            1.25 * e * e * sinDeg(2 * m))
        val utcMinutes = Math.floorMod(instant.epochSecond, 86400L) / 60.0 + instant.nano / 6e10
        val solarMinutes = ((utcMinutes + eq + 4 * point.longitude) % 1440 + 1440) % 1440
        val hourAngle = solarMinutes / 4 - 180
        val elevation = Math.toDegrees(asin((sinDeg(point.latitude) * sinDeg(declination) +
            cosDeg(point.latitude) * cosDeg(declination) * cosDeg(hourAngle)).coerceIn(-1.0, 1.0)))
        return SunPosition(elevation, hourAngle < 0)
    }

    fun phase(instant: Instant, point: GeoPoint): SkyPhase = phase(position(instant, point))

    fun phase(sun: SunPosition, dayElevation: Double = 10.0): SkyPhase = when {
        sun.elevation < -6 -> SkyPhase.NIGHT
        sun.rising && sun.elevation < dayElevation -> SkyPhase.DAWN
        !sun.rising && sun.elevation <= dayElevation -> SkyPhase.DUSK
        else -> SkyPhase.DAY
    }

    fun nextTransition(now: Instant, point: GeoPoint): SkyTransition? {
        var left = now
        var before = phase(left, point)
        repeat(576) { // 48 hours; polar day/night deliberately returns no transition.
            val right = left.plusSeconds(300)
            val after = phase(right, point)
            if (before != after) {
                var lo = left.epochSecond
                var hi = right.epochSecond
                while (hi - lo > 1) {
                    val mid = (lo + hi) / 2
                    if (phase(Instant.ofEpochSecond(mid), point) == before) lo = mid else hi = mid
                }
                val result = Instant.ofEpochSecond(hi)
                return SkyTransition(result, phase(result, point))
            }
            left = right
            before = after
        }
        return null
    }

    /** The day at this longitude, rather than the date in the phone's selected timezone. */
    fun dayStart(now: Instant, point: GeoPoint): Instant {
        val offset = (point.longitude * 240).roundToLong()
        val date = now.plusSeconds(offset).atOffset(ZoneOffset.UTC).toLocalDate()
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(offset)
    }

    fun events(now: Instant, point: GeoPoint): List<SolarEvent> {
        val start = dayStart(now, point)
        val events = mutableListOf<SolarEvent>()
        val thresholds = listOf(-6.0, -0.833, 10.0)
        var left = start
        var last = position(left, point)
        repeat(288) {
            val right = left.plusSeconds(300)
            val next = position(right, point)
            for (threshold in thresholds) {
                val rising = last.elevation < threshold && next.elevation >= threshold
                val falling = last.elevation >= threshold && next.elevation < threshold
                if (rising || falling) {
                    val title = when {
                        rising && threshold == -6.0 -> SolarEventType.DAWN_START
                        rising && threshold == -0.833 -> SolarEventType.SUNRISE
                        rising -> SolarEventType.DAY_START
                        threshold == 10.0 -> SolarEventType.DUSK_START
                        threshold == -0.833 -> SolarEventType.SUNSET
                        threshold == -6.0 -> SolarEventType.NIGHT_START
                        else -> null
                    }
                    if (title != null) {
                        var lo = left.epochSecond
                        var hi = right.epochSecond
                        while (hi - lo > 1) {
                            val mid = (lo + hi) / 2
                            val above = position(Instant.ofEpochSecond(mid), point).elevation >= threshold
                            if (above == rising) hi = mid else lo = mid
                        }
                        events += SolarEvent(title, Instant.ofEpochSecond(hi))
                    }
                }
            }
            left = right
            last = next
        }
        return events.sortedBy { it.instant }
    }
}

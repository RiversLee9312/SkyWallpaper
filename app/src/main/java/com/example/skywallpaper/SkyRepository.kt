package com.example.skywallpaper

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.content.SharedPreferences
import java.time.Instant

/** One store for both BFU and unlocked rendering; migrate the old store only after unlock. */
internal object SkyPreferenceStorage {
    @Synchronized
    fun open(context: Context, name: String = "sky", unlocked: Boolean = context.getSystemService(UserManager::class.java).isUserUnlocked): SharedPreferences {
        val deviceContext = context.createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (unlocked && !prefs.getBoolean("direct_boot_migrated", false)) {
            val credentialContext = if (context.isDeviceProtectedStorage) context.createPackageContext(context.packageName, 0) else context
            val legacy = credentialContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            // These values are all needed to reproduce the chosen sky before the first unlock.
            for (key in listOf("latitude", "longitude", "updated", "automatic", "manual_location", "phase")) {
                when (val value = legacy.all[key]) {
                    is Long -> edit.putLong(key, value)
                    is Int -> edit.putInt(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                }
            }
            // Commit the migration before deleting its source; a BFU engine keeps the same prefs instance.
            if (edit.putBoolean("direct_boot_migrated", true).commit()) credentialContext.deleteSharedPreferences(name)
        }
        return prefs
    }
}

class SkyRepository(context: Context) {
    private val context = context.applicationContext
    private val prefs = SkyPreferenceStorage.open(this.context)
    val wallpapers = WallpaperStore(this.context, prefs)
    fun onUserUnlocked() { SkyPreferenceStorage.open(context) }
    val point: GeoPoint? get() = if (prefs.contains("latitude"))
        GeoPoint(Double.fromBits(prefs.getLong("latitude", 0)), Double.fromBits(prefs.getLong("longitude", 0))) else null
    val automatic: Boolean get() = prefs.getBoolean("automatic", true)
    val manualPhase: SkyPhase get() = SkyPhase.entries.getOrElse(prefs.getInt("phase", 1)) { SkyPhase.DAY }
    val manualLocation: Boolean get() = prefs.getBoolean("manual_location", false)
    val updatedAt: Instant? get() = prefs.getLong("updated", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    fun savePoint(point: GeoPoint, manual: Boolean = false, time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("latitude", point.latitude.toBits()).putLong("longitude", point.longitude.toBits())
            .putBoolean("manual_location", manual).putLong("updated", time).apply()
    }
    fun setAutomatic(value: Boolean) { prefs.edit().putBoolean("automatic", value).apply() }
    fun setManualPhase(phase: SkyPhase) { prefs.edit().putInt("phase", phase.ordinal).apply() }
    fun phase(now: Instant = Instant.now()): SkyPhase =
        point?.takeIf { automatic }?.let { SolarClock.phase(now, it) } ?: manualPhase
    fun listen(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unlisten(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}

/** The platform fused provider owns GPS/network fusion; no Play services dependency. */
class DeviceLocation(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null
    fun refresh(onResult: (String) -> Unit) {
        stop()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onResult(context.getString(R.string.location_permission_needed))
            return
        }
        try {
            if (!manager.isLocationEnabled) { onResult(context.getString(R.string.location_disabled)); return }
            val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = LocationManager.FUSED_PROVIDER
            if (provider !in manager.allProviders || !manager.isProviderEnabled(provider)) {
                onResult(context.getString(R.string.no_provider)); return
            }
            var received = false
            val callback = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // A queued callback from a cancelled or replaced acquisition cannot overwrite a manual choice.
                    if (listener !== this) return
                    save(location)
                    received = true
                    // Allow the system to refine its fused result during this bounded acquisition.
                    onResult(context.getString(R.string.gps_fallback))
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Legacy callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listener = callback
            val request = LocationRequest.Builder(2_000L).setDurationMillis(60_000L)
                .setQuality(if (fine) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                .build()
            manager.requestLocationUpdates(provider, request, context.mainExecutor, callback)
            onResult(context.getString(if (fine) R.string.gps_loading else R.string.coarse_loading))
            timeout = Runnable {
                stop()
                onResult(context.getString(if (received) R.string.device_updated else R.string.gps_timeout_empty))
            }.also { handler.postDelayed(it, 60_000) }
        } catch (_: SecurityException) { stop(); onResult(context.getString(R.string.location_permission_error)) }
          catch (_: IllegalArgumentException) { stop(); onResult(context.getString(R.string.location_service_error)) }
    }
    private fun save(location: Location) {
        val precision = if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 100_000.0 else 100.0
        val point = GeoPoint(kotlin.math.round(location.latitude * precision) / precision,
            kotlin.math.round(location.longitude * precision) / precision)
        SkyRepository(context).savePoint(point, time = location.time)
    }
    fun stop() {
        val previous = listener
        listener = null
        try { previous?.let { manager.removeUpdates(it) } } catch (_: SecurityException) { /* Permission revoked while stopping. */ }
        timeout?.let(handler::removeCallbacks)
        timeout = null
    }
}

fun scheduleLocationRefresh(context: Context) {
    // Location services and persisted jobs resume after unlock; BFU uses the saved position offline.
    if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
    val scheduler = context.getSystemService(JobScheduler::class.java)
    val allowed = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    val repo = SkyRepository(context)
    if (allowed && repo.automatic && !repo.manualLocation) {
        if (scheduler.getPendingJob(731)?.flexMillis != 60 * 60 * 1000L) {
            scheduler.schedule(JobInfo.Builder(731, ComponentName(context, LocationRefreshJob::class.java))
                .setPeriodic(6 * 60 * 60 * 1000L, 60 * 60 * 1000L).setPersisted(true).build())
        }
    } else scheduler.cancel(731)
}

class LocationRefreshJob : JobService() {
    private var location: DeviceLocation? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val repository = SkyRepository(this)
        if (!repository.automatic || repository.manualLocation || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        location = DeviceLocation(this).also { source ->
            source.refresh { status -> if (!status.endsWith("…")) Handler(Looper.getMainLooper()).post { jobFinished(params, false) } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { location?.stop(); return false }
    override fun onDestroy() { location?.stop(); super.onDestroy() }
}

val SkyPhase.imageRes: Int get() = when (this) {
    SkyPhase.DAWN -> R.drawable.sky_dawn
    SkyPhase.DAY -> R.drawable.sky_day
    SkyPhase.DUSK -> R.drawable.sky_dusk
    SkyPhase.NIGHT -> R.drawable.sky_night
}

val SkyPhase.titleRes: Int get() = when (this) {
    SkyPhase.DAWN -> R.string.phase_dawn
    SkyPhase.DAY -> R.string.phase_day
    SkyPhase.DUSK -> R.string.phase_dusk
    SkyPhase.NIGHT -> R.string.phase_night
}
val SolarEventType.titleRes: Int get() = when (this) {
    SolarEventType.DAWN_START -> R.string.event_dawn
    SolarEventType.SUNRISE -> R.string.event_sunrise
    SolarEventType.DAY_START -> R.string.event_day
    SolarEventType.DUSK_START -> R.string.event_dusk
    SolarEventType.SUNSET -> R.string.event_sunset
    SolarEventType.NIGHT_START -> R.string.event_night
}

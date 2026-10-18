package com.example.skywallpaper

import android.Manifest
import android.app.WallpaperColors
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.Instant
import kotlin.math.max

class SkyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SkyEngine()
    inner class SkyEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val repository = SkyRepository(this@SkyWallpaperService)
        private var location: DeviceLocation? = null
        private var bitmap: Bitmap? = null
        private var loadedImage: String? = null
        private var gradientColorMinute = Long.MIN_VALUE
        private var visible = false
        private var surfaceReady = false
        private var colors: WallpaperColors? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val tick = Runnable { redraw() }
        private val preferences = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            gradientColorMinute = Long.MIN_VALUE
            if (!repository.automatic || repository.manualLocation) location?.stop()
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    repository.onUserUnlocked()
                    scheduleLocationRefresh(this@SkyWallpaperService)
                }
                redraw()
                if (visible) refreshLocationIfNeeded()
            }
        }
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            repository.listen(preferences)
            registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_USER_UNLOCKED)
                addAction("android.location.PROVIDERS_CHANGED")
            }, Context.RECEIVER_NOT_EXPORTED)
        }
        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(tick)
            if (isVisible) {
                redraw()
                refreshLocationIfNeeded()
                scheduleLocationRefresh(this@SkyWallpaperService)
            } else location?.stop()
        }
        private fun refreshLocationIfNeeded() {
            if (!getSystemService(UserManager::class.java).isUserUnlocked) return
            val age = repository.updatedAt?.let { Instant.now().epochSecond - it.epochSecond } ?: Long.MAX_VALUE
            if (repository.automatic && !repository.manualLocation && age > 6 * 3600 &&
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val source = location ?: DeviceLocation(this@SkyWallpaperService).also { location = it }
                source.refresh { redraw() }
            }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceReady = true
            redraw()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }
        private fun redraw() {
            handler.removeCallbacks(tick)
            if (!surfaceReady || !visible) return
            val now = Instant.now()
            val phase = repository.phase(now)
            val store = repository.wallpapers
            val type = store.type
            val gradient = if (store.mode == WallpaperMode.GRADIENT) store.frame(now, repository.point, repository.automatic, phase, type) else null
            if (gradient != null) {
                bitmap?.recycle(); bitmap = null; loadedImage = null
                if (gradientColorMinute != now.epochSecond / 60) {
                    colors = WallpaperColors(Color.valueOf(gradient.stops[gradient.stops.size / 2].color),
                        Color.valueOf(gradient.stops.first().color), Color.valueOf(gradient.stops.last().color))
                    gradientColorMinute = now.epochSecond / 60
                    notifyColorsChanged()
                }
            } else {
                val file = store.imageFile(phase)
                val imageKey = file?.path ?: phase.name
                if (loadedImage != imageKey || bitmap == null) {
                    val next = file?.let { BitmapFactory.decodeFile(it.path) } ?: BitmapFactory.decodeResource(resources, phase.imageRes)
                    bitmap?.recycle()
                    bitmap = next
                    loadedImage = imageKey
                    colors = WallpaperColors.fromBitmap(next)
                    notifyColorsChanged()
                }
            }
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                canvas?.let { target ->
                    target.drawColor(Color.BLACK)
                    if (gradient != null) {
                        paint.isDither = true
                        paint.shader = GradientRenderer.shader(type, gradient, target.width.toFloat(), target.height.toFloat())
                        target.drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), paint)
                        paint.shader = null
                    } else bitmap?.let { image ->
                        val scale = max(target.width.toFloat() / image.width, target.height.toFloat() / image.height)
                        val w = image.width * scale
                        val h = image.height * scale
                        target.drawBitmap(image, null, RectF((target.width - w) / 2, (target.height - h) / 2,
                            (target.width + w) / 2, (target.height + h) / 2), paint)
                    }
                }
            } catch (_: IllegalArgumentException) { /* Surface removed by launcher. */ }
              finally { canvas?.let { surfaceHolder.unlockCanvasAndPost(it) } }
            val delay = repository.point?.takeIf { repository.automatic }?.let {
                SolarClock.nextTransition(now, it)?.instant?.toEpochMilli()?.minus(now.toEpochMilli())
            } ?: 60_000L
            handler.postDelayed(tick, if (gradient != null && repository.automatic && repository.point != null) 1_000L else delay.coerceIn(1_000L, 60_000L))
        }
        override fun onComputeColors(): WallpaperColors? = colors
        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            location?.stop()
            repository.unlisten(preferences)
            unregisterReceiver(receiver)
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }
    }
}

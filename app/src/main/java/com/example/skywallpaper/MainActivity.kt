package com.example.skywallpaper

import android.os.Bundle
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.skywallpaper.ui.theme.SkyWallpaperTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: SkyRepository
    private lateinit var location: DeviceLocation
    private val revision = mutableIntStateOf(0)
    private val status = mutableStateOf("")
    private val preferences = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision.intValue++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Let the Monet surface continue behind gesture and three-button navigation.
        window.isNavigationBarContrastEnforced = false
        window.decorView.isForceDarkAllowed = false
        repository = SkyRepository(this)
        location = DeviceLocation(this)
        repository.listen(preferences)
        setContent {
            SkyWallpaperTheme {
                SkyScreen(repository, revision.intValue, status.value, location::stop) {
                    location.refresh { status.value = it; revision.intValue++ }
                    scheduleLocationRefresh(this)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        revision.intValue++
        if (::repository.isInitialized) {
            if (!repository.manualLocation) location.refresh { status.value = it; revision.intValue++ }
            scheduleLocationRefresh(this)
        }
    }
    override fun onPause() { location.stop(); super.onPause() }
    override fun onDestroy() { repository.unlisten(preferences); location.stop(); super.onDestroy() }
}

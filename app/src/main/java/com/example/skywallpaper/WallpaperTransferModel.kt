package com.example.skywallpaper

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Navigation-scoped transfers survive activity recreation and system document pickers. */
internal class WallpaperTransferModel(context: Context, store: WallpaperStore, private val saved: SavedStateHandle) : ViewModel() {
    private val archive = WallpaperArchive(context.applicationContext, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var busy by mutableStateOf(false)
        private set
    var pending by mutableStateOf<String?>(saved["pending"])
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    var importCount by mutableIntStateOf(0)
        private set

    fun clearMessage() { message = null }
    private fun pending(id: String?) { pending = id; saved["pending"] = id }
    private fun run(failure: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { message = failure }
            finally { busy = false }
        }
    }
    fun export(uri: Uri) = run(R.string.config_export_failed) {
        archive.export(uri); message = R.string.config_exported
    }
    fun prepare(uri: Uri) = run(R.string.config_import_failed) {
        pending?.let(archive::discard)
        pending(null)
        pending(archive.prepare(uri))
    }
    fun confirm() {
        val id = pending ?: return
        run(R.string.config_import_failed) {
            try { archive.apply(id); importCount++; message = R.string.config_imported }
            finally { archive.discard(id); pending(null) }
        }
    }
    fun cancel() {
        if (busy) return
        pending?.let(archive::discard)
        pending(null)
    }
    override fun onCleared() {
        scope.cancel()
        pending?.let(archive::discard)
    }
}

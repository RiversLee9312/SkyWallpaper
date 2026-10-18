package com.example.skywallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WallpaperArtwork(store: WallpaperStore, phase: SkyPhase, revision: Int, modifier: Modifier,
                     description: String? = null, liveFrame: GradientFrame? = null) {
    if (store.mode == WallpaperMode.GRADIENT) {
        val type = store.type
        GradientArtwork(type, liveFrame ?: GradientFrame.from(store.gradient(phase, type)), modifier, description)
    } else {
        val file = remember(revision, phase) { store.imageFile(phase) }
        val bitmap by produceState<Bitmap?>(null, file?.path) {
            value = file?.let { withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(it.path, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            } }
        }
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier, contentScale = ContentScale.Crop)
        else Image(painterResource(phase.imageRes), description, modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
fun GradientArtwork(type: GradientType, frame: GradientFrame, modifier: Modifier, description: String? = null) {
    Canvas(if (description != null) modifier.semantics { contentDescription = description } else modifier) {
        val paint = GradientRenderer.paint(type, frame, size.width, size.height)
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

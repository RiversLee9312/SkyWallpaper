package com.example.skywallpaper

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.abs
import kotlin.math.min

object GradientRenderer {
    fun shader(type: GradientType, frame: GradientFrame, width: Float, height: Float): Shader {
        val colors = frame.stops.map { it.color }.toIntArray()
        val positions = frame.stops.map { it.position }.toFloatArray()
        return when (type) {
            GradientType.LINEAR -> {
                val x0 = frame.start.x * width
                val y0 = frame.start.y * height
                val x1 = frame.end.x * width
                val y1 = frame.end.y * height
                // Two valid presets can cross while their geometry is interpolated.
                val endY = if (abs(x1 - x0) + abs(y1 - y0) < .01f) y1 + .01f else y1
                LinearGradient(x0, y0, x1, endY, colors, positions, Shader.TileMode.CLAMP)
            }
            GradientType.RADIAL -> RadialGradient(frame.center.x * width, frame.center.y * height,
                (frame.radius * min(width, height)).coerceAtLeast(1f), colors, positions, Shader.TileMode.CLAMP)
            GradientType.SWEEP -> SweepGradient(frame.center.x * width, frame.center.y * height, colors, positions)
        }
    }

    fun paint(type: GradientType, frame: GradientFrame, width: Float, height: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        shader = shader(type, frame, width, height)
    }
}

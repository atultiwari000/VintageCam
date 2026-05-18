package com.vintagecam.camera.capture

import android.graphics.Color
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.floor

internal class CubeLut private constructor(
    private val size: Int,
    private val values: FloatArray,
    private val domainMin: FloatArray,
    private val domainMax: FloatArray,
) {
    fun applyTo(pixels: IntArray, strength: Float) {
        val mix = strength.coerceIn(0f, 1f)
        if (mix <= 0f) return

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            val mapped = sample(r, g, b)

            pixels[i] = Color.argb(
                Color.alpha(color),
                lerp(Color.red(color) / 255f, mapped[0], mix).toByteColor(),
                lerp(Color.green(color) / 255f, mapped[1], mix).toByteColor(),
                lerp(Color.blue(color) / 255f, mapped[2], mix).toByteColor(),
            )
        }
    }

    private fun sample(r: Float, g: Float, b: Float): FloatArray {
        val rr = normalize(r, 0)
        val gg = normalize(g, 1)
        val bb = normalize(b, 2)

        val r0 = floor(rr).toInt().coerceIn(0, size - 1)
        val g0 = floor(gg).toInt().coerceIn(0, size - 1)
        val b0 = floor(bb).toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceAtMost(size - 1)
        val g1 = (g0 + 1).coerceAtMost(size - 1)
        val b1 = (b0 + 1).coerceAtMost(size - 1)

        val tr = rr - r0
        val tg = gg - g0
        val tb = bb - b0

        val c000 = colorAt(r0, g0, b0)
        val c100 = colorAt(r1, g0, b0)
        val c010 = colorAt(r0, g1, b0)
        val c110 = colorAt(r1, g1, b0)
        val c001 = colorAt(r0, g0, b1)
        val c101 = colorAt(r1, g0, b1)
        val c011 = colorAt(r0, g1, b1)
        val c111 = colorAt(r1, g1, b1)

        return FloatArray(3) { channel ->
            val c00 = lerp(c000[channel], c100[channel], tr)
            val c10 = lerp(c010[channel], c110[channel], tr)
            val c01 = lerp(c001[channel], c101[channel], tr)
            val c11 = lerp(c011[channel], c111[channel], tr)
            val c0 = lerp(c00, c10, tg)
            val c1 = lerp(c01, c11, tg)
            lerp(c0, c1, tb).coerceIn(0f, 1f)
        }
    }

    private fun normalize(value: Float, channel: Int): Float {
        val min = domainMin[channel]
        val max = domainMax[channel]
        val normalized = if (max > min) (value - min) / (max - min) else value
        return normalized.coerceIn(0f, 1f) * (size - 1)
    }

    private fun colorAt(r: Int, g: Int, b: Int): FloatArray {
        val base = ((b * size * size) + (g * size) + r) * 3
        return floatArrayOf(values[base], values[base + 1], values[base + 2])
    }

    companion object {
        fun parse(input: InputStream): CubeLut? {
            var size = 0
            val domainMin = floatArrayOf(0f, 0f, 0f)
            val domainMax = floatArrayOf(1f, 1f, 1f)
            val numbers = mutableListOf<Float>()

            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.substringBefore("#").trim()
                    if (line.isBlank()) return@forEach
                    val parts = line.split(Regex("\\s+"))
                    when (parts[0].uppercase()) {
                        "TITLE", "LUT_1D_SIZE" -> Unit
                        "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        "DOMAIN_MIN" -> parts.readTripletInto(domainMin)
                        "DOMAIN_MAX" -> parts.readTripletInto(domainMax)
                        else -> {
                            if (parts.size >= 3) {
                                val r = parts[0].toFloatOrNull()
                                val g = parts[1].toFloatOrNull()
                                val b = parts[2].toFloatOrNull()
                                if (r != null && g != null && b != null) {
                                    numbers += r.coerceIn(0f, 1f)
                                    numbers += g.coerceIn(0f, 1f)
                                    numbers += b.coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                }
            }

            if (size <= 1) return null
            if (numbers.size < size * size * size * 3) return null
            return CubeLut(
                size = size,
                values = numbers.take(size * size * size * 3).toFloatArray(),
                domainMin = domainMin,
                domainMax = domainMax,
            )
        }

        private fun List<String>.readTripletInto(target: FloatArray) {
            for (i in 0..2) {
                target[i] = getOrNull(i + 1)?.toFloatOrNull() ?: target[i]
            }
        }
    }
}

private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

private fun Float.toByteColor(): Int = (coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)

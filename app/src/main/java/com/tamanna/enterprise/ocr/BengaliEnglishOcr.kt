package com.tamanna.enterprise.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Hybrid memo OCR:
 * - ML Kit bundled Latin model: fast/strong for English and numbers.
 * - Tesseract Bengali model: Bengali + mixed-script fallback.
 *
 * The image is normalized before recognition because OCR accuracy depends
 * heavily on sufficient pixel data, focus, scale and contrast.
 */
object BengaliEnglishOcr {

    private const val TESSDATA_DIR = "tessdata"
    private const val LANG = "ben+eng"
    private const val MAX_SIDE = 2400

    fun recognize(context: Context, source: Bitmap): String {
        val bitmap = prepareBitmap(source)
        return try {
            val latin = recognizeLatin(bitmap)
            val tesseract = recognizeTesseract(context, bitmap)
            mergeResults(latin, tesseract)
        } finally {
            if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun recognizeLatin(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                20,
                TimeUnit.SECONDS
            )
            result.text.trim()
        } catch (_: Exception) {
            ""
        } finally {
            recognizer.close()
        }
    }

    private fun recognizeTesseract(context: Context, bitmap: Bitmap): String {
        val dataPath = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessDataPath = File(dataPath, TESSDATA_DIR).apply { mkdirs() }

        copyAssetIfNeeded(context, "tessdata/ben.traineddata", File(tessDataPath, "ben.traineddata"))
        copyAssetIfNeeded(context, "tessdata/eng.traineddata", File(tessDataPath, "eng.traineddata"))

        val api = TessBaseAPI()
        return try {
            if (!api.init(dataPath.absolutePath, LANG)) return ""

            val results = linkedSetOf<String>()
            val variants = listOf(
                bitmap,
                makeGray(bitmap),
                makeHighContrast(bitmap)
            )

            for ((index, variant) in variants.withIndex()) {
                api.pageSegMode = when (index) {
                    0 -> TessBaseAPI.PageSegMode.PSM_AUTO
                    1 -> TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                    else -> TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                }
                api.setImage(variant)
                api.utF8Text?.trim()?.takeIf { it.isNotBlank() }?.let(results::add)

                if (variant !== bitmap && !variant.isRecycled) variant.recycle()
            }

            results.maxByOrNull { score(it) }.orEmpty()
        } finally {
            api.end()
        }
    }

    private fun prepareBitmap(source: Bitmap): Bitmap {
        val rotated = if (source.width < source.height) source else {
            // Keep landscape memos as-is; do not blindly rotate user documents.
            source
        }

        val scale = MAX_SIDE.toFloat() / maxOf(rotated.width, rotated.height)
        if (scale >= 1f) return rotated

        val width = (rotated.width * scale).toInt().coerceAtLeast(1)
        val height = (rotated.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(rotated, width, height, true)
    }

    private fun makeGray(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun makeHighContrast(source: Bitmap): Bitmap {
        val gray = makeGray(source)
        val out = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(gray.width * gray.height)
        gray.getPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)

        for (i in pixels.indices) {
            val luminance = Color.red(pixels[i])
            val value = when {
                luminance < 90 -> 0
                luminance > 190 -> 255
                else -> ((luminance - 90) * 255 / 100).coerceIn(0, 255)
            }
            pixels[i] = Color.rgb(value, value, value)
        }

        out.setPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)
        gray.recycle()
        return out
    }

    private fun mergeResults(latin: String, tesseract: String): String {
        if (latin.isBlank()) return tesseract
        if (tesseract.isBlank()) return latin
        if (latin == tesseract) return latin

        // Keep both recognizers' lines. This prevents a strong Bengali result
        // from being lost just because ML Kit cannot read Bengali script.
        return (latin.lines() + tesseract.lines())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun score(text: String): Int {
        val bengali = text.count { it in '\u0980'..'\u09FF' }
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val digits = text.count { it.isDigit() || it in '০'..'৯' }
        return text.length + bengali * 4 + latin * 2 + digits * 3
    }

    private fun copyAssetIfNeeded(context: Context, assetName: String, destination: File) {
        if (destination.exists() && destination.length() > 0) return
        destination.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }
}

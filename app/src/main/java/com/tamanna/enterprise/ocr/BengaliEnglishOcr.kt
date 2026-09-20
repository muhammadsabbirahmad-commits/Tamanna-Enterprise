package com.tamanna.enterprise.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

object BengaliEnglishOcr {

    private const val TESSDATA_DIR = "tessdata"
    private const val LANG = "ben+eng"

    fun recognize(context: Context, bitmap: Bitmap): String {
        val dataPath = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessDataPath = File(dataPath, TESSDATA_DIR).apply { mkdirs() }

        copyAssetIfNeeded(context, "tessdata/ben.traineddata", File(tessDataPath, "ben.traineddata"))
        copyAssetIfNeeded(context, "tessdata/eng.traineddata", File(tessDataPath, "eng.traineddata"))

        val api = TessBaseAPI()
        return try {
            if (!api.init(dataPath.absolutePath, LANG)) {
                ""
            } else {
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                api.setImage(bitmap)
                api.utF8Text?.trim().orEmpty()
            }
        } finally {
            api.end()
        }
    }

    private fun copyAssetIfNeeded(context: Context, assetName: String, destination: File) {
        if (destination.exists() && destination.length() > 0) return
        destination.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}

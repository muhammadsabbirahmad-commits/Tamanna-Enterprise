package com.tamanna.enterprise.purchase

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.ocr.BengaliEnglishOcr
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.product.ProductStorage
import java.util.Locale

class MemoScannerActivity : ComponentActivity() {
    private var status by mutableStateOf("ছবি বা PDF নির্বাচন করুন")
    private var recognizedText by mutableStateOf("")
    private var matches by mutableStateOf<List<MemoMatch>>(emptyList())

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processImage(uri)
    }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ক্রয় মেমো স্ক্যান", style = MaterialTheme.typography.headlineSmall)
                    Text("পণ্য কোড মিলিয়ে মেমো থেকে পরিমাণ ও প্রতি-ইউনিট ক্রয়মূল্যের সম্ভাব্য তথ্য বের করা হবে। সংরক্ষণের আগে আপনি যাচাই/সংশোধন করতে পারবেন।")

                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("মেমোর ছবি নির্বাচন করুন")
                    }
                    Button(onClick = { pdfPicker.launch("application/pdf") }, modifier = Modifier.fillMaxWidth()) {
                        Text("মেমোর PDF নির্বাচন করুন")
                    }

                    Text(status)

                    if (recognizedText.isNotBlank()) {
                        Text("OCR ফলাফল", style = MaterialTheme.typography.titleMedium)
                        Text(recognizedText.take(3000))
                    }

                    if (matches.isNotEmpty()) {
                        Text("মিল ও মেমো-তথ্য যাচাই", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matches, key = { it.product.code }) { match ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(match.product.name, style = MaterialTheme.typography.titleMedium)
                                        Text("কোড: " + match.product.code)
                                        Text("মেমো থেকে পরিমাণ: " + (match.quantity?.toString() ?: "নিশ্চিতভাবে শনাক্ত হয়নি"))
                                        Text("মেমো থেকে প্রতি-ইউনিট ক্রয়মূল্য: " + (match.unitPrice?.let { "৳ %.2f".format(Locale.US, it) } ?: "নিশ্চিতভাবে শনাক্ত হয়নি"))
                                        Text(
                                            if (match.quantity != null && match.unitPrice != null)
                                                "✓ কোড, পরিমাণ ও ক্রয়মূল্য পাওয়া গেছে — সংরক্ষণের আগে যাচাই করুন।"
                                            else
                                                "⚠️ কোড মিলেছে, কিন্তু পরিমাণ/ক্রয়মূল্যের তথ্য নিশ্চিত নয়।"
                                        )
                                        Button(onClick = {
                                            val intent = Intent()
                                                .putExtra(EXTRA_PRODUCT_CODE, match.product.code)
                                                .putExtra(EXTRA_QUANTITY, match.quantity ?: 0)
                                                .putExtra(EXTRA_PURCHASE_PRICE, match.unitPrice ?: 0.0)
                                                .putExtra(EXTRA_MEMO_VERIFIED_DATA, true)
                                            setResult(Activity.RESULT_OK, intent)
                                            finish()
                                        }) {
                                            Text("এই তথ্য যাচাই করে ক্রয় যোগ করুন")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processImage(uri: Uri) {
        status = "ছবি থেকে লেখা পড়া হচ্ছে..."
        recognizedText = ""
        matches = emptyList()
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                status = "ছবিটি খোলা যায়নি। অন্য ছবি চেষ্টা করুন।"
            } else {
                runOcr(bitmap)
            }
        } catch (e: Exception) {
            status = "ছবি পড়তে সমস্যা হয়েছে। অন্য ছবি চেষ্টা করুন।"
        }
    }

    private fun processPdf(uri: Uri) {
        status = "PDF-এর প্রথম পৃষ্ঠা থেকে লেখা পড়া হচ্ছে..."
        recognizedText = ""
        matches = emptyList()
        try {
            val bitmap = renderFirstPdfPage(uri)
            if (bitmap == null) {
                status = "PDF-এর প্রথম পৃষ্ঠা পড়া যায়নি।"
                return
            }
            runOcr(bitmap)
        } catch (e: Exception) {
            status = "PDF পড়তে সমস্যা হয়েছে। অন্য PDF চেষ্টা করুন।"
        }
    }

    private fun renderFirstPdfPage(uri: Uri): Bitmap? {
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val width = (page.width * 2).coerceAtMost(2400)
                    val height = (page.height * 2).coerceAtMost(3200)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        status = "বাংলা ও ইংরেজি লেখা পড়া হচ্ছে..."
        Thread {
            val text = try {
                BengaliEnglishOcr.recognize(this, bitmap)
            } catch (e: Exception) {
                ""
            }
            runOnUiThread {
                recognizedText = text.trim()
                if (recognizedText.isBlank()) {
                    status = "বাংলা/ইংরেজি কোনো লেখা শনাক্ত করা যায়নি। পরিষ্কার ও সোজা ছবি ব্যবহার করুন।"
                    return@runOnUiThread
                }

                val products = ProductStorage.getProducts(this)
                val found = products.mapNotNull { product ->
                    if (!recognizedText.contains(product.code, ignoreCase = true)) return@mapNotNull null
                    val line = recognizedText.lineSequence()
                        .firstOrNull { it.contains(product.code, ignoreCase = true) }
                        .orEmpty()
                    MemoMatch(
                        product = product,
                        quantity = extractQuantity(line),
                        unitPrice = extractUnitPrice(line, recognizedText)
                    )
                }
                matches = found
                status = if (found.isEmpty()) {
                    "OCR সম্পন্ন হয়েছে, কিন্তু সংরক্ষিত পণ্য কোডের সাথে হুবহু মিল পাওয়া যায়নি।"
                } else {
                    found.size.toString() + "টি পণ্য কোড মিলেছে। এখন মেমোর পরিমাণ ও ক্রয়মূল্য যাচাই করুন।"
                }
            }
        }.start()
    }

    private fun extractQuantity(line: String): Int? {
        val normalized = normalizeDigits(line)
        val labeled = Regex("""(?i)(qty|quantity|pcs|piece|pieces|পরিমাণ|পিস|সংখ্যা)\\s*[:=-]?\\s*(\\d+)""")
            .find(normalized)?.groupValues?.getOrNull(2)?.toIntOrNull()
        if (labeled != null && labeled > 0) return labeled
        return null
    }

    private fun extractUnitPrice(line: String, fullText: String): Double? {
        val normalizedLine = normalizeDigits(line)
        val labeled = Regex("""(?i)(unit\\s*price|rate|price|purchase\\s*price|ক্রয়মূল্য|ক্রয়মূল্য|দর|মূল্য)\\s*[:=-]?\\s*(?:৳|tk|bdt)?\\s*(\\d+(?:\\.\\d+)?)""")
            .find(normalizedLine)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (labeled != null && labeled >= 0) return labeled

        val nearby = normalizeDigits(fullText)
            .lineSequence()
            .firstOrNull { it.contains(line) && Regex("""\\d+(?:\\.\\d+)?""").findAll(it).count() >= 2 }
        val nums = Regex("""d+(?:.d+)?""").findAll(nearby.orEmpty()).map { it.value.toDoubleOrNull() }.filterNotNull().toList()
        return nums.lastOrNull()?.takeIf { it >= 0 }
    }

    private fun normalizeDigits(value: String): String =
        value.map {
            when (it) {
                '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                else -> it
            }
        }.joinToString("")

    data class MemoMatch(
        val product: Product,
        val quantity: Int?,
        val unitPrice: Double?
    )

    companion object {
        const val EXTRA_PRODUCT_CODE = "memo_product_code"
        const val EXTRA_QUANTITY = "memo_quantity"
        const val EXTRA_PURCHASE_PRICE = "memo_purchase_price"
        const val EXTRA_MEMO_VERIFIED_DATA = "memo_verified_data"
    }
}
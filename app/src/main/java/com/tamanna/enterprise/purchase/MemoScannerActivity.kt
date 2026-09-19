package com.tamanna.enterprise.purchase

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.product.ProductStorage

class MemoScannerActivity : ComponentActivity() {
    private var status by mutableStateOf("ছবি বা PDF নির্বাচন করুন")
    private var recognizedText by mutableStateOf("")
    private var matches by mutableStateOf<List<Product>>(emptyList())

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
                    Text("মেমোর ছবি বা PDF থেকে লেখা পড়ে সংরক্ষিত পণ্য কোডের সাথে হুবহু মিল খোঁজা হবে।")
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
                        Text("মিল পাওয়া পণ্য", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matches, key = { it.code }) { product ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                                        Text("কোড: " + product.code)
                                        Button(onClick = {
                                            setResult(
                                                Activity.RESULT_OK,
                                                Intent().putExtra(EXTRA_PRODUCT_CODE, product.code)
                                            )
                                            finish()
                                        }) {
                                            Text("এই পণ্য দিয়ে ক্রয় যোগ করুন")
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
            runOcr(InputImage.fromFilePath(this, uri))
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
            runOcr(InputImage.fromBitmap(bitmap, 0))
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

    private fun runOcr(image: InputImage) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { result ->
                recognizedText = result.text.trim()
                if (recognizedText.isBlank()) {
                    status = "কোনো লেখা শনাক্ত করা যায়নি।"
                    return@addOnSuccessListener
                }
                val products = ProductStorage.getProducts(this)
                val exactMatches = products.filter { product -> recognizedText.contains(product.code) }
                matches = exactMatches
                status = if (exactMatches.isEmpty()) {
                    "OCR সম্পন্ন হয়েছে, কিন্তু সংরক্ষিত পণ্য কোডের সাথে হুবহু মিল পাওয়া যায়নি।"
                } else {
                    exactMatches.size.toString() + "টি পণ্য কোডের হুবহু মিল পাওয়া গেছে।"
                }
            }
            .addOnFailureListener {
                status = "OCR করতে সমস্যা হয়েছে। পরিষ্কার ছবি ব্যবহার করুন।"
            }
    }

    companion object {
        const val EXTRA_PRODUCT_CODE = "memo_product_code"
    }
}
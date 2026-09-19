package com.tamanna.enterprise.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class BarcodeScannerActivity : ComponentActivity() {
    private val scannerLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents?.trim().orEmpty()
        if (code.isNotBlank()) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CODE, code))
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply {
            setPrompt("পণ্যের বারকোড / QR কোড স্ক্যান করুন")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }

        scannerLauncher.launch(options)
    }

    companion object {
        const val EXTRA_CODE = "barcode_code"
    }
}

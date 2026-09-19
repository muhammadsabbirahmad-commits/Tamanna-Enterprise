package com.tamanna.enterprise.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class BarcodeScannerActivity : Activity() {
    private val scannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
        } else {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_CODE, result.contents.trim())
            )
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

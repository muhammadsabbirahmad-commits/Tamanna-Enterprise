package com.tamanna.enterprise.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract

class BarcodeScannerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scanner = ScanContract()
        val options = ScanOptions().apply {
            setPrompt("পণ্যের বারকোড / QR কোড স্ক্যান করুন")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }

        scanner.createIntent(this, options).let { intent ->
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SCAN)
        }
    }

    @Deprecated("Legacy activity result API used for scanner compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCAN && resultCode == Activity.RESULT_OK) {
            val code = data?.getStringExtra("SCAN_RESULT")?.trim().orEmpty()
            if (code.isNotBlank()) {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CODE, code))
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        private const val REQUEST_SCAN = 4100
        const val EXTRA_CODE = "barcode_code"
    }
}

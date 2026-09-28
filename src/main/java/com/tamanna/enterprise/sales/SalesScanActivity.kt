package com.tamanna.enterprise.sales

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.scanner.BarcodeScannerActivity

class SalesScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("বিক্রয়ের জন্য বারকোড স্ক্যান")
                Button(onClick = {
                    startActivityForResult(
                        Intent(this@SalesScanActivity, BarcodeScannerActivity::class.java),
                        REQUEST_SCAN
                    )
                }) {
                    Text("📷 স্ক্যান শুরু করুন")
                }
            }
        }
    }

    @Deprecated("Use Activity Result APIs for new code; kept simple for minSdk compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCAN || resultCode != Activity.RESULT_OK) return

        val code = data?.getStringExtra(BarcodeScannerActivity.EXTRA_CODE)?.trim().orEmpty()
        val product = ProductStorage.getProducts(this).firstOrNull {
            it.code.equals(code, ignoreCase = true)
        }

        if (product != null) {
            startActivity(
                Intent(this, NewSaleActivity::class.java)
                    .putExtra(NewSaleActivity.EXTRA_PRODUCT_CODE, product.code)
            )
        }
    }

    companion object {
        private const val REQUEST_SCAN = 4101
    }
}

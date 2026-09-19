package com.tamanna.enterprise.purchase

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PurchaseActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent {
            PurchaseScreen(
                onAddPurchase = { startActivity(Intent(this, AddPurchaseActivity::class.java)) },
                onMemoScan = { startActivityForResult(Intent(this, MemoScannerActivity::class.java), REQUEST_MEMO_SCAN) }
            )
        }
    }

    @Deprecated("Use Activity Result API in a future cleanup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEMO_SCAN && resultCode == RESULT_OK) {
            val code = data?.getStringExtra(MemoScannerActivity.EXTRA_PRODUCT_CODE).orEmpty()
            if (code.isNotBlank()) {
                val quantity = data?.getIntExtra(MemoScannerActivity.EXTRA_QUANTITY, 0) ?: 0
                val purchasePrice = data?.getDoubleExtra(MemoScannerActivity.EXTRA_PURCHASE_PRICE, 0.0) ?: 0.0
                val memoVerified = data?.getBooleanExtra(MemoScannerActivity.EXTRA_MEMO_VERIFIED_DATA, false) ?: false

                startActivity(
                    Intent(this, AddPurchaseActivity::class.java)
                        .putExtra(AddPurchaseActivity.EXTRA_PRODUCT_CODE, code)
                        .putExtra(AddPurchaseActivity.EXTRA_QUANTITY, quantity)
                        .putExtra(AddPurchaseActivity.EXTRA_PURCHASE_PRICE, purchasePrice)
                        .putExtra(AddPurchaseActivity.EXTRA_MEMO_VERIFIED_DATA, memoVerified)
                )
            }
        }
    }

    companion object { private const val REQUEST_MEMO_SCAN = 7101 }
}

@Composable
private fun PurchaseScreen(onAddPurchase: () -> Unit, onMemoScan: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var purchases by remember { mutableStateOf(PurchaseStorage.getPurchases(context)) }
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        purchases = PurchaseStorage.getPurchases(context)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddPurchase, modifier = Modifier.weight(1f)) { Text("নতুন ক্রয়") }
                    Button(onClick = onMemoScan, modifier = Modifier.weight(1f)) { Text("মেমো স্ক্যান") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (purchases.isEmpty()) {
                    Text("এখনো কোনো ক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(purchases, key = { it.id }) { purchase ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { selectedPurchase = purchase }) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + purchase.productCode + "  •  " + purchase.date, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
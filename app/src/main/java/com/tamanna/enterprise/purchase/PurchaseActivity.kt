package com.tamanna.enterprise.purchase

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            PurchaseScreen {
                startActivity(Intent(this, AddPurchaseActivity::class.java))
            }
        }
    }
}

@Composable
private fun PurchaseScreen(onAddPurchase: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var purchases by remember { mutableStateOf(PurchaseStorage.getPurchases(context)) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        purchases = PurchaseStorage.getPurchases(context)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ক্রয় ব্যবস্থাপনা", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = onAddPurchase) { Text("নতুন ক্রয়") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (purchases.isEmpty()) {
                    Text("এখনো কোনো ক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(purchases, key = { it.id }) { purchase ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: ${purchase.productCode}")
                                    Text("তারিখ: ${purchase.date}")
                                    Text("পরিমাণ: ${purchase.quantity}")
                                    Text("ক্রয়মূল্য: ৳ ${"%.2f".format(purchase.purchasePrice)}")
                                    Text("মোট: ৳ ${"%.2f".format(purchase.quantity * purchase.purchasePrice)}")
                                    if (purchase.supplier.isNotBlank()) Text("সরবরাহকারী: ${purchase.supplier}")
                                    if (purchase.memoNumber.isNotBlank()) Text("মেমো: ${purchase.memoNumber}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

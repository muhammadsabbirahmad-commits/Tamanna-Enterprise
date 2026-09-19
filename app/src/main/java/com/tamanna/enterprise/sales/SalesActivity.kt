package com.tamanna.enterprise.sales

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SalesActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent {
            SalesScreen {
                startActivity(Intent(this, NewSaleActivity::class.java))
            }
        }
    }
}

@Composable
private fun SalesScreen(onNewSale: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var sales by remember { mutableStateOf(SalesStorage.getSales(context)) }

    LaunchedEffect(Unit) {
        sales = SalesStorage.getSales(context)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("বিক্রয় ব্যবস্থাপনা", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = onNewSale) { Text("নতুন বিক্রয়") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (sales.isEmpty()) {
                    Text("এখনো কোনো বিক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sales, key = { it.id }) { sale ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(sale.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + sale.productCode)
                                    Text("তারিখ: " + sale.date)
                                    Text("পরিমাণ: " + sale.quantity)
                                    Text("বিক্রয়মূল্য: ৳ " + "%.2f".format(sale.salePrice))
                                    Text("মোট বিক্রয়: ৳ " + "%.2f".format(sale.quantity * sale.salePrice))
                                    Text("লাভ: ৳ " + "%.2f".format(sale.quantity * (sale.salePrice - sale.purchasePrice)))
                                    if (sale.customer.isNotBlank()) Text("ক্রেতা: " + sale.customer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
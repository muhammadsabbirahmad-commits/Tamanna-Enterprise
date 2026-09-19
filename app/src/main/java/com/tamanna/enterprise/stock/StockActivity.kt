package com.tamanna.enterprise.stock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.product.ProductStorage

class StockActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent { StockScreen() }
    }
    selectedProduct?.let { p -> AlertDialog(onDismissRequest = { selectedProduct = null }, title = { Text(p.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("কোড: " + p.code); Text("স্টক: " + p.stockQuantity + " ইউনিট"); Text("ক্রয়মূল্য: ৳ %.2f".format(p.purchasePrice)); Text("বিক্রয়মূল্য: ৳ %.2f".format(p.salePrice)); Text(when { p.stockQuantity <= 0 -> "স্ট্যাটাস: স্টক শেষ"; p.stockQuantity <= 5 -> "স্ট্যাটাস: কম স্টক"; else -> "স্ট্যাটাস: পর্যাপ্ত স্টক" }) } }, confirmButton = { Button({ selectedProduct = null }) { Text("বন্ধ") } }) }
}

@Composable
private fun StockScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    val products = ProductStorage.getProducts(context)
    val filtered = products.filter {
        query.isBlank() ||
            it.code.contains(query.trim(), ignoreCase = true) ||
            it.name.contains(query.trim(), ignoreCase = true)
    }.sortedBy { it.name.lowercase() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("স্টক ব্যবস্থাপনা", style = MaterialTheme.typography.headlineSmall)
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("পণ্য কোড বা নাম দিয়ে খুঁজুন") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("মোট পণ্য: " + products.size)
                    Text("মোট ইউনিট: " + products.sumOf { it.stockQuantity })
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Text("কোনো পণ্য পাওয়া যায়নি।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.code }) { product ->
                            Card(Modifier.fillMaxWidth().clickable { selectedProduct = product }) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + product.code + "  •  স্টক: " + product.stockQuantity, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

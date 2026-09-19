package com.tamanna.enterprise.product

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ProductActivity : ComponentActivity() {
    private var products by mutableStateOf(emptyList<Product>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadProducts()
        setContent {
            ProductScreen(
                products = products,
                onAddProductClick = {
                    startActivity(Intent(this, AddProductActivity::class.java))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun loadProducts() {
        products = ProductStorage.getProducts(this)
    }
}

@androidx.compose.runtime.Composable
fun ProductScreen(
    products: List<Product>,
    onAddProductClick: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("পণ্য ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onAddProductClick, modifier = Modifier.fillMaxWidth()) {
                    Text("নতুন পণ্য যোগ করুন")
                }
                Text("পণ্যের তালিকা", style = MaterialTheme.typography.titleLarge)
                if (products.isEmpty()) {
                    Text("এখনও কোনো পণ্য যোগ করা হয়নি।")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(products) { item ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Code: " + item.code)
                                    Text("ক্রয়মূল্য: ৳ " + item.purchasePrice)
                                    Text("বিক্রয়মূল্য: ৳ " + item.salePrice)
                                    Text("স্টক: " + item.stockQuantity)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
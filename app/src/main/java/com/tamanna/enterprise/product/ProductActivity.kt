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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
                },
                onProductsChanged = { loadProducts() }
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
    onAddProductClick: () -> Unit,
    onProductsChanged: () -> Unit
) {
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var stockProduct by remember { mutableStateOf<Product?>(null) }
    var deleteProduct by remember { mutableStateOf<Product?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                        items(products, key = { it.code }) { item ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Code: " + item.code)
                                    Text("ক্রয়মূল্য: ৳ " + item.purchasePrice)
                                    Text("বিক্রয়মূল্য: ৳ " + item.salePrice)
                                    Text("স্টক: " + item.stockQuantity)

                                    Button(onClick = { editingProduct = item }) {
                                        Text("Edit")
                                    }
                                    Button(onClick = { stockProduct = item }) {
                                        Text("Stock In / Out")
                                    }
                                    Button(onClick = { deleteProduct = item }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { editingProduct = null },
            onSaved = {
                editingProduct = null
                onProductsChanged()
            }
        )
    }

    stockProduct?.let { product ->
        StockDialog(
            product = product,
            onDismiss = { stockProduct = null },
            onSaved = {
                stockProduct = null
                onProductsChanged()
            }
        )
    }

    deleteProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteProduct = null },
            title = { Text("পণ্য মুছে ফেলবেন?") },
            text = { Text(product.name + " (" + product.code + ") স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                Button(onClick = {
                    ProductStorage.deleteProduct(context, product.code)
                    deleteProduct = null
                    onProductsChanged()
                }) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { deleteProduct = null }) { Text("Cancel") }
            }
        )
    }
}

@androidx.compose.runtime.Composable
fun EditProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember(product.code) { mutableStateOf(product.name) }
    var purchase by remember(product.code) { mutableStateOf(product.purchasePrice.toString()) }
    var sale by remember(product.code) { mutableStateOf(product.salePrice.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পণ্য Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("পণ্যের নাম") })
                OutlinedTextField(purchase, { purchase = it }, label = { Text("ক্রয়মূল্য") })
                OutlinedTextField(sale, { sale = it }, label = { Text("বিক্রয়মূল্য") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = purchase.toDoubleOrNull()
                val s = sale.toDoubleOrNull()
                if (name.isNotBlank() && p != null && p >= 0 && s != null && s >= 0) {
                    ProductStorage.updateProduct(
                        context = androidx.compose.ui.platform.LocalContext.current,
                        product = product.copy(name = name.trim(), purchasePrice = p, salePrice = s)
                    )
                    onSaved()
                }
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

@androidx.compose.runtime.Composable
fun StockDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var quantity by remember(product.code) { mutableStateOf("") }
    var add by remember(product.code) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock In / Out") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("বর্তমান স্টক: " + product.stockQuantity)
                Button(onClick = { add = true }) { Text("Stock In") }
                Button(onClick = { add = false }) { Text("Stock Out") }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("পরিমাণ") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull()
                if (q != null && q > 0) {
                    val newStock = if (add) product.stockQuantity + q else product.stockQuantity - q
                    if (newStock >= 0) {
                        ProductStorage.updateStock(context, product.code, newStock)
                        onSaved()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}
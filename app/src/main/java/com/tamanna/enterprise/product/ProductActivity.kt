package com.tamanna.enterprise.product

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
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
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class ProductActivity : ComponentActivity() {
    private var products by mutableStateOf(emptyList<Product>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadProducts()
        val canWrite = SecurityStorage.canWrite(this)
        setContent {
            ProductScreen(
                products = products,
                canWrite = canWrite,
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
    canWrite: Boolean,
    onAddProductClick: () -> Unit,
    onProductsChanged: () -> Unit
) {
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var stockProduct by remember { mutableStateOf<Product?>(null) }
    var deleteProduct by remember { mutableStateOf<Product?>(null) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var deletePin by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf("") }
    val context = LocalContext.current

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("পণ্য ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)

                if (canWrite) {
                    Button(onClick = onAddProductClick, modifier = Modifier.fillMaxWidth()) {
                        Text("নতুন পণ্য যোগ করুন")
                    }
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
                            Card(modifier = Modifier.fillMaxWidth().clickable { selectedProduct = item }) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + item.code, style = MaterialTheme.typography.bodySmall)
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

    selectedProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(product.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("কোড: " + product.code)
                    Text("ক্রয়মূল্য: ৳ " + product.purchasePrice)
                    Text("বিক্রয়মূল্য: ৳ " + product.salePrice)
                    Text("স্টক: " + product.stockQuantity)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canWrite) {
                        Button(onClick = { selectedProduct = null; editingProduct = product }) { Text("Edit") }
                        Button(onClick = { selectedProduct = null; stockProduct = product }) { Text("Stock") }
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canWrite) {
                        Button(onClick = { selectedProduct = null; deleteProduct = product }) { Text("Delete") }
                    }
                    Button(onClick = { selectedProduct = null }) { Text("বন্ধ") }
                }
            }
        )
    }

    deleteProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteProduct = null },
            title = { Text("পণ্য মুছে ফেলবেন?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.name + " (" + product.code + ") স্থায়ীভাবে মুছে যাবে।")
                    OutlinedTextField(deletePin, { deletePin = it }, label = { Text("Admin PIN") }, singleLine = true)
                    if (deleteError.isNotBlank()) Text(deleteError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val admin = SecurityStorage.getUsers(context).firstOrNull { it.role == SecurityStorage.ROLE_ADMIN }
                    if (admin != null && admin.passwordHash == SecurityStorage.hashPassword(deletePin)) {
                        ProductStorage.deleteProduct(context, product.code)
                        ActivityLogStorage.add(context, "পণ্য মুছে ফেলা", product.name + " (" + product.code + ")")
                        deletePin = ""
                        deleteError = ""
                        deleteProduct = null
                        onProductsChanged()
                    } else {
                        deleteError = "ভুল Admin PIN।"
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { deleteProduct = null }) {
                    Text("Cancel")
                }
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
    val context = LocalContext.current
    var name by remember(product.code) { mutableStateOf(product.name) }
    var purchase by remember(product.code) { mutableStateOf(product.purchasePrice.toString()) }
    var sale by remember(product.code) { mutableStateOf(product.salePrice.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পণ্য Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("পণ্যের নাম") }
                )
                OutlinedTextField(
                    purchase,
                    { purchase = it },
                    label = { Text("ক্রয়মূল্য") }
                )
                OutlinedTextField(
                    sale,
                    { sale = it },
                    label = { Text("বিক্রয়মূল্য") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = purchase.toDoubleOrNull()
                val s = sale.toDoubleOrNull()

                if (name.isNotBlank() && p != null && p >= 0 && s != null && s >= 0) {
                    ProductStorage.updateProduct(
                        context = context,
                        product = product.copy(
                            name = name.trim(),
                            purchasePrice = p,
                            salePrice = s
                        )
                    )
                    onSaved()
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@androidx.compose.runtime.Composable
fun StockDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var quantity by remember(product.code) { mutableStateOf("") }
    var add by remember(product.code) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock In / Out") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("বর্তমান স্টক: " + product.stockQuantity)
                Button(onClick = { add = true }) {
                    Text("Stock In")
                }
                Button(onClick = { add = false }) {
                    Text("Stock Out")
                }
                OutlinedTextField(
                    quantity,
                    { quantity = it },
                    label = { Text("পরিমাণ") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull()

                if (q != null && q > 0) {
                    val newStock =
                        if (add) product.stockQuantity + q
                        else product.stockQuantity - q

                    if (newStock >= 0) {
                        ProductStorage.updateStock(
                            context,
                            product.code,
                            newStock
                        )
                        onSaved()
                    }
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
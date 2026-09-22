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
import com.tamanna.enterprise.sales.SalesStorage
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ProductSort(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    STOCK_OUT("Stock Out First"),
    MOST_SOLD("Most Sold First")
}

class ProductActivity : ComponentActivity() {
    private var products by mutableStateOf(emptyList<Product>())
    private var canWrite by mutableStateOf(false)
    private var sortMode by mutableStateOf(ProductSort.NEWEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortMode = loadSortMode()
        loadProducts()
        canWrite = SecurityStorage.canWrite(this)
        setContent {
            ProductScreen(
                products = products,
                canWrite = canWrite,
                sortMode = sortMode,
                onSortChanged = { sortMode = it; saveSortMode(it) },
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
        canWrite = SecurityStorage.canWrite(this)
    }

    private fun loadProducts() { products = ProductStorage.getProducts(this) }
    private fun loadSortMode(): ProductSort = runCatching {
        ProductSort.valueOf(getPreferences(MODE_PRIVATE).getString("product_sort_mode", ProductSort.NEWEST.name) ?: ProductSort.NEWEST.name)
    }.getOrDefault(ProductSort.NEWEST)
    private fun saveSortMode(mode: ProductSort) {
        getPreferences(MODE_PRIVATE).edit().putString("product_sort_mode", mode.name).apply()
    }
}

@androidx.compose.runtime.Composable
fun ProductScreen(
    products: List<Product>,
    canWrite: Boolean,
    sortMode: ProductSort,
    onSortChanged: (ProductSort) -> Unit,
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
    var showSortDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val salesByCode = remember(products) { SalesStorage.getSales(context).groupingBy { it.productCode.lowercase() }.fold(0) { total, sale -> total + sale.quantity } }
    val filteredProducts = remember(products, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) products else products.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    val sortedProducts = remember(filteredProducts, sortMode, salesByCode) {
        when (sortMode) {
            ProductSort.NEWEST -> filteredProducts.sortedByDescending { it.createdAt }
            ProductSort.OLDEST -> filteredProducts.sortedBy { it.createdAt }
            ProductSort.STOCK_OUT -> filteredProducts.sortedWith(compareBy<Product> { it.stockQuantity }.thenByDescending { it.createdAt })
            ProductSort.MOST_SOLD -> filteredProducts.sortedWith(compareByDescending<Product> { salesByCode[it.code.lowercase()] ?: 0 }.thenByDescending { it.createdAt })
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("পণ্য ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)

                Button(
                    onClick = onAddProductClick,
                    enabled = canWrite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (canWrite) "নতুন পণ্য যোগ করুন" else "নতুন পণ্য যোগ করুন (অ্যাডমিন অনুমতি প্রয়োজন)")
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("পণ্যের নাম বা কোড দিয়ে খুঁজুন") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { showSortDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("সাজানো: ${sortMode.label}") }
                Text("পণ্যের তালিকা", style = MaterialTheme.typography.titleLarge)

                if (products.isEmpty()) {
                    Text("এখনও কোনো পণ্য যোগ করা হয়নি।")
                } else if (sortedProducts.isEmpty()) {
                    Text("আপনার খোঁজের সাথে মিলে কোনো পণ্য পাওয়া যায়নি।")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedProducts, key = { it.code }) { item ->
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

    if (showSortDialog) {
        AlertDialog(onDismissRequest = { showSortDialog = false }, title = { Text("পণ্য কীভাবে সাজাবেন?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductSort.values().forEach { option ->
                    Button(onClick = { onSortChanged(option); showSortDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (option == sortMode) "✓ ${option.label}" else option.label)
                    }
                }
            }
        }, confirmButton = {})
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
                        Button(onClick = {
                            val latest = ProductStorage.getProducts(context).firstOrNull {
                                it.code.equals(product.code, ignoreCase = true)
                            }
                            if (latest != null) {
                                selectedProduct = null
                                editingProduct = latest
                            }
                        }) { Text("Edit") }
                        Button(onClick = {
                            val latest = ProductStorage.getProducts(context).firstOrNull {
                                it.code.equals(product.code, ignoreCase = true)
                            }
                            if (latest != null) {
                                selectedProduct = null
                                stockProduct = latest
                            }
                        }) { Text("Stock") }
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canWrite) {
                        Button(onClick = {
                            val latest = ProductStorage.getProducts(context).firstOrNull {
                                it.code.equals(product.code, ignoreCase = true)
                            }
                            if (latest != null) {
                                selectedProduct = null
                                deleteError = ""
                                deleteProduct = latest
                            }
                        }) { Text("Delete") }
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
                Button(
                enabled = product.stockQuantity == 0,
                onClick = {
                    val admin = SecurityStorage.getUsers(context).firstOrNull { it.role == SecurityStorage.ROLE_ADMIN }
                    val latest = ProductStorage.getProducts(context).firstOrNull {
                        it.code.equals(product.code, ignoreCase = true)
                    }
                    when {
                        latest == null -> deleteError = "পণ্যটি আর পাওয়া যাচ্ছে না।"
                        latest.stockQuantity != 0 -> deleteError = "পণ্যটির বর্তমান স্টক 0 নয়। আগে Stock Out করুন।"
                        admin == null || admin.passwordHash != SecurityStorage.hashPassword(deletePin) ->
                            deleteError = "ভুল Admin PIN।"
                        else -> {
                            ProductStorage.deleteProduct(context, latest.code)
                            ActivityLogStorage.add(context, "পণ্য মুছে ফেলা", latest.name + " (" + latest.code + ")")
                            deletePin = ""
                            deleteError = ""
                            deleteProduct = null
                            onProductsChanged()
                        }
                    }
                }
            ) {
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
    var error by remember(product.code) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock In / Out") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val latest = ProductStorage.getProducts(context).firstOrNull {
                    it.code.equals(product.code, ignoreCase = true)
                }
                Text("বর্তমান স্টক: " + (latest?.stockQuantity ?: product.stockQuantity))
                Button(onClick = { add = true; error = "" }) { Text("Stock In") }
                Button(onClick = { add = false; error = "" }) { Text("Stock Out") }
                OutlinedTextField(
                    quantity,
                    { quantity = it.filter(Char::isDigit); error = "" },
                    label = { Text("পরিমাণ") },
                    singleLine = true
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull()
                val latest = ProductStorage.getProducts(context).firstOrNull {
                    it.code.equals(product.code, ignoreCase = true)
                }
                when {
                    latest == null -> error = "পণ্যটি আর পাওয়া যাচ্ছে না।"
                    q == null || q <= 0 -> error = "সঠিক পরিমাণ দিন।"
                    add && latest.stockQuantity > Int.MAX_VALUE - q ->
                        error = "স্টক সীমা অতিক্রম করছে।"
                    !add && q > latest.stockQuantity ->
                        error = "Stock Out করা যাবে না। বর্তমান স্টক: " + latest.stockQuantity
                    else -> {
                        val newStock = if (add) latest.stockQuantity + q else latest.stockQuantity - q
                        ProductStorage.updateStock(context, latest.code, newStock)
                        ActivityLogStorage.add(
                            context,
                            if (add) "Manual Stock In" else "Manual Stock Out",
                            latest.name + " (" + latest.code + ") x" + q +
                                " • " + latest.stockQuantity + " → " + newStock
                        )
                        onSaved()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}
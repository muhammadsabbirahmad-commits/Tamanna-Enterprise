package com.tamanna.enterprise.stock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.settings.ThemeStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.*
 
class StockAlertActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        if (!SecurityStorage.canWrite(this)) { finish(); return }
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { StockAlertScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAlertScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Product?>(null) }
    var adjustment by remember { mutableStateOf<Product?>(null) }
    val products = remember(refresh) { ProductStorage.getProducts(context).sortedBy { it.name.lowercase() } }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val low = products.filter { it.stockQuantity <= InventoryMetaStorage.getMeta(context, it.code).lowStockLimit }
    val expired = products.filter {
        val d = InventoryMetaStorage.getMeta(context, it.code).expiryDate
        d.isNotBlank() && d <= today
    }
    val soon = products.filter {
        val d = InventoryMetaStorage.getMeta(context, it.code).expiryDate
        d.isNotBlank() && d > today && d <= addDays(today, 30)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("স্টক সতর্কতা ও মেয়াদ") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "কম স্টক: ${low.size}টি  •  মেয়াদ শেষ: ${expired.size}টি  •  ৩০ দিনের মধ্যে: ${soon.size}টি",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item { Text("পণ্যে চাপ দিয়ে Batch, Expiry, Low-stock limit ও নষ্ট Qty সেট করুন।", style = MaterialTheme.typography.bodySmall) }
            item { StockHistorySection() }
            items(products, key = { it.code }) { p ->
                val meta = InventoryMetaStorage.getMeta(context, p.code)
                val isLow = p.stockQuantity <= meta.lowStockLimit
                val isExpired = meta.expiryDate.isNotBlank() && meta.expiryDate <= today
                val isSoon = meta.expiryDate.isNotBlank() && meta.expiryDate > today && meta.expiryDate <= addDays(today, 30)
                Card(onClick = { selected = p }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("কোড: ${p.code}  •  স্টক: ${p.stockQuantity}")
                        Text("Batch: ${meta.batch.ifBlank { "নেই" }}  •  Expiry: ${meta.expiryDate.ifBlank { "নেই" }}")
                        if (isLow || isExpired || isSoon) {
                            Text(
                                buildString {
                                    if (isLow) append("⚠ কম স্টক")
                                    if (isExpired) append(if (isLow) "  •  " else "").append("❌ মেয়াদ শেষ")
                                    else if (isSoon) append(if (isLow) "  •  " else "").append("⏳ শিগগির মেয়াদ শেষ")
                                },
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = { adjustment = p }) { Text("স্টক সমন্বয়") }
                    }
                }
            }
        }
    }

    selected?.let { p ->
        InventoryMetaDialog(p, { selected = null }) { selected = null; refresh++ }
    }
    adjustment?.let { p ->
        StockAdjustmentDialog(p, { adjustment = null }) { adjustment = null; refresh++ }
    }
}

private fun addDays(date: String, days: Int): String {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val d = runCatching { f.parse(date) }.getOrNull() ?: Date()
    val cal = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }
    return f.format(cal.time)
}

@Composable
private fun InventoryMetaDialog(product: Product, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val old = remember(product.code) { InventoryMetaStorage.getMeta(context, product.code) }
    var batch by remember { mutableStateOf(old.batch) }
    var expiry by remember { mutableStateOf(old.expiryDate) }
    var limit by remember { mutableStateOf(old.lowStockLimit.toString()) }
    var damaged by remember { mutableStateOf(old.damagedQuantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(batch, { batch = it }, label = { Text("Batch নম্বর") }, singleLine = true)
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(limit, { limit = it }, label = { Text("Low-stock limit") }, singleLine = true)
                OutlinedTextField(damaged, { damaged = it }, label = { Text("নষ্ট/মেয়াদোত্তীর্ণ Qty") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val l = limit.toIntOrNull()
                val dm = damaged.toIntOrNull()
                val validDate = expiry.isBlank() || runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }.parse(expiry)
                }.isSuccess
                if (l != null && l >= 0 && dm != null && dm >= 0 && validDate) {
                    InventoryMetaStorage.saveMeta(context, InventoryMeta(product.code, batch.trim(), expiry.trim(), l, dm))
                    onSaved()
                }
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun StockAdjustmentDialog(product: Product, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var quantity by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("স্টক সমন্বয়: ${product.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("বর্তমান স্টক: ${product.stockQuantity}")
            OutlinedTextField(quantity, { quantity = it }, label = { Text("নতুন স্টক") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("কারণ/নোট") }, singleLine = true)
        }
    }, confirmButton = {
        Button(onClick = {
            val newStock = quantity.toIntOrNull()
            if (newStock != null && newStock >= 0) {
                ProductStorage.updateStock(context, product.code, newStock)
                InventoryMetaStorage.addHistory(context, product.code, product.name, "ADJUSTMENT", newStock - product.stockQuantity, product.stockQuantity, newStock, note.trim())
                onSaved()
            }
        }) { Text("সংরক্ষণ") }
    }, dismissButton = { Button(onClick = onDismiss) { Text("বাতিল") } })
}

@Composable
private fun StockHistorySection() {
    val context = LocalContext.current
    val history = InventoryMetaStorage.getHistory(context)
    Text("স্টক ইতিহাস", style = MaterialTheme.typography.titleLarge)
    if (history.isEmpty()) Text("এখনও কোনো স্টক ইতিহাস নেই।")
    history.take(100).forEach { h ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("${h.productName} • ${h.type}", style = MaterialTheme.typography.titleMedium)
                Text("${h.date} • ${h.quantity}টি • ${h.before} → ${h.after}")
                if (h.note.isNotBlank()) Text(h.note)
            }
        }
    }
}

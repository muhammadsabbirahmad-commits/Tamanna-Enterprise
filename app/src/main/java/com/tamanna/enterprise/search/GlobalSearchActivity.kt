package com.tamanna.enterprise.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.purchase.SupplierDueStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.ThemeStorage

data class SearchResult(val type: String, val title: String, val details: String)

@OptIn(ExperimentalMaterial3Api::class)
class GlobalSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { GlobalSearchScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GlobalSearchScreen() {
        val context = this@GlobalSearchActivity
        var query by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf("সব") }

        val products = ProductStorage.getProducts(context)
        val sales = SalesStorage.getSales(context)
        val purchases = PurchaseStorage.getPurchases(context)
        val customerDue = CustomerDueStorage.getEntries(context)
        val supplierDue = SupplierDueStorage.getEntries(context)
        val q = query.trim()
        fun match(vararg values: String) = q.isBlank() || values.any { it.contains(q, true) }

        val results = buildList {
            if (filter == "সব" || filter == "পণ্য") products.filter { match(it.code, it.name) }.take(50).forEach {
                add(SearchResult("পণ্য", it.name, "কোড: " + it.code + " • স্টক: " + it.stockQuantity + " • বিক্রয়: ৳" + "%.2f".format(it.salePrice)))
            }
            if (filter == "সব" || filter == "বিক্রয়") sales.filter { match(it.productCode, it.productName, it.customer, it.date) }.take(50).forEach {
                add(SearchResult("বিক্রয়", it.productName, it.date + " • " + it.quantity + " ইউনিট • ৳" + "%.2f".format(it.quantity * it.salePrice)))
            }
            if (filter == "সব" || filter == "ক্রয়") purchases.filter { match(it.productCode, it.productName, it.supplier, it.memoNumber, it.date) }.take(50).forEach {
                add(SearchResult("ক্রয়", it.productName, it.date + " • " + it.quantity + " ইউনিট • ৳" + "%.2f".format(it.quantity * it.purchasePrice)))
            }
            if (filter == "সব" || filter == "ক্রেতা") customerDue.filter { match(it.customer, it.mobile, it.note, it.date) }.take(50).forEach {
                add(SearchResult("ক্রেতার বাকি", it.customer, it.date + " • " + it.type + " • ৳" + "%.2f".format(it.amount)))
            }
            if (filter == "সব" || filter == "সরবরাহকারী") supplierDue.filter { match(it.supplier, it.note, it.date) }.take(50).forEach {
                add(SearchResult("সরবরাহকারী", it.supplier, it.date + " • " + it.type + " • ৳" + "%.2f".format(kotlin.math.abs(it.amount))))
            }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("🔎 গ্লোবাল সার্চ") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("পণ্য / কোড / ক্রেতা / সরবরাহকারী / মেমো / তারিখ") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("সব", "পণ্য", "বিক্রয়", "ক্রয়", "ক্রেতা", "সরবরাহকারী").forEach { label ->
                        FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) })
                    }
                }
                Text(results.size.toString() + "টি ফলাফল")
                if (results.isEmpty()) Text("কোনো মিল পাওয়া যায়নি।")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { result ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(result.type + " • " + result.title)
                                Text(result.details)
                            }
                        }
                    }
                }
            }
        }
    }
}

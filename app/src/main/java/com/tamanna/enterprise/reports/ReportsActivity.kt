package com.tamanna.enterprise.reports

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
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReportsScreen() }
    }
}

@Composable
private fun ReportsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sales = SalesStorage.getSales(context)
    val purchases = PurchaseStorage.getPurchases(context)
    val products = ProductStorage.getProducts(context)
    var dateQuery by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    val selectedSales = sales.filter { it.date.startsWith(dateQuery.trim()) }
    val selectedPurchases = purchases.filter { it.date.startsWith(dateQuery.trim()) }
    val salesAmount = selectedSales.sumOf { it.quantity * it.salePrice }
    val purchaseAmount = selectedPurchases.sumOf { it.quantity * it.purchasePrice }
    val profit = selectedSales.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }
    val soldUnits = selectedSales.sumOf { it.quantity }
    val purchasedUnits = selectedPurchases.sumOf { it.quantity }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("স্টক ও রিপোর্ট", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dateQuery,
                    onValueChange = { dateQuery = it },
                    label = { Text("তারিখ (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportCard("বিক্রয়", "৳ %.2f".format(salesAmount), Modifier.weight(1f))
                    ReportCard("ক্রয়", "৳ %.2f".format(purchaseAmount), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportCard("লাভ", "৳ %.2f".format(profit), Modifier.weight(1f))
                    ReportCard("বর্তমান স্টক", products.sumOf { it.stockQuantity }.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("বিক্রিত ইউনিট: " + soldUnits + " | ক্রয়কৃত ইউনিট: " + purchasedUnits)
                Spacer(Modifier.height(12.dp))
                Text("বিক্রয় রিপোর্ট", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                if (selectedSales.isEmpty()) {
                    Text("এই তারিখে কোনো বিক্রয় নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedSales, key = { it.id }) { sale ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(sale.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + sale.productCode + " | পরিমাণ: " + sale.quantity)
                                    Text("বিক্রয়: ৳ %.2f | লাভ: ৳ %.2f".format(
                                        sale.quantity * sale.salePrice,
                                        sale.quantity * (sale.salePrice - sale.purchasePrice)
                                    ))
                                    Text("সময়: " + sale.date)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

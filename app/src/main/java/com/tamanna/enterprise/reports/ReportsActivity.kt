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
    var fromDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var toDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    val sales = SalesStorage.getSales(context)
    val purchases = PurchaseStorage.getPurchases(context)
    val products = ProductStorage.getProducts(context)

    val validRange = fromDate.length == 10 &&
        toDate.length == 10 &&
        fromDate <= toDate

    val rangeSales = if (validRange) {
        sales.filter {
            val date = it.date.substringBefore(" ")
            date in fromDate..toDate
        }
    } else {
        emptyList()
    }

    val rangePurchases = if (validRange) {
        purchases.filter {
            val date = it.date.substringBefore(" ")
            date in fromDate..toDate
        }
    } else {
        emptyList()
    }

    val salesAmount = rangeSales.sumOf { it.quantity * it.salePrice }
    val costOfSales = rangeSales.sumOf { it.quantity * it.purchasePrice }
    val profit = salesAmount - costOfSales
    val purchaseAmount = rangePurchases.sumOf { it.quantity * it.purchasePrice }
    val soldUnits = rangeSales.sumOf { it.quantity }
    val purchasedUnits = rangePurchases.sumOf { it.quantity }
    val currentStockUnits = products.sumOf { it.stockQuantity }
    val stockValueAtPurchase = products.sumOf { it.stockQuantity * it.purchasePrice }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("বিস্তারিত রিপোর্ট", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = fromDate,
                    onValueChange = { fromDate = it },
                    label = { Text("শুরুর তারিখ (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = toDate,
                    onValueChange = { toDate = it },
                    label = { Text("শেষ তারিখ (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (!validRange) {
                    Spacer(Modifier.height(6.dp))
                    Text("তারিখের ফরম্যাট বা তারিখের পরিসর সঠিক নয়।")
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportCard("মোট বিক্রয়", "৳ %.2f".format(salesAmount), Modifier.weight(1f))
                    ReportCard("ক্রয় ব্যয়", "৳ %.2f".format(purchaseAmount), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportCard("লাভ", "৳ %.2f".format(profit), Modifier.weight(1f))
                    ReportCard("বর্তমান স্টক", "\${currentStockUnits ইউনিট", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("বিক্রিত: \${soldUnits ইউনিট | ক্রয়কৃত: \${purchasedUnits ইউনিট")
                Text("বিক্রয়ের পণ্যমূল্য: ৳ %.2f".format(costOfSales))
                Text("বর্তমান স্টকের ক্রয়মূল্য: ৳ %.2f".format(stockValueAtPurchase))

                Spacer(Modifier.height(14.dp))
                Text("বিক্রয় বিস্তারিত", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))

                if (rangeSales.isEmpty()) {
                    Text("নির্বাচিত সময়ে কোনো বিক্রয় নেই।")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rangeSales, key = { it.id }) { sale ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(sale.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: \${{sale.productCode}")
                                    Text("পরিমাণ: \${{sale.quantity} ইউনিট")
                                    Text("বিক্রয়: ৳ %.2f".format(sale.quantity * sale.salePrice))
                                    Text("লাভ: ৳ %.2f".format(
                                        sale.quantity * (sale.salePrice - sale.purchasePrice)
                                    ))
                                    Text("তারিখ: \${{sale.date}")
                                    if (sale.customer.isNotBlank()) Text("ক্রেতা: \${{sale.customer}")
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("ক্রয় বিস্তারিত", style = MaterialTheme.typography.titleLarge)
                        }

                        if (rangePurchases.isEmpty()) {
                            item { Text("নির্বাচিত সময়ে কোনো ক্রয় নেই।") }
                        } else {
                            items(rangePurchases, key = { it.id }) { purchase ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                                        Text("কোড: \${{purchase.productCode}")
                                        Text("পরিমাণ: \${{purchase.quantity} ইউনিট")
                                        Text("ক্রয়মূল্য: ৳ %.2f".format(purchase.quantity * purchase.purchasePrice))
                                        Text("তারিখ: \${{purchase.date}")
                                        if (purchase.supplier.isNotBlank()) Text("সরবরাহকারী: \${{purchase.supplier}")
                                        if (purchase.memoNumber.isNotBlank()) Text("মেমো: \${{purchase.memoNumber}")
                                    }
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

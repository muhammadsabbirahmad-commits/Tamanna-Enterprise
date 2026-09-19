package com.tamanna.enterprise.profit

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.product.ProductStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProfitScreen(this) }
    }
}

@Composable
private fun ProfitScreen(activity: ComponentActivity) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { fmt.format(Calendar.getInstance().time) }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today) }
    var refresh by remember { mutableIntStateOf(0) }

    val sales = remember(refresh) { SalesStorage.getSales(activity) }
    val purchases = remember(refresh) { PurchaseStorage.getPurchases(activity) }
    val products = remember(refresh) { ProductStorage.getProducts(activity) }

    val validRange = from.isNotBlank() && to.isNotBlank() && from <= to
    val rangeSales = if (validRange) sales.filter { it.date.substringBefore(" ") in from..to } else emptyList()
    val rangePurchases = if (validRange) purchases.filter { it.date.substringBefore(" ") in from..to } else emptyList()

    val salesAmount = rangeSales.sumOf { it.quantity * it.salePrice }
    val costAmount = rangeSales.sumOf { it.quantity * it.purchasePrice }
    val profit = salesAmount - costAmount
    val purchasedAmount = rangePurchases.sumOf { it.quantity * it.purchasePrice }
    val soldUnits = rangeSales.sumOf { it.quantity }
    val purchasedUnits = rangePurchases.sumOf { it.quantity }
    val stockUnits = products.sumOf { it.stockQuantity }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("লাভের হিসাব", style = MaterialTheme.typography.headlineMedium)
                Text("তারিখের পরিসর অনুযায়ী বিক্রয় ও লাভ", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text("শুরু") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        label = { Text("শেষ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { refresh++ },
                    enabled = validRange,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("হিসাব দেখুন") }

                Spacer(Modifier.height(16.dp))

                if (!validRange) {
                    Text("সঠিক তারিখ দিন: YYYY-MM-DD এবং শুরু তারিখ শেষ তারিখের আগে/সমান হতে হবে।")
                } else {
                    SummaryCard("মোট বিক্রয়", "৳ %.2f".format(salesAmount))
                    SummaryCard("বিক্রয়ের ক্রয়মূল্য", "৳ %.2f".format(costAmount))
                    SummaryCard("মোট লাভ", "৳ %.2f".format(profit))
                    SummaryCard("মোট ক্রয়", "৳ %.2f".format(purchasedAmount))
                    SummaryCard("বিক্রি হয়েছে", "$soldUnits ইউনিট".replace("$", ""))
                    SummaryCard("ক্রয় হয়েছে", "$purchasedUnits ইউনিট".replace("$", ""))
                    SummaryCard("বর্তমান স্টক", "$stockUnits ইউনিট".replace("$", ""))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

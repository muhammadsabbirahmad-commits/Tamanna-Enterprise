package com.tamanna.enterprise.profit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.profit.BusinessProfitCalculator
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.finance.ExpenseStorage
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

    val products = remember(refresh) { ProductStorage.getProducts(activity) }
    val validRange = from.isNotBlank() && to.isNotBlank() && from <= to
    val report = remember(refresh, from, to) {
        if (validRange) BusinessProfitCalculator.calculate(activity, from, to) else null
    }
    val grossSales = report?.grossSales ?: 0.0
    val salesDiscount = report?.salesDiscount ?: 0.0
    val costAmount = report?.costOfGoodsSold ?: 0.0
    val returnRevenue = report?.returnRevenue ?: 0.0
    val returnCost = report?.returnCost ?: 0.0
    val institutionExpenses = report?.expenses ?: 0.0
    val damageLoss = report?.damageLoss ?: 0.0
    val netProfit = report?.netProfit ?: 0.0
    val purchasedAmount = remember(refresh, from, to) {
        if (validRange) PurchaseStorage.getPurchases(activity)
            .filter { it.date.substringBefore(" ") in from..to }
            .sumOf { it.quantity.coerceAtLeast(0) * it.purchasePrice.coerceAtLeast(0.0) }
        else 0.0
    }
    val soldUnits = remember(refresh, from, to) {
        if (validRange) SalesStorage.getSales(activity)
            .filter { it.date.substringBefore(" ") in from..to }
            .sumOf { it.quantity.coerceAtLeast(0) }
        else 0
    }
    val purchasedUnits = remember(refresh, from, to) {
        if (validRange) PurchaseStorage.getPurchases(activity)
            .filter { it.date.substringBefore(" ") in from..to }
            .sumOf { it.quantity.coerceAtLeast(0) }
        else 0
    }
    val stockUnits = products.sumOf { it.stockQuantity.coerceAtLeast(0) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("লাভের হিসাব", style = MaterialTheme.typography.headlineMedium)
                Text("তারিখের পরিসর অনুযায়ী বিক্রয় ও লাভ")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text("শুরু") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it }, label = { Text("শেষ") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { refresh++ }, enabled = validRange, modifier = Modifier.fillMaxWidth()) { Text("হিসাব দেখুন") }
                Spacer(Modifier.height(16.dp))
                if (!validRange) {
                    Text("সঠিক তারিখ দিন: YYYY-MM-DD এবং শুরু তারিখ শেষ তারিখের আগে/সমান হতে হবে।")
                } else {
                    SummaryCard("মোট বিক্রয় (ডিসকাউন্টের আগে)", "৳ %.2f".format(grossSales))
                    SummaryCard("বিক্রিত পণ্যের ক্রয়মূল্য", "৳ %.2f".format(costAmount))
                    SummaryCard("বিক্রয় ডিসকাউন্ট", "৳ %.2f".format(salesDiscount))
                    SummaryCard("প্রতিষ্ঠানের খরচ", "৳ %.2f".format(institutionExpenses))
                    SummaryCard("ড্যামেজ ক্ষতি", "৳ %.2f".format(damageLoss))
                    SummaryCard("বিক্রয় ফেরতের নিট মূল্য", "৳ %.2f".format(returnRevenue))
                    SummaryCard("ফেরত পণ্যের পুনরুদ্ধারকৃত ক্রয়মূল্য", "৳ %.2f".format(returnCost))
                    SummaryCard("নিট লাভ (ডিসকাউন্ট, রিটার্ন, খরচ ও ড্যামেজ বাদে)", "৳ %.2f".format(netProfit))
                    report?.warnings?.take(8)?.forEach { warning ->
                        Text("• $warning", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
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
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

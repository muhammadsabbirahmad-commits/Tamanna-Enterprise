package com.tamanna.enterprise.profit

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
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.partner.PartnerStorage
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

    val sales = remember(refresh) { SalesStorage.getSales(activity) }
    val purchases = remember(refresh) { PurchaseStorage.getPurchases(activity) }
    val products = remember(refresh) { ProductStorage.getProducts(activity) }
    val partners = remember(refresh) { PartnerStorage.getPartners(activity) }

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
    val partnerPercentage = partners.sumOf { it.percentage }
    val expenses = if (validRange) ExpenseStorage.getExpenses(activity).filter { it.date in from..to } else emptyList()
    val damages = if (validRange) ExpenseStorage.getDamages(activity).filter { it.date in from..to } else emptyList()
    val withdrawals = if (validRange) ExpenseStorage.getWithdrawals(activity).filter { it.date in from..to } else emptyList()
    val institutionExpenses = expenses.sumOf { it.amount }
    val damageLoss = damages.sumOf { it.totalLoss }
    val netProfit = profit - institutionExpenses - damageLoss

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
                    SummaryCard("মোট বিক্রয়", "৳ %.2f".format(salesAmount))
                    SummaryCard("বিক্রয়ের ক্রয়মূল্য", "৳ %.2f".format(costAmount))
                    SummaryCard("বিক্রয়ভিত্তিক মোট লাভ", "৳ %.2f".format(profit))
                    SummaryCard("প্রতিষ্ঠানের খরচ", "৳ %.2f".format(institutionExpenses))
                    SummaryCard("ড্যামেজ ক্ষতি", "৳ %.2f".format(damageLoss))
                    SummaryCard("নিট লাভ", "৳ %.2f".format(netProfit))
                    SummaryCard("পার্টনারদের উত্তোলন", "৳ %.2f".format(withdrawals.sumOf { it.amount }))
                    SummaryCard("মোট ক্রয়", "৳ %.2f".format(purchasedAmount))
                    SummaryCard("বিক্রি হয়েছে", "$soldUnits ইউনিট".replace("$", ""))
                    SummaryCard("ক্রয় হয়েছে", "$purchasedUnits ইউনিট".replace("$", ""))
                    SummaryCard("বর্তমান স্টক", "$stockUnits ইউনিট".replace("$", ""))

                    if (partners.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("পার্টনারদের লাভ বণ্টন", style = MaterialTheme.typography.titleLarge)
                        Text("মোট নির্ধারিত অংশ: %.2f%%".format(Locale.US, partnerPercentage))
                        if (partnerPercentage > 100.0001) {
                            Text("সতর্কতা: পার্টনারদের শতাংশ ১০০%-এর বেশি।", color = MaterialTheme.colorScheme.error)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                            items(partners, key = { it.id }) { partner ->
                                val share = PartnerStorage.profitShare(netProfit, partner)
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(partner.name, style = MaterialTheme.typography.titleMedium)
                                        Text("বিনিয়োগ: ৳ %.2f".format(Locale.US, partner.investment))
                                        Text("লাভের অংশ: %.2f%%".format(Locale.US, partner.percentage))
                                        Text("এই সময়ের লাভ: ৳ %.2f".format(Locale.US, share))
                                    }
                                }
                            }
                        }
                        val unallocated = netProfit * (100.0 - partnerPercentage) / 100.0
                        Text("অবণ্টিত লাভ: ৳ %.2f".format(Locale.US, unallocated))
                    }
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

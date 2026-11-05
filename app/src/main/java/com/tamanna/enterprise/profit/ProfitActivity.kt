package com.tamanna.enterprise.profit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.sales.SaleReturnStorage
import com.tamanna.enterprise.sales.SalesTransactionStorage
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

    val sales = remember(refresh) { SalesStorage.getSales(activity) }
    val purchases = remember(refresh) { PurchaseStorage.getPurchases(activity) }
    val products = remember(refresh) { ProductStorage.getProducts(activity) }
    val returns = remember(refresh) { SaleReturnStorage.getReturns(activity) }
    val returnLines = remember(refresh) { SaleReturnStorage.getLines(activity) }
    val transactions = remember(refresh) { SalesTransactionStorage.getTransactions(activity) }

    val validRange = from.isNotBlank() && to.isNotBlank() && from <= to
    val rangeSales = if (validRange) sales.filter { it.date.substringBefore(" ") in from..to } else emptyList()
    val rangePurchases = if (validRange) purchases.filter { it.date.substringBefore(" ") in from..to } else emptyList()

    // রিটার্নের তারিখ অনুযায়ী রিটার্ন গণনা হবে; বিক্রির তারিখ আলাদা হলেও বাদ পড়বে না।
    val rangeReturns = if (validRange) returns.filter { it.date.substringBefore(" ") in from..to } else emptyList()
    val rangeReturnIds = rangeReturns.map { it.returnId }.toSet()
    val rangeReturnLines = returnLines.filter { it.returnId in rangeReturnIds }

    val salesAmount = rangeSales.sumOf { it.quantity.coerceAtLeast(0) * it.salePrice }
    val costAmount = rangeSales.sumOf { it.quantity.coerceAtLeast(0) * it.purchasePrice }

    // মূল বিক্রয় রেকর্ড থেকেই ক্রয়মূল্য নেওয়া হচ্ছে; বর্তমান পণ্যের পরিবর্তিত ক্রয়মূল্য নয়।
    val salesByTransactionAndCode = sales.groupBy {
        it.transactionId to it.productCode.trim().lowercase(Locale.ROOT)
    }
    val transactionsById = transactions.associateBy { it.transactionId }

    val returnSalesAmount = rangeReturnLines.sumOf { line ->
        val tx = transactionsById[line.transactionId]
        val discountRate = if (tx != null && tx.subtotal > 0.0) {
            (tx.discount / tx.subtotal).coerceIn(0.0, 1.0)
        } else 0.0
        line.quantity.coerceAtLeast(0) * line.unitPrice.coerceAtLeast(0.0) * (1.0 - discountRate)
    }

    val returnCostAmount = rangeReturnLines.sumOf { line ->
        val originalLines = salesByTransactionAndCode[
            line.transactionId to line.productCode.trim().lowercase(Locale.ROOT)
        ].orEmpty()
        val originalQty = originalLines.sumOf { it.quantity.coerceAtLeast(0) }
        val originalPurchasePrice = if (originalQty > 0) {
            originalLines.sumOf { it.quantity.coerceAtLeast(0) * it.purchasePrice } / originalQty
        } else 0.0
        line.quantity.coerceAtLeast(0) * originalPurchasePrice
    }

    val netSalesAmount = salesAmount - returnSalesAmount
    val netCostAmount = costAmount - returnCostAmount
    val profit = netSalesAmount - netCostAmount

    val purchasedAmount = rangePurchases.sumOf { it.quantity * it.purchasePrice }
    val soldUnits = rangeSales.sumOf { it.quantity.coerceAtLeast(0) } - rangeReturnLines.sumOf { it.quantity.coerceAtLeast(0) }
    val purchasedUnits = rangePurchases.sumOf { it.quantity }
    val stockUnits = products.sumOf { it.stockQuantity }
    val expenses = if (validRange) ExpenseStorage.getExpenses(activity).filter { it.date in from..to } else emptyList()
    val damages = if (validRange) ExpenseStorage.getDamages(activity).filter { it.date in from..to } else emptyList()
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
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item { SummaryCard("মোট বিক্রয় (রিটার্ন বাদে)", "৳ %.2f".format(netSalesAmount)) }
                        item { SummaryCard("বিক্রয়ের ক্রয়মূল্য (রিটার্ন বাদে)", "৳ %.2f".format(netCostAmount)) }
                        item { SummaryCard("বিক্রয়ভিত্তিক নিট লাভ", "৳ %.2f".format(profit)) }
                        item { SummaryCard("প্রতিষ্ঠানের খরচ", "৳ %.2f".format(institutionExpenses)) }
                        item { SummaryCard("ড্যামেজ ক্ষতি", "৳ %.2f".format(damageLoss)) }
                        item { SummaryCard("সর্বমোট নিট লাভ", "৳ %.2f".format(netProfit)) }
                        item { SummaryCard("মোট ক্রয়", "৳ %.2f".format(purchasedAmount)) }
                        item { SummaryCard("বিক্রি হয়েছে", "$soldUnits ইউনিট") }
                        item { SummaryCard("ক্রয় হয়েছে", "$purchasedUnits ইউনিট") }
                        item { SummaryCard("বর্তমান স্টক", "$stockUnits ইউনিট") }
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

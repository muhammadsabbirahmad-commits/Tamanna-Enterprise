package com.tamanna.enterprise.purchase

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PurchaseActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent {
            PurchaseScreen(
                onAddPurchase = { startActivity(Intent(this, AddPurchaseActivity::class.java)) },
                onMemoScan = { startActivityForResult(Intent(this, MemoScannerActivity::class.java), REQUEST_MEMO_SCAN) }
            )
        }
    }

    @Deprecated("Use Activity Result API in a future cleanup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEMO_SCAN && resultCode == RESULT_OK) {
            val code = data?.getStringExtra(MemoScannerActivity.EXTRA_PRODUCT_CODE).orEmpty()
            if (code.isNotBlank()) {
                val quantity = data?.getIntExtra(MemoScannerActivity.EXTRA_QUANTITY, 0) ?: 0
                val purchasePrice = data?.getDoubleExtra(MemoScannerActivity.EXTRA_PURCHASE_PRICE, 0.0) ?: 0.0
                val memoVerified = data?.getBooleanExtra(MemoScannerActivity.EXTRA_MEMO_VERIFIED_DATA, false) ?: false

                startActivity(
                    Intent(this, AddPurchaseActivity::class.java)
                        .putExtra(AddPurchaseActivity.EXTRA_PRODUCT_CODE, code)
                        .putExtra(AddPurchaseActivity.EXTRA_QUANTITY, quantity)
                        .putExtra(AddPurchaseActivity.EXTRA_PURCHASE_PRICE, purchasePrice)
                        .putExtra(AddPurchaseActivity.EXTRA_MEMO_VERIFIED_DATA, memoVerified)
                )
            }
        }
    }

    companion object { private const val REQUEST_MEMO_SCAN = 7101 }
}

@Composable
private fun PurchaseScreen(onAddPurchase: () -> Unit, onMemoScan: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val canWrite = com.tamanna.enterprise.security.SecurityStorage.canWrite(context)
    var purchases by remember { mutableStateOf(PurchaseStorage.getPurchases(context)) }
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var appliedStart by remember { mutableStateOf("") }
    var appliedEnd by remember { mutableStateOf("") }
    var dateMessage by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        purchases = PurchaseStorage.getPurchases(context)
    }

    val filteredPurchases = purchases.filter { purchase ->
        val day = purchase.date.take(10)
        (appliedStart.isBlank() || day >= appliedStart) &&
            (appliedEnd.isBlank() || day <= appliedEnd)
    }
    val totalAmount = filteredPurchases.sumOf { it.quantity * it.purchasePrice }
    val totalQuantity = filteredPurchases.sumOf { it.quantity }

    fun applyRange(from: String, to: String) {
        if ((from.isNotBlank() && !isValidDate(from)) ||
            (to.isNotBlank() && !isValidDate(to)) ||
            (from.isNotBlank() && to.isNotBlank() && from > to)
        ) {
            dateMessage = "সঠিক তারিখ দিন (YYYY-MM-DD) এবং শুরুর তারিখ শেষের তারিখের আগে রাখুন।"
            return
        }
        appliedStart = from
        appliedEnd = to
        startDate = from
        endDate = to
        dateMessage = "তারিখ অনুযায়ী ক্রয়ের হিসাব আপডেট হয়েছে।"
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (canWrite) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAddPurchase, modifier = Modifier.weight(1f)) { Text("নতুন ক্রয়") }
                        Button(onClick = onMemoScan, modifier = Modifier.weight(1f)) { Text("মেমো স্ক্যান") }
                    }
                } else {
                    Text("👁 VIEW ONLY — ক্রয়ের তথ্য দেখা যাবে, নতুন ক্রয়/মেমো স্ক্যান করা যাবে না।", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("ক্রয় হিসাবের তারিখ নির্বাচন", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { val d = today(); applyRange(d, d) }) { Text("আজ") }
                    TextButton(onClick = { val d = daysAgo(1); applyRange(d, d) }) { Text("গতকাল") }
                    TextButton(onClick = { applyRange(daysAgo(6), today()) }) { Text("৭ দিন") }
                    TextButton(onClick = { applyRange(daysAgo(29), today()) }) { Text("৩০ দিন") }
                    TextButton(onClick = { applyRange(monthStart(), today()) }) { Text("এই মাস") }
                    TextButton(onClick = { applyRange("", "") }) { Text("সব") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate, onValueChange = { startDate = it },
                        label = { Text("শুরুর তারিখ") }, placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate, onValueChange = { endDate = it },
                        label = { Text("শেষের তারিখ") }, placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Button(onClick = { applyRange(startDate.trim(), endDate.trim()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("তারিখ অনুযায়ী হিসাব রিফ্রেশ করুন")
                }
                if (dateMessage.isNotBlank()) Text(dateMessage, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ক্রয়ের সারসংক্ষেপ", style = MaterialTheme.typography.titleMedium)
                        Text("সময়: " + if (appliedStart.isBlank() && appliedEnd.isBlank()) "সকল তারিখ" else (appliedStart.ifBlank { "শুরু থেকে" } + " — " + appliedEnd.ifBlank { "আজ পর্যন্ত" }))
                        Text("মোট ক্রয়: ৳ " + purchaseMoney(totalAmount))
                        Text("মোট পণ্য/পরিমাণ: " + totalQuantity)
                        Text("ক্রয় রেকর্ড: " + filteredPurchases.size + "টি")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (filteredPurchases.isEmpty()) {
                    Text("নির্বাচিত সময়ে কোনো ক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredPurchases, key = { it.id }) { purchase ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { selectedPurchase = purchase }) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + purchase.productCode + "  •  " + purchase.date, style = MaterialTheme.typography.bodySmall)
                                    Text("পরিমাণ: " + purchase.quantity + "  •  মোট: ৳ " + purchaseMoney(purchase.quantity * purchase.purchasePrice))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPurchase?.let { purchase ->
        AlertDialog(
            onDismissRequest = { selectedPurchase = null },
            title = { Text("ক্রয়ের বিস্তারিত") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("পণ্য: " + purchase.productName)
                    Text("পণ্য কোড: " + purchase.productCode)
                    Text("তারিখ: " + purchase.date)
                    Text("পরিমাণ: " + purchase.quantity)
                    Text("প্রতি ইউনিট ক্রয়মূল্য: ৳ " + purchaseMoney(purchase.purchasePrice))
                    Text("মোট ক্রয়মূল্য: ৳ " + purchaseMoney(purchase.quantity * purchase.purchasePrice))
                    Text("সরবরাহকারী: " + purchase.supplier.ifBlank { "উল্লেখ নেই" })
                    Text("মেমো নম্বর: " + purchase.memoNumber.ifBlank { "উল্লেখ নেই" })
                }
            },
            confirmButton = { TextButton(onClick = { selectedPurchase = null }) { Text("বন্ধ") } }
        )
    }
}

private fun isValidDate(value: String): Boolean = try {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    format.parse(value)?.let { format.format(it) == value } == true
} catch (_: Exception) { false }

private fun today(): String = dateOffset(0)
private fun daysAgo(days: Int): String = dateOffset(-days)
private fun monthStart(): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
private fun dateOffset(days: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, days)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
private fun purchaseMoney(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

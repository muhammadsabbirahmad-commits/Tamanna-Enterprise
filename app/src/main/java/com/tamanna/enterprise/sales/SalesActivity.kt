package com.tamanna.enterprise.sales

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SalesActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent {
            SalesScreen {
                startActivity(Intent(this, NewSaleActivity::class.java))
            }
        }
    }
}

private data class SaleGroup(
    val transactionId: String,
    val lines: List<Sale>,
    val transaction: SaleTransaction?
)

@androidx.compose.runtime.Composable
private fun SalesScreen(onNewSale: () -> Unit) {
    val context = LocalContext.current
    val canWrite = SecurityStorage.canWrite(context)
    var selectedGroup by remember { mutableStateOf<SaleGroup?>(null) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var appliedStart by remember { mutableStateOf("") }
    var appliedEnd by remember { mutableStateOf("") }
    var dateMessage by remember { mutableStateOf("") }

    val sales = remember { SalesStorage.getSales(context) }
    val transactions = remember { SalesTransactionStorage.getTransactions(context) }

    val allGroups = sales.groupBy { it.transactionId }.values
        .map { lines ->
            val id = lines.first().transactionId
            SaleGroup(id, lines.sortedBy { it.id }, transactions.firstOrNull { it.transactionId == id })
        }
        .sortedByDescending { it.lines.maxOfOrNull { line -> line.id } ?: 0L }

    val groups = allGroups.filter { group ->
        val day = (group.transaction?.date ?: group.lines.first().date).take(10)
        (appliedStart.isBlank() || day >= appliedStart) &&
            (appliedEnd.isBlank() || day <= appliedEnd)
    }
    val totalSales = groups.sumOf { group ->
        group.transaction?.total ?: group.lines.sumOf { it.quantity * it.salePrice }
    }
    val totalPaid = groups.sumOf { group -> group.transaction?.paid ?: group.lines.sumOf { it.quantity * it.salePrice } }
    val totalDue = groups.sumOf { group -> group.transaction?.due ?: 0.0 }
    val totalReturned = groups.sumOf { group -> SaleReturnStorage.getReturnedAmount(context, group.transactionId) }
    val totalQuantity = groups.sumOf { group -> group.lines.sumOf { it.quantity } }

    fun applyRange(from: String, to: String) {
        if ((from.isNotBlank() && !isValidSaleDate(from)) ||
            (to.isNotBlank() && !isValidSaleDate(to)) ||
            (from.isNotBlank() && to.isNotBlank() && from > to)
        ) {
            dateMessage = "সঠিক তারিখ দিন (YYYY-MM-DD) এবং শুরুর তারিখ শেষের তারিখের আগে রাখুন।"
            return
        }
        appliedStart = from
        appliedEnd = to
        startDate = from
        endDate = to
        dateMessage = "তারিখ অনুযায়ী বিক্রয়ের হিসাব আপডেট হয়েছে।"
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("বিক্রয় ইতিহাস", style = MaterialTheme.typography.headlineSmall)
                    if (canWrite) Button(onClick = onNewSale) { Text("নতুন বিক্রয়") }
                    else Text("শুধু দেখার অনুমতি", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text("বিক্রয় হিসাবের তারিখ নির্বাচন", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { val d = todaySale(); applyRange(d, d) }) { Text("আজ") }
                    TextButton(onClick = { val d = daysAgoSale(1); applyRange(d, d) }) { Text("গতকাল") }
                    TextButton(onClick = { applyRange(daysAgoSale(6), todaySale()) }) { Text("৭ দিন") }
                    TextButton(onClick = { applyRange(daysAgoSale(29), todaySale()) }) { Text("৩০ দিন") }
                    TextButton(onClick = { applyRange(monthStartSale(), todaySale()) }) { Text("এই মাস") }
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

                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("বিক্রয়ের সারসংক্ষেপ", style = MaterialTheme.typography.titleMedium)
                        Text("সময়: " + if (appliedStart.isBlank() && appliedEnd.isBlank()) "সকল তারিখ" else (appliedStart.ifBlank { "শুরু থেকে" } + " — " + appliedEnd.ifBlank { "আজ পর্যন্ত" }))
                        Text("মোট বিক্রয়: ৳ " + saleMoney(totalSales))
                        Text("মোট জমা: ৳ " + saleMoney(totalPaid) + "  •  মোট বাকি: ৳ " + saleMoney(totalDue))
                        Text("মোট রিটার্ন: ৳ " + saleMoney(totalReturned))
                        Text("বিক্রয়কৃত পণ্যের পরিমাণ: " + totalQuantity)
                        Text("বিক্রয় ইনভয়েস: " + groups.size + "টি")
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (groups.isEmpty()) {
                    Text("নির্বাচিত সময়ে কোনো বিক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(groups, key = { it.transactionId }) { group ->
                            val tx = group.transaction
                            val returnedAmount = SaleReturnStorage.getReturnedAmount(context, group.transactionId)
                            Card(Modifier.fillMaxWidth().clickable { selectedGroup = group }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("ইনভয়েস: " + group.transactionId, style = MaterialTheme.typography.titleMedium)
                                    Text((tx?.date ?: group.lines.first().date) + "  •  " + group.lines.size + "টি পণ্য")
                                    if (!tx?.customer.isNullOrBlank()) Text("ক্রেতা: " + tx!!.customer)
                                    if (tx != null) {
                                        Text("মোট: ৳ " + saleMoney(tx.total) + "  •  জমা: ৳ " + saleMoney(tx.paid) + "  •  বাকি: ৳ " + saleMoney(tx.due))
                                    } else {
                                        Text("মোট: ৳ " + saleMoney(group.lines.sumOf { it.quantity * it.salePrice }))
                                    }
                                    if (returnedAmount > 0.0) Text("রিটার্ন: ৳ " + saleMoney(returnedAmount))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedGroup?.let { group ->
        val tx = group.transaction
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text("বিক্রয় বিস্তারিত") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Transaction ID: " + group.transactionId)
                    Text("তারিখ: " + (tx?.date ?: group.lines.first().date))
                    if (!tx?.customer.isNullOrBlank()) Text("ক্রেতা: " + tx!!.customer)
                    if (!tx?.mobile.isNullOrBlank()) Text("মোবাইল: " + tx!!.mobile)
                    group.lines.forEachIndexed { index, line ->
                        Text((index + 1).toString() + ". " + line.productName + " (" + line.productCode + ") × " + line.quantity + " = ৳ " + saleMoney(line.quantity * line.salePrice))
                    }
                    if (tx != null) {
                        Text("সাবটোটাল: ৳ " + saleMoney(tx.subtotal))
                        Text("ছাড়: ৳ " + saleMoney(tx.discount))
                        Text("মোট: ৳ " + saleMoney(tx.total))
                        Text("জমা: ৳ " + saleMoney(tx.paid))
                        Text("বাকি: ৳ " + saleMoney(tx.due))
                        Text("পেমেন্ট: " + tx.paymentMethod)
                    }
                    val returned = SaleReturnStorage.getReturnedAmount(context, group.transactionId)
                    if (returned > 0.0) Text("এ পর্যন্ত রিটার্ন: ৳ " + saleMoney(returned))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tx != null && SecurityStorage.canWrite(context)) {
                        TextButton(onClick = {
                            selectedGroup = null
                            context.startActivity(
                                Intent(context, SaleReturnActivity::class.java)
                                    .putExtra(SaleReturnActivity.EXTRA_TRANSACTION_ID, group.transactionId)
                            )
                        }) { Text("রিটার্ন") }
                    }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.shareInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("শেয়ার") }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.viewInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("দেখুন") }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.printInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("প্রিন্ট") }
                    TextButton(onClick = { selectedGroup = null }) { Text("বন্ধ") }
                }
            }
        )
    }
}

private fun isValidSaleDate(value: String): Boolean = try {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    format.parse(value)?.let { format.format(it) == value } == true
} catch (_: Exception) { false }

private fun todaySale(): String = saleDateOffset(0)
private fun daysAgoSale(days: Int): String = saleDateOffset(-days)
private fun monthStartSale(): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
private fun saleDateOffset(days: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, days)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
private fun saleMoney(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

package com.tamanna.enterprise.sales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ReturnRow(val sale: Sale, val returned: Int, val remaining: Int)

class SaleReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID).orEmpty()
        setContent { SaleReturnScreen(transactionId, { finish() }) }
    }
    companion object { const val EXTRA_TRANSACTION_ID = "transaction_id" }
}

@androidx.compose.runtime.Composable
private fun SaleReturnScreen(transactionId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val tx = remember(transactionId) { SalesTransactionStorage.getTransaction(context, transactionId) }
    val originalLines = remember(transactionId) { SalesStorage.getSales(context).filter { it.transactionId == transactionId } }
    val returned = remember(transactionId) { SaleReturnStorage.getReturnedQuantities(context, transactionId) }
    val rows = remember(originalLines, returned) {
        originalLines.map { ReturnRow(it, returned[it.productCode] ?: 0, (it.quantity - (returned[it.productCode] ?: 0)).coerceAtLeast(0)) }
    }
    val quantities = remember(rows) { mutableStateListOf<Int>().also { list -> rows.forEach { list.add(0) } } }
    var refundMethod by remember { mutableStateOf("Cash") }
    var message by remember { mutableStateOf("") }

    val grossReturn = rows.indices.sumOf { quantities[it] * rows[it].sale.salePrice }
    val discountRate = if ((tx?.subtotal ?: 0.0) > 0.0) (tx!!.discount / tx.subtotal).coerceIn(0.0, 1.0) else 0.0
    val returnAmount = grossReturn * (1.0 - discountRate)
    val previousDueReduction = SaleReturnStorage.getDueReduction(context, transactionId)
    val remainingOriginalDue = ((tx?.due ?: 0.0) - previousDueReduction).coerceAtLeast(0.0)
    val dueReduction = returnAmount.coerceAtMost(remainingOriginalDue)
    val refundAmount = (returnAmount - dueReduction).coerceAtLeast(0.0)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("বিক্রয় রিটার্ন", style = MaterialTheme.typography.headlineSmall)
            if (tx == null || rows.isEmpty()) {
                Text("এই ইনভয়েসের বিক্রয় রেকর্ড পাওয়া যায়নি।")
                Button(onClick = onDone) { Text("বন্ধ") }
            } else {
                Text("ইনভয়েস: $transactionId")
                Text("ক্রেতা: " + tx.customer)
                Text("মূল বাকি: ৳ " + money(tx.due) + " • রিটার্নের পর সমন্বয়যোগ্য বাকি: ৳ " + money(remainingOriginalDue))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.sale.productCode }) { row ->
                        val index = rows.indexOf(row)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(row.sale.productName, style = MaterialTheme.typography.titleMedium)
                                Text("বিক্রি: ${row.sale.quantity} • আগে রিটার্ন: ${row.returned} • ফেরতযোগ্য: ${row.remaining}")
                                OutlinedTextField(
                                    value = quantities[index].toString(),
                                    onValueChange = { value -> quantities[index] = (value.toIntOrNull() ?: 0).coerceIn(0, row.remaining) },
                                    label = { Text("ফেরত পরিমাণ") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Text("ইউনিট মূল্য: ৳ " + money(row.sale.salePrice))
                            }
                        }
                    }
                }

                Text("রিটার্নের সমন্বয়যোগ্য মোট: ৳ " + money(returnAmount))
                Text("বাকি থেকে কমবে: ৳ " + money(dueReduction))
                Text("ফেরত দেওয়ার টাকা: ৳ " + money(refundAmount))
                Text("ফেরত দেওয়ার মাধ্যম", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "bKash", "Bank").forEach { method ->
                        FilterChip(selected = refundMethod == method, onClick = { refundMethod = method }, label = { Text(method) })
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDone, Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            if (!SecurityStorage.canWrite(context)) {
                                message = "রিটার্ন সংরক্ষণের অনুমতি নেই."
                                return@Button
                            }
                            if (quantities.sum() <= 0) {
                                message = "কমপক্ষে একটি পণ্যের ফেরত পরিমাণ দিন."
                                return@Button
                            }
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            val returnId = "RT-" + SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(Date())
                            val lines = rows.indices.mapNotNull { i ->
                                val q = quantities[i]
                                if (q <= 0) null else SaleReturnLine(returnId, transactionId, rows[i].sale.productCode, rows[i].sale.productName, q, rows[i].sale.salePrice)
                            }
                            // Re-read every affected product immediately before changing stock so the return
                            // cannot use a stale quantity from the screen.
                            val latestProducts = ProductStorage.getProducts(context)
                            val currentProducts = lines.associate { line ->
                                line.productCode.lowercase(Locale.getDefault()) to
                                    (latestProducts.firstOrNull { it.code.equals(line.productCode, true) }
                                        ?: error("পণ্য পাওয়া যায়নি: " + line.productCode))
                            }
                            currentProducts.values.forEach { product ->
                                val returnQty = lines.filter { it.productCode.equals(product.code, true) }.sumOf { it.quantity }
                                if (returnQty > Int.MAX_VALUE - product.stockQuantity) {
                                    error("Stock সীমা অতিক্রম করছে: " + product.name)
                                }
                            }
                            val itemStocks = currentProducts.values.associate { it.code to it.stockQuantity }
                            var paymentId = 0L
                            try {
                                lines.groupBy { it.productCode.lowercase(Locale.getDefault()) }.forEach { (_, groupedLines) ->
                                    val product = currentProducts[groupedLines.first().productCode.lowercase(Locale.getDefault())]
                                        ?: error("পণ্য পাওয়া যায়নি: " + groupedLines.first().productCode)
                                    val totalQty = groupedLines.sumOf { it.quantity }
                                    val stockUpdated = ProductStorage.updateStock(context, product.code, product.stockQuantity + totalQty)
                                    if (!stockUpdated) error("স্টক আপডেট করা যায়নি: " + product.code)
                                }
                                val returnSaved = SaleReturnStorage.addReturn(
                                    context,
                                    SaleReturn(returnId, transactionId, now, returnAmount, dueReduction, refundAmount, refundMethod),
                                    lines
                                )
                                if (!returnSaved) error("রিটার্ন রেকর্ড সংরক্ষণ করা যায়নি")
                                if (dueReduction > 0.0 && tx.customer.isNotBlank()) {
                                    paymentId = CustomerDueStorage.addPayment(
                                        context, tx.customer, tx.mobile, dueReduction,
                                        "বিক্রয় রিটার্ন: $returnId ($transactionId)"
                                    )
                                    if (paymentId <= 0L) error("রিটার্নের বাকি সমন্বয় সংরক্ষণ করা যায়নি")
                                }
                                val logSaved = ActivityLogStorage.add(
                                    context, "বিক্রয় রিটার্ন", "$" + "{returnId} • " + "$" + "{transactionId} • ৳" + money(returnAmount)
                                )
                                if (!logSaved) error("রিটার্নের Activity Log সংরক্ষণ করা যায়নি")
                                onDone()
                            } catch (e: Exception) {
                                if (paymentId > 0L) CustomerDueStorage.removePaymentById(context, paymentId)
                                itemStocks.forEach { (code, stock) -> ProductStorage.updateStock(context, code, stock) }
                                SaleReturnStorage.removeReturn(context, returnId)
                                message = "রিটার্ন সংরক্ষণ ব্যর্থ হয়েছে। কোনো পরিবর্তন রাখা হয়নি."
                            }
                        },
                        Modifier.weight(1f)
                    ) { Text("রিটার্ন সম্পন্ন") }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

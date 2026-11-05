package com.tamanna.enterprise.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinanceActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { FinanceScreen(this) }
    }
}

fun todayFinance() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun financeDate(daysAgo: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun financeMonthStart(): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun validFinanceDate(value: String): Boolean = runCatching {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = format.parse(value) ?: return false
    format.format(parsed) == value
}.getOrDefault(false)

@Composable
fun FinanceScreen(a: ComponentActivity) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("খরচ ও ড্যামেজ", style = MaterialTheme.typography.headlineSmall)
        Row {
            Button({ tab = 0 }, Modifier.weight(1f)) { Text("প্রতিষ্ঠানের খরচ") }
            Button({ tab = 1 }, Modifier.weight(1f)) { Text("ড্যামেজ মাল") }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> ExpensePane(a)
            1 -> DamagePane(a)
        }
    }
}

@Composable
fun ExpensePane(a: ComponentActivity) {
    var cat by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayFinance()) }
    var list by remember { mutableStateOf(ExpenseStorage.getExpenses(a)) }
    var msg by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(financeDate(29)) }
    var endDate by remember { mutableStateOf(todayFinance()) }
    var appliedStart by remember { mutableStateOf(financeDate(29)) }
    var appliedEnd by remember { mutableStateOf(todayFinance()) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    val canManage = SecurityStorage.canManage(a)
    val validRange = validFinanceDate(startDate) && validFinanceDate(endDate) && startDate <= endDate
    val filteredExpenses = list.filter { it.date >= appliedStart && it.date <= appliedEnd }
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
    val filteredTotal = filteredExpenses.sumOf { it.amount }
    // The all-time total is independent of the selected date filter.
    val allTimeTotal = list.sumOf { it.amount.coerceAtLeast(0.0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("প্রতিষ্ঠানের দৈনন্দিন/মাসিক খরচ")
        if (!canManage) Text("শুধু Admin খরচ যোগ ও সম্পাদনা করতে পারবেন।", color = MaterialTheme.colorScheme.error)

        Text("তারিখ অনুযায়ী খরচ দেখুন", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(startDate, { startDate = it }, label = { Text("শুরু (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(endDate, { endDate = it }, label = { Text("শেষ (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(
            onClick = {
                if (validRange) {
                    appliedStart = startDate
                    appliedEnd = endDate
                    list = ExpenseStorage.getExpenses(a)
                    msg = "নির্বাচিত তারিখের হিসাব আপডেট হয়েছে।"
                } else {
                    msg = "সঠিক তারিখ দিন এবং শুরুর তারিখ শেষের তারিখের আগে রাখুন।"
                }
            },
            enabled = validRange,
            modifier = Modifier.fillMaxWidth()
        ) { Text("তারিখ অনুযায়ী হিসাব রিফ্রেশ করুন") }
        Text("খরচের সারসংক্ষেপ", style = MaterialTheme.typography.titleSmall)
        Text("প্রতিষ্ঠানের সর্বমোট খরচ (শুরু থেকে): ৳ %.2f".format(Locale.US, allTimeTotal), style = MaterialTheme.typography.titleMedium)
        Text("নির্বাচিত সময়ের খরচ: ৳ %.2f • এন্ট্রি: ${filteredExpenses.size}টি".format(Locale.US, filteredTotal), style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()
        Text("নতুন খরচ যোগ করুন", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cat, { cat = it }, label = { Text("খরচের ধরন") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amt, { amt = it }, label = { Text("টাকা") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("বিবরণ") }, modifier = Modifier.fillMaxWidth())
        Button({
            if (!canManage) {
                msg = "শুধু Admin খরচ যোগ করতে পারবেন।"
            } else {
                val amount = amt.toDoubleOrNull()
                when {
                    !validFinanceDate(date) -> msg = "সঠিক তারিখ দিন: YYYY-MM-DD"
                    cat.isBlank() -> msg = "খরচের ধরন দিন।"
                    amount == null || amount <= 0 -> msg = "সঠিক টাকার পরিমাণ দিন।"
                    else -> {
                        ExpenseStorage.addExpense(a, Expense(System.currentTimeMillis(), date, cat.trim(), amount, note.trim()))
                        ActivityLogStorage.add(a, "প্রতিষ্ঠানের খরচ যোগ", cat.trim() + " — ৳ " + String.format(Locale.US, "%.2f", amount))
                        list = ExpenseStorage.getExpenses(a)
                        amt = ""
                        note = ""
                        msg = "খরচ সংরক্ষণ হয়েছে।"
                    }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = canManage) { Text("খরচ যোগ করুন") }

        if (msg.isNotBlank()) Text(msg)
        HorizontalDivider()
        Text("খরচের তালিকা", style = MaterialTheme.typography.titleMedium)
        if (filteredExpenses.isEmpty()) Text("নির্বাচিত তারিখে কোনো খরচ পাওয়া যায়নি।")
        Column {
            filteredExpenses.forEach { expense ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.date + " — " + expense.category, style = MaterialTheme.typography.bodyLarge)
                        Text("৳ " + String.format(Locale.US, "%.2f", expense.amount))
                        if (expense.note.isNotBlank()) Text(expense.note, style = MaterialTheme.typography.bodySmall)
                    }
                    if (canManage) {
                        TextButton(onClick = { selectedExpense = expense }) { Text("এডিট") }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    selectedExpense?.let { expense ->
        ExpenseEditDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onSave = { updated ->
                if (!canManage) {
                    msg = "শুধু Admin খরচ সম্পাদনা করতে পারবেন।"
                } else if (ExpenseStorage.updateExpense(a, updated)) {
                    ActivityLogStorage.add(a, "প্রতিষ্ঠানের খরচ সম্পাদনা", updated.category + " — " + updated.date + " — ৳ " + String.format(Locale.US, "%.2f", updated.amount))
                    list = ExpenseStorage.getExpenses(a)
                    msg = "খরচের তথ্য ও তারিখ আপডেট হয়েছে।"
                } else {
                    msg = "খরচটি পাওয়া যায়নি। আবার তালিকা রিফ্রেশ করুন।"
                }
                selectedExpense = null
            }
        )
    }
}

@Composable
private fun ExpenseEditDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var editDate by remember(expense.id) { mutableStateOf(expense.date) }
    var editCategory by remember(expense.id) { mutableStateOf(expense.category) }
    var editAmount by remember(expense.id) { mutableStateOf(expense.amount.toString()) }
    var editNote by remember(expense.id) { mutableStateOf(expense.note) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("খরচ সম্পাদনা করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(editDate, { editDate = it }, label = { Text("তারিখ (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(editCategory, { editCategory = it }, label = { Text("খরচের ধরন") }, singleLine = true)
                OutlinedTextField(editAmount, { editAmount = it }, label = { Text("টাকা") }, singleLine = true)
                OutlinedTextField(editNote, { editNote = it }, label = { Text("বিবরণ") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = editAmount.toDoubleOrNull()
                when {
                    !validFinanceDate(editDate) -> error = "সঠিক তারিখ দিন: YYYY-MM-DD"
                    editCategory.isBlank() -> error = "খরচের ধরন দিন।"
                    amount == null || amount <= 0 -> error = "সঠিক টাকার পরিমাণ দিন।"
                    else -> onSave(expense.copy(date = editDate, category = editCategory.trim(), amount = amount, note = editNote.trim()))
                }
            }) { Text("আপডেট") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun DamagePane(a: ComponentActivity) {
    val canManage = SecurityStorage.canManage(a)
    val products = remember { ProductStorage.getProducts(a) }
    var code by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayFinance()) }
    var list by remember { mutableStateOf(ExpenseStorage.getDamages(a)) }
    var msg by remember { mutableStateOf("") }
    Column {
        Text("ড্যামেজ/নষ্ট/ভাঙা পণ্য")
        if (!canManage) Text("শুধু Admin ড্যামেজ সংরক্ষণ করতে পারবেন।", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it }, label = { Text("পণ্যের কোড") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qty, { qty = it }, label = { Text("পরিমাণ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reason, { reason = it }, label = { Text("কারণ") }, modifier = Modifier.fillMaxWidth())
        Button({
            if (!canManage) {
                msg = "শুধু Admin ড্যামেজ সংরক্ষণ করতে পারবেন."
            } else {
                val product = products.firstOrNull { it.code.equals(code.trim(), true) }
                val quantity = qty.toIntOrNull()
                when {
                    !validFinanceDate(date) -> msg = "সঠিক তারিখ দিন।"
                    product == null -> msg = "পণ্যের কোড মেলেনি।"
                    quantity == null || quantity <= 0 -> msg = "সঠিক পরিমাণ দিন।"
                    quantity > product.stockQuantity -> msg = "স্টকে এত পরিমাণ নেই।"
                    else -> {
                        val stockUpdated = ProductStorage.updateStock(a, product.code, product.stockQuantity - quantity)
                        if (!stockUpdated) {
                            msg = "ড্যামেজের স্টক আপডেট করা যায়নি।"
                        } else {
                            ExpenseStorage.addDamage(a, DamageRecord(System.currentTimeMillis(), date, product.code, product.name, quantity, product.purchasePrice, reason))
                            ActivityLogStorage.add(a, "ড্যামেজ যোগ", product.name + " × " + quantity)
                            list = ExpenseStorage.getDamages(a)
                            msg = "ড্যামেজ যোগ হয়েছে এবং স্টক কমেছে।"
                            code = ""
                            qty = ""
                            reason = ""
                        }
                    }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = canManage) { Text("ড্যামেজ সংরক্ষণ") }
        if (msg.isNotBlank()) Text(msg)
        Text("মোট ক্ষতি: ৳ %.2f".format(Locale.US, list.sumOf { it.totalLoss }))
        LazyColumn {
            items(list.take(50)) {
                Text(it.date + " — " + it.productName + " × " + it.quantity + ": ৳ " + String.format(Locale.US, "%.2f", it.totalLoss), Modifier.padding(5.dp))
            }
        }
    }
}

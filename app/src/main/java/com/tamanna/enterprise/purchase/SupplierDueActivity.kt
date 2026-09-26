package com.tamanna.enterprise.purchase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.settings.ThemeStorage
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.security.ActivityLogStorage

@OptIn(ExperimentalMaterial3Api::class)
class SupplierDueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityStorage.canWrite(this)) { finish(); return }
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { SupplierDueScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierDueScreen() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    val suppliers = remember(refresh) { SupplierDueStorage.getSuppliers(context) }
    val balances = remember(refresh) { SupplierDueStorage.getBalances(context) }
    val entries = remember(refresh) { SupplierDueStorage.getEntries(context) }
    val filtered = suppliers.filter { it.name.contains(query.trim(), true) }
    val total = balances.sumOf { it.second }
    val selectedBalance = if (selected.isBlank()) 0.0 else SupplierDueStorage.getBalance(context, selected)

    Scaffold(topBar = { TopAppBar(title = { Text("সরবরাহকারীর বাকি") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // বাটন এবং টেক্সট সুন্দরভাবে সাজানো হলো
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("মোট বাকি: ৳ %.2f".format(total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { name = ""; mobile = ""; address = ""; message = ""; showAdd = true }) {
                    Text("＋ যোগ করুন")
                }
            }
            
            OutlinedTextField(query, { query = it }, label = { Text("সরবরাহকারী খুঁজুন") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            LazyColumn(Modifier.height(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { supplier ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selected = supplier.name; message = "" }) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(supplier.name)
                            Text("৳ %.2f".format(SupplierDueStorage.getBalance(context, supplier.name)))
                        }
                    }
                }
            }

            if (selected.isNotBlank()) {
                Text("নির্বাচিত: $selected")
                Text("বর্তমান বাকি: ৳ %.2f".format(selectedBalance))
                OutlinedTextField(payment, { payment = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("পরিশোধের পরিমাণ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("নোট (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    val amount = payment.toDoubleOrNull()
                    when {
                        amount == null || amount <= 0 -> message = "সঠিক পরিশোধের পরিমাণ দিন।"
                        amount > selectedBalance -> message = "পরিশোধ বর্তমান বাকি থেকে বেশি হতে পারবে না।"
                        else -> {
                            val paymentId = SupplierDueStorage.addPayment(context, selected, amount, note.trim())
                            if (paymentId <= 0L) {
                                message = "পরিশোধ সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন."
                            } else {
                                val logId = ActivityLogStorage.addAndGetId(
                                    context,
                                    "সরবরাহকারীকে পরিশোধ",
                                    selected + " • ৳ " + String.format("%.2f", amount) +
                                        if (note.isBlank()) "" else " • " + note.trim()
                                )
                                if (logId <= 0L) {
                                    SupplierDueStorage.removePaymentById(context, paymentId)
                                    message = "পরিশোধের Activity Log সংরক্ষণ করা যায়নি। পরিশোধ rollback করা হয়েছে।"
                                } else {
                                    payment = ""; note = ""; message = "পরিশোধ সংরক্ষণ হয়েছে।"; refresh++
                                }
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("সরবরাহকারীকে পরিশোধ করুন") }
            }

            if (message.isNotBlank()) Text(message, color = if (message.contains("সফল") || message.contains("সংরক্ষণ")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            
            Text("লেনদেনের ইতিহাস", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries.take(30), key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            val label = if (entry.type == "PURCHASE") "ক্রয় বাকি" else "পরিশোধ"
                            Text(entry.supplier + " • " + label + " • ৳ %.2f".format(kotlin.math.abs(entry.amount)))
                            Text(entry.date + if (entry.note.isBlank()) "" else " • " + entry.note,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // সরবরাহকারী যোগ করার হারানো ডায়ালগটি যুক্ত করা হলো
        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("নতুন সরবরাহকারী যোগ") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it; message = "" }, label = { Text("নাম *") }, singleLine = true)
                        OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter(Char::isDigit) }, label = { Text("মোবাইল নম্বর") }, singleLine = true)
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("ঠিকানা") }, singleLine = true)
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (name.trim().isBlank()) {
                            message = "সরবরাহকারীর নাম দেওয়া আবশ্যক।"
                        } else {
                            val ok = SupplierDueStorage.addSupplier(context, name, mobile, address)
                            if (ok) {
                                showAdd = false
                                refresh++
                                message = ""
                            } else {
                                message = "এই নামের সরবরাহকারী আগে থেকেই আছে।"
                            }
                        }
                    }) { Text("সংরক্ষণ") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false; message = "" }) { Text("বাতিল") }
                }
            )
        }
    }
}

package com.tamanna.enterprise.due

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
import com.tamanna.enterprise.dashboard.TamannaTheme
import java.util.Locale

class CustomerDueActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent { TamannaTheme("green") { CustomerDueScreen() } }
    }

    @Composable
    private fun CustomerDueScreen() {
        val context = this
        var refresh by remember { mutableIntStateOf(0) }
        var search by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var payment by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        val entries = remember(refresh) { CustomerDueStorage.getEntries(context) }
        val balances = remember(refresh) { CustomerDueStorage.getBalances(context) }
        val filtered = balances.filterKeys { it.contains(search.trim(), true) }

        Scaffold(topBar = { TopAppBar(title = { Text("ক্রেতার বাকি") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("মোট পাওনা: ৳ " + String.format(Locale.getDefault(), "%.2f", balances.values.sum()), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("ক্রেতা খুঁজুন") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                if (selected.isNotBlank()) {
                    Text("নির্বাচিত: $selected", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter(Char::isDigit) }, label = { Text("মোবাইল") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("বর্তমান বাকি: ৳ " + String.format(Locale.getDefault(), "%.2f", CustomerDueStorage.getBalance(context, selected, mobile)))
                    OutlinedTextField(value = payment, onValueChange = { payment = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("জমা ৳") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("নোট") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val amount = payment.toDoubleOrNull() ?: 0.0
                        val balance = CustomerDueStorage.getBalance(context, selected, mobile)
                        if (amount > 0 && amount <= balance) {
                            CustomerDueStorage.addPayment(context, selected, mobile, amount, note)
                            payment = ""; note = ""; refresh++
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("বাকি থেকে জমা নিন") }
                }

                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered.toList(), key = { it.first }) { (customerKey, balance) ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(customerKey, style = MaterialTheme.typography.titleMedium)
                                Text("পাওনা: ৳ " + String.format(Locale.getDefault(), "%.2f", balance))
                                TextButton(onClick = {
                                    selected = customerKey.substringBefore(" • ")
                                    mobile = customerKey.substringAfter(" • ", "")
                                    search = customerKey
                                }) { Text("পেমেন্ট নিন") }
                            }
                        }
                    }
                }

                Text("লেনদেন ইতিহাস", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    items(entries.take(30), key = { it.id }) { entry ->
                        val label = if (entry.type == "SALE") "বাকি +" else "জমা -"
                        Text(entry.date + " • " + entry.customer + " • " + label + "৳" + String.format(Locale.getDefault(), "%.2f", entry.amount))
                    }
                }
            }
        }
    }
}

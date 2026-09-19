package com.tamanna.enterprise.partner

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class PartnerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PartnerScreen(this) }
    }
}

@Composable
private fun PartnerScreen(activity: ComponentActivity) {
    var partners by remember { mutableStateOf(PartnerStorage.getPartners(activity)) }
    var name by remember { mutableStateOf("") }
    var investment by remember { mutableStateOf("") }
    var percentage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedPartner by remember { mutableStateOf<Partner?>(null) }

    val totalInvestment = partners.sumOf { it.investment }
    val totalPercentage = partners.sumOf { it.percentage }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("পার্টনার ও বিনিয়োগ", style = MaterialTheme.typography.headlineMedium)
                Text("প্রত্যেক পার্টনারের বিনিয়োগ ও লাভের শতাংশ নির্ধারণ করুন।")
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(name, { name = it }, label = { Text("পার্টনারের নাম") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(investment, { investment = it }, label = { Text("বিনিয়োগ (টাকা)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(percentage, { percentage = it }, label = { Text("লাভের অংশ (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val inv = investment.toDoubleOrNull()
                        val pct = percentage.toDoubleOrNull()
                        when {
                            name.isBlank() || inv == null || pct == null || inv < 0 || pct < 0 -> message = "সঠিক নাম, বিনিয়োগ ও শতাংশ দিন।"
                            totalPercentage + pct > 100.0001 -> message = "মোট লাভের শতাংশ ১০০%-এর বেশি হতে পারবে না।"
                            else -> {
                                PartnerStorage.addPartner(activity, name, inv, pct)
                                partners = PartnerStorage.getPartners(activity)
                                name = ""; investment = ""; percentage = ""
                                message = "পার্টনার যোগ হয়েছে।"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("পার্টনার যোগ করুন") }
                Spacer(Modifier.height(8.dp))
                Text("মোট বিনিয়োগ: ৳ ${"%.2f".format(Locale.US, totalInvestment)}")
                Text("মোট লাভের অংশ: ${"%.2f".format(Locale.US, totalPercentage)}%")
                if (message.isNotBlank()) Text(message)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(partners, key = { it.id }) { p ->
                        Card(Modifier.fillMaxWidth().clickable { selectedPartner = p }) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("বিনিয়োগ ও লাভের অংশ দেখতে ট্যাপ করুন", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    selectedPartner?.let { p ->
        AlertDialog(
            onDismissRequest = { selectedPartner = null },
            title = { Text(p.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("বিনিয়োগ: ৳ " + "%.2f".format(Locale.US, p.investment))
                Text("লাভের অংশ: " + "%.2f".format(Locale.US, p.percentage) + "%")
            }},
            confirmButton = { Button(onClick = { selectedPartner = null }) { Text("বন্ধ") } },
            dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { activity.startActivity(Intent(activity, PartnerLedgerActivity::class.java).putExtra("partner_id", p.id)); selectedPartner = null }) { Text("লেজার") }; Button(onClick = { PartnerStorage.deletePartner(activity, p.id); partners = PartnerStorage.getPartners(activity); selectedPartner = null }) { Text("মুছে ফেলুন") } } }
        )
    }
}

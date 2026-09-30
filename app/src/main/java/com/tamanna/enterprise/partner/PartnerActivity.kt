package com.tamanna.enterprise.partner

import android.os.Bundle
import android.widget.Toast
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
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.settings.ThemeStorage

class PartnerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                PartnerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen() {
    val context = LocalContext.current
    val canWrite = remember { SecurityStorage.canWrite(context) }

    var partners by remember { mutableStateOf(PartnerStorage.getPartners(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPartner by remember { mutableStateOf<Partner?>(null) }

    fun refreshData() {
        partners = PartnerStorage.getPartners(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("পার্টনার / অংশীদার বিবরণ") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (canWrite) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("＋ নতুন পার্টনার যোগ করুন")
                }
            } else {
                Text(
                    "👁 VIEW ONLY — পার্টনারের তথ্য দেখা যাবে, পরিবর্তন করা যাবে না।",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val totalShare = partners.sumOf { it.shareAmount }
            val totalProfitPct = partners.sumOf { it.profitPercentage }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("মোট অংশীদারিত্ব/মূলধন: ৳ %.2f".format(totalShare), style = MaterialTheme.typography.titleMedium)
                    Text("মোট লাভের শতাংশ বন্টন: %.1f%%".format(totalProfitPct), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("পার্টনারদের তালিকা", style = MaterialTheme.typography.titleLarge)

            if (partners.isEmpty()) {
                Text("এখনও কোনো পার্টনারের তথ্য যোগ করা হয়নি।")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(partners, key = { it.id }) { partner ->
                        PartnerCard(
                            partner = partner,
                            canWrite = canWrite,
                            onClick = {
                                if (canWrite) {
                                    editingPartner = partner
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && canWrite) {
        AddEditPartnerDialog(
            partner = null,
            onDismiss = { showAddDialog = false },
            onSaved = { newPartner ->
                val saved = PartnerStorage.addPartner(context, newPartner)
                if (saved) {
                    ActivityLogStorage.addAndGetId(
                        context,
                        "নতুন পার্টনার যোগ",
                        "${newPartner.name} • অংশ: ৳${newPartner.shareAmount} • লাভ: ${newPartner.profitPercentage}%"
                    )
                    Toast.makeText(context, "পার্টনার যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    refreshData()
                    showAddDialog = false
                } else {
                    Toast.makeText(context, "তথ্য সেভ করা যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    editingPartner?.let { partnerToEdit ->
        AddEditPartnerDialog(
            partner = partnerToEdit,
            onDismiss = { editingPartner = null },
            onSaved = { updatedPartner ->
                val saved = PartnerStorage.updatePartner(context, updatedPartner)
                if (saved) {
                    ActivityLogStorage.addAndGetId(
                        context,
                        "পার্টনার তথ্য আপডেট",
                        "${updatedPartner.name} • অংশ: ৳${updatedPartner.shareAmount} • লাভ: ${updatedPartner.profitPercentage}%"
                    )
                    Toast.makeText(context, "তথ্য আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                    refreshData()
                    editingPartner = null
                } else {
                    Toast.makeText(context, "আপডেট করা যায়নি", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                val deleted = PartnerStorage.deletePartner(context, partnerToEdit.id)
                if (deleted) {
                    ActivityLogStorage.addAndGetId(
                        context,
                        "পার্টনার মুছে ফেলা",
                        "${partnerToEdit.name} (${partnerToEdit.id})"
                    )
                    Toast.makeText(context, "পার্টনার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    refreshData()
                    editingPartner = null
                }
            }
        )
    }
}

@Composable
fun PartnerCard(
    partner: Partner,
    canWrite: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canWrite, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(partner.name, style = MaterialTheme.typography.titleMedium)
                if (canWrite) {
                    Text("সম্পাদনা ✎", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            Text("অংশীদারিত্বের পরিমাণ (Share): ৳ %.2f".format(partner.shareAmount), style = MaterialTheme.typography.bodyMedium)
            Text("লাভের হার (Profit): %.1f%%".format(partner.profitPercentage), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (partner.note.isNotBlank()) {
                Text("নোট: ${partner.note}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AddEditPartnerDialog(
    partner: Partner?,
    onDismiss: () -> Unit,
    onSaved: (Partner) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(partner?.name.orEmpty()) }
    var share by remember { mutableStateOf(partner?.shareAmount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var profit by remember { mutableStateOf(partner?.profitPercentage?.takeIf { it > 0 }?.toString().orEmpty()) }
    var note by remember { mutableStateOf(partner?.note.orEmpty()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (partner == null) "নতুন পার্টনার যোগ" else "পার্টনার তথ্য সম্পাদনা") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    label = { Text("পার্টনারের নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    label = { Text("অংশীদারিত্বের পরিমাণ (৳) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = profit,
                    onValueChange = { profit = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    label = { Text("লাভের শতাংশ (%) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val shareAmount = share.toDoubleOrNull()
                val profitPct = profit.toDoubleOrNull()
                when {
                    name.trim().isBlank() -> error = "পার্টনারের নাম দিন।"
                    shareAmount == null || shareAmount < 0 -> error = "সঠিক অংশীদারিত্বের পরিমাণ দিন।"
                    profitPct == null || profitPct < 0 || profitPct > 100 -> error = "সঠিক লাভের শতাংশ দিন (০-১০০%)।"
                    else -> {
                        onSaved(
                            Partner(
                                id = partner?.id ?: System.currentTimeMillis(),
                                name = name.trim(),
                                shareAmount = shareAmount,
                                profitPercentage = profitPct,
                                note = note.trim()
                            )
                        )
                    }
                }
            }) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (partner != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("বাতিল")
                }
            }
        }
    )
}

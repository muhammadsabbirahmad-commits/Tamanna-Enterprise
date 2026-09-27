package com.tamanna.enterprise.security

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.settings.ThemeStorage

@OptIn(ExperimentalMaterial3Api::class)
class UserManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityStorage.canWrite(this)) { finish(); return }
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { UserManagementScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen() {
    val context = LocalContext.current
    var newEmail by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<CloudAccessUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }

    fun loadData() {
        loading = true
        db.collection("preApprovedPartners").get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    val email = doc.getString("email") ?: return@mapNotNull null
                    CloudAccessUser(doc.id, email, "PARTNER", true, false)
                }
                members = list
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("পার্টনার ব্যবস্থাপনা") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("নতুন পার্টনার যোগ করুন (View-Only)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("কর্মচারীর সঠিক জিমেইল অ্যাড্রেসটি লিখে অ্যাড করুন।", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("পার্টনারের জিমেইল") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val emailToSave = newEmail.trim().lowercase()
                                if (emailToSave.isNotBlank() && emailToSave.contains("@")) {
                                    val partnerKey = "partner_" + emailToSave.replace(".", "_").replace("@", "_")
                                    val data = mapOf("email" to emailToSave, "addedAt" to System.currentTimeMillis())
                                    
                                    db.collection("preApprovedPartners").document(partnerKey)
                                        .set(data)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "পার্টনার সফলভাবে অ্যাড করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                            newEmail = ""
                                            loadData()
                                        }
                                        .addOnFailureListener { Toast.makeText(context, "ত্রুটি হয়েছে!", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        ) { Text("অ্যাড করুন") }
                    }
                }
            }

            Text("সংযুক্ত পার্টনারগণ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (loading) { Text("লোড হচ্ছে...") } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members) { user ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(user.email, style = MaterialTheme.typography.titleMedium)
                                    Text("রোল: পার্টনার (View-Only)", style = MaterialTheme.typography.bodySmall)
                                    Text("স্ট্যাটাস: Active ✅", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(onClick = {
                                    db.collection("preApprovedPartners").document(user.uid).delete()
                                        .addOnSuccessListener { loadData() }
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

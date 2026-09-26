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
import com.tamanna.enterprise.business.BusinessAccountStorage
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
    var invites by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    
    val businessId = remember { BusinessAccountStorage.get(context).businessId }
    val db = remember { FirebaseFirestore.getInstance() }

    fun loadData() {
        loading = true
        CloudAccessManager.listBusinessMembers(context) { list, _ ->
            members = list
            db.collection("businesses").document(businessId).collection("invites").get()
                .addOnSuccessListener { snap -> 
                    invites = snap.documents.map { it.id }
                    loading = false 
                }
                .addOnFailureListener { loading = false }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("পার্টনার ব্যবস্থাপনা") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
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
                                    db.collection("businesses").document(businessId)
                                        .collection("invites").document(emailToSave)
                                        .set(mapOf("email" to emailToSave, "role" to "PARTNER", "addedAt" to System.currentTimeMillis()))
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "পার্টনার সফলভাবে অ্যাড করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                            newEmail = ""
                                            loadData()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "সেভ করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    Toast.makeText(context, "দয়া করে একটি সঠিক জিমেইল লিখুন", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("অ্যাড করুন")
                        }
                    }
                }
            }

            if (invites.isNotEmpty()) {
                Text("অপেক্ষারত পার্টনার (লগইন করেনি)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(invites) { email ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(email, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    db.collection("businesses").document(businessId)
                                        .collection("invites").document(email)
                                        .delete()
                                        .addOnSuccessListener { loadData() }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Text("সংযুক্ত পার্টনার ও অ্যাডমিন", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (loading) {
                Text("লোড হচ্ছে...")
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members) { user ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(user.email, style = MaterialTheme.typography.titleMedium)
                                Text("রোল: ${if (user.role == SecurityStorage.ROLE_ADMIN) "অ্যাডমিন" else "পার্টনার (View-Only)"}", style = MaterialTheme.typography.bodySmall)
                                
                                val statusText = if (user.blocked) "Blocked ❌" else if (!user.approved) "Pending ⏳" else "Active ✅"
                                val statusColor = if (user.blocked) MaterialTheme.colorScheme.error else if (!user.approved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                Text("স্ট্যাটাস: $statusText", color = statusColor, style = MaterialTheme.typography.labelMedium)

                                if (user.role != SecurityStorage.ROLE_ADMIN) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (user.blocked) {
                                            Button(onClick = { CloudAccessManager.unblockPartner(context, user.uid) { _, _ -> loadData() } }) { Text("Unblock") }
                                        } else {
                                            Button(
                                                onClick = { CloudAccessManager.blockPartner(context, user.uid) { _, _ -> loadData() } },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) { Text("Block") }
                                        }
                                        Button(
                                            onClick = { CloudAccessManager.removePartner(context, user.uid) { _, _ -> loadData() } },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

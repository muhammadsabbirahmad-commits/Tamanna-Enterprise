package com.tamanna.enterprise.security

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class UserManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = SecurityStorage.getCurrentUser(this)
        if (current?.role != SecurityStorage.ROLE_ADMIN) {
            finish()
            return
        }

        setContent {
            var refresh by remember { mutableStateOf(0) }
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var role by remember { mutableStateOf(SecurityStorage.ROLE_PARTNER) }
            var message by remember { mutableStateOf("") }
            var deleteTarget by remember { mutableStateOf<AppUser?>(null) }
            var deletePin by remember { mutableStateOf("") }
            var deleteError by remember { mutableStateOf("") }
            var approveTarget by remember { mutableStateOf<AppUser?>(null) }

            val users = remember(refresh) { SecurityStorage.getUsers(this@UserManagementActivity) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Text("ইউজার ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(username, { username = it }, label = { Text("ইউজারনেম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(password, { password = it }, label = { Text("পাসওয়ার্ড / PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                SecurityStorage.ROLE_PARTNER to "পার্টনার",
                                SecurityStorage.ROLE_VIEWER to "ভিউয়ার"
                            ).forEach { (value, label) ->
                                Row {
                                    RadioButton(selected = role == value, onClick = { role = value })
                                    Text(label, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                message = if (SecurityStorage.addUser(this@UserManagementActivity, username, password, role)) {
                                    username = ""
                                    password = ""
                                    refresh++
                                    "ইউজার যোগ হয়েছে।"
                                } else "ইউজারনেম আগে থেকেই আছে অথবা তথ্য অসম্পূর্ণ।"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ইউজার যোগ করুন") }

                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(message)
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("বর্তমান ইউজার", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(users, key = { it.id }) { user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.username)
                                        Text(SecurityStorage.roleLabel(user.role) + if (user.approved) " • অনুমোদিত" else " • অনুমোদনের অপেক্ষায়", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!user.approved && user.id != current.id) {
                                        Button(onClick = { approveTarget = user }) { Text("Approve") }
                                    }
                                    if (user.id != current.id) {
                                        Button(onClick = {
                                            deleteTarget = user
                                            deletePin = ""
                                            deleteError = ""
                                        }) { Text("মুছুন") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            approveTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = { approveTarget = null },
                    title = { Text("Admin অনুমোদন") },
                    text = { Text(target.username + " — " + SecurityStorage.roleLabel(target.role) + " অ্যাকাউন্ট অনুমোদন করবেন?") },
                    confirmButton = {
                        Button(onClick = {
                            if (SecurityStorage.approveUser(this@UserManagementActivity, target.id)) {
                                ActivityLogStorage.add(this@UserManagementActivity, "ইউজার অনুমোদন", target.username + " (" + SecurityStorage.roleLabel(target.role) + ")")
                                approveTarget = null
                                refresh++
                                message = "ইউজার অনুমোদন হয়েছে।"
                            }
                        }) { Text("অনুমোদন") }
                    },
                    dismissButton = { Button(onClick = { approveTarget = null }) { Text("বাতিল") } }
                )
            }

            deleteTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text("Admin PIN দিয়ে মুছুন") },
                    text = {
                        Column {
                            Text("ইউজার: ${target.username}")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = deletePin,
                                onValueChange = { deletePin = it },
                                label = { Text("Admin PIN") },
                                singleLine = true
                            )
                            if (deleteError.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(deleteError)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val admin = SecurityStorage.getUsers(this@UserManagementActivity)
                                .firstOrNull { it.role == SecurityStorage.ROLE_ADMIN }
                            if (admin != null && admin.passwordHash == SecurityStorage.hashPassword(deletePin)) {
                                if (SecurityStorage.deleteUser(this@UserManagementActivity, target.id)) {
                                    ActivityLogStorage.add(
                                        this@UserManagementActivity,
                                        "ইউজার মুছে ফেলা",
                                        target.username + " (" + SecurityStorage.roleLabel(target.role) + ")"
                                    )
                                    deleteTarget = null
                                    deletePin = ""
                                    deleteError = ""
                                    refresh++
                                } else deleteError = "এই ইউজার মুছে ফেলা যাচ্ছে না।"
                            } else deleteError = "ভুল Admin PIN।"
                        }) { Text("নিশ্চিত করুন") }
                    },
                    dismissButton = { Button(onClick = { deleteTarget = null }) { Text("বাতিল") } }
                )
            }
        }
    }
}

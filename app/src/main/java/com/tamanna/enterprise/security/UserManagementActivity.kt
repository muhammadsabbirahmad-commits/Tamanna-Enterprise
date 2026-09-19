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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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

            val users = remember(refresh) { SecurityStorage.getUsers(this@UserManagementActivity) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp)
                    ) {
                        Text("ইউজার ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("ইউজারনেম") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("পাসওয়ার্ড / PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                } else {
                                    "ইউজারনেম আগে থেকেই আছে অথবা তথ্য অসম্পূর্ণ।"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ইউজার যোগ করুন")
                        }

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
                                        Text(SecurityStorage.roleLabel(user.role), style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (user.id != current.id) {
                                        Button(onClick = {
                                            SecurityStorage.deleteUser(this@UserManagementActivity, user.id)
                                            refresh++
                                        }) {
                                            Text("মুছুন")
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
}

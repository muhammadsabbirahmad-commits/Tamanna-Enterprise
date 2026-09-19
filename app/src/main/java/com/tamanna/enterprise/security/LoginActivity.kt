package com.tamanna.enterprise.security

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.DashboardActivity

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)

        setContent {
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var error by remember { mutableStateOf("") }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("লগইন করুন", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; error = "" },
                            label = { Text("ইউজারনেম") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = "" },
                            label = { Text("পাসওয়ার্ড / PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        if (error.isNotBlank()) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                val user = SecurityStorage.verifyLogin(this@LoginActivity, username, password)
                                if (user != null) {
                                    SecurityStorage.login(this@LoginActivity, user)
                                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                                    finish()
                                } else {
                                    error = "ইউজারনেম বা পাসওয়ার্ড সঠিক নয়।"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("লগইন")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "প্রথমবারের ডিফল্ট অ্যাডমিন: admin / 1234 — লগইন চালু করার পর অবশ্যই পরিবর্তন করুন।",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

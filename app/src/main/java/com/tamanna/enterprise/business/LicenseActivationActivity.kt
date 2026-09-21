package com.tamanna.enterprise.business

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

class LicenseActivationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var code by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("") }

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("License Activation", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("অ্যাপ ব্যবহার শুরু করতে আপনার অনুমোদিত License Code দিন।")
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase(); message = "" },
                            label = { Text("License Code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                loading = true
                                message = ""
                                fun verifyLicenseAfterAuth() {
                                    LicenseManager.verify(code) { ok, info, text ->
                                        loading = false
                                        if (ok && info != null) {
                                            LicenseManager.saveVerifiedLicense(this@LicenseActivationActivity, info)
                                            startActivity(
                                                android.content.Intent(
                                                    this@LicenseActivationActivity,
                                                    com.tamanna.enterprise.security.LoginActivity::class.java
                                                )
                                            )
                                            finish()
                                        } else {
                                            message = text
                                        }
                                    }
                                }

                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {
                                    verifyLicenseAfterAuth()
                                } else {
                                    FirebaseAuth.getInstance().signInAnonymously()
                                        .addOnSuccessListener { verifyLicenseAfterAuth() }
                                        .addOnFailureListener {
                                            loading = false
                                            message = "License যাচাইয়ের আগে নিরাপদ সংযোগ তৈরি করা যায়নি। আবার চেষ্টা করুন।"
                                        }
                                }
                            },
                            enabled = !loading && code.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (loading) "License যাচাই হচ্ছে..." else "License Activate করুন")
                        }
                    }
                }
            }
        }
    }
}

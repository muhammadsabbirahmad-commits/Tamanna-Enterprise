package com.tamanna.enterprise.security

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tamanna.enterprise.dashboard.DashboardActivity

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    private var googleOnSuccess: (() -> Unit)? = null
    private var googleOnError: ((String) -> Unit)? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)

                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    googleOnError?.invoke("Google ID Token পাওয়া যায়নি। Firebase/Google সেটআপ পরীক্ষা করতে হবে।")
                    googleOnSuccess = null
                    googleOnError = null
                    return@registerForActivityResult
                }

                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            googleOnSuccess?.invoke()
                        } else {
                            googleOnError?.invoke(
                                task.exception?.localizedMessage
                                    ?: "Firebase Google লগইন ব্যর্থ হয়েছে।"
                            )
                        }
                        googleOnSuccess = null
                        googleOnError = null
                    }
            } catch (e: ApiException) {
                val message = when (e.statusCode) {
                    12501 -> "Google অ্যাকাউন্ট নির্বাচন বাতিল হয়েছে।"
                    10 -> "Google লগইন কনফিগারেশন ভুল। এই APK-এর SHA-1 Firebase-এ যোগ করা আছে কি না পরীক্ষা করতে হবে।"
                    7 -> "Google সার্ভারে সংযোগ করা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।"
                    else -> "Google লগইন ব্যর্থ হয়েছে। Status code: ${e.statusCode}"
                }
                googleOnError?.invoke(message)
                googleOnSuccess = null
                googleOnError = null
            } catch (e: Exception) {
                googleOnError?.invoke(e.localizedMessage ?: "Google লগইনে একটি সমস্যা হয়েছে।")
                googleOnSuccess = null
                googleOnError = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        auth = FirebaseAuth.getInstance()

        setContent {
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var error by remember { mutableStateOf("") }
            var googleLoading by remember { mutableStateOf(false) }

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

                        Button(
                            onClick = {
                                error = ""
                                googleLoading = true
                                googleOnSuccess = {
                                    googleLoading = false
                                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                                    finish()
                                }
                                googleOnError = {
                                    googleLoading = false
                                    error = it
                                }

                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(getString(com.tamanna.enterprise.R.string.default_web_client_id))
                                    .requestEmail()
                                    .build()

                                val client = GoogleSignIn.getClient(this@LoginActivity, gso)
                                googleSignInLauncher.launch(client.signInIntent)
                            },
                            enabled = !googleLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (googleLoading) "Google লগইন হচ্ছে..." else "Google দিয়ে লগইন")
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

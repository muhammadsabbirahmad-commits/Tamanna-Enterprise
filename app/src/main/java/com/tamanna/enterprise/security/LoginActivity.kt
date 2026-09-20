package com.tamanna.enterprise.security

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
import androidx.compose.runtime.LaunchedEffect
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
import com.tamanna.enterprise.sync.CloudSyncManager

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var masterPassword = ""
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
                    clearCallbacks()
                    return@registerForActivityResult
                }

                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (!task.isSuccessful) {
                            googleOnError?.invoke(
                                task.exception?.localizedMessage
                                    ?: "Firebase Google লগইন ব্যর্থ হয়েছে।"
                            )
                            clearCallbacks()
                            return@addOnCompleteListener
                        }

                        val email = auth.currentUser?.email.orEmpty()
                        CloudAccessManager.resolveGoogleLogin(
                            context = this@LoginActivity,
                            email = email,
                            masterPassword = masterPassword
                        ) { success, message, _ ->
                            masterPassword = ""
                            if (success) {
                                CloudSyncManager.pullThenSync(this@LoginActivity) {
                                    googleOnSuccess?.invoke()
                                    clearCallbacks()
                                }
                            } else {
                                googleOnError?.invoke(message)
                                clearCallbacks()
                            }
                        }
                    }
            } catch (e: ApiException) {
                val message = when (e.statusCode) {
                    12501 -> "Google অ্যাকাউন্ট নির্বাচন বাতিল হয়েছে।"
                    10 -> "Google লগইন কনফিগারেশন ভুল। এই APK-এর SHA-1 Firebase-এ যোগ করা আছে কি না পরীক্ষা করতে হবে।"
                    7 -> "Google সার্ভারে সংযোগ করা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।"
                    else -> "Google লগইন ব্যর্থ হয়েছে। Status code: ${e.statusCode}"
                }
                googleOnError?.invoke(message)
                clearCallbacks()
            } catch (e: Exception) {
                googleOnError?.invoke(e.localizedMessage ?: "Google লগইনে একটি সমস্যা হয়েছে।")
                clearCallbacks()
            }
        }

    private fun clearCallbacks() {
        googleOnSuccess = null
        googleOnError = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        auth = FirebaseAuth.getInstance()

        setContent {
            var error by remember { mutableStateOf("") }
            var googleLoading by remember { mutableStateOf(false) }
            var master by remember { mutableStateOf("") }
            var firstAdminSetup by remember { mutableStateOf(false) }
            var checkingAccess by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                CloudAccessManager.hasApprovedAdmin {
                    firstAdminSetup = !it
                    checkingAccess = false
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Google দিয়ে নিরাপদ লগইন", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))

                        if (checkingAccess) {
                            Text("অ্যাক্সেস যাচাই করা হচ্ছে...")
                        } else {
                            if (firstAdminSetup) {
                                Text(
                                    "এটি প্রথম Admin সেটআপ। আপনার Master Password একবার দিতে হবে। সঠিক হলে নির্বাচিত Gmail স্থায়ীভাবে Admin হিসেবে অনুমোদিত হবে।",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = master,
                                    onValueChange = { master = it; error = "" },
                                    label = { Text("Master Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(12.dp))
                            } else {
                                Text(
                                    "অনুমোদিত Admin Gmail হলে সরাসরি লগইন হবে। নতুন Gmail স্বয়ংক্রিয়ভাবে Admin হবে না; সেটি Partner approval-এর জন্য Pending হবে।",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            if (error.isNotBlank()) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                            }

                            Button(
                                onClick = {
                                    error = ""
                                    masterPassword = master
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

                                    GoogleSignIn.getClient(this@LoginActivity, gso)
                                        .signInIntent
                                        .also { googleSignInLauncher.launch(it) }
                                },
                                enabled = !googleLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (googleLoading) "Google লগইন হচ্ছে..." else "Google দিয়ে লগইন")
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Partner: Google authentication → Pending Approval → Admin অনুমোদন → তারপর প্রবেশ।",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

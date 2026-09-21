package com.tamanna.enterprise.security

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
    private var adminLoginMode = true
    private var googleOnSuccess: (() -> Unit)? = null
    private var googleOnError: ((String) -> Unit)? = null
    private var masterPrompt: (() -> Unit)? = null

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
                            googleOnError?.invoke(task.exception?.localizedMessage ?: "Firebase Google লগইন ব্যর্থ হয়েছে।")
                            clearCallbacks()
                            return@addOnCompleteListener
                        }
                        resolveCurrentGoogle("")
                    }
            } catch (e: ApiException) {
                val message = when (e.statusCode) {
                    12501 -> "Google অ্যাকাউন্ট নির্বাচন বাতিল হয়েছে।"
                    10 -> "Google লগইন কনফিগারেশন ভুল। Firebase/Google সেটআপ পরীক্ষা করতে হবে।"
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

    private fun resolveCurrentGoogle(masterPassword: String) {
        val email = auth.currentUser?.email.orEmpty()
        CloudAccessManager.resolveGoogleLogin(
            context = this,
            email = email,
            masterPassword = masterPassword,
            adminLogin = adminLoginMode
        ) { success, message, _ ->
            if (success) {
                googleOnSuccess?.invoke()
                clearCallbacks()
            } else if (adminLoginMode && message == CloudAccessManager.MASTER_REQUIRED) {
                masterPrompt?.invoke()
            } else {
                googleOnError?.invoke(message)
                clearCallbacks()
            }
        }
    }

    private fun clearCallbacks() {
        googleOnSuccess = null
        googleOnError = null
        masterPrompt = null
    }

    private fun startGoogleLogin() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.tamanna.enterprise.R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(this, gso)
            .signInIntent
            .also { googleSignInLauncher.launch(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        auth = FirebaseAuth.getInstance()

        adminLoginMode = intent.getStringExtra("LOGIN_MODE") != "PARTNER"

        setContent {
            var error by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var showMaster by remember { mutableStateOf(false) }
            var master by remember { mutableStateOf("") }
            var masterLoading by remember { mutableStateOf(false) }

            masterPrompt = {
                showMaster = true
                loading = false
                error = ""
                masterLoading = false
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (adminLoginMode) "Admin Login" else "Partner Login",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(12.dp))

                        if (showMaster) {
                            Text(
                                "Google/Gmail account সফলভাবে সংযুক্ত হয়েছে। এখন Master Password দিন। Admin-কে কেউ Approve করবে না।",
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

                            if (error.isNotBlank()) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                            }

                            Button(
                                onClick = {
                                    error = ""
                                    masterLoading = true
                                    resolveCurrentGoogle(master)
                                },
                                enabled = !masterLoading && master.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (masterLoading) "Admin লগইন হচ্ছে..." else "Master Password দিয়ে Admin প্রবেশ")
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    auth.signOut()
                                    finish()
                                },
                                enabled = !masterLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ফিরে যান")
                            }
                        } else {
                            Text(
                                if (adminLoginMode)
                                    "Gmail Connect করুন। প্রথমবার Master Password দিয়ে Admin সেটআপ হবে।"
                                else
                                    "Gmail Connect করুন। Admin অনুমোদনের পর Partner Login চালু হবে।",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))

                            if (error.isNotBlank()) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(10.dp))
                            }

                            Button(
                                onClick = {
                                    error = ""
                                    loading = true
                                    googleOnSuccess = {
                                        loading = false
                                        setResult(RESULT_OK)
                                        finish()
                                    }
                                    googleOnError = {
                                        loading = false
                                        error = it
                                    }
                                    startGoogleLogin()
                                },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (loading) "Google সংযোগ হচ্ছে..."
                                    else if (adminLoginMode) "👑 Gmail Connect → Master Password"
                                    else "Gmail Connect"
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { finish() },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ফিরে যান")
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.tamanna.enterprise.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SettingsStorage {
    private const val PREFS = "tamanna_enterprise_settings"
    private const val KEY_SHOP_NAME = "shop_name"
    private const val DEFAULT_SHOP_NAME = "Tamanna Enterprise"

    fun getShopName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME)
            ?: DEFAULT_SHOP_NAME

    fun saveShopName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHOP_NAME, name.trim().ifBlank { DEFAULT_SHOP_NAME })
            .apply()
    }
}

class SettingsActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        super.onCreate(savedInstanceState)

        setContent {
            SettingsScreen(
                initialShopName = SettingsStorage.getShopName(this),
                onSave = { name ->
                    SettingsStorage.saveShopName(this, name)
                    finish()
                },
                onCancel = { finish() },
                onGoogleSignIn = { onSuccess, onError -> signInWithGoogle(onSuccess, onError) },
                googleEmail = auth.currentUser?.email
            )
        }
    }

    private fun signInWithGoogle(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(getString(com.tamanna.enterprise.R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = this@SettingsActivity,
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: GoogleIdTokenParsingException) {
                        onError("Google অ্যাকাউন্টের তথ্য পড়া যায়নি।")
                        return@launch
                    }

                    val firebaseCredential = GoogleAuthProvider.getCredential(
                        googleIdTokenCredential.idToken,
                        null
                    )

                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(this@SettingsActivity) { task ->
                            if (task.isSuccessful) { onSuccess(); recreate() }
                            else onError(
                                task.exception?.localizedMessage
                                    ?: "Firebase Google লগইন ব্যর্থ হয়েছে।"
                            )
                        }
                } else {
                    onError("Google লগইনের জন্য সঠিক credential পাওয়া যায়নি।")
                }
            } catch (e: GetCredentialException) {
                onError("Google অ্যাকাউন্ট সংযোগ বাতিল হয়েছে বা ব্যর্থ হয়েছে। আবার চেষ্টা করুন।")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Google অ্যাকাউন্ট সংযোগে একটি সমস্যা হয়েছে।")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    initialShopName: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onGoogleSignIn: ((() -> Unit), (String) -> Unit) -> Unit,
    googleEmail: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser = com.tamanna.enterprise.security.SecurityStorage.getCurrentUser(context)
    var shopName by remember { mutableStateOf(initialShopName) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text("সেটিংস", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                Text("দোকানের তথ্য", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("দোকানের নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                var loginEnabled by remember { mutableStateOf(com.tamanna.enterprise.security.SecurityStorage.isLoginEnabled(context)) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("লগইন নিরাপত্তা", style = MaterialTheme.typography.titleMedium)
                        Text("চালু করলে অ্যাপ খোলার সময় ইউজার লগইন লাগবে।", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = loginEnabled,
                        onCheckedChange = {
                            loginEnabled = it
                            com.tamanna.enterprise.security.SecurityStorage.setLoginEnabled(context, it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(20.dp))

                Text("Google অ্যাকাউন্ট", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                if (!googleEmail.isNullOrBlank()) {
                    Text("সংযুক্ত: $googleEmail", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Firebase ব্যাকআপ ও সিঙ্কের জন্য Google অ্যাকাউন্ট সংযুক্ত করুন।",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var googleLoading by remember { mutableStateOf(false) }
                    var googleError by remember { mutableStateOf("") }

                    Button(
                        onClick = {
                            googleError = ""
                            googleLoading = true
                            onGoogleSignIn(
                                {
                                    googleLoading = false
                                },
                                {
                                    googleLoading = false
                                    googleError = it
                                }
                            )
                        },
                        enabled = !googleLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (googleLoading) "Google অ্যাকাউন্ট সংযুক্ত হচ্ছে..." else "Google অ্যাকাউন্ট সংযুক্ত করুন")
                    }

                    if (googleError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(googleError, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (currentUser?.role == com.tamanna.enterprise.security.SecurityStorage.ROLE_ADMIN) {
                    Button(
                        onClick = { context.startActivity(android.content.Intent(context, com.tamanna.enterprise.security.UserManagementActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ইউজার ব্যবস্থাপনা")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("বাতিল")
                    }

                    Button(
                        onClick = { onSave(shopName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("সংরক্ষণ")
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    "সংরক্ষণ করলে দোকানের নাম ড্যাশবোর্ডে দেখাবে।",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }


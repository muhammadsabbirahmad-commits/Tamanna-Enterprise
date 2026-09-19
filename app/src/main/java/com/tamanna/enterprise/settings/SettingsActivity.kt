package com.tamanna.enterprise.settings

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.sync.CloudSyncManager

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
    private var googleOnSuccess: (() -> Unit)? = null
    private var googleOnError: ((String) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                com.tamanna.enterprise.notifications.NotificationScheduler.scheduleDaily(this)
            }
        }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                googleOnError?.invoke("Google অ্যাকাউন্ট নির্বাচন বাতিল হয়েছে। আবার চেষ্টা করুন।")
                return@registerForActivityResult
            }

            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)

                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    googleOnError?.invoke("Google ID Token পাওয়া যায়নি। Firebase/Google সেটআপ পরীক্ষা করতে হবে।")
                    return@registerForActivityResult
                }

                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            CloudSyncManager.pullThenSync(this@SettingsActivity) {
                                googleOnSuccess?.invoke()
                                googleOnSuccess = null
                                googleOnError = null
                                recreate()
                            }
                        } else {
                            googleOnError?.invoke(
                                task.exception?.localizedMessage ?: "Firebase Google সংযোগ ব্যর্থ হয়েছে।"
                            )
                        }
                    }
            } catch (e: ApiException) {
                googleOnError?.invoke("Google অ্যাকাউন্ট নির্বাচন ব্যর্থ হয়েছে। কোড: ${e.statusCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            SettingsScreen(
                initialShopName = SettingsStorage.getShopName(this),
                onSave = { name ->
                    SettingsStorage.saveShopName(this, name)
                    finish()
                },
                onCancel = { finish() },
                onGoogleSignIn = { onSuccess, onError ->
                    googleOnSuccess = onSuccess
                    googleOnError = onError
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(com.tamanna.enterprise.R.string.default_web_client_id))
                        .requestEmail()
                        .build()

                    val client = GoogleSignIn.getClient(this, gso)
                    googleSignInLauncher.launch(client.signInIntent)
                },
                googleEmail = auth.currentUser?.email,
                onEnableNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        com.tamanna.enterprise.notifications.NotificationScheduler.scheduleDaily(this)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    var loginEnabled by remember {
        mutableStateOf(com.tamanna.enterprise.security.SecurityStorage.isLoginEnabled(context))
    }
    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(ThemeStorage.getTheme(context)) }

    TamannaTheme(selectedTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp)
            ) {
                Text("সেটিংস", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))

                Text("দোকানের তথ্য", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("দোকানের নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("লগইন নিরাপত্তা", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "চালু করলে অ্যাপ খোলার সময় ইউজার লগইন লাগবে।",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = loginEnabled,
                        onCheckedChange = {
                            loginEnabled = it
                            com.tamanna.enterprise.security.SecurityStorage.setLoginEnabled(context, it)
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text("Google অ্যাকাউন্ট", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                if (!googleEmail.isNullOrBlank()) {
                    Text("সংযুক্ত: $googleEmail", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Firebase ব্যাকআপ ও সিঙ্কের জন্য Google অ্যাকাউন্ট সংযুক্ত করুন।",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            googleError = ""
                            googleLoading = true
                            onGoogleSignIn(
                                { googleLoading = false },
                                {
                                    googleLoading = false
                                    googleError = it
                                }
                            )
                        },
                        enabled = !googleLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (googleLoading) "Google অ্যাকাউন্ট সংযুক্ত হচ্ছে..."
                            else "Google অ্যাকাউন্ট সংযুক্ত করুন"
                        )
                    }

                    if (googleError.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(googleError, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                context,
                                com.tamanna.enterprise.sync.CloudBackupActivity::class.java
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Google ব্যাকআপ ও সিঙ্ক")
                }

                Spacer(Modifier.height(20.dp))

                Text("নোটিফিকেশন", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("প্রতিদিন রাত ৯টায় বিক্রয়, লাভ, কম স্টক ও ক্রেতার বাকি সম্পর্কে আপডেট পাবেন।", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("নোটিফিকেশন চালু করুন")
                }

                Spacer(Modifier.height(20.dp))

                Text("অ্যাপের থিম", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text("পছন্দের রঙ নির্বাচন করুন", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "green" to "সবুজ",
                        "blue" to "নীল",
                        "purple" to "বেগুনি",
                        "dark" to "ডার্ক"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedTheme == key,
                            onClick = {
                                selectedTheme = key
                                ThemeStorage.saveTheme(context, key)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (currentUser?.role == com.tamanna.enterprise.security.SecurityStorage.ROLE_ADMIN) {
                    Button(
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    context,
                                    com.tamanna.enterprise.security.UserManagementActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ইউজার ব্যবস্থাপনা")
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("বাতিল")
                    }
                    Button(onClick = { onSave(shopName) }, modifier = Modifier.weight(1f)) {
                        Text("সংরক্ষণ")
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "সংরক্ষণ করলে দোকানের নাম ড্যাশবোর্ডে দেখাবে।",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

package com.tamanna.enterprise.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme

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
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                com.tamanna.enterprise.notifications.NotificationScheduler.scheduleDaily(this)
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                initialShopName = SettingsStorage.getShopName(this),
                onSave = { name ->
                    SettingsStorage.saveShopName(this, name)
                    finish()
                },
                onCancel = { finish() },
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
    onEnableNotifications: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser = com.tamanna.enterprise.security.SecurityStorage.getCurrentUser(context)
    var shopName by remember { mutableStateOf(initialShopName) }
    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(ThemeStorage.getTheme(context)) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf("") }

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

                Text("লগইন নিরাপত্তা", style = MaterialTheme.typography.titleMedium)
                Text(
                    "অ্যাপের ব্যবসায়িক ডাটা দেখতে ও ব্যবহার করতে অনুমোদিত Admin/Partner লগইন বাধ্যতামূলক। এই নিরাপত্তা বন্ধ করার অপশন রাখা হয়নি।",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(24.dp))

                Text("অ্যাকাউন্ট ও লগইন", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "এখান থেকেই Admin বা Partner Login করবেন। Login Screen আর অ্যাপ চালুর সময় দেখানো হবে না।",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, com.tamanna.enterprise.security.LoginActivity::class.java)
                                .putExtra("LOGIN_MODE", "ADMIN")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👑 Admin Login")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, com.tamanna.enterprise.security.LoginActivity::class.java)
                                .putExtra("LOGIN_MODE", "PARTNER")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👤 Partner Login")
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
                    Text("অ্যাডমিন নিরাপত্তা", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit) }, label = { Text("বর্তমান Admin PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) }, label = { Text("নতুন Admin PIN (কমপক্ষে ৪ সংখ্যা)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit) }, label = { Text("নতুন PIN আবার দিন") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        pinMessage = when {
                            newPin.length < 4 -> "নতুন PIN কমপক্ষে ৪ সংখ্যার হতে হবে।"
                            newPin != confirmPin -> "নতুন PIN দুবার একই নয়।"
                            com.tamanna.enterprise.security.SecurityStorage.changeAdminPin(context, currentPin, newPin) -> {
                                currentPin = ""; newPin = ""; confirmPin = ""
                                "Admin PIN সফলভাবে পরিবর্তন হয়েছে।"
                            }
                            else -> "বর্তমান Admin PIN সঠিক নয়।"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Admin PIN পরিবর্তন করুন") }
                    if (pinMessage.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(pinMessage, color = if (pinMessage.contains("সফল")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            context.startActivity(android.content.Intent(context, com.tamanna.enterprise.security.ActivityLogActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("অ্যাক্টিভিটি লগ") }

                    Spacer(Modifier.height(8.dp))
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

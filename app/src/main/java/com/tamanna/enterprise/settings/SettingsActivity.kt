package com.tamanna.enterprise.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.security.SecurityStorage

object SettingsStorage {
    private const val PREFS = "tamanna_enterprise_settings"
    private const val KEY_SHOP_NAME = "shop_name"
    private const val DEFAULT_SHOP_NAME = "Tamanna Enterprise"

    fun getShopName(context: Context): String =
        BusinessStorage.prefs(context, PREFS)
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
    val currentUser = SecurityStorage.getCurrentUser(context)
    var shopName by remember { mutableStateOf(initialShopName) }
    var selectedTheme by remember { mutableStateOf(ThemeStorage.getTheme(context)) }
    
    // PIN states
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf("") }
    val isPinSet = remember(pinMessage) { SecurityStorage.isAdminPinSet(context) }

    TamannaTheme(selectedTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("সেটিংস", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))

                Text("দোকানের তথ্য", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                // নামের পাশে নতুন সেভ বাটন যুক্ত করা হলো
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("দোকানের নাম") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            SettingsStorage.saveShopName(context, shopName)
                            Toast.makeText(context, "নাম আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("সেভ করুন")
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("অ্যাকাউন্ট ও লগইন", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "এখান থেকেই Partner Login করবেন। Login Screen আর অ্যাপ চালুর সময় দেখানো হবে না।",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))

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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("green" to "সবুজ", "blue" to "নীল", "purple" to "বেগুনি", "dark" to "ডার্ক").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedTheme == key,
                            onClick = { selectedTheme = key; ThemeStorage.saveTheme(context, key) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("পার্টনার ও ব্যবহারকারী ব্যবস্থাপনা", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(context, com.tamanna.enterprise.security.UserManagementActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👥 Partner Management")
                }

                Spacer(Modifier.height(20.dp))

                if (currentUser?.role == SecurityStorage.ROLE_ADMIN) {
                    Text("ডিলিট নিরাপত্তা (ঐচ্ছিক)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "পণ্য বা পার্টনার ডিলিট করার জন্য একটি পিন সেট করতে পারেন। পিন না চাইলে ফাঁকা রেখে সেভ করুন।",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isPinSet) {
                        OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit) }, label = { Text("বর্তমান PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) }, label = { Text(if (isPinSet) "নতুন PIN (বন্ধ করতে ফাঁকা রাখুন)" else "নতুন PIN সেট করুন") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit) }, label = { Text("নতুন PIN আবার দিন") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    Button(onClick = {
                        pinMessage = when {
                            newPin.isNotBlank() && newPin.length < 4 -> "নতুন PIN কমপক্ষে ৪ সংখ্যার হতে হবে।"
                            newPin.isNotBlank() && newPin != confirmPin -> "নতুন PIN দুবার একই নয়।"
                            SecurityStorage.changeAdminPin(context, currentPin, newPin) -> {
                                val msg = if (newPin.isBlank()) "PIN সফলভাবে বন্ধ করা হয়েছে।" else "PIN সফলভাবে সেট হয়েছে।"
                                currentPin = ""; newPin = ""; confirmPin = ""
                                msg
                            }
                            else -> "বর্তমান PIN সঠিক নয়।"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (newPin.isBlank() && isPinSet) "PIN বন্ধ করুন" else "PIN সেভ করুন") }

                    if (pinMessage.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(pinMessage, color = if (pinMessage.contains("সফল")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { context.startActivity(Intent(context, com.tamanna.enterprise.security.ActivityLogActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("অ্যাক্টিভিটি লগ") }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                    Button(onClick = { onSave(shopName) }, modifier = Modifier.weight(1f)) { Text("সব সেভ করে বের হোন") }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

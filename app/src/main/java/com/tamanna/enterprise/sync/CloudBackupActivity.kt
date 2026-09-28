package com.tamanna.enterprise.sync

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.settings.ThemeStorage

@OptIn(ExperimentalMaterial3Api::class)
class CloudBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // অ্যাক্টিভিটির শুরুতেই সেফলি ইউজার বা ইমেইল ফেচ করে নেওয়া হচ্ছে
        val authUser = FirebaseAuth.getInstance().currentUser
        val localUser = SecurityStorage.getCurrentUser(this)
        val userEmail = authUser?.email ?: localUser?.email

        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                CloudBackupScreen(userEmail)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CloudBackupScreen(email: String?) {
        var message by remember { mutableStateOf("স্ট্যাটাস: রেডি") }
        var busy by remember { mutableStateOf(false) }
        var autoSyncEnabled by remember { mutableStateOf(CloudSyncManager.isAutoSyncEnabled()) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("ক্লাউড সিঙ্ক ও ব্যাকআপ") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("সংযুক্ত Google অ্যাকাউন্ট", style = MaterialTheme.typography.titleMedium)
                Text(email ?: "কোনো অ্যাকাউন্ট সংযুক্ত নেই।", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(30.dp))

                Button(
                    onClick = {
                        busy = true
                        message = "ডেটা মেলানো হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন..."
                        
                        CloudSyncManager.smartSync(this@CloudBackupActivity, isAuto = false) { resultMsg, isDataSynced ->
                            busy = false
                            message = resultMsg
                            
                            if (isDataSynced) {
                                recreate()
                            }
                        }
                    },
                    enabled = !busy && email != null,
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☁️ 🔄", fontSize = 48.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Sync Now")
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(30.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("অটো-সিঙ্ক (Auto Sync)", style = MaterialTheme.typography.titleMedium)
                            Text("স্বয়ংক্রিয়ভাবে ডেটা ক্লাউডে ইন/আউট হবে।", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { isChecked ->
                                autoSyncEnabled = isChecked
                                if (isChecked) {
                                    CloudSyncManager.startAutoSync(this@CloudBackupActivity)
                                    message = "অটো-সিঙ্ক চালু করা হয়েছে। ✅"
                                } else {
                                    CloudSyncManager.stopAutoSync()
                                    message = "অটো-সিঙ্ক বন্ধ করা হয়েছে। ❌"
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

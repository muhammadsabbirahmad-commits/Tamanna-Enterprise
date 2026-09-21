package com.tamanna.enterprise.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.settings.ThemeStorage

@OptIn(ExperimentalMaterial3Api::class)
class CloudBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                CloudBackupScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun CloudBackupScreen() {
        val email = FirebaseAuth.getInstance().currentUser?.email
        var message by androidx.compose.runtime.remember { mutableStateOf("") }
        var busy by androidx.compose.runtime.remember { mutableStateOf(false) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Google ব্যাকআপ ও সিঙ্ক") }) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("সংযুক্ত Google অ্যাকাউন্ট", style = MaterialTheme.typography.titleMedium)
                Text(email ?: "কোনো Google অ্যাকাউন্ট সংযুক্ত নেই।")

                Text(
                    "Google অ্যাকাউন্ট সংযুক্ত থাকলে পণ্য, বিক্রয়, ক্রয়, বাকি, পার্টনার, হিসাব, স্টক ও সেটিংসের ডেটা ক্লাউডে সিঙ্ক হবে।",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = {
                        busy = true
                        message = "ক্লাউডে ব্যাকআপ নেওয়া হচ্ছে..."
                        CloudSyncManager.manualBackup(this@CloudBackupActivity) {
                            busy = false
                            message = "ব্যাকআপ সফলভাবে সম্পন্ন হয়েছে।"
                        }
                    },
                    enabled = !busy && email != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("এখনই ব্যাকআপ নিন")
                }

                Button(
                    onClick = {
                        busy = true
                        message = "পুরোনো Cloud Data Migration যাচাই করা হচ্ছে..."
                        CloudSyncManager.migrateLegacyCloudData(this@CloudBackupActivity) { success, result ->
                            busy = false
                            message = result
                        }
                    },
                    enabled = !busy && email != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("পুরোনো Cloud Data Migration")
                }

                Button(
                    onClick = {
                        busy = true
                        message = "ক্লাউড থেকে ডেটা ফিরিয়ে আনা হচ্ছে..."
                        CloudSyncManager.pullThenSync(this@CloudBackupActivity) { found ->
                            busy = false
                            message = if (found) {
                                "ক্লাউড ডেটা সফলভাবে ফিরিয়ে আনা হয়েছে।"
                            } else {
                                "ক্লাউডে আগের ডেটা পাওয়া যায়নি বা সংযোগ ব্যর্থ হয়েছে।"
                            }
                        }
                    },
                    enabled = !busy && email != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ক্লাউড থেকে Restore করুন")
                }

                Text(
                    "স্বয়ংক্রিয় ব্যাকআপ/সিঙ্ক বন্ধ। লগইন করলেই কোনো পুরোনো ক্লাউড ডেটা আসবে না। আপনি নিজে ব্যাকআপ বা Restore চাপলেই কাজ হবে।",
                    style = MaterialTheme.typography.bodySmall
                )

                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

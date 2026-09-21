package com.tamanna.enterprise.business

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage

class BusinessAccessActivity : ComponentActivity() {
    private var setStatus: ((String) -> Unit)? = null

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by remember { mutableStateOf("Account ও Business Access যাচাই হচ্ছে...") }
            setStatus = { status = it }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Account & Business Access", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                status = "Admin Login খোলা হচ্ছে..."
                                loginLauncher.launch(
                                    Intent(this@BusinessAccessActivity, LoginActivity::class.java)
                                        .putExtra("LOGIN_MODE", "ADMIN")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("👑 Admin Login") }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                status = "Partner Login খোলা হচ্ছে..."
                                loginLauncher.launch(
                                    Intent(this@BusinessAccessActivity, LoginActivity::class.java)
                                        .putExtra("LOGIN_MODE", "PARTNER")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Partner Login") }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                FirebaseAuth.getInstance().signOut()
                                SecurityStorage.logout(this@BusinessAccessActivity)
                                status = "বর্তমান Account সংযোগ বিচ্ছিন্ন হয়েছে।"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Account সংযোগ বিচ্ছিন্ন করুন") }
                    }
                }
            }
        }
        checkAccess()
    }

    private fun checkAccess() {
        val license = LicenseStorage.get(this)
        if (!LicenseStorage.isActive(this)) {
            startActivity(Intent(this, LicenseActivationActivity::class.java))
            finish()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            setStatus?.invoke("একটি Admin বা Partner Account দিয়ে Login করুন।")
            return
        }

        val business = BusinessAccountStorage.get(this)
        if (business.businessId.isBlank() || business.businessId == BusinessAccountStorage.LEGACY_BUSINESS_ID) {
            setStatus?.invoke("এই License-এর Business Account প্রস্তুত নয়।")
            FirebaseAuth.getInstance().signOut()
            SecurityStorage.logout(this)
            return
        }

        if (license.businessId != business.businessId) {
            setStatus?.invoke("License এবং Business Account মিলছে না। পুনরায় License যাচাই করুন।")
            FirebaseAuth.getInstance().signOut()
            SecurityStorage.logout(this)
            return
        }

        setStatus?.invoke("License ও Business যাচাই হচ্ছে...")
        LicenseManager.verify(license.code) { ok, remoteLicense, message ->
            runOnUiThread {
                if (!ok || remoteLicense == null || remoteLicense.businessId != business.businessId) {
                    setStatus?.invoke("License যাচাই ব্যর্থ: ${message.ifBlank { "Business-এর সাথে মিল নেই।" }}")
                    FirebaseAuth.getInstance().signOut()
                    SecurityStorage.logout(this)
                    return@runOnUiThread
                }

                setStatus?.invoke("Business membership যাচাই হচ্ছে...")
                BusinessMembershipManager.currentMember(business.businessId) { member ->
                    runOnUiThread {
                        if (member != null && member.approved && !member.blocked) {
                            if (member.role == "OWNER" && (business.ownerUid.isBlank() || business.ownerUid != user.uid)) {
                                setStatus?.invoke("Business Owner Account-এর সাথে বর্তমান Account মিলছে না।")
                                FirebaseAuth.getInstance().signOut()
                                SecurityStorage.logout(this)
                                return@runOnUiThread
                            }

                            val email = member.email.ifBlank { user.email.orEmpty() }
                            val localRole = if (member.role == "OWNER") SecurityStorage.ROLE_ADMIN else member.role
                            val local = SecurityStorage.upsertGoogleUser(this, email, localRole, true, user.uid)
                            SecurityStorage.login(this, local)
                            openDashboard()
                        } else {
                            setStatus?.invoke(
                                if (member?.blocked == true)
                                    "এই Business-এ আপনার access Block করা হয়েছে।"
                                else
                                    "এই Business-এ আপনার Account এখনো অনুমোদিত নয়।"
                            )
                            FirebaseAuth.getInstance().signOut()
                            SecurityStorage.logout(this)
                        }
                    }
                }
            }
        }
    }
    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}

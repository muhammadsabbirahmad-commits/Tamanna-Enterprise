package com.tamanna.enterprise.security

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessMembershipManager
import com.tamanna.enterprise.dashboard.DashboardActivity

class LoginActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private var success: (() -> Unit)? = null
    private var failure: ((String) -> Unit)? = null

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) { failure?.invoke("Google ID Token পাওয়া যায়নি।"); clear(); return@registerForActivityResult }
            
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) { failure?.invoke(task.exception?.localizedMessage ?: "Firebase Google লগইন ব্যর্থ হয়েছে।"); clear(); return@addOnCompleteListener }
                
                val currentUser = auth.currentUser
                if (currentUser == null) { failure?.invoke("ইউজার পাওয়া যায়নি।"); clear(); return@addOnCompleteListener }

                val email = currentUser.email?.trim()?.lowercase() ?: ""
                val partnerKey = "partner_" + email.replace(".", "_").replace("@", "_")
                val db = FirebaseFirestore.getInstance()

                // --- গ্লোবাল ক্লাউড বাইপাস লজিক ---
                db.collection("preApprovedPartners").document(partnerKey).get(Source.SERVER)
                    .addOnCompleteListener { docTask ->
                        if (docTask.isSuccessful && docTask.result?.exists() == true) {
                            // Gmailটি Business App-এর অনুমোদিত Partner তালিকায় আছে। Business ID ও membership cloud থেকে নিশ্চিত করে তারপর সরাসরি প্রবেশ।
                            val businessId = docTask.result?.getString("businessId").orEmpty().trim()
                            if (businessId.isBlank()) {
                                auth.signOut()
                                failure?.invoke("এই Partner Gmail-এর Business ID সংরক্ষিত নেই। Owner-এর Partner তালিকা থেকে Gmailটি আবার সংরক্ষণ করুন।")
                                clear()
                                return@addOnCompleteListener
                            }
                            BusinessAccountStorage.setActiveBusinessId(this@LoginActivity, businessId)
                            BusinessMembershipManager.ensurePreApprovedPartnerMembership(businessId, email) { membershipOk, membershipMessage ->
                                if (!membershipOk) {
                                    auth.signOut()
                                    failure?.invoke(membershipMessage)
                                } else {
                                    SecurityStorage.upsertGoogleUser(this@LoginActivity, email, SecurityStorage.ROLE_PARTNER, true, currentUser.uid)
                                    SecurityStorage.findByGoogleEmail(this@LoginActivity, email)?.let { SecurityStorage.login(this@LoginActivity, it) }
                                    success?.invoke()
                                }
                                clear()
                            }
                        } else {
                            // পার্টনার না হলে রেগুলার অ্যাডমিন চেকিং হবে
                            AccessRequestManager.requestOrCheck { ok, message, record ->
                                if (ok && record != null) {
                                    SecurityStorage.upsertGoogleUser(this@LoginActivity, record.email, SecurityStorage.ROLE_ADMIN, true, record.uid)
                                    SecurityStorage.findByGoogleEmail(this@LoginActivity, record.email)?.let { SecurityStorage.login(this@LoginActivity, it) }
                                    success?.invoke()
                                } else { auth.signOut(); failure?.invoke(message) }
                                clear()
                            }
                        }
                    }
            }
        } catch (e: ApiException) { failure?.invoke("Google লগইন ব্যর্থ হয়েছে। Status code: ${e.statusCode}"); clear() }
        catch (e: Exception) { failure?.invoke(e.localizedMessage ?: "Google লগইনে সমস্যা হয়েছে।"); clear() }
    }

    private fun clear() { success = null; failure = null }

    private fun startLogin() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.tamanna.enterprise.R.string.default_web_client_id))
            .requestEmail().build()
        launcher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        auth = FirebaseAuth.getInstance()
        setContent {
            var message by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Gmail Login", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Gmail দিয়ে Login করুন। Business App-এ Partner Gmail আগে থেকে সংরক্ষিত থাকলে সরাসরি View-Only প্রবেশ হবে; না থাকলে Admin-এর কাছে অনুমোদনের অনুরোধ যাবে।")
                    Spacer(Modifier.height(12.dp))
                    if (message.isNotBlank()) { Text(message, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)) }
                    Button(onClick = {
                        message = ""; loading = true
                        success = { 
                            loading = false
                            setResult(RESULT_OK)
                            val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish() 
                        }
                        failure = { loading = false; message = it }
                        startLogin()
                    }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Gmail যাচাই হচ্ছে..." else "Gmail দিয়ে Login") }
                }
            } }
        }
    }
}

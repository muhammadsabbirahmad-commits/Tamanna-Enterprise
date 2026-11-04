package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        
        // যদি লগইন করা থাকে তবে সরাসরি ড্যাশবোর্ডে যাবে, নাহলে লগইন স্ক্রিনে
        val localUser = SecurityStorage.getCurrentUser(this)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        // Never open one Gmail's locally cached business data under a different Firebase UID.
        val matchingSession = localUser != null && firebaseUser != null && localUser.id == firebaseUser.uid
        if (matchingSession) {
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            // Do not clear the saved app session while Firebase restores its persisted Gmail session.
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}

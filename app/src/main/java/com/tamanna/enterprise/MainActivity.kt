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
        val localSession = SecurityStorage.isLoggedIn(this)
        val firebaseSession = FirebaseAuth.getInstance().currentUser != null
        if (localSession && firebaseSession) {
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            if (localSession && !firebaseSession) SecurityStorage.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}

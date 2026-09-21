package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.business.LicenseActivationActivity
import com.tamanna.enterprise.business.LicenseStorage
import com.tamanna.enterprise.security.CloudAccessManager
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.notifications.NotificationScheduler

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurityStorage.ensureInitialized(this)
        NotificationScheduler.scheduleDaily(this)

        if (!LicenseStorage.isActive(this)) {
            startActivity(Intent(this, LicenseActivationActivity::class.java))
            finish()
            return
        }

        val partnerSession = com.tamanna.enterprise.partner.PartnerStorage.getCurrentPartner(this)
        if (partnerSession != null) {
            startActivity(Intent(this, com.tamanna.enterprise.partner.PartnerLedgerActivity::class.java))
            finish()
            return
        }

        // Login choices live in Settings. The dashboard opens normally,
        // but business data/actions are locked until an approved Admin or Partner
        // session exists.
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()

    }
}

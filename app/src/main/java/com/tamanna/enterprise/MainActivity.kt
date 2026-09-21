package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tamanna.enterprise.business.BusinessAccessActivity
import com.tamanna.enterprise.business.LicenseActivationActivity
import com.tamanna.enterprise.business.LicenseStorage
import com.tamanna.enterprise.notifications.NotificationScheduler
import com.tamanna.enterprise.security.SecurityStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)
        NotificationScheduler.scheduleDaily(this)

        if (!LicenseStorage.isActive(this)) {
            startActivity(Intent(this, LicenseActivationActivity::class.java))
        } else {
            startActivity(Intent(this, BusinessAccessActivity::class.java))
        }
        finish()
    }
}

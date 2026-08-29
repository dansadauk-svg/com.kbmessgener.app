package ng.kbmovies.customercare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ng.kbmovies.customercare.data.CareRepository

class CareApp : Application() {
    val repository by lazy { CareRepository(this) }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("kbcc_messages", "Customer messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New KB Movies customer-care messages"
                    enableVibration(true)
                }
            )
        }
    }
}

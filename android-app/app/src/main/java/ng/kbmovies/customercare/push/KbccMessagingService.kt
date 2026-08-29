package ng.kbmovies.customercare.push

import android.app.*
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import ng.kbmovies.customercare.CareApp
import ng.kbmovies.customercare.MainActivity

class KbccMessagingService:FirebaseMessagingService(){
    override fun onNewToken(token:String){super.onNewToken(token);CoroutineScope(Dispatchers.IO).launch{runCatching{(application as CareApp).repository.registerDevice()}}}
    override fun onMessageReceived(message:RemoteMessage){super.onMessageReceived(message);val channel="kbcc_messages";val manager=getSystemService(NotificationManager::class.java);if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(channel,"Customer messages",NotificationManager.IMPORTANCE_HIGH));val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val title=message.data["title"]?:message.notification?.title?:"New customer message";val body=message.data["body"]?:message.notification?.body?:"Open the app to reply";manager.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),NotificationCompat.Builder(this,channel).setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle(title).setContentText(body).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(intent).build())}
}

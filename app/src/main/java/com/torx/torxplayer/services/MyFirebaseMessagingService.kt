package com.torx.torxplayer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.torx.torxplayer.R
import androidx.core.net.toUri

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e("token", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Only show notification if internet is connected
        if (isInternetAvailable()) {
            val title = remoteMessage.notification?.title ?: "No title"
            val message = remoteMessage.notification?.body ?: "No message"
            val imageUrl = remoteMessage.notification?.imageUrl?.toString()
            val link = remoteMessage.data["link"] // Firebase console custom data field

            showNotification(title, message, imageUrl, link)
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNotification(title: String, message: String, imageUrl: String?, link: String?) {
        val channelId = "firebase_notifications"

        val intent = Intent(Intent.ACTION_VIEW, (link ?: "https://www.google.com").toUri())
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Load image if provided
        imageUrl?.let {
            try {
                val url = java.net.URL(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Firebase Notifications", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

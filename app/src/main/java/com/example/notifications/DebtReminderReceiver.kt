package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class DebtReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val debtId = intent.getStringExtra(EXTRA_DEBT_ID) ?: return
    val personName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: "شخص"
    val amountText = intent.getStringExtra(EXTRA_AMOUNT_TEXT) ?: ""
    val directionLabel = intent.getStringExtra(EXTRA_DIRECTION_LABEL) ?: "مستحق"
    val verificationCode = intent.getStringExtra(EXTRA_VERIFICATION_CODE) ?: ""

    showDueNotification(
      context = context,
      debtId = debtId,
      personName = personName,
      amountText = amountText,
      directionLabel = directionLabel,
      verificationCode = verificationCode,
    )
  }

  companion object {
    const val CHANNEL_ID = "duyun_due_reminders"
    const val EXTRA_DEBT_ID = "extra_debt_id"
    const val EXTRA_PERSON_NAME = "extra_person_name"
    const val EXTRA_AMOUNT_TEXT = "extra_amount_text"
    const val EXTRA_DIRECTION_LABEL = "extra_direction_label"
    const val EXTRA_VERIFICATION_CODE = "extra_verification_code"

    fun ensureChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_desc)
        val channel =
          NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = descriptionText
          }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
      }
    }

    fun showDueNotification(
      context: Context,
      debtId: String,
      personName: String,
      amountText: String,
      directionLabel: String,
      verificationCode: String,
    ) {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
      ) {
        return
      }
      ensureChannel(context)

      val openAppIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra(EXTRA_DEBT_ID, debtId)
        }
      val contentPendingIntent =
        PendingIntent.getActivity(
          context,
          debtId.hashCode(),
          openAppIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val title = "تذكير بموعد سداد: $personName"
      val body = "$directionLabel بمبلغ $amountText (مرجع: $verificationCode)"

      val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle(title)
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true)
          .setContentIntent(contentPendingIntent)
          .build()

      NotificationManagerCompat.from(context).notify(debtId.hashCode(), notification)
    }
  }
}

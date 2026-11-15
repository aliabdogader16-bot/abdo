package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.Debt
import com.example.util.formatAmount
import java.util.Calendar

object DebtReminderScheduler {

  fun syncReminders(context: Context, debts: List<Debt>) {
    DebtReminderReceiver.ensureChannel(context)
    debts.forEach { debt ->
      if (debt.reminderEnabled && !debt.isSettled && debt.dueDateMillis > 0L) {
        scheduleReminder(context, debt)
      } else {
        cancelReminder(context, debt.id)
      }
    }
  }

  fun scheduleReminder(context: Context, debt: Debt) {
    if (debt.id.isBlank() || debt.dueDateMillis <= 0L || !debt.reminderEnabled || debt.isSettled) {
      return
    }
    DebtReminderReceiver.ensureChannel(context)

    val cal =
      Calendar.getInstance().apply {
        timeInMillis = debt.dueDateMillis
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    val triggerAtMillis = cal.timeInMillis
    val now = System.currentTimeMillis()
    // Only schedule future alarms; overdue items are surfaced prominently in the Reminders tab
    if (triggerAtMillis <= now) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = buildPendingIntent(context, debt)
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
  }

  fun cancelReminder(context: Context, debtId: String) {
    if (debtId.isBlank()) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, DebtReminderReceiver::class.java)
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        debtId.hashCode(),
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      )
    if (pendingIntent != null) {
      alarmManager.cancel(pendingIntent)
      pendingIntent.cancel()
    }
  }

  fun sendImmediateReminderNotification(context: Context, debt: Debt) {
    DebtReminderReceiver.showDueNotification(
      context = context,
      debtId = debt.id,
      personName = debt.personName,
      amountText = formatAmount(debt.remainingAmount, debt.currency),
      directionLabel = debt.directionEnum.labelAr,
      verificationCode = debt.verificationCode,
    )
  }

  private fun buildPendingIntent(context: Context, debt: Debt): PendingIntent {
    val intent =
      Intent(context, DebtReminderReceiver::class.java).apply {
        putExtra(DebtReminderReceiver.EXTRA_DEBT_ID, debt.id)
        putExtra(DebtReminderReceiver.EXTRA_PERSON_NAME, debt.personName)
        putExtra(
          DebtReminderReceiver.EXTRA_AMOUNT_TEXT,
          formatAmount(debt.remainingAmount, debt.currency),
        )
        putExtra(DebtReminderReceiver.EXTRA_DIRECTION_LABEL, debt.directionEnum.labelAr)
        putExtra(DebtReminderReceiver.EXTRA_VERIFICATION_CODE, debt.verificationCode)
      }
    return PendingIntent.getBroadcast(
      context,
      debt.id.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

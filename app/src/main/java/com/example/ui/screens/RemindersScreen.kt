package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.Debt
import com.example.notifications.DebtReminderScheduler
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.OverdueAmber
import com.example.ui.theme.OverdueAmberContainer
import com.example.ui.theme.PayableCrimson
import com.example.ui.theme.PayableCrimsonContainer
import com.example.ui.viewmodel.UiState
import com.example.util.DueUrgency
import com.example.util.buildReminderShareText
import com.example.util.computeDueStatus
import com.example.util.formatAmount
import com.example.util.formatDateAr

@Composable
fun RemindersScreen(
  allDebtsState: UiState<List<Debt>>,
  onDebtClick: (Debt) -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasNotificationPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
      onShowMessage(
        if (granted) "تم تفعيل أذونات تنبيهات السداد بنجاح"
        else "يمكنك تفعيل الإشعارات لاحقاً من إعدادات التطبيق"
      )
    }

  val activeWithDates =
    ((allDebtsState as? UiState.Success)?.data.orEmpty())
      .filter { !it.isSettled && it.dueDateMillis > 0L }
      .sortedBy { it.dueDateMillis }

  val overdue = activeWithDates.filter { computeDueStatus(it).urgency == DueUrgency.OVERDUE }
  val dueSoon = activeWithDates.filter { computeDueStatus(it).urgency == DueUrgency.DUE_SOON }
  val upcoming = activeWithDates.filter { computeDueStatus(it).urgency == DueUrgency.UPCOMING }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
      item {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Filled.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text = "تفعيل إشعارات مواعيد السداد",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Text(
              text = "اسمح بالإشعارات ليصلك تنبيه تلقائي في موعد استحقاق كل دين أو التزام مالي.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              modifier = Modifier.testTag("grant_notifications_button"),
            ) {
              Text("السماح بالإشعارات")
            }
          }
        }
      }
    }

    if (activeWithDates.isEmpty()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 1.dp,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.size(60.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(30.dp),
              )
            }
            Text("لا توجد مواعيد سداد مجدولة", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "عند إضافة دين جديد وتحديد موعد سداد له، سيظهر هنا مع تنبيهاته التلقائية.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      if (overdue.isNotEmpty()) {
        item {
          SectionTitle("متأخرة عن السداد (${overdue.size})", PayableCrimson)
        }
        items(overdue, key = { "overdue_${it.id}" }) { debt ->
          ReminderCard(
            debt = debt,
            hasPermission = hasNotificationPermission,
            onRequestPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenDetail = { onDebtClick(debt) },
            onShowMessage = onShowMessage,
          )
        }
      }

      if (dueSoon.isNotEmpty()) {
        item {
          SectionTitle("تستحق اليوم أو قريباً (${dueSoon.size})", OverdueAmber)
        }
        items(dueSoon, key = { "soon_${it.id}" }) { debt ->
          ReminderCard(
            debt = debt,
            hasPermission = hasNotificationPermission,
            onRequestPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenDetail = { onDebtClick(debt) },
            onShowMessage = onShowMessage,
          )
        }
      }

      if (upcoming.isNotEmpty()) {
        item {
          SectionTitle("مواعيد قادمة (${upcoming.size})", EmeraldPrimary)
        }
        items(upcoming, key = { "upcoming_${it.id}" }) { debt ->
          ReminderCard(
            debt = debt,
            hasPermission = hasNotificationPermission,
            onRequestPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenDetail = { onDebtClick(debt) },
            onShowMessage = onShowMessage,
          )
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(title: String, color: androidx.compose.ui.graphics.Color) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = color,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun ReminderCard(
  debt: Debt,
  hasPermission: Boolean,
  onRequestPermission: () -> Unit,
  onOpenDetail: () -> Unit,
  onShowMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val dueInfo = computeDueStatus(debt)
  val (bg, fg, icon) =
    when (dueInfo.urgency) {
      DueUrgency.OVERDUE ->
        Triple(PayableCrimsonContainer, PayableCrimson, Icons.Filled.WarningAmber)
      DueUrgency.DUE_SOON ->
        Triple(OverdueAmberContainer, OverdueAmber, Icons.Filled.Schedule)
      else -> Triple(EmeraldContainer, EmeraldPrimary, Icons.Filled.NotificationsActive)
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(debt.personName, style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${debt.directionEnum.labelAr} • ${formatAmount(debt.remainingAmount, debt.currency)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(color = bg, shape = RoundedCornerShape(50)) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(dueInfo.label, style = MaterialTheme.typography.labelSmall, color = fg)
          }
        }
      }

      Text(
        text =
          "تاريخ الاستحقاق: ${formatDateAr(debt.dueDateMillis)}  •  التنبيه: ${if (debt.reminderEnabled) "مفعّل" else "متوقف"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = {
            if (!hasPermission) {
              onRequestPermission()
            } else {
              DebtReminderScheduler.sendImmediateReminderNotification(context, debt)
              onShowMessage("تم إرسال إشعار التذكير إلى شريط التنبيهات")
            }
          },
          modifier = Modifier.weight(1f).testTag("trigger_notification_${debt.id}"),
        ) {
          Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("إرسال إشعار")
        }

        OutlinedButton(
          onClick = {
            val intent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildReminderShareText(debt))
              }
            runCatching {
              context.startActivity(Intent.createChooser(intent, "إرسال تذكير"))
            }.onFailure {
              onShowMessage("لا يوجد تطبيق متاح لمشاركة التذكير")
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("تذكير الشخص")
        }
      }
    }
  }
}

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.Debt
import com.example.ui.theme.ReceivableGreen
import com.example.ui.viewmodel.UiState

@Composable
fun AccountScreen(
  displayName: String,
  email: String,
  photoUrl: String?,
  allDebtsState: UiState<List<Debt>>,
  onSignOut: () -> Unit,
  onOpenStatistics: () -> Unit = {},
  onOpenReminders: () -> Unit = {},
  onShowMessage: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showSignOutDialog by remember { mutableStateOf(false) }
  var showProfileDialog by remember { mutableStateOf(false) }
  var showSecurityDialog by remember { mutableStateOf(false) }

  val debts = (allDebtsState as? UiState.Success)?.data.orEmpty()
  val activeCount = debts.count { !it.isSettled }
  val settledCount = debts.count { it.isSettled }

  if (showSignOutDialog) {
    AlertDialog(
      onDismissRequest = { showSignOutDialog = false },
      title = { Text("تسجيل الخروج") },
      text = { Text("ستبقى جميع سجلاتك وعملياتك الموثقة محفوظة بأمان في حسابك السحابي.") },
      confirmButton = {
        TextButton(
          onClick = {
            showSignOutDialog = false
            onSignOut()
          }
        ) {
          Text("خروج", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSignOutDialog = false }) { Text("بقاء") }
      },
    )
  }

  if (showProfileDialog) {
    AlertDialog(
      onDismissRequest = { showProfileDialog = false },
      title = { Text("بطاقة الحساب") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("الاسم: ${displayName.ifBlank { "مستخدم ديون" }}")
          if (email.isNotBlank()) Text("البريد: $email")
          Text("السجلات النشطة: $activeCount  •  المسدّدة: $settledCount")
        }
      },
      confirmButton = {
        TextButton(onClick = { showProfileDialog = false }) { Text("إغلاق") }
      },
    )
  }

  if (showSecurityDialog) {
    AlertDialog(
      onDismissRequest = { showSecurityDialog = false },
      title = { Text("ضمانات التوثيق والأمان") },
      text = {
        Text(
          "• سجل عمليات غير قابل للتعديل برمز مرجعي وتاريخ موثق من الخادم.\n" +
            "• تصدير كشوفات حساب رسمية بصيغة PDF ومشاركتها.\n" +
            "• عزل وخصوصية كاملة مرتبطة بحساب Google الشخصي."
        )
      },
      confirmButton = {
        TextButton(onClick = { showSecurityDialog = false }) { Text("تم") }
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "المزيد",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .clickable {
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "تطبيق ديون — لمتابعة الديون والمستحقات المالية بدقة وتوثيق موثوق.",
                    )
                  }
                runCatching {
                  context.startActivity(Intent.createChooser(shareIntent, "مشاركة تطبيق ديون"))
                }.onFailure {
                  onShowMessage("لا يوجد تطبيق متاح للمشاركة على هذا الجهاز")
                }
              }
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .testTag("share_app_button"),
        ) {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "شارك التطبيق",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "مع الأصدقاء",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(38.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "شارك التطبيق",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }

    item {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { showProfileDialog = true }
              .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
              model = photoUrl,
              contentDescription = "صورة الحساب",
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(48.dp).clip(CircleShape),
            )
          } else {
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = displayName.firstOrNull()?.toString() ?: "د",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = displayName.ifBlank { "مستخدم ديون" },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                if (email.isNotBlank()) email
                else "$activeCount سجل نشط • $settledCount مسدّد",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text = "$activeCount نشط",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    item {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          MoreMenuRow(
            icon = Icons.Outlined.Badge,
            title = "بطاقة الحساب والسجلات",
            subtitle = "$activeCount نشط  •  $settledCount مسدّد",
            onClick = { showProfileDialog = true },
          )
          MenuRowDivider()
          MoreMenuRow(
            icon = Icons.Outlined.BarChart,
            title = "التقارير والإحصائيات",
            onClick = onOpenStatistics,
          )
          MenuRowDivider()
          MoreMenuRow(
            icon = Icons.Outlined.NotificationsActive,
            title = "تنبيهات مواعيد السداد",
            onClick = onOpenReminders,
          )
          MenuRowDivider()
          MoreMenuRow(
            icon = Icons.Outlined.CloudDone,
            title = "نسخ تلقائي سحابي",
            trailingBadgeIcon = Icons.Filled.CheckCircle,
            trailingBadgeTint = ReceivableGreen,
            onClick = { onShowMessage("المزامنة السحابية مفعّلة وتعمل تلقائياً") },
          )
          MenuRowDivider()
          MoreMenuRow(
            icon = Icons.Outlined.VerifiedUser,
            title = "ضمانات التوثيق والأمان",
            onClick = { showSecurityDialog = true },
          )
          MenuRowDivider()
          MoreMenuRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = "تسجيل الخروج",
            iconTint = MaterialTheme.colorScheme.error,
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { showSignOutDialog = true },
            modifier = Modifier.testTag("sign_out_button"),
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(12.dp))
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("version_info_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "حول تطبيق ديون",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "الإصدار ${BuildConfig.VERSION_NAME}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "توثيق مالي آمن ومتزامن عبر حساب Google",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun MenuRowDivider() {
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    modifier = Modifier.padding(horizontal = 16.dp),
  )
}

@Composable
private fun MoreMenuRow(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  iconTint: Color = MaterialTheme.colorScheme.primary,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
  trailingBadgeIcon: ImageVector? = null,
  trailingBadgeTint: Color = ReceivableGreen,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      color =
        if (iconTint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.size(38.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp),
        )
      }
    }

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = titleColor,
          fontWeight = FontWeight.SemiBold,
        )
        if (trailingBadgeIcon != null) {
          Icon(
            imageVector = trailingBadgeIcon,
            contentDescription = null,
            tint = trailingBadgeTint,
            modifier = Modifier.size(16.dp),
          )
        }
      }
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(22.dp),
    )
  }
}

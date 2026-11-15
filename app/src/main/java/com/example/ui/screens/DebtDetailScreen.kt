package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.data.model.DebtTransaction
import com.example.data.model.TransactionType
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PayableCrimson
import com.example.ui.theme.PayableCrimsonContainer
import com.example.ui.theme.ReceivableGreen
import com.example.ui.theme.ReceivableGreenContainer
import com.example.ui.viewmodel.UiState
import com.example.util.ReceiptPdfExporter
import com.example.util.buildDocumentedStatementText
import com.example.util.buildReminderShareText
import com.example.util.computeDueStatus
import com.example.util.formatAmount
import com.example.util.formatDateAr
import com.example.util.formatDateTimeAr

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtDetailScreen(
  debtState: UiState<Debt?>,
  transactionsState: UiState<List<DebtTransaction>>,
  ownerName: String,
  onBack: () -> Unit,
  onRecordPaymentClick: (Debt) -> Unit,
  onAddAmountClick: (Debt) -> Unit,
  onEditClick: (Debt) -> Unit,
  onDeleteConfirm: (Debt) -> Unit,
  onShowMessage: (String) -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  var showDeleteDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("تفاصيل وتوثيق السجل") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    }
  ) { innerPadding ->
    when (debtState) {
      is UiState.Loading -> {
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(debtState.message, color = MaterialTheme.colorScheme.error)
        }
      }
      is UiState.Success -> {
        val debt = debtState.data
        if (debt == null) {
          Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
          ) {
            Text("السجل غير موجود أو تم حذفه")
          }
        } else {
          val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()
          val isReceivable = debt.directionEnum == DebtDirection.RECEIVABLE
          val accentColor = if (isReceivable) ReceivableGreen else PayableCrimson
          val accentContainer = if (isReceivable) ReceivableGreenContainer else PayableCrimsonContainer
          val dueInfo = computeDueStatus(debt)

          if (showDeleteDialog) {
            AlertDialog(
              onDismissRequest = { showDeleteDialog = false },
              title = { Text("حذف السجل المالي؟") },
              text = {
                Text("سيتم حذف سجل ${debt.personName} وجميع العمليات الموثقة المرتبطة به نهائياً.")
              },
              confirmButton = {
                TextButton(
                  onClick = {
                    showDeleteDialog = false
                    onDeleteConfirm(debt)
                  }
                ) {
                  Text("حذف نهائي", color = MaterialTheme.colorScheme.error)
                }
              },
              dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") }
              },
            )
          }

          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            item {
              Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(color = accentContainer, shape = RoundedCornerShape(50)) {
                      Text(
                        text = debt.directionEnum.labelAr,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                      )
                    }
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(50),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Icon(
                          Icons.Filled.Verified,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.size(14.dp),
                        )
                        Text(
                          text = debt.verificationCode,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      }
                    }
                  }

                  Text(
                    text = debt.personName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                  )

                  if (debt.personPhone.isNotBlank()) {
                    Text(
                      text = "الجوال: ${debt.personPhone}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  Text(
                    text =
                      if (debt.isSettled) "تم السداد بالكامل"
                      else "المتبقي: ${formatAmount(debt.remainingAmount, debt.currency)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (debt.isSettled) EmeraldPrimary else accentColor,
                    fontWeight = FontWeight.Bold,
                  )

                  LinearProgressIndicator(
                    progress = { debt.progressFraction },
                    color = if (debt.isSettled) EmeraldPrimary else accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "الأصلي: ${formatAmount(debt.originalAmount, debt.currency)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "المدفوع: ${formatAmount(debt.paidAmount, debt.currency)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = EmeraldPrimary,
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text =
                        if (debt.dueDateMillis > 0L)
                          "موعد السداد: ${formatDateAr(debt.dueDateMillis)} (${dueInfo.label})"
                        else "موعد السداد: غير محدد",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  if (debt.notes.isNotBlank()) {
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                      shape = MaterialTheme.shapes.small,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = debt.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                      )
                    }
                  }
                }
              }
            }

            item {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                if (!debt.isSettled) {
                  Button(
                    onClick = { onRecordPaymentClick(debt) },
                    modifier = Modifier.testTag("record_payment_button"),
                  ) {
                    Icon(Icons.Filled.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تسجيل دفعة")
                  }
                  FilledTonalButton(
                    onClick = { onAddAmountClick(debt) },
                    modifier = Modifier.testTag("add_amount_button"),
                  ) {
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة مبلغ")
                  }
                  OutlinedButton(
                    onClick = { onEditClick(debt) },
                    modifier = Modifier.testTag("edit_debt_button"),
                  ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تعديل الموعد")
                  }
                }

                FilledTonalButton(
                  onClick = {
                    ReceiptPdfExporter.shareStatementPdf(context, debt, transactions, ownerName)
                      .onFailure {
                        onShowMessage("تعذر إنشاء ملف PDF، يمكنك استخدام المشاركة النصية")
                      }
                  },
                  modifier = Modifier.testTag("export_pdf_button"),
                ) {
                  Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("كشف PDF موثق")
                }

                OutlinedButton(
                  onClick = {
                    val shareText = buildDocumentedStatementText(debt, transactions)
                    val intent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "توثيق مالي - ${debt.personName}")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                      }
                    runCatching {
                      context.startActivity(Intent.createChooser(intent, "مشاركة التوثيق المالي"))
                    }.onFailure {
                      onShowMessage("لا يوجد تطبيق متاح لمشاركة النص على هذا الجهاز")
                    }
                  },
                  modifier = Modifier.testTag("share_text_button"),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("مشاركة نصية")
                }

                if (debt.personPhone.isNotBlank()) {
                  OutlinedButton(
                    onClick = {
                      val dialIntent =
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${debt.personPhone.trim()}"))
                      runCatching {
                        context.startActivity(dialIntent)
                      }.onFailure {
                        onShowMessage("لا يتوفر تطبيق اتصال هاتفي على هذا الجهاز")
                      }
                    },
                    modifier = Modifier.testTag("call_person_button"),
                  ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اتصال")
                  }
                }

                if (!debt.isSettled) {
                  OutlinedButton(
                    onClick = {
                      val reminderText = buildReminderShareText(debt)
                      val intent =
                        Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(Intent.EXTRA_TEXT, reminderText)
                        }
                      runCatching {
                        context.startActivity(Intent.createChooser(intent, "إرسال تذكير بالسداد"))
                      }.onFailure {
                        onShowMessage("لا يوجد تطبيق متاح لإرسال التذكير")
                      }
                    },
                    modifier = Modifier.testTag("send_reminder_button"),
                  ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تذكير الطرف الآخر")
                  }
                }

                OutlinedButton(
                  onClick = { showDeleteDialog = true },
                  colors =
                    ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error
                    ),
                  modifier = Modifier.testTag("delete_debt_button"),
                ) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("حذف")
                }
              }
            }

            item {
              Text(
                text = "سجل العمليات الموثقة (${transactions.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp),
              )
            }

            if (transactionsState is UiState.Loading) {
              item { CircularProgressIndicator() }
            } else if (transactions.isEmpty()) {
              item {
                Text(
                  text = "لا توجد عمليات مسجلة بعد.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              items(transactions, key = { it.id }) { tx ->
                TransactionTimelineItem(tx = tx, currency = debt.currency)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TransactionTimelineItem(tx: DebtTransaction, currency: String) {
  val (icon, tint, bg) =
    when (tx.typeEnum) {
      TransactionType.INITIAL ->
        Triple(Icons.Filled.Verified, MaterialTheme.colorScheme.primary, EmeraldContainer)
      TransactionType.PAYMENT ->
        Triple(Icons.Filled.CheckCircle, ReceivableGreen, ReceivableGreenContainer)
      TransactionType.ADDITION ->
        Triple(Icons.Filled.AddCircleOutline, PayableCrimson, PayableCrimsonContainer)
    }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth().testTag("tx_item_${tx.id}"),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = tx.typeEnum.labelAr,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = formatAmount(tx.amount, currency),
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = "الرصيد بعد العملية: ${formatAmount(tx.balanceAfter, currency)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (tx.note.isNotBlank()) {
          Text(
            text = tx.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = formatDateTimeAr(tx.performedAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = tx.referenceCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

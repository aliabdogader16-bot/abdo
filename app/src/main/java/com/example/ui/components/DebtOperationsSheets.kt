package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Debt
import com.example.util.MAX_DEBT_AMOUNT
import com.example.util.formatAmount
import com.example.util.formatEditableAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentSheet(
  debt: Debt,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onConfirmPayment: (amount: Double, note: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var amountText by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text("تسجيل دفعة سداد", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "الرصيد المتبقي: ${formatAmount(debt.remainingAmount, debt.currency)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Filled.Close, contentDescription = "إغلاق")
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
          onClick = {
            amountText = formatEditableAmount(debt.remainingAmount)
            error = null
          },
          label = { Text("سداد المتبقي بالكامل") },
          modifier = Modifier.testTag("full_settlement_chip"),
        )
        if (debt.remainingAmount >= 2.0) {
          val half = ((debt.remainingAmount / 2.0) * 100.0).toLong() / 100.0
          AssistChip(
            onClick = {
              amountText = formatEditableAmount(half)
              error = null
            },
            label = { Text("نصف المبلغ") },
          )
        }
      }

      OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
          if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            amountText = input
            error = null
          }
        },
        label = { Text("قيمة الدفعة (${debt.currency})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
      )

      OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("بيان الدفعة (مثال: تحويل بنكي، نقداً)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("payment_note_input"),
      )

      if (error != null) {
        Text(
          text = error ?: "",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Button(
        onClick = {
          val parsed = amountText.toDoubleOrNull()
          when {
            parsed == null || parsed <= 0.0 -> error = "يرجى إدخال مبلغ صحيح أكبر من صفر"
            parsed > debt.remainingAmount + 0.001 ->
              error = "لا يمكن أن تتجاوز الدفعة الرصيد المتبقي"
            else -> onConfirmPayment(parsed, note.trim())
          }
        },
        enabled = !isSaving,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_payment_button"),
      ) {
        Text(
          text = if (isSaving) "جاري التوثيق..." else "تأكيد وتوثيق الدفعة",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAmountSheet(
  debt: Debt,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onConfirmAddition: (amount: Double, note: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var amountText by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text("إضافة مبلغ جديد للسجل", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "سيُضاف إلى الرصيد الحالي مع توثيق العملية",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Filled.Close, contentDescription = "إغلاق")
        }
      }

      OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
          if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            amountText = input
            error = null
          }
        },
        label = { Text("المبلغ الإضافي (${debt.currency})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("addition_amount_input"),
      )

      OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("سبب الإضافة (اختياري)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      if (error != null) {
        Text(
          text = error ?: "",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Button(
        onClick = {
          val parsed = amountText.toDoubleOrNull()
          when {
            parsed == null || parsed <= 0.0 -> error = "يرجى إدخال مبلغ صحيح أكبر من صفر"
            debt.originalAmount + parsed > MAX_DEBT_AMOUNT ->
              error = "الإجمالي بعد الإضافة يتجاوز الحد الأقصى المسموح به (1,000,000,000)"
            else -> onConfirmAddition(parsed, note.trim())
          }
        },
        enabled = !isSaving,
        shape = MaterialTheme.shapes.medium,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_addition_button"),
      ) {
        Text(
          text = if (isSaving) "جاري التوثيق..." else "إضافة وتوثيق المبلغ",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.ui.theme.PayableCrimson
import com.example.ui.theme.ReceivableGreen
import com.example.util.MAX_DEBT_AMOUNT
import com.example.util.SUPPORTED_CURRENCIES
import com.example.util.formatDateAr
import com.example.util.formatEditableAmount

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditDebtSheet(
  existingDebt: Debt? = null,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onSaveNew: (
    personName: String,
    personPhone: String,
    direction: DebtDirection,
    currency: String,
    amount: Double,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
  ) -> Unit,
  onSaveEdit: (
    debtId: String,
    personName: String,
    personPhone: String,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
  ) -> Unit = { _, _, _, _, _, _ -> },
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isEditing = existingDebt != null

  var personName by remember(existingDebt) { mutableStateOf(existingDebt?.personName ?: "") }
  var personPhone by remember(existingDebt) { mutableStateOf(existingDebt?.personPhone ?: "") }
  var direction by remember(existingDebt) {
    mutableStateOf(existingDebt?.directionEnum ?: DebtDirection.RECEIVABLE)
  }
  var currency by remember(existingDebt) { mutableStateOf(existingDebt?.currency ?: "SAR") }
  var amountText by remember(existingDebt) {
    mutableStateOf(
      if (existingDebt != null) formatEditableAmount(existingDebt.originalAmount) else ""
    )
  }
  var dueDateMillis by remember(existingDebt) { mutableLongStateOf(existingDebt?.dueDateMillis ?: 0L) }
  var reminderEnabled by remember(existingDebt) {
    mutableStateOf(existingDebt?.reminderEnabled ?: true)
  }
  var notes by remember(existingDebt) { mutableStateOf(existingDebt?.notes ?: "") }
  var validationError by remember { mutableStateOf<String?>(null) }
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis =
          if (dueDateMillis > 0L) dueDateMillis else System.currentTimeMillis() + 7 * 86_400_000L
      )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { dueDateMillis = it }
            showDatePicker = false
          }
        ) {
          Text("تأكيد")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = if (isEditing) "تعديل تفاصيل السجل" else "توثيق دين / مستحق جديد",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Filled.Close, contentDescription = "إغلاق")
        }
      }

      if (!isEditing) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = direction == DebtDirection.RECEIVABLE,
            onClick = { direction = DebtDirection.RECEIVABLE },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors =
              SegmentedButtonDefaults.colors(
                activeContainerColor = ReceivableGreen.copy(alpha = 0.16f),
                activeContentColor = ReceivableGreen,
              ),
            modifier = Modifier.testTag("direction_receivable_btn"),
          ) {
            Text("مستحق لي (لي عنده)")
          }
          SegmentedButton(
            selected = direction == DebtDirection.PAYABLE,
            onClick = { direction = DebtDirection.PAYABLE },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors =
              SegmentedButtonDefaults.colors(
                activeContainerColor = PayableCrimson.copy(alpha = 0.15f),
                activeContentColor = PayableCrimson,
              ),
            modifier = Modifier.testTag("direction_payable_btn"),
          ) {
            Text("مطلوب مني (عليّ له)")
          }
        }
      }

      OutlinedTextField(
        value = personName,
        onValueChange = {
          personName = it
          validationError = null
        },
        label = { Text("اسم الشخص / الطرف الآخر") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("debt_person_name_input"),
      )

      OutlinedTextField(
        value = personPhone,
        onValueChange = { personPhone = it },
        label = { Text("رقم الجوال (اختياري للتذكير)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().testTag("debt_person_phone_input"),
      )

      if (!isEditing) {
        OutlinedTextField(
          value = amountText,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
              amountText = input
              validationError = null
            }
          },
          label = { Text("المبلغ") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("debt_amount_input"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "العملة",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SUPPORTED_CURRENCIES.forEach { option ->
              FilterChip(
                selected = currency == option.code,
                onClick = { currency = option.code },
                label = { Text("${option.symbolAr} (${option.code})") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
              )
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "موعد السداد المستهدف",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val now = System.currentTimeMillis()
          FilterChip(
            selected = dueDateMillis == 0L,
            onClick = { dueDateMillis = 0L },
            label = { Text("بدون موعد") },
          )
          FilterChip(
            selected = false,
            onClick = { dueDateMillis = now + 7L * 86_400_000L },
            label = { Text("بعد أسبوع") },
          )
          FilterChip(
            selected = false,
            onClick = { dueDateMillis = now + 30L * 86_400_000L },
            label = { Text("بعد شهر") },
          )
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.testTag("pick_due_date_button"),
          ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (dueDateMillis > 0L) formatDateAr(dueDateMillis) else "تحديد تاريخ")
          }
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Column {
              Text("تنبيه بموعد السداد", style = MaterialTheme.typography.titleSmall)
              Text(
                "إرسال إشعار محلي في تاريخ الاستحقاق",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Switch(
            checked = reminderEnabled,
            onCheckedChange = { reminderEnabled = it },
          )
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("البيان / سبب الدين وشروط السداد (اختياري)") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("debt_notes_input"),
      )

      if (validationError != null) {
        Text(
          text = validationError ?: "",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }

      Button(
        onClick = {
          val cleanName = personName.trim()
          if (cleanName.isEmpty()) {
            validationError = "يرجى إدخال اسم الشخص"
            return@Button
          }
          if (existingDebt != null) {
            onSaveEdit(
              existingDebt.id,
              cleanName,
              personPhone.trim(),
              dueDateMillis,
              reminderEnabled,
              notes.trim(),
            )
          } else {
            val parsedAmount = amountText.toDoubleOrNull()
            if (parsedAmount == null || parsedAmount <= 0.0) {
              validationError = "يرجى إدخال مبلغ صحيح أكبر من صفر"
              return@Button
            }
            if (parsedAmount > MAX_DEBT_AMOUNT) {
              validationError = "الحد الأقصى للمبلغ هو 1,000,000,000"
              return@Button
            }
            onSaveNew(
              cleanName,
              personPhone.trim(),
              direction,
              currency,
              parsedAmount,
              dueDateMillis,
              reminderEnabled,
              notes.trim(),
            )
          }
        },
        enabled = !isSaving,
        shape = MaterialTheme.shapes.medium,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .height(52.dp)
            .testTag("save_debt_button"),
      ) {
        Text(
          text = if (isSaving) "جاري الحفظ والتوثيق..." else if (isEditing) "حفظ التعديلات" else "حفظ وتوثيق الدين",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

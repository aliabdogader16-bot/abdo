package com.example.util

import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.data.model.DebtTransaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class CurrencyOption(
  val code: String,
  val labelAr: String,
  val symbolAr: String,
)

const val MAX_DEBT_AMOUNT = 1_000_000_000.0

val SUPPORTED_CURRENCIES =
  listOf(
    CurrencyOption("SAR", "ريال سعودي", "ر.س"),
    CurrencyOption("SDG", "جنيه سوداني", "ج.س"),
    CurrencyOption("EGP", "جنيه مصري", "ج.م"),
    CurrencyOption("AED", "درهم إماراتي", "د.إ"),
    CurrencyOption("KWD", "دينار كويتي", "د.ك"),
    CurrencyOption("JOD", "دينار أردني", "د.أ"),
    CurrencyOption("USD", "دولار أمريكي", "$"),
  )

fun currencySymbol(code: String): String =
  SUPPORTED_CURRENCIES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.symbolAr ?: code

fun formatAmount(amount: Double, currencyCode: String): String {
  val nf = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    maximumFractionDigits = 2
  }
  return "${nf.format(amount)} ${currencySymbol(currencyCode)}"
}

fun formatNumberOnly(amount: Double): String {
  val nf = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    maximumFractionDigits = 2
  }
  return nf.format(amount)
}

fun formatEditableAmount(amount: Double): String {
  val rounded = kotlin.math.round(amount * 100.0) / 100.0
  val formatted = String.format(Locale.US, "%.2f", rounded)
  return if (formatted.contains('.')) {
    formatted.trimEnd('0').trimEnd('.')
  } else {
    formatted
  }
}

fun formatDateAr(millis: Long): String {
  if (millis <= 0L) return "غير محدد"
  val sdf = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("ar"))
  return sdf.format(Date(millis))
}

fun formatDateTimeAr(millis: Long): String {
  if (millis <= 0L) return "الآن"
  val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.forLanguageTag("ar"))
  return sdf.format(Date(millis))
}

enum class DueUrgency {
  NONE,
  OVERDUE,
  DUE_SOON,
  UPCOMING,
  SETTLED,
}

data class DueStatusInfo(
  val urgency: DueUrgency,
  val label: String,
)

fun computeDueStatus(debt: Debt, nowMillis: Long = System.currentTimeMillis()): DueStatusInfo {
  if (debt.isSettled) return DueStatusInfo(DueUrgency.SETTLED, "مسدّد بالكامل")
  if (debt.dueDateMillis <= 0L) return DueStatusInfo(DueUrgency.NONE, "بدون موعد سداد")

  val startOfToday = startOfDayMillis(nowMillis)
  val startOfDue = startOfDayMillis(debt.dueDateMillis)
  val diffDays = ((startOfDue - startOfToday) / 86_400_000L).toInt()

  return when {
    diffDays < 0 -> DueStatusInfo(DueUrgency.OVERDUE, "متأخر بـ ${abs(diffDays)} يوم")
    diffDays == 0 -> DueStatusInfo(DueUrgency.DUE_SOON, "يستحق اليوم")
    diffDays <= 3 -> DueStatusInfo(DueUrgency.DUE_SOON, "يستحق خلال $diffDays أيام")
    else -> DueStatusInfo(DueUrgency.UPCOMING, "بعد $diffDays يوم (${formatDateAr(debt.dueDateMillis)})")
  }
}

fun startOfDayMillis(millis: Long): Long {
  val cal = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }
  return cal.timeInMillis
}

fun buildReminderShareText(debt: Debt): String {
  val dueLine =
    if (debt.dueDateMillis > 0L) "\nموعد السداد: ${formatDateAr(debt.dueDateMillis)}" else ""
  return if (debt.directionEnum == DebtDirection.RECEIVABLE) {
    "السلام عليكم ${debt.personName}،\nتذكير لطيف بموعد سداد المستحق المالي بقيمة ${formatAmount(debt.remainingAmount, debt.currency)}." +
      dueLine +
      "\nرقم التوثيق: ${debt.verificationCode}\nشكراً لتعاونكم."
  } else {
    "السلام عليكم ${debt.personName}،\nتأكيد التزامنا بسداد المبلغ المستحق لكم وقدره ${formatAmount(debt.remainingAmount, debt.currency)}." +
      dueLine +
      "\nرقم التوثيق: ${debt.verificationCode}"
  }
}

fun buildDocumentedStatementText(debt: Debt, transactions: List<DebtTransaction>): String {
  val sb = StringBuilder()
  sb.appendLine("════ كشف حساب وتوثيق مالي ════")
  sb.appendLine("التطبيق: ديون (Duyun)")
  sb.appendLine("رقم التوثيق: ${debt.verificationCode}")
  sb.appendLine("الطرف الآخر: ${debt.personName}")
  sb.appendLine("الصفة: ${debt.directionEnum.labelAr}")
  sb.appendLine("المبلغ الأصلي: ${formatAmount(debt.originalAmount, debt.currency)}")
  sb.appendLine("المدفوع: ${formatAmount(debt.paidAmount, debt.currency)}")
  sb.appendLine("المتبقي: ${formatAmount(debt.remainingAmount, debt.currency)}")
  sb.appendLine("الحالة: ${debt.statusEnum.labelAr}")
  if (debt.dueDateMillis > 0L) {
    sb.appendLine("موعد السداد: ${formatDateAr(debt.dueDateMillis)}")
  }
  if (debt.notes.isNotBlank()) {
    sb.appendLine("ملاحظات: ${debt.notes}")
  }
  sb.appendLine()
  sb.appendLine("── سجل العمليات الموثقة (${transactions.size}) ──")
  transactions.sortedBy { it.performedAtMillis }.forEachIndexed { idx, tx ->
    sb.appendLine(
      "${idx + 1}. ${tx.typeEnum.labelAr} | ${formatAmount(tx.amount, debt.currency)} | الرصيد بعد العملية: ${formatAmount(tx.balanceAfter, debt.currency)}"
    )
    sb.appendLine("   التاريخ: ${formatDateTimeAr(tx.performedAtMillis)} | مرجع: ${tx.referenceCode}")
    if (tx.note.isNotBlank()) sb.appendLine("   البيان: ${tx.note}")
  }
  sb.appendLine("══════════════════════════════")
  return sb.toString()
}

package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DebtDirection(val value: String, val labelAr: String) {
  RECEIVABLE("receivable", "مستحق لي"),
  PAYABLE("payable", "مطلوب مني");

  companion object {
    fun fromValue(value: String): DebtDirection =
      entries.firstOrNull { it.value == value } ?: RECEIVABLE
  }
}

enum class DebtStatus(val value: String, val labelAr: String) {
  ACTIVE("active", "نشط"),
  SETTLED("settled", "مسدّد");

  companion object {
    fun fromValue(value: String): DebtStatus =
      entries.firstOrNull { it.value == value } ?: ACTIVE
  }
}

enum class TransactionType(val value: String, val labelAr: String) {
  INITIAL("initial", "تأسيس الدين"),
  PAYMENT("payment", "دفعة سداد"),
  ADDITION("addition", "إضافة مبلغ");

  companion object {
    fun fromValue(value: String): TransactionType =
      entries.firstOrNull { it.value == value } ?: PAYMENT
  }
}

@IgnoreExtraProperties
data class Debt(
  @DocumentId val id: String = "",
  val userId: String = "",
  val personName: String = "",
  val personPhone: String = "",
  val direction: String = DebtDirection.RECEIVABLE.value,
  val currency: String = "SAR",
  val originalAmount: Double = 0.0,
  val remainingAmount: Double = 0.0,
  val status: String = DebtStatus.ACTIVE.value,
  val dueDateMillis: Long = 0L,
  val reminderEnabled: Boolean = false,
  val notes: String = "",
  val verificationCode: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val creditorName: String
    get() = personName

  @get:Exclude
  val amount: Double
    get() = if (isSettled) originalAmount else remainingAmount

  @get:Exclude
  val dueDate: Long
    get() = dueDateMillis

  @get:Exclude
  val directionEnum: DebtDirection
    get() = DebtDirection.fromValue(direction)

  @get:Exclude
  val statusEnum: DebtStatus
    get() = DebtStatus.fromValue(status)

  @get:Exclude
  val isSettled: Boolean
    get() = status == DebtStatus.SETTLED.value || remainingAmount <= 0.0

  @get:Exclude
  val paidAmount: Double
    get() = (originalAmount - remainingAmount).coerceAtLeast(0.0)

  @get:Exclude
  val progressFraction: Float
    get() =
      if (originalAmount <= 0.0) 0f
      else (paidAmount / originalAmount).toFloat().coerceIn(0f, 1f)
}

@IgnoreExtraProperties
data class DebtTransaction(
  @DocumentId val id: String = "",
  val userId: String = "",
  val debtId: String = "",
  val type: String = TransactionType.INITIAL.value,
  val amount: Double = 0.0,
  val balanceAfter: Double = 0.0,
  val note: String = "",
  val referenceCode: String = "",
  val performedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  val typeEnum: TransactionType
    get() = TransactionType.fromValue(type)
}

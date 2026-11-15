package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.data.model.DebtStatus
import com.example.data.model.DebtTransaction
import com.example.data.model.TransactionType
import com.example.util.MAX_DEBT_AMOUNT
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DebtRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً قبل الوصول إلى البيانات.")
  }

  private fun debtsCollection(uid: String) =
    db.collection("users").document(uid).collection("debts")

  fun observeDebts(userId: String): Flow<List<Debt>> = flow {
    val path = "users/$userId/debts"
    emitAll(
      debtsCollection(userId)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Debt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareBy<Debt> { it.isSettled }
                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  fun observeDebt(userId: String, debtId: String): Flow<Debt?> = flow {
    val docRef = debtsCollection(userId).document(debtId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(Debt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
          }
          throw error
        }
    )
  }

  fun observeTransactions(userId: String, debtId: String): Flow<List<DebtTransaction>> = flow {
    val txCollection = debtsCollection(userId).document(debtId).collection("transactions")
    emitAll(
      txCollection
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              DebtTransaction::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.performedAtMillis }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, txCollection.path, auth)
          }
          throw error
        }
    )
  }

  suspend fun getDebtOnce(debtId: String): Result<Debt?> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = debtsCollection(uid).document(debtId)
    return try {
      val snapshot = docRef.get().await()
      val debt =
        if (snapshot.exists()) {
          snapshot.toObject(Debt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      Result.success(debt)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun createDebt(
    personName: String,
    personPhone: String,
    direction: DebtDirection,
    currency: String,
    amount: Double,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
  ): Result<String> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanName = personName.trim()
    val cleanAmount = roundAmount(amount)
    if (cleanName.isEmpty() || cleanAmount <= 0.0) {
      return Result.failure(IllegalArgumentException("يرجى إدخال الاسم ومبلغ صحيح أكبر من صفر"))
    }
    if (cleanAmount > MAX_DEBT_AMOUNT) {
      return Result.failure(IllegalArgumentException("الحد الأقصى للمبلغ المسموح به هو 1,000,000,000"))
    }

    val debtRef = debtsCollection(uid).document()
    val txRef = debtRef.collection("transactions").document()
    val verificationCode = generateReferenceCode("DY", debtRef.id)
    val txCode = generateReferenceCode("TX", txRef.id)
    val nowMillis = System.currentTimeMillis()

    val debtPayload =
      mapOf(
        "userId" to uid,
        "personName" to cleanName.take(80),
        "personPhone" to personPhone.trim().take(30),
        "direction" to direction.value,
        "currency" to currency.trim().ifEmpty { "SAR" }.take(10),
        "originalAmount" to cleanAmount,
        "remainingAmount" to cleanAmount,
        "status" to DebtStatus.ACTIVE.value,
        "dueDateMillis" to dueDateMillis.coerceAtLeast(0L),
        "reminderEnabled" to reminderEnabled,
        "notes" to notes.trim().take(500),
        "verificationCode" to verificationCode,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val txPayload =
      mapOf(
        "userId" to uid,
        "debtId" to debtRef.id,
        "type" to TransactionType.INITIAL.value,
        "amount" to cleanAmount,
        "balanceAfter" to cleanAmount,
        "note" to notes.trim().ifEmpty { "تأسيس سجل الدين وتوثيق المبلغ الأساسي" }.take(300),
        "referenceCode" to txCode,
        "performedAtMillis" to nowMillis,
        "createdAt" to FieldValue.serverTimestamp(),
      )

    return try {
      db.batch().apply {
        set(debtRef, debtPayload)
        set(txRef, txPayload)
      }.commit().await()
      Result.success(debtRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, debtRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun recordPayment(debt: Debt, paymentAmount: Double, note: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanPayment = roundAmount(paymentAmount)
    if (debt.id.isBlank() || debt.isSettled) {
      return Result.failure(IllegalStateException("لا يمكن تسجيل دفعة على دين مسدّد بالفعل"))
    }
    if (cleanPayment <= 0.0 || cleanPayment > debt.remainingAmount + 0.001) {
      return Result.failure(IllegalArgumentException("مبلغ الدفعة يجب أن يكون ضمن الرصيد المتبقي"))
    }

    val newRemaining = roundAmount((debt.remainingAmount - cleanPayment).coerceAtLeast(0.0))
    val newStatus = if (newRemaining <= 0.0) DebtStatus.SETTLED.value else DebtStatus.ACTIVE.value
    val debtRef = debtsCollection(uid).document(debt.id)
    val txRef = debtRef.collection("transactions").document()
    val txCode = generateReferenceCode("TX", txRef.id)

    val debtUpdates =
      mapOf(
        "remainingAmount" to if (newStatus == DebtStatus.SETTLED.value) 0.0 else newRemaining,
        "status" to newStatus,
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val txPayload =
      mapOf(
        "userId" to uid,
        "debtId" to debt.id,
        "type" to TransactionType.PAYMENT.value,
        "amount" to cleanPayment,
        "balanceAfter" to if (newStatus == DebtStatus.SETTLED.value) 0.0 else newRemaining,
        "note" to
          note
            .trim()
            .ifEmpty { if (newStatus == DebtStatus.SETTLED.value) "سداد كامل وتصفية الدين" else "دفعة سداد جزئية" }
            .take(300),
        "referenceCode" to txCode,
        "performedAtMillis" to System.currentTimeMillis(),
        "createdAt" to FieldValue.serverTimestamp(),
      )

    return try {
      db.batch().apply {
        update(debtRef, debtUpdates)
        set(txRef, txPayload)
      }.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, debtRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun addAmountToDebt(debt: Debt, additionalAmount: Double, note: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanAddition = roundAmount(additionalAmount)
    if (debt.id.isBlank() || debt.isSettled) {
      return Result.failure(IllegalStateException("لا يمكن تعديل دين مسدّد بالفعل"))
    }
    if (cleanAddition <= 0.0) {
      return Result.failure(IllegalArgumentException("يرجى إدخال مبلغ صحيح أكبر من صفر"))
    }

    val newOriginal = roundAmount(debt.originalAmount + cleanAddition)
    val newRemaining = roundAmount(debt.remainingAmount + cleanAddition)
    if (newOriginal > MAX_DEBT_AMOUNT || newRemaining > MAX_DEBT_AMOUNT) {
      return Result.failure(
        IllegalArgumentException("الإجمالي بعد الإضافة يتجاوز الحد الأقصى المسموح به (1,000,000,000)")
      )
    }
    val debtRef = debtsCollection(uid).document(debt.id)
    val txRef = debtRef.collection("transactions").document()
    val txCode = generateReferenceCode("TX", txRef.id)

    val debtUpdates =
      mapOf(
        "originalAmount" to newOriginal,
        "remainingAmount" to newRemaining,
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val txPayload =
      mapOf(
        "userId" to uid,
        "debtId" to debt.id,
        "type" to TransactionType.ADDITION.value,
        "amount" to cleanAddition,
        "balanceAfter" to newRemaining,
        "note" to note.trim().ifEmpty { "إضافة مبلغ جديد إلى الرصيد" }.take(300),
        "referenceCode" to txCode,
        "performedAtMillis" to System.currentTimeMillis(),
        "createdAt" to FieldValue.serverTimestamp(),
      )

    return try {
      db.batch().apply {
        update(debtRef, debtUpdates)
        set(txRef, txPayload)
      }.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, debtRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateDebtDetails(
    debtId: String,
    personName: String,
    personPhone: String,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
  ): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val cleanName = personName.trim()
    if (debtId.isBlank() || cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("بيانات التحديث غير مكتملة"))
    }
    val debtRef = debtsCollection(uid).document(debtId)
    val updates =
      mapOf(
        "personName" to cleanName.take(80),
        "personPhone" to personPhone.trim().take(30),
        "dueDateMillis" to dueDateMillis.coerceAtLeast(0L),
        "reminderEnabled" to reminderEnabled,
        "notes" to notes.trim().take(500),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      debtRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, debtRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteDebt(debtId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (debtId.isBlank()) {
      return Result.failure(IllegalArgumentException("معرف الدين غير صالح"))
    }
    val debtRef = debtsCollection(uid).document(debtId)
    return try {
      val txSnapshot = debtRef.collection("transactions").get().await()
      val txDocs = txSnapshot.documents
      val chunks = txDocs.chunked(450)
      if (chunks.isEmpty()) {
        debtRef.delete().await()
      } else {
        chunks.forEachIndexed { index, chunk ->
          val batch = db.batch()
          chunk.forEach { doc -> batch.delete(doc.reference) }
          if (index == chunks.lastIndex) {
            batch.delete(debtRef)
          }
          batch.commit().await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, debtRef.path, auth)
      Result.failure(e)
    }
  }

  private fun roundAmount(value: Double): Double = (value * 100.0).roundToLong() / 100.0

  private fun generateReferenceCode(prefix: String, docId: String): String {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val suffix = docId.take(4).uppercase(Locale.US).ifEmpty { "0000" }
    return "$prefix-$year-$suffix"
  }
}

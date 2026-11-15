package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DebtDirection
import com.example.data.model.DebtStatus
import com.example.data.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createDebt_validPayload_createsDebtAndInitialTransaction() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = DebtRepository(firestore, auth)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createDebt(
          personName = "خالد العتيبي",
          personPhone = "0500001122",
          direction = DebtDirection.RECEIVABLE,
          currency = "SAR",
          amount = 1200.0,
          dueDateMillis = System.currentTimeMillis() + 86_400_000L,
          reminderEnabled = true,
          notes = "سلفة مؤقتة",
        )
      }
    assertTrue("Expected createDebt to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
    val debtId = createResult.getOrThrow()

    val debt = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getDebtOnce(debtId).getOrThrow() }
    assertNotNull(debt)
    assertEquals(uid, debt?.userId)
    assertEquals(1200.0, debt?.remainingAmount ?: 0.0, 0.01)

    val transactions =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeTransactions(uid, debtId).first { it.isNotEmpty() }
      }
    assertEquals(1, transactions.size)
    assertEquals(TransactionType.INITIAL.value, transactions.first().type)
  }

  @Test
  fun recordPayment_partialAndFinalSettlement_updatesDebtAndLogsImmutableTransactions() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = DebtRepository(firestore, auth)

    val debtId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createDebt(
            personName = "سعيد الشهري",
            personPhone = "",
            direction = DebtDirection.PAYABLE,
            currency = "SAR",
            amount = 500.0,
            dueDateMillis = 0L,
            reminderEnabled = false,
            notes = "شراء مستلزمات",
          )
          .getOrThrow()
      }

    val initialDebt = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getDebtOnce(debtId).getOrThrow()!! }
    val partialResult =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.recordPayment(initialDebt, 200.0, "دفعة أولى") }
    assertTrue("Partial payment failed: ${partialResult.exceptionOrNull()}", partialResult.isSuccess)

    val afterPartial = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getDebtOnce(debtId).getOrThrow()!! }
    assertEquals(300.0, afterPartial.remainingAmount, 0.01)
    assertEquals(DebtStatus.ACTIVE.value, afterPartial.status)

    val finalResult =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.recordPayment(afterPartial, 300.0, "تصفية كاملة") }
    assertTrue("Final payment failed: ${finalResult.exceptionOrNull()}", finalResult.isSuccess)

    val settledDebt = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getDebtOnce(debtId).getOrThrow()!! }
    assertEquals(0.0, settledDebt.remainingAmount, 0.01)
    assertEquals(DebtStatus.SETTLED.value, settledDebt.status)

    val txs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeTransactions(uid, debtId).first { it.size == 3 }
      }
    assertEquals(3, txs.size)
  }

  @Test
  fun getDebtOnce_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = DebtRepository(firestore, auth)
    val debtId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createDebt(
            personName = "فهد",
            personPhone = "",
            direction = DebtDirection.RECEIVABLE,
            currency = "SAR",
            amount = 100.0,
            dueDateMillis = 0L,
            reminderEnabled = false,
            notes = "",
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = DebtRepository(firestore, auth)
    val crossFlowError =
      try {
        withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeDebts(aliceUid).first() }
        null
      } catch (e: Exception) {
        e.unwrapFirestoreException()
      }
    assertNotNull("Expected PERMISSION_DENIED for cross-user observeDebts", crossFlowError)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossFlowError?.code)
    assertTrue(debtId.isNotBlank())
  }

  @Test
  fun observeDebts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = DebtRepository(firestore, auth)
    val exception =
      try {
        withTimeout(FLOW_TIMEOUT_MS) { repo.observeDebts("some_user_uid").first() }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull("Expected exception when unauthenticated", exception)
    val firestoreException = exception?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun viewModel_fetchesDebtsFromFirestore_exposesCreditorAmountAndDueDate() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = DebtRepository(firestore, auth)
    val targetDueDate = System.currentTimeMillis() + 5 * 86_400_000L

    val debtId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createDebt(
            personName = "عبدالله الحربي",
            personPhone = "0551122334",
            direction = DebtDirection.PAYABLE,
            currency = "SAR",
            amount = 750.0,
            dueDateMillis = targetDueDate,
            reminderEnabled = true,
            notes = "مستحق شراء",
          )
          .getOrThrow()
      }

    val viewModel = com.example.ui.viewmodel.DebtViewModel(repo, uid)
    val successState =
      withTimeout(FLOW_TIMEOUT_MS) {
        viewModel.filteredDebtsState
          .first { state ->
            state is com.example.ui.viewmodel.UiState.Success &&
              state.data.any { it.id == debtId }
          } as com.example.ui.viewmodel.UiState.Success
      }

    val fetchedDebt = successState.data.first { it.id == debtId }
    assertEquals("عبدالله الحربي", fetchedDebt.creditorName)
    assertEquals(750.0, fetchedDebt.amount, 0.01)
    assertEquals(targetDueDate, fetchedDebt.dueDate)
  }

  @Test
  fun updateAndDeleteDebt_updatesFirestoreImmediately() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = DebtRepository(firestore, auth)
    val initialDue = System.currentTimeMillis() + 3 * 86_400_000L
    val updatedDue = System.currentTimeMillis() + 10 * 86_400_000L

    val debtId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createDebt(
            personName = "ياسر القحطاني",
            personPhone = "0501112233",
            direction = DebtDirection.RECEIVABLE,
            currency = "SAR",
            amount = 400.0,
            dueDateMillis = initialDue,
            reminderEnabled = false,
            notes = "ملاحظة قديمة",
          )
          .getOrThrow()
      }

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateDebtDetails(
          debtId = debtId,
          personName = "ياسر صالح",
          personPhone = "0559998877",
          dueDateMillis = updatedDue,
          reminderEnabled = true,
          notes = "ملاحظة محدثة",
        )
      }
    assertTrue("Expected update to succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getDebtOnce(debtId).getOrThrow()!! }
    assertEquals("ياسر صالح", updated.creditorName)
    assertEquals("0559998877", updated.personPhone)
    assertEquals(updatedDue, updated.dueDate)
    assertTrue(updated.reminderEnabled)
    assertEquals("ملاحظة محدثة", updated.notes)

    val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteDebt(debtId) }
    assertTrue("Expected delete to succeed: ${deleteResult.exceptionOrNull()}", deleteResult.isSuccess)

    val listAfterDelete =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeDebts(uid).first { list -> list.none { it.id == debtId } }
      }
    assertTrue(listAfterDelete.none { it.id == debtId })
  }

  private companion object {
    const val ALICE_EMAIL = "alice.duyun@test.com"
    const val BOB_EMAIL = "bob.duyun@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}

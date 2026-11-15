package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.data.model.DebtTransaction
import com.example.data.repository.DebtRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class DebtFilter(val labelAr: String) {
  ALL("الكل"),
  RECEIVABLE("مستحق لي"),
  PAYABLE("مطلوب مني"),
  SETTLED("مسددة"),
}

enum class DebtSortOrder(val labelAr: String) {
  RECENT("الأحدث"),
  DUE_DATE("الأقرب استحقاقاً"),
  HIGHEST_AMOUNT("الأعلى مبلغاً"),
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebtViewModel(
  private val repository: DebtRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val _refreshTrigger = MutableStateFlow(0)

  private val _filter = MutableStateFlow(DebtFilter.ALL)
  val filter: StateFlow<DebtFilter> = _filter.asStateFlow()

  private val _sortOrder = MutableStateFlow(DebtSortOrder.RECENT)
  val sortOrder: StateFlow<DebtSortOrder> = _sortOrder.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedDebtId = MutableStateFlow<String?>(null)
  val selectedDebtId: StateFlow<String?> = _selectedDebtId.asStateFlow()

  private val _isActionLoading = MutableStateFlow(false)
  val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

  private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val events: SharedFlow<String> = _events.asSharedFlow()

  val allDebtsState: StateFlow<UiState<List<Debt>>> =
    _refreshTrigger
      .flatMapLatest {
        repository
          .observeDebts(currentUserId)
          .map<List<Debt>, UiState<List<Debt>>> { UiState.Success(it) }
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing debts", rootError)
            emit(UiState.Error(friendlyErrorMessage(rootError, "تعذر تحميل الديون من السحابة")))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val filteredDebtsState: StateFlow<UiState<List<Debt>>> =
    combine(allDebtsState, _filter, _sortOrder, _searchQuery) {
      state,
      activeFilter,
      activeSort,
      query ->
      when (state) {
        is UiState.Loading -> UiState.Loading
        is UiState.Error -> state
        is UiState.Success -> {
          val trimmedQuery = query.trim()
          val filtered =
            state.data.filter { debt ->
              val matchesFilter =
                when (activeFilter) {
                  DebtFilter.ALL -> !debt.isSettled
                  DebtFilter.RECEIVABLE ->
                    !debt.isSettled && debt.directionEnum == DebtDirection.RECEIVABLE
                  DebtFilter.PAYABLE ->
                    !debt.isSettled && debt.directionEnum == DebtDirection.PAYABLE
                  DebtFilter.SETTLED -> debt.isSettled
                }
              val matchesQuery =
                trimmedQuery.isEmpty() ||
                  debt.creditorName.contains(trimmedQuery, ignoreCase = true) ||
                  debt.notes.contains(trimmedQuery, ignoreCase = true) ||
                  debt.verificationCode.contains(trimmedQuery, ignoreCase = true)
              matchesFilter && matchesQuery
            }
          val sorted =
            when (activeSort) {
              DebtSortOrder.RECENT -> filtered
              DebtSortOrder.DUE_DATE ->
                filtered.sortedWith(
                  compareBy<Debt> { if (it.dueDate <= 0L) Long.MAX_VALUE else it.dueDate }
                    .thenByDescending { it.amount }
                )
              DebtSortOrder.HIGHEST_AMOUNT -> filtered.sortedByDescending { it.amount }
            }
          UiState.Success(sorted)
        }
      }
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val selectedDebtState: StateFlow<UiState<Debt?>> =
    _selectedDebtId
      .flatMapLatest { debtId ->
        if (debtId.isNullOrBlank()) {
          flowOf<UiState<Debt?>>(UiState.Success(null))
        } else {
          repository
            .observeDebt(currentUserId, debtId)
            .map<Debt?, UiState<Debt?>> { UiState.Success(it) }
            .catch { error ->
              val firestoreError =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (firestoreError == null && error is CancellationException) throw error
              val rootError = firestoreError ?: error
              emit(UiState.Error(friendlyErrorMessage(rootError, "تعذر تحميل تفاصيل الدين")))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val selectedTransactionsState: StateFlow<UiState<List<DebtTransaction>>> =
    _selectedDebtId
      .flatMapLatest { debtId ->
        if (debtId.isNullOrBlank()) {
          flowOf<UiState<List<DebtTransaction>>>(UiState.Success(emptyList()))
        } else {
          repository
            .observeTransactions(currentUserId, debtId)
            .map<List<DebtTransaction>, UiState<List<DebtTransaction>>> { UiState.Success(it) }
            .catch { error ->
              val firestoreError =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (firestoreError == null && error is CancellationException) throw error
              val rootError = firestoreError ?: error
              emit(UiState.Error(friendlyErrorMessage(rootError, "تعذر تحميل سجل العمليات")))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  fun retryObserve() {
    _refreshTrigger.value++
  }

  fun setFilter(newFilter: DebtFilter) {
    _filter.value = newFilter
  }

  fun setSortOrder(newOrder: DebtSortOrder) {
    _sortOrder.value = newOrder
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectDebt(debtId: String?) {
    _selectedDebtId.value = debtId
  }

  fun createDebt(
    personName: String,
    personPhone: String,
    direction: DebtDirection,
    currency: String,
    amount: Double,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
    onSuccess: (String) -> Unit = {},
  ) {
    viewModelScope.launch {
      _isActionLoading.value = true
      val result =
        repository.createDebt(
          personName = personName,
          personPhone = personPhone,
          direction = direction,
          currency = currency,
          amount = amount,
          dueDateMillis = dueDateMillis,
          reminderEnabled = reminderEnabled,
          notes = notes,
        )
      _isActionLoading.value = false
      result
        .onSuccess { debtId ->
          _events.tryEmit("تم توثيق وحفظ السجل المالي بنجاح")
          onSuccess(debtId)
        }
        .onFailure { e ->
          _events.tryEmit(friendlyErrorMessage(e, "تعذر حفظ الدين، يرجى المحاولة مرة أخرى"))
        }
    }
  }

  fun recordPayment(
    debt: Debt,
    paymentAmount: Double,
    note: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isActionLoading.value = true
      val result = repository.recordPayment(debt, paymentAmount, note)
      _isActionLoading.value = false
      result
        .onSuccess {
          val settled = paymentAmount >= debt.remainingAmount - 0.001
          _events.tryEmit(
            if (settled) "تم سداد الدين بالكامل وتوثيق التصفية"
            else "تم تسجيل وتوثيق دفعة السداد بنجاح"
          )
          onSuccess()
        }
        .onFailure { e ->
          _events.tryEmit(friendlyErrorMessage(e, "تعذر تسجيل الدفعة"))
        }
    }
  }

  fun addAmountToDebt(
    debt: Debt,
    additionalAmount: Double,
    note: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isActionLoading.value = true
      val result = repository.addAmountToDebt(debt, additionalAmount, note)
      _isActionLoading.value = false
      result
        .onSuccess {
          _events.tryEmit("تمت إضافة المبلغ وتوثيق العملية بنجاح")
          onSuccess()
        }
        .onFailure { e ->
          _events.tryEmit(friendlyErrorMessage(e, "تعذر إضافة المبلغ"))
        }
    }
  }

  fun updateDebtDetails(
    debtId: String,
    personName: String,
    personPhone: String,
    dueDateMillis: Long,
    reminderEnabled: Boolean,
    notes: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isActionLoading.value = true
      val result =
        repository.updateDebtDetails(
          debtId = debtId,
          personName = personName,
          personPhone = personPhone,
          dueDateMillis = dueDateMillis,
          reminderEnabled = reminderEnabled,
          notes = notes,
        )
      _isActionLoading.value = false
      result
        .onSuccess {
          _events.tryEmit("تم تحديث بيانات الدين وحفظها في السحابة بنجاح")
          onSuccess()
        }
        .onFailure { e ->
          _events.tryEmit(friendlyErrorMessage(e, "تعذر تحديث البيانات"))
        }
    }
  }

  fun deleteDebt(debtId: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isActionLoading.value = true
      val result = repository.deleteDebt(debtId)
      _isActionLoading.value = false
      result
        .onSuccess {
          _events.tryEmit("تم حذف الدين وتحديث القائمة بنجاح")
          onSuccess()
        }
        .onFailure { e ->
          _events.tryEmit(friendlyErrorMessage(e, "تعذر حذف السجل"))
        }
    }
  }

  fun emitMessage(message: String) {
    _events.tryEmit(message)
  }

  private fun friendlyErrorMessage(error: Throwable, fallback: String): String {
    val firestoreException =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreException?.code) {
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
        "تعذر الاتصال بالخادم، تحقق من اتصال الإنترنت وحاول مرة أخرى"
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "لا تملك صلاحية تنفيذ هذه العملية أو البيانات غير مطابقة للشروط"
      FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        "انتهت جلسة الدخول، يرجى إعادة تسجيل الدخول بحساب Google"
      FirebaseFirestoreException.Code.NOT_FOUND ->
        "السجل المطلوب غير موجود أو تم حذفه مسبقاً"
      else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
  }

  private companion object {
    const val TAG = "DebtVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}

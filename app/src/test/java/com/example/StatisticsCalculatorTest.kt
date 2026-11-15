package com.example

import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.data.model.DebtStatus
import com.example.ui.screens.StatisticsScope
import com.example.ui.screens.computeDebtStatistics
import com.example.ui.screens.extractSortedCurrencies
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsCalculatorTest {

  private fun sampleDebts(): List<Debt> =
    listOf(
      Debt(
        id = "1",
        personName = "أحمد",
        direction = DebtDirection.RECEIVABLE.value,
        currency = "SAR",
        originalAmount = 1000.0,
        remainingAmount = 700.0,
        status = DebtStatus.ACTIVE.value,
      ),
      Debt(
        id = "2",
        personName = "خالد",
        direction = DebtDirection.RECEIVABLE.value,
        currency = "SAR",
        originalAmount = 500.0,
        remainingAmount = 0.0,
        status = DebtStatus.SETTLED.value,
      ),
      Debt(
        id = "3",
        personName = "فهد",
        direction = DebtDirection.PAYABLE.value,
        currency = "SAR",
        originalAmount = 400.0,
        remainingAmount = 250.0,
        status = DebtStatus.ACTIVE.value,
      ),
      Debt(
        id = "4",
        personName = "سالم",
        direction = DebtDirection.PAYABLE.value,
        currency = "USD",
        originalAmount = 200.0,
        remainingAmount = 200.0,
        status = DebtStatus.ACTIVE.value,
      ),
    )

  @Test
  fun extractSortedCurrencies_ordersByFrequency() {
    val currencies = extractSortedCurrencies(sampleDebts())
    assertEquals(listOf("SAR", "USD"), currencies)
  }

  @Test
  fun computeDebtStatistics_activeRemaining_excludesSettledAndOtherCurrencies() {
    val summary = computeDebtStatistics(sampleDebts(), "SAR", StatisticsScope.ACTIVE_REMAINING)

    assertEquals(700.0, summary.receivableTotal, 0.01)
    assertEquals(250.0, summary.payableTotal, 0.01)
    assertEquals(450.0, summary.netBalance, 0.01)
    assertEquals(1, summary.receivableCount)
    assertEquals(1, summary.payableCount)
    assertEquals((800.0 / 1500.0).toFloat(), summary.receivableCollectionRate, 0.001f)
    assertEquals((150.0 / 400.0).toFloat(), summary.payableRepaymentRate, 0.001f)
  }

  @Test
  fun computeDebtStatistics_allOriginal_includesSettledAmounts() {
    val summary = computeDebtStatistics(sampleDebts(), "SAR", StatisticsScope.ALL_ORIGINAL)

    assertEquals(1500.0, summary.receivableTotal, 0.01)
    assertEquals(400.0, summary.payableTotal, 0.01)
    assertEquals(1100.0, summary.netBalance, 0.01)
    assertEquals(2, summary.receivableCount)
    assertEquals(1, summary.payableCount)
  }

  @Test
  fun sudaneseCurrency_andEditableFormatting_workAccurately() {
    assertEquals("ج.س", com.example.util.currencySymbol("SDG"))
    assertEquals("50,000 ج.س", com.example.util.formatAmount(50_000.0, "SDG"))
    assertEquals("50000000", com.example.util.formatEditableAmount(50_000_000.0))
    assertEquals("1250.5", com.example.util.formatEditableAmount(1250.50))
  }
}

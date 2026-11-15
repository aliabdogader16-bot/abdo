package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.ui.theme.BlueGreenGradientColors
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PayableCrimson
import com.example.ui.theme.PayableCrimsonContainer
import com.example.ui.theme.ReceivableGreen
import com.example.ui.theme.ReceivableGreenContainer
import com.example.ui.viewmodel.UiState
import com.example.util.formatAmount
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

enum class StatisticsScope(val labelAr: String) {
  ACTIVE_REMAINING("المتبقي النشط"),
  ALL_ORIGINAL("إجمالي المبالغ الموثقة"),
}

data class DebtStatisticsSummary(
  val currency: String,
  val scope: StatisticsScope,
  val receivableTotal: Double,
  val payableTotal: Double,
  val receivableCount: Int,
  val payableCount: Int,
  val receivablePaidTotal: Double,
  val receivableOriginalTotal: Double,
  val payablePaidTotal: Double,
  val payableOriginalTotal: Double,
) {
  val combinedTotal: Double
    get() = receivableTotal + payableTotal

  val netBalance: Double
    get() = receivableTotal - payableTotal

  val receivableShare: Float
    get() = if (combinedTotal > 0.0) (receivableTotal / combinedTotal).toFloat() else 0f

  val payableShare: Float
    get() = if (combinedTotal > 0.0) (payableTotal / combinedTotal).toFloat() else 0f

  val receivableCollectionRate: Float
    get() =
      if (receivableOriginalTotal > 0.0) {
        (receivablePaidTotal / receivableOriginalTotal).toFloat().coerceIn(0f, 1f)
      } else {
        0f
      }

  val payableRepaymentRate: Float
    get() =
      if (payableOriginalTotal > 0.0) {
        (payablePaidTotal / payableOriginalTotal).toFloat().coerceIn(0f, 1f)
      } else {
        0f
      }
}

fun extractSortedCurrencies(debts: List<Debt>): List<String> =
  debts
    .groupBy { it.currency.ifBlank { "SAR" } }
    .entries
    .sortedByDescending { it.value.size }
    .map { it.key }
    .ifEmpty { listOf("SAR") }

fun computeDebtStatistics(
  debts: List<Debt>,
  currency: String,
  scope: StatisticsScope,
): DebtStatisticsSummary {
  val currencyDebts = debts.filter { it.currency.ifBlank { "SAR" } == currency }
  val scopedDebts =
    when (scope) {
      StatisticsScope.ACTIVE_REMAINING -> currencyDebts.filter { !it.isSettled }
      StatisticsScope.ALL_ORIGINAL -> currencyDebts
    }

  val receivables = scopedDebts.filter { it.directionEnum == DebtDirection.RECEIVABLE }
  val payables = scopedDebts.filter { it.directionEnum == DebtDirection.PAYABLE }

  val receivableTotal =
    when (scope) {
      StatisticsScope.ACTIVE_REMAINING -> receivables.sumOf { it.remainingAmount }
      StatisticsScope.ALL_ORIGINAL -> receivables.sumOf { it.originalAmount }
    }
  val payableTotal =
    when (scope) {
      StatisticsScope.ACTIVE_REMAINING -> payables.sumOf { it.remainingAmount }
      StatisticsScope.ALL_ORIGINAL -> payables.sumOf { it.originalAmount }
    }

  val allReceivables = currencyDebts.filter { it.directionEnum == DebtDirection.RECEIVABLE }
  val allPayables = currencyDebts.filter { it.directionEnum == DebtDirection.PAYABLE }

  return DebtStatisticsSummary(
    currency = currency,
    scope = scope,
    receivableTotal = receivableTotal,
    payableTotal = payableTotal,
    receivableCount = receivables.size,
    payableCount = payables.size,
    receivablePaidTotal = allReceivables.sumOf { it.paidAmount },
    receivableOriginalTotal = allReceivables.sumOf { it.originalAmount },
    payablePaidTotal = allPayables.sumOf { it.paidAmount },
    payableOriginalTotal = allPayables.sumOf { it.originalAmount },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(
  allDebtsState: UiState<List<Debt>>,
  onNavigateToDebts: () -> Unit = {},
  onRetry: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  when (allDebtsState) {
    is UiState.Loading -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      Box(
        modifier = modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = allDebtsState.message,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag("retry_statistics_button")) {
              Text("إعادة المحاولة")
            }
          }
        }
      }
    }
    is UiState.Success -> {
      val allDebts = allDebtsState.data
      if (allDebts.isEmpty()) {
        Box(
          modifier = modifier.fillMaxSize().padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          EmptyStatisticsState(onNavigateToDebts = onNavigateToDebts)
        }
        return
      }

      val currencies = remember(allDebts) { extractSortedCurrencies(allDebts) }
      var selectedCurrency by remember(currencies) { mutableStateOf(currencies.first()) }
      val activeCurrency =
        if (selectedCurrency in currencies) selectedCurrency else currencies.first()

      var selectedScope by remember { mutableStateOf(StatisticsScope.ACTIVE_REMAINING) }
      val summary =
        remember(allDebts, activeCurrency, selectedScope) {
          computeDebtStatistics(allDebts, activeCurrency, selectedScope)
        }

      LazyColumn(
        modifier = modifier.fillMaxSize().testTag("statistics_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatisticsScope.entries.forEachIndexed { index, scope ->
              SegmentedButton(
                selected = selectedScope == scope,
                onClick = { selectedScope = scope },
                shape =
                  SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatisticsScope.entries.size,
                  ),
                modifier = Modifier.testTag("stats_scope_${scope.name.lowercase()}"),
              ) {
                Text(scope.labelAr)
              }
            }
          }
        }

        if (currencies.size > 1) {
          item {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              currencies.forEach { code ->
                FilterChip(
                  selected = activeCurrency == code,
                  onClick = { selectedCurrency = code },
                  label = { Text("عملة $code") },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primary,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                  modifier = Modifier.testTag("stats_currency_$code"),
                )
              }
            }
          }
        }

        item {
          StatisticsKpiRow(summary = summary)
        }

        item {
          AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = spring())) {
            ReceivableVsPayableBarChartCard(summary = summary)
          }
        }

        item {
          DistributionDonutCard(summary = summary)
        }

        item {
          RepaymentProgressCard(summary = summary)
        }
      }
    }
  }
}

@Composable
private fun StatisticsKpiRow(summary: DebtStatisticsSummary) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Card(
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
      modifier = Modifier.fillMaxWidth().testTag("stats_net_card"),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(Brush.linearGradient(colors = BlueGreenGradientColors))
      ) {
        Canvas(modifier = Modifier.matchParentSize()) {
          drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width * 0.1f, size.height * 0.2f),
          )
          drawCircle(
            color = Color(0xFF4ADEAC).copy(alpha = 0.14f),
            radius = size.minDimension * 0.45f,
            center = Offset(size.width * 0.9f, size.height * 0.85f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "صافي الرصيد (${summary.scope.labelAr})",
              style = MaterialTheme.typography.labelLarge,
              color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text =
                (if (summary.netBalance >= 0) "+" else "-") +
                  formatAmount(abs(summary.netBalance), summary.currency),
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
          }
          Surface(
            color = Color.White.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text =
                if (summary.netBalance >= 0) "لصالحك"
                else "التزام صافي",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      StatMetricCard(
        title = "المستحق لك",
        amountText = formatAmount(summary.receivableTotal, summary.currency),
        countLabel = "${summary.receivableCount} سجل",
        accentColor = ReceivableGreen,
        containerColor = ReceivableGreenContainer,
        isReceivable = true,
        modifier = Modifier.weight(1f).testTag("stats_receivable_kpi"),
      )
      StatMetricCard(
        title = "عليك سداده",
        amountText = formatAmount(summary.payableTotal, summary.currency),
        countLabel = "${summary.payableCount} سجل",
        accentColor = PayableCrimson,
        containerColor = PayableCrimsonContainer,
        isReceivable = false,
        modifier = Modifier.weight(1f).testTag("stats_payable_kpi"),
      )
    }
  }
}

@Composable
private fun StatMetricCard(
  title: String,
  amountText: String,
  countLabel: String,
  accentColor: Color,
  containerColor: Color,
  isReceivable: Boolean,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier = Modifier.size(32.dp).clip(CircleShape).background(containerColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (isReceivable) Icons.AutoMirrored.Filled.TrendingUp
              else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = amountText,
        style = MaterialTheme.typography.titleLarge,
        color = accentColor,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = countLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReceivableVsPayableBarChartCard(summary: DebtStatisticsSummary) {
  var selectedBar by remember { mutableStateOf<DebtDirection?>(null) }

  val maxRaw = max(summary.receivableTotal, summary.payableTotal)
  val axisCeiling = remember(maxRaw) { computeNiceAxisMax(maxRaw) }

  val receivableTarget =
    if (axisCeiling > 0.0) (summary.receivableTotal / axisCeiling).toFloat().coerceIn(0f, 1f) else 0f
  val payableTarget =
    if (axisCeiling > 0.0) (summary.payableTotal / axisCeiling).toFloat().coerceIn(0f, 1f) else 0f

  val animatedReceivable by
    animateFloatAsState(
      targetValue = receivableTarget,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
      label = "receivableBar",
    )
  val animatedPayable by
    animateFloatAsState(
      targetValue = payableTarget,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
      label = "payableBar",
    )

  val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  val selectedHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = Modifier.fillMaxWidth().testTag("comparison_bar_chart_card"),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "المستحق لك مقابل المطلوب سداده",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "اضغط على أي عمود لعرض التفاصيل الدقيقة",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.Filled.BarChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LegendDot(color = ReceivableGreen, label = "مستحق لك")
        LegendDot(color = PayableCrimson, label = "عليك سداده")
      }

      if (selectedBar != null) {
        val isRec = selectedBar == DebtDirection.RECEIVABLE
        val label = if (isRec) "إجمالي المستحق لك" else "إجمالي المطلوب منك سداده"
        val value = if (isRec) summary.receivableTotal else summary.payableTotal
        val count = if (isRec) summary.receivableCount else summary.payableCount
        val pct = ((if (isRec) summary.receivableShare else summary.payableShare) * 100f).toInt()
        val bg = if (isRec) ReceivableGreenContainer else PayableCrimsonContainer
        val fg = if (isRec) ReceivableGreen else PayableCrimson

        Surface(
          color = bg,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().testTag("chart_tooltip_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column {
              Text(text = label, style = MaterialTheme.typography.labelLarge, color = fg)
              Text(
                text = "$count سجل • يمثل $pct% من الإجمالي",
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f),
              )
            }
            Text(
              text = formatAmount(value, summary.currency),
              style = MaterialTheme.typography.titleMedium,
              color = fg,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      if (summary.combinedTotal <= 0.0) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "لا توجد مبالغ ضمن هذا التصنيف للرسم البياني حالياً.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
          )
        }
      } else {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .height(220.dp)
              .semantics {
                contentDescription =
                  "رسم بياني للمستحق لك ${formatAmount(summary.receivableTotal, summary.currency)} مقابل المطلوب سداده ${formatAmount(summary.payableTotal, summary.currency)}"
              },
          verticalAlignment = Alignment.Bottom,
        ) {
          Column(
            modifier = Modifier.fillMaxHeight().padding(bottom = 26.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
          ) {
            listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { factor ->
              Text(
                text = formatCompactAxisValue(axisCeiling * factor),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Canvas(
              modifier = Modifier.fillMaxSize().padding(bottom = 26.dp, top = 6.dp)
            ) {
              val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
              val steps = 4
              for (i in 0..steps) {
                val y = size.height * (i.toFloat() / steps)
                drawLine(
                  color = gridLineColor,
                  start = Offset(0f, y),
                  end = Offset(size.width, y),
                  strokeWidth = if (i == steps) 2.5f else 1.5f,
                  pathEffect = if (i == steps) null else dashEffect,
                )
              }

              val slotWidth = size.width / 2f
              val barWidth = (slotWidth * 0.48f).coerceAtMost(140f)
              val radius = CornerRadius(24f, 24f)

              // Receivable bar (slot 0)
              val recLeft = (slotWidth - barWidth) / 2f
              if (selectedBar == DebtDirection.RECEIVABLE) {
                drawRoundRect(
                  color = selectedHighlight,
                  topLeft = Offset(recLeft - 10f, 0f),
                  size = Size(barWidth + 20f, size.height),
                  cornerRadius = CornerRadius(28f, 28f),
                )
              }
              val recHeight = (size.height * animatedReceivable).coerceAtLeast(if (receivableTarget > 0f) 10f else 0f)
              if (recHeight > 0f) {
                drawRoundRect(
                  brush =
                    Brush.verticalGradient(
                      colors = listOf(Color(0xFF0891B2), ReceivableGreen)
                    ),
                  topLeft = Offset(recLeft, size.height - recHeight),
                  size = Size(barWidth, recHeight),
                  cornerRadius = radius,
                )
              }

              // Payable bar (slot 1)
              val payLeft = slotWidth + (slotWidth - barWidth) / 2f
              if (selectedBar == DebtDirection.PAYABLE) {
                drawRoundRect(
                  color = selectedHighlight,
                  topLeft = Offset(payLeft - 10f, 0f),
                  size = Size(barWidth + 20f, size.height),
                  cornerRadius = CornerRadius(28f, 28f),
                )
              }
              val payHeight = (size.height * animatedPayable).coerceAtLeast(if (payableTarget > 0f) 10f else 0f)
              if (payHeight > 0f) {
                drawRoundRect(
                  brush =
                    Brush.verticalGradient(
                      colors = listOf(Color(0xFFE45847), PayableCrimson)
                    ),
                  topLeft = Offset(payLeft, size.height - payHeight),
                  size = Size(barWidth, payHeight),
                  cornerRadius = radius,
                )
              }
            }

            Row(modifier = Modifier.fillMaxSize()) {
              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxHeight()
                    .clickable {
                      selectedBar =
                        if (selectedBar == DebtDirection.RECEIVABLE) null
                        else DebtDirection.RECEIVABLE
                    }
                    .testTag("bar_receivable_hit"),
                contentAlignment = Alignment.BottomCenter,
              ) {
                Text(
                  text = "مستحق لك",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight =
                    if (selectedBar == DebtDirection.RECEIVABLE) FontWeight.Bold
                    else FontWeight.Medium,
                  color = ReceivableGreen,
                )
              }
              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxHeight()
                    .clickable {
                      selectedBar =
                        if (selectedBar == DebtDirection.PAYABLE) null
                        else DebtDirection.PAYABLE
                    }
                    .testTag("bar_payable_hit"),
                contentAlignment = Alignment.BottomCenter,
              ) {
                Text(
                  text = "عليك سداده",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight =
                    if (selectedBar == DebtDirection.PAYABLE) FontWeight.Bold
                    else FontWeight.Medium,
                  color = PayableCrimson,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DistributionDonutCard(summary: DebtStatisticsSummary) {
  val animatedRecShare by
    animateFloatAsState(
      targetValue = summary.receivableShare,
      animationSpec = spring(dampingRatio = 0.8f, stiffness = 240f),
      label = "donutReceivable",
    )
  val animatedPayShare by
    animateFloatAsState(
      targetValue = summary.payableShare,
      animationSpec = spring(dampingRatio = 0.8f, stiffness = 240f),
      label = "donutPayable",
    )
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth().testTag("distribution_donut_card"),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "توزيع النسبة المئوية",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Icon(
          imageVector = Icons.Filled.PieChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Box(
          modifier = Modifier.size(132.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokePx = 22.dp.toPx()
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Butt)

            drawArc(
              color = trackColor,
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = stroke,
            )

            if (summary.combinedTotal > 0.0) {
              val recSweep = 360f * animatedRecShare
              val paySweep = 360f * animatedPayShare
              if (recSweep > 0f) {
                drawArc(
                  color = ReceivableGreen,
                  startAngle = -90f,
                  sweepAngle = recSweep,
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style = stroke,
                )
              }
              if (paySweep > 0f) {
                drawArc(
                  color = PayableCrimson,
                  startAngle = -90f + recSweep,
                  sweepAngle = paySweep,
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style = stroke,
                )
              }
            }
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "الإجمالي",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = formatCompactAxisValue(summary.combinedTotal),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = summary.currency,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ShareBreakdownRow(
            label = "مستحق لك",
            percent = (summary.receivableShare * 100).toInt(),
            amountText = formatAmount(summary.receivableTotal, summary.currency),
            color = ReceivableGreen,
          )
          ShareBreakdownRow(
            label = "عليك سداده",
            percent = (summary.payableShare * 100).toInt(),
            amountText = formatAmount(summary.payableTotal, summary.currency),
            color = PayableCrimson,
          )
        }
      }
    }
  }
}

@Composable
private fun ShareBreakdownRow(
  label: String,
  percent: Int,
  amountText: String,
  color: Color,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.25f), MaterialTheme.shapes.small),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Column {
          Text(text = label, style = MaterialTheme.typography.labelLarge)
          Text(
            text = amountText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = "$percent%",
        style = MaterialTheme.typography.titleMedium,
        color = color,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun RepaymentProgressCard(summary: DebtStatisticsSummary) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = Modifier.fillMaxWidth().testTag("repayment_progress_card"),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = EmeraldPrimary,
        )
        Text(
          text = "مؤشر التحصيل والسداد (${summary.currency})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      ProgressIndicatorRow(
        title = "نسبة تحصيل مستحقاتك",
        paid = summary.receivablePaidTotal,
        original = summary.receivableOriginalTotal,
        currency = summary.currency,
        rate = summary.receivableCollectionRate,
        barColor = ReceivableGreen,
      )

      ProgressIndicatorRow(
        title = "نسبة سداد التزاماتك",
        paid = summary.payablePaidTotal,
        original = summary.payableOriginalTotal,
        currency = summary.currency,
        rate = summary.payableRepaymentRate,
        barColor = PayableCrimson,
      )
    }
  }
}

@Composable
private fun ProgressIndicatorRow(
  title: String,
  paid: Double,
  original: Double,
  currency: String,
  rate: Float,
  barColor: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = title, style = MaterialTheme.typography.labelLarge)
      Text(
        text = "${(rate * 100).toInt()}%",
        style = MaterialTheme.typography.labelLarge,
        color = barColor,
        fontWeight = FontWeight.Bold,
      )
    }
    LinearProgressIndicator(
      progress = { rate },
      color = barColor,
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
    )
    Text(
      text = "مسدّد ${formatAmount(paid, currency)} من أصل ${formatAmount(original, currency)}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EmptyStatisticsState(onNavigateToDebts: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.BarChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      }
      Text(
        text = "لا توجد بيانات مالية للعرض بعد",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "أضف أول دين أو مستحق مالي لعرض الرسوم البيانية والمقارنات التفصيلية هنا.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Button(onClick = onNavigateToDebts, modifier = Modifier.testTag("empty_stats_go_debts")) {
        Text("الانتقال إلى الديون")
      }
    }
  }
}

private fun computeNiceAxisMax(maxVal: Double): Double {
  if (maxVal <= 0.0) return 100.0
  val exp = kotlin.math.floor(log10(maxVal))
  val magnitude = 10.0.pow(exp)
  val fraction = maxVal / magnitude
  val niceFraction =
    when {
      fraction <= 1.0 -> 1.0
      fraction <= 1.2 -> 1.2
      fraction <= 1.5 -> 1.5
      fraction <= 2.0 -> 2.0
      fraction <= 2.5 -> 2.5
      fraction <= 3.0 -> 3.0
      fraction <= 4.0 -> 4.0
      fraction <= 5.0 -> 5.0
      fraction <= 8.0 -> 8.0
      else -> ceil(fraction)
    }
  return niceFraction * magnitude
}

private fun formatCompactAxisValue(value: Double): String {
  return when {
    value >= 1_000_000.0 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000.0 -> {
      val k = value / 1_000.0
      if (k % 1.0 == 0.0) "${k.toInt()}K" else String.format(java.util.Locale.US, "%.1fK", k)
    }
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", value)
  }
}

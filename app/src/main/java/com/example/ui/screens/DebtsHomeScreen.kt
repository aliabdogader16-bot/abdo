package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Debt
import com.example.data.model.DebtDirection
import com.example.ui.theme.ActionTileBg
import com.example.ui.theme.ActionTileBorder
import com.example.ui.theme.AvatarSoftBg
import com.example.ui.theme.AvatarSoftText
import com.example.ui.theme.CoolDivider
import com.example.ui.theme.OverdueAmber
import com.example.ui.theme.OverdueAmberContainer
import com.example.ui.theme.PayableCrimson
import com.example.ui.theme.ReceivableGreen
import com.example.ui.viewmodel.DebtFilter
import com.example.ui.viewmodel.DebtSortOrder
import com.example.ui.viewmodel.UiState
import com.example.util.DueUrgency
import com.example.util.computeDueStatus
import com.example.util.formatAmount

@Composable
fun DebtsHomeScreen(
  allDebtsState: UiState<List<Debt>>,
  filteredDebtsState: UiState<List<Debt>>,
  activeFilter: DebtFilter,
  activeSort: DebtSortOrder,
  searchQuery: String,
  onFilterChange: (DebtFilter) -> Unit,
  onSortChange: (DebtSortOrder) -> Unit,
  onSearchChange: (String) -> Unit,
  onDebtClick: (Debt) -> Unit,
  onEditDebt: (Debt) -> Unit = {},
  onDeleteDebt: (Debt) -> Unit = {},
  onAddDebtClick: () -> Unit,
  onOpenStatisticsClick: () -> Unit = {},
  onOpenRemindersClick: () -> Unit = {},
  onShowMessage: (String) -> Unit = {},
  onRetry: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val allDebts = (allDebtsState as? UiState.Success)?.data.orEmpty()
  var pendingDeleteDebt by remember { mutableStateOf<Debt?>(null) }
  var sortMenuExpanded by remember { mutableStateOf(false) }

  pendingDeleteDebt?.let { debtToDelete ->
    AlertDialog(
      onDismissRequest = { pendingDeleteDebt = null },
      title = { Text("تأكيد حذف الدين") },
      text = {
        Text(
          "هل تريد حذف سجل «${debtToDelete.creditorName}» (${formatAmount(debtToDelete.amount, debtToDelete.currency)}) من القائمة نهائياً؟"
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingDeleteDebt = null
            onDeleteDebt(debtToDelete)
          },
          modifier = Modifier.testTag("confirm_delete_debt_button"),
        ) {
          Text("حذف", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteDebt = null }) { Text("إلغاء") }
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SegmentedLedgerFilterTrack(
        activeFilter = activeFilter,
        onFilterChange = onFilterChange,
      )
    }

    item {
      CleanSummaryCard(
        debts = allDebts,
        onOpenRemindersClick = onOpenRemindersClick,
        onOpenStatisticsClick = onOpenStatisticsClick,
      )
    }

    item {
      val shownCount = (filteredDebtsState as? UiState.Success)?.data?.size ?: 0
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${activeFilter.sectionTitleAr} ($shownCount)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "الترتيب: ${activeSort.labelAr}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          placeholder = {
            Text(
              "البحث",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          leadingIcon = {
            Icon(
              Icons.Filled.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
          modifier = Modifier.weight(1f).testTag("search_debts_input"),
        )

        Box {
          SquareActionIconButton(
            icon = Icons.Filled.Tune,
            contentDescription = "فرز وترتيب السجلات",
            onClick = { sortMenuExpanded = true },
            modifier = Modifier.testTag("sort_menu_button"),
          )
          DropdownMenu(
            expanded = sortMenuExpanded,
            onDismissRequest = { sortMenuExpanded = false },
          ) {
            DebtSortOrder.entries.forEach { sort ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = sort.labelAr,
                    fontWeight = if (activeSort == sort) FontWeight.Bold else FontWeight.Normal,
                    color =
                      if (activeSort == sort) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurface,
                  )
                },
                onClick = {
                  sortMenuExpanded = false
                  onSortChange(sort)
                },
                modifier = Modifier.testTag("sort_chip_${sort.name.lowercase()}"),
              )
            }
          }
        }

        SquareActionIconButton(
          icon = Icons.Outlined.Share,
          contentDescription = "مشاركة ملخص الديون",
          onClick = {
            val active = allDebts.filter { !it.isSettled }
            if (active.isEmpty()) {
              onShowMessage("لا توجد ديون نشطة للمشاركة حالياً")
            } else {
              val summaryText = buildString {
                appendLine("ملخص دفتر الديون (${active.size} سجل نشط):")
                active.take(15).forEachIndexed { i, d ->
                  appendLine("${i + 1}. ${d.creditorName} • ${d.directionEnum.labelAr}: ${formatAmount(d.amount, d.currency)}")
                }
              }
              val intent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, summaryText)
                }
              runCatching {
                context.startActivity(Intent.createChooser(intent, "مشاركة ملخص الديون"))
              }.onFailure {
                onShowMessage("لا يوجد تطبيق متاح للمشاركة على هذا الجهاز")
              }
            }
          },
          modifier = Modifier.testTag("share_ledger_summary_button"),
        )
      }
    }

    when (filteredDebtsState) {
      is UiState.Loading -> {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      }
      is UiState.Error -> {
        item {
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
                text = filteredDebtsState.message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
              )
              Button(onClick = onRetry, modifier = Modifier.testTag("retry_debts_button")) {
                Text("إعادة المحاولة")
              }
            }
          }
        }
      }
      is UiState.Success -> {
        val debts = filteredDebtsState.data
        if (debts.isEmpty()) {
          item {
            EmptyDebtsState(
              hasAnyDebts = allDebts.isNotEmpty(),
              onAddDebtClick = onAddDebtClick,
            )
          }
        } else {
          itemsIndexed(debts, key = { _, item -> item.id }) { index, debt ->
            val shape: Shape =
              when {
                debts.size == 1 -> RoundedCornerShape(16.dp)
                index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                index == debts.lastIndex ->
                  RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                else -> RoundedCornerShape(0.dp)
              }
            AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = spring())) {
              DebtLedgerRow(
                debt = debt,
                shape = shape,
                showBottomDivider = index < debts.lastIndex,
                onClick = { onDebtClick(debt) },
                onEditClick = { onEditDebt(debt) },
                onDeleteClick = { pendingDeleteDebt = debt },
              )
            }
          }
        }
      }
    }
  }
}

private val DebtFilter.sectionTitleAr: String
  get() =
    when (this) {
      DebtFilter.ALL -> "جميع السجلات"
      DebtFilter.RECEIVABLE -> "العملاء (مستحق لي)"
      DebtFilter.PAYABLE -> "الموردين (عليّ سداده)"
      DebtFilter.SETTLED -> "السجلات المسدّدة"
    }

@Composable
private fun SegmentedLedgerFilterTrack(
  activeFilter: DebtFilter,
  onFilterChange: (DebtFilter) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val tabs =
        listOf(
          Triple(DebtFilter.ALL, "الكل", null),
          Triple(DebtFilter.RECEIVABLE, "مستحق لي", Icons.Filled.PersonOutline),
          Triple(DebtFilter.PAYABLE, "عليّ", Icons.Filled.Storefront),
          Triple(DebtFilter.SETTLED, "مسددة", Icons.Filled.CheckCircle),
        )
      tabs.forEach { (filter, label, icon) ->
        val selected = activeFilter == filter
        Box(
          modifier =
            Modifier.weight(1f)
              .then(
                if (selected) {
                  Modifier.shadow(2.dp, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                } else {
                  Modifier
                }
              )
              .clip(RoundedCornerShape(10.dp))
              .clickable { onFilterChange(filter) }
              .padding(vertical = 10.dp, horizontal = 4.dp)
              .testTag("filter_chip_${filter.name.lowercase()}"),
          contentAlignment = Alignment.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (icon != null) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color =
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CleanSummaryCard(
  debts: List<Debt>,
  onOpenRemindersClick: () -> Unit,
  onOpenStatisticsClick: () -> Unit,
) {
  val activeDebts = debts.filter { !it.isSettled }
  val availableCurrencies =
    remember(activeDebts) {
      activeDebts
        .groupBy { it.currency }
        .entries
        .sortedByDescending { it.value.size }
        .map { it.key }
        .ifEmpty { listOf("SAR") }
    }
  var selectedCurrency by remember(availableCurrencies) { mutableStateOf(availableCurrencies.first()) }
  val primaryCurrency =
    if (selectedCurrency in availableCurrencies) selectedCurrency else availableCurrencies.first()

  val totalReceivable =
    activeDebts
      .filter { it.directionEnum == DebtDirection.RECEIVABLE && it.currency == primaryCurrency }
      .sumOf { it.remainingAmount }
  val totalPayable =
    activeDebts
      .filter { it.directionEnum == DebtDirection.PAYABLE && it.currency == primaryCurrency }
      .sumOf { it.remainingAmount }
  val overdueCount =
    activeDebts.count { computeDueStatus(it).urgency == DueUrgency.OVERDUE }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    shadowElevation = 2.dp,
    modifier = Modifier.fillMaxWidth().testTag("financial_summary_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "مستحق لي",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = formatAmount(totalReceivable, primaryCurrency),
              style = MaterialTheme.typography.headlineSmall,
              color = ReceivableGreen,
              fontWeight = FontWeight.Bold,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "عليّ سداده",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = formatAmount(totalPayable, primaryCurrency),
              style = MaterialTheme.typography.headlineSmall,
              color = PayableCrimson,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          QuickSummaryTile(
            title = "التنبيهات",
            icon = Icons.Outlined.NotificationsActive,
            onClick = onOpenRemindersClick,
            modifier = Modifier.testTag("summary_tile_reminders"),
          )
          QuickSummaryTile(
            title = "التقارير",
            icon = Icons.Outlined.BarChart,
            onClick = onOpenStatisticsClick,
            modifier = Modifier.testTag("summary_tile_reports"),
          )
        }
      }

      if (availableCurrencies.size > 1 || overdueCount > 0) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (availableCurrencies.size > 1) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(50),
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .clickable {
                    val idx = availableCurrencies.indexOf(primaryCurrency)
                    selectedCurrency = availableCurrencies[(idx + 1) % availableCurrencies.size]
                  }
                  .testTag("switch_summary_currency_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.SwapHoriz,
                  contentDescription = "تبديل العملة",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "العملة: $primaryCurrency",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
          if (overdueCount > 0) {
            Surface(
              color = OverdueAmberContainer,
              shape = RoundedCornerShape(50),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Filled.WarningAmber,
                  contentDescription = null,
                  tint = OverdueAmber,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "$overdueCount متأخر",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF78350F),
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
private fun QuickSummaryTile(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isDark = isSystemInDarkTheme()
  val bg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else ActionTileBg
  val border = if (isDark) MaterialTheme.colorScheme.outline else ActionTileBorder

  Surface(
    color = bg,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, border),
    modifier = modifier.size(width = 78.dp, height = 78.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SquareActionIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

@Composable
private fun DebtLedgerRow(
  debt: Debt,
  shape: Shape,
  showBottomDivider: Boolean,
  onClick: () -> Unit,
  onEditClick: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  val isReceivable = debt.directionEnum == DebtDirection.RECEIVABLE
  val amountColor =
    when {
      debt.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
      isReceivable -> ReceivableGreen
      else -> PayableCrimson
    }
  val dueInfo = computeDueStatus(debt)
  val isDark = isSystemInDarkTheme()
  val avatarBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else AvatarSoftBg
  val avatarFg = if (isDark) MaterialTheme.colorScheme.onSurface else AvatarSoftText
  val dividerColor = if (isDark) MaterialTheme.colorScheme.outline else CoolDivider
  var menuExpanded by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = shape,
    modifier =
      Modifier.fillMaxWidth()
        .clip(shape)
        .clickable(onClick = onClick)
        .testTag("debt_card_${debt.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarBg),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = debt.personName.trim().firstOrNull()?.toString() ?: "د",
            style = MaterialTheme.typography.titleMedium,
            color = avatarFg,
            fontWeight = FontWeight.Bold,
          )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = debt.creditorName,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (debt.reminderEnabled && !debt.isSettled) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = "تنبيه مفعل",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
            }
          }
          Text(
            text = dueInfo.label,
            style = MaterialTheme.typography.bodySmall,
            color =
              if (dueInfo.urgency == DueUrgency.OVERDUE) PayableCrimson
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formatAmount(debt.amount, debt.currency),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              when {
                debt.isSettled -> "مسدّد"
                isReceivable -> "أعطيت"
                else -> "أخذت"
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(36.dp).testTag("debt_menu_${debt.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = "خيارات السجل",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
          ) {
            if (!debt.isSettled) {
              DropdownMenuItem(
                text = { Text("تعديل البيانات") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onEditClick()
                },
                modifier = Modifier.testTag("edit_debt_item_${debt.id}"),
              )
            }
            DropdownMenuItem(
              text = { Text("حذف من القائمة", color = MaterialTheme.colorScheme.error) },
              leadingIcon = {
                Icon(
                  Icons.Filled.DeleteOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
              },
              onClick = {
                menuExpanded = false
                onDeleteClick()
              },
              modifier = Modifier.testTag("delete_debt_item_${debt.id}"),
            )
          }
        }
      }

      if (showBottomDivider) {
        HorizontalDivider(
          color = dividerColor,
          modifier = Modifier.padding(start = 70.dp, end = 14.dp),
        )
      }
    }
  }
}

@Composable
private fun EmptyDebtsState(hasAnyDebts: Boolean, onAddDebtClick: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
  ) {
    Column(
      modifier = Modifier.padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.PersonOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(30.dp),
        )
      }
      Text(
        text = if (hasAnyDebts) "لا توجد سجلات مطابقة للبحث" else "ابدأ بتسجيل أول دين",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text =
          if (hasAnyDebts) "جرّب تغيير التبويب أو مسح نص البحث"
          else "سجّل المبالغ التي أعطيتها أو أخذتها مع مواعيد السداد لحفظ حقوق الجميع.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!hasAnyDebts) {
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onAddDebtClick, modifier = Modifier.testTag("empty_add_debt_button")) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("إضافة دين")
        }
      }
    }
  }
}

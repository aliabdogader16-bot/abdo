package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.Debt
import com.example.data.repository.DebtRepository
import com.example.notifications.DebtReminderReceiver
import com.example.notifications.DebtReminderScheduler
import com.example.ui.components.AddAmountSheet
import com.example.ui.components.AddEditDebtSheet
import com.example.ui.components.RecordPaymentSheet
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.DebtDetailScreen
import com.example.ui.screens.DebtsHomeScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.DuyunTheme
import com.example.ui.viewmodel.DebtViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.DueUrgency
import com.example.util.computeDueStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_STATISTICS = "statistics"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_DETAIL = "debt_detail/{debtId}"

private data class NavTabItem(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {
  private var initialDebtIdExtra by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    DebtReminderReceiver.ensureChannel(this)
    initialDebtIdExtra = intent?.getStringExtra(DebtReminderReceiver.EXTRA_DEBT_ID)

    setContent {
      DuyunTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          AppNavigation(
            initialDebtId = initialDebtIdExtra,
            onConsumeInitialDebt = { initialDebtIdExtra = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val debtId = intent.getStringExtra(DebtReminderReceiver.EXTRA_DEBT_ID)
    if (!debtId.isNullOrBlank()) {
      initialDebtIdExtra = debtId
    }
  }
}

@Composable
fun AppNavigation(
  auth: FirebaseAuth = Firebase.auth,
  initialDebtId: String? = null,
  onConsumeInitialDebt: () -> Unit = {},
) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    SignInScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      DuyunMainShell(
        currentUserId = user.uid,
        displayName = user.displayName ?: "",
        email = user.email ?: "",
        photoUrl = user.photoUrl?.toString(),
        initialDebtId = initialDebtId,
        onConsumeInitialDebt = onConsumeInitialDebt,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuyunMainShell(
  currentUserId: String,
  displayName: String,
  email: String,
  photoUrl: String?,
  initialDebtId: String?,
  onConsumeInitialDebt: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: DebtViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            DebtViewModel(DebtRepository(db), currentUserId)
          }
        },
    )

  val allDebtsState by viewModel.allDebtsState.collectAsStateWithLifecycle()
  val filteredDebtsState by viewModel.filteredDebtsState.collectAsStateWithLifecycle()
  val activeFilter by viewModel.filter.collectAsStateWithLifecycle()
  val activeSort by viewModel.sortOrder.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedDebtState by viewModel.selectedDebtState.collectAsStateWithLifecycle()
  val selectedTransactionsState by viewModel.selectedTransactionsState.collectAsStateWithLifecycle()
  val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  var showAddDebtSheet by remember { mutableStateOf(false) }
  var editingDebt by remember { mutableStateOf<Debt?>(null) }
  var paymentDebt by remember { mutableStateOf<Debt?>(null) }
  var addAmountDebt by remember { mutableStateOf<Debt?>(null) }

  LaunchedEffect(Unit) {
    viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
  }

  LaunchedEffect(allDebtsState) {
    val debts = (allDebtsState as? UiState.Success)?.data
    if (debts != null) {
      DebtReminderScheduler.syncReminders(context, debts)
    }
  }

  LaunchedEffect(initialDebtId) {
    if (!initialDebtId.isNullOrBlank()) {
      viewModel.selectDebt(initialDebtId)
      navController.navigate("debt_detail/$initialDebtId")
      onConsumeInitialDebt()
    }
  }

  val allDebts = (allDebtsState as? UiState.Success)?.data.orEmpty()
  val urgentAlertsCount =
    remember(allDebts) {
      allDebts.count {
        val urgency = computeDueStatus(it).urgency
        urgency == DueUrgency.OVERDUE || urgency == DueUrgency.DUE_SOON
      }
    }

  val navItems =
    remember {
      listOf(
        NavTabItem(
          route = ROUTE_HOME,
          labelRes = R.string.nav_debts,
          selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
          unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
          testTag = "nav_tab_home",
        ),
        NavTabItem(
          route = ROUTE_STATISTICS,
          labelRes = R.string.nav_statistics,
          selectedIcon = Icons.Filled.BarChart,
          unselectedIcon = Icons.Outlined.BarChart,
          testTag = "nav_tab_statistics",
        ),
        NavTabItem(
          route = ROUTE_REMINDERS,
          labelRes = R.string.nav_reminders,
          selectedIcon = Icons.Filled.NotificationsActive,
          unselectedIcon = Icons.Outlined.Notifications,
          testTag = "nav_tab_reminders",
        ),
        NavTabItem(
          route = ROUTE_ACCOUNT,
          labelRes = R.string.nav_account,
          selectedIcon = Icons.Filled.GridView,
          unselectedIcon = Icons.Outlined.GridView,
          testTag = "nav_tab_account",
        ),
      )
    }

  val isTopLevelRoute =
    currentRoute == ROUTE_HOME ||
      currentRoute == ROUTE_STATISTICS ||
      currentRoute == ROUTE_REMINDERS ||
      currentRoute == ROUTE_ACCOUNT

  if (showAddDebtSheet || editingDebt != null) {
    AddEditDebtSheet(
      existingDebt = editingDebt,
      isSaving = isActionLoading,
      onDismiss = {
        showAddDebtSheet = false
        editingDebt = null
      },
      onSaveNew = { name, phone, direction, currency, amount, dueMillis, reminderEnabled, notes ->
        viewModel.createDebt(
          personName = name,
          personPhone = phone,
          direction = direction,
          currency = currency,
          amount = amount,
          dueDateMillis = dueMillis,
          reminderEnabled = reminderEnabled,
          notes = notes,
          onSuccess = { showAddDebtSheet = false },
        )
      },
      onSaveEdit = { debtId, name, phone, dueMillis, reminderEnabled, notes ->
        viewModel.updateDebtDetails(
          debtId = debtId,
          personName = name,
          personPhone = phone,
          dueDateMillis = dueMillis,
          reminderEnabled = reminderEnabled,
          notes = notes,
          onSuccess = { editingDebt = null },
        )
      },
    )
  }

  paymentDebt?.let { debt ->
    RecordPaymentSheet(
      debt = debt,
      isSaving = isActionLoading,
      onDismiss = { paymentDebt = null },
      onConfirmPayment = { amount, note ->
        viewModel.recordPayment(debt, amount, note) { paymentDebt = null }
      },
    )
  }

  addAmountDebt?.let { debt ->
    AddAmountSheet(
      debt = debt,
      isSaving = isActionLoading,
      onDismiss = { addAmountDebt = null },
      onConfirmAddition = { amount, note ->
        viewModel.addAmountToDebt(debt, amount, note) { addAmountDebt = null }
      },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        when (currentRoute) {
          ROUTE_HOME -> {
            Surface(color = MaterialTheme.colorScheme.surface) {
              Column {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .statusBarsPadding()
                      .padding(horizontal = 16.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(22.dp),
                    )
                    Text(
                      text = displayName.ifBlank { stringResource(R.string.app_name) },
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                      color = MaterialTheme.colorScheme.surface,
                      shape = CircleShape,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                      modifier =
                        Modifier.size(40.dp)
                          .clip(CircleShape)
                          .clickable {
                            navController.navigate(ROUTE_REMINDERS) {
                              popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                              }
                              launchSingleTop = true
                              restoreState = true
                            }
                          }
                          .testTag("header_reminders_shortcut"),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        if (urgentAlertsCount > 0) {
                          BadgedBox(badge = { Badge { Text(urgentAlertsCount.toString()) } }) {
                            Icon(
                              imageVector = Icons.Outlined.Notifications,
                              contentDescription = stringResource(R.string.nav_reminders),
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(20.dp),
                            )
                          }
                        } else {
                          Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.nav_reminders),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                          )
                        }
                      }
                    }

                    Surface(
                      color = MaterialTheme.colorScheme.surface,
                      shape = CircleShape,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                      modifier =
                        Modifier.size(40.dp)
                          .clip(CircleShape)
                          .clickable {
                            navController.navigate(ROUTE_STATISTICS) {
                              popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                              }
                              launchSingleTop = true
                              restoreState = true
                            }
                          }
                          .testTag("header_statistics_shortcut"),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Outlined.BarChart,
                          contentDescription = stringResource(R.string.nav_statistics),
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }
                  }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
              }
            }
          }
          ROUTE_STATISTICS,
          ROUTE_REMINDERS -> {
            Column {
              CenterAlignedTopAppBar(
                title = {
                  val title =
                    when (currentRoute) {
                      ROUTE_STATISTICS -> stringResource(R.string.nav_statistics)
                      else -> stringResource(R.string.nav_reminders)
                    }
                  Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                },
                colors =
                  TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                  ),
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            }
          }
        }
      },
      bottomBar = {
        if (isTopLevelRoute && !useNavigationRail) {
          Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              tonalElevation = 0.dp,
              windowInsets = WindowInsets.navigationBars,
            ) {
              navItems.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(item.route) {
                      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.primary,
                      selectedTextColor = MaterialTheme.colorScheme.primary,
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                  icon = {
                    if (item.route == ROUTE_REMINDERS && urgentAlertsCount > 0) {
                      BadgedBox(badge = { Badge { Text(urgentAlertsCount.toString()) } }) {
                        Icon(
                          imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                          contentDescription = stringResource(item.labelRes),
                        )
                      }
                    } else {
                      Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                      )
                    }
                  },
                  label = {
                    Text(
                      text = stringResource(item.labelRes),
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                      maxLines = 1,
                    )
                  },
                  modifier = Modifier.testTag(item.testTag),
                )
              }
            }
          }
        }
      },
      floatingActionButton = {
        if (currentRoute == ROUTE_HOME) {
          ExtendedFloatingActionButton(
            onClick = { showAddDebtSheet = true },
            shape = RoundedCornerShape(50),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = {
              Text(
                text = stringResource(R.string.add_debt),
                fontWeight = FontWeight.Bold,
              )
            },
            modifier = Modifier.testTag("fab_add_debt"),
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isTopLevelRoute && useNavigationRail) {
          NavigationRail(
            header = {
              FloatingActionButton(
                onClick = { showAddDebtSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_debt))
              }
            }
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                icon = {
                  if (item.route == ROUTE_REMINDERS && urgentAlertsCount > 0) {
                    BadgedBox(badge = { Badge { Text(urgentAlertsCount.toString()) } }) {
                      Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                      )
                    }
                  } else {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  }
                },
                label = { Text(stringResource(item.labelRes)) },
              )
            }
          }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
          ) {
            composable(ROUTE_HOME) {
              DebtsHomeScreen(
                allDebtsState = allDebtsState,
                filteredDebtsState = filteredDebtsState,
                activeFilter = activeFilter,
                activeSort = activeSort,
                searchQuery = searchQuery,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSortOrder,
                onSearchChange = viewModel::setSearchQuery,
                onDebtClick = { debt ->
                  viewModel.selectDebt(debt.id)
                  navController.navigate("debt_detail/${debt.id}")
                },
                onEditDebt = { debt -> editingDebt = debt },
                onDeleteDebt = { debt ->
                  DebtReminderScheduler.cancelReminder(context, debt.id)
                  viewModel.deleteDebt(debt.id)
                },
                onAddDebtClick = { showAddDebtSheet = true },
                onOpenStatisticsClick = {
                  navController.navigate(ROUTE_STATISTICS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onOpenRemindersClick = {
                  navController.navigate(ROUTE_REMINDERS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onShowMessage = viewModel::emitMessage,
                onRetry = viewModel::retryObserve,
              )
            }

            composable(ROUTE_STATISTICS) {
              StatisticsScreen(
                allDebtsState = allDebtsState,
                onNavigateToDebts = {
                  navController.navigate(ROUTE_HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onRetry = viewModel::retryObserve,
              )
            }

            composable(ROUTE_REMINDERS) {
              RemindersScreen(
                allDebtsState = allDebtsState,
                onDebtClick = { debt ->
                  viewModel.selectDebt(debt.id)
                  navController.navigate("debt_detail/${debt.id}")
                },
                onShowMessage = viewModel::emitMessage,
              )
            }

            composable(ROUTE_ACCOUNT) {
              AccountScreen(
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
                allDebtsState = allDebtsState,
                onSignOut = {
                  signOutUser(
                    credentialManager = credentialManager,
                    scope = coroutineScope,
                  )
                },
                onOpenStatistics = {
                  navController.navigate(ROUTE_STATISTICS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onOpenReminders = {
                  navController.navigate(ROUTE_REMINDERS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onShowMessage = viewModel::emitMessage,
              )
            }

            composable(
              route = ROUTE_DETAIL,
              arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
            ) { backStackEntry ->
              val debtId = backStackEntry.arguments?.getString("debtId")
              LaunchedEffect(debtId) { viewModel.selectDebt(debtId) }

              DebtDetailScreen(
                debtState = selectedDebtState,
                transactionsState = selectedTransactionsState,
                ownerName = displayName,
                onBack = {
                  viewModel.selectDebt(null)
                  navController.popBackStack()
                },
                onRecordPaymentClick = { debt -> paymentDebt = debt },
                onAddAmountClick = { debt -> addAmountDebt = debt },
                onEditClick = { debt -> editingDebt = debt },
                onDeleteConfirm = { debt ->
                  DebtReminderScheduler.cancelReminder(context, debt.id)
                  viewModel.deleteDebt(debt.id) {
                    viewModel.selectDebt(null)
                    navController.popBackStack()
                  }
                },
                onShowMessage = viewModel::emitMessage,
              )
            }
          }
        }
      }
    }
  }
}

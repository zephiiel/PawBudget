package com.zephiiel.pawbudget.nativeapp

import android.net.Uri
import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Locale.Builder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val Ink = Color(0xFF4B352D)
private val Forest = Color(0xFF4D5C51)
private val Sage = Color(0xFFACB89F)
private val Paper = Color(0xFFF7F5ED)
private val Soft = Color(0xFFEDF0E6)
private val Gold = Color(0xFFE9A934)
private val ChartColors = listOf(Ink, Forest, Sage, Gold, Color(0xFFD9C8A8))
private val PhilippinesLocale = Builder().setLanguage("en").setRegion("PH").build()
private val Currency = NumberFormat.getCurrencyInstance(PhilippinesLocale)
private val DateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", PhilippinesLocale)
private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", PhilippinesLocale)

private enum class Page(val label: String, val symbol: String) {
    Overview("Overview", "◫"),
    Transactions("Transactions", "≡"),
    Pending("Pending", "◷"),
    Budgets("Budgets", "▤"),
    Todo("To-do", "✓"),
    Settings("Settings", "⚙"),
}

private data class TransactionFilter(val label: String, val matches: (Transaction) -> Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.WHITE
        val store = TrackerStore(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = Sage,
                    background = Paper,
                    surface = Color(0xFFFFFEFA),
                    onSurface = Ink,
                    onBackground = Ink,
                    outline = Color(0xFFE6DFD2),
                ),
            ) {
                PawBudgetApp(store)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PawBudgetApp(store: TrackerStore) {
    val context = LocalContext.current
    val loadResult = remember { runCatching { store.load() } }
    if (loadResult.isFailure) {
        ErrorScreen(loadResult.exceptionOrNull()?.message ?: "Could not load PawBudget data.")
        return
    }
    var data by remember { mutableStateOf(loadResult.getOrThrow()) }
    var page by remember { mutableStateOf(Page.Overview) }
    var selectedMonth by remember { mutableStateOf(cycleMonth(LocalDate.now(), data.cycleStartDay)) }
    var status by remember { mutableStateOf("All changes saved") }
    var filter by remember { mutableStateOf<TransactionFilter?>(null) }
    var transactionDialog by remember { mutableStateOf(false) }
    var editedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    var editedBudget by remember { mutableStateOf<Budget?>(null) }
    var todoDialog by remember { mutableStateOf(false) }
    var editedTodo by remember { mutableStateOf<PeriodTodo?>(null) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<TrackerData?>(null) }
    var selectedPendingIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTodoIds by remember { mutableStateOf(setOf<String>()) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var availableUpdate by remember { mutableStateOf<NativeUpdate?>(null) }
    var updateMessage by remember { mutableStateOf("(Up-to-date)") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<UpdateDownloadProgress?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun checkForUpdates() {
        isCheckingUpdate = true
        availableUpdate = null
        updateMessage = "Checking GitHub for updates…"
        try {
            val result = checkForNativeUpdate(BuildConfig.VERSION_NAME)
            availableUpdate = result.update
            updateMessage = result.message
        } catch (error: Exception) {
            updateMessage = "Could not check for updates. ${error.message ?: "Check your internet connection and try again."}"
        } finally {
            isCheckingUpdate = false
        }
    }

    fun persist(next: TrackerData): Boolean {
        return try {
            store.save(next)
            data = next
            status = "All changes saved"
            true
        } catch (error: Exception) {
            status = error.message ?: "Could not save changes."
            false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) {
            status = "Backup export cancelled."
        } else {
            runCatching {
                val backup = JSONObject()
                    .put("format", "ledger-monthly-tracker")
                    .put("version", 1)
                    .put("exportedAt", java.time.Instant.now().toString())
                    .put("data", data.toJson())
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }
                    ?: error("Android could not open the selected file.")
            }.onSuccess { status = "Backup saved to the selected location." }
                .onFailure { status = "Could not export backup: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val contents = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Android could not read the selected file.")
                TrackerData.fromBackup(JSONObject(contents))
            }.onSuccess {
                pendingImport = it
                showImportConfirmation = true
            }.onFailure {
                status = "Could not import backup: ${it.message}"
            }
        }
    }

    val periodTransactions = remember(data.transactions, selectedMonth, data.cycleStartDay) {
        val start = cycleStartDate(selectedMonth, data.cycleStartDay)
        val end = periodEnd(selectedMonth, data.cycleStartDay)
        data.transactions.filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            date != null && date >= start && date <= end
        }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Paper).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (page == Page.Settings || (page == Page.Transactions && filter != null)) {
                        IconButton(onClick = {
                            filter = null
                            page = Page.Overview
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to overview")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (page != Page.Settings) {
                                Image(
                                    painter = painterResource(R.drawable.pawbudget_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).padding(end = 8.dp),
                                )
                            }
                            Text(if (page == Page.Settings) "Settings & lists" else "PawBudget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (page != Page.Settings) {
                        IconButton(onClick = { page = Page.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings and lists")
                        }
                    }
                }
                if (page != Page.Settings) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(onClick = {
                            selectedMonth = selectedMonth.minusMonths(1)
                            selectedPendingIds = emptySet()
                            selectedTodoIds = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous billing period")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(190.dp)) {
                            Text(selectedMonth.format(MonthFormat), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${cycleStartDate(selectedMonth, data.cycleStartDay).format(DateFormat)} – ${periodEnd(selectedMonth, data.cycleStartDay).format(DateFormat)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                        }
                        IconButton(onClick = {
                            selectedMonth = selectedMonth.plusMonths(1)
                            selectedPendingIds = emptySet()
                            selectedTodoIds = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next billing period")
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (page != Page.Settings) {
                NavigationBar(containerColor = Color(0xFFFFFEFA)) {
                    listOf(Page.Overview, Page.Transactions, Page.Pending, Page.Budgets, Page.Todo).forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = {
                                page = item
                                if (item != Page.Transactions) filter = null
                            },
                            icon = { Text(item.symbol, fontWeight = FontWeight.Bold) },
                            label = { Text(item.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when (page) {
                Page.Overview, Page.Transactions -> ExtendedFloatingActionButton(
                    onClick = {
                        editedTransaction = null
                        transactionDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Transaction") },
                    containerColor = Forest,
                    contentColor = Color.White,
                )
                Page.Budgets -> ExtendedFloatingActionButton(
                    onClick = { editedBudget = null; budgetDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Budget") },
                    containerColor = Forest,
                    contentColor = Color.White,
                )
                Page.Todo -> ExtendedFloatingActionButton(
                    onClick = { editedTodo = null; todoDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Task") },
                    containerColor = Forest,
                    contentColor = Color.White,
                )
                Page.Pending, Page.Settings -> Unit
            }
        },
        containerColor = Paper,
    ) { padding ->
        when (page) {
            Page.Overview -> OverviewPage(
                data, periodTransactions, selectedMonth,
                onCategory = { category -> filter = TransactionFilter(category) { it.category == category }; page = Page.Transactions },
                onAccount = { account -> filter = TransactionFilter(account) { it.account == account }; page = Page.Transactions },
                modifier = Modifier.padding(padding),
            )
            Page.Transactions -> TransactionsPage(
                periodTransactions, filter,
                onClearFilter = { filter = null },
                onEdit = { editedTransaction = it; transactionDialog = true },
                onDelete = { item ->
                    confirmation = "Delete \"${item.description}\"?" to {
                        persist(data.copy(transactions = data.transactions.filterNot { it.id == item.id }))
                    }
                },
                modifier = Modifier.padding(padding),
            )
            Page.Pending -> PendingPage(
                periodTransactions,
                selectedPendingIds,
                onSelect = { id, selected -> selectedPendingIds = if (selected) selectedPendingIds + id else selectedPendingIds - id },
                onClearSelection = { selectedPendingIds = emptySet() },
                onSetPaid = { id, paid ->
                    persist(data.copy(transactions = data.transactions.map {
                        if (it.id == id && it.paymentType == "credit") it.copy(isPaid = paid) else it
                    }))
                },
                onSetSelectedPaid = {
                    val chosen = periodTransactions.filter { it.id in selectedPendingIds && it.paymentType == "credit" }
                    val markPaid = !chosen.all { it.isPaid }
                    val ids = chosen.map { it.id }.toSet()
                    if (ids.isNotEmpty() && persist(data.copy(transactions = data.transactions.map {
                            if (it.id in ids) it.copy(isPaid = markPaid) else it
                        }))) selectedPendingIds = emptySet()
                },
                modifier = Modifier.padding(padding),
            )
            Page.Budgets -> BudgetsPage(
                data.budgets.filter { it.periodStart == periodStartLabel(selectedMonth, data.cycleStartDay) },
                onEdit = { editedBudget = it; budgetDialog = true },
                onDelete = { budget ->
                    confirmation = "Delete the budget from ${budget.person}?" to {
                        persist(data.copy(budgets = data.budgets.filterNot { it.id == budget.id }))
                    }
                },
                modifier = Modifier.padding(padding),
            )
            Page.Todo -> TodoPage(
                data.todos.filter { it.periodStart == periodStartLabel(selectedMonth, data.cycleStartDay) },
                selectedIds = selectedTodoIds,
                remainingBudget = data.budgets
                    .filter { it.periodStart == periodStartLabel(selectedMonth, data.cycleStartDay) }
                    .sumOf { it.amount } - periodTransactions.sumOf { it.amount },
                onSelect = { id, selected -> selectedTodoIds = if (selected) selectedTodoIds + id else selectedTodoIds - id },
                onClearSelection = { selectedTodoIds = emptySet() },
                onSetSelectedCompleted = {
                    val todos = data.todos.filter {
                        it.periodStart == periodStartLabel(selectedMonth, data.cycleStartDay) && it.id in selectedTodoIds
                    }
                    val completed = !todos.all { it.completed }
                    val ids = todos.map { it.id }.toSet()
                    if (ids.isNotEmpty() && persist(data.copy(todos = data.todos.map {
                            if (it.id in ids) it.copy(completed = completed) else it
                        }))) selectedTodoIds = emptySet()
                },
                onEdit = { editedTodo = it; todoDialog = true },
                onDelete = { todo -> persist(data.copy(todos = data.todos.filterNot { it.id == todo.id })) },
                modifier = Modifier.padding(padding),
            )
            Page.Settings -> SettingsPage(
                data = data,
                status = status,
                onCycleDay = { day ->
                    val todos = data.todos.map { todo ->
                        val remappedStart = periodStartFor(LocalDate.parse(todo.periodStart), day).toString()
                        todo.copy(periodStart = remappedStart)
                    }
                    if (persist(data.copy(cycleStartDay = day, todos = todos))) {
                        selectedMonth = cycleMonth(LocalDate.now(), day)
                    }
                },
                onAddCategory = { value -> addSettingOption(value, data.categories, "category", { next -> data.copy(categories = next) }, ::persist) },
                onRemoveCategory = { value -> persist(data.copy(categories = data.categories.filterNot { it == value })) },
                onAddAccount = { value -> addSettingOption(value, data.accounts, "account", { next -> data.copy(accounts = next) }, ::persist) },
                onRemoveAccount = { value -> persist(data.copy(accounts = data.accounts.filterNot { it == value })) },
                onExport = {
                    exportLauncher.launch("pawbudget-backup-${LocalDate.now()}.json")
                },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                currentVersion = BuildConfig.VERSION_NAME,
                updateMessage = updateMessage,
                availableUpdate = availableUpdate,
                isCheckingUpdate = isCheckingUpdate,
                isDownloadingUpdate = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                onCheckUpdates = { coroutineScope.launch { checkForUpdates() } },
                onInstallUpdate = {
                    val update = availableUpdate ?: return@SettingsPage
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        val permissionIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(permissionIntent)
                        updateMessage = "Allow PawBudget to install apps in Android settings, then try again."
                    } else {
                        isDownloadingUpdate = true
                        downloadProgress = null
                        updateMessage = "Downloading version ${update.version}…"
                        coroutineScope.launch {
                            try {
                                downloadAndOpenNativeUpdate(context, update) { progress ->
                                    coroutineScope.launch { downloadProgress = progress }
                                }
                                updateMessage = "The Android installer opened. Follow its prompts to finish updating."
                            } catch (error: Exception) {
                                updateMessage = error.message ?: "Could not install the update. Try again."
                            } finally {
                                isDownloadingUpdate = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    confirmation?.let { (message, confirmAction) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Confirm deletion") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { confirmAction(); confirmation = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }

    if (transactionDialog) {
        TransactionEditor(
            initial = editedTransaction,
            categories = data.categories,
            accounts = data.accounts,
            defaultDate = LocalDate.now().takeIf {
                it >= cycleStartDate(selectedMonth, data.cycleStartDay) &&
                    it <= periodEnd(selectedMonth, data.cycleStartDay)
            }?.toString() ?: cycleStartDate(selectedMonth, data.cycleStartDay).toString(),
            onDismiss = { transactionDialog = false },
            onSave = { date, description, amount, category, account, paymentType ->
                val existing = editedTransaction
                val transaction = Transaction(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    date = date,
                    description = description.trim(),
                    category = category,
                    amount = amount,
                    account = account,
                    paymentType = paymentType,
                    isPaid = paymentType == "paid" || (existing?.paymentType == "credit" && existing.isPaid),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                val transactions = if (existing == null) data.transactions + transaction else
                    data.transactions.map { if (it.id == existing.id) transaction else it }
                if (persist(data.copy(transactions = transactions))) transactionDialog = false
            },
        )
    }
    if (budgetDialog) {
        BudgetEditor(
            initial = editedBudget,
            defaultPeriod = periodStartLabel(selectedMonth, data.cycleStartDay),
            onDismiss = { budgetDialog = false },
            onSave = { person, amount ->
                val existing = editedBudget
                val budget = Budget(existing?.id ?: UUID.randomUUID().toString(), existing?.periodStart ?: periodStartLabel(selectedMonth, data.cycleStartDay), person.trim(), amount)
                val budgets = if (existing == null) data.budgets + budget else data.budgets.map { if (it.id == existing.id) budget else it }
                if (persist(data.copy(budgets = budgets))) budgetDialog = false
            },
        )
    }
    if (todoDialog) {
        TodoEditor(
            initial = editedTodo,
            onDismiss = { todoDialog = false },
            onSave = { title, amount ->
                val existing = editedTodo
                val todo = PeriodTodo(
                    existing?.id ?: UUID.randomUUID().toString(),
                    title.trim(),
                    existing?.completed ?: false,
                    existing?.periodStart ?: periodStartLabel(selectedMonth, data.cycleStartDay),
                    amount,
                )
                val todos = if (existing == null) data.todos + todo else data.todos.map { if (it.id == existing.id) todo else it }
                if (persist(data.copy(todos = todos))) todoDialog = false
            },
        )
    }
    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false; pendingImport = null },
            title = { Text("Replace tracker data?") },
            text = { Text("Importing this backup replaces all PawBudget data currently saved in this app. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport?.let { imported ->
                        if (persist(imported)) {
                            selectedMonth = cycleMonth(LocalDate.now(), imported.cycleStartDay)
                            filter = null
                            selectedPendingIds = emptySet()
                            selectedTodoIds = emptySet()
                            page = Page.Overview
                            status = "Backup imported. This app now has the selected file's data."
                        }
                    }
                    showImportConfirmation = false
                    pendingImport = null
                }) { Text("Replace data") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false; pendingImport = null }) { Text("Cancel") }
            },
        )
    }
}

private fun addSettingOption(
    value: String,
    existing: List<String>,
    label: String,
    update: (List<String>) -> TrackerData,
    persist: (TrackerData) -> Boolean,
) {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return
    if (existing.any { it.equals(trimmed, ignoreCase = true) }) return
    persist(update((existing + trimmed).sortedBy { it.lowercase(Locale.ROOT) }))
}

@Composable
private fun OverviewPage(
    data: TrackerData,
    transactions: List<Transaction>,
    month: YearMonth,
    onCategory: (String) -> Unit,
    onAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = transactions.sumOf { it.amount }
    val paid = transactions.filter { it.paymentType == "paid" }.sumOf { it.amount }
    val credit = transactions.filter { it.paymentType == "credit" }.sumOf { it.amount }
    val budgets = data.budgets.filter { it.periodStart == periodStartLabel(month, data.cycleStartDay) }
    val budgetTotal = budgets.sumOf { it.amount }
    val remaining = budgetTotal - total
    val categoryTotals = transactions.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }
    val accountTotals = transactions.groupBy { it.account }.mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("YOUR MONEY, IN FOCUS", style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                Text("Monthly overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("A clear picture of where your money went this billing period.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(
                        "Budget remaining",
                        money(remaining),
                        "${money(budgetTotal)} budget · ${money(total)} used",
                        Forest,
                        Modifier.weight(1f),
                    )
                    SummaryCard(
                        "Total used",
                        money(total),
                        "Paid and credit spending",
                        Ink,
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(
                        "Paid directly",
                        money(paid),
                        "Debit, cash, and paid purchases",
                        Color(0xFFFFFEFA),
                        Modifier.weight(1f),
                    )
                    SummaryCard(
                        "Credit used",
                        money(credit),
                        "Charged to your credit accounts",
                        Sage,
                        Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            SectionTitle("Spending by category")
            if (categoryTotals.isEmpty()) EmptyMessage("No spending for this billing period yet.")
            else {
                CardBlock {
                    Box(contentAlignment = Alignment.Center) {
                        DonutChart(categoryTotals)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(money(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                Spacer(Modifier.height(8.dp))
                    categoryTotals.forEachIndexed { index, (name, amount) ->
                        ChartBar(name, amount, categoryTotals.maxOf { it.second }, ChartColors[index % ChartColors.size], onClick = { onCategory(name) })
                    }
                }
            }
        }
        item {
            SectionTitle("Spending by account")
            if (accountTotals.isEmpty()) EmptyMessage("No account activity for this billing period yet.")
            else CardBlock {
                accountTotals.forEachIndexed { index, (name, amount) ->
                    ChartBar(name, amount, accountTotals.maxOf { it.second }, ChartColors[(index + 1) % ChartColors.size], onClick = { onAccount(name) })
                }
            }
        }
        item {
            val year = LocalDate.now().year
            SectionTitle("Spending this year $year")
            val monthly = (1..12).map { monthNumber ->
                val chartMonth = YearMonth.of(year, monthNumber).let { if (data.cycleStartDay == 1) it else it.minusMonths(1) }
                transactionsForYearPeriod(data.transactions, chartMonth, data.cycleStartDay).sumOf { it.amount }
            }
            CardBlock {
                Row(Modifier.fillMaxWidth().height(126.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    monthly.forEachIndexed { index, amount ->
                        val barFraction = if (monthly.maxOrNull() == 0.0) 0.03f else (amount / monthly.maxOrNull()!!).toFloat().coerceAtLeast(0.03f)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth(0.66f).fillMaxHeight(barFraction).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (index % 2 == 0) Forest else Sage))
                            }
                            Text(
                                YearMonth.of(year, index + 1).month.name.take(3),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.Gray,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun transactionsForYearPeriod(transactions: List<Transaction>, month: YearMonth, day: Int): List<Transaction> {
    val start = cycleStartDate(month, day)
    val end = periodEnd(month, day)
    return transactions.filter {
        val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
        date != null && date >= start && date <= end
    }
}

@Composable
private fun SummaryCard(title: String, amount: String, caption: String, background: Color, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().height(124.dp),
        colors = CardDefaults.cardColors(containerColor = background, contentColor = if (background == Ink || background == Forest) Color.White else Ink),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp), maxLines = 1, softWrap = false)
            Text(caption, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), color = if (background == Ink || background == Forest) Color.White.copy(alpha = 0.78f) else Color.Gray, maxLines = 2)
        }
    }
}

@Composable
private fun DonutChart(totals: List<Pair<String, Double>>) {
    val sum = totals.sumOf { it.second }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val diameter = size.height * 0.84f
        val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var startAngle = -90f
        totals.forEachIndexed { index, (_, amount) ->
            val sweep = (amount / sum * 360.0).toFloat()
            drawArc(
                color = ChartColors[index % ChartColors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun ChartBar(name: String, amount: Double, maximum: Double, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(money(amount), style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(7.dp).clip(CircleShape).background(Color(0xFFE9E6DC))) {
            Box(Modifier.fillMaxWidth((amount / maximum).toFloat().coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun TransactionsPage(
    transactions: List<Transaction>,
    filter: TransactionFilter?,
    onClearFilter: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = if (filter == null) transactions else transactions.filter(filter.matches)
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            SectionTitle("All transactions")
            filter?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtered: ${it.label}", color = Forest, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearFilter) { Text("Clear") }
                }
            }
        }
        if (visible.isEmpty()) item { EmptyMessage(if (filter == null) "No transactions in this billing period." else "No matching transactions.") }
        items(visible, key = { it.id }) { transaction ->
            TransactionCard(transaction, onEdit, onDelete)
        }
    }
}

@Composable
private fun TransactionCard(transaction: Transaction, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transaction.description, fontWeight = FontWeight.SemiBold)
                Text("${transaction.category} · ${transaction.account}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    "${displayDate(transaction.date)} · ${if (transaction.paymentType == "credit") "Credit${if (transaction.isPaid) " · Paid" else " · Pending"}" else "Paid"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forest,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(money(transaction.amount), fontWeight = FontWeight.SemiBold)
                Row {
                    IconButton(onClick = { onEdit(transaction) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${transaction.description}", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(transaction) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${transaction.description}", tint = Color(0xFF9D5145), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPage(
    transactions: List<Transaction>,
    selectedIds: Set<String>,
    onSelect: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSetPaid: (String, Boolean) -> Unit,
    onSetSelectedPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unpaid = transactions.filter { it.paymentType == "credit" && !it.isPaid }
    val paid = transactions.filter { it.paymentType == "credit" && it.isPaid }
    val chosen = unpaid.filter { it.id in selectedIds } + paid.filter { it.id in selectedIds }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            SectionTitle("Pending payments")
            Text("${unpaid.size} unpaid · ${money(unpaid.sumOf { it.amount })}", color = Color.Gray)
        }
        if (chosen.isNotEmpty()) {
            item {
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${chosen.size} selected · ${money(chosen.sumOf { it.amount })}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearSelection) { Text("Deselect All") }
                    }
                    Button(onClick = onSetSelectedPaid, modifier = Modifier.padding(top = 8.dp)) {
                        Text(if (chosen.all { it.isPaid }) "Mark selected unpaid" else "Mark selected paid")
                    }
                }
            }
        }
        if (unpaid.isEmpty()) item { EmptyMessage("No pending credit payments for this billing period.") }
        items(unpaid, key = { "unpaid-${it.id}" }) { item ->
            CreditPaymentCard(item, selectedIds.contains(item.id), { onSelect(item.id, it) }, { onSetPaid(item.id, true) })
        }
        if (paid.isNotEmpty()) item { SectionTitle("Paid credit transactions") }
        items(paid, key = { "paid-${it.id}" }) { item ->
            CreditPaymentCard(item, selectedIds.contains(item.id), { onSelect(item.id, it) }, { onSetPaid(item.id, false) })
        }
    }
}

@Composable
private fun CreditPaymentCard(item: Transaction, selected: Boolean, onSelect: (Boolean) -> Unit, onTogglePaid: () -> Unit) {
    CardBlock(background = if (item.isPaid) Color(0xFFF0EEE7) else Color(0xFFFFFEFA)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onSelect)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(item.description, fontWeight = FontWeight.SemiBold)
                Text("${displayDate(item.date)} · ${item.account}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(item.amount), fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = onTogglePaid,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                ) { Text(if (item.isPaid) "Mark unpaid" else "Mark paid") }
            }
        }
    }
}

@Composable
private fun BudgetsPage(
    budgets: List<Budget>,
    onEdit: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Budgets")
                    Text("${budgets.size} budgets · ${money(budgets.sumOf { it.amount })} total", color = Color.Gray)
                }
            }
        }
        if (budgets.isEmpty()) item { EmptyMessage("No budgets for this billing period yet.") }
        items(budgets, key = { it.id }) { budget ->
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(budget.person, fontWeight = FontWeight.SemiBold)
                        Text("Budget for ${displayDate(budget.periodStart)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(budget.amount), fontWeight = FontWeight.SemiBold)
                        Row {
                            IconButton(onClick = { onEdit(budget) }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Edit, "Edit budget", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(budget) }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Delete, "Delete budget", tint = Color(0xFF9D5145), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoPage(
    todos: List<PeriodTodo>,
    selectedIds: Set<String>,
    remainingBudget: Double,
    onSelect: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSetSelectedCompleted: () -> Unit,
    onEdit: (PeriodTodo) -> Unit,
    onDelete: (PeriodTodo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            SectionTitle("To-do List")
            Text("${todos.count { !it.completed }} remaining · ${todos.count { it.completed }} completed", color = Color.Gray)
        }
        val selected = todos.filter { it.id in selectedIds }
        if (selected.isNotEmpty()) {
            item {
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${selected.size} selected", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Total amount · ${money(selected.sumOf { it.amount ?: 0.0 })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                            Text(
                                "Budget after selected tasks · ${money(budgetAfterSelectedTodos(remainingBudget, selected))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Forest,
                            )
                        }
                        TextButton(onClick = onClearSelection) { Text("Deselect All") }
                    }
                    Button(onClick = onSetSelectedCompleted, modifier = Modifier.padding(top = 8.dp)) {
                        Text(if (selected.all { it.completed }) "Mark selected not done" else "Mark selected done")
                    }
                }
            }
        }
        if (todos.isEmpty()) item { EmptyMessage("No tasks for this billing period yet. Add one to get started.") }
        items(todos.sortedBy { it.completed }, key = { it.id }) { todo ->
            CardBlock(background = if (todo.completed) Color(0xFFF0EEE7) else Color(0xFFFFFEFA)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = todo.id in selectedIds, onCheckedChange = { onSelect(todo.id, it) })
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            todo.title,
                            color = if (todo.completed) Color.Gray else Ink,
                            textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        todo.amount?.let { Text(money(it), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                    }
                    IconButton(onClick = { onEdit(todo) }) { Icon(Icons.Default.Edit, "Edit task") }
                    IconButton(onClick = { onDelete(todo) }) { Icon(Icons.Default.Delete, "Delete task", tint = Color(0xFF9D5145)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    data: TrackerData,
    status: String,
    onCycleDay: (Int) -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    currentVersion: String,
    updateMessage: String,
    availableUpdate: NativeUpdate?,
    isCheckingUpdate: Boolean,
    isDownloadingUpdate: Boolean,
    downloadProgress: UpdateDownloadProgress?,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var categoryText by remember { mutableStateOf("") }
    var accountText by remember { mutableStateOf("") }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            CardBlock {
                SectionTitle("Billing cycle")
                Text("Choose the day each monthly billing period starts. Short months use their last day.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text("Starts on the ${ordinal(data.cycleStartDay)} of each month")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        (1..31).forEach { day ->
                            DropdownMenuItem(
                                text = { Text(if (day == 31) "31st (last day in short months)" else "${ordinal(day)} of each month") },
                                onClick = { onCycleDay(day); expanded = false },
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsListCard("Categories", data.categories, categoryText, { categoryText = it }, onAddCategory, onRemoveCategory) {
                categoryText = ""
            }
        }
        item {
            SettingsListCard("Accounts", data.accounts, accountText, { accountText = it }, onAddAccount, onRemoveAccount) {
                accountText = ""
            }
        }
        item {
            CardBlock {
                SectionTitle("Backup and restore")
                Text("Export or import a JSON backup using Android's file picker. Import replaces data in this app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export backup") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import backup") }
                }
                Text(status, Modifier.padding(top = 8.dp), color = Forest, style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            CardBlock {
                SectionTitle("Android updates")
                val versionStatus = when {
                    availableUpdate != null -> "(Update available)"
                    isCheckingUpdate -> "(Checking…)"
                    updateMessage == "(Up-to-date)" || updateMessage == "This app is up to date." -> "(Up-to-date)"
                    else -> updateMessage
                }
                Text("Current Version: $currentVersion $versionStatus", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text("Vibe Coded by: Gabriel Royo Jr.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                downloadProgress?.let { progress ->
                    if (progress.percent == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "Downloading update · ${formatMegabytes(progress.downloadedBytes)}" +
                            if (progress.totalBytes > 0) " of ${formatMegabytes(progress.totalBytes)} (${progress.percent ?: 0}%)"
                            else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        enabled = !isCheckingUpdate && !isDownloadingUpdate,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isCheckingUpdate) "Checking…" else "Check for updates")
                    }
                    if (availableUpdate != null) {
                        Button(
                            onClick = onInstallUpdate,
                            enabled = !isDownloadingUpdate,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isDownloadingUpdate) "Downloading…" else "Install ${availableUpdate.version}")
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("PawBudget saves your data only on this device.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Text("Keep a backup before uninstalling.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsListCard(
    title: String,
    values: List<String>,
    input: String,
    onInput: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    afterAdd: () -> Unit,
) {
    CardBlock {
        SectionTitle(title)
        val itemLabel = if (title == "Categories") "Category" else "Account"
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, onInput, modifier = Modifier.weight(1f), label = { Text("Add $itemLabel") }, singleLine = true)
            IconButton(onClick = { onAdd(input); afterAdd() }, enabled = input.isNotBlank()) {
                Icon(Icons.Default.Add, "Add $itemLabel")
            }
        }
        if (values.isEmpty()) Text("No ${title.lowercase()} yet.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        values.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item, Modifier.weight(1f))
                IconButton(onClick = { onRemove(item) }) {
                    Icon(Icons.Default.Delete, "Remove $item", tint = Color(0xFF9D5145))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditor(
    initial: Transaction?,
    categories: List<String>,
    accounts: List<String>,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, String) -> Unit,
) {
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: defaultDate) }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var category by remember(initial, categories) { mutableStateOf(initial?.category ?: categories.firstOrNull().orEmpty()) }
    var account by remember(initial, accounts) { mutableStateOf(initial?.account ?: accounts.firstOrNull().orEmpty()) }
    var paymentType by remember(initial) { mutableStateOf(initial?.paymentType ?: "paid") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add transaction" else "Edit transaction") },
        text = {
            Column(Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    date,
                    { date = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Date") },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Choose") }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    description,
                    { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    singleLine = true,
                )
                OutlinedTextField(
                    amountText,
                    { amountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                SelectionField("Category", category, categories, { category = it })
                SelectionField("Account Used", account, accounts, { account = it })
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Payment Type")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Paid", "Credit").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = paymentType == label.lowercase(),
                                onClick = { paymentType = label.lowercase() },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFF9D5145), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                val validDate = runCatching { LocalDate.parse(date) }.isSuccess
                if (description.isBlank() || !validDate || amount == null || !amount.isFinite() || amount <= 0 || category.isBlank() || account.isBlank()) {
                    error = "Enter a valid date, description, category, account, and amount greater than zero."
                } else {
                    onSave(date, description, amount, category, account, paymentType)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showDatePicker) {
        val initialDate = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("Select date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun BudgetEditor(initial: Budget?, defaultPeriod: String, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var person by remember(initial) { mutableStateOf(initial?.person.orEmpty()) }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Billing period starting ${displayDate(initial?.periodStart ?: defaultPeriod)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                OutlinedTextField(person, { person = it }, label = { Text("Person") }, singleLine = true)
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount (PHP)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                error?.let { Text(it, color = Color(0xFF9D5145)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (person.isBlank() || amount == null || !amount.isFinite() || amount <= 0) error = "Enter a name and a budget amount greater than zero."
                else onSave(person, amount)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TodoEditor(initial: PeriodTodo?, onDismiss: () -> Unit, onSave: (String, Double?) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Task") }, singleLine = true)
                OutlinedTextField(
                    amountText,
                    { amountText = it },
                    label = { Text("Amount (optional, PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                error?.let { Text(it, color = Color(0xFF9D5145)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (title.isBlank()) {
                    error = "A task cannot have an empty title."
                } else if (amountText.isNotBlank() && (amount == null || !amount.isFinite() || amount <= 0)) {
                    error = "Enter an amount greater than zero, or leave it blank."
                } else {
                    onSave(title, amount)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionField(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = options.isNotEmpty(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun CardBlock(
    background: Color = Color(0xFFFFFEFA),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyMessage(text: String) {
    CardBlock { Text(text, color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun ErrorScreen(message: String) {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("PawBudget could not open", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, Modifier.padding(top = 8.dp), color = Color(0xFF9D5145))
        }
    }
}

private fun money(value: Double): String = Currency.format(value)

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

private fun displayDate(value: String): String =
    runCatching { LocalDate.parse(value).format(DateFormat) }.getOrDefault(value)

private fun ordinal(value: Int): String {
    val remainder = value % 100
    if (remainder in 11..13) return "${value}th"
    return when (value % 10) {
        1 -> "${value}st"
        2 -> "${value}nd"
        3 -> "${value}rd"
        else -> "${value}th"
    }
}

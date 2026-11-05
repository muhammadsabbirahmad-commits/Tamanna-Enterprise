package com.tamanna.enterprise.partner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.profit.BusinessProfitCalculator
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.settings.ThemeStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PartnerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                PartnerScreen()
            }
        }
    }
}

private fun partnerDate(daysAgo: Int = 0): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun parsePartnerDate(value: String): java.util.Date? = runCatching {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = format.parse(value.trim()) ?: return@runCatching null
    parsed.takeIf { format.format(it) == value.trim() }
}.getOrNull()

private fun isValidPartnerDate(value: String): Boolean = parsePartnerDate(value) != null

private fun normalizePartnerDate(value: String): String {
    val date = value.trim().substringBefore('T').substringBefore(' ')
    return date.takeIf(::isValidPartnerDate).orEmpty()
}

private fun partnerDateRange(fromDate: String, toDate: String): List<String> {
    val start = parsePartnerDate(fromDate) ?: return emptyList()
    val end = parsePartnerDate(toDate) ?: return emptyList()
    if (start.after(end)) return emptyList()
    val calendar = Calendar.getInstance().apply {
        time = start
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCalendar = Calendar.getInstance().apply {
        time = end
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return buildList {
        while (!calendar.after(endCalendar)) {
            add(format.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun partnerDateToUtcMillis(value: String): Long? = runCatching {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    format.parse(value)?.time
}.getOrNull()

private fun partnerUtcMillisToDate(value: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    return format.format(java.util.Date(value))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerDatePickerDialog(
    title: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = partnerDateToUtcMillis(initialDate)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onSelect(partnerUtcMillisToDate(it)) }
                },
                enabled = state.selectedDateMillis != null
            ) { Text("নির্বাচন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    ) {
        DatePicker(state = state, title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen() {
    val context = LocalContext.current
    val canWrite = remember { SecurityStorage.canWrite(context) }
    var refresh by remember { mutableIntStateOf(0) }
    // Refresh saved expense allocations whenever this screen becomes visible again.
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val partners = remember(refresh) { PartnerStorage.getPartners(context) }
    val withdrawals = remember(refresh) { PartnerStorage.getWithdrawals(context) }
    val profitReport = remember(refresh) { BusinessProfitCalculator.calculate(context) }
    // Partner profit/loss uses rolling expense recovery, not expenses deducted immediately.
    val allDailyNet = remember(profitReport) { profitReport.partnerDistributionByDay }
    val unallocatedExpenseTotal = remember(refresh) { ExpenseStorage.getUnallocatedExpenseTotal(context) }
    val estimatedExpenseTotal = remember(refresh) { ExpenseStorage.getEstimatedAllocationExpenseTotal(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPartner by remember { mutableStateOf<Partner?>(null) }
    var addingCapitalFor by remember { mutableStateOf<Partner?>(null) }
    var showDistributionDialog by remember { mutableStateOf(false) }
    var withdrawingPartner by remember { mutableStateOf<Partner?>(null) }
    var fromDate by remember { mutableStateOf(partnerDate(29)) }
    var toDate by remember { mutableStateOf(partnerDate()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val validRange = isValidPartnerDate(fromDate) && isValidPartnerDate(toDate) && fromDate <= toDate
    val dailyNet = remember(refresh, fromDate, toDate, allDailyNet) {
        if (!validRange) emptyList() else partnerDateRange(fromDate, toDate).map { day -> day to (allDailyNet[day] ?: 0.0) }
    }
    val lifetimeNetByDay = allDailyNet
    val totalCapital = partners.sumOf { it.shareAmount }
    val totalPercentage = partners.sumOf { it.profitPercentage }
    val distributionValid = kotlin.math.abs(totalPercentage - 100.0) <= 0.0001

    Scaffold(
        topBar = { TopAppBar(title = { Text("পার্টনার / অংশীদার বিবরণ") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (canWrite) {
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ নতুন পার্টনার যোগ করুন")
                }
                OutlinedButton(onClick = { showDistributionDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("লাভের শতাংশ বণ্টন সম্পাদনা")
                }
            } else {
                Text("👁 VIEW ONLY — পার্টনারের তথ্য দেখা যাবে, পরিবর্তন করা যাবে না।", color = MaterialTheme.colorScheme.primary)
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("মোট বিনিয়োগ: ৳ %.2f".format(Locale.US, totalCapital), style = MaterialTheme.typography.titleMedium)
                    Text("বর্তমান লাভ বণ্টন: %.2f%%".format(Locale.US, totalPercentage))
                    Text(
                        if (distributionValid) "লাভ বণ্টন ১০০% — সঠিক" else "সতর্কতা: লাভের শতাংশ ১০০% নয়; বণ্টন হিসাব অসম্পূর্ণ।",
                        color = if (distributionValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (unallocatedExpenseTotal > 0.004) {
                Text(
                    "সতর্কতা: ৳ %.2f প্রতিষ্ঠানের খরচের পার্টনার বরাদ্দ অসম্পূর্ণ। ঐতিহাসিক শতাংশের রেকর্ড বা ১০০%% বণ্টন যাচাই করুন।".format(Locale.US, unallocatedExpenseTotal),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (estimatedExpenseTotal > 0.004) {
                Text(
                    "নোট: ৳ %.2f পুরোনো খরচের বরাদ্দ বর্তমান শতাংশ থেকে আনুমানিকভাবে তৈরি হয়েছে; মূল তারিখের শতাংশের রেকর্ড না থাকায় ১০০%% ঐতিহাসিক নিশ্চয়তা নেই।".format(Locale.US, estimatedExpenseTotal),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (profitReport.warnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("লাভ-লোকসানের হিসাব যাচাই প্রয়োজন", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        profitReport.warnings.take(8).forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (profitReport.warnings.size > 8) {
                            Text("আরও ${profitReport.warnings.size - 8}টি সতর্কবার্তা আছে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Text("হিসাবের সময়সীমা", style = MaterialTheme.typography.titleLarge)
            Text("ক্যালেন্ডার থেকে শুরুর ও শেষের তারিখ বেছে নিলে প্রত্যেক পার্টনারের নির্বাচিত সময়ের লাভ-ঘাটতি আপডেট হবে।", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("শুরুর তারিখ", style = MaterialTheme.typography.labelSmall)
                        Text(fromDate)
                    }
                }
                OutlinedButton(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("শেষের তারিখ", style = MaterialTheme.typography.labelSmall)
                        Text(toDate)
                    }
                }
            }
            if (!validRange) {
                Text("শুরুর তারিখ শেষের তারিখের পরে হতে পারবে না।", color = MaterialTheme.colorScheme.error)
            } else {
                Text("$fromDate থেকে $toDate পর্যন্ত হিসাব", style = MaterialTheme.typography.labelMedium)
            }

            Text("পার্টনারদের তালিকা", style = MaterialTheme.typography.titleLarge)
            Text("প্রত্যেক পার্টনারের সব তথ্য ও কাজ একটিমাত্র কার্ডে রাখা হয়েছে। তারিখের পরিসর বদলালে কার্ডের সময়ভিত্তিক হিসাবও বদলাবে।", style = MaterialTheme.typography.bodySmall)
            if (partners.isEmpty()) Text("এখনও কোনো পার্টনারের তথ্য যোগ করা হয়নি।")
            partners.forEach { partner ->
                val allocatedProfit = dailyNet.sumOf { (day, net) ->
                    maxOf(net, 0.0) * PartnerStorage.getPercentageForDate(context, partner.id, day) / 100.0
                }
                val allocatedLoss = dailyNet.sumOf { (day, net) ->
                    kotlin.math.abs(minOf(net, 0.0)) * PartnerStorage.getPercentageForDate(context, partner.id, day) / 100.0
                }
                val lifetimeProfit = lifetimeNetByDay.entries.sumOf { (day, net) ->
                    maxOf(net, 0.0) * PartnerStorage.getPercentageForDate(context, partner.id, day) / 100.0
                }
                val lifetimeLoss = lifetimeNetByDay.entries.sumOf { (day, net) ->
                    kotlin.math.abs(minOf(net, 0.0)) * PartnerStorage.getPercentageForDate(context, partner.id, day) / 100.0
                }
                val withdrawnTotal = withdrawals.filter { it.partnerId == partner.id }.sumOf { it.amount }
                val capitalizedProfit = PartnerStorage.getCapitalContributions(context)
                    .filter { it.partnerId == partner.id && it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") }
                    .sumOf { it.amount.coerceAtLeast(0.0) }
                val availableBalance = lifetimeProfit - lifetimeLoss - withdrawnTotal - capitalizedProfit
                val periodPartnerNet = allocatedProfit - allocatedLoss
                // Includes ordinary business expenses and damaged-stock losses.
                val allocatedExpense = remember(refresh, fromDate, toDate, partner.id) {
                    ExpenseStorage.getPartnerExpenseTotal(context, partner.id, fromDate, toDate)
                }
                val lifetimeExpense = remember(refresh, partner.id) {
                    ExpenseStorage.getPartnerExpenseTotal(context, partner.id)
                }
                PartnerCard(
                    partner = partner,
                    canWrite = canWrite,
                    periodProfit = allocatedProfit,
                    periodExpense = allocatedExpense,
                    periodLoss = allocatedLoss,
                    periodNet = periodPartnerNet,
                    lifetimeProfit = lifetimeProfit,
                    lifetimeExpense = lifetimeExpense,
                    lifetimeLoss = lifetimeLoss,
                    withdrawnTotal = withdrawnTotal,
                    availableBalance = availableBalance,
                    distributionValid = distributionValid,
                    onAddCapital = { addingCapitalFor = partner },
                    onWithdraw = { withdrawingPartner = partner },
                    onEdit = { if (canWrite) editingPartner = partner }
                )
            }

        }
    }

    if (showStartDatePicker) {
        PartnerDatePickerDialog(
            title = "হিসাবের শুরুর তারিখ",
            initialDate = fromDate,
            onDismiss = { showStartDatePicker = false },
            onSelect = { selected ->
                fromDate = selected
                showStartDatePicker = false
                refresh++
            }
        )
    }
    if (showEndDatePicker) {
        PartnerDatePickerDialog(
            title = "হিসাবের শেষ তারিখ",
            initialDate = toDate,
            onDismiss = { showEndDatePicker = false },
            onSelect = { selected ->
                toDate = selected
                showEndDatePicker = false
                refresh++
            }
        )
    }

    if (showAddDialog && canWrite) {
        AddEditPartnerDialog(null, onDismiss = { showAddDialog = false }, onSaved = { newPartner ->
            val saved = PartnerStorage.addPartner(context, newPartner)
            if (saved) {
                ActivityLogStorage.addAndGetId(context, "নতুন পার্টনার যোগ", "${newPartner.name} • মূলধন: ৳${newPartner.shareAmount}")
                Toast.makeText(context, "পার্টনার যোগ হয়েছে। এখন লাভের শতাংশ বণ্টন ১০০% করুন।", Toast.LENGTH_LONG).show()
                refresh++
                showAddDialog = false
            } else Toast.makeText(context, "তথ্য সেভ হয়নি।", Toast.LENGTH_SHORT).show()
        })
    }

    addingCapitalFor?.let { selected ->
        CapitalContributionDialog(selected, onDismiss = { addingCapitalFor = null }) { amount, date, note ->
            if (PartnerStorage.addCapitalContribution(context, selected.id, amount, date, note)) {
                ActivityLogStorage.addAndGetId(context, "পার্টনারের মূলধন জমা", selected.name + " • ৳" + amount + " • " + date)
                Toast.makeText(context, "মূলধন জমা সংরক্ষণ হয়েছে।", Toast.LENGTH_SHORT).show()
                refresh++
                addingCapitalFor = null
            } else Toast.makeText(context, "মূলধন জমা সংরক্ষণ করা যায়নি।", Toast.LENGTH_SHORT).show()
        }
    }

    editingPartner?.let { selected ->
        AddEditPartnerDialog(selected, onDismiss = { editingPartner = null }, onSaved = { updated ->
            if (PartnerStorage.updatePartner(context, updated)) {
                ActivityLogStorage.addAndGetId(context, "পার্টনার তথ্য আপডেট", updated.name + " • মূলধন: ৳" + updated.shareAmount)
                Toast.makeText(context, "তথ্য আপডেট হয়েছে।", Toast.LENGTH_SHORT).show()
                refresh++
                editingPartner = null
            } else Toast.makeText(context, "আপডেট করা যায়নি।", Toast.LENGTH_SHORT).show()
        }, onDelete = {
            val deleted = PartnerStorage.deletePartner(context, selected.id, partnerDate())
            if (deleted) {
                ActivityLogStorage.addAndGetId(context, "পার্টনার মুছে ফেলা", selected.name + " (${selected.id})")
                Toast.makeText(context, "পার্টনার মুছে ফেলা হয়েছে।", Toast.LENGTH_SHORT).show()
                refresh++
                editingPartner = null
            } else {
                Toast.makeText(context, "মুছতে হলে বাকি পার্টনারদের বর্তমান শতাংশের যোগফল ১০০% হতে হবে।", Toast.LENGTH_LONG).show()
            }
        })
    }

    if (showDistributionDialog && canWrite) {
        ProfitDistributionDialog(partners, onDismiss = { showDistributionDialog = false }, onSave = { rates, date ->
            if (PartnerStorage.saveProfitDistribution(context, rates, date)) {
                ActivityLogStorage.addAndGetId(context, "পার্টনার লাভ বণ্টন পরিবর্তন", "কার্যকর: $date • মোট: 100%")
                Toast.makeText(context, "লাভের শতাংশ সংরক্ষিত হয়েছে।", Toast.LENGTH_SHORT).show()
                refresh++
                showDistributionDialog = false
            } else Toast.makeText(context, "সংরক্ষণ হয়নি। তারিখ ও মোট শতাংশ ১০০% যাচাই করুন।", Toast.LENGTH_LONG).show()
        })
    }

    withdrawingPartner?.let { selected ->
        val earnedProfit = lifetimeNetByDay.entries.sumOf { (day, net) -> maxOf(net, 0.0) * PartnerStorage.getPercentageForDate(context, selected.id, day) / 100.0 }
        val allocatedLoss = lifetimeNetByDay.entries.sumOf { (day, net) -> kotlin.math.abs(minOf(net, 0.0)) * PartnerStorage.getPercentageForDate(context, selected.id, day) / 100.0 }
        val capitalized = PartnerStorage.getCapitalContributions(context)
            .filter { it.partnerId == selected.id && it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") }
            .sumOf { it.amount.coerceAtLeast(0.0) }
        val balance = earnedProfit - allocatedLoss -
            withdrawals.filter { it.partnerId == selected.id }.sumOf { it.amount } - capitalized
        PartnerWithdrawalDialog(selected, balance, onDismiss = { withdrawingPartner = null }) { amount, date, note ->
            val currentProfit = lifetimeNetByDay.entries.sumOf { (day, net) -> maxOf(net, 0.0) * PartnerStorage.getPercentageForDate(context, selected.id, day) / 100.0 }
            val currentLoss = lifetimeNetByDay.entries.sumOf { (day, net) -> kotlin.math.abs(minOf(net, 0.0)) * PartnerStorage.getPercentageForDate(context, selected.id, day) / 100.0 }
            val currentCapitalized = PartnerStorage.getCapitalContributions(context)
                .filter { it.partnerId == selected.id && it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") }
                .sumOf { it.amount.coerceAtLeast(0.0) }
            val currentBalance = currentProfit - currentLoss -
                PartnerStorage.getWithdrawals(context).filter { it.partnerId == selected.id }.sumOf { it.amount } - currentCapitalized
            if (amount > currentBalance + 0.0001) {
                Toast.makeText(context, "অবশিষ্ট ব্যালান্সের বেশি উত্তোলন করা যাবে না।", Toast.LENGTH_LONG).show()
            } else if (PartnerStorage.addWithdrawal(context, selected.id, amount, date, note)) {
                ActivityLogStorage.addAndGetId(context, "পার্টনার লভ্যাংশ উত্তোলন", selected.name + " • ৳" + amount + " • " + date)
                Toast.makeText(context, "উত্তোলন সংরক্ষিত হয়েছে।", Toast.LENGTH_SHORT).show()
                refresh++
                withdrawingPartner = null
            } else Toast.makeText(context, "উত্তোলন সংরক্ষণ করা যায়নি।", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PartnerCard(
    partner: Partner,
    canWrite: Boolean,
    periodProfit: Double,
    periodExpense: Double,
    periodLoss: Double,
    periodNet: Double,
    lifetimeProfit: Double,
    lifetimeExpense: Double,
    lifetimeLoss: Double,
    withdrawnTotal: Double,
    availableBalance: Double,
    distributionValid: Boolean,
    onAddCapital: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(partner.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val statusColor = when {
                    periodNet > 0.004 -> MaterialTheme.colorScheme.primaryContainer
                    periodNet < -0.004 -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val statusTextColor = when {
                    periodNet > 0.004 -> MaterialTheme.colorScheme.onPrimaryContainer
                    periodNet < -0.004 -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = when {
                            periodNet > 0.004 -> "লাভ ৳ %.2f".format(Locale.US, periodNet)
                            periodNet < -0.004 -> "ঘাটতি ৳ %.2f".format(Locale.US, kotlin.math.abs(periodNet))
                            else -> "সমতা ৳ 0.00"
                        },
                        color = statusTextColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (partner.note.isNotBlank()) Text("নোট: ${partner.note}", style = MaterialTheme.typography.bodySmall)
            Text("বিনিয়োগ / বর্তমান মূলধন: ৳ %.2f".format(Locale.US, partner.shareAmount))
            Text("লাভ বণ্টনের হার: %.2f%%".format(Locale.US, partner.profitPercentage),
                color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("নির্বাচিত সময়ের হিসাব", style = MaterialTheme.typography.titleMedium)
            Text("লাভের অংশ: ৳ %.2f".format(Locale.US, periodProfit))
            Text("প্রতিষ্ঠানের খরচের অংশ: ৳ %.2f".format(Locale.US, periodExpense))
            Text("লোকসানের অংশ: ৳ %.2f".format(Locale.US, periodLoss),
                color = MaterialTheme.colorScheme.error)
            Text("নিট ফলাফল: ৳ %.2f".format(Locale.US, periodNet),
                style = MaterialTheme.typography.titleMedium,
                color = if (periodNet < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("সর্বমোট হিসাব", style = MaterialTheme.typography.titleMedium)
            Text("সর্বমোট লাভের অংশ: ৳ %.2f".format(Locale.US, lifetimeProfit))
            Text("সর্বমোট প্রতিষ্ঠানের খরচের অংশ: ৳ %.2f".format(Locale.US, lifetimeExpense))
            Text("সর্বমোট লোকসানের অংশ: ৳ %.2f".format(Locale.US, lifetimeLoss),
                color = MaterialTheme.colorScheme.error)
            Text("মোট লভ্যাংশ উত্তোলন: ৳ %.2f".format(Locale.US, withdrawnTotal))
            Text("মূলধনে যোগ করা লভ্যাংশ উত্তোলনযোগ্য হিসাবে ধরা হয়নি.", style = MaterialTheme.typography.bodySmall)
            Text("অবশিষ্ট নিট পাওনা / (ঘাটতি): ৳ %.2f".format(Locale.US, availableBalance),
                style = MaterialTheme.typography.titleMedium,
                color = if (availableBalance < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (!distributionValid) {
                Text("সতর্কতা: পার্টনারদের লাভ বণ্টনের মোট হার ১০০% নয়।", color = MaterialTheme.colorScheme.error)
            }
            if (canWrite) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onAddCapital, modifier = Modifier.weight(1f)) { Text("＋ মূলধন জমা") }
                    TextButton(onClick = onWithdraw, enabled = availableBalance > 0.0, modifier = Modifier.weight(1f)) { Text("লভ্যাংশ উত্তোলন") }
                    TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("সম্পাদনা") }
                }
            }
        }
    }
}

@Composable
fun AddEditPartnerDialog(
    partner: Partner?,
    onDismiss: () -> Unit,
    onSaved: (Partner) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(partner?.name.orEmpty()) }
    var share by remember { mutableStateOf(partner?.shareAmount?.toString().orEmpty()) }
    var note by remember { mutableStateOf(partner?.note.orEmpty()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (partner == null) "নতুন পার্টনার যোগ" else "পার্টনার তথ্য সম্পাদনা") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = "" }, label = { Text("পার্টনারের নাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(share, { share = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, label = { Text("বিনিয়োগ / মূলধন (৳) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("নোট (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("লাভের শতাংশ আলাদা ‘লাভের শতাংশ বণ্টন’ অপশন থেকে সব পার্টনারের জন্য একসঙ্গে নির্ধারণ করুন।", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = share.toDoubleOrNull()
                when {
                    name.isBlank() -> error = "পার্টনারের নাম দিন।"
                    amount == null || amount < 0 -> error = "সঠিক মূলধনের পরিমাণ দিন।"
                    partner == null -> onSaved(Partner(name = name.trim(), shareAmount = amount, profitPercentage = 0.0, note = note.trim()))
                    else -> onSaved(partner.copy(name = name.trim(), shareAmount = amount, note = note.trim()))
                }
            }) { Text("সংরক্ষণ করুন") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (partner != null && onDelete != null) TextButton(onClick = onDelete) { Text("মুছুন", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("বাতিল") }
            }
        }
    )
}

@Composable
private fun CapitalContributionDialog(
    partner: Partner,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(partnerDate()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(partner.name + " — মূলধন জমা") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }; error = "" }, label = { Text("জমার পরিমাণ (৳)") }, singleLine = true)
                OutlinedTextField(date, { date = it; error = "" }, label = { Text("জমার তারিখ (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("বিবরণ (ঐচ্ছিক)") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = amount.toDoubleOrNull()
                when {
                    value == null || !value.isFinite() || value <= 0.0 -> error = "সঠিক জমার পরিমাণ লিখুন।"
                    !isValidPartnerDate(date) -> error = "সঠিক জমার তারিখ লিখুন।"
                    else -> onSave(value, date, note)
                }
            }) { Text("জমা সংরক্ষণ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun ProfitDistributionDialog(
    partners: List<Partner>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Double>, String) -> Unit
) {
    var rates by remember(partners) { mutableStateOf(partners.associate { it.id to it.profitPercentage.toString() }) }
    var effectiveDate by remember { mutableStateOf(partnerDate()) }
    var error by remember { mutableStateOf("") }
    val total = rates.values.sumOf { it.toDoubleOrNull() ?: 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("লাভের শতাংশ বণ্টন") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("সব পার্টনারের শতাংশের যোগফল অবশ্যই ১০০% হতে হবে।")
                partners.forEach { partner ->
                    OutlinedTextField(
                        value = rates[partner.id].orEmpty(),
                        onValueChange = { value -> rates = rates + (partner.id to value.filter { c -> c.isDigit() || c == '.' }); error = "" },
                        label = { Text("${partner.name} — লাভের অংশ (%)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("মোট: %.2f%%".format(Locale.US, total), color = if (kotlin.math.abs(total - 100.0) < 0.0001) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                OutlinedTextField(effectiveDate, { effectiveDate = it; error = "" }, label = { Text("কার্যকর হওয়ার তারিখ (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = rates.mapValues { it.value.toDoubleOrNull() ?: Double.NaN }
                when {
                    effectiveDate.length != 10 -> error = "সঠিক কার্যকর তারিখ দিন (YYYY-MM-DD)।"
                    parsed.values.any { !it.isFinite() || it < 0 || it > 100 } -> error = "প্রত্যেক পার্টনারের সঠিক শতাংশ দিন।"
                    kotlin.math.abs(parsed.values.sum() - 100.0) > 0.0001 -> error = "সব পার্টনারের শতাংশ যোগ করে ঠিক ১০০% হতে হবে।"
                    else -> onSave(parsed, effectiveDate)
                }
            }, enabled = partners.isNotEmpty()) { Text("১০০% সংরক্ষণ করুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}


@Composable
private fun PartnerWithdrawalDialog(
    partner: Partner,
    availableBalance: Double,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(partnerDate()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(partner.name + " — লভ্যাংশ উত্তোলন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("উত্তোলনযোগ্য ব্যালান্স: ৳ %.2f".format(Locale.US, availableBalance))
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, label = { Text("উত্তোলনের পরিমাণ (৳)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it; error = "" }, label = { Text("উত্তোলনের তারিখ (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("বিবরণ (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = amount.toDoubleOrNull()
                when {
                    value == null || !value.isFinite() || value <= 0 -> error = "সঠিক উত্তোলনের পরিমাণ লিখুন।"
                    value > availableBalance + 0.0001 -> error = "অবশিষ্ট ব্যালান্সের বেশি উত্তোলন করা যাবে না।"
                    date.length != 10 -> error = "সঠিক তারিখ লিখুন (YYYY-MM-DD)।"
                    else -> onSave(value, date, note)
                }
            }) { Text("উত্তোলন সংরক্ষণ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

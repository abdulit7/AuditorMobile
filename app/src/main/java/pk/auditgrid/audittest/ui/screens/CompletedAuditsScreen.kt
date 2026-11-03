package pk.auditgrid.audittest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import java.util.Locale

private sealed class CompletedAuditEntry {
    abstract val key: String
    abstract val sortTimestamp: String
    abstract val kind: String // "distributor" | "retail" | "custom"
    abstract val reportCode: String
    abstract val formTitle: String
    abstract val targetName: String
    abstract val territoryLabel: String
    abstract val dateTimeLabel: String
    abstract val locationMatched: Boolean
    abstract val statusLabel: String
    abstract val isQueuedOutbox: Boolean

    data class OutboxEntry(val item: PendingOutboxItem) : CompletedAuditEntry() {
        override val key: String = "outbox_${item.id}"
        override val sortTimestamp: String = item.createdAtISO.ifEmpty { "9999" }
        override val kind: String = item.kind
        override val reportCode: String = item.reportCode
        override val formTitle: String = item.formTitle
        override val targetName: String = item.targetName
        override val territoryLabel: String = listOf(item.cityName, item.territoryCode)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        override val dateTimeLabel: String = item.createdAtDisplay
        override val locationMatched: Boolean = item.locationMatched
        override val statusLabel: String = "Queued — Pending Upload"
        override val isQueuedOutbox: Boolean = true
    }

    data class DistributorEntry(val report: DistributorProductAuditReport) : CompletedAuditEntry() {
        override val key: String = "dist_${report.id}"
        override val sortTimestamp: String = report.submittedAtISO
        override val kind: String = "distributor"
        override val reportCode: String = report.reportCode
        override val formTitle: String = "Distributor Product Stock Audit"
        override val targetName: String = report.distributorSetupName
        override val territoryLabel: String = listOf(report.cityName, report.territoryCode)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        override val dateTimeLabel: String = listOf(report.submittedDate, report.submittedTime)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifEmpty { report.reportDateDisplay }
        override val locationMatched: Boolean = report.locationMatched
        override val statusLabel: String = report.reviewStatus ?: "Submitted"
        override val isQueuedOutbox: Boolean = false
    }

    data class RetailEntry(val report: RetailMarketShopAuditReport) : CompletedAuditEntry() {
        override val key: String = "retail_${report.id}"
        override val sortTimestamp: String = report.submittedAtISO
        override val kind: String = "retail"
        override val reportCode: String = report.reportCode
        override val formTitle: String = "Retail Market Shop Audit"
        override val targetName: String = "${report.subLocationName} (${report.auditedShopsCount} shops)"
        override val territoryLabel: String = listOf(report.cityName, report.territoryCode, report.locationName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        override val dateTimeLabel: String = listOf(report.submittedDate, report.submittedTime)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifEmpty { report.reportDateDisplay }
        override val locationMatched: Boolean =
            report.auditedShopsCount > 0 && report.matchedLocationShopsCount == report.auditedShopsCount
        override val statusLabel: String = report.reviewStatus ?: "Submitted"
        override val isQueuedOutbox: Boolean = false
    }

    data class CustomEntry(val submission: CustomAuditSubmission) : CompletedAuditEntry() {
        override val key: String = "custom_${submission.id}"
        override val sortTimestamp: String = submission.submittedAtISO
        override val kind: String = "custom"
        override val reportCode: String = submission.reportCode
        override val formTitle: String = submission.formTitle.ifEmpty { "Custom Audit" }
        override val targetName: String = submission.targetName.ifEmpty { submission.targetId }
        override val territoryLabel: String = listOf(submission.cityName, submission.territoryCode)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        override val dateTimeLabel: String = listOf(submission.submittedDate, submission.submittedTime)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifEmpty { submission.submittedAtISO.take(16).replace('T', ' ') }
        override val locationMatched: Boolean = submission.locationMatched
        override val statusLabel: String = submission.reviewStatus.ifEmpty { "Submitted" }
        override val isQueuedOutbox: Boolean = false
    }
}

@Composable
fun CompletedAuditsScreen(
    distributorReports: List<DistributorProductAuditReport>,
    retailReports: List<RetailMarketShopAuditReport>,
    customSubmissions: List<CustomAuditSubmission>,
    pendingOutbox: List<PendingOutboxItem>,
    formConfigs: FormConfigMap,
    syncing: Boolean,
    lastSyncedAt: String,
    online: Boolean,
    onSyncNow: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("all") } // "all" | "distributor" | "retail" | "custom"
    var searchQuery by remember { mutableStateOf("") }
    var selectedEntryKey by remember { mutableStateOf<String?>(null) }

    val allEntries = remember(distributorReports, retailReports, customSubmissions, pendingOutbox) {
        val outboxIds = pendingOutbox.map { it.id }.toSet()
        val list = mutableListOf<CompletedAuditEntry>()
        pendingOutbox.forEach { list.add(CompletedAuditEntry.OutboxEntry(it)) }
        distributorReports.filterNot { outboxIds.contains(it.id) }
            .forEach { list.add(CompletedAuditEntry.DistributorEntry(it)) }
        retailReports.filterNot { outboxIds.contains(it.id) }
            .forEach { list.add(CompletedAuditEntry.RetailEntry(it)) }
        customSubmissions.filterNot { outboxIds.contains(it.id) }
            .forEach { list.add(CompletedAuditEntry.CustomEntry(it)) }
        list.sortedWith(
            compareByDescending<CompletedAuditEntry> { it.isQueuedOutbox }
                .thenByDescending { it.sortTimestamp }
        )
    }

    val selectedEntry = remember(allEntries, selectedEntryKey) {
        selectedEntryKey?.let { k -> allEntries.find { it.key == k } }
    }

    if (selectedEntry != null) {
        BackHandler { selectedEntryKey = null }
        CompletedAuditReadOnlyDetail(
            entry = selectedEntry,
            formConfigs = formConfigs,
            onBack = { selectedEntryKey = null }
        )
        return
    }

    val filteredEntries = remember(allEntries, selectedFilter, searchQuery) {
        allEntries.filter { e ->
            val matchesKind = selectedFilter == "all" || e.kind == selectedFilter
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                e.reportCode.contains(q, ignoreCase = true) ||
                e.targetName.contains(q, ignoreCase = true) ||
                e.formTitle.contains(q, ignoreCase = true) ||
                e.territoryLabel.contains(q, ignoreCase = true) ||
                e.dateTimeLabel.contains(q, ignoreCase = true)
            matchesKind && matchesQuery
        }
    }

    val totalSubmittedCount = distributorReports.size + retailReports.size + customSubmissions.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Strict Read-Only Ledger Header Card
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(AgPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Completed Audits",
                                color = AgForeground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lastSyncedAt.isNotBlank()) {
                                    "$totalSubmittedCount submitted · Last synced $lastSyncedAt"
                                } else {
                                    "$totalSubmittedCount submitted to main server"
                                },
                                color = AgMutedText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("completed_sync_button")
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing…", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync with server",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Read-only compliance notice
                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AgPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Record Only — Submitted audits are locked and cannot be modified or deleted.",
                            color = AgMutedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Category breakdown mini-pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryMiniChip(
                        label = "Distributor",
                        count = distributorReports.size,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMiniChip(
                        label = "Retail Shop",
                        count = retailReports.size,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMiniChip(
                        label = "Custom",
                        count = customSubmissions.size,
                        modifier = Modifier.weight(1f)
                    )
                    if (pendingOutbox.isNotEmpty()) {
                        SummaryMiniChip(
                            label = "Outbox",
                            count = pendingOutbox.size,
                            highlightWarning = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Filter Pills & Search Bar
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf(
                        "all" to "All (${allEntries.size})",
                        "distributor" to "Distributor (${distributorReports.size})",
                        "retail" to "Retail Shop (${retailReports.size})",
                        "custom" to "Custom (${customSubmissions.size})"
                    )
                    filters.forEach { (key, label) ->
                        val active = selectedFilter == key
                        Surface(
                            color = if (active) AgPrimary else AgBackground,
                            contentColor = if (active) Color.White else AgMutedText,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { selectedFilter = key }
                                .testTag("completed_filter_$key")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by report code, distributor, shop, or date…",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AgMutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("completed_search_input")
                )
            }
        }

        // Completed Audit Cards List
        if (filteredEntries.isEmpty()) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = AgMutedText,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = if (allEntries.isEmpty()) "No submitted audits yet" else "No matching audit records",
                        color = AgForeground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (allEntries.isEmpty()) {
                            "When you submit a Distributor, Retail Shop, or Custom audit, it is sent to the main server and appears here as a permanent read-only record."
                        } else {
                            "Try clearing your search query or switching the form filter above."
                        },
                        color = AgMutedText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            filteredEntries.forEach { entry ->
                CompletedAuditCard(
                    entry = entry,
                    onClick = { selectedEntryKey = entry.key }
                )
            }
        }
    }
}

@Composable
private fun SummaryMiniChip(
    label: String,
    count: Int,
    highlightWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlightWarning) AgWarningBg else AgBackground,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (highlightWarning) AgWarning.copy(alpha = 0.4f) else AgBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = if (highlightWarning) AgWarningDark else AgForeground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = if (highlightWarning) AgWarningDark else AgMutedText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompletedAuditCard(
    entry: CompletedAuditEntry,
    onClick: () -> Unit
) {
    val kindBadgeLabel = when (entry.kind) {
        "retail" -> "Retail Shop"
        "custom" -> "Custom"
        else -> "Distributor"
    }
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (entry.isQueuedOutbox) AgWarning.copy(alpha = 0.5f) else AgBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("completed_audit_card_${entry.reportCode}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CodeBadge(entry.reportCode)
                    Surface(
                        color = AgSoftBlueBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AgSoftBlueBorder)
                    ) {
                        Text(
                            text = kindBadgeLabel,
                            color = AgActionBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                ReviewOrQueueStatusBadge(
                    status = entry.statusLabel,
                    isQueued = entry.isQueuedOutbox
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entry.targetName,
                        color = AgForeground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.formTitle,
                        color = AgMutedText,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View read-only audit record",
                    tint = AgMutedText
                )
            }

            // Extra summary metrics per type
            when (entry) {
                is CompletedAuditEntry.DistributorEntry -> {
                    val r = entry.report
                    Text(
                        text = "${r.items.size} SKUs · ${String.format(Locale.US, "%.2f", r.totalCtns)} CTNs · ${formatPKR(r.totalStockValueRs)}",
                        color = AgPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is CompletedAuditEntry.RetailEntry -> {
                    val r = entry.report
                    Text(
                        text = "${r.auditedShopsCount}/${r.totalShopsInSubLocation} Shops · Prd A: ${r.totalProductACount} · Prd B: ${r.totalProductBCount} · Packed: ${r.totalPackedProductQty}",
                        color = AgPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is CompletedAuditEntry.CustomEntry -> {
                    val c = entry.submission
                    if (c.answers.isNotEmpty()) {
                        Text(
                            text = "${c.answers.size} fields recorded · ${c.capturedImages.size} photos",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is CompletedAuditEntry.OutboxEntry -> {
                    entry.item.lastError?.let { err ->
                        Text(
                            text = err,
                            color = AgWarningDark,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = AgBorder.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listOf(entry.territoryLabel, entry.dateTimeLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = AgMutedText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (entry.locationMatched) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = if (entry.locationMatched) AgAccentGreen else AgWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (entry.locationMatched) "GPS OK" else "GPS Flagged",
                        color = if (entry.locationMatched) AgAccentGreenDark else AgWarningDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Read-only",
                        tint = AgMutedText,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewOrQueueStatusBadge(status: String, isQueued: Boolean) {
    val (bg, fg, label) = when {
        isQueued -> Triple(AgWarningBg, AgWarningDark, "Queued Outbox")
        status.equals("Approved", ignoreCase = true) -> Triple(AgAccentGreenBg, AgAccentGreenDark, "Approved")
        status.equals("Flagged", ignoreCase = true) || status.equals("Rejected", ignoreCase = true) ->
            Triple(AgDestructiveBg, AgDestructiveDark, status)
        else -> Triple(AgAccentGreenBg, AgAccentGreenDark, status.ifEmpty { "Submitted" })
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isQueued) Icons.Default.Schedule else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompletedAuditReadOnlyDetail(
    entry: CompletedAuditEntry,
    formConfigs: FormConfigMap,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top Back & Read-Only Lock Bar
        Surface(
            color = AgPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("completed_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Completed Audits",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = entry.reportCode,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = entry.formTitle,
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "READ ONLY",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Strict Non-Editable Notice Banner
        Surface(
            color = if (entry.isQueuedOutbox) AgWarningBg else AgSoftBlueBg,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (entry.isQueuedOutbox) AgWarning.copy(alpha = 0.4f) else AgSoftBlueBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (entry.isQueuedOutbox) AgWarningDark else AgActionBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (entry.isQueuedOutbox) {
                        "Locked in Offline Outbox — Will automatically upload to the main server when online. Cannot be edited."
                    } else {
                        "Submitted to Main Server — This audit record is finalized and cannot be changed."
                    },
                    color = if (entry.isQueuedOutbox) AgWarningDark else AgForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        when (entry) {
            is CompletedAuditEntry.DistributorEntry -> {
                ReadOnlyDistributorDetailBody(entry.report)
            }
            is CompletedAuditEntry.RetailEntry -> {
                ReadOnlyRetailDetailBody(entry.report)
            }
            is CompletedAuditEntry.CustomEntry -> {
                ReadOnlyCustomDetailBody(entry.submission, formConfigs[entry.submission.formId])
            }
            is CompletedAuditEntry.OutboxEntry -> {
                val p = entry.item
                when {
                    p.distributorReport != null -> ReadOnlyDistributorDetailBody(p.distributorReport)
                    p.retailReport != null -> ReadOnlyRetailDetailBody(p.retailReport)
                    p.customSubmission != null -> ReadOnlyCustomDetailBody(
                        p.customSubmission,
                        formConfigs[p.customSubmission.formId]
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = "← Back to Completed Audits",
                color = AgForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReadOnlyDistributorDetailBody(r: DistributorProductAuditReport) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = r.distributorSetupName,
                        color = AgForeground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${r.distributorCode} · Owner: ${r.distributorOwnerName}",
                        color = AgMutedText,
                        fontSize = 12.sp
                    )
                }
                ProductGroupTag(r.groupName.ifEmpty { "Group ${r.groupCode}" })
            }

            HorizontalDivider(color = AgBorder)

            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv("City & Territory", "${r.cityName} · ${r.territoryCode}", Modifier.weight(1f))
                ReadOnlyKv("Submitted At", "${r.submittedDate} ${r.submittedTime}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv(
                    "GPS Distance",
                    "${r.distanceMeters} m (Allowed ${r.allowedRadiusMeters} m)",
                    Modifier.weight(1f)
                )
                ReadOnlyKv(
                    "GPS Status",
                    if (r.locationMatched) "Matched" else "Outside Radius / Flagged",
                    Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv("Total Cartons", String.format(Locale.US, "%.2f CTN", r.totalCtns), Modifier.weight(1f))
                ReadOnlyKv("Total Stock Value", formatPKR(r.totalStockValueRs), Modifier.weight(1f))
            }

            if (!r.reviewNote.isNullOrBlank()) {
                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Manager Review (${r.reviewStatus ?: "Reviewed"})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgPrimary)
                        Text(r.reviewNote, fontSize = 12.sp, color = AgForeground)
                    }
                }
            }
        }
    }

    // Line items
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SUBMITTED SKU LINE ITEMS (${r.items.size})",
                color = AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            r.items.forEachIndexed { idx, item ->
                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. ${item.sku} · ${item.productName}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatPKR(item.stockValueRs),
                                color = AgPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "CTNs: ${item.qtyInCtn} · Boxes: ${item.qtyInBox} · Total: ${String.format(Locale.US, "%.2f", item.totalCtns)} CTN",
                            color = AgForeground,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Dispatch: ${item.dispatchDate.ifEmpty { "—" }} · Stacking: ${item.stacking.ifEmpty { "—" }}",
                            color = AgMutedText,
                            fontSize = 11.sp
                        )
                        if (item.remarks.isNotBlank()) {
                            Text(
                                text = "Remarks: ${item.remarks}",
                                color = AgMutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (r.capturedImages.isNotEmpty()) {
        ReadOnlyPhotosCard(r.capturedImages)
    }
}

@Composable
private fun ReadOnlyRetailDetailBody(r: RetailMarketShopAuditReport) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${r.subLocationCode} · ${r.subLocationName}",
                color = AgForeground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${r.cityName} → ${r.territoryCode} → ${r.locationName}",
                color = AgMutedText,
                fontSize = 12.sp
            )
            HorizontalDivider(color = AgBorder)
            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv("Audited Shops", "${r.auditedShopsCount} / ${r.totalShopsInSubLocation}", Modifier.weight(1f))
                ReadOnlyKv("GPS Matched Shops", "${r.matchedLocationShopsCount} / ${r.auditedShopsCount}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv("Product A / B Total", "${r.totalProductACount} / ${r.totalProductBCount}", Modifier.weight(1f))
                ReadOnlyKv("Packed Product Qty", "${r.totalPackedProductQty}", Modifier.weight(1f))
            }
            ReadOnlyKv("Submitted At", "${r.submittedDate} ${r.submittedTime}")
        }
    }

    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AUDITED SHOPS (${r.shopAudits.size})",
                color = AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            r.shopAudits.forEachIndexed { idx, sa ->
                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${sa.shopCode} · ${sa.shopName}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (sa.locationMatched) "${sa.distanceMeters}m OK" else "${sa.distanceMeters}m Flagged",
                                color = if (sa.locationMatched) AgAccentGreenDark else AgWarningDark,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${sa.ownerName} · ${sa.phone} · ${sa.shopCategory}",
                            color = AgMutedText,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Product A: ${sa.productACount} · Product B: ${sa.productBCount} · Packed Qty: ${sa.packedProductQty}",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Display Rating: ${sa.displayRating} · Regularity: ${sa.regularity} · Time: ${sa.auditedAt}",
                            color = AgForeground,
                            fontSize = 11.sp
                        )
                        if (sa.remarks.isNotBlank()) {
                            Text(
                                text = "Remarks: ${sa.remarks}",
                                color = AgMutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyCustomDetailBody(
    c: CustomAuditSubmission,
    config: FormConfigPayload?
) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = c.targetName.ifEmpty { c.targetId },
                color = AgForeground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Target Type: ${c.targetType} · ${listOf(c.cityName, c.territoryCode).filter { it.isNotBlank() }.joinToString(" · ")}",
                color = AgMutedText,
                fontSize = 12.sp
            )
            HorizontalDivider(color = AgBorder)
            Row(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyKv("Submitted At", "${c.submittedDate} ${c.submittedTime}".trim().ifEmpty { c.submittedAtISO }, Modifier.weight(1f))
                ReadOnlyKv("GPS Status", if (c.locationMatched) "Matched" else "Flagged", Modifier.weight(1f))
            }
            if (c.answers.isNotEmpty()) {
                HorizontalDivider(color = AgBorder)
                Text(
                    text = "SUBMITTED FIELD VALUES",
                    color = AgMutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                c.answers.forEach { (key, value) ->
                    val label = config?.fields?.find { it.key == key }?.label ?: key
                    ReadOnlyKv(label, value)
                }
            }
        }
    }

    if (c.capturedImages.isNotEmpty()) {
        ReadOnlyPhotosCard(c.capturedImages)
    }
}

@Composable
private fun ReadOnlyPhotosCard(images: List<AuditCapturedImage>) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ATTACHED PHOTOS (${images.size})",
                color = AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images.forEach { img ->
                    Column(
                        modifier = Modifier.width(110.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DataUrlImage(
                            dataUrl = img.url,
                            contentDescription = img.caption.ifEmpty { "Audit photo" },
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        if (img.caption.isNotBlank()) {
                            Text(
                                text = img.caption,
                                color = AgMutedText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyKv(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = AgMutedText, fontSize = 10.sp)
        Text(
            text = value.ifEmpty { "—" },
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

package pk.auditgrid.audittest.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.api.AuditGridJsonParsers
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistributorAuditFormScreen(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    locations: List<LocationItem>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    distributors: List<Distributor>,
    products: List<Product>,
    auditForms: List<AuditFormDefinition>,
    formConfigs: FormConfigMap,
    apiClient: AuditGridApiClient,
    initialFormId: String? = null,
    preset: AuditPreset? = null,
    planLink: PlanLink? = null,
    onExit: (() -> Unit)? = null,
    completedTotalCount: Int = 0,
    pendingOutboxCount: Int = 0,
    onOpenCompletedAudits: (() -> Unit)? = null,
    syncing: Boolean = false,
    online: Boolean = true,
    lastSyncedAt: String = "",
    newlySyncedFormIds: Set<String> = emptySet(),
    onSyncFormsNow: (() -> Unit)? = null,
    onSubmitReport: suspend (DistributorProductAuditReport) -> DistributorProductAuditReport,
    onSubmitRetailShopReport: suspend (RetailMarketShopAuditReport) -> RetailMarketShopAuditReport,
    onSubmitCustomAudit: suspend (CustomAuditSubmission, Map<String, Any?>, PlanLink?) -> CustomAuditSubmission,
    onCustomSubmitted: (CustomAuditSubmission) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distDraftKey = "auditgrid_dist_product_audit_draft_v1_${activeAuditor.id}"

    var openedFormId by remember {
        mutableStateOf<String?>(
            when {
                !initialFormId.isNullOrBlank() -> initialFormId
                auditForms.any { it.id == RETAIL_FORM_ID } && hasSavedRetailDraft(apiClient, activeAuditor.id) -> RETAIL_FORM_ID
                else -> null
            }
        )
    }

    LaunchedEffect(initialFormId) {
        if (!initialFormId.isNullOrBlank()) {
            openedFormId = initialFormId
        }
    }

    fun closeForm() {
        if (onExit != null) onExit() else openedFormId = null
    }

    val assignedTerritoryIdsSet = remember(activeAuditor.id) {
        activeAuditor.assignedTerritoryIds.toSet()
    }
    val scopedDistributors = remember(distributors, assignedTerritoryIdsSet) {
        distributors.filter { it.status == "Active" && assignedTerritoryIdsSet.contains(it.territoryId) }
    }
    val scopedShops = remember(shops, assignedTerritoryIdsSet) {
        shops.filter { it.status == "Active" && assignedTerritoryIdsSet.contains(it.territoryId) }
    }
    val scopedSubLocations = remember(subLocations, assignedTerritoryIdsSet) {
        subLocations.filter { it.status == "Active" && assignedTerritoryIdsSet.contains(it.territoryId) }
    }

    // VIEW 1: AUDIT FORMS LIST
    val currentOpenedId = openedFormId
    if (currentOpenedId == null) {
        val activeForms = remember(auditForms) {
            auditForms.filter { it.status == "Active" }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                color = AgRoyalBlue,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mobile Field Audit Forms",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeForms.isEmpty()) {
                                "Sync with web application to download forms"
                            } else {
                                "Select a synced form below to start an audit"
                            },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    if (onSyncFormsNow != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable(enabled = !syncing) { onSyncFormsNow() }
                                .testTag("header_sync_forms_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync forms from web server",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = if (syncing) "Syncing…" else "Sync Forms",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Empty-APK State: When no forms are in the mobile app yet
            if (activeForms.isEmpty()) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_forms_sync_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(AgSoftBlueBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = AgRoyalBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "No Audit Forms Synced Yet",
                            color = AgForeground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap the button below to sync audit forms from the main web application. Synced forms are saved on your phone for both offline and online use, and any new form created on the web application is inserted when you sync.",
                            color = AgMutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        if (onSyncFormsNow != null) {
                            Button(
                                onClick = onSyncFormsNow,
                                enabled = !syncing,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("empty_state_sync_forms_button")
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Syncing Forms from Web Server…",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sync Forms from Web Application",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Uniform Synced Audit Forms List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    activeForms.forEach { form ->
                        val isRetailForm = form.id == RETAIL_FORM_ID
                        val savedDraftExists = isRetailForm && hasSavedRetailDraft(apiClient, activeAuditor.id)
                        val isNewlyInserted = newlySyncedFormIds.contains(form.id)

                        // Clean, consistent 1-line subtitle across all 3 form types
                        val subText = when {
                            form.kind == "custom" -> {
                                when (form.target) {
                                    "shop" -> "${scopedShops.size} Assigned Territory Retail Shops"
                                    "subLocation" -> "${scopedSubLocations.size} Assigned Territory Sub-locations"
                                    else -> "${scopedDistributors.size} Assigned Territory Distributors"
                                }
                            }
                            isRetailForm -> "City → Territory → Location → Sub-location Shops"
                            else -> "${scopedDistributors.size} Assigned Territory Distributors"
                        }

                        Surface(
                            color = AgCardWhite,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isNewlyInserted) 1.5.dp else 1.dp,
                                color = if (isNewlyInserted) AgAccentGreen else AgSoftBlueBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openedFormId = form.id }
                                .testTag("open_form_${form.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CodeBadge(form.code, bg = AgSoftBlueBg, fg = AgRoyalBlue)
                                        if (isNewlyInserted) {
                                            Surface(
                                                color = Color(0xFFD1FAE5),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    color = AgAccentGreenDark,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (savedDraftExists) {
                                        Surface(
                                            color = AgWarningBg,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = "Draft Saved",
                                                color = AgWarning,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = AgAccentGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Active",
                                                color = AgAccentGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(AgRoyalBlue, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isRetailForm) Icons.Default.Storefront else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = form.title,
                                                color = AgForeground,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 18.sp
                                            )
                                            Text(
                                                text = subText,
                                                color = AgMutedText,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open ${form.title}",
                                        tint = AgRoyalBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (onOpenCompletedAudits != null) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCompletedAudits() }
                        .testTag("open_completed_audits_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(AgPrimary, RoundedCornerShape(12.dp)),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Completed Audits ($completedTotalCount)",
                                        color = AgForeground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Read-only",
                                        tint = AgMutedText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (pendingOutboxCount > 0) {
                                        "$pendingOutboxCount queued in Outbox · View all submitted records (Read-Only)"
                                    } else {
                                        "View all submitted reports sent to main server (Read-Only)"
                                    },
                                    color = if (pendingOutboxCount > 0) AgWarningDark else AgMutedText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Completed Audits",
                            tint = AgPrimary
                        )
                    }
                }
            }
        }
        return
    }

    // VIEW 2A: CUSTOM AUDIT FORM
    val customForm = auditForms.find { it.id == currentOpenedId && it.kind == "custom" }
    if (customForm != null) {
        CustomAuditFormScreen(
            form = customForm,
            config = formConfigs[customForm.id],
            activeAuditor = activeAuditor,
            cities = cities,
            territories = territories,
            distributors = distributors,
            subLocations = subLocations,
            shops = shops,
            presetTargetId = preset?.targetId,
            planLink = planLink,
            apiClient = apiClient,
            onBack = ::closeForm,
            onSubmitCustomAudit = onSubmitCustomAudit,
            onSubmitted = onCustomSubmitted,
            onToast = onToast
        )
        return
    }

    // VIEW 2B: RETAIL MARKET SHOP AUDIT FORM
    if (currentOpenedId == RETAIL_FORM_ID) {
        RetailShopAuditFormScreen(
            activeAuditor = activeAuditor,
            cities = cities,
            territories = territories,
            locations = locations,
            subLocations = subLocations,
            shops = shops,
            formConfigs = formConfigs,
            apiClient = apiClient,
            preset = preset,
            planLink = planLink,
            onSubmitReport = onSubmitRetailShopReport,
            onFinished = ::closeForm,
            onBackToForms = ::closeForm,
            onToast = onToast
        )
        return
    }

    // VIEW 2C: DISTRIBUTOR PRODUCT AUDIT FORM
    BackHandler { closeForm() }

    val fh = remember(formConfigs) { makeFormHelper(formConfigs[DISTRIBUTOR_FORM_ID]) }
    var selectedDistributorId by remember { mutableStateOf(preset?.distributorId ?: "") }
    var productSearchText by remember { mutableStateOf("") }
    var productDropdownOpen by remember { mutableStateOf(false) }
    var selectedProductId by remember { mutableStateOf("") }

    var qtyInCtn by remember { mutableStateOf("") }
    var qtyInBox by remember { mutableStateOf("") }
    var dispatchDate by remember { mutableStateOf("") }
    var stacking by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    var savedItems by remember { mutableStateOf<List<DistributorAuditLineItem>>(emptyList()) }
    var capturedImages by remember { mutableStateOf<List<AuditCapturedImage>>(emptyList()) }

    var silentCoords by remember { mutableStateOf<DevicePosition?>(null) }
    var gpsError by remember { mutableStateOf<String?>(null) }
    var gpsBusy by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitKey by remember { mutableStateOf(newSubmitKey()) }

    // Load saved distributor draft if exists
    LaunchedEffect(distDraftKey) {
        try {
            val raw = apiClient.getDraftString(distDraftKey)
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                val distId = obj.optString("selectedDistributorId")
                if (distId.isNotEmpty() && preset?.distributorId == null) {
                    selectedDistributorId = distId
                }
                val itemsArr = obj.optJSONArray("savedItems")
                if (itemsArr != null) {
                    savedItems = (0 until itemsArr.length()).mapNotNull { idx ->
                        itemsArr.optJSONObject(idx)?.let { AuditGridJsonParsers.parseDistributorLineItem(it) }
                    }
                }
                val imgsArr = obj.optJSONArray("capturedImages")
                if (imgsArr != null) {
                    capturedImages = (0 until imgsArr.length()).mapNotNull { idx ->
                        imgsArr.optJSONObject(idx)?.let { AuditGridJsonParsers.parseCapturedImage(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun captureRealLocation(): DevicePosition? {
        gpsBusy = true
        gpsError = null
        return try {
            val pos = getDevicePosition(context)
            silentCoords = pos
            pos
        } catch (e: Exception) {
            silentCoords = null
            gpsError = if (e is GpsException) e.message else "Could not read your location. Tap \"Try again\"."
            null
        } finally {
            gpsBusy = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { captureRealLocation() }
    }

    LaunchedEffect(selectedDistributorId, currentOpenedId) {
        if (!hasLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            captureRealLocation()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (capturedImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                val dataUrl = compressBitmapToDataUrl(bmp)
                capturedImages = capturedImages + AuditCapturedImage(
                    id = "img-${System.currentTimeMillis()}",
                    url = dataUrl,
                    caption = "Warehouse Stock Photo",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is needed to capture warehouse photos.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (capturedImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val dataUrl = compressUriToDataUrl(context, uri)
                    if (dataUrl != null) {
                        capturedImages = capturedImages + AuditCapturedImage(
                            id = "img-${System.currentTimeMillis()}",
                            url = dataUrl,
                            caption = "Warehouse Stock Photo",
                            capturedAt = currentTimePkt()
                        )
                    } else {
                        onToast("Could not read that photo. Try again.")
                    }
                }
            }
        }
    }

    val selectedDistributor = scopedDistributors.find { it.id == selectedDistributorId }
    val distributorProducts = remember(selectedDistributor, products) {
        if (selectedDistributor == null) emptyList()
        else products.filter { p ->
            p.status == "Active" &&
                selectedDistributor.groupIds.contains(p.groupId) &&
                selectedDistributor.productStatuses[p.id] != "Inactive"
        }
    }
    val matchingSearchProducts = distributorProducts.filter { p ->
        p.name.contains(productSearchText, ignoreCase = true) ||
            p.sku.contains(productSearchText, ignoreCase = true)
    }
    val selectedProduct = distributorProducts.find { it.id == selectedProductId }

    val parsedCtn = max(0, qtyInCtn.toIntOrNull() ?: 0)
    val parsedBox = max(0, qtyInBox.toIntOrNull() ?: 0)
    val liveTotalCtns = if (selectedProduct != null && selectedProduct.boxesPerCtn > 0) {
        parsedCtn.toDouble() + parsedBox.toDouble() / selectedProduct.boxesPerCtn.toDouble()
    } else 0.0
    val liveTotalBoxes = if (selectedProduct != null) {
        parsedCtn * selectedProduct.boxesPerCtn + parsedBox
    } else 0
    val liveStockValueRs = if (selectedProduct != null) {
        liveTotalBoxes * selectedProduct.pricePerBoxRs
    } else 0.0

    val grandTotalCtns = savedItems.sumOf { it.totalCtns }
    val grandTotalValueRs = savedItems.sumOf { it.stockValueRs }

    val activeStep = when {
        selectedDistributor == null -> 1
        savedItems.isEmpty() -> 2
        else -> 3
    }

    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Royal Blue App Header ("New Audit")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgRoyalBlue)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = ::closeForm, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("New Audit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = ::closeForm, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live GPS Status Bar
                val coords = silentCoords
                val (gpsBg, gpsBorder, gpsFg) = when {
                    gpsError != null -> Triple(AgDestructiveBg, AgDestructive, AgDestructiveDark)
                    coords != null && coords.accuracy <= fh.settings.maxAccuracyMeters ->
                        Triple(AgAccentGreenBg, AgAccentGreen.copy(alpha = 0.4f), AgAccentGreenDark)
                    coords != null -> Triple(AgWarningBg, AgWarning.copy(alpha = 0.4f), AgWarningDark)
                    else -> Triple(AgCardWhite, AgBorder, AgMutedText)
                }
                Surface(
                    color = gpsBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, gpsBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val statusText = when {
                            gpsBusy -> "Getting your location…"
                            gpsError != null -> gpsError!!
                            coords != null -> {
                                val acc = coords.accuracy.roundToInt()
                                val warn = if (acc > fh.settings.maxAccuracyMeters) " — low accuracy, audit will be flagged for review" else ""
                                "Location captured (±$acc m)$warn"
                            }
                            else -> "Location not captured yet"
                        }
                        Text(
                            text = statusText,
                            color = gpsFg,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { scope.launch { captureRealLocation() } },
                            enabled = !gpsBusy,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, gpsFg),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Text(
                                text = if (gpsError != null) "Try again" else "Refresh",
                                color = gpsFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3-Step Progress Indicator Bar
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StepPill(1, "Audit Details", activeStep >= 1)
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), color = AgBorder)
                        StepPill(2, "Products", activeStep >= 2)
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), color = AgBorder)
                        StepPill(3, "Photos & Submit", activeStep >= 3)
                    }
                }

                // Main Card: Select Distributor + Select Product + Inputs
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Select Distributor
                        var distExpanded by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Select Distributor *", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            ExposedDropdownMenuBox(
                                expanded = distExpanded,
                                onExpandedChange = { distExpanded = !distExpanded }
                            ) {
                                val distLabel = selectedDistributor?.let { d ->
                                    val c = cities.find { it.id == d.cityId }
                                    "${d.setupName} – ${c?.name ?: ""}"
                                } ?: "Select Distributor..."
                                OutlinedTextField(
                                    value = distLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = distExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("select_distributor_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = distExpanded,
                                    onDismissRequest = { distExpanded = false }
                                ) {
                                    scopedDistributors.forEach { dist ->
                                        val c = cities.find { it.id == dist.cityId }
                                        DropdownMenuItem(
                                            text = { Text("${dist.setupName} – ${c?.name ?: ""}", fontSize = 13.sp) },
                                            onClick = {
                                                selectedDistributorId = dist.id
                                                selectedProductId = ""
                                                productSearchText = ""
                                                productDropdownOpen = false
                                                distExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Select Product Search & Dropdown
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Select Product *", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = productSearchText,
                                onValueChange = {
                                    productSearchText = it
                                    productDropdownOpen = true
                                    if (selectedProductId.isNotEmpty()) selectedProductId = ""
                                },
                                enabled = selectedDistributor != null,
                                placeholder = {
                                    Text(
                                        if (selectedDistributor != null) "Search Product..." else "Select a distributor first...",
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (productSearchText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            productSearchText = ""
                                            selectedProductId = ""
                                            productDropdownOpen = true
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    } else if (selectedDistributor != null) {
                                        IconButton(onClick = { productDropdownOpen = !productDropdownOpen }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Toggle products")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_search_input")
                            )

                            if (selectedDistributor != null && productDropdownOpen) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AgBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        if (matchingSearchProducts.isEmpty()) {
                                            Text(
                                                text = "No matching products found",
                                                color = AgMutedText,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp)
                                            )
                                        } else {
                                            matchingSearchProducts.take(10).forEachIndexed { idx, prd ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (idx == 0) AgSoftBlueBg else AgCardWhite)
                                                        .clickable {
                                                            selectedProductId = prd.id
                                                            productSearchText = prd.name
                                                            productDropdownOpen = false
                                                            qtyInCtn = ""
                                                            qtyInBox = ""
                                                            dispatchDate = ""
                                                            stacking = ""
                                                            remarks = ""
                                                        }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(AgActionBlue, RoundedCornerShape(8.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(prd.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            text = "Box/CTN: ${prd.boxesPerCtn} · Unit/Box: ${prd.unitsCountPerBox.takeIf { it > 0 } ?: prd.unitPerBox} · Rs. ${prd.pricePerBoxRs.roundToInt()}",
                                                            color = AgMutedText,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                                if (idx < minOf(9, matchingSearchProducts.lastIndex)) {
                                                    HorizontalDivider(color = AgLightBorder)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Locked Product Spec Banner + Inputs
                        if (selectedProduct != null) {
                            Surface(
                                color = AgSoftBlueBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFC9DCFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(AgCardWhite, RoundedCornerShape(10.dp))
                                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(selectedProduct.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                            Text(
                                                text = "Box per CTN: ${selectedProduct.boxesPerCtn} | Unit per Box: ${selectedProduct.unitsCountPerBox.takeIf { it > 0 } ?: selectedProduct.unitPerBox} | Price per Box: Rs. ${selectedProduct.pricePerBoxRs.roundToInt()}",
                                                color = AgForeground,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.Lock, contentDescription = "Locked Master Specs", tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = qtyInCtn,
                                    onValueChange = { qtyInCtn = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("${fh.label("qtyInCtn", "Qty in CTN")} *", fontSize = 11.sp) },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = qtyInBox,
                                    onValueChange = { qtyInBox = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("${fh.label("qtyInBox", "Qty in Box")} *", fontSize = 11.sp) },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (fh.visible("dispatchDate")) {
                                OutlinedTextField(
                                    value = dispatchDate,
                                    onValueChange = { dispatchDate = it },
                                    label = {
                                        Text(
                                            fh.label("dispatchDate", "Dispatch Date") +
                                                if (fh.required("dispatchDate", true)) " *" else "",
                                            fontSize = 11.sp
                                        )
                                    },
                                    placeholder = { Text(fh.placeholder("dispatchDate", "e.g. 30/10/2026 or JUL-26"), fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (fh.visible("stacking")) {
                                    var stExpanded by remember { mutableStateOf(false) }
                                    val stOptions = fh.options("stacking", listOf("Yes", "No"))
                                    ExposedDropdownMenuBox(
                                        expanded = stExpanded,
                                        onExpandedChange = { stExpanded = !stExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = stacking.ifEmpty { "Select…" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = {
                                                Text(
                                                    fh.label("stacking", "Stacking") +
                                                        if (fh.required("stacking", true)) " *" else "",
                                                    fontSize = 11.sp
                                                )
                                            },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stExpanded) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = stExpanded,
                                            onDismissRequest = { stExpanded = false }
                                        ) {
                                            stOptions.forEach { o ->
                                                DropdownMenuItem(
                                                    text = { Text(o) },
                                                    onClick = {
                                                        stacking = o
                                                        stExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (fh.visible("remarks")) {
                                    OutlinedTextField(
                                        value = remarks,
                                        onValueChange = { remarks = it },
                                        label = {
                                            Text(
                                                fh.label("remarks", "Remarks") +
                                                    if (fh.required("remarks")) " *" else "",
                                                fontSize = 11.sp
                                            )
                                        },
                                        placeholder = { Text(fh.placeholder("remarks", "Optional notes"), fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "Calc: %.2f CTN · %s", liveTotalCtns, formatPKR(liveStockValueRs)),
                                    color = AgMutedText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Button(
                                    onClick = {
                                        if (qtyInCtn.isBlank() && qtyInBox.isBlank()) {
                                            onToast("Please enter Qty in CTN or Qty in Box before adding (enter 0 if none).")
                                            return@Button
                                        }
                                        if (!fh.settings.allowMultipleEntriesPerProduct && savedItems.any { it.productId == selectedProduct.id }) {
                                            onToast("This product is already in the list. Remove it first to enter it again.")
                                            return@Button
                                        }
                                        if (fh.visible("dispatchDate") && fh.required("dispatchDate", true) && dispatchDate.isBlank()) {
                                            onToast("${fh.label("dispatchDate", "Dispatch date")} is required.")
                                            return@Button
                                        }
                                        if (fh.visible("stacking") && fh.required("stacking", true) && stacking.isBlank()) {
                                            onToast("Please choose ${fh.label("stacking", "stacking").lowercase()}.")
                                            return@Button
                                        }
                                        if (fh.visible("remarks") && fh.required("remarks") && remarks.isBlank()) {
                                            onToast("${fh.label("remarks", "Remarks")} is required.")
                                            return@Button
                                        }

                                        val newItem = DistributorAuditLineItem(
                                            id = "line-${System.currentTimeMillis()}",
                                            productId = selectedProduct.id,
                                            sku = selectedProduct.sku,
                                            productName = selectedProduct.name,
                                            boxesPerCtn = selectedProduct.boxesPerCtn,
                                            unitPerBox = (selectedProduct.unitsCountPerBox.takeIf { it > 0 } ?: selectedProduct.unitPerBox).toString(),
                                            unitsCountPerBox = selectedProduct.unitsCountPerBox,
                                            pricePerBoxRs = selectedProduct.pricePerBoxRs,
                                            qtyInCtn = parsedCtn,
                                            qtyInBox = parsedBox,
                                            totalCtns = liveTotalCtns,
                                            dispatchDate = if (fh.visible("dispatchDate")) dispatchDate.trim() else "",
                                            stacking = if (fh.visible("stacking")) stacking.trim() else "",
                                            remarks = if (fh.visible("remarks")) remarks.trim() else "",
                                            stockValueRs = liveStockValueRs
                                        )
                                        savedItems = savedItems + newItem
                                        selectedProductId = ""
                                        productSearchText = ""
                                        qtyInCtn = ""
                                        qtyInBox = ""
                                        dispatchDate = ""
                                        stacking = ""
                                        remarks = ""
                                        onToast(
                                            String.format(
                                                Locale.US,
                                                "Added %s (%.2f CTN · %s)",
                                                selectedProduct.name,
                                                liveTotalCtns,
                                                formatPKR(liveStockValueRs)
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier.testTag("add_product_line_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Product", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Added Products Card
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                            Text("Added Products", color = AgPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        HorizontalDivider(color = AgLightBorder)

                        if (savedItems.isEmpty()) {
                            Text(
                                text = "No products added yet. Select a distributor and product above, then tap + Add Product.",
                                color = AgMutedText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            )
                        } else {
                            savedItems.forEachIndexed { idx, item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("#${idx + 1}", color = AgMutedText, fontSize = 11.sp)
                                            Text(item.productName, color = AgForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(
                                            onClick = { savedItems = savedItems.filterNot { it.id == item.id } },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.productName}", tint = AgDestructive, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        SmallKv("CTN", item.qtyInCtn.toString(), Modifier.weight(1f))
                                        SmallKv("Box", item.qtyInBox.toString(), Modifier.weight(1f))
                                        SmallKv("Total CTNs", String.format(Locale.US, "%.2f", item.totalCtns), Modifier.weight(1f))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        SmallKv("Dispatch", item.dispatchDate.ifEmpty { "—" }, Modifier.weight(1f))
                                        SmallKv("Stacking", item.stacking.ifEmpty { "—" }, Modifier.weight(1f))
                                        SmallKv("Value", formatPKR(item.stockValueRs), Modifier.weight(1f))
                                    }
                                    if (item.remarks.isNotEmpty()) {
                                        Text(item.remarks, color = AgMutedText, fontSize = 12.sp)
                                    }
                                }
                                if (idx < savedItems.lastIndex) {
                                    HorizontalDivider(color = AgLightBorder)
                                }
                            }
                        }

                        // Totals Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgSoftBlueBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "Total CTN: %.2f", grandTotalCtns),
                                color = AgPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Total Value: ${formatPKR(grandTotalValueRs)}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Capture Images Card
                if (fh.settings.allowPhotoCapture && fh.visible("photos")) {
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AgLightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${fh.label("photos", "Capture Images")} ${if (fh.settings.requirePhoto) "(Required)" else "(Optional)"} ${capturedImages.size}/${fh.settings.maxPhotos}",
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (capturedImages.size < fh.settings.maxPhotos) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) cameraLauncher.launch(null)
                                            else cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tap to Capture", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            galleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Upload Gallery", color = AgActionBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (capturedImages.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    capturedImages.forEach { img ->
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, AgBorder, RoundedCornerShape(12.dp))
                                        ) {
                                            DataUrlImage(img.url, img.caption, Modifier.fillMaxSize())
                                            IconButton(
                                                onClick = { capturedImages = capturedImages.filterNot { it.id == img.id } },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(AgForeground.copy(alpha = 0.8f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar: Save Draft / Submit Audit
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%d product%s · %.2f CTN",
                                    savedItems.size,
                                    if (savedItems.size == 1) "" else "s",
                                    grandTotalCtns
                                ),
                                color = AgMutedText,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatPKR(grandTotalValueRs),
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val itemsArr = JSONArray()
                                        savedItems.forEach { item ->
                                            itemsArr.put(JSONObject().apply {
                                                put("id", item.id)
                                                put("productId", item.productId)
                                                put("sku", item.sku)
                                                put("productName", item.productName)
                                                put("boxesPerCtn", item.boxesPerCtn)
                                                put("unitPerBox", item.unitPerBox)
                                                put("unitsCountPerBox", item.unitsCountPerBox)
                                                put("pricePerBoxRs", item.pricePerBoxRs)
                                                put("qtyInCtn", item.qtyInCtn)
                                                put("qtyInBox", item.qtyInBox)
                                                put("totalCtns", item.totalCtns)
                                                put("dispatchDate", item.dispatchDate)
                                                put("stacking", item.stacking)
                                                put("remarks", item.remarks)
                                                put("stockValueRs", item.stockValueRs)
                                            })
                                        }
                                        val imgsArr = JSONArray()
                                        capturedImages.forEach { img ->
                                            imgsArr.put(JSONObject().apply {
                                                put("id", img.id)
                                                put("url", img.url)
                                                put("caption", img.caption)
                                                put("capturedAt", img.capturedAt)
                                            })
                                        }
                                        val draft = JSONObject().apply {
                                            put("selectedDistributorId", selectedDistributorId)
                                            put("savedItems", itemsArr)
                                            put("capturedImages", imgsArr)
                                            put("updatedAt", currentIsoTimestamp())
                                        }
                                        apiClient.saveDraftString(distDraftKey, draft.toString())
                                        onToast("Audit draft saved to mobile device.")
                                    } catch (_: Exception) {
                                        onToast("Could not save draft.")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_distributor_draft_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Draft", color = AgActionBlue, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (selectedDistributor == null) {
                                        onToast("Please select a distributor before submitting.")
                                        return@Button
                                    }
                                    if (submitting) return@Button
                                    if (fh.settings.allowPhotoCapture && fh.settings.requirePhoto && capturedImages.isEmpty()) {
                                        onToast("At least one photo is required before submitting.")
                                        return@Button
                                    }
                                    if (savedItems.isEmpty()) {
                                        onToast("Please add at least one product before submitting.")
                                        return@Button
                                    }

                                    submitting = true
                                    scope.launch {
                                        val fix = captureRealLocation()
                                        if (fix == null) {
                                            onToast("Location is required to submit this audit. Allow location and tap \"Try again\".")
                                            submitting = false
                                            return@launch
                                        }
                                        val mLat = (fix.lat * 1_000_000).roundToInt() / 1_000_000.0
                                        val mLng = (fix.lng * 1_000_000).roundToInt() / 1_000_000.0
                                        val mAcc = fix.accuracy.roundToInt()
                                        val distMeters = calculateDistanceMeters(
                                            mLat,
                                            mLng,
                                            selectedDistributor.lat,
                                            selectedDistributor.lng
                                        )
                                        val allowedRadius = fh.settings.allowedRadiusMeters
                                        val matched = distMeters <= allowedRadius && mAcc <= fh.settings.maxAccuracyMeters

                                        val city = cities.find { it.id == selectedDistributor.cityId }
                                        val territory = territories.find { it.id == selectedDistributor.territoryId }
                                        val primaryGroupCode = if (selectedDistributor.groupIds.contains("grp-b")) "B" else "A"
                                        val primaryGroupName = if (selectedDistributor.groupIds.contains("grp-b")) "Group B" else "Group A"

                                        val newReport = DistributorProductAuditReport(
                                            id = submitKey,
                                            reportCode = makeReportCode("AUD-DST"),
                                            formId = DISTRIBUTOR_FORM_ID,
                                            distributorId = selectedDistributor.id,
                                            distributorCode = selectedDistributor.code,
                                            distributorSetupName = selectedDistributor.setupName.uppercase(Locale.US),
                                            distributorOwnerName = selectedDistributor.distributorName,
                                            groupCode = primaryGroupCode,
                                            groupName = primaryGroupName,
                                            cityId = selectedDistributor.cityId,
                                            cityName = (city?.name ?: "PUNJAB").uppercase(Locale.US),
                                            territoryId = selectedDistributor.territoryId,
                                            territoryCode = territory?.code ?: "",
                                            territoryName = territory?.name ?: "",
                                            auditorId = activeAuditor.id,
                                            auditorCode = activeAuditor.code,
                                            auditorName = activeAuditor.fullName,
                                            auditorUsername = activeAuditor.username,
                                            reportDateDisplay = currentReportDateDisplay(),
                                            submittedDate = currentIsoDate(),
                                            submittedTime = currentTimePkt(),
                                            submittedAtISO = currentIsoTimestamp(),
                                            mobileLat = mLat,
                                            mobileLng = mLng,
                                            mobileAccuracyMeters = mAcc,
                                            distributorLat = selectedDistributor.lat,
                                            distributorLng = selectedDistributor.lng,
                                            distanceMeters = distMeters,
                                            allowedRadiusMeters = allowedRadius,
                                            locationMatched = matched,
                                            items = savedItems,
                                            totalCtns = grandTotalCtns,
                                            totalStockValueRs = grandTotalValueRs,
                                            capturedImages = capturedImages,
                                            overallComments = "Submitted from Mobile App by ${activeAuditor.fullName} (@${activeAuditor.username}).",
                                            planId = planLink?.planId,
                                            planItemId = planLink?.itemId
                                        )

                                        try {
                                            onSubmitReport(newReport)
                                            submitKey = newSubmitKey()
                                            apiClient.removeDraft(distDraftKey)
                                            savedItems = emptyList()
                                            capturedImages = emptyList()
                                            selectedProductId = ""
                                            productSearchText = ""
                                            closeForm()
                                        } catch (e: Exception) {
                                            onToast(e.message ?: "Could not submit. Your entries are kept on this phone; try again.")
                                        } finally {
                                            submitting = false
                                        }
                                    }
                                },
                                enabled = savedItems.isNotEmpty() && !submitting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("submit_distributor_audit_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (submitting) "Submitting…" else "Submit Audit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPill(number: Int, label: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(if (active) AgActionBlue else AgBackground, CircleShape)
                .border(1.dp, if (active) AgActionBlue else AgBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = if (active) Color.White else AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = if (active) AgActionBlue else AgMutedText,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun SmallKv(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = AgMutedText, fontSize = 10.sp)
        Text(value, color = AgForeground, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

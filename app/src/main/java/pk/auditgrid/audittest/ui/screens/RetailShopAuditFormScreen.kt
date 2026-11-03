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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.api.AuditGridJsonParsers
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*
import kotlin.math.max
import kotlin.math.roundToInt

private val DISPLAY_OPTIONS = listOf("N/A", "Poor", "Average", "Good", "Excellent")
private val REGULARITY_OPTIONS = listOf("Yes", "No")

fun hasSavedRetailDraft(apiClient: AuditGridApiClient, auditorId: String): Boolean {
    return try {
        val raw = apiClient.getDraftString("auditgrid_retail_shop_audit_draft_v1_$auditorId") ?: return false
        val obj = JSONObject(raw)
        val audits = obj.optJSONObject("auditsBySubLocation") ?: return false
        audits.length() > 0
    } catch (_: Exception) {
        false
    }
}

@Composable
fun RetailShopAuditFormScreen(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    locations: List<LocationItem>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    formConfigs: FormConfigMap,
    apiClient: AuditGridApiClient,
    preset: AuditPreset?,
    planLink: PlanLink?,
    onSubmitReport: suspend (RetailMarketShopAuditReport) -> RetailMarketShopAuditReport,
    onFinished: () -> Unit,
    onBackToForms: (() -> Unit)?,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageKey = "auditgrid_retail_shop_audit_draft_v1_${activeAuditor.id}"
    val fh = remember(formConfigs) { makeFormHelper(formConfigs[RETAIL_FORM_ID]) }

    val assignedCities = remember(cities, activeAuditor.id) {
        cities.filter { activeAuditor.assignedCityIds.contains(it.id) }
    }
    val assignedTerritories = remember(territories, activeAuditor.id) {
        territories.filter { activeAuditor.assignedTerritoryIds.contains(it.id) }
    }
    val assignedTerritoryIdsSet = remember(assignedTerritories) {
        assignedTerritories.map { it.id }.toSet()
    }
    val assignedLocations = remember(locations, assignedTerritoryIdsSet) {
        locations.filter { assignedTerritoryIdsSet.contains(it.territoryId) }
    }
    val assignedSubLocations = remember(subLocations, assignedTerritoryIdsSet) {
        subLocations.filter { assignedTerritoryIdsSet.contains(it.territoryId) }
    }

    var selectedCityId by remember { mutableStateOf("") }
    var selectedTerritoryId by remember { mutableStateOf("") }
    var selectedLocationId by remember { mutableStateOf("") }
    var selectedSubLocationId by remember { mutableStateOf("") }
    var auditsBySubLocation by remember {
        mutableStateOf<Map<String, Map<String, RetailShopAuditItem>>>(emptyMap())
    }
    var statusBannerText by remember { mutableStateOf<String?>(null) }

    // Load saved draft on mount
    LaunchedEffect(activeAuditor.id) {
        try {
            val raw = apiClient.getDraftString(storageKey)
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                selectedCityId = obj.optString("selectedCityId")
                selectedTerritoryId = obj.optString("selectedTerritoryId")
                selectedLocationId = obj.optString("selectedLocationId")
                selectedSubLocationId = obj.optString("selectedSubLocationId")
                val auditsObj = obj.optJSONObject("auditsBySubLocation")
                if (auditsObj != null) {
                    val map = mutableMapOf<String, Map<String, RetailShopAuditItem>>()
                    val subKeys = auditsObj.keys()
                    while (subKeys.hasNext()) {
                        val subId = subKeys.next()
                        val shopMapObj = auditsObj.optJSONObject(subId) ?: continue
                        val shopMap = mutableMapOf<String, RetailShopAuditItem>()
                        val shopKeys = shopMapObj.keys()
                        while (shopKeys.hasNext()) {
                            val shId = shopKeys.next()
                            shopMapObj.optJSONObject(shId)?.let { itemJson ->
                                shopMap[shId] = AuditGridJsonParsers.parseRetailShopAuditItem(itemJson)
                            }
                        }
                        if (shopMap.isNotEmpty()) map[subId] = shopMap
                    }
                    auditsBySubLocation = map
                    if (map.isNotEmpty()) {
                        statusBannerText = "Draft restored automatically from device!"
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Apply preset from plan if provided
        preset?.subLocationId?.let { preSubId ->
            assignedSubLocations.find { it.id == preSubId }?.let { sl ->
                selectedCityId = sl.cityId
                selectedTerritoryId = sl.territoryId
                selectedLocationId = sl.locationId
                selectedSubLocationId = sl.id
            }
        }
    }

    // Persist draft changes
    fun saveDraftToPrefs(
        cId: String = selectedCityId,
        tId: String = selectedTerritoryId,
        lId: String = selectedLocationId,
        sId: String = selectedSubLocationId,
        audits: Map<String, Map<String, RetailShopAuditItem>> = auditsBySubLocation
    ) {
        try {
            val auditsObj = JSONObject()
            for ((subId, shopMap) in audits) {
                val sObj = JSONObject()
                for ((shId, item) in shopMap) {
                    val imgsArr = JSONArray()
                    item.capturedImages.forEach { img ->
                        imgsArr.put(JSONObject().apply {
                            put("id", img.id)
                            put("url", img.url)
                            put("caption", img.caption)
                            put("capturedAt", img.capturedAt)
                        })
                    }
                    sObj.put(shId, JSONObject().apply {
                        put("shopId", item.shopId)
                        put("shopCode", item.shopCode)
                        put("shopName", item.shopName)
                        put("ownerName", item.ownerName)
                        put("phone", item.phone)
                        put("addressLine", item.addressLine)
                        put("shopCategory", item.shopCategory)
                        put("productACount", item.productACount)
                        put("productBCount", item.productBCount)
                        put("displayRating", item.displayRating)
                        put("packedProductQty", item.packedProductQty)
                        put("regularity", item.regularity)
                        put("remarks", item.remarks)
                        put("capturedImages", imgsArr)
                        put("auditStatus", item.auditStatus)
                        put("auditedAt", item.auditedAt)
                        put("mobileLat", item.mobileLat)
                        put("mobileLng", item.mobileLng)
                        put("mobileAccuracyMeters", item.mobileAccuracyMeters)
                        put("shopLat", item.shopLat)
                        put("shopLng", item.shopLng)
                        put("distanceMeters", item.distanceMeters)
                        put("allowedRadiusMeters", item.allowedRadiusMeters)
                        put("locationMatched", item.locationMatched)
                    })
                }
                auditsObj.put(subId, sObj)
            }
            val payload = JSONObject().apply {
                put("selectedCityId", cId)
                put("selectedTerritoryId", tId)
                put("selectedLocationId", lId)
                put("selectedSubLocationId", sId)
                put("auditsBySubLocation", auditsObj)
                put("updatedAt", currentIsoTimestamp())
            }
            apiClient.saveDraftString(storageKey, payload.toString())
        } catch (_: Exception) {
        }
    }

    var subLocationSearchQuery by remember { mutableStateOf("") }
    var subLocationDropdownOpen by remember { mutableStateOf(false) }
    var shopFilterQuery by remember { mutableStateOf("") }
    var activePopupShop by remember { mutableStateOf<Shop?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitKey by remember { mutableStateOf(newSubmitKey()) }
    var completedSummaryReport by remember { mutableStateOf<RetailMarketShopAuditReport?>(null) }

    BackHandler(enabled = onBackToForms != null) {
        onBackToForms?.invoke()
    }

    val summary = completedSummaryReport
    if (summary != null) {
        RetailAuditSummaryView(
            report = summary,
            onDone = {
                completedSummaryReport = null
                onFinished()
            }
        )
        return
    }

    val filteredTerritories = assignedTerritories.filter { it.cityId == selectedCityId }
    val filteredLocations = assignedLocations.filter { it.territoryId == selectedTerritoryId }
    val filteredSubLocations = assignedSubLocations.filter { it.locationId == selectedLocationId }

    val matchingDirectSubLocations = assignedSubLocations.filter { sub ->
        if (subLocationSearchQuery.isBlank()) true
        else {
            val q = subLocationSearchQuery.lowercase()
            val loc = locations.find { it.id == sub.locationId }
            val ter = territories.find { it.id == sub.territoryId }
            val city = cities.find { it.id == sub.cityId }
            sub.name.lowercase().contains(q) ||
                sub.code.lowercase().contains(q) ||
                (loc?.name?.lowercase()?.contains(q) == true) ||
                (ter?.name?.lowercase()?.contains(q) == true) ||
                (city?.name?.lowercase()?.contains(q) == true)
        }
    }

    val selectedSubLocation = assignedSubLocations.find { it.id == selectedSubLocationId }
    val subLocationShops = if (selectedSubLocation != null) {
        shops.filter { sh ->
            sh.subLocationId == selectedSubLocation.id &&
                sh.status == "Active" &&
                (preset?.shopIds.isNullOrEmpty() ||
                    selectedSubLocation.id != preset?.subLocationId ||
                    preset?.shopIds?.contains(sh.id) == true)
        }
    } else emptyList()

    val visibleSubLocationShops = subLocationShops.filter { sh ->
        if (shopFilterQuery.isBlank()) true
        else {
            val q = shopFilterQuery.lowercase()
            sh.name.lowercase().contains(q) ||
                sh.code.lowercase().contains(q) ||
                sh.ownerName.lowercase().contains(q) ||
                sh.addressLine.lowercase().contains(q)
        }
    }

    val currentSubLocationAuditsMap = auditsBySubLocation[selectedSubLocationId] ?: emptyMap()
    val doneShopsCount = subLocationShops.count { currentSubLocationAuditsMap[it.id]?.auditStatus == "Done" }

    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Royal Blue Top Header
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
                    if (onBackToForms != null) {
                        IconButton(
                            onClick = onBackToForms,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Audit Forms",
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        text = "Retail Audit",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onBackToForms != null) {
                    IconButton(
                        onClick = onBackToForms,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                statusBannerText?.let { banner ->
                    Surface(
                        color = AgAccentGreenBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
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
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AgAccentGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = banner,
                                    color = AgAccentGreenDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { statusBannerText = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = AgAccentGreenDark,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Cascading Geography Card
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
                        GeographyDropdown(
                            label = "City *",
                            selectedLabel = assignedCities.find { it.id == selectedCityId }?.name ?: "Select City...",
                            enabled = true,
                            options = assignedCities.map { it.id to it.name },
                            onSelect = { cId ->
                                selectedCityId = cId
                                selectedTerritoryId = ""
                                selectedLocationId = ""
                                selectedSubLocationId = ""
                                saveDraftToPrefs(cId = cId, tId = "", lId = "", sId = "")
                            }
                        )
                        GeographyDropdown(
                            label = "Territory *",
                            selectedLabel = when {
                                selectedCityId.isBlank() -> "Select City First..."
                                else -> filteredTerritories.find { it.id == selectedTerritoryId }?.name ?: "Select Territory..."
                            },
                            enabled = selectedCityId.isNotBlank(),
                            options = filteredTerritories.map { it.id to it.name },
                            onSelect = { tId ->
                                selectedTerritoryId = tId
                                selectedLocationId = ""
                                selectedSubLocationId = ""
                                saveDraftToPrefs(tId = tId, lId = "", sId = "")
                            }
                        )
                        GeographyDropdown(
                            label = "Location *",
                            selectedLabel = when {
                                selectedTerritoryId.isBlank() -> "Select Territory First..."
                                else -> filteredLocations.find { it.id == selectedLocationId }?.name ?: "Select Location..."
                            },
                            enabled = selectedTerritoryId.isNotBlank(),
                            options = filteredLocations.map { it.id to it.name },
                            onSelect = { lId ->
                                selectedLocationId = lId
                                selectedSubLocationId = ""
                                saveDraftToPrefs(lId = lId, sId = "")
                            }
                        )
                        GeographyDropdown(
                            label = "Sub Location *",
                            selectedLabel = when {
                                selectedLocationId.isBlank() -> "Select Location First..."
                                else -> filteredSubLocations.find { it.id == selectedSubLocationId }?.name ?: "Select Sub Location..."
                            },
                            enabled = selectedLocationId.isNotBlank(),
                            options = filteredSubLocations.map { it.id to it.name },
                            onSelect = { sId ->
                                selectedSubLocationId = sId
                                saveDraftToPrefs(sId = sId)
                            }
                        )

                        // Direct Search Sub Location
                        OutlinedTextField(
                            value = subLocationSearchQuery,
                            onValueChange = {
                                subLocationSearchQuery = it
                                subLocationDropdownOpen = true
                            },
                            placeholder = { Text("Search Sub Location...", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (subLocationSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        subLocationSearchQuery = ""
                                        subLocationDropdownOpen = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (subLocationDropdownOpen && matchingDirectSubLocations.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgActionBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    matchingDirectSubLocations.take(8).forEachIndexed { idx, sub ->
                                        val city = cities.find { it.id == sub.cityId }
                                        val ter = territories.find { it.id == sub.territoryId }
                                        val loc = locations.find { it.id == sub.locationId }
                                        val shCount = shops.count { it.subLocationId == sub.id && it.status == "Active" }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCityId = sub.cityId
                                                    selectedTerritoryId = sub.territoryId
                                                    selectedLocationId = sub.locationId
                                                    selectedSubLocationId = sub.id
                                                    subLocationSearchQuery = sub.name
                                                    subLocationDropdownOpen = false
                                                    saveDraftToPrefs(sub.cityId, sub.territoryId, sub.locationId, sub.id)
                                                }
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(sub.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                CodeBadge("$shCount Shops", bg = AgSoftBlueBg, fg = AgActionBlue)
                                            }
                                            Text(
                                                text = "${city?.name ?: ""} → ${ter?.name ?: ""} → ${loc?.name ?: ""}",
                                                color = AgMutedText,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (idx < minOf(7, matchingDirectSubLocations.lastIndex)) {
                                            HorizontalDivider(color = AgLightBorder)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Shops in Sub Location
                if (selectedSubLocation != null) {
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
                            OutlinedTextField(
                                value = shopFilterQuery,
                                onValueChange = { shopFilterQuery = it },
                                placeholder = { Text("Search Shops...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Shops in ${selectedSubLocation.name} (${subLocationShops.size})",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            if (visibleSubLocationShops.isEmpty()) {
                                Text(
                                    text = "No shops found in this sub-location.",
                                    color = AgMutedText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(AgSoftBlueBg)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                            Text("Shop", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                                            Text("Owner / Address", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                                            Text("Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                                        }
                                        visibleSubLocationShops.forEachIndexed { idx, shop ->
                                            val isDone = currentSubLocationAuditsMap[shop.id]?.auditStatus == "Done"
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { activePopupShop = shop }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.width(24.dp)
                                                )
                                                Column(modifier = Modifier.weight(1.3f)) {
                                                    Text(shop.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgForeground)
                                                    Text("${shop.code} · ${shop.phone}", fontSize = 10.sp, color = AgMutedText, fontFamily = FontFamily.Monospace)
                                                }
                                                Column(modifier = Modifier.weight(1.3f)) {
                                                    Text(shop.ownerName, fontSize = 11.sp, color = AgForeground)
                                                    Text(shop.addressLine, fontSize = 10.sp, color = AgMutedText)
                                                }
                                                Box(
                                                    modifier = Modifier.width(64.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Surface(
                                                        color = if (isDone) AgAccentGreenBg else AgSoftBlueBorder,
                                                        shape = RoundedCornerShape(999.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isDone) "Done" else "Pending",
                                                            color = if (isDone) AgAccentGreenDark else AgActionBlue,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (idx < visibleSubLocationShops.lastIndex) {
                                                HorizontalDivider(color = AgLightBorder)
                                            }
                                        }
                                    }
                                }
                            }

                            // Audited Progress Bar
                            Surface(
                                color = AgSoftBlueBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgSoftBlueBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AgAccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$doneShopsCount / ${subLocationShops.size} Shops Audited",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (doneShopsCount == 0) {
                                Button(
                                    onClick = {
                                        val firstPending = subLocationShops.find {
                                            currentSubLocationAuditsMap[it.id]?.auditStatus != "Done"
                                        } ?: subLocationShops.firstOrNull()
                                        if (firstPending != null) activePopupShop = firstPending
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("start_retail_audit_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (submitting) return@Button
                                        val auditedItems = subLocationShops.mapNotNull { sh ->
                                            currentSubLocationAuditsMap[sh.id]?.takeIf { it.auditStatus == "Done" }
                                        }
                                        if (auditedItems.isEmpty()) {
                                            onToast("Please audit at least one shop before submitting.")
                                            return@Button
                                        }
                                        val cityObj = cities.find { it.id == selectedSubLocation.cityId }
                                        val terObj = territories.find { it.id == selectedSubLocation.territoryId }
                                        val locObj = locations.find { it.id == selectedSubLocation.locationId }

                                        val newReport = RetailMarketShopAuditReport(
                                            id = submitKey,
                                            reportCode = makeReportCode("AUD-SHP"),
                                            formId = RETAIL_FORM_ID,
                                            cityId = selectedSubLocation.cityId,
                                            cityName = cityObj?.name ?: "",
                                            territoryId = selectedSubLocation.territoryId,
                                            territoryCode = terObj?.code ?: "",
                                            territoryName = terObj?.name ?: "",
                                            locationId = selectedSubLocation.locationId,
                                            locationCode = locObj?.code ?: "",
                                            locationName = locObj?.name ?: "",
                                            subLocationId = selectedSubLocation.id,
                                            subLocationCode = selectedSubLocation.code,
                                            subLocationName = selectedSubLocation.name,
                                            auditorId = activeAuditor.id,
                                            auditorCode = activeAuditor.code,
                                            auditorName = activeAuditor.fullName,
                                            auditorUsername = activeAuditor.username,
                                            reportDateDisplay = currentReportDateDisplay(),
                                            submittedDate = currentIsoDate(),
                                            submittedTime = currentTimePkt(),
                                            submittedAtISO = currentIsoTimestamp(),
                                            totalShopsInSubLocation = subLocationShops.size,
                                            auditedShopsCount = auditedItems.size,
                                            totalProductACount = auditedItems.sumOf { it.productACount },
                                            totalProductBCount = auditedItems.sumOf { it.productBCount },
                                            totalPackedProductQty = auditedItems.sumOf { it.packedProductQty },
                                            matchedLocationShopsCount = auditedItems.count { it.locationMatched },
                                            shopAudits = auditedItems,
                                            planId = planLink?.planId,
                                            planItemId = planLink?.itemId
                                        )

                                        submitting = true
                                        scope.launch {
                                            try {
                                                val saved = onSubmitReport(newReport)
                                                val nextAudits = auditsBySubLocation - selectedSubLocation.id
                                                auditsBySubLocation = nextAudits
                                                saveDraftToPrefs(audits = nextAudits)
                                                submitKey = newSubmitKey()
                                                statusBannerText = null
                                                completedSummaryReport = saved
                                            } catch (e: Exception) {
                                                onToast(e.message ?: "Could not submit. Your answers are saved on this phone; try again.")
                                            } finally {
                                                submitting = false
                                            }
                                        }
                                    },
                                    enabled = !submitting,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("submit_retail_audit_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (submitting) "Submitting…" else "Submit Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AgLightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = AgActionBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Select a Sub Location to View Shops",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose City → Territory → Location → Sub Location above, or use Search Sub Location.",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    val popupShop = activePopupShop
    if (popupShop != null && selectedSubLocation != null) {
        ShopAuditPopupDialog(
            shop = popupShop,
            existingAudit = currentSubLocationAuditsMap[popupShop.id],
            fh = fh,
            onDismiss = { activePopupShop = null },
            onSaved = { savedItem ->
                val updatedSubMap = (auditsBySubLocation[selectedSubLocation.id] ?: emptyMap()) + (popupShop.id to savedItem)
                val nextAudits = auditsBySubLocation + (selectedSubLocation.id to updatedSubMap)
                auditsBySubLocation = nextAudits
                saveDraftToPrefs(audits = nextAudits)
                activePopupShop = null
                statusBannerText = "Audit saved successfully!"
                onToast("Audit saved for \"${popupShop.name}\" (Status: Done).")
            },
            onToast = onToast
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeographyDropdown(
    label: String,
    selectedLabel: String,
    enabled: Boolean,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 13.sp) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopAuditPopupDialog(
    shop: Shop,
    existingAudit: RetailShopAuditItem?,
    fh: FormHelper,
    onDismiss: () -> Unit,
    onSaved: (RetailShopAuditItem) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var productACount by remember { mutableStateOf(existingAudit?.productACount?.toString() ?: "") }
    var productBCount by remember { mutableStateOf(existingAudit?.productBCount?.toString() ?: "") }
    var displayRating by remember { mutableStateOf(existingAudit?.displayRating ?: "") }
    var packedQty by remember { mutableStateOf(existingAudit?.packedProductQty?.toString() ?: "") }
    var regularity by remember { mutableStateOf(existingAudit?.regularity ?: "") }
    var remarks by remember { mutableStateOf(existingAudit?.remarks ?: "") }
    var shopImages by remember { mutableStateOf(existingAudit?.capturedImages ?: emptyList()) }
    var isSavingWithGps by remember { mutableStateOf(false) }
    var formProblem by remember { mutableStateOf<String?>(null) }
    var shopGpsError by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (shopImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                val url = compressBitmapToDataUrl(bmp)
                shopImages = shopImages + AuditCapturedImage(
                    id = "shp-img-${System.currentTimeMillis()}",
                    url = url,
                    caption = "${shop.name} — Shop Display Photo",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is required to capture shop photos.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (shopImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val url = compressUriToDataUrl(context, uri)
                    if (url != null) {
                        shopImages = shopImages + AuditCapturedImage(
                            id = "shp-img-${System.currentTimeMillis()}",
                            url = url,
                            caption = "${shop.name} — Shop Display Photo",
                            capturedAt = currentTimePkt()
                        )
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgSoftBlueBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = AgRoyalBlue)
                        Text("Shop Audit", color = AgRoyalBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = AgLightBorder)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Metadata
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetaRow("Shop Name", shop.name)
                        MetaRow("Shop No", "${shop.code} (${shop.phone})")
                        MetaRow("Address", shop.addressLine)
                        MetaRow("Owner Name", shop.ownerName)
                    }
                    HorizontalDivider(color = AgLightBorder)

                    if (fh.visible("productACount")) {
                        StepperInputRow(
                            label = fh.label("productACount", "No. of Product-A"),
                            required = fh.required("productACount", true),
                            value = productACount,
                            onChange = { productACount = it }
                        )
                    }
                    if (fh.visible("productBCount")) {
                        StepperInputRow(
                            label = fh.label("productBCount", "No. of Product-B"),
                            required = fh.required("productBCount", true),
                            value = productBCount,
                            onChange = { productBCount = it }
                        )
                    }
                    if (fh.visible("displayRating")) {
                        SelectOptionRow(
                            label = fh.label("displayRating", "Display"),
                            required = fh.required("displayRating", true),
                            value = displayRating,
                            options = fh.options("displayRating", DISPLAY_OPTIONS),
                            onChange = { displayRating = it }
                        )
                    }
                    if (fh.visible("packedProductQty")) {
                        StepperInputRow(
                            label = fh.label("packedProductQty", "Packed Product Qty (Not in Display)"),
                            required = fh.required("packedProductQty", true),
                            value = packedQty,
                            onChange = { packedQty = it }
                        )
                    }
                    if (fh.visible("regularity")) {
                        SelectOptionRow(
                            label = fh.label("regularity", "Regularity"),
                            required = fh.required("regularity", true),
                            value = regularity,
                            options = fh.options("regularity", REGULARITY_OPTIONS),
                            onChange = { regularity = it }
                        )
                    }
                    if (fh.visible("remarks")) {
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = {
                                Text(
                                    fh.label("remarks", "Remarks") +
                                        if (fh.required("remarks")) " *" else ""
                                )
                            },
                            placeholder = { Text(fh.placeholder("remarks", "Optional notes")) },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (fh.settings.allowPhotoCapture && fh.visible("photos")) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${fh.label("photos", "Capture Image")} ${if (fh.settings.requirePhoto) "(Required)" else "(Optional)"} ${shopImages.size}/${fh.settings.maxPhotos}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Camera", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Gallery", fontSize = 12.sp)
                                }
                            }
                            if (shopImages.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    shopImages.forEach { img ->
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                                        ) {
                                            DataUrlImage(img.url, img.caption, Modifier.fillMaxSize())
                                            IconButton(
                                                onClick = { shopImages = shopImages.filterNot { it.id == img.id } },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    formProblem?.let {
                        Surface(
                            color = AgDestructiveBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgDestructive)
                        ) {
                            Text(it, color = AgDestructiveDark, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }

                    shopGpsError?.let {
                        Surface(
                            color = AgDestructiveBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgDestructive)
                        ) {
                            Text("$it Tap Save to try again.", color = AgDestructiveDark, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(2.dp, AgActionBlue),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text("Cancel", color = AgActionBlue, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                fun need(key: String, empty: Boolean, fallback: String): String? =
                                    if (fh.visible(key) && fh.required(key, true) && empty) "${fh.label(key, fallback)} is required." else null

                                val problem = need("productACount", productACount.isBlank(), "No. of Product-A")
                                    ?: need("productBCount", productBCount.isBlank(), "No. of Product-B")
                                    ?: need("displayRating", displayRating.isBlank(), "Display")
                                    ?: need("packedProductQty", packedQty.isBlank(), "Packed qty")
                                    ?: need("regularity", regularity.isBlank(), "Regularity")
                                    ?: if (fh.visible("remarks") && fh.required("remarks") && remarks.isBlank()) "${fh.label("remarks", "Remarks")} is required." else null
                                    ?: if (fh.settings.allowPhotoCapture && fh.settings.requirePhoto && shopImages.isEmpty()) "A photo of the shop is required." else null

                                if (problem != null) {
                                    formProblem = problem
                                    shopGpsError = null
                                    return@Button
                                }
                                formProblem = null
                                isSavingWithGps = true
                                scope.launch {
                                    try {
                                        val pos = getDevicePosition(context)
                                        val mLat = (pos.lat * 1_000_000).roundToInt() / 1_000_000.0
                                        val mLng = (pos.lng * 1_000_000).roundToInt() / 1_000_000.0
                                        val acc = pos.accuracy.roundToInt()
                                        val distMeters = calculateDistanceMeters(mLat, mLng, shop.lat, shop.lng)
                                        val allowedRadius = shop.allowedRadiusMeters.takeIf { it > 0 } ?: DEFAULT_ALLOWED_RADIUS_M
                                        val matched = distMeters <= allowedRadius && acc <= fh.settings.maxAccuracyMeters

                                        val item = RetailShopAuditItem(
                                            shopId = shop.id,
                                            shopCode = shop.code,
                                            shopName = shop.name,
                                            ownerName = shop.ownerName,
                                            phone = shop.phone,
                                            addressLine = shop.addressLine,
                                            shopCategory = shop.shopCategory,
                                            productACount = if (fh.visible("productACount")) (productACount.toIntOrNull() ?: 0) else 0,
                                            productBCount = if (fh.visible("productBCount")) (productBCount.toIntOrNull() ?: 0) else 0,
                                            displayRating = if (fh.visible("displayRating") && displayRating.isNotEmpty()) displayRating else "N/A",
                                            packedProductQty = if (fh.visible("packedProductQty")) (packedQty.toIntOrNull() ?: 0) else 0,
                                            regularity = if (fh.visible("regularity") && regularity.isNotEmpty()) regularity else "No",
                                            remarks = if (fh.visible("remarks")) remarks.trim() else "",
                                            capturedImages = shopImages,
                                            auditStatus = "Done",
                                            auditedAt = "${currentIsoDate()} ${currentTimePkt().removeSuffix(" PKT")}",
                                            mobileLat = mLat,
                                            mobileLng = mLng,
                                            mobileAccuracyMeters = acc,
                                            shopLat = shop.lat,
                                            shopLng = shop.lng,
                                            distanceMeters = distMeters,
                                            allowedRadiusMeters = allowedRadius,
                                            locationMatched = matched
                                        )
                                        onSaved(item)
                                    } catch (e: Exception) {
                                        val msg = if (e is GpsException) e.message ?: "Could not read your location."
                                        else "Could not read your location."
                                        shopGpsError = msg
                                        onToast(msg)
                                    } finally {
                                        isSavingWithGps = false
                                    }
                                }
                            },
                            enabled = !isSavingWithGps,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("save_shop_audit_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSavingWithGps) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AgMutedText, fontSize = 11.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = AgForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun StepperInputRow(
    label: String,
    required: Boolean,
    value: String,
    onChange: (String) -> Unit
) {
    fun bump(delta: Int) {
        val cur = value.toIntOrNull() ?: 0
        onChange(max(0, cur + delta).toString())
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (required) "$label *" else label,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                .background(AgBackground, RoundedCornerShape(10.dp))
        ) {
            IconButton(onClick = { bump(-1) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = AgActionBlue, modifier = Modifier.size(16.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
                placeholder = { Text("0", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(64.dp)
            )
            IconButton(onClick = { bump(1) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = AgActionBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectOptionRow(
    label: String,
    required: Boolean,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (required) "$label *" else label,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(150.dp)
        ) {
            OutlinedTextField(
                value = value.ifEmpty { "Select…" },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 12.sp) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RetailAuditSummaryView(
    report: RetailMarketShopAuditReport,
    onDone: () -> Unit
) {
    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgRoyalBlue)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onDone, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Submit Audit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Audit Summary",
                            color = AgRoyalBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        HorizontalDivider(color = AgLightBorder)
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgLightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetaRow("City:", report.cityName)
                                MetaRow("Territory:", report.territoryName)
                                MetaRow("Location:", report.locationName)
                                MetaRow("Sub Location:", report.subLocationName)
                                MetaRow("Total Shops:", report.totalShopsInSubLocation.toString())
                                MetaRow("Audited:", report.auditedShopsCount.toString())
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(AgAccentGreenBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AgAccentGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Audit Completed Successfully!",
                            color = AgAccentGreenDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Your audit data has been saved with GPS location.",
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Text("View Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

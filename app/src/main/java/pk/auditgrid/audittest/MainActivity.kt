package pk.auditgrid.audittest

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.auditgrid.audittest.model.AuditPreset
import pk.auditgrid.audittest.model.PlanLink
import pk.auditgrid.audittest.ui.AppUiState
import pk.auditgrid.audittest.ui.AuditGridViewModel
import pk.auditgrid.audittest.ui.MobileSection
import pk.auditgrid.audittest.ui.screens.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.hasLocationPermission
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val viewModel: AuditGridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuditGridTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.liveSharing.sharing) {
                    if (uiState.liveSharing.sharing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                AuditGridRoot(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun AuditGridRoot(
    uiState: AppUiState,
    viewModel: AuditGridViewModel
) {
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && uiState.liveSharing.sharing) {
            viewModel.startLiveTracking()
        }
    }

    LaunchedEffect(uiState.session?.user?.id) {
        if (uiState.session != null && !hasLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgBackground)
    ) {
        when {
            uiState.booting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AgPrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading…",
                            color = AgMutedText,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            uiState.session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    NativeLoginScreen(
                        apiClient = viewModel.apiClient,
                        onSignedIn = viewModel::onSignedIn
                    )
                }
            }

            uiState.loadError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.loadError,
                        color = AgForeground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = viewModel::retryLoadAll,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("retry_load_button")
                        ) {
                            Text(
                                text = "Try again",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::handleLogout,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("error_sign_out_button")
                        ) {
                            Text(
                                text = "Sign out",
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            else -> {
                AuditorMobileShell(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        // Toast overlay
        uiState.toast?.let { toastText ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 72.dp)
                    .widthIn(max = 460.dp)
            ) {
                Surface(
                    color = AgForeground,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = toastText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = viewModel::dismissToast,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditorMobileShell(
    uiState: AppUiState,
    viewModel: AuditGridViewModel
) {
    val session = uiState.session ?: return
    val activeAuditor = uiState.users.find { it.id == session.user.id } ?: session.user
    val activeRole = uiState.roles.find { it.id == activeAuditor.roleId } ?: session.role

    val assignedTerritoryIdsSet = remember(activeAuditor.id, activeAuditor.assignedTerritoryIds) {
        activeAuditor.assignedTerritoryIds.toSet()
    }
    val assignedTerritories = remember(uiState.territories, assignedTerritoryIdsSet) {
        uiState.territories.filter {
            assignedTerritoryIdsSet.contains(it.id) && it.status == "Active"
        }
    }
    val assignedCityIdsSet = remember(assignedTerritories) {
        assignedTerritories.map { it.cityId }.toSet()
    }
    val assignedCities = remember(uiState.cities, assignedCityIdsSet) {
        uiState.cities.filter {
            assignedCityIdsSet.contains(it.id) && it.status == "Active"
        }
    }
    val assignedLocations = remember(uiState.locations, assignedTerritoryIdsSet) {
        uiState.locations.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }
    val assignedSubLocations = remember(uiState.subLocations, assignedTerritoryIdsSet) {
        uiState.subLocations.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }
    val assignedShops = remember(uiState.shops, assignedTerritoryIdsSet) {
        uiState.shops.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }
    val assignedDistributors = remember(uiState.distributors, assignedTerritoryIdsSet) {
        uiState.distributors.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }

    val visibleTerritories = remember(assignedTerritories, uiState.filterCityId, uiState.searchQuery) {
        assignedTerritories.filter { t ->
            (uiState.filterCityId.isBlank() || t.cityId == uiState.filterCityId) &&
                (uiState.searchQuery.isBlank() ||
                    t.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    t.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleLocations = remember(assignedLocations, uiState.filterTerritoryId, uiState.searchQuery) {
        assignedLocations.filter { l ->
            (uiState.filterTerritoryId.isBlank() || l.territoryId == uiState.filterTerritoryId) &&
                (uiState.searchQuery.isBlank() ||
                    l.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    l.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleSubLocations = remember(
        assignedSubLocations,
        uiState.filterTerritoryId,
        uiState.filterLocationId,
        uiState.searchQuery
    ) {
        assignedSubLocations.filter { s ->
            (uiState.filterTerritoryId.isBlank() || s.territoryId == uiState.filterTerritoryId) &&
                (uiState.filterLocationId.isBlank() || s.locationId == uiState.filterLocationId) &&
                (uiState.searchQuery.isBlank() ||
                    s.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    s.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleShops = remember(
        assignedShops,
        uiState.filterTerritoryId,
        uiState.filterSubLocationId,
        uiState.searchQuery
    ) {
        assignedShops.filter { sh ->
            (uiState.filterTerritoryId.isBlank() || sh.territoryId == uiState.filterTerritoryId) &&
                (uiState.filterSubLocationId.isBlank() || sh.subLocationId == uiState.filterSubLocationId) &&
                (uiState.searchQuery.isBlank() ||
                    sh.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    sh.code.contains(uiState.searchQuery, ignoreCase = true) ||
                    sh.ownerName.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleDistributors = remember(
        assignedDistributors,
        uiState.filterCityId,
        uiState.filterTerritoryId,
        uiState.searchQuery
    ) {
        assignedDistributors.filter { d ->
            (uiState.filterCityId.isBlank() || d.cityId == uiState.filterCityId) &&
                (uiState.filterTerritoryId.isBlank() || d.territoryId == uiState.filterTerritoryId) &&
                (uiState.searchQuery.isBlank() ||
                    d.setupName.contains(uiState.searchQuery, ignoreCase = true) ||
                    d.code.contains(uiState.searchQuery, ignoreCase = true) ||
                    d.distributorName.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val permMap = remember(activeRole) {
        activeRole?.permissions
    }
    val canPermission: (String) -> Boolean = remember(permMap) {
        { key: String -> permMap == null || permMap.isEmpty() || permMap[key] == true }
    }
    val canBrowsePlaces = remember(canPermission) {
        canPermission("cities.view") ||
            canPermission("territories.view") ||
            canPermission("locations.view") ||
            canPermission("sublocations.view") ||
            canPermission("shops.view")
    }
    val canBrowseDistributors = remember(canPermission) {
        canPermission("distributors.view")
    }

    val isGeoSection = uiState.activeSection in listOf(
        MobileSection.CITIES,
        MobileSection.TERRITORIES,
        MobileSection.LOCATIONS,
        MobileSection.SUBLOCATIONS,
        MobileSection.SHOPS
    )

    // Back handler on non-Audit top-level sections when no detail item is open
    if (uiState.activeSection != MobileSection.AUDIT &&
        uiState.selectedShop == null &&
        uiState.selectedDistributor == null
    ) {
        BackHandler {
            viewModel.setSection(MobileSection.AUDIT)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgBackground)
    ) {
        // Top App Bar (#1E3A5F)
        Surface(
            color = AgPrimary,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.selectedShop != null || uiState.selectedDistributor != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    viewModel.selectShop(null)
                                    viewModel.selectDistributor(null)
                                }
                                .testTag("top_bar_back_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = AgSecondaryBlue,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeAuditor.fullName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.online) Color(0xFF34D399) else Color(0xFFFBBF24))
                            )
                            Text(
                                text = buildString {
                                    append(if (uiState.online) "Online" else "Offline")
                                    append(" · ${assignedTerritories.size} territories")
                                    if (uiState.lastSyncedAt.isNotBlank()) {
                                        append(" · Synced ${uiState.lastSyncedAt}")
                                    }
                                },
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Manual Sync Button in Top App Bar
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(enabled = !uiState.syncing) {
                                viewModel.syncAllNow(manual = true)
                            }
                            .testTag("top_bar_sync_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (uiState.syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync with main server",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (uiState.syncing) "Syncing" else "Sync",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.pendingOutbox.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF59E0B), CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiState.pendingOutbox.size.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = viewModel::handleLogout,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("top_bar_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log out",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Offline Banner
        if (!uiState.online) {
            Surface(
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = AgWarningDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "No internet — submitted audits are locked in your Offline Outbox and will auto-sync when online.",
                        color = AgWarningDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Pending Outbox Banner when reports are waiting to upload
        if (uiState.pendingOutbox.isNotEmpty()) {
            Surface(
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = AgWarningDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${uiState.pendingOutbox.size} submitted audit(s) in Offline Outbox waiting to upload.",
                            color = AgWarningDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.online && !uiState.syncing) {
                            Text(
                                text = "Upload now",
                                color = AgActionBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.syncAllNow(manual = true) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "View",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.setSection(MobileSection.COMPLETED) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Live Sharing Banner while a plan is In Progress
        if (uiState.liveSharing.sharing) {
            Surface(
                color = Color(0xFFDBEAFE),
                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AgSecondaryBlue)
                    )
                    val bannerText = when {
                        uiState.liveSharing.problem.isNotBlank() -> uiState.liveSharing.problem
                        uiState.liveSharing.position != null ->
                            "Sharing live location while your plan is running (±${uiState.liveSharing.position.accuracy.roundToInt()} m)"
                        else -> "Starting live location for your active plan…"
                    }
                    Text(
                        text = bannerText,
                        color = Color(0xFF1E40AF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Geography Drill-Down Sub-Tabs
        if (isGeoSection) {
            Surface(
                color = AgCardWhite,
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val geoTabs = listOf(
                        Triple(MobileSection.CITIES, "Cities", assignedCities.size),
                        Triple(MobileSection.TERRITORIES, "Territories", assignedTerritories.size),
                        Triple(MobileSection.LOCATIONS, "Locations", assignedLocations.size),
                        Triple(MobileSection.SUBLOCATIONS, "Sub-locations", assignedSubLocations.size),
                        Triple(MobileSection.SHOPS, "Shops", assignedShops.size)
                    )
                    geoTabs.forEach { (sec, label, count) ->
                        val active = uiState.activeSection == sec
                        Surface(
                            color = if (active) AgPrimary else AgBackground,
                            contentColor = if (active) Color.White else AgMutedText,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { viewModel.setSection(sec) }
                                .testTag("geo_tab_${sec.name.lowercase()}")
                        ) {
                            Text(
                                text = "$label ($count)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (uiState.filterCityId.isNotBlank() ||
                        uiState.filterTerritoryId.isNotBlank() ||
                        uiState.filterLocationId.isNotBlank() ||
                        uiState.filterSubLocationId.isNotBlank()
                    ) {
                        Surface(
                            color = AgDestructiveBg,
                            contentColor = AgDestructiveDark,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { viewModel.resetFilters() }
                                .testTag("clear_geo_filters_button")
                        ) {
                            Text(
                                text = "Clear filters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active Scope Filter Chips & Search Bar (for Geography & Distributors list views)
        if ((isGeoSection || uiState.activeSection == MobileSection.DISTRIBUTORS) &&
            uiState.selectedShop == null &&
            uiState.selectedDistributor == null
        ) {
            Surface(
                color = AgCardWhite,
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.filterCityId.isNotBlank() ||
                        uiState.filterTerritoryId.isNotBlank() ||
                        uiState.filterLocationId.isNotBlank() ||
                        uiState.filterSubLocationId.isNotBlank()
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.filterCityId.isNotBlank()) {
                                val cityName = uiState.cities.find { it.id == uiState.filterCityId }?.name ?: ""
                                FilterScopeChip("City: $cityName")
                            }
                            if (uiState.filterTerritoryId.isNotBlank()) {
                                val terCode = uiState.territories.find { it.id == uiState.filterTerritoryId }?.code ?: ""
                                FilterScopeChip("Territory: $terCode")
                            }
                            if (uiState.filterLocationId.isNotBlank()) {
                                val locName = uiState.locations.find { it.id == uiState.filterLocationId }?.name ?: ""
                                FilterScopeChip("Location: $locName")
                            }
                            if (uiState.filterSubLocationId.isNotBlank()) {
                                val subName = uiState.subLocations.find { it.id == uiState.filterSubLocationId }?.name ?: ""
                                FilterScopeChip("Sub: $subName")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = {
                            Text(
                                text = "Search ${uiState.activeSection.name.lowercase()} within assigned scope...",
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
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scope_search_input")
                    )
                }
            }
        }

        // Scrollable Main Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                val totalCompletedCount =
                    uiState.auditReports.size + uiState.retailReports.size + uiState.customSubmissions.size

                when (uiState.activeSection) {
                    MobileSection.AUDIT -> {
                        DistributorAuditFormScreen(
                            activeAuditor = activeAuditor,
                            cities = uiState.cities,
                            territories = uiState.territories,
                            locations = uiState.locations,
                            subLocations = uiState.subLocations,
                            shops = uiState.shops,
                            distributors = uiState.distributors,
                            products = uiState.products,
                            auditForms = uiState.auditForms,
                            formConfigs = uiState.formConfigs,
                            apiClient = viewModel.apiClient,
                            completedTotalCount = totalCompletedCount,
                            pendingOutboxCount = uiState.pendingOutbox.size,
                            onOpenCompletedAudits = { viewModel.setSection(MobileSection.COMPLETED) },
                            syncing = uiState.syncing,
                            online = uiState.online,
                            lastSyncedAt = uiState.lastSyncedAt,
                            newlySyncedFormIds = uiState.newlySyncedFormIds,
                            onSyncFormsNow = { viewModel.syncAllNow(manual = true) },
                            onSubmitReport = viewModel::submitDistributorReport,
                            onSubmitRetailShopReport = viewModel::submitRetailShopReport,
                            onSubmitCustomAudit = viewModel::submitCustomAuditReport,
                            onCustomSubmitted = {},
                            onToast = viewModel::showToast
                        )
                    }

                    MobileSection.COMPLETED -> {
                        CompletedAuditsScreen(
                            distributorReports = uiState.auditReports,
                            retailReports = uiState.retailReports,
                            customSubmissions = uiState.customSubmissions,
                            pendingOutbox = uiState.pendingOutbox,
                            formConfigs = uiState.formConfigs,
                            syncing = uiState.syncing,
                            lastSyncedAt = uiState.lastSyncedAt,
                            online = uiState.online,
                            onSyncNow = { viewModel.syncAllNow(manual = true) }
                        )
                    }

                    MobileSection.PLANS -> {
                        MobilePlansScreen(
                            activeAuditor = activeAuditor,
                            auditForms = uiState.auditForms,
                            distributors = assignedDistributors,
                            subLocations = assignedSubLocations,
                            shops = assignedShops,
                            apiClient = viewModel.apiClient,
                            mePosition = uiState.liveSharing.position,
                            refreshVersion = uiState.plansRefreshVersion,
                            onPlansChanged = viewModel::notifyPlansChanged,
                            onToast = viewModel::showToast,
                            renderAuditForm = { plan, item, onExit ->
                                DistributorAuditFormScreen(
                                    activeAuditor = activeAuditor,
                                    cities = uiState.cities,
                                    territories = uiState.territories,
                                    locations = uiState.locations,
                                    subLocations = uiState.subLocations,
                                    shops = uiState.shops,
                                    distributors = uiState.distributors,
                                    products = uiState.products,
                                    auditForms = uiState.auditForms,
                                    formConfigs = uiState.formConfigs,
                                    apiClient = viewModel.apiClient,
                                    initialFormId = item.formId,
                                    preset = AuditPreset(
                                        distributorId = item.targetId,
                                        subLocationId = item.targetId,
                                        targetId = item.targetId,
                                        shopIds = item.shopIds
                                    ),
                                    planLink = PlanLink(planId = plan.id, itemId = item.id),
                                    onExit = onExit,
                                    onSubmitReport = viewModel::submitDistributorReport,
                                    onSubmitRetailShopReport = viewModel::submitRetailShopReport,
                                    onSubmitCustomAudit = viewModel::submitCustomAuditReport,
                                    onCustomSubmitted = {},
                                    onToast = viewModel::showToast
                                )
                            }
                        )
                    }

                    MobileSection.CITIES -> {
                        CitiesListSection(
                            assignedCities = assignedCities,
                            assignedTerritories = assignedTerritories,
                            assignedDistributors = assignedDistributors,
                            searchQuery = uiState.searchQuery,
                            onSelectCity = { city ->
                                viewModel.setGeographyFilter(
                                    cityId = city.id,
                                    territoryId = "",
                                    locationId = "",
                                    subLocationId = "",
                                    targetSection = MobileSection.TERRITORIES
                                )
                            }
                        )
                    }

                    MobileSection.TERRITORIES -> {
                        TerritoriesListSection(
                            visibleTerritories = visibleTerritories,
                            cities = uiState.cities,
                            assignedLocations = assignedLocations,
                            assignedShops = assignedShops,
                            assignedDistributors = assignedDistributors,
                            onNavigateSection = { cityId, territoryId, target ->
                                viewModel.setGeographyFilter(
                                    cityId = cityId,
                                    territoryId = territoryId,
                                    locationId = "",
                                    subLocationId = "",
                                    targetSection = target
                                )
                            }
                        )
                    }

                    MobileSection.LOCATIONS -> {
                        LocationsListSection(
                            visibleLocations = visibleLocations,
                            assignedTerritories = assignedTerritories,
                            assignedSubLocations = assignedSubLocations,
                            assignedShops = assignedShops,
                            onSelectLocation = { loc ->
                                viewModel.setGeographyFilter(
                                    territoryId = loc.territoryId,
                                    locationId = loc.id,
                                    subLocationId = "",
                                    targetSection = MobileSection.SUBLOCATIONS
                                )
                            }
                        )
                    }

                    MobileSection.SUBLOCATIONS -> {
                        SubLocationsListSection(
                            visibleSubLocations = visibleSubLocations,
                            assignedLocations = assignedLocations,
                            assignedTerritories = assignedTerritories,
                            assignedShops = assignedShops,
                            onSelectSubLocation = { sub ->
                                viewModel.setGeographyFilter(
                                    territoryId = sub.territoryId,
                                    locationId = sub.locationId,
                                    subLocationId = sub.id,
                                    targetSection = MobileSection.SHOPS
                                )
                            }
                        )
                    }

                    MobileSection.SHOPS -> {
                        ShopsSection(
                            visibleShops = visibleShops,
                            selectedShop = uiState.selectedShop,
                            assignedTerritories = assignedTerritories,
                            assignedSubLocations = assignedSubLocations,
                            onSelectShop = viewModel::selectShop
                        )
                    }

                    MobileSection.DISTRIBUTORS -> {
                        DistributorsSection(
                            visibleDistributors = visibleDistributors,
                            selectedDistributor = uiState.selectedDistributor,
                            cities = uiState.cities,
                            territories = uiState.territories,
                            products = uiState.products,
                            onSelectDistributor = viewModel::selectDistributor
                        )
                    }

                    MobileSection.ACCOUNT -> {
                        AccountSection(
                            activeAuditor = activeAuditor,
                            activeRole = activeRole,
                            citiesCount = assignedCities.size,
                            territoriesCount = assignedTerritories.size,
                            shopsCount = assignedShops.size,
                            distributorsCount = assignedDistributors.size,
                            completedAuditsCount = totalCompletedCount,
                            pendingOutboxCount = uiState.pendingOutbox.size,
                            syncing = uiState.syncing,
                            lastSyncedAt = uiState.lastSyncedAt,
                            online = uiState.online,
                            apiClient = viewModel.apiClient,
                            onSyncNow = { viewModel.syncAllNow(manual = true) },
                            onOpenCompletedAudits = { viewModel.setSection(MobileSection.COMPLETED) },
                            onToast = viewModel::showToast,
                            onLogout = viewModel::handleLogout
                        )
                    }
                }
            }
        }

        // Bottom Navigation Bar
        val totalCompletedNavCount =
            uiState.auditReports.size + uiState.retailReports.size + uiState.customSubmissions.size + uiState.pendingOutbox.size

        Surface(
            color = AgCardWhite,
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "Audit",
                    icon = Icons.Default.AssignmentTurnedIn,
                    active = uiState.activeSection == MobileSection.AUDIT,
                    testTag = "nav_tab_audit",
                    onClick = { viewModel.setSection(MobileSection.AUDIT) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Completed",
                    icon = Icons.Default.TaskAlt,
                    active = uiState.activeSection == MobileSection.COMPLETED,
                    badgeCount = totalCompletedNavCount,
                    testTag = "nav_tab_completed",
                    onClick = { viewModel.setSection(MobileSection.COMPLETED) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Plans",
                    icon = Icons.Default.Route,
                    active = uiState.activeSection == MobileSection.PLANS,
                    testTag = "nav_tab_plans",
                    onClick = { viewModel.setSection(MobileSection.PLANS) },
                    modifier = Modifier.weight(1f)
                )
                if (canBrowsePlaces) {
                    BottomNavItem(
                        label = "Places",
                        icon = Icons.Default.LocationOn,
                        active = isGeoSection,
                        testTag = "nav_tab_places",
                        onClick = {
                            if (!isGeoSection) {
                                viewModel.setSection(MobileSection.TERRITORIES)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canBrowseDistributors) {
                    BottomNavItem(
                        label = "Distributors",
                        icon = Icons.Default.LocalShipping,
                        active = uiState.activeSection == MobileSection.DISTRIBUTORS,
                        testTag = "nav_tab_distributors",
                        onClick = { viewModel.setSection(MobileSection.DISTRIBUTORS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                BottomNavItem(
                    label = "Account",
                    icon = Icons.Default.Person,
                    active = uiState.activeSection == MobileSection.ACCOUNT,
                    testTag = "nav_tab_account",
                    onClick = { viewModel.setSection(MobileSection.ACCOUNT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FilterScopeChip(text: String) {
    Surface(
        color = AgPrimary.copy(alpha = 0.1f),
        contentColor = AgPrimary,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    testTag: String,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (active) AgPrimary else AgMutedText
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(20.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .background(if (active) AgPrimary else AgActionBlue, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package pk.auditgrid.audittest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.ApiError
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.MobileSection
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*
import kotlin.math.roundToInt

@Composable
fun CitiesListSection(
    assignedCities: List<City>,
    assignedTerritories: List<Territory>,
    assignedDistributors: List<Distributor>,
    searchQuery: String,
    onSelectCity: (City) -> Unit
) {
    val filtered = assignedCities.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "ASSIGNED CITIES (${assignedCities.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        filtered.forEach { city ->
            val cityTerritories = assignedTerritories.count { it.cityId == city.id }
            val cityDistributors = assignedDistributors.count { it.cityId == city.id }
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCity(city) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeBadge(city.code)
                            Text(
                                text = city.name,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${city.province} · ${city.region}",
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$cityTerritories Assigned Territories · $cityDistributors Distributors",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${city.name}",
                        tint = AgMutedText
                    )
                }
            }
        }
    }
}

@Composable
fun TerritoriesListSection(
    visibleTerritories: List<Territory>,
    cities: List<City>,
    assignedLocations: List<LocationItem>,
    assignedShops: List<Shop>,
    assignedDistributors: List<Distributor>,
    onNavigateSection: (cityId: String, territoryId: String, target: MobileSection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "ASSIGNED TERRITORIES (${visibleTerritories.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        visibleTerritories.forEach { ter ->
            val city = cities.find { it.id == ter.cityId }
            val terLocations = assignedLocations.count { it.territoryId == ter.id }
            val terShops = assignedShops.count { it.territoryId == ter.id }
            val terDistributors = assignedDistributors.count { it.territoryId == ter.id }

            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
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
                        CodeBadge(ter.code)
                        Text(
                            text = city?.name ?: "",
                            color = AgMutedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = ter.name,
                        color = AgForeground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Territory Distributor: ${ter.distributorName} (${ter.distributorCode})",
                        color = AgMutedText,
                        fontSize = 12.sp
                    )
                    HorizontalDivider(color = AgBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.LOCATIONS) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AgBackground,
                                contentColor = AgPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text(
                                text = "Locations ($terLocations)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.DISTRIBUTORS) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AgBackground,
                                contentColor = AgPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text(
                                text = "Distributors ($terDistributors)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$terShops Scoped Shops in Territory",
                            color = AgMutedText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        TextButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.SHOPS) }
                        ) {
                            Text(
                                text = "View Shops →",
                                color = AgSecondaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationsListSection(
    visibleLocations: List<LocationItem>,
    assignedTerritories: List<Territory>,
    assignedSubLocations: List<SubLocation>,
    assignedShops: List<Shop>,
    onSelectLocation: (LocationItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "SELECTED TERRITORY LOCATIONS (${visibleLocations.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        visibleLocations.forEach { loc ->
            val ter = assignedTerritories.find { it.id == loc.territoryId }
            val subCount = assignedSubLocations.count { it.locationId == loc.id }
            val shopCount = assignedShops.count { it.locationId == loc.id }
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLocation(loc) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeBadge(loc.code)
                            Text(
                                text = "${ter?.code ?: ""} · ${loc.areaClassification}",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = loc.name,
                            color = AgForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$subCount Sub-locations · $shopCount Shops",
                            color = AgMutedText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${loc.name}",
                        tint = AgMutedText
                    )
                }
            }
        }
    }
}

@Composable
fun SubLocationsListSection(
    visibleSubLocations: List<SubLocation>,
    assignedLocations: List<LocationItem>,
    assignedTerritories: List<Territory>,
    assignedShops: List<Shop>,
    onSelectSubLocation: (SubLocation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "SELECTED TERRITORY SUB-LOCATIONS (${visibleSubLocations.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        visibleSubLocations.forEach { sub ->
            val loc = assignedLocations.find { it.id == sub.locationId }
            val ter = assignedTerritories.find { it.id == sub.territoryId }
            val shopCount = assignedShops.count { it.subLocationId == sub.id }
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSubLocation(sub) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeBadge(sub.code)
                            Text(
                                text = "${ter?.code ?: ""} → ${loc?.name ?: ""}",
                                color = AgMutedText,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = sub.name,
                            color = AgForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sub.marketType} · Postal ${sub.postalCode} · $shopCount Shops",
                            color = AgMutedText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${sub.name}",
                        tint = AgMutedText
                    )
                }
            }
        }
    }
}

@Composable
fun ShopsSection(
    visibleShops: List<Shop>,
    selectedShop: Shop?,
    assignedTerritories: List<Territory>,
    assignedSubLocations: List<SubLocation>,
    onSelectShop: (Shop?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var distanceBusy by remember { mutableStateOf(false) }
    var distanceResult by remember(selectedShop?.id) { mutableStateOf<Pair<Boolean, String>?>(null) }

    if (selectedShop != null) {
        BackHandler { onSelectShop(null) }
        val ter = assignedTerritories.find { it.id == selectedShop.territoryId }
        val sub = assignedSubLocations.find { it.id == selectedShop.subLocationId }
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CodeBadge(selectedShop.code)
                        Text(
                            text = selectedShop.name,
                            color = AgForeground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedShop.addressLine,
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                    }
                    GPSStatusChip(selectedShop.gpsStatus)
                }
                HorizontalDivider(color = AgBorder)

                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Retailer Owner", selectedShop.ownerName, Modifier.weight(1f))
                            InfoCell("Contact Phone", selectedShop.phone, Modifier.weight(1f), mono = true)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Territory", ter?.name ?: "", Modifier.weight(1f), mono = true)
                            InfoCell("Sub-location", sub?.name ?: "", Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell(
                                "Registered GPS",
                                String.format("%.4f, %.4f", selectedShop.lat, selectedShop.lng),
                                Modifier.weight(1f),
                                mono = true
                            )
                            InfoCell(
                                "Allowed Radius",
                                "${selectedShop.allowedRadiusMeters} meters",
                                Modifier.weight(1f),
                                mono = true
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        distanceBusy = true
                        scope.launch {
                            try {
                                val pos = getDevicePosition(context)
                                val d = haversineMeters(pos.lat, pos.lng, selectedShop.lat, selectedShop.lng).roundToInt()
                                val radius = selectedShop.allowedRadiusMeters.takeIf { it > 0 } ?: DEFAULT_ALLOWED_RADIUS_M
                                val lowAcc = pos.accuracy > MAX_ACCEPTABLE_ACCURACY_M
                                val ok = d <= radius && !lowAcc
                                val msg = when {
                                    lowAcc -> "GPS accuracy is low (±${pos.accuracy.roundToInt()} m). Move to an open area and try again."
                                    d <= radius -> "You are $d m from the registered point (allowed $radius m)."
                                    else -> "You are $d m from the registered point — outside the allowed $radius m."
                                }
                                distanceResult = ok to msg
                            } catch (e: Exception) {
                                val msg = if (e is GpsException) e.message ?: "Could not read your location."
                                else "Could not read your location."
                                distanceResult = false to msg
                            } finally {
                                distanceBusy = false
                            }
                        }
                    },
                    enabled = !distanceBusy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_distance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (distanceBusy) "Getting location…" else "Check my distance from this shop",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                distanceResult?.let { (ok, text) ->
                    Surface(
                        color = if (ok) AgAccentGreenBg else AgDestructiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (ok) AgAccentGreen.copy(alpha = 0.4f) else AgDestructive.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = text,
                            color = if (ok) AgAccentGreenDark else AgDestructiveDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onSelectShop(null) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text(
                        text = "← Back to Territory Shops List",
                        color = AgForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SELECTED TERRITORY SHOPS (${visibleShops.size})",
                color = AgMutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            visibleShops.forEach { sh ->
                val ter = assignedTerritories.find { it.id == sh.territoryId }
                val sub = assignedSubLocations.find { it.id == sh.subLocationId }
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectShop(sh) }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CodeBadge(sh.code)
                            GPSStatusChip(sh.gpsStatus)
                        }
                        Text(
                            text = sh.name,
                            color = AgForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sh.ownerName} · ${sh.phone} · ${sh.shopCategory}",
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(color = AgBorder.copy(alpha = 0.6f))
                        Text(
                            text = "${ter?.code ?: ""} · ${sub?.name ?: ""}",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DistributorsSection(
    visibleDistributors: List<Distributor>,
    selectedDistributor: Distributor?,
    cities: List<City>,
    territories: List<Territory>,
    products: List<Product>,
    onSelectDistributor: (Distributor?) -> Unit
) {
    if (selectedDistributor != null) {
        BackHandler { onSelectDistributor(null) }
        val city = cities.find { it.id == selectedDistributor.cityId }
        val ter = territories.find { it.id == selectedDistributor.territoryId }
        val activeProducts = products.filter { p ->
            p.status == "Active" &&
                selectedDistributor.groupIds.contains(p.groupId) &&
                selectedDistributor.productStatuses[p.id] != "Inactive"
        }

        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CodeBadge(selectedDistributor.code)
                            selectedDistributor.groupIds.forEach { gid ->
                                ProductGroupTag(if (gid == "grp-a") "Group A" else "Group B")
                            }
                        }
                        Text(
                            text = selectedDistributor.setupName,
                            color = AgForeground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedDistributor.completeAddress,
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                    }
                    EntityStatusIndicator(selectedDistributor.status)
                }
                HorizontalDivider(color = AgBorder)

                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Distributor Name", selectedDistributor.distributorName, Modifier.weight(1f))
                            InfoCell("Father Name", selectedDistributor.fatherName, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("CNIC Number", selectedDistributor.cnic, Modifier.weight(1f), mono = true)
                            InfoCell("Contact Number", selectedDistributor.phone, Modifier.weight(1f), mono = true)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("City", city?.name ?: "", Modifier.weight(1f))
                            InfoCell("Linked Territory", ter?.name ?: "", Modifier.weight(1f), mono = true)
                        }
                    }
                }

                Text(
                    text = "Active Dealing Products (${activeProducts.size})",
                    color = AgForeground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeProducts.forEach { prd ->
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prd.sku} · ${prd.name}",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prd.boxesPerCtn} Boxes/CTN · ${prd.unitPerBox}",
                                        color = AgMutedText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = formatPKR(prd.pricePerBoxRs),
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onSelectDistributor(null) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text(
                        text = "← Back to Territory Distributors",
                        color = AgForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SELECTED TERRITORY DISTRIBUTORS (${visibleDistributors.size})",
                color = AgMutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (visibleDistributors.isEmpty()) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No territory distributors matched the current filter.",
                        color = AgMutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                visibleDistributors.forEach { dist ->
                    val city = cities.find { it.id == dist.cityId }
                    val ter = territories.find { it.id == dist.territoryId }
                    val activeSkuCount = products.count { p ->
                        p.status == "Active" &&
                            dist.groupIds.contains(p.groupId) &&
                            dist.productStatuses[p.id] != "Inactive"
                    }
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDistributor(dist) }
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
                                    CodeBadge(dist.code)
                                    dist.groupIds.forEach { gid ->
                                        ProductGroupTag(if (gid == "grp-a") "Group A" else "Group B")
                                    }
                                }
                                EntityStatusIndicator(dist.status)
                            }
                            Text(
                                text = dist.setupName,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Owner: ${dist.distributorName} (S/O ${dist.fatherName})",
                                color = AgMutedText,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Phone: ${dist.phone} · CNIC: ${dist.cnic}",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            HorizontalDivider(color = AgBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${city?.name ?: ""} → ${ter?.code ?: ""}",
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$activeSkuCount Active SKUs →",
                                    color = AgAccentGreen,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
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
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = AgMutedText, fontSize = 11.sp)
        Text(
            text = value,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun AccountSection(
    activeAuditor: AppUser,
    activeRole: UserRole?,
    citiesCount: Int,
    territoriesCount: Int,
    shopsCount: Int,
    distributorsCount: Int,
    completedAuditsCount: Int = 0,
    pendingOutboxCount: Int = 0,
    syncing: Boolean = false,
    lastSyncedAt: String = "",
    online: Boolean,
    apiClient: AuditGridApiClient,
    onSyncNow: () -> Unit = {},
    onOpenCompletedAudits: () -> Unit = {},
    onToast: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showPasswordDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activeAuditor.fullName,
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${activeAuditor.username} · ${activeAuditor.phone}",
                    color = AgMutedText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (activeRole != null) {
                    Text(
                        text = activeRole.name,
                        color = AgMutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        val stats = listOf(
            "Cities" to citiesCount,
            "Territories" to territoriesCount,
            "Shops" to shopsCount,
            "Distributors" to distributorsCount
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stats[0].first, stats[0].second, Modifier.weight(1f))
                StatCard(stats[1].first, stats[1].second, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stats[2].first, stats[2].second, Modifier.weight(1f))
                StatCard(stats[3].first, stats[3].second, Modifier.weight(1f))
            }
        }

        // Server Sync & Outbox Status Card
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
                    Column {
                        Text(
                            text = "Main Server Sync & Outbox",
                            color = AgForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lastSyncedAt.isNotBlank()) "Last synced at $lastSyncedAt PKT" else "Ready to sync with main server",
                            color = AgMutedText,
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        color = if (pendingOutboxCount > 0) AgWarningBg else AgAccentGreenBg,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (pendingOutboxCount > 0) "$pendingOutboxCount Pending" else "All Uploaded",
                            color = if (pendingOutboxCount > 0) AgWarningDark else AgAccentGreenDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Tapping Sync uploads any queued offline audit reports and refreshes Cities, Territories, Shops, Distributors, SKUs, Form Configs, and Daily Plans.",
                    color = AgMutedText,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("account_sync_button")
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing…", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenCompletedAudits,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("account_open_completed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = AgPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Completed ($completedAuditsCount)",
                            color = AgPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Surface(
            color = if (online) AgAccentGreenBg else AgWarningBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (online) AgAccentGreen.copy(alpha = 0.4f) else AgWarning.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (online) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (online) AgAccentGreenDark else AgWarningDark,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (online) {
                        "Connected to Main Server · ${apiClient.getApiBase()}"
                    } else {
                        "Offline — submitted audits queue in Outbox & auto-sync when online"
                    },
                    color = if (online) AgAccentGreenDark else AgWarningDark,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedButton(
            onClick = { showPasswordDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AgCardWhite,
                contentColor = AgPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("change_password_button")
        ) {
            Text(text = "Change password", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AgDestructiveBg.copy(alpha = 0.6f),
                contentColor = AgDestructive
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("logout_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Log out", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showPasswordDialog) {
        ChangePasswordModal(
            apiClient = apiClient,
            onClose = { showPasswordDialog = false },
            onDone = {
                showPasswordDialog = false
                onToast(it)
            }
        )
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = AgMutedText, fontSize = 11.sp)
            Text(
                text = count.toString(),
                color = AgForeground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChangePasswordModal(
    apiClient: AuditGridApiClient,
    onClose: () -> Unit,
    onDone: (String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change password",
                        color = AgForeground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
                error?.let {
                    Surface(
                        color = AgDestructiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AgDestructive)
                    ) {
                        Text(
                            text = it,
                            color = AgDestructiveDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "At least 8 characters with an uppercase letter, a lowercase letter and a number.",
                    color = AgMutedText,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Repeat new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        error = null
                        if (next != confirm) {
                            error = "The new passwords do not match."
                            return@Button
                        }
                        if (next.length < 8) {
                            error = "Password must be at least 8 characters."
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            try {
                                apiClient.changePassword(current, next)
                                onDone("Password changed. Other devices were signed out.")
                            } catch (e: Exception) {
                                error = if (e is ApiError) e.message else "Could not change the password."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (busy) "Saving…" else "Change password",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

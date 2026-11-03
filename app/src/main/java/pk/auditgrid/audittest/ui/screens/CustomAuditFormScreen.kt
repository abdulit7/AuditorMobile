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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
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
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*

private data class TargetOption(val id: String, val label: String, val sub: String)

@Composable
fun CustomAuditFormScreen(
    form: AuditFormDefinition,
    config: FormConfigPayload?,
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    distributors: List<Distributor>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    presetTargetId: String?,
    planLink: PlanLink?,
    apiClient: AuditGridApiClient,
    onBack: () -> Unit,
    onSubmitCustomAudit: suspend (CustomAuditSubmission, Map<String, Any?>, PlanLink?) -> CustomAuditSubmission,
    onSubmitted: (CustomAuditSubmission) -> Unit,
    onToast: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val targetType = form.target.ifEmpty { "distributor" }
    val targetLabel = when (targetType) {
        "subLocation" -> "Market area"
        "shop" -> "Shop"
        else -> "Distributor"
    }

    val terrs = remember(activeAuditor.id) { activeAuditor.assignedTerritoryIds.toSet() }
    val options = remember(targetType, distributors, subLocations, shops, terrs) {
        when (targetType) {
            "subLocation" -> subLocations
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { TargetOption(it.id, it.name, it.code) }
            "shop" -> shops
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { TargetOption(it.id, it.name, it.code) }
            else -> distributors
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { TargetOption(it.id, it.setupName, it.code) }
        }
    }

    var targetId by remember { mutableStateOf(presetTargetId ?: "") }
    var search by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var photos by remember { mutableStateOf<List<AuditCapturedImage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var doneSubmission by remember { mutableStateOf<CustomAuditSubmission?>(null) }
    val submitKey = remember { newSubmitKey() }

    val settings = config?.settings ?: FormSettings()
    val fields = remember(config) {
        (config?.fields ?: emptyList()).filter { it.visible && it.type != "photo" }
    }
    val hasPhotoField = remember(config) {
        (config?.fields ?: emptyList()).any { it.type == "photo" && it.visible }
    }
    val photosOn = settings.allowPhotoCapture || hasPhotoField
    val chosen = options.find { it.id == targetId }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (photos.size >= settings.maxPhotos) {
                onToast("Up to ${settings.maxPhotos} photos.")
            } else {
                val url = compressBitmapToDataUrl(bmp)
                photos = photos + AuditCapturedImage(
                    id = newSubmitKey(),
                    url = url,
                    caption = "",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is needed to take photos.")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (photos.size >= settings.maxPhotos) {
                onToast("Up to ${settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val url = compressUriToDataUrl(context, uri)
                    if (url != null) {
                        photos = photos + AuditCapturedImage(
                            id = newSubmitKey(),
                            url = url,
                            caption = "",
                            capturedAt = currentTimePkt()
                        )
                    } else {
                        onToast("Could not read that photo.")
                    }
                }
            }
        }
    }

    fun validate(): String {
        if (targetId.isBlank()) return "Choose the ${targetLabel.lowercase()} you audited."
        for (f in fields) {
            val v = answers[f.key]
            val empty = v == null || v.toString().isBlank()
            if (f.required && empty) return "${f.label} is required."
            if (f.type == "number" && !empty) {
                val n = v.toString().toDoubleOrNull() ?: return "${f.label} must be a number."
                if (f.min != null && n < f.min) return "${f.label} must be at least ${f.min}."
                if (f.max != null && n > f.max) return "${f.label} must be at most ${f.max}."
            }
        }
        if (settings.requirePhoto && photos.isEmpty()) return "At least one photo is required."
        return ""
    }

    val done = doneSubmission
    if (done != null) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgAccentGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Submitted",
                    tint = AgAccentGreen,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Audit submitted",
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = done.reportCode,
                    color = AgPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (done.locationMatched) "Location matched" else "Location flagged for manager review",
                    color = if (done.locationMatched) AgAccentGreenDark else AgWarning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (planLink != null) "Back to plan" else "Done",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    val shown = options.filter {
        (it.label + " " + it.sub).contains(search, ignoreCase = true)
    }.take(40)

    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgPrimary)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = form.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = form.code,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Target Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "$targetLabel *",
                        color = AgForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!presetTargetId.isNullOrBlank() && chosen != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgMutedBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = chosen.label,
                                color = AgPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search ${targetLabel.lowercase()}…", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                if (shown.isEmpty()) {
                                    Text(
                                        text = "Nothing in your assigned areas matches.",
                                        color = AgMutedText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                } else {
                                    shown.forEachIndexed { idx, o ->
                                        val isSelected = o.id == targetId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isSelected) AgMutedBg else AgCardWhite)
                                                .clickable { targetId = o.id }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = o.label,
                                                color = AgForeground,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = o.sub,
                                                color = AgMutedText,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (idx < shown.lastIndex) {
                                            HorizontalDivider(color = AgLightBorder)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Fields
                fields.forEach { f ->
                    CustomFieldInput(
                        field = f,
                        value = answers[f.key],
                        onChange = { v -> answers = answers + (f.key to v) }
                    )
                }

                // Photos
                if (photosOn) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (settings.requirePhoto) "Photos *" else "Photos",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) cameraLauncher.launch(null)
                                    else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Camera", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gallery", fontSize = 12.sp)
                            }
                        }
                        if (photos.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                photos.forEach { p ->
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                                    ) {
                                        DataUrlImage(
                                            dataUrl = p.url,
                                            contentDescription = "Audit photo",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { photos = photos.filterNot { it.id == p.id } },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(26.dp)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = AgMutedText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Your location is checked automatically when you submit.",
                        color = AgMutedText,
                        fontSize = 11.sp
                    )
                }

                if (error.isNotEmpty()) {
                    Surface(
                        color = AgDestructiveBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = error,
                            color = AgDestructiveDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val problem = validate()
                        error = problem
                        if (problem.isNotEmpty()) return@Button
                        busy = true
                        scope.launch {
                            try {
                                val pos = getDevicePosition(context)
                                val cleanAnswers = mutableMapOf<String, Any?>()
                                val stringAnswers = mutableMapOf<String, String>()
                                for (f in fields) {
                                    val v = answers[f.key]
                                    if (v == null || v.toString().isBlank()) continue
                                    val cleanVal: Any = if (f.type == "number") {
                                        v.toString().toDoubleOrNull() ?: 0.0
                                    } else {
                                        v
                                    }
                                    cleanAnswers[f.key] = cleanVal
                                    stringAnswers[f.key] = cleanVal.toString()
                                }
                                val (tLat, tLng, tCityId, tTerId) = when (targetType) {
                                    "subLocation" -> {
                                        val s = subLocations.find { it.id == targetId }
                                        listOf(s?.lat ?: 0.0, s?.lng ?: 0.0, s?.cityId ?: "", s?.territoryId ?: "")
                                    }
                                    "shop" -> {
                                        val sh = shops.find { it.id == targetId }
                                        listOf(sh?.lat ?: 0.0, sh?.lng ?: 0.0, sh?.cityId ?: "", sh?.territoryId ?: "")
                                    }
                                    else -> {
                                        val d = distributors.find { it.id == targetId }
                                        listOf(d?.lat ?: 0.0, d?.lng ?: 0.0, d?.cityId ?: "", d?.territoryId ?: "")
                                    }
                                }
                                val targetLat = tLat as Double
                                val targetLng = tLng as Double
                                val cityIdStr = tCityId as String
                                val terIdStr = tTerId as String
                                val distM = if (targetLat != 0.0 || targetLng != 0.0) {
                                    calculateDistanceMeters(pos.lat, pos.lng, targetLat, targetLng)
                                } else 0
                                val matched = !settings.requireGps || (
                                    distM <= settings.allowedRadiusMeters &&
                                        pos.accuracy <= settings.maxAccuracyMeters
                                    )
                                val cityObj = cities.find { it.id == cityIdStr }
                                val terObj = territories.find { it.id == terIdStr }

                                val localSubmission = CustomAuditSubmission(
                                    id = submitKey,
                                    reportCode = makeReportCode("AUD-CST"),
                                    formId = form.id,
                                    formCode = form.code,
                                    formTitle = form.title,
                                    targetType = targetType,
                                    targetId = targetId,
                                    targetCode = chosen?.sub ?: "",
                                    targetName = chosen?.label ?: targetId,
                                    cityName = cityObj?.name ?: "",
                                    territoryCode = terObj?.code ?: "",
                                    territoryName = terObj?.name ?: "",
                                    auditorId = activeAuditor.id,
                                    auditorName = activeAuditor.fullName,
                                    submittedDate = currentIsoDate(),
                                    submittedTime = currentTimePkt(),
                                    submittedAtISO = currentIsoTimestamp(),
                                    mobileLat = pos.lat,
                                    mobileLng = pos.lng,
                                    mobileAccuracyMeters = pos.accuracy.toInt(),
                                    distanceMeters = distM,
                                    allowedRadiusMeters = settings.allowedRadiusMeters,
                                    locationMatched = matched,
                                    answers = stringAnswers,
                                    capturedImages = photos,
                                    reviewStatus = "Submitted",
                                    planId = planLink?.planId,
                                    planItemId = planLink?.itemId
                                )

                                val saved = onSubmitCustomAudit(localSubmission, cleanAnswers, planLink)
                                doneSubmission = saved
                                onSubmitted(saved)
                            } catch (e: Exception) {
                                error = e.message ?: "Could not submit. Your entries are kept; try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgAccentGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("submit_custom_audit_button")
                ) {
                    Text(
                        text = if (busy) "Submitting…" else "Submit audit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldInput(
    field: FormField,
    value: Any?,
    onChange: (Any?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (field.required) "${field.label} *" else field.label,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        when (field.type) {
            "number" -> {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = { onChange(it) },
                    placeholder = { Text(field.placeholder ?: "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "textarea" -> {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = { onChange(it) },
                    placeholder = { Text(field.placeholder ?: "") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "select" -> {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = value?.toString() ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select…") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        field.options.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    onChange(opt)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            "yesno" -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isYes = value == true
                    val isNo = value == false
                    Button(
                        onClick = { onChange(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isYes) AgAccentGreen else AgCardWhite,
                            contentColor = if (isYes) Color.White else AgForeground
                        ),
                        border = BorderStroke(1.dp, if (isYes) AgAccentGreen else AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Yes", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onChange(false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNo) AgDestructive else AgCardWhite,
                            contentColor = if (isNo) Color.White else AgForeground
                        ),
                        border = BorderStroke(1.dp, if (isNo) AgDestructive else AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("No", fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = { onChange(it) },
                    placeholder = { Text(field.placeholder ?: if (field.type == "date") "YYYY-MM-DD" else "") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        field.helpText?.let {
            Text(text = it, color = AgMutedText, fontSize = 11.sp)
        }
    }
}

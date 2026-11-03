package pk.auditgrid.audittest.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.BuildConfig
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.util.dataUrlToBytes
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiError(
    override val message: String,
    val status: Int,
    val code: String? = null
) : Exception(message) {
    val isNetwork: Boolean get() = status == 0
}

class AuditGridApiClient(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auditgrid_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var accessToken: String? = null
    private val refreshMutex = Mutex()
    var onSessionEnded: (() -> Unit)? = null

    companion object {
        private const val BASE_KEY = "ag.apiBase"
        private const val RT_KEY = "ag.refreshToken"
        private const val SESSION_USER_KEY = "ag.session.username"
        private const val LOCAL_SESSION_KEY = "ag.session.local"
        private const val LOGGED_OUT_KEY = "ag.session.loggedOut"

        fun normalizeBase(v: String): String = v.trim().trimEnd('/')
    }

    @Volatile
    private var localPlansList: MutableList<AuditPlan> = mutableListOf()

    init {
        // Purge any legacy dummy completed reports or demo flags from earlier builds
        if (prefs.contains("ag.demoMode") ||
            prefs.contains("ag.demoSeeded") ||
            prefs.contains("ag.completed.dist.usr-demo-auditor-01") ||
            prefs.contains("ag.completed.retail.usr-demo-auditor-01") ||
            prefs.contains("ag.completed.custom.usr-demo-auditor-01")
        ) {
            prefs.edit()
                .remove("ag.demoMode")
                .remove("ag.demoSeeded")
                .remove("ag.demo.webCreatedCount")
                .remove("ag.completed.dist.usr-demo-auditor-01")
                .remove("ag.completed.retail.usr-demo-auditor-01")
                .remove("ag.completed.custom.usr-demo-auditor-01")
                .apply()
        }
    }

    fun isLocalFallbackSession(): Boolean = prefs.getBoolean(LOCAL_SESSION_KEY, false)

    fun getApiBase(): String {
        val stored = prefs.getString(BASE_KEY, null)
        if (!stored.isNullOrBlank() && !stored.startsWith("demo://")) return normalizeBase(stored)
        val envUrl = try {
            val field = BuildConfig::class.java.getDeclaredField("VITE_API_URL")
            field.get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
        return normalizeBase(envUrl).ifBlank { "http://192.168.1.20:3001" }
    }

    fun getConfiguredServerUrl(): String = getApiBase()

    fun setApiBase(v: String) {
        val normalized = normalizeBase(v)
        if (normalized.isNotBlank()) {
            prefs.edit().putString(BASE_KEY, normalized).apply()
        }
    }

    fun hasStoredSession(): Boolean {
        if (isLocalFallbackSession() && !prefs.getString(SESSION_USER_KEY, null).isNullOrBlank()) {
            return true
        }
        return !prefs.getString(RT_KEY, null).isNullOrBlank() && getConfiguredServerUrl().isNotBlank()
    }

    private fun applySession(p: JSONObject): Session {
        accessToken = p.optString("accessToken").takeIf { it.isNotEmpty() }
        val rt = p.optString("refreshToken")
        val userObj = p.optJSONObject("user") ?: JSONObject()
        val parsedUser = AuditGridJsonParsers.parseUser(userObj)
        val ed = prefs.edit()
            .putBoolean(LOCAL_SESSION_KEY, false)
            .putBoolean(LOGGED_OUT_KEY, false)
            .putString(SESSION_USER_KEY, parsedUser.username)
        if (rt.isNotEmpty()) {
            ed.putString(RT_KEY, rt)
        }
        ed.apply()
        val roleObj = p.optJSONObject("role")
        return Session(
            user = parsedUser,
            role = roleObj?.let { AuditGridJsonParsers.parseRole(it) },
            surface = p.optString("surface", "mobile"),
            isGlobal = p.optBoolean("isGlobal", false)
        )
    }

    fun signInLocalSession(username: String): Session {
        val clean = username.trim().ifBlank { "auditor" }
        prefs.edit()
            .putBoolean(LOCAL_SESSION_KEY, true)
            .putBoolean(LOGGED_OUT_KEY, false)
            .putString(SESSION_USER_KEY, clean)
            .apply()
        return DefaultServerCatalog.buildSession(clean)
    }

    suspend fun checkServer(base: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${normalizeBase(base)}/api/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                json.optBoolean("ok", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendRaw(
        path: String,
        method: String = "GET",
        jsonBody: Any? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val base = getConfiguredServerUrl()
        if (base.isBlank()) {
            throw ApiError("Server address is not configured.", 0)
        }
        try {
            val url = URL(base + path)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 6000
                if (method == "PATCH") {
                    try {
                        requestMethod = "PATCH"
                    } catch (_: Exception) {
                        requestMethod = "POST"
                        setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    }
                } else {
                    requestMethod = method
                }
                val token = accessToken
                if (!token.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            if (jsonBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val bytes = jsonBody.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            status to text
        } catch (e: ApiError) {
            throw e
        } catch (_: Exception) {
            throw ApiError(
                "Cannot reach the server. Check the server address, your Wi-Fi/mobile data, and that the server is running.",
                0
            )
        }
    }

    private fun readError(status: Int, body: String): ApiError {
        return try {
            val j = JSONObject(body)
            val err = j.optString("error").ifEmpty { "Request failed ($status)." }
            val code = j.optString("code").takeIf { it.isNotEmpty() }
            ApiError(err, status, code)
        } catch (_: Exception) {
            ApiError("Request failed ($status).", status)
        }
    }

    suspend fun refreshSession(): Session? = refreshMutex.withLock {
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            return@withLock DefaultServerCatalog.buildSession(savedUser)
        }
        val token = prefs.getString(RT_KEY, null) ?: return@withLock null
        try {
            val payload = JSONObject().apply {
                put("client", "native")
                put("refreshToken", token)
            }
            val (status, body) = sendRaw("/api/auth/refresh", "POST", payload)
            if (status !in 200..299) {
                accessToken = null
                if (status == 401) {
                    prefs.edit().remove(RT_KEY).apply()
                }
                return@withLock null
            }
            applySession(JSONObject(body))
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val savedUser = prefs.getString(SESSION_USER_KEY, null)
                if (!savedUser.isNullOrBlank()) {
                    return@withLock DefaultServerCatalog.buildSession(savedUser)
                }
                throw e
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun requestJson(
        path: String,
        method: String = "GET",
        jsonBody: Any? = null
    ): String {
        var (status, body) = sendRaw(path, method, jsonBody)
        if (status == 401 && !path.startsWith("/api/auth/")) {
            val refreshed = runCatching { refreshSession() }.getOrNull()
            if (refreshed != null) {
                val retry = sendRaw(path, method, jsonBody)
                status = retry.first
                body = retry.second
            } else {
                accessToken = null
                onSessionEnded?.invoke()
                throw ApiError("Your session has ended. Please sign in again.", 401, "unauthenticated")
            }
        }
        if (status !in 200..299) {
            throw readError(status, body)
        }
        return body
    }

    suspend fun login(username: String, password: String): Session {
        val payload = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("surface", "mobile")
            put("client", "native")
        }
        val (status, body) = sendRaw("/api/auth/login", "POST", payload)
        if (status !in 200..299) throw readError(status, body)
        return applySession(JSONObject(body))
    }

    suspend fun restoreSession(): Session? {
        if (prefs.getBoolean(LOGGED_OUT_KEY, false)) {
            return null
        }
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            return DefaultServerCatalog.buildSession(savedUser)
        }
        if (!hasStoredSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            return signInLocalSession(savedUser)
        }
        return try {
            refreshSession() ?: signInLocalSession(prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor")
        } catch (_: Exception) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            DefaultServerCatalog.buildSession(savedUser)
        }
    }

    suspend fun logout() {
        val token = prefs.getString(RT_KEY, null)
        if (!isLocalFallbackSession() && !token.isNullOrEmpty()) {
            try {
                val payload = JSONObject().apply {
                    put("client", "native")
                    put("refreshToken", token)
                }
                sendRaw("/api/auth/logout", "POST", payload)
            } catch (_: Exception) {
            }
        }
        accessToken = null
        prefs.edit()
            .remove(RT_KEY)
            .remove(SESSION_USER_KEY)
            .putBoolean(LOCAL_SESSION_KEY, false)
            .putBoolean(LOGGED_OUT_KEY, true)
            .apply()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        if (isLocalFallbackSession()) return
        val payload = JSONObject().apply {
            put("currentPassword", currentPassword)
            put("newPassword", newPassword)
        }
        requestJson("/api/auth/change-password", "POST", payload)
    }

    suspend fun fetchBootstrap(session: Session): BootstrapPayload {
        if (isLocalFallbackSession()) {
            return DefaultServerCatalog.buildBootstrap(session)
        }
        return try {
            val body = requestJson("/api/bootstrap")
            saveRawBootstrapCache(session.user.id, body)
            AuditGridJsonParsers.parseBootstrap(JSONObject(body), session)
        } catch (e: ApiError) {
            if (e.isNetwork) {
                loadCachedBootstrap(session.user.id, session) ?: DefaultServerCatalog.buildBootstrap(session)
            } else {
                throw e
            }
        }
    }

    suspend fun fetchForms(): List<AuditFormDefinition> {
        if (isLocalFallbackSession()) {
            return DefaultServerCatalog.forms
        }
        return try {
            val body = requestJson("/api/forms")
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseFormDefinition(it) }
            }
        } catch (e: ApiError) {
            if (e.isNetwork) DefaultServerCatalog.forms else throw e
        }
    }

    suspend fun fetchFormConfig(formId: String): FormConfigPayload {
        if (isLocalFallbackSession()) {
            return DefaultServerCatalog.formConfigs[formId] ?: FormConfigPayload(formId = formId)
        }
        return try {
            val body = requestJson("/api/forms/$formId/config")
            AuditGridJsonParsers.parseFormConfig(JSONObject(body))
        } catch (e: ApiError) {
            if (e.isNetwork) {
                DefaultServerCatalog.formConfigs[formId] ?: FormConfigPayload(formId = formId)
            } else {
                throw e
            }
        }
    }

    suspend fun fetchDistributorReports(): List<DistributorProductAuditReport> {
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            val uid = DefaultServerCatalog.buildUser(savedUser).id
            return loadCachedDistributorReports(uid)
        }
        val body = requestJson("/api/reports/distributor")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseDistributorReport(it) }
        }
    }

    suspend fun fetchRetailReports(): List<RetailMarketShopAuditReport> {
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            val uid = DefaultServerCatalog.buildUser(savedUser).id
            return loadCachedRetailReports(uid)
        }
        val body = requestJson("/api/reports/retail")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseRetailReport(it) }
        }
    }

    suspend fun fetchCustomSubmissions(): List<CustomAuditSubmission> {
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            val uid = DefaultServerCatalog.buildUser(savedUser).id
            return loadCachedCustomSubmissions(uid)
        }
        val body = requestJson("/api/submissions")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseCustomSubmission(it) }
        }
    }

    suspend fun uploadPhotoDataUrl(dataUrl: String): String = withContext(Dispatchers.IO) {
        if (dataUrl.startsWith("/api/photos/")) {
            return@withContext dataUrl.substringAfterLast('/')
        }
        val (mime, bytes) = dataUrlToBytes(dataUrl)
        val boundary = "----AuditGridBoundary${UUID.randomUUID()}"
        val base = getApiBase()

        fun doUpload(): Pair<Int, String> {
            val url = URL("$base/api/photos")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                doOutput = true
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return status to text
        }

        var (status, body) = try {
            doUpload()
        } catch (_: Exception) {
            throw ApiError("Could not upload photo. Check your network connection.", 0)
        }
        if (status == 401) {
            val s = runCatching { refreshSession() }.getOrNull()
            if (s != null) {
                val retry = doUpload()
                status = retry.first
                body = retry.second
            }
        }
        if (status !in 200..299) throw readError(status, body)
        JSONObject(body).optString("id")
    }

    private fun markLocalPlanItemDone(
        planId: String?,
        planItemId: String?,
        reportKind: String,
        reportId: String,
        reportCode: String
    ) {
        if (planId.isNullOrBlank() || planItemId.isNullOrBlank()) return
        val idx = localPlansList.indexOfFirst { it.id == planId }
        if (idx < 0) return
        val plan = localPlansList[idx]
        val updatedItems = plan.items.map { item ->
            if (item.id == planItemId) {
                item.copy(
                    status = "Done",
                    doneAt = pk.auditgrid.audittest.util.currentTimePkt(),
                    reportKind = reportKind,
                    reportId = reportId,
                    reportCode = reportCode
                )
            } else {
                item
            }
        }
        val allFinished = updatedItems.isNotEmpty() && updatedItems.all { it.status == "Done" || it.status == "Skipped" }
        localPlansList[idx] = plan.copy(
            status = if (allFinished) "Completed" else plan.status,
            items = updatedItems
        )
    }

    suspend fun submitDistributorReport(report: DistributorProductAuditReport): DistributorProductAuditReport {
        if (isLocalFallbackSession()) {
            val saved = report.copy(reviewStatus = report.reviewStatus ?: "Submitted")
            markLocalPlanItemDone(report.planId, report.planItemId, "distributor", saved.id, saved.reportCode)
            return saved
        }
        val photosArr = JSONArray()
        for (img in report.capturedImages) {
            val photoId = uploadPhotoDataUrl(img.url)
            photosArr.put(JSONObject().apply {
                put("photoId", photoId)
                put("caption", img.caption)
            })
        }
        val itemsArr = JSONArray()
        for (item in report.items) {
            itemsArr.put(JSONObject().apply {
                put("productId", item.productId)
                put("qtyInCtn", item.qtyInCtn)
                put("qtyInBox", item.qtyInBox)
                put("dispatchDate", item.dispatchDate)
                put("stacking", item.stacking)
                put("remarks", item.remarks)
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", report.id)
            put("distributorId", report.distributorId)
            put("items", itemsArr)
            put("mobileLat", report.mobileLat)
            put("mobileLng", report.mobileLng)
            put("mobileAccuracyMeters", report.mobileAccuracyMeters)
            put("photos", photosArr)
            report.overallComments?.let { put("overallComments", it) }
            report.planId?.let { put("planId", it) }
            report.planItemId?.let { put("planItemId", it) }
        }
        val res = requestJson("/api/reports/distributor", "POST", payload)
        return AuditGridJsonParsers.parseDistributorReport(JSONObject(res))
    }

    suspend fun submitRetailShopReport(report: RetailMarketShopAuditReport): RetailMarketShopAuditReport {
        if (isLocalFallbackSession()) {
            val saved = report.copy(reviewStatus = report.reviewStatus ?: "Submitted")
            markLocalPlanItemDone(report.planId, report.planItemId, "retail", saved.id, saved.reportCode)
            return saved
        }
        val shopAuditsArr = JSONArray()
        for (sa in report.shopAudits) {
            val photosArr = JSONArray()
            for (img in sa.capturedImages) {
                val photoId = uploadPhotoDataUrl(img.url)
                photosArr.put(JSONObject().apply {
                    put("photoId", photoId)
                    put("caption", img.caption)
                })
            }
            shopAuditsArr.put(JSONObject().apply {
                put("shopId", sa.shopId)
                put("productACount", sa.productACount)
                put("productBCount", sa.productBCount)
                put("displayRating", sa.displayRating)
                put("packedProductQty", sa.packedProductQty)
                put("regularity", sa.regularity)
                put("remarks", sa.remarks)
                put("auditedAt", sa.auditedAt)
                put("mobileLat", sa.mobileLat)
                put("mobileLng", sa.mobileLng)
                put("mobileAccuracyMeters", sa.mobileAccuracyMeters)
                put("photos", photosArr)
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", report.id)
            put("subLocationId", report.subLocationId)
            put("shopAudits", shopAuditsArr)
            report.planId?.let { put("planId", it) }
            report.planItemId?.let { put("planItemId", it) }
        }
        val res = requestJson("/api/reports/retail", "POST", payload)
        return AuditGridJsonParsers.parseRetailReport(JSONObject(res))
    }

    suspend fun submitCustomAudit(
        clientRequestId: String,
        formId: String,
        targetType: String,
        targetId: String,
        answers: Map<String, Any?>,
        photos: List<AuditCapturedImage>,
        mobileLat: Double,
        mobileLng: Double,
        mobileAccuracyMeters: Double,
        planLink: PlanLink?,
        fallbackMeta: CustomAuditSubmission? = null
    ): CustomAuditSubmission {
        val stringAnswers = mutableMapOf<String, String>()
        for ((k, v) in answers) {
            if (v != null && v != "") {
                stringAnswers[k] = v.toString()
            }
        }
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            val userObj = DefaultServerCatalog.buildUser(savedUser)
            val saved = (fallbackMeta ?: CustomAuditSubmission(
                id = clientRequestId,
                reportCode = pk.auditgrid.audittest.util.makeReportCode("SUB"),
                formId = formId,
                formTitle = DefaultServerCatalog.forms.find { it.id == formId }?.title ?: formId,
                targetType = targetType,
                targetId = targetId,
                targetName = targetId,
                auditorId = userObj.id,
                auditorName = userObj.fullName,
                submittedDate = pk.auditgrid.audittest.util.currentIsoDate(),
                submittedTime = pk.auditgrid.audittest.util.currentTimePkt(),
                submittedAtISO = pk.auditgrid.audittest.util.currentIsoTimestamp(),
                mobileLat = mobileLat,
                mobileLng = mobileLng,
                mobileAccuracyMeters = mobileAccuracyMeters.toInt(),
                locationMatched = true,
                answers = stringAnswers,
                capturedImages = photos,
                reviewStatus = "Submitted",
                planId = planLink?.planId,
                planItemId = planLink?.itemId
            )).copy(
                answers = if (stringAnswers.isNotEmpty()) stringAnswers else (fallbackMeta?.answers ?: emptyMap()),
                capturedImages = photos,
                reviewStatus = "Submitted"
            )
            markLocalPlanItemDone(planLink?.planId, planLink?.itemId, "custom", saved.id, saved.reportCode)
            return saved
        }
        val photosArr = JSONArray()
        for (img in photos) {
            val photoId = uploadPhotoDataUrl(img.url)
            photosArr.put(JSONObject().apply {
                put("photoId", photoId)
                put("caption", img.caption)
            })
        }
        val ansObj = JSONObject()
        for ((k, v) in answers) {
            if (v != null && v != "") {
                ansObj.put(k, v)
            }
        }
        val payload = JSONObject().apply {
            put("clientRequestId", clientRequestId)
            put("formId", formId)
            put("targetType", targetType)
            put("targetId", targetId)
            put("answers", ansObj)
            put("photos", photosArr)
            put("mobileLat", mobileLat)
            put("mobileLng", mobileLng)
            put("mobileAccuracyMeters", mobileAccuracyMeters)
            planLink?.let {
                put("planId", it.planId)
                put("planItemId", it.itemId)
            }
        }
        val res = requestJson("/api/submissions", "POST", payload)
        val parsed = AuditGridJsonParsers.parseCustomSubmission(JSONObject(res))
        return parsed.copy(
            formId = parsed.formId.ifEmpty { formId },
            formCode = parsed.formCode.ifEmpty { fallbackMeta?.formCode ?: "" },
            formTitle = parsed.formTitle.ifEmpty { fallbackMeta?.formTitle ?: "" },
            targetType = parsed.targetType.ifEmpty { targetType },
            targetId = parsed.targetId.ifEmpty { targetId },
            targetCode = parsed.targetCode.ifEmpty { fallbackMeta?.targetCode ?: "" },
            targetName = parsed.targetName.ifEmpty { fallbackMeta?.targetName ?: "" },
            cityName = parsed.cityName.ifEmpty { fallbackMeta?.cityName ?: "" },
            territoryCode = parsed.territoryCode.ifEmpty { fallbackMeta?.territoryCode ?: "" },
            territoryName = parsed.territoryName.ifEmpty { fallbackMeta?.territoryName ?: "" },
            submittedDate = parsed.submittedDate.ifEmpty { fallbackMeta?.submittedDate ?: "" },
            submittedTime = parsed.submittedTime.ifEmpty { fallbackMeta?.submittedTime ?: "" },
            mobileLat = if (parsed.mobileLat != 0.0) parsed.mobileLat else mobileLat,
            mobileLng = if (parsed.mobileLng != 0.0) parsed.mobileLng else mobileLng,
            mobileAccuracyMeters = if (parsed.mobileAccuracyMeters != 0) parsed.mobileAccuracyMeters else mobileAccuracyMeters.toInt(),
            distanceMeters = if (parsed.distanceMeters != 0) parsed.distanceMeters else (fallbackMeta?.distanceMeters ?: 0),
            allowedRadiusMeters = if (parsed.allowedRadiusMeters != 0) parsed.allowedRadiusMeters else (fallbackMeta?.allowedRadiusMeters ?: 150),
            answers = if (parsed.answers.isNotEmpty()) parsed.answers else stringAnswers,
            capturedImages = if (parsed.capturedImages.isNotEmpty()) parsed.capturedImages else photos
        )
    }

    suspend fun fetchPlans(inProgressOnly: Boolean = false): List<AuditPlan> {
        if (isLocalFallbackSession()) {
            val all = localPlansList.toList()
            return if (inProgressOnly) all.filter { it.status == "In Progress" } else all
        }
        return try {
            val path = if (inProgressOnly) "/api/plans?status=In%20Progress" else "/api/plans"
            val body = requestJson(path)
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parsePlan(it) }
            }
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val all = localPlansList.toList()
                if (inProgressOnly) all.filter { it.status == "In Progress" } else all
            } else {
                throw e
            }
        }
    }

    suspend fun createPlan(
        clientRequestId: String,
        planDate: String,
        title: String?,
        items: List<PlanItem>
    ): AuditPlan {
        if (isLocalFallbackSession()) {
            val savedUser = prefs.getString(SESSION_USER_KEY, "auditor") ?: "auditor"
            val userObj = DefaultServerCatalog.buildUser(savedUser)
            val newPlan = AuditPlan(
                id = clientRequestId.ifBlank { UUID.randomUUID().toString() },
                auditorId = userObj.id,
                auditorName = userObj.fullName,
                planDate = planDate,
                title = title?.trim()?.ifBlank { "Field Audit Plan ($planDate)" } ?: "Field Audit Plan ($planDate)",
                notes = "",
                status = "Planned",
                startedAt = null,
                items = items.mapIndexed { idx, i ->
                    val formTitle = DefaultServerCatalog.forms.find { it.id == i.formId }?.title ?: i.formId
                    i.copy(
                        id = i.id.ifBlank { "pli-${System.currentTimeMillis()}-$idx" },
                        formTitle = i.formTitle.ifBlank { formTitle },
                        status = "Pending"
                    )
                }
            )
            localPlansList.add(0, newPlan)
            return newPlan
        }
        val itemsArr = JSONArray()
        for (i in items) {
            itemsArr.put(JSONObject().apply {
                put("formId", i.formId)
                put("targetType", i.targetType)
                put("targetId", i.targetId)
                put("shopIds", JSONArray(i.shopIds))
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", clientRequestId)
            put("planDate", planDate)
            if (!title.isNullOrBlank()) put("title", title.trim())
            put("items", itemsArr)
        }
        val res = requestJson("/api/plans", "POST", payload)
        return AuditGridJsonParsers.parsePlan(JSONObject(res))
    }

    suspend fun startPlan(planId: String): AuditPlan {
        if (isLocalFallbackSession()) {
            val idx = localPlansList.indexOfFirst { it.id == planId }
            if (idx >= 0) {
                val updated = localPlansList[idx].copy(
                    status = "In Progress",
                    startedAt = pk.auditgrid.audittest.util.currentTimePkt()
                )
                localPlansList[idx] = updated
                return updated
            }
        }
        val res = requestJson("/api/plans/$planId/start", "POST", JSONObject())
        return AuditGridJsonParsers.parsePlan(JSONObject(res))
    }

    suspend fun cancelPlan(planId: String): AuditPlan {
        if (isLocalFallbackSession()) {
            val idx = localPlansList.indexOfFirst { it.id == planId }
            if (idx >= 0) {
                val updated = localPlansList[idx].copy(status = "Cancelled")
                localPlansList[idx] = updated
                return updated
            }
        }
        val res = requestJson("/api/plans/$planId/cancel", "POST", JSONObject())
        return AuditGridJsonParsers.parsePlan(JSONObject(res))
    }

    suspend fun deletePlan(planId: String) {
        if (isLocalFallbackSession()) {
            localPlansList.removeAll { it.id == planId }
            return
        }
        requestJson("/api/plans/$planId", "DELETE")
    }

    suspend fun skipPlanItem(planId: String, itemId: String, reason: String): AuditPlan {
        if (isLocalFallbackSession()) {
            val idx = localPlansList.indexOfFirst { it.id == planId }
            if (idx >= 0) {
                val plan = localPlansList[idx]
                val updatedItems = plan.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            status = "Skipped",
                            doneAt = pk.auditgrid.audittest.util.currentTimePkt(),
                            skipReason = reason.trim()
                        )
                    } else {
                        item
                    }
                }
                val allFinished = updatedItems.isNotEmpty() && updatedItems.all { it.status == "Done" || it.status == "Skipped" }
                val updated = plan.copy(
                    status = if (allFinished) "Completed" else plan.status,
                    items = updatedItems
                )
                localPlansList[idx] = updated
                return updated
            }
        }
        val payload = JSONObject().apply {
            put("status", "Skipped")
            put("reason", reason.trim())
        }
        val res = requestJson("/api/plans/$planId/items/$itemId", "PATCH", payload)
        return AuditGridJsonParsers.parsePlan(JSONObject(res))
    }

    suspend fun sendTrackingPing(lat: Double, lng: Double, accuracy: Double) {
        if (isLocalFallbackSession()) return
        val payload = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("accuracy", accuracy)
        }
        requestJson("/api/tracking/ping", "POST", payload)
    }

    // Local draft storage (mirrors localStorage in browser app)
    fun saveDraftString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getDraftString(key: String): String? = prefs.getString(key, null)

    fun removeDraft(key: String) {
        prefs.edit().remove(key).apply()
    }

    // Offline Outbox Queue & Read-Only Completed Audits Cache
    fun loadOutboxItems(auditorId: String): List<PendingOutboxItem> {
        val raw = prefs.getString("ag.outbox.$auditorId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parsePendingOutboxItem(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveOutboxItems(auditorId: String, items: List<PendingOutboxItem>) {
        try {
            val arr = JSONArray()
            items.forEach { arr.put(AuditGridJsonParsers.serializePendingOutboxItem(it)) }
            prefs.edit().putString("ag.outbox.$auditorId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedDistributorReports(auditorId: String): List<DistributorProductAuditReport> {
        val raw = prefs.getString("ag.completed.dist.$auditorId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseDistributorReport(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedDistributorReports(auditorId: String, items: List<DistributorProductAuditReport>) {
        try {
            val arr = JSONArray()
            items.take(100).forEach { arr.put(AuditGridJsonParsers.serializeDistributorReport(it)) }
            prefs.edit().putString("ag.completed.dist.$auditorId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedRetailReports(auditorId: String): List<RetailMarketShopAuditReport> {
        val raw = prefs.getString("ag.completed.retail.$auditorId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseRetailReport(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedRetailReports(auditorId: String, items: List<RetailMarketShopAuditReport>) {
        try {
            val arr = JSONArray()
            items.take(100).forEach { arr.put(AuditGridJsonParsers.serializeRetailReport(it)) }
            prefs.edit().putString("ag.completed.retail.$auditorId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedCustomSubmissions(auditorId: String): List<CustomAuditSubmission> {
        val raw = prefs.getString("ag.completed.custom.$auditorId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseCustomSubmission(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedCustomSubmissions(auditorId: String, items: List<CustomAuditSubmission>) {
        try {
            val arr = JSONArray()
            items.take(100).forEach { arr.put(AuditGridJsonParsers.serializeCustomSubmission(it)) }
            prefs.edit().putString("ag.completed.custom.$auditorId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    // On-device persistence for Synced Audit Forms, Field Configs & Master Data (Offline & Online Use)
    fun hasSyncedFormsOnce(auditorId: String): Boolean =
        prefs.getBoolean("ag.forms.synced.$auditorId", false)

    fun setFormsSyncedOnce(auditorId: String, synced: Boolean) {
        prefs.edit().putBoolean("ag.forms.synced.$auditorId", synced).apply()
    }

    fun loadCachedForms(auditorId: String): List<AuditFormDefinition> {
        val raw = prefs.getString("ag.forms.list.$auditorId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseFormDefinition(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedForms(auditorId: String, forms: List<AuditFormDefinition>) {
        try {
            val arr = JSONArray()
            forms.forEach { arr.put(AuditGridJsonParsers.serializeFormDefinition(it)) }
            prefs.edit().putString("ag.forms.list.$auditorId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedFormConfigs(auditorId: String): FormConfigMap {
        val raw = prefs.getString("ag.forms.configs.$auditorId", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, FormConfigPayload>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                obj.optJSONObject(k)?.let { out[k] = AuditGridJsonParsers.parseFormConfig(it) }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveCachedFormConfigs(auditorId: String, configs: FormConfigMap) {
        try {
            val obj = JSONObject()
            configs.forEach { (id, cfg) ->
                obj.put(id, AuditGridJsonParsers.serializeFormConfig(cfg))
            }
            prefs.edit().putString("ag.forms.configs.$auditorId", obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun saveRawBootstrapCache(auditorId: String, rawJson: String) {
        try {
            prefs.edit().putString("ag.bootstrap.raw.$auditorId", rawJson).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedBootstrap(auditorId: String, session: Session): BootstrapPayload? {
        val raw = prefs.getString("ag.bootstrap.raw.$auditorId", null) ?: return null
        return try {
            AuditGridJsonParsers.parseBootstrap(JSONObject(raw), session)
        } catch (_: Exception) {
            null
        }
    }
}

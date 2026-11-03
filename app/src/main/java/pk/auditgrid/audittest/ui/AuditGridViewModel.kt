package pk.auditgrid.audittest.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import pk.auditgrid.audittest.api.ApiError
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.util.*

enum class MobileSection {
    AUDIT, COMPLETED, PLANS, CITIES, TERRITORIES, LOCATIONS, SUBLOCATIONS, SHOPS, DISTRIBUTORS, ACCOUNT
}

data class LiveSharingState(
    val sharing: Boolean = false,
    val position: DevicePosition? = null,
    val problem: String = ""
)

data class AppUiState(
    val booting: Boolean = true,
    val session: Session? = null,
    val loadError: String? = null,
    val toast: String? = null,
    val online: Boolean = true,
    val syncing: Boolean = false,
    val lastSyncedAt: String = "",
    val plansRefreshVersion: Int = 0,
    val liveSharing: LiveSharingState = LiveSharingState(),
    val activeSection: MobileSection = MobileSection.AUDIT,
    val cities: List<City> = emptyList(),
    val territories: List<Territory> = emptyList(),
    val locations: List<LocationItem> = emptyList(),
    val subLocations: List<SubLocation> = emptyList(),
    val shops: List<Shop> = emptyList(),
    val distributors: List<Distributor> = emptyList(),
    val groups: List<ProductGroup> = emptyList(),
    val products: List<Product> = emptyList(),
    val users: List<AppUser> = emptyList(),
    val roles: List<UserRole> = emptyList(),
    val auditForms: List<AuditFormDefinition> = emptyList(),
    val formConfigs: FormConfigMap = emptyMap(),
    val formsSyncedOnce: Boolean = false,
    val newlySyncedFormIds: Set<String> = emptySet(),
    val mapsApiKey: String = "",
    val auditReports: List<DistributorProductAuditReport> = emptyList(),
    val retailReports: List<RetailMarketShopAuditReport> = emptyList(),
    val customSubmissions: List<CustomAuditSubmission> = emptyList(),
    val pendingOutbox: List<PendingOutboxItem> = emptyList(),
    // Geography drill-down filters
    val filterCityId: String = "",
    val filterTerritoryId: String = "",
    val filterLocationId: String = "",
    val filterSubLocationId: String = "",
    val selectedShop: Shop? = null,
    val selectedDistributor: Distributor? = null,
    val searchQuery: String = ""
)

class AuditGridViewModel(application: Application) : AndroidViewModel(application) {

    val apiClient = AuditGridApiClient(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val syncMutex = Mutex()
    private var toastJob: Job? = null
    private var periodicJob: Job? = null
    private var liveLocationListener: LocationListener? = null
    private var lastPingTimeMs: Long = 0L
    private var lastPingLat: Double = 0.0
    private var lastPingLng: Double = 0.0

    init {
        apiClient.onSessionEnded = {
            viewModelScope.launch {
                stopLiveTracking()
                _uiState.update {
                    AppUiState(
                        booting = false,
                        session = null,
                        online = it.online
                    )
                }
                showToast("Your session ended. Please sign in again.")
            }
        }
        setupNetworkMonitoring()
        restoreInitialSession()
    }

    private fun setupNetworkMonitoring() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val activeNet = cm.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val initialOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(online = initialOnline) }

        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _uiState.update { it.copy(online = true) }
                    if (_uiState.value.session != null) {
                        syncAllNow(manual = false)
                    }
                }

                override fun onLost(network: Network) {
                    _uiState.update { it.copy(online = false) }
                }
            })
        }
    }

    private fun restoreInitialSession() {
        viewModelScope.launch {
            val s = apiClient.restoreSession()
            if (s != null) {
                loadLocalCacheAndOutbox(s)
                _uiState.update { it.copy(session = s, booting = false) }
                loadAllData(s)
                startPeriodicSync()
                if (_uiState.value.online && _uiState.value.pendingOutbox.isNotEmpty()) {
                    syncAllNow(manual = false)
                }
            } else {
                _uiState.update { it.copy(booting = false) }
            }
        }
    }

    private fun loadLocalCacheAndOutbox(session: Session) {
        val auditorId = session.user.id
        val outbox = apiClient.loadOutboxItems(auditorId)
        val cachedDist = apiClient.loadCachedDistributorReports(auditorId)
        val cachedRetail = apiClient.loadCachedRetailReports(auditorId)
        val cachedCustom = apiClient.loadCachedCustomSubmissions(auditorId)
        val cachedBoot = apiClient.loadCachedBootstrap(auditorId, session)
        val cachedForms = apiClient.loadCachedForms(auditorId)
        val cachedConfigs = apiClient.loadCachedFormConfigs(auditorId)

        _uiState.update {
            it.copy(
                pendingOutbox = outbox,
                auditReports = cachedDist,
                retailReports = cachedRetail,
                customSubmissions = cachedCustom,
                formsSyncedOnce = cachedForms.isNotEmpty() || apiClient.hasSyncedFormsOnce(auditorId),
                auditForms = cachedForms,
                formConfigs = cachedConfigs,
                cities = cachedBoot?.cities ?: it.cities,
                territories = cachedBoot?.territories ?: it.territories,
                locations = cachedBoot?.locations ?: it.locations,
                subLocations = cachedBoot?.subLocations ?: it.subLocations,
                shops = cachedBoot?.shops ?: it.shops,
                distributors = cachedBoot?.distributors ?: it.distributors,
                groups = cachedBoot?.groups ?: it.groups,
                products = cachedBoot?.products ?: it.products,
                users = cachedBoot?.users ?: it.users,
                roles = cachedBoot?.roles ?: it.roles
            )
        }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(4000L)
            _uiState.update { state ->
                if (state.toast == message) state.copy(toast = null) else state
            }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = null) }
    }

    fun onSignedIn(session: Session) {
        viewModelScope.launch {
            loadLocalCacheAndOutbox(session)
            _uiState.update { it.copy(session = session, booting = false, loadError = null) }
            loadAllData(session)
            showToast("Welcome, ${session.user.fullName}")
            startPeriodicSync()
            if (_uiState.value.online && _uiState.value.pendingOutbox.isNotEmpty()) {
                syncAllNow(manual = false)
            }
        }
    }

    fun retryLoadAll() {
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(booting = true, loadError = null) }
            loadAllData(s)
            _uiState.update { it.copy(booting = false) }
        }
    }

    private suspend fun loadAllData(s: Session) {
        try {
            val boot = apiClient.fetchBootstrap(s)
            val dr = runCatching { apiClient.fetchDistributorReports() }.getOrNull()
            val rr = runCatching { apiClient.fetchRetailReports() }.getOrNull()
            val cs = runCatching { apiClient.fetchCustomSubmissions() }.getOrNull()

            val mergedDist = mergeById(dr, _uiState.value.auditReports) { it.id }
            val mergedRetail = mergeById(rr, _uiState.value.retailReports) { it.id }
            val mergedCustom = mergeById(cs, _uiState.value.customSubmissions) { it.id }

            apiClient.saveCachedDistributorReports(s.user.id, mergedDist)
            apiClient.saveCachedRetailReports(s.user.id, mergedRetail)
            apiClient.saveCachedCustomSubmissions(s.user.id, mergedCustom)

            val cachedForms = apiClient.loadCachedForms(s.user.id)
            val cachedConfigs = apiClient.loadCachedFormConfigs(s.user.id)
            val effectiveForms = mergeById(boot.forms, cachedForms) { it.id }
            val mergedCfg = mutableMapOf<String, FormConfigPayload>()
            mergedCfg.putAll(cachedConfigs)
            mergedCfg.putAll(boot.formConfigs)
            val effectiveConfigs: FormConfigMap = mergedCfg

            if (effectiveForms.isNotEmpty()) {
                apiClient.saveCachedForms(s.user.id, effectiveForms)
                apiClient.saveCachedFormConfigs(s.user.id, effectiveConfigs)
                apiClient.setFormsSyncedOnce(s.user.id, true)
            }

            val nowStr = currentTimePkt()
            _uiState.update {
                it.copy(
                    loadError = null,
                    lastSyncedAt = nowStr,
                    cities = boot.cities,
                    territories = boot.territories,
                    locations = boot.locations,
                    subLocations = boot.subLocations,
                    shops = boot.shops,
                    distributors = boot.distributors,
                    groups = boot.groups,
                    products = boot.products,
                    users = boot.users,
                    roles = boot.roles,
                    formsSyncedOnce = effectiveForms.isNotEmpty(),
                    auditForms = effectiveForms,
                    formConfigs = effectiveConfigs,
                    mapsApiKey = boot.mapsApiKey,
                    auditReports = mergedDist,
                    retailReports = mergedRetail,
                    customSubmissions = mergedCustom
                )
            }
            checkLiveSharingStatus()
        } catch (e: Exception) {
            val hasOfflineCache = _uiState.value.cities.isNotEmpty() || _uiState.value.auditForms.isNotEmpty()
            if (hasOfflineCache) {
                _uiState.update { it.copy(loadError = null) }
            } else {
                val msg = if (e is ApiError) e.message else "Could not load your data."
                _uiState.update { it.copy(loadError = msg) }
            }
        }
    }

    private fun <T> mergeById(
        remote: List<T>?,
        local: List<T>,
        idSelector: (T) -> String
    ): List<T> {
        if (remote == null) return local
        val remoteIds = remote.map(idSelector).toSet()
        return remote + local.filterNot { remoteIds.contains(idSelector(it)) }
    }

    /**
     * Full two-way sync:
     * 1. Uploads any queued offline audit reports from `pendingOutbox`.
     * 2. Refreshes cities, territories, locations, sub-locations, shops, distributors, groups, and SKUs (`/api/bootstrap`).
     * 3. Refreshes submitted reports (`/api/reports/distributor`, `/api/reports/retail`, `/api/submissions`).
     * 4. Fetches all audit forms (`/api/forms`) & field configurations (`/api/forms/{id}/config`) from the web application,
     *    inserts any newly created web forms into the mobile app, and saves all synced forms on the phone for offline & online use.
     */
    fun syncAllNow(manual: Boolean = false) {
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            if (!syncMutex.tryLock()) {
                if (manual) showToast("Sync is already in progress…")
                return@launch
            }
            _uiState.update { it.copy(syncing = true) }
            try {
                val previousFormIds = _uiState.value.auditForms.map { it.id }.toSet()
                var flushedCount = 0
                var failedCount = 0
                val currentOutbox = _uiState.value.pendingOutbox.toMutableList()
                val remainingOutbox = mutableListOf<PendingOutboxItem>()

                for (item in currentOutbox) {
                    try {
                        when {
                            item.distributorReport != null -> {
                                val saved = apiClient.submitDistributorReport(item.distributorReport)
                                prependDistributorReport(s.user.id, saved)
                                flushedCount++
                            }
                            item.retailReport != null -> {
                                val saved = apiClient.submitRetailShopReport(item.retailReport)
                                prependRetailReport(s.user.id, saved)
                                flushedCount++
                            }
                            item.customSubmission != null -> {
                                val c = item.customSubmission
                                val saved = apiClient.submitCustomAudit(
                                    clientRequestId = c.id,
                                    formId = c.formId,
                                    targetType = c.targetType,
                                    targetId = c.targetId,
                                    answers = c.answers,
                                    photos = c.capturedImages,
                                    mobileLat = c.mobileLat,
                                    mobileLng = c.mobileLng,
                                    mobileAccuracyMeters = c.mobileAccuracyMeters.toDouble(),
                                    planLink = if (!c.planId.isNullOrBlank() && !c.planItemId.isNullOrBlank()) {
                                        PlanLink(c.planId, c.planItemId)
                                    } else null,
                                    fallbackMeta = c
                                )
                                prependCustomSubmission(s.user.id, saved)
                                flushedCount++
                            }
                        }
                    } catch (e: Exception) {
                        failedCount++
                        val errMsg = if (e is ApiError) e.message else "Waiting for server connection"
                        remainingOutbox.add(item.copy(lastError = errMsg))
                    }
                }

                apiClient.saveOutboxItems(s.user.id, remainingOutbox)
                _uiState.update { it.copy(pendingOutbox = remainingOutbox) }

                // Refresh bootstrap master data & reports
                val boot = apiClient.fetchBootstrap(s)
                val dr = runCatching { apiClient.fetchDistributorReports() }.getOrNull()
                val rr = runCatching { apiClient.fetchRetailReports() }.getOrNull()
                val cs = runCatching { apiClient.fetchCustomSubmissions() }.getOrNull()

                // Fetch all active & newly created forms from web application and merge with phone cache
                val remoteForms = runCatching { apiClient.fetchForms() }.getOrDefault(boot.forms)
                val mergedForms = mergeById(remoteForms, _uiState.value.auditForms) { it.id }

                val mergedConfigs = mutableMapOf<String, FormConfigPayload>()
                mergedConfigs.putAll(_uiState.value.formConfigs)
                mergedConfigs.putAll(boot.formConfigs)
                for (f in mergedForms) {
                    runCatching { apiClient.fetchFormConfig(f.id) }.getOrNull()?.let { cfg ->
                        mergedConfigs[f.id] = cfg
                    }
                }

                // Persist synced forms & field configs on phone for offline and online use
                apiClient.saveCachedForms(s.user.id, mergedForms)
                apiClient.saveCachedFormConfigs(s.user.id, mergedConfigs)
                apiClient.setFormsSyncedOnce(s.user.id, true)

                val newlyInsertedForms = mergedForms.filter { it.id !in previousFormIds }
                val newlyInsertedIds = newlyInsertedForms.map { it.id }.toSet()

                val mergedDist = mergeById(dr, _uiState.value.auditReports) { it.id }
                val mergedRetail = mergeById(rr, _uiState.value.retailReports) { it.id }
                val mergedCustom = mergeById(cs, _uiState.value.customSubmissions) { it.id }

                apiClient.saveCachedDistributorReports(s.user.id, mergedDist)
                apiClient.saveCachedRetailReports(s.user.id, mergedRetail)
                apiClient.saveCachedCustomSubmissions(s.user.id, mergedCustom)

                val syncedTime = currentTimePkt()
                _uiState.update {
                    it.copy(
                        online = true,
                        lastSyncedAt = syncedTime,
                        plansRefreshVersion = it.plansRefreshVersion + 1,
                        cities = boot.cities,
                        territories = boot.territories,
                        locations = boot.locations,
                        subLocations = boot.subLocations,
                        shops = boot.shops,
                        distributors = boot.distributors,
                        groups = boot.groups,
                        products = boot.products,
                        users = boot.users,
                        roles = boot.roles,
                        formsSyncedOnce = true,
                        auditForms = mergedForms,
                        formConfigs = mergedConfigs,
                        newlySyncedFormIds = if (newlyInsertedIds.isNotEmpty()) newlyInsertedIds else it.newlySyncedFormIds,
                        mapsApiKey = boot.mapsApiKey,
                        auditReports = mergedDist,
                        retailReports = mergedRetail,
                        customSubmissions = mergedCustom
                    )
                }
                checkLiveSharingStatus()

                if (flushedCount > 0 && failedCount == 0) {
                    showToast("Synced $flushedCount queued audit(s) and saved ${mergedForms.size} form(s) on phone.")
                } else if (flushedCount > 0 && failedCount > 0) {
                    showToast("Uploaded $flushedCount audit(s); $failedCount still in outbox.")
                } else if (newlyInsertedForms.isNotEmpty()) {
                    if (previousFormIds.isEmpty()) {
                        showToast("Synced ${mergedForms.size} audit form(s) from web application & saved on phone for offline/online use.")
                    } else {
                        val codes = newlyInsertedForms.joinToString(", ") { it.code }
                        showToast("Inserted ${newlyInsertedForms.size} new form(s) from web server ($codes) & saved on phone.")
                    }
                } else if (manual) {
                    if (failedCount > 0) {
                        showToast("$failedCount queued audit(s) could not be uploaded yet.")
                    } else {
                        showToast("Synced all ${mergedForms.size} forms (saved offline & online), places, SKUs & plans ($syncedTime).")
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    val msg = if (e is ApiError) e.message else "Cannot reach server right now. Saved forms remain available offline."
                    showToast(msg)
                }
            } finally {
                _uiState.update { it.copy(syncing = false) }
                syncMutex.unlock()
            }
        }
    }

    fun refreshFormsSilently() {
        syncAllNow(manual = false)
    }

    private fun startPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = viewModelScope.launch {
            while (isActive) {
                delay(120_000L)
                if (_uiState.value.online && _uiState.value.session != null) {
                    syncAllNow(manual = false)
                }
            }
        }
    }

    fun notifyPlansChanged() {
        viewModelScope.launch {
            checkLiveSharingStatus()
        }
    }

    private suspend fun checkLiveSharingStatus() {
        if (_uiState.value.session == null) return
        try {
            val inProgress = apiClient.fetchPlans(inProgressOnly = true)
            val shouldShare = inProgress.isNotEmpty()
            val currentlySharing = _uiState.value.liveSharing.sharing
            _uiState.update {
                it.copy(liveSharing = it.liveSharing.copy(sharing = shouldShare))
            }
            if (shouldShare && !currentlySharing) {
                startLiveTracking()
            } else if (!shouldShare && currentlySharing) {
                stopLiveTracking()
            }
        } catch (_: Exception) {
            // offline: keep last state
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveTracking() {
        val ctx = getApplication<Application>()
        if (!hasLocationPermission(ctx)) {
            _uiState.update {
                it.copy(
                    liveSharing = it.liveSharing.copy(
                        problem = "Location is turned off, so your managers cannot see you. Allow location for this app."
                    )
                )
            }
            return
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        stopLiveTracking()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val pos = DevicePosition(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 20.0,
                    at = ""
                )
                _uiState.update {
                    it.copy(liveSharing = it.liveSharing.copy(position = pos, problem = ""))
                }
                val now = System.currentTimeMillis()
                if (lastPingTimeMs > 0 &&
                    now - lastPingTimeMs < 20_000L &&
                    haversineMeters(lastPingLat, lastPingLng, pos.lat, pos.lng) < 40.0
                ) {
                    return
                }
                lastPingTimeMs = now
                lastPingLat = pos.lat
                lastPingLng = pos.lng
                viewModelScope.launch {
                    try {
                        apiClient.sendTrackingPing(pos.lat, pos.lng, pos.accuracy)
                    } catch (e: ApiError) {
                        if (e.status == 409) {
                            stopLiveTracking()
                            _uiState.update { s ->
                                s.copy(liveSharing = s.liveSharing.copy(sharing = false))
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        liveLocationListener = listener
        runCatching {
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            lm.requestLocationUpdates(provider, 5000L, 5f, listener, Looper.getMainLooper())
        }.onFailure {
            _uiState.update {
                it.copy(liveSharing = it.liveSharing.copy(problem = "Waiting for a GPS signal…"))
            }
        }
    }

    private fun stopLiveTracking() {
        val listener = liveLocationListener ?: return
        liveLocationListener = null
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching { lm?.removeUpdates(listener) }
    }

    fun handleLogout() {
        viewModelScope.launch {
            periodicJob?.cancel()
            stopLiveTracking()
            apiClient.logout()
            _uiState.update {
                AppUiState(
                    booting = false,
                    session = null,
                    online = it.online
                )
            }
        }
    }

    fun setSection(section: MobileSection) {
        _uiState.update {
            it.copy(
                activeSection = section,
                selectedShop = null,
                selectedDistributor = null
            )
        }
    }

    fun setGeographyFilter(
        cityId: String = _uiState.value.filterCityId,
        territoryId: String = _uiState.value.filterTerritoryId,
        locationId: String = _uiState.value.filterLocationId,
        subLocationId: String = _uiState.value.filterSubLocationId,
        targetSection: MobileSection? = null
    ) {
        _uiState.update {
            it.copy(
                filterCityId = cityId,
                filterTerritoryId = territoryId,
                filterLocationId = locationId,
                filterSubLocationId = subLocationId,
                activeSection = targetSection ?: it.activeSection,
                selectedShop = null,
                selectedDistributor = null
            )
        }
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                filterCityId = "",
                filterTerritoryId = "",
                filterLocationId = "",
                filterSubLocationId = "",
                selectedShop = null,
                selectedDistributor = null,
                searchQuery = ""
            )
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun selectShop(shop: Shop?) {
        _uiState.update { it.copy(selectedShop = shop) }
    }

    fun selectDistributor(distributor: Distributor?) {
        _uiState.update { it.copy(selectedDistributor = distributor) }
    }

    private fun prependDistributorReport(auditorId: String, saved: DistributorProductAuditReport) {
        val updated = listOf(saved) + _uiState.value.auditReports.filter { it.id != saved.id }
        apiClient.saveCachedDistributorReports(auditorId, updated)
        _uiState.update { it.copy(auditReports = updated) }
    }

    private fun prependRetailReport(auditorId: String, saved: RetailMarketShopAuditReport) {
        val updated = listOf(saved) + _uiState.value.retailReports.filter { it.id != saved.id }
        apiClient.saveCachedRetailReports(auditorId, updated)
        _uiState.update { it.copy(retailReports = updated) }
    }

    private fun prependCustomSubmission(auditorId: String, saved: CustomAuditSubmission) {
        val updated = listOf(saved) + _uiState.value.customSubmissions.filter { it.id != saved.id }
        apiClient.saveCachedCustomSubmissions(auditorId, updated)
        _uiState.update { it.copy(customSubmissions = updated) }
    }

    private fun enqueueOutboxItem(auditorId: String, item: PendingOutboxItem) {
        val updated = listOf(item) + _uiState.value.pendingOutbox.filter { it.id != item.id }
        apiClient.saveOutboxItems(auditorId, updated)
        _uiState.update { it.copy(pendingOutbox = updated) }
    }

    suspend fun submitDistributorReport(report: DistributorProductAuditReport): DistributorProductAuditReport {
        val auditorId = _uiState.value.session?.user?.id ?: report.auditorId
        return try {
            val saved = apiClient.submitDistributorReport(report)
            prependDistributorReport(auditorId, saved)
            showToast("Submitted ${saved.reportCode} for ${saved.distributorSetupName}.")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val outboxItem = PendingOutboxItem(
                    id = report.id,
                    kind = "distributor",
                    reportCode = report.reportCode,
                    formId = report.formId,
                    formTitle = "Distributor Product Stock Audit",
                    targetName = report.distributorSetupName,
                    territoryCode = report.territoryCode,
                    cityName = report.cityName,
                    createdAtDisplay = "${report.submittedDate} · ${report.submittedTime}",
                    createdAtISO = report.submittedAtISO,
                    locationMatched = report.locationMatched,
                    distributorReport = report
                )
                enqueueOutboxItem(auditorId, outboxItem)
                showToast("Saved ${report.reportCode} to Offline Outbox. Will auto-sync when online.")
                report
            } else {
                throw e
            }
        }
    }

    suspend fun submitRetailShopReport(report: RetailMarketShopAuditReport): RetailMarketShopAuditReport {
        val auditorId = _uiState.value.session?.user?.id ?: report.auditorId
        return try {
            val saved = apiClient.submitRetailShopReport(report)
            prependRetailReport(auditorId, saved)
            showToast("Submitted ${saved.reportCode} for ${saved.subLocationName}.")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val outboxItem = PendingOutboxItem(
                    id = report.id,
                    kind = "retail",
                    reportCode = report.reportCode,
                    formId = report.formId,
                    formTitle = "Retail Market Shop Audit",
                    targetName = "${report.subLocationName} (${report.auditedShopsCount} shops)",
                    territoryCode = report.territoryCode,
                    cityName = report.cityName,
                    createdAtDisplay = "${report.submittedDate} · ${report.submittedTime}",
                    createdAtISO = report.submittedAtISO,
                    locationMatched = report.auditedShopsCount > 0 && report.matchedLocationShopsCount == report.auditedShopsCount,
                    retailReport = report
                )
                enqueueOutboxItem(auditorId, outboxItem)
                showToast("Saved ${report.reportCode} to Offline Outbox. Will auto-sync when online.")
                report
            } else {
                throw e
            }
        }
    }

    suspend fun submitCustomAuditReport(
        localRecord: CustomAuditSubmission,
        rawAnswers: Map<String, Any?>,
        planLink: PlanLink?
    ): CustomAuditSubmission {
        val auditorId = _uiState.value.session?.user?.id ?: localRecord.auditorId
        return try {
            val saved = apiClient.submitCustomAudit(
                clientRequestId = localRecord.id,
                formId = localRecord.formId,
                targetType = localRecord.targetType,
                targetId = localRecord.targetId,
                answers = rawAnswers,
                photos = localRecord.capturedImages,
                mobileLat = localRecord.mobileLat,
                mobileLng = localRecord.mobileLng,
                mobileAccuracyMeters = localRecord.mobileAccuracyMeters.toDouble(),
                planLink = planLink,
                fallbackMeta = localRecord
            )
            prependCustomSubmission(auditorId, saved)
            showToast("Submitted ${saved.reportCode} (${saved.formTitle}).")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val outboxItem = PendingOutboxItem(
                    id = localRecord.id,
                    kind = "custom",
                    reportCode = localRecord.reportCode,
                    formId = localRecord.formId,
                    formTitle = localRecord.formTitle,
                    targetName = localRecord.targetName,
                    territoryCode = localRecord.territoryCode,
                    cityName = localRecord.cityName,
                    createdAtDisplay = "${localRecord.submittedDate} · ${localRecord.submittedTime}",
                    createdAtISO = localRecord.submittedAtISO,
                    locationMatched = localRecord.locationMatched,
                    customSubmission = localRecord
                )
                enqueueOutboxItem(auditorId, outboxItem)
                showToast("Saved ${localRecord.reportCode} to Offline Outbox. Will auto-sync when online.")
                localRecord
            } else {
                throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveTracking()
    }
}

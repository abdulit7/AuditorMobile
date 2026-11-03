package pk.auditgrid.audittest.model

data class City(
    val id: String,
    val code: String,
    val name: String,
    val province: String,
    val region: String,
    val lat: Double,
    val lng: Double,
    val status: String,
    val createdAt: String = ""
)

data class Territory(
    val id: String,
    val code: String,
    val name: String,
    val cityId: String,
    val distributorName: String,
    val distributorCode: String,
    val assignedAuditorIds: List<String>,
    val lat: Double,
    val lng: Double,
    val status: String,
    val updatedAt: String = ""
)

data class LocationItem(
    val id: String,
    val code: String,
    val name: String,
    val territoryId: String,
    val cityId: String,
    val areaClassification: String,
    val lat: Double,
    val lng: Double,
    val status: String
)

data class SubLocation(
    val id: String,
    val code: String,
    val name: String,
    val locationId: String,
    val territoryId: String,
    val cityId: String,
    val marketType: String,
    val postalCode: String,
    val lat: Double,
    val lng: Double,
    val status: String
)

data class Shop(
    val id: String,
    val code: String,
    val name: String,
    val ownerName: String,
    val phone: String,
    val subLocationId: String,
    val locationId: String,
    val territoryId: String,
    val cityId: String,
    val shopCategory: String,
    val lat: Double,
    val lng: Double,
    val allowedRadiusMeters: Int,
    val gpsStatus: String,
    val gpsAccuracyMeters: Int? = null,
    val status: String,
    val lastVerifiedAt: String = "",
    val addressLine: String
)

data class ProductGroup(
    val id: String,
    val code: String,
    val name: String,
    val divisionTitle: String = "",
    val description: String = "",
    val storageType: String = "",
    val defaultStackingMaxCtn: Int = 10,
    val badgeStyle: String = "groupA",
    val status: String = "Active"
)

data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val groupId: String,
    val groupName: String,
    val unitPerBox: String,
    val unitsCountPerBox: Int,
    val boxesPerCtn: Int,
    val pricePerBoxRs: Double,
    val stackingStandard: String = "",
    val shelfLifeDays: Int = 365,
    val status: String = "Active"
)

data class UserRole(
    val id: String,
    val code: String,
    val name: String,
    val surface: String,
    val description: String = "",
    val isSystemRole: Boolean = false,
    val permissions: Map<String, Boolean> = emptyMap()
)

data class AppUser(
    val id: String,
    val code: String,
    val userType: String,
    val canLoginFromMobile: Boolean = true,
    val canLoginFromWeb: Boolean = false,
    val username: String,
    val fullName: String,
    val email: String = "",
    val phone: String = "",
    val roleId: String = "",
    val surfaceAccess: String = "Mobile PWA",
    val assignedCityIds: List<String> = emptyList(),
    val assignedTerritoryIds: List<String> = emptyList(),
    val assignedGroupIds: List<String> = emptyList(),
    val status: String = "Active",
    val lastActiveAt: String = ""
)

data class Distributor(
    val id: String,
    val code: String,
    val setupName: String,
    val distributorName: String,
    val fatherName: String,
    val cnic: String,
    val phone: String,
    val cityId: String,
    val territoryId: String,
    val completeAddress: String,
    val groupIds: List<String>,
    val productStatuses: Map<String, String>,
    val lat: Double,
    val lng: Double,
    val status: String,
    val createdAt: String = ""
)

data class AuditFormDefinition(
    val id: String,
    val code: String,
    val title: String,
    val shortTitle: String,
    val category: String,
    val kind: String = "builtin",
    val target: String = "distributor",
    val description: String = "",
    val requiresBackgroundGPS: Boolean = true,
    val allowMultipleEntriesPerProduct: Boolean = true,
    val allowPhotoCapture: Boolean = true,
    val status: String = "Active",
    val updatedAt: String = ""
)

data class AuditCapturedImage(
    val id: String,
    val url: String,
    val caption: String,
    val capturedAt: String
)

data class DistributorAuditLineItem(
    val id: String,
    val productId: String,
    val sku: String,
    val productName: String,
    val boxesPerCtn: Int,
    val unitPerBox: String,
    val unitsCountPerBox: Int,
    val pricePerBoxRs: Double,
    val qtyInCtn: Int,
    val qtyInBox: Int,
    val totalCtns: Double,
    val dispatchDate: String,
    val stacking: String,
    val remarks: String,
    val stockValueRs: Double
)

data class DistributorProductAuditReport(
    val id: String,
    val reportCode: String,
    val formId: String,
    val distributorId: String,
    val distributorCode: String,
    val distributorSetupName: String,
    val distributorOwnerName: String,
    val groupCode: String,
    val groupName: String,
    val cityId: String,
    val cityName: String,
    val territoryId: String,
    val territoryCode: String,
    val territoryName: String,
    val auditorId: String,
    val auditorCode: String,
    val auditorName: String,
    val auditorUsername: String,
    val reportDateDisplay: String,
    val submittedDate: String,
    val submittedTime: String,
    val submittedAtISO: String,
    val mobileLat: Double,
    val mobileLng: Double,
    val mobileAccuracyMeters: Int,
    val distributorLat: Double,
    val distributorLng: Double,
    val distanceMeters: Int,
    val allowedRadiusMeters: Int,
    val locationMatched: Boolean,
    val items: List<DistributorAuditLineItem>,
    val totalCtns: Double,
    val totalStockValueRs: Double,
    val capturedImages: List<AuditCapturedImage>,
    val overallComments: String? = null,
    val reviewStatus: String? = null,
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class RetailShopAuditItem(
    val shopId: String,
    val shopCode: String,
    val shopName: String,
    val ownerName: String,
    val phone: String,
    val addressLine: String,
    val shopCategory: String,
    val productACount: Int,
    val productBCount: Int,
    val displayRating: String,
    val packedProductQty: Int,
    val regularity: String,
    val remarks: String,
    val capturedImages: List<AuditCapturedImage>,
    val auditStatus: String,
    val auditedAt: String,
    val mobileLat: Double,
    val mobileLng: Double,
    val mobileAccuracyMeters: Int,
    val shopLat: Double,
    val shopLng: Double,
    val distanceMeters: Int,
    val allowedRadiusMeters: Int,
    val locationMatched: Boolean
)

data class RetailMarketShopAuditReport(
    val id: String,
    val reportCode: String,
    val formId: String,
    val cityId: String,
    val cityName: String,
    val territoryId: String,
    val territoryCode: String,
    val territoryName: String,
    val locationId: String,
    val locationCode: String,
    val locationName: String,
    val subLocationId: String,
    val subLocationCode: String,
    val subLocationName: String,
    val auditorId: String,
    val auditorCode: String,
    val auditorName: String,
    val auditorUsername: String,
    val reportDateDisplay: String,
    val submittedDate: String,
    val submittedTime: String,
    val submittedAtISO: String,
    val totalShopsInSubLocation: Int,
    val auditedShopsCount: Int,
    val totalProductACount: Int,
    val totalProductBCount: Int,
    val totalPackedProductQty: Int,
    val matchedLocationShopsCount: Int,
    val shopAudits: List<RetailShopAuditItem>,
    val reviewStatus: String? = null,
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class CustomAuditSubmission(
    val id: String,
    val reportCode: String,
    val formId: String,
    val formCode: String = "",
    val formTitle: String,
    val targetType: String,
    val targetId: String,
    val targetCode: String = "",
    val targetName: String,
    val cityName: String = "",
    val territoryCode: String = "",
    val territoryName: String = "",
    val auditorId: String,
    val auditorName: String,
    val submittedDate: String = "",
    val submittedTime: String = "",
    val submittedAtISO: String,
    val mobileLat: Double = 0.0,
    val mobileLng: Double = 0.0,
    val mobileAccuracyMeters: Int = 0,
    val distanceMeters: Int = 0,
    val allowedRadiusMeters: Int = 150,
    val locationMatched: Boolean,
    val answers: Map<String, String> = emptyMap(),
    val capturedImages: List<AuditCapturedImage> = emptyList(),
    val reviewStatus: String = "Submitted",
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class PendingOutboxItem(
    val id: String,
    val kind: String, // "distributor" | "retail" | "custom"
    val reportCode: String,
    val formId: String,
    val formTitle: String,
    val targetName: String,
    val territoryCode: String,
    val cityName: String,
    val createdAtDisplay: String,
    val createdAtISO: String,
    val locationMatched: Boolean,
    val distributorReport: DistributorProductAuditReport? = null,
    val retailReport: RetailMarketShopAuditReport? = null,
    val customSubmission: CustomAuditSubmission? = null,
    val lastError: String? = null
)

data class PlanItem(
    val id: String,
    val formId: String,
    val formTitle: String,
    val targetType: String,
    val targetId: String,
    val targetLabel: String,
    val cityId: String = "",
    val territoryId: String = "",
    val shopIds: List<String> = emptyList(),
    val status: String = "Pending",
    val doneAt: String? = null,
    val reportKind: String? = null,
    val reportId: String? = null,
    val reportCode: String? = null,
    val skipReason: String? = null
)

data class AuditPlan(
    val id: String,
    val auditorId: String,
    val auditorName: String? = null,
    val planDate: String,
    val title: String,
    val notes: String = "",
    val status: String,
    val startedAt: String? = null,
    val items: List<PlanItem>
)

data class FormField(
    val key: String,
    val label: String,
    val helpText: String? = null,
    val placeholder: String? = null,
    val type: String, // 'number' | 'date' | 'select' | 'text' | 'textarea' | 'photo' | 'yesno'
    val required: Boolean = false,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLocked: Boolean = false,
    val min: Double? = null,
    val max: Double? = null
)

data class FormSettings(
    val allowPhotoCapture: Boolean = true,
    val maxPhotos: Int = 6,
    val requirePhoto: Boolean = false,
    val allowMultipleEntriesPerProduct: Boolean = true,
    val requireGps: Boolean = true,
    val allowedRadiusMeters: Int = 150,
    val maxAccuracyMeters: Int = 100
)

data class FormConfigPayload(
    val formId: String,
    val version: Int = 1,
    val settings: FormSettings = FormSettings(),
    val fields: List<FormField> = emptyList()
)

typealias FormConfigMap = Map<String, FormConfigPayload>

const val DISTRIBUTOR_FORM_ID = "form-distributor-product-audit"
const val RETAIL_FORM_ID = "form-retail-market-shop-audit"

class FormHelper(private val cfg: FormConfigPayload?) {
    val version: Int = cfg?.version ?: 0
    val settings: FormSettings = cfg?.settings ?: FormSettings()

    private fun fieldOf(key: String): FormField? = cfg?.fields?.find { it.key == key }

    fun label(key: String, fallback: String): String = fieldOf(key)?.label ?: fallback
    fun visible(key: String): Boolean = fieldOf(key)?.visible ?: true
    fun required(key: String, fallback: Boolean = false): Boolean = fieldOf(key)?.required ?: fallback
    fun options(key: String, fallback: List<String>): List<String> =
        fieldOf(key)?.options?.takeIf { it.isNotEmpty() } ?: fallback
    fun placeholder(key: String, fallback: String = ""): String = fieldOf(key)?.placeholder ?: fallback
    fun help(key: String): String? = fieldOf(key)?.helpText
}

fun makeFormHelper(cfg: FormConfigPayload?): FormHelper = FormHelper(cfg)

data class Session(
    val user: AppUser,
    val role: UserRole?,
    val surface: String,
    val isGlobal: Boolean
)

data class BootstrapPayload(
    val cities: List<City>,
    val territories: List<Territory>,
    val locations: List<LocationItem>,
    val subLocations: List<SubLocation>,
    val shops: List<Shop>,
    val distributors: List<Distributor>,
    val groups: List<ProductGroup>,
    val products: List<Product>,
    val users: List<AppUser>,
    val roles: List<UserRole>,
    val forms: List<AuditFormDefinition>,
    val formConfigs: FormConfigMap,
    val mapsApiKey: String
)

data class PlanLink(
    val planId: String,
    val itemId: String
)

data class AuditPreset(
    val distributorId: String? = null,
    val subLocationId: String? = null,
    val targetId: String? = null,
    val shopIds: List<String> = emptyList()
)

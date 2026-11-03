package pk.auditgrid.audittest.api

import pk.auditgrid.audittest.model.*

/**
 * Default server master catalog (Cities, Territories, Locations, Sub-locations, Shops,
 * Distributors, Product Groups, SKUs, and the 3 standard Audit Forms AUD-01, AUD-02, AUD-03)
 * so the mobile app functions in the cloud emulator and offline when the local LAN server
 * is not reachable. Contains zero dummy completed audit reports and zero dummy daily plans.
 */
object DefaultServerCatalog {

    val defaultRole = UserRole(
        id = "role-auditor",
        code = "ROLE-AUD",
        name = "Field Auditor",
        surface = "mobile",
        description = "Field auditor mobile access",
        isSystemRole = true,
        permissions = mapOf(
            "audit.submit" to true,
            "audit.viewOwn" to true,
            "places.view" to true,
            "plans.viewOwn" to true
        )
    )

    fun buildSession(usernameInput: String): Session {
        val clean = usernameInput.trim().ifBlank { "auditor" }
        val displayName = clean
            .replace('.', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            .ifBlank { "Field Auditor" }

        val user = AppUser(
            id = "usr-${clean.lowercase().replace(Regex("[^a-z0-9]"), "-")}",
            code = "AUD-01",
            userType = "Auditor",
            canLoginFromMobile = true,
            canLoginFromWeb = false,
            username = clean,
            fullName = displayName,
            email = "$clean@auditgrid.pk",
            phone = "+92-300-0000000",
            roleId = defaultRole.id,
            surfaceAccess = "Mobile PWA",
            assignedCityIds = listOf("city-lhr", "city-khi", "city-isb"),
            assignedTerritoryIds = listOf("ter-lhr-north", "ter-lhr-south", "ter-khi-east", "ter-isb-central"),
            assignedGroupIds = listOf("grp-a", "grp-b"),
            status = "Active"
        )
        return Session(
            user = user,
            role = defaultRole,
            surface = "mobile",
            isGlobal = false
        )
    }

    fun buildUser(usernameInput: String): AppUser = buildSession(usernameInput).user

    val cities = listOf(
        City(
            id = "city-lhr",
            code = "LHR",
            name = "Lahore",
            province = "Punjab",
            region = "Central Punjab",
            lat = 31.5204,
            lng = 74.3587,
            status = "Active"
        ),
        City(
            id = "city-khi",
            code = "KHI",
            name = "Karachi",
            province = "Sindh",
            region = "South Sindh",
            lat = 24.8607,
            lng = 67.0011,
            status = "Active"
        ),
        City(
            id = "city-isb",
            code = "ISB",
            name = "Islamabad",
            province = "ICT",
            region = "North Capital",
            lat = 33.6844,
            lng = 73.0479,
            status = "Active"
        )
    )

    val territories = listOf(
        Territory(
            id = "ter-lhr-north",
            code = "LHR-N01",
            name = "Lahore North Territory",
            cityId = "city-lhr",
            distributorName = "Al-Fatah Traders Lahore",
            distributorCode = "DST-LHR-01",
            assignedAuditorIds = emptyList(),
            lat = 31.5102,
            lng = 74.3441,
            status = "Active"
        ),
        Territory(
            id = "ter-lhr-south",
            code = "LHR-S02",
            name = "Lahore South Territory",
            cityId = "city-lhr",
            distributorName = "Punjab Consumer Distribution",
            distributorCode = "DST-LHR-02",
            assignedAuditorIds = emptyList(),
            lat = 31.4697,
            lng = 74.2728,
            status = "Active"
        ),
        Territory(
            id = "ter-khi-east",
            code = "KHI-E01",
            name = "Karachi East Territory",
            cityId = "city-khi",
            distributorName = "Mehran Bulk Supply House",
            distributorCode = "DST-KHI-01",
            assignedAuditorIds = emptyList(),
            lat = 24.9180,
            lng = 67.0971,
            status = "Active"
        ),
        Territory(
            id = "ter-isb-central",
            code = "ISB-C01",
            name = "Islamabad Central Territory",
            cityId = "city-isb",
            distributorName = "Capital Valley Logistics",
            distributorCode = "DST-ISB-01",
            assignedAuditorIds = emptyList(),
            lat = 33.6693,
            lng = 73.0738,
            status = "Active"
        )
    )

    val locations = listOf(
        LocationItem(
            id = "loc-gulberg",
            code = "LOC-GLB",
            name = "Gulberg Commercial Zone",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            areaClassification = "Urban Commercial",
            lat = 31.5102,
            lng = 74.3441,
            status = "Active"
        ),
        LocationItem(
            id = "loc-modeltown",
            code = "LOC-MDT",
            name = "Model Town Link Road",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            areaClassification = "Retail Hub",
            lat = 31.4805,
            lng = 74.3239,
            status = "Active"
        ),
        LocationItem(
            id = "loc-johartown",
            code = "LOC-JHR",
            name = "Johar Town Main Blvd",
            territoryId = "ter-lhr-south",
            cityId = "city-lhr",
            areaClassification = "Mixed Commercial",
            lat = 31.4697,
            lng = 74.2728,
            status = "Active"
        ),
        LocationItem(
            id = "loc-gulshan",
            code = "LOC-GSH",
            name = "Gulshan-e-Iqbal Block 13",
            territoryId = "ter-khi-east",
            cityId = "city-khi",
            areaClassification = "High Density Market",
            lat = 24.9180,
            lng = 67.0971,
            status = "Active"
        ),
        LocationItem(
            id = "loc-bluearea",
            code = "LOC-BLU",
            name = "Blue Area Jinnah Ave",
            territoryId = "ter-isb-central",
            cityId = "city-isb",
            areaClassification = "Commercial Center",
            lat = 33.7077,
            lng = 73.0568,
            status = "Active"
        )
    )

    val subLocations = listOf(
        SubLocation(
            id = "sub-liberty",
            code = "SUB-LIB-01",
            name = "Liberty Market Circle",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            marketType = "Wholesale & Retail",
            postalCode = "54660",
            lat = 31.5115,
            lng = 74.3418,
            status = "Active"
        ),
        SubLocation(
            id = "sub-ghalib",
            code = "SUB-GHB-02",
            name = "Ghalib Market Strip",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            marketType = "Neighborhood Market",
            postalCode = "54660",
            lat = 31.5142,
            lng = 74.3521,
            status = "Active"
        ),
        SubLocation(
            id = "sub-amanah",
            code = "SUB-AMN-01",
            name = "Link Road Retail Strip",
            locationId = "loc-modeltown",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            marketType = "High Street Retail",
            postalCode = "54700",
            lat = 31.4805,
            lng = 74.3239,
            status = "Active"
        ),
        SubLocation(
            id = "sub-g1market",
            code = "SUB-G1M-01",
            name = "Johar Town G-1 Market",
            locationId = "loc-johartown",
            territoryId = "ter-lhr-south",
            cityId = "city-lhr",
            marketType = "Community Market",
            postalCode = "54782",
            lat = 31.4697,
            lng = 74.2728,
            status = "Active"
        ),
        SubLocation(
            id = "sub-disco",
            code = "SUB-DSC-01",
            name = "Disco Bakery Market",
            locationId = "loc-gulshan",
            territoryId = "ter-khi-east",
            cityId = "city-khi",
            marketType = "Central Bazaar",
            postalCode = "75300",
            lat = 24.9204,
            lng = 67.0945,
            status = "Active"
        )
    )

    val shops = listOf(
        Shop(
            id = "shp-lib-01",
            code = "SHP-1001",
            name = "Al-Madina Cash & Carry",
            ownerName = "Haji Tariq Mehmood",
            phone = "+92-321-4101001",
            subLocationId = "sub-liberty",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            shopCategory = "LMT / Cash & Carry",
            lat = 31.5116,
            lng = 74.3420,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 8,
            status = "Active",
            addressLine = "Shop 14-B, Commercial Plaza, Liberty Roundabout, Gulberg III, Lahore"
        ),
        Shop(
            id = "shp-lib-02",
            code = "SHP-1002",
            name = "Bismillah General Store",
            ownerName = "Muhammad Nadeem",
            phone = "+92-300-4229981",
            subLocationId = "sub-liberty",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            shopCategory = "Kiryana / General Store",
            lat = 31.5121,
            lng = 74.3425,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 10,
            status = "Active",
            addressLine = "Shop 4, Main Liberty Lane, Gulberg III, Lahore"
        ),
        Shop(
            id = "shp-lib-03",
            code = "SHP-1003",
            name = "Lahore Super Mart",
            ownerName = "Sheikh Zafar Iqbal",
            phone = "+92-333-4891122",
            subLocationId = "sub-liberty",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            shopCategory = "Super Store",
            lat = 31.5110,
            lng = 74.3412,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 7,
            status = "Active",
            addressLine = "22-C Dupatta Gali Corner, Liberty Market, Lahore"
        ),
        Shop(
            id = "shp-ghb-01",
            code = "SHP-1004",
            name = "Ghalib Fresh Mart",
            ownerName = "Rana Asif Ali",
            phone = "+92-322-4556789",
            subLocationId = "sub-ghalib",
            locationId = "loc-gulberg",
            territoryId = "ter-lhr-north",
            cityId = "city-lhr",
            shopCategory = "Mini Mart",
            lat = 31.5143,
            lng = 74.3522,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 9,
            status = "Active",
            addressLine = "Plot 88, Main Ghalib Market, Gulberg III, Lahore"
        ),
        Shop(
            id = "shp-g1m-01",
            code = "SHP-2001",
            name = "Rehman Brothers Provision Store",
            ownerName = "Abdur Rehman",
            phone = "+92-301-4773311",
            subLocationId = "sub-g1market",
            locationId = "loc-johartown",
            territoryId = "ter-lhr-south",
            cityId = "city-lhr",
            shopCategory = "Wholesale & Retail",
            lat = 31.4698,
            lng = 74.2730,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 8,
            status = "Active",
            addressLine = "64-G1 Market, Phase 1 Johar Town, Lahore"
        ),
        Shop(
            id = "shp-khi-01",
            code = "SHP-3001",
            name = "Karachi Mart & Super Store",
            ownerName = "Salman Memon",
            phone = "+92-331-2445566",
            subLocationId = "sub-disco",
            locationId = "loc-gulshan",
            territoryId = "ter-khi-east",
            cityId = "city-khi",
            shopCategory = "Super Store",
            lat = 24.9205,
            lng = 67.0946,
            allowedRadiusMeters = 150,
            gpsStatus = "Verified",
            gpsAccuracyMeters = 9,
            status = "Active",
            addressLine = "Plot B-12, Block 13-D, Gulshan-e-Iqbal, Karachi"
        )
    )

    val groups = listOf(
        ProductGroup(
            id = "grp-a",
            code = "Group A",
            name = "Group A — Core Culinary & Beverage",
            divisionTitle = "Culinary & Beverages Division",
            description = "High-velocity tea, culinary pastes, and seasoning lines",
            storageType = "Ambient Dry Warehouse",
            defaultStackingMaxCtn = 8,
            badgeStyle = "groupA",
            status = "Active"
        ),
        ProductGroup(
            id = "grp-b",
            code = "Group B",
            name = "Group B — Confectionery & Dairy",
            divisionTitle = "Dairy & Impulse Division",
            description = "Temperature-sensitive dairy, biscuits, and impulse packs",
            storageType = "Cool Dry (<28°C)",
            defaultStackingMaxCtn = 6,
            badgeStyle = "groupB",
            status = "Active"
        )
    )

    val products = listOf(
        Product(
            id = "prd-a1",
            sku = "SKU-GA-101",
            name = "Supreme Gold Leaf Tea 900g",
            groupId = "grp-a",
            groupName = "Group A",
            unitPerBox = "12 Pouches x 900g",
            unitsCountPerBox = 12,
            boxesPerCtn = 4,
            pricePerBoxRs = 16800.0,
            stackingStandard = "Max 8 CTN high on wooden pallet",
            shelfLifeDays = 540,
            status = "Active"
        ),
        Product(
            id = "prd-a2",
            sku = "SKU-GA-102",
            name = "Everyday Cardamom Tea SachetJar",
            groupId = "grp-a",
            groupName = "Group A",
            unitPerBox = "24 Jars x 100 Sachets",
            unitsCountPerBox = 24,
            boxesPerCtn = 6,
            pricePerBoxRs = 9600.0,
            stackingStandard = "Max 6 CTN high",
            shelfLifeDays = 365,
            status = "Active"
        ),
        Product(
            id = "prd-a3",
            sku = "SKU-GA-103",
            name = "Royal Biryani Masala Twin Pack",
            groupId = "grp-a",
            groupName = "Group A",
            unitPerBox = "48 Packs x 120g",
            unitsCountPerBox = 48,
            boxesPerCtn = 8,
            pricePerBoxRs = 7200.0,
            stackingStandard = "Max 8 CTN high",
            shelfLifeDays = 730,
            status = "Active"
        ),
        Product(
            id = "prd-b1",
            sku = "SKU-GB-201",
            name = "Full Cream Milk Powder 800g",
            groupId = "grp-b",
            groupName = "Group B",
            unitPerBox = "12 Tins x 800g",
            unitsCountPerBox = 12,
            boxesPerCtn = 4,
            pricePerBoxRs = 19200.0,
            stackingStandard = "Max 6 CTN high",
            shelfLifeDays = 365,
            status = "Active"
        ),
        Product(
            id = "prd-b2",
            sku = "SKU-GB-202",
            name = "Butter Crunch Family Pack",
            groupId = "grp-b",
            groupName = "Group B",
            unitPerBox = "24 Half-Rolls",
            unitsCountPerBox = 24,
            boxesPerCtn = 6,
            pricePerBoxRs = 4800.0,
            stackingStandard = "Max 5 CTN high — fragile",
            shelfLifeDays = 270,
            status = "Active"
        )
    )

    val distributors = listOf(
        Distributor(
            id = "dst-lhr-01",
            code = "DST-LHR-01",
            setupName = "Al-Fatah Traders Lahore",
            distributorName = "Mian Naveed Akhtar",
            fatherName = "Mian Bashir Ahmad",
            cnic = "35202-1199887-1",
            phone = "+92-300-8441100",
            cityId = "city-lhr",
            territoryId = "ter-lhr-north",
            completeAddress = "Plot 41, Industrial Warehouse Complex, Gulberg III, Lahore",
            groupIds = listOf("grp-a", "grp-b"),
            productStatuses = mapOf(
                "prd-a1" to "Continue",
                "prd-a2" to "Continue",
                "prd-a3" to "Continue",
                "prd-b1" to "Continue",
                "prd-b2" to "Continue"
            ),
            lat = 31.5102,
            lng = 74.3441,
            status = "Active"
        ),
        Distributor(
            id = "dst-lhr-02",
            code = "DST-LHR-02",
            setupName = "Punjab Consumer Distribution",
            distributorName = "Chaudhry Faisal Mehmood",
            fatherName = "Chaudhry Anwar",
            cnic = "35202-6677889-3",
            phone = "+92-321-9445522",
            cityId = "city-lhr",
            territoryId = "ter-lhr-south",
            completeAddress = "Warehouse 19-A, Katar Bund Road, Thokar Niaz Baig, Lahore",
            groupIds = listOf("grp-a", "grp-b"),
            productStatuses = mapOf(
                "prd-a1" to "Continue",
                "prd-a2" to "Continue",
                "prd-a3" to "Continue",
                "prd-b1" to "Continue"
            ),
            lat = 31.4697,
            lng = 74.2728,
            status = "Active"
        ),
        Distributor(
            id = "dst-khi-01",
            code = "DST-KHI-01",
            setupName = "Mehran Bulk Supply House",
            distributorName = "Zubair Ahmed Siddiqui",
            fatherName = "Iqbal Siddiqui",
            cnic = "42101-8877665-5",
            phone = "+92-333-2110099",
            cityId = "city-khi",
            territoryId = "ter-khi-east",
            completeAddress = "Sector 16-B, Korangi Industrial Area, Karachi",
            groupIds = listOf("grp-a", "grp-b"),
            productStatuses = mapOf(
                "prd-a1" to "Continue",
                "prd-a2" to "Continue",
                "prd-b1" to "Continue",
                "prd-b2" to "Continue"
            ),
            lat = 24.9180,
            lng = 67.0971,
            status = "Active"
        ),
        Distributor(
            id = "dst-isb-01",
            code = "DST-ISB-01",
            setupName = "Capital Valley Logistics",
            distributorName = "Sardar Kamran Khan",
            fatherName = "Sardar Sikandar",
            cnic = "61101-3344556-7",
            phone = "+92-345-5112233",
            cityId = "city-isb",
            territoryId = "ter-isb-central",
            completeAddress = "Street 9, I-9/2 Industrial Area, Islamabad",
            groupIds = listOf("grp-a"),
            productStatuses = mapOf(
                "prd-a1" to "Continue",
                "prd-a2" to "Continue",
                "prd-a3" to "Continue"
            ),
            lat = 33.6693,
            lng = 73.0738,
            status = "Active"
        )
    )

    val forms = listOf(
        AuditFormDefinition(
            id = DISTRIBUTOR_FORM_ID,
            code = "AUD-01",
            title = "Distributor Product Audit",
            shortTitle = "Distributor Audit",
            category = "Warehouse & Stock",
            kind = "builtin",
            target = "distributor",
            description = "Physical warehouse stock count, batch dispatch dates, stacking compliance, and stock value calculation.",
            requiresBackgroundGPS = true,
            allowMultipleEntriesPerProduct = true,
            allowPhotoCapture = true,
            status = "Active",
            updatedAt = "2025-05-01"
        ),
        AuditFormDefinition(
            id = RETAIL_FORM_ID,
            code = "AUD-02",
            title = "Retail Market Shop Audit",
            shortTitle = "Retail Shop Audit",
            category = "Market Execution",
            kind = "builtin",
            target = "subLocation",
            description = "Sub-location retail shop execution check, Product A/B shelf counts, display rating, and distributor supply regularity.",
            requiresBackgroundGPS = true,
            allowMultipleEntriesPerProduct = false,
            allowPhotoCapture = true,
            status = "Active",
            updatedAt = "2025-05-01"
        ),
        AuditFormDefinition(
            id = "form-cold-chain-chiller-audit",
            code = "AUD-03",
            title = "POSM & Merchandising Compliance Check",
            shortTitle = "POSM & Merchandising",
            category = "Visibility & POSM",
            kind = "custom",
            target = "shop",
            description = "Verify chiller temperature, planogram compliance, fascia signage, and promotional hanger visibility.",
            requiresBackgroundGPS = true,
            allowMultipleEntriesPerProduct = false,
            allowPhotoCapture = true,
            status = "Active",
            updatedAt = "2025-05-01"
        )
    )

    private val defaultDistributorFields = listOf(
        FormField(
            key = "qtyInCtn",
            label = "Qty in Cartons (CTN)",
            type = "number",
            required = true,
            visible = true
        ),
        FormField(
            key = "qtyInBox",
            label = "Loose Boxes",
            type = "number",
            required = false,
            visible = true
        ),
        FormField(
            key = "dispatchDate",
            label = "Batch / Dispatch Date",
            type = "date",
            required = true,
            visible = true
        ),
        FormField(
            key = "stacking",
            label = "Stacking Compliance",
            type = "select",
            required = true,
            visible = true,
            options = listOf("Standard (On Pallet)", "Direct Floor", "Over-stacked", "Damaged Outer")
        ),
        FormField(
            key = "remarks",
            label = "Batch / Line Remarks",
            type = "text",
            required = false,
            visible = true
        )
    )

    private val defaultRetailFields = listOf(
        FormField(
            key = "productACount",
            label = "Group A Facings",
            type = "number",
            required = true,
            visible = true
        ),
        FormField(
            key = "productBCount",
            label = "Group B Facings",
            type = "number",
            required = true,
            visible = true
        ),
        FormField(
            key = "displayRating",
            label = "Shelf Display Rating",
            type = "select",
            required = true,
            visible = true,
            options = listOf("Excellent", "Good", "Average", "Poor", "No Display")
        ),
        FormField(
            key = "packedProductQty",
            label = "Packed Stock (Units)",
            type = "number",
            required = false,
            visible = true
        ),
        FormField(
            key = "regularity",
            label = "Distributor Supply Regularity",
            type = "select",
            required = true,
            visible = true,
            options = listOf("Weekly Regular", "Bi-Weekly", "Irregular", "Out of Coverage")
        ),
        FormField(
            key = "remarks",
            label = "Shop Observations",
            type = "textarea",
            required = false,
            visible = true
        )
    )

    val formConfigs: Map<String, FormConfigPayload> = mapOf(
        DISTRIBUTOR_FORM_ID to FormConfigPayload(
            formId = DISTRIBUTOR_FORM_ID,
            version = 3,
            settings = FormSettings(),
            fields = defaultDistributorFields
        ),
        RETAIL_FORM_ID to FormConfigPayload(
            formId = RETAIL_FORM_ID,
            version = 2,
            settings = FormSettings(),
            fields = defaultRetailFields
        ),
        "form-cold-chain-chiller-audit" to FormConfigPayload(
            formId = "form-cold-chain-chiller-audit",
            version = 1,
            settings = FormSettings(
                requireGps = true,
                allowedRadiusMeters = 150,
                requirePhoto = false,
                maxPhotos = 5
            ),
            fields = listOf(
                FormField(
                    key = "planogram_compliant",
                    label = "Is the primary shelf planogram compliant?",
                    type = "yesno",
                    required = true,
                    visible = true
                ),
                FormField(
                    key = "posm_condition",
                    label = "POSM & Branded Hanger Condition",
                    type = "select",
                    required = true,
                    visible = true,
                    options = listOf("Excellent — Eye Level", "Good — Visible", "Damaged / Faded", "Missing")
                ),
                FormField(
                    key = "chiller_temp_c",
                    label = "Chiller Cabinet Temperature (°C)",
                    type = "number",
                    required = true,
                    visible = true,
                    placeholder = "e.g. 4"
                ),
                FormField(
                    key = "competitor_promo",
                    label = "Active Competitor Trade Promo Observed",
                    type = "text",
                    required = false,
                    visible = true,
                    placeholder = "Optional promo details"
                ),
                FormField(
                    key = "auditor_notes",
                    label = "Detailed Merchandising Notes",
                    type = "textarea",
                    required = false,
                    visible = true,
                    placeholder = "Enter field observations..."
                )
            )
        )
    )

    fun buildBootstrap(session: Session): BootstrapPayload {
        return BootstrapPayload(
            cities = cities,
            territories = territories,
            locations = locations,
            subLocations = subLocations,
            shops = shops,
            distributors = distributors,
            groups = groups,
            products = products,
            users = listOf(session.user),
            roles = listOf(defaultRole),
            forms = forms,
            formConfigs = formConfigs,
            mapsApiKey = ""
        )
    }
}

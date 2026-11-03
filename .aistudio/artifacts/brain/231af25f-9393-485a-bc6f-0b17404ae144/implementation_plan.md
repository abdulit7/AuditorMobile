# AuditGrid — Dynamic Web-to-Mobile Form Sync, Offline Form Persistence & Incremental New-Form Insertion

Enables the **AuditGrid — Auditor** mobile APK to start with **zero hardcoded forms**, download and insert all active audit forms and field configurations from the main web server via **Sync**, persist synced forms locally on the phone for **both offline and online auditing**, and automatically insert any **newly created web forms** on subsequent syncs.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions confirmed in your responses are built into this plan:

- **Confirmed Decision 1 — Zero Forms Initially -> Sync Inserts Forms from Web Application**:
  - When no forms have been synced to the mobile APK yet, the **Audit** screen displays a clear **No Audit Forms Synced Yet** empty state with a prominent **Sync Forms from Web Server** button.
  - Tapping **Sync** pulls all active forms (`GET /api/forms`) and their field definitions (`GET /api/forms/{id}/config`) from the main web server and inserts them into the mobile app.
  - Whenever an Admin creates a **new form** on the web server, the next **Sync** detects and inserts the newly created form (along with any updated field configurations) into the mobile app and notifies the auditor (e.g., *"Synced 4 forms — 1 new form added from web server"*).
- **Confirmed Decision 2 — Persist Synced Forms on Phone for Both Offline & Online Use**:
  - Once forms, field configurations, and assigned master data (cities, territories, locations, sub-locations, shops, distributors, and SKUs) are synced from the web application, they are **saved in on-device storage** (`SharedPreferences` JSON cache).
  - If the auditor closes the app or goes **offline** in the field, all previously synced forms remain available on the phone so the auditor can open, fill, and submit them (queuing submissions in the **Offline Outbox** until internet returns).
- **Confirmed Decision 3 — Completed Audits Page (Read-Only / Record Only) & Built-in Dummy Test User Preserved**:
  - All existing features—including the strictly read-only **Completed Audits** tab, **Offline Outbox Queue**, and the **Dummy Test Auditor (`demo.auditor`)**—are preserved and enhanced with a test harness so you can also test the "0 forms -> Sync -> New Web Form Created -> Sync Inserts New Form" lifecycle in Test Mode.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **Empty-to-Synced Form Lifecycle**: The mobile app does not rely on static hardcoded forms. Before the first sync (or if local forms are cleared), the mobile app starts with 0 forms and prompts the auditor to sync with the web server.
  2. **Incremental Insertion of Newly Created Web Forms**: Every sync compares the incoming web server forms (`GET /api/forms` + `GET /api/forms/{id}/config`) against the phone's locally stored forms, inserting newly created forms and updating modified form versions while keeping everything cached on-device for offline and online use.
  3. **Interactive Web-Form Sync Simulator (in Dummy Test Mode)**: When signed in as the Dummy Test User (`demo.auditor`), provides quick controls on the **Audit** tab to test:
     - **Reset to 0 Forms (Unsynced State)** -> Tap **Sync** to watch initial forms download from the server.
     - **Simulate New Form Created on Web Server** -> Tap **Sync** to watch the newly created web form get inserted into the mobile APK.
- **Target Audience / Persona**: Field auditors and administrators who create and evolve audit forms on the web server and need those forms to appear on mobile devices via **Sync** without rebuilding the Android APK, while still working reliably offline in the field.
- **Key Value**: True zero-rebuild form extensibility from the web portal combined with 100% offline field reliability.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Initial Launch / Zero Forms on Mobile APK**:
     - If the phone has 0 synced forms, the **Audit** tab displays a clean empty-state card:
       - Icon + Title: **"No Audit Forms on Device Yet"**
       - Subtitle: **"Sync with the main web application to download your assigned audit forms and field configurations."**
       - Primary CTA Button: **"Sync Forms from Web Server"**
  2. **First Sync from Web Application**:
     - Auditor taps **Sync Forms from Web Server** (or the top-bar **Sync** button).
     - The app fetches `/api/bootstrap`, `/api/forms`, and `/api/forms/{id}/config`, saves the forms and master data to on-device storage, and renders the form cards in the **Audit** tab with version badges (`v1`, `v2`, `Custom Web Form`).
  3. **Admin Creates a New Form on Web Application -> Auditor Syncs**:
     - When a new form is created in the web application, the auditor taps **Sync** (or auto-sync runs).
     - The new form and its field configuration are downloaded, merged into the phone's saved forms list, highlighted with a **"Synced · v{version}"** badge, and announced via a confirmation toast: *"1 new form added from web server (Total: X active forms)"*.
  4. **Offline & Online Auditing**:
     - When the auditor is offline, the mobile app automatically loads the last synced forms, field configurations, and assigned places/SKUs from the phone's local cache so the auditor can continue opening and submitting any synced form without interruption.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Enterprise utilitarian clarity matching the AuditGrid palette (`#1E3A5F` Deep Navy, `#1D4ED8` Royal Blue, `#059669` Emerald Green, `#F8FAFC` Slate Background).
  - *Form Cards & Sync Status Strip*: Shows total synced forms count, last synced timestamp, offline-ready cache indicator (`Saved on phone for offline & online use`), and a `Sync Forms` button directly in the Audit Forms header.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: On-Device Persistence of Synced Forms, Configs & Master Data**
  - *Chosen Approach*: Persist `List<AuditFormDefinition>`, `Map<String, FormConfigPayload>`, and `BootstrapPayload` (cities, territories, locations, sub-locations, shops, distributors, groups, products) in `SharedPreferences` keyed by auditor/server scope.
  - *Why*: Ensures that once forms are synced from the web server, the auditor can open the app even with zero internet connectivity and immediately see and fill all synced forms.
- **Decision 2: Incremental Form Merge & New-Form Detection on Sync**
  - *Chosen Approach*: During `syncAllNow()`, compare the previous local form IDs and config versions against the newly fetched `/api/forms` and `/api/forms/{id}/config` payloads. Insert new forms, update changed form configs, persist the updated catalog on-device, and report exact counts of newly inserted/updated forms in the user toast.
  - *Why*: Gives clear visual feedback when a newly created form from the web server lands in the mobile app.
- **Decision 3: Testability in Both Live Server Mode & Dummy Test Mode**
  - *Chosen Approach*: Against a live server, `GET /api/forms` and `GET /api/forms/{id}/config` fetch directly from the web backend. In Dummy Test Mode (`demo.auditor`), include a **Web Form Sync Test Bar** allowing you to reset to `0 forms` (to test the empty state) or queue a newly created web form on the simulated server and tap **Sync** to verify that new web forms insert seamlessly.
  - *Why*: Lets you verify both the empty-state sync and new-form insertion on the mobile emulator immediately, even before connecting to your live server.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     Main Web Server (Admin Portal)                           │
│  • Admin creates/edits forms -> Exposed via GET /api/forms & /config         │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ HTTP Sync (Auto or Manual Sync Button)
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                      AuditGridViewModel & ApiClient                          │
│  • On Boot / Offline Launch:                                                 │
│      - Loads cached forms, formConfigs & master data from Local Phone Cache  │
│      - If no forms synced yet -> auditForms = emptyList() (Zero-Form State)  │
│  • On syncAllNow():                                                          │
│      1. Flushes Offline Outbox Queue                                         │
│      2. Fetches GET /api/bootstrap + GET /api/forms + GET /api/forms/{id}/cfg│
│      3. Detects newly created forms (newIds = remoteIds - previousIds)       │
│      4. Saves updated forms, configs & master data to Local Phone Cache      │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     DistributorAuditFormScreen (Audit Tab)                   │
│  • Header: Shows Synced Forms count + "Saved for Offline & Online" + Sync CTA│
│  • If auditForms.isEmpty():                                                  │
│      - "No Audit Forms Synced Yet" card + "Sync Forms from Web Server" button│
│  • If auditForms.isNotEmpty():                                               │
│      - Renders all synced Built-in & Custom Web Forms dynamically            │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `AuditGridJsonParsers`: Add `serializeFormDefinition`, `serializeFormConfig`, and `serializeBootstrap` / `parseBootstrap` helpers so all synced forms, field schemas, and master data entities serialize cleanly to and from local phone storage.
  - `AuditGridApiClient`:
    - Add `loadCachedForms()`, `saveCachedForms()`, `loadCachedFormConfigs()`, `saveCachedFormConfigs()`, `loadCachedBootstrap()`, and `saveCachedBootstrap()`.
    - In Dummy Test Mode, support starting with 0 forms when reset and queuing simulated newly created web forms (e.g. *Shelf Expiry & FIFO Compliance Audit*, *Competitor Price & Promo Survey*, *Cold Chain Chiller Audit*) that get inserted when **Sync** is tapped.
- **Interactive Component & State Mapping**:
  - **Audit Tab Header & Empty State**: Displays a **Sync Forms** button and offline-cached badge; when `auditForms` is empty, shows the zero-forms empty state with a primary **Sync Forms from Web Server** action.
  - **ViewModel Sync Handler**: Tracks `previousFormIds` before syncing, computes `newlyAddedCount = newForms.count { it.id !in previousFormIds }`, persists all forms/configs to the phone cache, and surfaces a descriptive toast when new web forms are inserted.

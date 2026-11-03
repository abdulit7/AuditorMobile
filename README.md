# AuditGrid — Auditor (Android)

Native Android (Kotlin + Jetpack Compose) field auditor application for **AuditGrid**. Connects directly to your main AuditGrid application server over Wi-Fi (`http://`) or HTTPS (`https://`) to fetch scoped territories, distributors, retail shops, daily audit plans, and dynamic form configurations, and submits verified field audits with GPS coordinates and compressed photos.

## Features

- **Native Server Connection & Sign-In**:
  - Configurable AuditGrid server URL (`http://` or `https://`) with built-in `/api/health` connection check.
  - Authenticates against `/api/auth/login` (`surface: "mobile"`, `client: "native"`) with rotating refresh token session persistence (`/api/auth/refresh`).
- **Field Audit Forms**:
  - **Distributor Product Stock Audit** (`form-distributor-product-audit`): Scoped distributor & product group selection, SKU batch entries (cartons, boxes, dispatch date, stacking, remarks), automatic carton/value calculation, device GPS distance check, photo capture/gallery upload, and local draft persistence.
  - **Retail Market Shop Audit** (`form-retail-market-shop-audit`): Hierarchical City → Territory → Location → Sub-Location shop queue, per-shop GPS check, Product A/B & packed quantities, display rating, regularity, photo capture, and local draft persistence.
  - **Custom Audit Forms** (`kind: "custom"`): Dynamic server-driven fields (`number`, `date`, `select`, `text`, `textarea`, `photo`, `yesno`), configurable GPS radius/accuracy checks, and photo attachments.
- **Daily Audit Plans (`Plans`)**:
  - Create, start, cancel, and delete daily plans (`/api/plans`), launch pre-filled audits directly from plan stops, skip stops with a reason, view the route map, and open turn-by-turn directions in Google Maps.
  - Automatic live GPS location sharing (`/api/tracking/ping`) and screen wake-lock while a plan is `In Progress`.
- **Scoped Geography & Distributors (`Places` & `Distributors`)**:
  - Drill down through assigned Cities, Territories, Locations, Sub-locations, and Shops with live GPS distance verification.
  - Browse assigned Distributors and their active dealing SKUs.
- **Account Management (`Account`)**:
  - View auditor profile, assigned scope counts, connection status, self-service password change (`/api/auth/change-password`), and sign-out.

## Building & Running

Build the debug APK with Gradle:

```bash
gradle assembleDebug
```

The compiled APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

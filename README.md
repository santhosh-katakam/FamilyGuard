# FamilyGuard

**Free Render test deployment:** [Open FamilyGuard](https://familyguard-9urq.onrender.com). See [deployed URLs, verified checks and login-file location](docs/RENDER-DEPLOYMENT.md). The free API can sleep and the test database expires on 18 November 2026.

A one-parent / one-child family safety monorepo with a React dashboard, authenticated Spring Boot API, PostgreSQL migrations, and a real native Java Android module. All dashboard data comes from the API; no activity fixtures or simulated monitoring are shipped.

**Status:** implemented baseline requiring deployment and real-device acceptance. See [verification results](docs/VERIFICATION.md) for commands actually run and outstanding checks. Do not treat code generation as production certification. Background FCM push is not configured; alerts appear in the open dashboard and persisted alert list.

## Project

- frontend/ — React + Vite + Tailwind, Router, Axios, Lucide, Recharts, Leaflet/OpenStreetMap. Twelve navigation sections, pairing, privacy controls, filters, reports, honest empty/offline states.
- backend/ — Java 21, Spring Boot, Spring Security, JWT/rotating refresh cookies, JPA, Flyway, REST APIs, parent-only WebSocket changes, retention and missing-sync alerts.
- android-app/ — directly openable Android Studio project, one APK with Admin WebView and native Child Mode. UsageStatsManager, launcher-visible inventory, notification metadata listener, cellular call-state receiver, foreground opt-in LocationManager service, Keystore-encrypted Room queue, WorkManager retries.
- database/ — review copy of the migration. backend resources are the canonical Flyway source.
- deployment/ and render.yaml — PostgreSQL local compose and Render deployment configuration.
- docs/ — [Android/APK instructions](docs/ANDROID.md), [Render deployment](docs/DEPLOYMENT.md), [security boundaries](docs/SECURITY.md), [API](docs/API.md), [phase file inventory](docs/PHASES.md).

## Local development in VS Code

Install Java 21, Maven 3.9+, Node 22.12+, and PostgreSQL 17 (or start Docker Desktop). Open familyguard/ in VS Code.

Create a local PostgreSQL database/user, or from deployment/ set DATABASE_PASSWORD in an ignored .env and run `docker compose up -d`. The compose port binds only to localhost. Export the settings from backend/.env.example in the terminal running Maven; Spring does not automatically load .env. The examples are templates and contain no valid production secrets. Choose a password with at least 14 characters and two independent base64-encoded 32-byte keys. For local HTTP use FRONTEND_ORIGIN=http://localhost:5173 and SECURE_COOKIES=false.

```powershell
cd backend
mvn test
mvn spring-boot:run
```

In a second terminal:

```powershell
cd frontend
npm ci
npm test
npm run dev
```

Open **http://localhost:5173** and use the configured Parent Admin credentials. Vite proxies /api and /ws to localhost:8080. Use the same exact origin configured for CORS/cookie refresh. Before pairing, the dashboard contains no device or monitoring data. Use /child-setup for setup guidance; all actual pairing and Android system permissions happen in the APK.

For Android/Render testing use real HTTPS URLs. Configure android-app/local.properties, build a debug APK, choose Child Mode, accept the notice and pair using a parent-generated code. See [full setup and signed release steps](docs/ANDROID.md). Location is stale after two minutes, connectivity is inferred from recent sync (45-minute offline threshold), and Android background work is best effort. Devices 500+ km apart work through the same HTTPS server when connected; no local-network proximity is required.

## Data and permissions

No hidden operation, private chat extraction, call audio, phone numbers, account interception or permission bypass. Notification records contain package/count/time only, and counts are notification events rather than messages. Usage reports are Android foreground-app aggregates, not guaranteed exact screen-on duration. Package visibility limits which apps can be observed. Location requires explicit child opt-in and a visible ongoing notification. Both child and parent controls can reduce sharing; parent controls cannot grant child OS permissions.

Offline data remains in an encrypted local queue with a seven-day/20,000-event cap, is acknowledged by UUID, and is deduplicated server-side. Retention is configurable. Pause, local removal, server revoke and account disable/delete are implemented with the scope documented in SECURITY.md.

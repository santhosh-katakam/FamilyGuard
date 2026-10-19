# Implementation phases and file inventory

This is the review log for work in the current workspace. Features span modules, so later phases also changed files introduced earlier. Final command results and limits are in [VERIFICATION.md](VERIFICATION.md); device-specific acceptance is in [ANDROID.md](ANDROID.md).

## Phase 1 — foundation, schema, authentication

Created backend/pom.xml, backend/src/main/resources/application.yml, backend/src/main/resources/db/migration/V1__foundation.sql, database/V1__foundation.sql, and Java sources under backend/src/main/java/com/familyguard/: Application.java, Model.java, Repositories.java, Tokens.java, Security.java, AuthController.java, ApiErrors.java, EncryptedText.java. Generated dedicated entities and repositories for PermissionStatus, InstalledAppEvent, AppUsageLog, NotificationEvent, CallEvent, LocationHistory and DeviceStatus. generate-model.cjs retains the schema/entity scaffold; do not rerun over edited files without review. The migration includes all requested tables plus device_status for battery/connection telemetry. sync_queue is the server deduplication receipt ledger; unsent events live on Android in Room.

Java 21/Maven were not initially on PATH. Official toolchain downloads were placed in ignored ../.tools/. Compilation first found a cascaded-validation annotation placement error, corrected before proceeding. Backend compilation succeeded before workflow tests were added. No database or hardware operation was claimed from compilation.

## Phase 2 — connected React interfaces

Created frontend/package.json, package-lock.json, index.html, vite.config.js, .env.example, src/main.jsx, src/styles.css, src/api.js, src/data.js, src/data.test.js and src/workflows.test.jsx. Parent sign-in and twelve sections use real REST APIs; the Child Setup route directs permissions/pairing to native Android. No monitoring data is seeded. Responsive CSS, search/date/event filters, charts, maps, retention controls, confirmation dialogs, network errors and honest empty states are present.

`npm run build` and `npm test` passed. The first bundle-size advisory was addressed by separating maps and charts. Browser automation could not complete its preview connection, so visual interaction QA remains unverified. The local page did return successful HTTP HTML.

## Phase 3 — pairing, authorization, dashboard

Created PairingController.java, DeviceController.java, EventService.java, DashboardController.java. Changed repositories/auth/security for atomic one-time pairing, endpoint role isolation, one-device restriction, revocation, account erasure, validated/sanitized events, dashboard summaries and privacy settings. Frontend now uses these endpoints.

Backend tests exercise live Spring/JPA/Flyway/MockMvc workflows against H2 PostgreSQL mode, including role isolation, consumed/expired codes, explicit consent, deduplication, privacy denial, dashboard data and revocation. Hardware pairing over deployed HTTPS remains a required acceptance test.

## Phase 4 — directly openable native Java Android app

Created android-app/settings.gradle, build.gradle, gradle.properties, Gradle wrapper scripts/JAR/properties, app/build.gradle, local.properties.example, keystore.properties.example, app/src/main/AndroidManifest.xml, res/xml/network_security_config.xml, res/xml/data_extraction_rules.xml, res/drawable/ic_shield.xml; native MainActivity.java, ChildActivity.java, AdminActivity.java, Ui.java and Api.java. One APK has a secure React WebView for Parent Admin and real native Child Mode screens.

Gradle wrapper generation succeeded. Final Android artifact checks are recorded in VERIFICATION.md. No connected Android device was returned by `adb devices`; installation and runtime system-permission flows cannot be certified here.

## Phase 5 — permitted native collection

Created Consent.java, Collectors.java, PackageReceiver.java, NotificationEventsService.java, CallStateReceiver.java, LocationService.java and MonitorNotice.java. Changed child setup, manifest and backend event handling. Launcher inventory and supported package broadcasts, approximate foreground usage, metadata-only notifications, delivered cellular states, battery/network, opt-in foreground GPS and accuracy-aware server geofence transitions are implemented. Supervision has a persistent visible notification, and collection requires notification visibility.

Tests validate server allowlists, encryption, data denial and geofence transitions. Android usage, notification visibility/redaction, telephony delivery, approximate location, permission revocation and OEM behavior require the real-device matrix in ANDROID.md. No call audio or chat archive exists.

## Phase 6 — offline queue, retry, real-time alerts

Created SecureStore.java, QueueDb.java, Events.java, SyncWorker.java and CollectWorker.java; backend Realtime.java and Maintenance.java. Queue payloads use Android Keystore AES-GCM; offline collection has its own periodic worker without a connectivity constraint; uploads require connectivity and delete only acknowledged UUIDs. Local queue retention is seven days/20,000 records. Duplicate receipts stay 91 days. Location history and other activity expire according to settings.

Open-parent-dashboard alerts use persisted alert records, rate limiting, one-use authenticated WebSocket tickets, reconnects and polling fallback. Missing sync is inferred after 45 minutes, not instant connectivity detection. The subsequent native parent upgrade adds a visible foreground WebSocket receiver, PostgreSQL catch-up feed and Android notification channels, with no external push provider. See [REALTIME-NOTIFICATIONS.md](REALTIME-NOTIFICATIONS.md).

## Phase 7 — hardening, verification, logging

Created backend/src/test/java/com/familyguard/WorkflowsTest.java and android-app/app/src/androidTest/java/com/familyguard/android/LocalQueueTest.java. Changed auth, security, event encryption, deletion, rate limits, 256 KiB request limit, session-version checks and API error references. Encrypted privacy/geofence settings as well as event payloads; erased identity fields on account deletion. Corrected a Spring-proxy field-access issue found by expanded tests and added regression coverage for affected dashboard/maintenance paths. Formatted Java and React sources for review.

Nine backend workflow tests passed before the final Spring Boot patch update; final patch-build results are recorded separately in VERIFICATION.md. Ten frontend tests passed (three data calculations and seven interface workflows). Android instrumented tests cover queue deduplication/acknowledgment, retention and Keystore-backed encryption; they require a device/emulator. Production npm dependency audit reported zero known vulnerabilities; this is not a complete security certification.

## Phase 8 — deployment and APK handoff

Created backend/Dockerfile, backend/.dockerignore, backend/.env.example, frontend/.env.example, render.yaml, deployment/docker-compose.yml, README.md and docs/ANDROID.md, DEPLOYMENT.md, SECURITY.md, API.md, PHASES.md, VERIFICATION.md. Changed JDBC configuration to accept Render-provided host/port/name separately; production origins and secrets remain operator-supplied. Render plan IDs were checked against the current official Blueprint reference. Android uses actual deployed HTTPS URLs configured locally before building.

No Render account, PostgreSQL service, Firebase project, production URLs, signing keystore or physical phones were supplied. Deployment, provider backups/restore, real PostgreSQL behavior, push integration, signed release and real-device acceptance remain external release work. Do not label the platform production-ready until those checks pass.

## Complete source file list

- .gitignore
- README.md
- android-app/app/build.gradle
- android-app/app/src/androidTest/java/com/familyguard/android/LocalQueueTest.java
- android-app/app/src/main/AndroidManifest.xml
- android-app/app/src/main/java/com/familyguard/android/AdminActivity.java
- android-app/app/src/main/java/com/familyguard/android/Api.java
- android-app/app/src/main/java/com/familyguard/android/CallStateReceiver.java
- android-app/app/src/main/java/com/familyguard/android/ChildActivity.java
- android-app/app/src/main/java/com/familyguard/android/CollectWorker.java
- android-app/app/src/main/java/com/familyguard/android/Collectors.java
- android-app/app/src/main/java/com/familyguard/android/Consent.java
- android-app/app/src/main/java/com/familyguard/android/Events.java
- android-app/app/src/main/java/com/familyguard/android/LocationService.java
- android-app/app/src/main/java/com/familyguard/android/MainActivity.java
- android-app/app/src/main/java/com/familyguard/android/MonitorNotice.java
- android-app/app/src/main/java/com/familyguard/android/NotificationEventsService.java
- android-app/app/src/main/java/com/familyguard/android/PackageReceiver.java
- android-app/app/src/main/java/com/familyguard/android/QueueDb.java
- android-app/app/src/main/java/com/familyguard/android/SecureStore.java
- android-app/app/src/main/java/com/familyguard/android/SyncWorker.java
- android-app/app/src/main/java/com/familyguard/android/Ui.java
- android-app/app/src/main/res/drawable/ic_shield.xml
- android-app/app/src/main/res/xml/data_extraction_rules.xml
- android-app/app/src/main/res/xml/network_security_config.xml
- android-app/build.gradle
- android-app/gradle.properties
- android-app/gradle/wrapper/gradle-wrapper.jar
- android-app/gradle/wrapper/gradle-wrapper.properties
- android-app/gradlew
- android-app/gradlew.bat
- android-app/keystore.properties.example
- android-app/local.properties.example
- android-app/settings.gradle
- backend/.dockerignore
- backend/.env.example
- backend/Dockerfile
- backend/pom.xml
- backend/src/main/java/com/familyguard/ApiErrors.java
- backend/src/main/java/com/familyguard/AppUsageLog.java
- backend/src/main/java/com/familyguard/AppUsageLogRepository.java
- backend/src/main/java/com/familyguard/Application.java
- backend/src/main/java/com/familyguard/AuthController.java
- backend/src/main/java/com/familyguard/CallEvent.java
- backend/src/main/java/com/familyguard/CallEventRepository.java
- backend/src/main/java/com/familyguard/DashboardController.java
- backend/src/main/java/com/familyguard/DeviceController.java
- backend/src/main/java/com/familyguard/DeviceStatus.java
- backend/src/main/java/com/familyguard/DeviceStatusRepository.java
- backend/src/main/java/com/familyguard/EncryptedText.java
- backend/src/main/java/com/familyguard/EventService.java
- backend/src/main/java/com/familyguard/InstalledAppEvent.java
- backend/src/main/java/com/familyguard/InstalledAppEventRepository.java
- backend/src/main/java/com/familyguard/LocationHistory.java
- backend/src/main/java/com/familyguard/LocationHistoryRepository.java
- backend/src/main/java/com/familyguard/Maintenance.java
- backend/src/main/java/com/familyguard/Model.java
- backend/src/main/java/com/familyguard/NotificationEvent.java
- backend/src/main/java/com/familyguard/NotificationEventRepository.java
- backend/src/main/java/com/familyguard/PairingController.java
- backend/src/main/java/com/familyguard/PermissionStatus.java
- backend/src/main/java/com/familyguard/PermissionStatusRepository.java
- backend/src/main/java/com/familyguard/Realtime.java
- backend/src/main/java/com/familyguard/Repositories.java
- backend/src/main/java/com/familyguard/Security.java
- backend/src/main/java/com/familyguard/Tokens.java
- backend/src/main/resources/application.yml
- backend/src/main/resources/db/migration/V1__foundation.sql
- backend/src/test/java/com/familyguard/WorkflowsTest.java
- database/V1__foundation.sql
- deployment/docker-compose.yml
- docs/ANDROID.md
- docs/API.md
- docs/DEPLOYMENT.md
- docs/PHASES.md
- docs/SECURITY.md
- docs/VERIFICATION.md
- frontend/.env.example
- frontend/index.html
- frontend/package-lock.json
- frontend/package.json
- frontend/src/api.js
- frontend/src/data.js
- frontend/src/data.test.js
- frontend/src/main.jsx
- frontend/src/styles.css
- frontend/src/workflows.test.jsx
- frontend/vite.config.js
- generate-model.cjs
- render.yaml

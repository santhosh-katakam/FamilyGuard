# Verification — 19 October 2026

**Deployment follow-up:** the project was subsequently deployed to Render. Real PostgreSQL migration, deployed HTTPS login/refresh/logout, database health and the authenticated WebSocket handshake passed. The Android APK was rebuilt with actual deployed URLs. See [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md) for the current environment. The original workspace-only results and remaining hardware checks below are retained as history.

This file records checks actually run in this workspace. Source creation is not deployment or runtime acceptance.

**Native parent receiver upgrade:** 14 backend tests now pass, including an actual embedded-server WebSocket connection, one-use ticket replay rejection, commit-triggered refresh delivery, web/native credential independence and receiver revocation. React has 20 passing tests. Parent/child Android unit tests pass (three each), both role test APKs compile, all three role APKs build and their v2 debug signatures verify. Lint reports zero errors (parent 26 warnings, child 24, universal 24). Built manifests confirm the parent has no child location/usage/phone-state permissions, and neither role requests SMS, microphone, call-log or unrestricted package inventory. Real local PostgreSQL successfully applied V2. Physical-device notification delivery remains unverified: ADB still shows no phones, and the user intends to connect them. See [REALTIME-NOTIFICATIONS.md](REALTIME-NOTIFICATIONS.md) and [PHYSICAL-DEVICE-CHECKLIST.md](PHYSICAL-DEVICE-CHECKLIST.md).

**Local testing follow-up:** a separate PostgreSQL 18.4 cluster, Spring API on 8082 and Vite on 5173 now run locally. All **11 frontend tests** and the production build passed after fixing Child Setup navigation. Real local login, dashboard authorization, refresh rotation/replay rejection, authenticated WebSocket proxying and logout passed. Start/stop/restart preserved the local account and database. See [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md) for commands and login-file location. Browser visual QA and native-device tests remain outstanding.

**Child browser interface follow-up:** `/child` now provides overview, pairing-form validation, browser-only sharing choices and monitoring-control confirmations. The old setup route redirects there. All **18 frontend tests** and the production build passed; tests cover storage persistence/denial, form validation, navigation, reset and browser connectivity. The portal does not claim Android permissions, consume pairing codes or send device commands. Visual browser testing remains unverified.

## Passed

- Backend Java 21 compilation, Flyway migration and JPA schema validation against H2 in PostgreSQL compatibility mode. Final `mvn -B -ntp verify` on Spring Boot **3.5.16** passed **9 integration tests**, packaged and repackaged `backend/target/familyguard-api-1.0.0.jar`. Initial compile/SQL compatibility/proxy-access problems were corrected; the final results are passing.
- Backend workflows: bad credentials and endpoint role isolation; one-time pairing/single-device enforcement; expiry and required consent; UUID deduplication; removal of extra notification text; stored ciphertext verification; pairing revocation; refresh rotation and Origin enforcement; AES-GCM tamper rejection; privacy-denied acknowledgment without storage; invalid location rejection/rollback; identity erasure and account disablement; real dashboard values; missing-sync/offline and recovery; accuracy-aware geofence transition alerts.
- Frontend `npm test`: **10 tests across two files**, all passed. Three data calculation tests and seven interface tests cover absent telemetry, unavailable/outdated location, login network failure, pairing response, notification summaries and privacy-setting submission. Test fixtures exist only in test files; runtime sources contain no fake device activity.
- Frontend `npm run build`: Vite production build passed. Chart/map chunks split from the application. A local dev server returned HTML at http://127.0.0.1:5173 during verification.
- Frontend `npm audit --omit=dev --audit-level=high`: zero known production dependency vulnerabilities reported at the time of the check. This does not audit the entire platform.
- Android Gradle `assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL** after correcting Android 16 gesture-back handling and the local Windows property path. Both the native app APK and instrumented-test APK were generated. Lint has zero errors and 19 warnings; remaining warnings are documented below.
- Android `apksigner verify --verbose`: debug APK verified with the APK Signature Scheme v2 development signature. This is not a production release key.
- Android APK permission inspection: no microphone, camera, contacts, SMS, call-log or QUERY_ALL_PACKAGES permission. WorkManager adds WAKE_LOCK / RECEIVE_BOOT_COMPLETED for deferred scheduling; it does not restart foreground location sharing.
- `adb devices`: **no device attached**. This is the explicit reason runtime instrumentation/hardware tests were not run.

Tooling used: local Temurin JDK 21, Maven 3.9.11, Gradle 8.13, Android Gradle Plugin 8.13.0, Android SDK 36 and Build Tools 35.0.0, Node 26.4.0 and npm 11.17.0. Tool downloads are in ignored ../.tools/ and are not part of the project sources. Android Studio may use its compatible bundled JDK; backend requires Java 21.

## Artifacts

- `backend/target/familyguard-api-1.0.0.jar`
- `frontend/dist/`
- `android-app/app/build/outputs/apk/debug/app-debug.apk`
- `android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `android-app/app/build/reports/lint-results-debug.html`

**The current APK contains the actual Render API and website origins.** The wrapper mismatch was repaired, `gradlew.bat assembleDebug` and lint/instrumented-test compilation passed, and the APK's debug signature and embedded endpoints were verified. API health timed out during this latest build; device-level pairing and monitoring remain unverified. See [APK-BUILD.md](APK-BUILD.md) for the current artifact and configuration.

Debug APK SHA-256: `e56d49fc3a0ed477cc76a72f6de0762b8af4822bcfe39d517083b593639a418a`.

## Remaining release acceptance

- Android instrumented tests are **compiled but not executed**. Connect a real device or emulator and run `gradlew.bat connectedDebugAndroidTest`. Follow the device matrix in [ANDROID.md](ANDROID.md), especially permission denial/revocation, selected-app notifications/redaction, usage aggregation, cellular call-state delivery, GPS/approximate location, Doze, OEM restrictions and offline retry/replay.
- Real PostgreSQL startup/migration/integration, database backup/restore, provider-network isolation and Render health/deploy behavior are **not verified**. Docker's daemon was unavailable in this environment. H2 compatibility tests are useful coverage, not proof of PostgreSQL operation.
- Render hosting, DNS/custom domains, TLS production ingress, actual CORS/cookie origins, service availability and end-to-end long-distance pairing need your deployment/account configuration. No deployment was performed.
- No production signing keystore was supplied. Signed release APK generation/verification and install/upgrade on both physical phones remain operator tasks; full instructions are in ANDROID.md.
- Third-party push services are explicitly excluded. Native parent notifications now use the user-started visible WebSocket receiver and a PostgreSQL catch-up feed. Immediate wakeup while killed, force-stopped or network-suspended cannot be guaranteed; physical acceptance is pending.
- Full visual/browser interaction QA is **not verified**: the browser-control preview connection timed out. The successful jsdom interface tests and HTTP/build checks are not a substitute for mobile/desktop/WebView visual review.
- Remaining Android lint warnings concern synchronous preference commits (retained for consent/revocation durability and worker sequencing), intentionally enabled WebView JavaScript for trusted React, localization of the device-name string, and newer toolchain availability. Gradle also reports deprecated features for a future Gradle 9 migration. They do not fail the current build, but review them before distribution. The app targets API 36 and uses AndroidX back handling.

The project is a working implementation baseline with verified builds and automated tests. It is **not yet a production-certified monitoring deployment**. Do not claim live/offline behavior or push delivery beyond the implemented and validated boundaries.

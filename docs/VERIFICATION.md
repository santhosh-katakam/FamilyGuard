# Verification — 19 October 2026

**Deployment follow-up:** the project was subsequently deployed to Render. Real PostgreSQL migration, deployed HTTPS login/refresh/logout, database health and the authenticated WebSocket handshake passed. The Android APK was rebuilt with actual deployed URLs. See [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md) for the current environment. The original workspace-only results and remaining hardware checks below are retained as history.

This file records checks actually run in this workspace. Source creation is not deployment or runtime acceptance.

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

**The APK currently contains the explicit placeholder Render URLs from the template.** It can be used to inspect native setup, but it cannot pair with a real backend until local.properties is configured with your actual HTTPS API/web origins and the APK is rebuilt. No functional deployment URL is invented.

Debug APK SHA-256: `ec11686021321880001ed1271f1f8d1455ea78ab5049ae8adefa6d936a678dbd`.

## Remaining release acceptance

- Android instrumented tests are **compiled but not executed**. Connect a real device or emulator and run `gradlew.bat connectedDebugAndroidTest`. Follow the device matrix in [ANDROID.md](ANDROID.md), especially permission denial/revocation, selected-app notifications/redaction, usage aggregation, cellular call-state delivery, GPS/approximate location, Doze, OEM restrictions and offline retry/replay.
- Real PostgreSQL startup/migration/integration, database backup/restore, provider-network isolation and Render health/deploy behavior are **not verified**. Docker's daemon was unavailable in this environment. H2 compatibility tests are useful coverage, not proof of PostgreSQL operation.
- Render hosting, DNS/custom domains, TLS production ingress, actual CORS/cookie origins, service availability and end-to-end long-distance pairing need your deployment/account configuration. No deployment was performed.
- No production signing keystore was supplied. Signed release APK generation/verification and install/upgrade on both physical phones remain operator tasks; full instructions are in ANDROID.md.
- No Firebase project or background FCM integration is shipped/configured. Persisted alerts, open-dashboard authenticated WebSockets and polling are implemented. Closed-parent-app/background push is a remaining integration.
- Full visual/browser interaction QA is **not verified**: the browser-control preview connection timed out. The successful jsdom interface tests and HTTP/build checks are not a substitute for mobile/desktop/WebView visual review.
- Remaining Android lint warnings concern synchronous preference commits (retained for consent/revocation durability and worker sequencing), intentionally enabled WebView JavaScript for trusted React, localization of the device-name string, and newer toolchain availability. Gradle also reports deprecated features for a future Gradle 9 migration. They do not fail the current build, but review them before distribution. The app targets API 36 and uses AndroidX back handling.

The project is a working implementation baseline with verified builds and automated tests. It is **not yet a production-certified monitoring deployment**. Do not claim live/offline behavior or push delivery beyond the implemented and validated boundaries.

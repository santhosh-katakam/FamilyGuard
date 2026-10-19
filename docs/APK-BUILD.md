# Verified role APKs - 19 October 2026

The existing native Java application now builds Parent, Child and universal role variants. No Firebase or external push provider is included. All variants use the configured Render HTTPS origins through BuildConfig.

## Verified artifacts

### Parent

```text
C:\Users\DELL\Downloads\parental\familyguard\android-app\app\build\outputs\apk\parent\debug\app-parent-debug.apk
```

Size: 3,825,898 bytes. SHA-256: `925d145961f28563c8c748e420b2153c1a6efe2fc1e25252159b551d9149b98b`.

### Child

```text
C:\Users\DELL\Downloads\parental\familyguard\android-app\app\build\outputs\apk\child\debug\app-child-debug.apk
```

Size: 3,826,289 bytes. SHA-256: `a821157928ba8346606f5dfc988d56651aa11a2d345aa1e38f86e1123d0b9749`.

### Universal

```text
C:\Users\DELL\Downloads\parental\familyguard\android-app\app\build\outputs\apk\universal\debug\app-universal-debug.apk
```

Size: 3,767,675 bytes. SHA-256: `6a5dc8112ec467b8a07b9eeab51affb6de1a4b578f4fe467d5532b5b6a2d5f74`.

Version: **1.1.0 (2)**. Minimum Android API 26, compile/target API 36. The two-mode universal app retains `com.familyguard.android`; role APK IDs are `com.familyguard.android.parent` and `com.familyguard.android.child`.

All three APKs passed APK Signature Scheme v2 debug-certificate verification. They are installable development builds; production release signing requires your private keystore. Built manifests were checked with aapt: parent has no child location/phone-state/usage access or notification-listener component; child has no parent receiver. No role requests SMS, microphone, call-log or QUERY_ALL_PACKAGES permission.

## Commands actually run

Using Temurin JDK 21.0.12.1+1, Gradle 8.13, AGP 8.13.0, SDK 36 and Build Tools 35.0.0, from android-app/:

```powershell
$env:JAVA_HOME = 'C:\Users\DELL\Downloads\parental\.tools\jdk\jdk-21.0.12.1+1'
$env:ANDROID_HOME = 'C:\Users\DELL\Downloads\parental\.tools\android-sdk'
.\gradlew.bat assembleParentDebug assembleChildDebug assembleUniversalDebug
.\gradlew.bat testParentDebugUnitTest testChildDebugUnitTest
.\gradlew.bat assembleParentDebugAndroidTest assembleChildDebugAndroidTest
.\gradlew.bat lintParentDebug lintChildDebug lintUniversalDebug
```

Builds passed. Three quiet-hour unit tests passed for each role. Both instrumented-test APKs compiled; they have not been executed on phones. Lint: **zero errors**, parent 26 warnings, child 24, universal 24. Warnings include deliberate synchronous consent writes, trusted WebView JavaScript, localization and API recommendations; no blanket lint baseline was added. Narrow foreground-service lint suppressions address shared services removed from another role and are backed by direct built-manifest checks and a new instrumented role-manifest test. SDK XML/deprecation notices are nonfatal.

Backend tests: **14 passed**, including a real embedded-server WebSocket handshake/delivery/revocation test. Frontend: **20 passed**, and production build passed. The local PostgreSQL database applied migration V2 and API health returned UP.

## Physical acceptance and backend dependencies

**No physical Android device was attached at the last ADB check.** The user intends to connect two phones. Installation, permission prompts, background networking and actual system notification delivery remain unverified; do not interpret compilation as physical success. Follow [PHYSICAL-DEVICE-CHECKLIST.md](PHYSICAL-DEVICE-CHECKLIST.md).

Both role APKs embed the API `https://familyguard-api-osj1.onrender.com` and parent website `https://familyguard-9urq.onrender.com`. The updated backend must be deployed for native receiver registration, authenticated socket tickets and catch-up APIs. Parent login/pairing/synchronization/dashboard history/alerts/revocation require backend availability. Native setup screens open without it. After pairing, permitted child events can queue encrypted offline and upload when connectivity returns.

Native parent notifications work only when the visible receiver is running and Android allows network access. Force-stop, reboot, Doze/OEM restrictions, network loss and Render sleep can interrupt delivery. Reconnect catches up retained events; this is not guaranteed OS wakeup push. Full configuration and security behavior: [REALTIME-NOTIFICATIONS.md](REALTIME-NOTIFICATIONS.md).

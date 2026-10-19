# Deployed free testing environment

Deployed on 19 October 2026 from [santhosh-katakam/FamilyGuard](https://github.com/santhosh-katakam/FamilyGuard), branch main.

- Parent website: https://familyguard-9urq.onrender.com
- API: https://familyguard-api-osj1.onrender.com
- Health: https://familyguard-api-osj1.onrender.com/actuator/health
- [Frontend settings](https://dashboard.render.com/static/srv-dbasfm4s728c739go8fg)
- [Backend settings](https://dashboard.render.com/web/srv-dbasfns9v7es739pi35g)
- [PostgreSQL settings](https://dashboard.render.com/d/dpg-dbasfjrbc2fs73ckb3u0-a)

The backend and PostgreSQL use free plans in Singapore. The frontend is a static site. Database external access is denied; the backend uses the internal database host with TLS. PostgreSQL 17 successfully ran the Flyway migration and JPA schema validation. Both Render service deployments reached live.

The services were created directly through Render's API using the repository configuration. They are not attached to a Blueprint sync. Do not create a second Blueprint deployment over the same resources without deliberately importing their configuration. Git auto-deploy is enabled, with rootDir backend and frontend respectively; changes outside a service's root do not rebuild it. The checked-in render.yaml is validated and can be used as a template for another environment.

## Verified against the deployed services

- Render ran all nine backend tests successfully while building the Docker image.
- Backend health returned UP with a real PostgreSQL connection.
- Parent login succeeded over HTTPS using the privately generated credentials.
- Parent dashboard returned an unpaired device state; no simulated monitoring data was added.
- Exact-origin credentialed CORS, refresh-cookie rotation and logout succeeded through real HTTP requests.
- An authenticated WebSocket handshake succeeded with a one-use ticket; consuming that ticket again was rejected.
- Live frontend returned HTTP 200, and its deployed JavaScript contains the actual API origin with no setup placeholder.
- The Android debug APK was rebuilt with both deployed URLs and its debug signature verified.

These are HTTP/API and artifact checks. Browser visual testing, Android permissions, physical-device pairing, background collection and offline synchronization still need real-device acceptance. The later native alert upgrade uses the existing stack without a push provider; see [REALTIME-NOTIFICATIONS.md](REALTIME-NOTIFICATIONS.md). Production signing remains an operator step.

## Parent account and private files

The Parent Admin email was configured from the user's supplied address. The generated password is stored locally in **deployment/.private/parent-login.txt**. This file and all files under deployment/.private/ are ignored by Git and were not pushed. Do not put the password in a GitHub issue, commit, screenshot or public chat.

deployment/.private/backend.env contains the exact runtime configuration, encryption key and database credentials; frontend.env contains the frontend environment values. Keep an encrypted backup of backend.env. Never regenerate DATA_ENCRYPTION_KEY against an existing database. Setting a different ADMIN_PASSWORD in Render does not reset the existing database user's password: provisioning uses it only on first creation.

Android local.properties and its example contain the real API/web origins. The compiled debug APK is at android-app/app/build/outputs/apk/debug/app-debug.apk. Install the same APK on both phones, choose Parent Admin / Child Mode respectively, generate a five-minute code in Device Management and pair from native Child Mode after accepting the notice. See ANDROID.md for permission testing and signed release instructions.

## Free-tier lifetime

The API can sleep after inactivity. Cold starts may take several minutes on this Java free instance; use the health URL to wake it and retry login after it reports UP. Scheduled jobs, offline alerts and WebSockets do not operate continuously while the backend is asleep.

The database's reported expiration is **18 November 2026 at 1:03 PM IST** (2026-11-18T07:33:35Z). Export or upgrade before expiration if you need to retain data. Free Postgres has no managed backup guarantee. The workspace also has other free services; free instance-hour quotas are shared. No paid plans were selected and no existing services were changed.

Sources: [Render free limits](https://render.com/docs/free), [root-directory deployment behavior](https://render.com/docs/monorepo-support).

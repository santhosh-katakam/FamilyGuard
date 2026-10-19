# Local testing on Windows

The browser URL for Parent Admin is **http://localhost:5173/**. The child interface test is **http://localhost:5173/child**; the old `/child-setup` link redirects there. Overview, pairing-form validation, sharing choices, selected notification applications and pause/reset confirmations are accessible without installing an APK. The return link navigates back to parent sign-in without a page reload.

The child browser portal is explicitly an interface test. Choices can be saved in this browser, but no pairing code is consumed, no Android permission is granted and no phone command or device activity is sent to the backend. Actual pairing and collection remain native Android functionality.

## Start and stop

From `familyguard/` in PowerShell:

```powershell
.\deployment\start-local.ps1
```

The script requires Node, installed frontend dependencies, Java 21 and a built backend JAR, plus PostgreSQL command-line tools. It discovers tools already installed in this workspace, including the ignored `../.tools/jdk/` directory. For a fresh checkout, first run `npm ci` in frontend and `mvn verify` in backend. PostgreSQL tools can be supplied with `-PostgresBin 'C:\Program Files\PostgreSQL\18\bin'`.

On first startup, `-AdminEmail 'your-address@example.com'` selects the local parent email. A strong password and independent local encryption keys are generated automatically. Later starts preserve that account and database.

Open `deployment/.private/local/parent-login.txt` for the **local** email and password. Render credentials belong to the separate deployed environment. Private files, database files, logs and frontend/.env.local are excluded from Git.

Current workspace ports:

- Frontend: http://localhost:5173/
- API health: http://127.0.0.1:8082/actuator/health
- Isolated PostgreSQL: 127.0.0.1:5433

Ports are selected on first startup and stored in the private configuration. Apache on 8080, Oracle on 8081 and the existing PostgreSQL service on 5432 remain running. The local services listen on loopback. Vite forwards `/api` and `/ws` to the local API; browser requests do not go to Render.

To stop only these FamilyGuard services while keeping the database and password:

```powershell
.\deployment\stop-local.ps1
```

Run the start command again to resume. After backend source changes, stop the services, rebuild the JAR with `mvn verify`, and restart. Frontend edits update through Vite. Logs and recorded process IDs are in `deployment/.private/local/`.

## What to test

1. Open the root URL and sign in using the local login file.
2. Open Child Setup and use **Return to parent sign in**; it should return immediately.
3. Sign in, browse the dashboard, and open Device Management. The local database has no paired device, so empty activity and location states are expected.
4. Generate a one-time pairing code when ready to connect an Android device. A browser cannot collect Android device activity.
5. Refresh the page to verify session restoration, then sign out.

The existing APK points to the deployed Render services. It will not send activity to this local database. Native local integration requires a reachable **HTTPS** development endpoint for this API and rebuilding the APK with that endpoint, following [ANDROID.md](ANDROID.md). A phone's `localhost` refers to the phone itself. Android permissions, collection, offline retries and location still need real-device tests.

## Verified in this workspace

- Frontend: all **18 tests passed**, including child portal navigation, pairing-form validation, browser-choice persistence/reset, pause confirmation, browser connectivity and blocked storage, plus both directions of parent/child navigation. `npm run build` passed. The test command disables Node's experimental Web Storage so jsdom supplies the browser storage used by interface tests.
- Real local PostgreSQL 18.4: Flyway migration and JPA validation completed; API health returned `UP`. Flyway logs a warning because this version officially lists PostgreSQL support through 17; the migration did run successfully here. Render uses its own database independently.
- Real local HTTP checks through Vite: protected-route rejection, parent login, unpaired dashboard, refresh rotation and replay rejection, logout and access-token invalidation passed.
- Authenticated WebSocket handshake through the local Vite proxy passed.
- Both PowerShell scripts parsed successfully. Stop/start completed successfully, preserved the database and credentials, and waited for healthy API/frontend responses before reporting ready.
- Browser-control automation timed out, so visual interaction in Chrome was not verified. The route regression was verified with React interface tests, and both browser routes returned HTML over local HTTP.

No test child was paired and no simulated monitoring activity was inserted.

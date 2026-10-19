# HTTP contract

All application routes use /api. JSON requests/responses use ISO-8601 UTC instants and UUID event IDs. Parent access tokens are Bearer JWTs; device Bearer tokens are distinct opaque credentials and cannot access parent endpoints. Each invalid credential returns 401; disallowed role requests fail authorization. Empty and denied categories are not evidence of no activity.

## Public authentication and pairing

- POST /auth/login `{email,password}` → `{accessToken,expiresIn:900}` plus fg_refresh HttpOnly cookie. Parent provisioned from environment, no public signup.
- POST /auth/refresh → rotated cookie + access token. Requires cookie and exact allowed Origin.
- POST /auth/logout → revoke refresh/session version if matching cookie. Requires exact allowed Origin.
- POST /pairing/complete `{code,deviceName,consent:true}` → `{deviceToken,deviceId,deviceName}`. Code expires after five minutes and is consumed once; refuses a second child.

## Parent-only

- POST /pairing/code → `{code,expiresAt}`. Cannot run while paired. New generation invalidates earlier code.
- DELETE /pairing → remove credential, pairings, device and event data. Child clears local queue when it discovers revocation on sync.
- DELETE /account → same data deletion, reset privacy settings, remove parent identity fields, disable account tombstone and sessions. Operator reset required for future provisioning.
- GET /dashboard?dayStart=ISO_INSTANT → device summary, permissions at last report, visible-app count, daily usage snapshots, alertsToday, last location, recent events. Unpaired returns `{device:null}`. Device status is recently_seen/offline, inferred from synchronization.
- GET /events/{category}?from=ISO_INSTANT&to=ISO_INSTANT&search=PACKAGE_SUBSTRING → newest retained events (up to 10,000 per category), inclusive timestamp filters. Categories: permissions/apps/usage/notifications/calls/location/status. Rows `{id,category,kind,occurredAt,packageName,data}`. category in responses is the entity label, not the route name.
- GET /alerts → latest 200 alert records; POST /alerts/{uuid}/read marks a record read.
- GET /preferences and PUT /preferences. Boolean fields apps/usage/notifications/calls/location/alertNotifications; integer retentionDays/locationHistoryDays each 1–90; optional geofence `{latitude,longitude,radius}` (meters, 100–100000). Parent switches deny server storage immediately, child sees them after next sync. Geofence transition state is server-managed.
- POST /realtime/ticket → one-use ticket valid 30 seconds. Connect wss://API/ws?ticket=... with an allowed Origin. Messages `{type:"refresh"}` invalidate cached API data. Session version checked on delivery; use API credentials for subsequent reads. Fallback poll 60 seconds.

## Child-only

- GET /device/preferences → sharing/retention preferences.
- POST /device/sync `{events:[...]}` (max 200 per batch) → `{acknowledged:[UUID...],serverTime,preferences}`. Even rejected-by-privacy and duplicate events are acknowledged so they can leave the local queue. Transaction failure acknowledges nothing. Device lastSeen is server receipt time; events keep device time. Requests with invalid data get 400; clear/review poisoned queued records in Child Mode. 429/5xx/timeouts use retry/backoff; 401/403 clears local pairing.

Every event has `id,category,kind,occurredAt,data` and optional packageName (required for apps/usage/notifications). Allowed payloads:

- status / STATUS: battery 0–100, monitoring boolean, connected boolean.
- permissions / PERMISSION: permission (usage/notifications/location/calls/apps/monitoring), enabled boolean.
- apps / INSTALLED,REMOVED,UPDATED,SNAPSHOT: label (at most 100 characters), packageName.
- usage / DAILY_USAGE: day YYYY-MM-DD (child device's local day), durationMs 0–86400000, packageName. Use latest snapshot per package/day, never sum repeated cumulative snapshots. Android's buckets may expand the queried interval; usage is approximate.
- notifications / NOTIFICATION: count forced to 1, packageName. All text/title/extras fields discarded.
- calls / CALL_STATE: state RINGING/OFFHOOK/IDLE. Counts are states, not completed-call totals.
- location / LOCATION: latitude −90..90, longitude −180..180, accuracy 0..100000 meters. No provider-supplied historical points older than two minutes are collected by the native service.

Device timestamps up to five minutes in the future are accepted to tolerate clock drift; older than retained activity is acknowledged and dropped. Device receipts remain 91 days. Configure automatic date/time on both phones. UUID deduplication is global for this one-child installation. No endpoint accepts private message archives or audio.

GET /actuator/health is the only exposed operational route and includes DB health without details. API errors return `{message}` for validation/status failures; security/ingress errors may be generic HTTP errors, and clients handle them by status.

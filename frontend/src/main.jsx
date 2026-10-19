import React, { useState, useEffect, useCallback } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Routes,
  Route,
  NavLink,
  useLocation,
  Navigate,
} from "react-router-dom";
import {
  ShieldCheck,
  LayoutDashboard,
  MapPin,
  AppWindow,
  ChartNoAxesCombined,
  Bell,
  Phone,
  TriangleAlert,
  History,
  FileChartColumn,
  Smartphone,
  LockKeyhole,
  Settings,
  ArrowUpRight,
  RefreshCw,
  Search,
  Menu,
  X,
  LogOut,
  Plus,
  ChevronRight,
  Battery,
  Wifi,
  Clock,
  Check,
  Download,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { MapContainer, TileLayer, Circle, Popup } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { api, baseURL, setToken, message } from "./api";
import { usageRows, localDay, minutes, installed, stale } from "./data";
import "./styles.css";
const nav = [
  ["", "Dashboard", LayoutDashboard],
  ["location", "Live Location", MapPin],
  ["apps", "Installed Applications", AppWindow],
  ["usage", "App Usage", ChartNoAxesCombined],
  ["notifications", "Notification Activity", Bell],
  ["calls", "Call Activity", Phone],
  ["alerts", "Alerts", TriangleAlert],
  ["timeline", "Activity Timeline", History],
  ["reports", "Reports", FileChartColumn],
  ["device", "Device Management", Smartphone],
  ["privacy", "Privacy & Permissions", LockKeyhole],
  ["settings", "Settings", Settings],
];
const date = (value) =>
  value ? new Date(value).toLocaleString() : "Not available";
function Empty({
  title = "Nothing here yet",
  detail = "Approved activity will appear after the child device synchronizes.",
  action,
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <ShieldCheck size={30} />
      </div>
      <h3>{title}</h3>
      <p>{detail}</p>
      {action}
    </div>
  );
}
function ErrorBox({ error }) {
  return (
    error && (
      <div className="error" role="alert">
        {error}
      </div>
    )
  );
}
function Brand() {
  return (
    <div className="brand">
      <span>
        <ShieldCheck size={25} />
      </span>
      FamilyGuard<i>●</i>
    </div>
  );
}
function Login({ onLogin }) {
  const [email, emailSet] = useState(""),
    [password, passwordSet] = useState(""),
    [busy, busySet] = useState(false),
    [error, errorSet] = useState("");
  return (
    <main className="login">
      <div className="login-story">
        <Brand />
        <div className="eyebrow">A LITTLE CLOSER. WHEREVER YOU ARE.</div>
        <h1>
          Peace of mind,
          <br />
          <em>built on trust.</em>
        </h1>
        <p>
          One family. One connected device. A clearer picture of your child's
          digital world, with privacy at its heart.
        </p>
        <div className="trust">
          <ShieldCheck /> Transparent supervision <span>·</span> Secure by
          design
        </div>
      </div>
      <form
        className="login-form"
        onSubmit={async (e) => {
          e.preventDefault();
          busySet(true);
          errorSet("");
          try {
            const r = await api.post("/api/auth/login", { email, password });
            setToken(r.data.accessToken);
            onLogin();
          } catch (e) {
            errorSet(message(e));
          } finally {
            busySet(false);
          }
        }}
      >
        <span className="eyebrow">PARENT SPACE</span>
        <h2>Welcome back</h2>
        <p>Sign in to your family's private dashboard.</p>
        <label>
          Email address
          <input
            type="email"
            required
            autoComplete="username"
            value={email}
            onChange={(e) => emailSet(e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            required
            maxLength={200}
            autoComplete="current-password"
            value={password}
            onChange={(e) => passwordSet(e.target.value)}
          />
        </label>
        <ErrorBox error={error} />
        <button disabled={busy}>
          {busy ? "Connecting securely…" : "Sign in securely"}
          <ArrowUpRight size={18} />
        </button>
        <div className="form-note">
          <LockKeyhole size={15} /> Only your configured Parent Admin can sign
          in.
        </div>
        <NavLink to="/child-setup">
          Setting up a child device? <ChevronRight size={16} />
        </NavLink>
      </form>
    </main>
  );
}
function ChildSetup() {
  return (
    <main className="setup">
      <Brand />
      <section className="panel">
        <span className="eyebrow">CHILD DEVICE SETUP</span>
        <h1>
          Your device.
          <br />
          Your permissions.
        </h1>
        <p>
          FamilyGuard shares only the activity you enable. The child app always
          shows its supervision status and lets you pause collection.
        </p>
        {[
          "Install the FamilyGuard APK on the Android phone.",
          "Choose Child Mode and read the supervision notice.",
          "Enter the 12-character code from the parent’s Device Management page.",
          "Choose permissions in Android Settings, then start monitoring.",
        ].map((s, i) => (
          <div className="step" key={s}>
            <b>{i + 1}</b>
            <p>{s}</p>
          </div>
        ))}
        <div className="notice">
          This browser cannot monitor Android activity. Pairing and system
          permissions are handled in the native Android app. Notification access
          shares event counts, never private chat histories.
        </div>
        <NavLink to="/">
          Return to parent sign in <ArrowUpRight size={16} />
        </NavLink>
      </section>
    </main>
  );
}
function App() {
  const [authenticated, authSet] = useState(false),
    [checking, checkingSet] = useState(true);
  useEffect(() => {
    api
      .post("/api/auth/refresh")
      .then((r) => {
        setToken(r.data.accessToken);
        authSet(true);
      })
      .catch(() => {})
      .finally(() => checkingSet(false));
    const expired = () => {
      setToken(null);
      authSet(false);
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, []);
  if (location.pathname === "/child-setup") return <ChildSetup />;
  if (checking)
    return (
      <div className="boot">
        <ShieldCheck /> Opening your family space…
      </div>
    );
  if (!authenticated) return <Login onLogin={() => authSet(true)} />;
  return (
    <Shell
      onLogout={async () => {
        try {
          await api.post("/api/auth/logout");
        } finally {
          setToken(null);
          authSet(false);
        }
      }}
    />
  );
}
function Shell({ onLogout }) {
  const [open, openSet] = useState(false),
    [data, dataSet] = useState(null),
    [error, errorSet] = useState(""),
    [loading, loadingSet] = useState(true);
  const path = useLocation().pathname;
  const load = useCallback(async () => {
    try {
      const r = await api.get("/api/dashboard", {
        params: { dayStart: new Date(localDay() + "T00:00:00").toISOString() },
      });
      dataSet(r.data);
      errorSet("");
    } catch (e) {
      errorSet(message(e));
    } finally {
      loadingSet(false);
    }
  }, []);
  useEffect(() => {
    load();
    const interval = setInterval(load, 60000);
    let socket,
      retry,
      closed = false;
    async function connect() {
      try {
        const r = await api.post("/api/realtime/ticket");
        if (closed) return;
        const url = new URL(baseURL || location.origin);
        url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
        url.pathname = "/ws";
        url.search = `?ticket=${encodeURIComponent(r.data.ticket)}`;
        socket = new WebSocket(url);
        socket.onmessage = load;
        socket.onclose = () => {
          if (!closed) retry = setTimeout(connect, 15000);
        };
      } catch {
        if (!closed) retry = setTimeout(connect, 30000);
      }
    }
    connect();
    return () => {
      closed = true;
      clearInterval(interval);
      clearTimeout(retry);
      socket?.close();
    };
  }, [load]);
  const title = nav.find(([p]) => path === "/" + p)?.[1] || "Dashboard";
  return (
    <div className="app">
      <div
        className={`overlay ${open ? "shown" : ""}`}
        onClick={() => openSet(false)}
      />
      <aside className={open ? "open" : ""}>
        <Brand />
        <div className="workspace">
          <span className="avatar">F</span>
          <div>
            <b>My family</b>
            <small>Personal workspace</small>
          </div>
          <LockKeyhole size={14} />
        </div>
        <span className="nav-label">FAMILY OVERVIEW</span>
        <nav>
          {nav.map(([p, label, Icon], i) => (
            <React.Fragment key={p}>
              {i === 9 && <span className="nav-label">MANAGEMENT</span>}
              <NavLink end to={"/" + p} onClick={() => openSet(false)}>
                <Icon size={18} />
                {label}
                {p === "alerts" &&
                  !!data?.alerts?.filter((a) => !a.read).length && (
                    <i>{data.alerts.filter((a) => !a.read).length}</i>
                  )}
              </NavLink>
            </React.Fragment>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="privacy-mark">
            <ShieldCheck size={19} />
            <div>
              <b>Privacy comes first</b>
              <small>Visible. Consensual. Secure.</small>
            </div>
          </div>
          <button className="text-button" onClick={onLogout}>
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </aside>
      <div className="body">
        <header>
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label="Open menu"
              onClick={() => openSet(true)}
            >
              <Menu />
            </button>
            My family <ChevronRight size={14} />
            <strong>{title}</strong>
          </div>
          <div className="header-right">
            <span className="secure">
              <LockKeyhole size={13} /> Private workspace
            </span>
            <NavLink to="/alerts" className="icon-button" aria-label="Alerts">
              <Bell size={19} />
            </NavLink>
            <span className="parent-avatar">PA</span>
          </div>
        </header>
        <main className="content">
          <ErrorBox error={error} />
          {loading ? (
            <div className="skeletons">
              {[1, 2, 3, 4].map((n) => (
                <div className="skeleton" key={n} />
              ))}
            </div>
          ) : (
            <Routes>
              <Route
                path="/"
                element={<Dashboard data={data} refresh={load} />}
              />
              <Route
                path="/device"
                element={<Device data={data} refresh={load} />}
              />
              <Route path="/location" element={<LocationPage data={data} />} />
              <Route path="/privacy" element={<Preferences data={data} />} />
              <Route
                path="/settings"
                element={<Preferences data={data} settings />}
              />
              <Route path="/alerts" element={<Alerts refresh={load} />} />
              <Route path="/reports" element={<Reports data={data} />} />
              {["apps", "usage", "notifications", "calls", "timeline"].map(
                (category) => (
                  <Route
                    key={category}
                    path={"/" + category}
                    element={
                      <Activity
                        category={category}
                        title={nav.find((n) => n[0] === category)[1]}
                      />
                    }
                  />
                ),
              )}
              <Route path="*" element={<Navigate to="/" />} />
            </Routes>
          )}
          <footer>
            <ShieldCheck size={14} /> FamilyGuard · Built around trust.
            <span>
              Activity depends on device permissions and connectivity.
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
function PageHead({
  eyebrow = "YOUR FAMILY, AT A GLANCE",
  title,
  detail,
  children,
}) {
  return (
    <div className="page-head">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{detail}</p>
      </div>
      <div className="head-actions">{children}</div>
    </div>
  );
}
function Dashboard({ data, refresh }) {
  const device = data?.device,
    rows = usageRows(data?.usage),
    today = rows.filter((e) => e.data.day === localDay()),
    screen = today.reduce((a, e) => a + e.data.durationMs, 0),
    todayAlerts = data?.alertsToday || 0;
  const chart = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 6 + i);
    return {
      day: d.toLocaleDateString(undefined, { weekday: "short" }),
      minutes: Math.round(
        rows
          .filter((e) => e.data.day === localDay(d))
          .reduce((a, e) => a + e.data.durationMs, 0) / 60000,
      ),
    };
  });
  return (
    <>
      <PageHead
        title="A little peace of mind."
        detail="Stay connected to their world. Give them room to grow."
      >
        <button className="secondary" onClick={refresh}>
          <RefreshCw size={16} /> Refresh
        </button>
        <NavLink className="button" to="/device">
          <Smartphone size={16} />
          {device ? "Manage device" : "Connect a device"}
        </NavLink>
      </PageHead>
      <div className="welcome-banner">
        <div>
          <div className="banner-tag">
            <ShieldCheck size={15} /> YOUR FAMILY SPACE
          </div>
          <h2>
            Connected by care.
            <br />
            Protected by trust.
          </h2>
          <p>
            Thoughtful insights. Transparent permissions.
            <br />
            Family safety that puts people first.
          </p>
          <NavLink to="/privacy">
            Review privacy & permissions <ArrowUpRight size={16} />
          </NavLink>
        </div>
        <div className="orbit">
          <div className="orbit-inner">
            <ShieldCheck size={65} strokeWidth={1.4} />
          </div>
          <span className="orbit-badge one">
            <LockKeyhole size={20} />
          </span>
          <span className="orbit-badge two">
            <MapPin size={20} />
          </span>
          <span className="orbit-badge three">
            <Check size={18} />
          </span>
        </div>
      </div>
      <div className="section-line">
        <h2>Device overview</h2>
        <span>
          <span
            className={`dot ${device?.status === "recently_seen" ? "green" : ""}`}
          />
          {device ? device.name : "No device connected"}
        </span>
      </div>
      <div className="stats">
        {[
          [
            Smartphone,
            "Device status",
            device
              ? device.status === "recently_seen"
                ? "Recently seen"
                : "Offline"
              : "Not paired",
            device?.monitoring
              ? "Monitoring enabled"
              : "Monitoring not confirmed",
            "mint",
          ],
          [
            Clock,
            "Today’s screen time",
            device ? (today.length ? minutes(screen) : "Not reported") : "—",
            "Reported foreground app time",
            "lavender",
          ],
          [
            AppWindow,
            "Visible applications",
            device && data.permissions?.apps === true
              ? data.installedAppsCount
              : "—",
            "Within Android package visibility",
            "blue",
          ],
          [
            TriangleAlert,
            "Today’s alerts",
            device ? todayAlerts : "—",
            "Permission-based activity alerts",
            "peach",
          ],
        ].map(([Icon, label, value, note, color]) => (
          <div className="stat panel" key={label}>
            <div className="stat-top">
              <span className={`stat-icon ${color}`}>
                <Icon size={20} />
              </span>
              <ArrowUpRight size={15} />
            </div>
            <p>{label}</p>
            <h2>{value}</h2>
            <small>{note}</small>
          </div>
        ))}
      </div>
      <div className="dashboard-grid">
        <section className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <h2>Screen time</h2>
              <p>A week of digital activity</p>
            </div>
            <span className="chip">Last 7 days</span>
          </div>
          {rows.length ? (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chart} barSize={24}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="day" axisLine={false} tickLine={false} />
                <YAxis axisLine={false} tickLine={false} unit="m" width={45} />
                <Tooltip />
                <Bar dataKey="minutes" fill="#348474" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <Empty
              title="Their rhythm, at a glance"
              detail="Enable Usage Access on the child phone to see screen-time insights."
            />
          )}
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Last shared location</h2>
              <p>Only when location sharing is enabled</p>
            </div>
            <MapPin size={20} />
          </div>
          {data?.lastLocation ? (
            <>
              <LocationMap point={data.lastLocation} compact />
              <div className="location-caption">
                <span className="chip">
                  {stale(data.lastLocation.occurredAt)
                    ? "Outdated location"
                    : "Recent location"}
                </span>
                <small>{date(data.lastLocation.occurredAt)}</small>
              </div>
            </>
          ) : (
            <Empty
              title="A place for peace of mind"
              detail="Opt in on the child phone to share a location."
            />
          )}
          <NavLink className="panel-link" to="/location">
            View location details <ArrowUpRight size={15} />
          </NavLink>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Recent activity</h2>
              <p>The latest from your connected device</p>
            </div>
            <NavLink to="/timeline">
              View all <ChevronRight size={15} />
            </NavLink>
          </div>
          {data?.recent?.length ? (
            <EventList rows={data.recent.slice(0, 5)} />
          ) : (
            <Empty
              title="All quiet here"
              detail="Your family’s approved device activity will appear here."
            />
          )}
        </section>
        <section className="panel device-health">
          <div className="panel-heading">
            <div>
              <h2>Connection & care</h2>
              <p>Know what your device is sharing</p>
            </div>
            <ShieldCheck size={20} />
          </div>
          <div className="health-row">
            <Battery size={18} />
            <span>Battery level</span>
            <b>{device?.battery >= 0 ? `${device.battery}%` : "Unknown"}</b>
          </div>
          <div className="health-row">
            <RefreshCw size={18} />
            <span>Last synchronization</span>
            <b>{date(device?.lastSeen)}</b>
          </div>
          <div className="health-row">
            <Wifi size={18} />
            <span>Connection status</span>
            <b>
              {device?.status === "recently_seen"
                ? "Recently synchronized"
                : "No recent connection"}
            </b>
          </div>
          <div className="notice">
            Offline devices cannot provide live updates. Background
            synchronization may be delayed by Android.
          </div>
          <NavLink className="panel-link" to="/privacy">
            Manage permissions <ArrowUpRight size={15} />
          </NavLink>
        </section>
      </div>
    </>
  );
}
function LocationMap({ point, compact }) {
  const d = point.data;
  return (
    <MapContainer
      key={`${point.id}-${compact}`}
      center={[d.latitude, d.longitude]}
      zoom={14}
      style={{ height: compact ? 190 : 420, borderRadius: 12 }}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Circle
        center={[d.latitude, d.longitude]}
        radius={Math.max(d.accuracy, 15)}
        pathOptions={{ color: "#2c806e" }}
      >
        <Popup>
          Shared {date(point.occurredAt)} · ±{Math.round(d.accuracy)} m
        </Popup>
      </Circle>
    </MapContainer>
  );
}
function LocationPage({ data }) {
  const [history, setHistory] = useState([]),
    [error, errorSet] = useState("");
  useEffect(() => {
    api
      .get("/api/events/location")
      .then((r) => setHistory(r.data))
      .catch((e) => errorSet(message(e)));
  }, [data]);
  const point = data?.lastLocation;
  return (
    <>
      <PageHead
        title="A shared sense of place."
        detail="Location is opt-in. Accuracy and update times are always shown."
      />
      <ErrorBox error={error} />
      <section className="panel">
        {point ? (
          <>
            <div className="panel-heading">
              <h2>
                {stale(point.occurredAt)
                  ? "Outdated · Last known location"
                  : "Recently shared location"}
              </h2>
              <span className="chip">±{Math.round(point.data.accuracy)} m</span>
            </div>
            <LocationMap point={point} />
            <p>
              Last update: {date(point.occurredAt)}. Updates require
              connectivity and enabled location sharing.
            </p>
            <p className="muted">
              Map tiles are provided by OpenStreetMap; loading this map sends
              the map area and your IP address to the tile provider.
            </p>
          </>
        ) : (
          <Empty
            title="Location sharing is not available"
            detail="On the child device, allow location and tap Start location sharing."
          />
        )}
      </section>
      <section className="panel spaced">
        <h2>Location history</h2>
        <EventList rows={history.slice(0, 100)} />
      </section>
    </>
  );
}
function EventList({ rows }) {
  if (!rows.length) return <Empty />;
  return (
    <div className="event-list">
      {rows.map((e) => (
        <div className="event" key={e.id}>
          <span className="event-icon">
            <History size={16} />
          </span>
          <div>
            <b>
              {e.packageName ||
                e.data.permission ||
                e.kind.replaceAll("_", " ")}
            </b>
            <p>
              {e.kind}
              {e.data.state ? ` · ${e.data.state}` : ""}
              {e.data.durationMs !== undefined
                ? ` · ${minutes(e.data.durationMs)}`
                : ""}
              {e.data.latitude !== undefined
                ? ` · ${e.data.latitude.toFixed(4)}, ${e.data.longitude.toFixed(4)} · ±${Math.round(e.data.accuracy)}m`
                : ""}
              {e.data.enabled !== undefined
                ? ` · ${e.data.enabled ? "Enabled" : "Disabled"}`
                : ""}
            </p>
          </div>
          <time>{date(e.occurredAt)}</time>
        </div>
      ))}
    </div>
  );
}
function Activity({ category, title }) {
  const [rows, rowsSet] = useState([]),
    [search, searchSet] = useState(""),
    [from, fromSet] = useState(""),
    [to, toSet] = useState(""),
    [error, errorSet] = useState(""),
    [loading, loadingSet] = useState(true),
    [kind, kindSet] = useState("all");
  useEffect(() => {
    let active = true;
    loadingSet(true);
    const params = { search };
    if (from) params.from = new Date(`${from}T00:00:00`).toISOString();
    if (to) params.to = new Date(`${to}T23:59:59`).toISOString();
    const timer = setTimeout(async () => {
      try {
        const r =
          category === "timeline"
            ? await Promise.all(
                [
                  "apps",
                  "usage",
                  "notifications",
                  "calls",
                  "permissions",
                  "location",
                  "status",
                ].map((c) => api.get("/api/events/" + c, { params })),
              )
            : [await api.get("/api/events/" + category, { params })];
        if (active) {
          rowsSet(
            r
              .flatMap((x) => x.data)
              .sort((a, b) => new Date(b.occurredAt) - new Date(a.occurredAt)),
          );
          errorSet("");
        }
      } catch (e) {
        if (active) errorSet(message(e));
      } finally {
        if (active) loadingSet(false);
      }
    }, 250);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [category, search, from, to]);
  const displayed =
    category === "usage"
      ? usageRows(rows)
      : category === "apps" && kind === "current"
        ? installed(rows)
        : kind === "all"
          ? rows
          : rows.filter((e) => e.kind === kind);
  return (
    <>
      <PageHead
        title={title}
        detail={
          category === "notifications"
            ? "Event metadata only. Notification access does not provide every message or full chat history."
            : category === "calls"
              ? "Cellular call states only, when Android delivers them. No phone numbers, call audio or VoIP monitoring."
              : "Activity shared with the permissions enabled on the child phone."
        }
      />
      <div className="filters panel">
        <label className="search">
          <Search size={17} />
          <input
            placeholder="Search package name…"
            aria-label="Search package name"
            value={search}
            onChange={(e) => searchSet(e.target.value)}
          />
        </label>
        <label>
          From
          <input
            aria-label="From date"
            type="date"
            value={from}
            max={to || undefined}
            onChange={(e) => fromSet(e.target.value)}
          />
        </label>
        <label>
          To
          <input
            aria-label="To date"
            type="date"
            value={to}
            min={from || undefined}
            onChange={(e) => toSet(e.target.value)}
          />
        </label>
        <label>
          Filter
          <select value={kind} onChange={(e) => kindSet(e.target.value)}>
            <option value="all">All events</option>
            {category === "apps" && (
              <option value="current">Currently visible</option>
            )}
            {[...new Set(rows.map((e) => e.kind))].map((k) => (
              <option key={k}>{k}</option>
            ))}
          </select>
        </label>
      </div>
      <ErrorBox error={error} />
      {!loading && ["notifications", "calls"].includes(category) && (
        <div className="stats spaced" style={{ marginBottom: 20 }}>
          {(category === "notifications"
            ? [
                ["Notification events", displayed.length],
                [
                  "Applications represented",
                  new Set(displayed.map((e) => e.packageName)).size,
                ],
                [
                  "Most recent event",
                  displayed[0]
                    ? date(displayed[0].occurredAt)
                    : "None reported",
                ],
              ]
            : [
                [
                  "Ringing states",
                  displayed.filter((e) => e.data.state === "RINGING").length,
                ],
                [
                  "Off-hook states",
                  displayed.filter((e) => e.data.state === "OFFHOOK").length,
                ],
                [
                  "Idle states",
                  displayed.filter((e) => e.data.state === "IDLE").length,
                ],
              ]
          ).map(([label, value]) => (
            <div className="panel" key={label}>
              <p>{label}</p>
              <h2>{value}</h2>
              <small>Within the filtered retained records</small>
            </div>
          ))}
        </div>
      )}
      <section className="panel">
        <div className="panel-heading">
          <h2>Shared activity</h2>
          <span className="chip">
            {displayed.length} records · latest 10,000 per category
          </span>
        </div>
        {loading ? (
          <div className="skeleton" />
        ) : (
          <EventList rows={displayed} />
        )}
      </section>
    </>
  );
}
function Device({ data, refresh }) {
  const [code, codeSet] = useState(null),
    [error, errorSet] = useState(""),
    [busy, busySet] = useState(false),
    [confirm, confirmSet] = useState(false);
  return (
    <>
      <PageHead
        title="One device. A closer connection."
        detail="Pair one child Android device securely, wherever your family is."
      />
      <section className="panel">
        <h2>{data?.device?.name || "Connect your child’s Android phone"}</h2>
        <p>
          Install the same APK on both phones. Choose Admin Mode on your phone,
          and Child Mode on the supervised phone.
        </p>
        <ErrorBox error={error} />
        {!data?.device ? (
          <>
            <button
              disabled={busy}
              onClick={async () => {
                busySet(true);
                try {
                  codeSet((await api.post("/api/pairing/code")).data);
                } catch (e) {
                  errorSet(message(e));
                } finally {
                  busySet(false);
                }
              }}
            >
              <Plus size={16} /> Generate one-time pairing code
            </button>
            {code && (
              <div className="pair-code">
                <small>ENTER IN CHILD MODE</small>
                <strong>{code.code}</strong>
                <p>
                  Expires {date(code.expiresAt)}. Share privately with the child
                  device.
                </p>
              </div>
            )}
          </>
        ) : (
          <>
            <div className="notice">
              Pairing active · {data.device.id}
              <br />
              Last synchronized {date(data.device.lastSeen)}
            </div>
            <button className="danger" onClick={() => confirmSet(true)}>
              Revoke pairing & delete device data
            </button>
            {confirm && (
              <div className="confirmation">
                <h3>Delete all stored device activity?</h3>
                <p>
                  This revokes the device credential and permanently deletes the
                  server’s device data. The child device clears its queue when
                  it next connects.
                </p>
                <button
                  className="danger"
                  disabled={busy}
                  onClick={async () => {
                    busySet(true);
                    try {
                      await api.delete("/api/pairing");
                      confirmSet(false);
                      await refresh();
                    } catch (e) {
                      errorSet(message(e));
                    } finally {
                      busySet(false);
                    }
                  }}
                >
                  Delete and revoke
                </button>
                <button className="secondary" onClick={() => confirmSet(false)}>
                  Cancel
                </button>
              </div>
            )}
          </>
        )}
        <div className="notice spaced">
          Pairing requires the child app’s supervision notice to be accepted.
          Distance does not affect pairing; both devices need internet access.
        </div>
      </section>
    </>
  );
}
function Preferences({ data, settings }) {
  const [p, pSet] = useState(null),
    [error, errorSet] = useState(""),
    [saved, savedSet] = useState(false),
    [busy, busySet] = useState(false),
    [deleting, deletingSet] = useState(false);
  useEffect(() => {
    api
      .get("/api/preferences")
      .then((r) => pSet(r.data))
      .catch((e) => errorSet(message(e)));
  }, []);
  return (
    <>
      <PageHead
        title={
          settings
            ? "Your family, your preferences."
            : "Trust starts with transparency."
        }
        detail="The child controls local permissions. Parent preferences can reduce collection, never grant Android access."
      />
      <ErrorBox error={error} />
      {p && (
        <form
          className="panel"
          onSubmit={async (e) => {
            e.preventDefault();
            busySet(true);
            savedSet(false);
            try {
              pSet((await api.put("/api/preferences", p)).data);
              savedSet(true);
              errorSet("");
            } catch (e) {
              errorSet(message(e));
            } finally {
              busySet(false);
            }
          }}
        >
          <h2>Sharing preferences</h2>
          {[
            ["apps", "Application changes"],
            ["usage", "App usage reports"],
            ["notifications", "Notification event counts"],
            ["calls", "Cellular call states"],
            ["location", "Location sharing"],
            ["alertNotifications", "Selected notification alerts"],
          ].map(([key, label]) => (
            <label className="toggle-row" key={key}>
              <div>
                <b>{label}</b>
                <small>
                  Child permission:{" "}
                  {key in (data?.permissions || {})
                    ? data.permissions[key]
                      ? "Enabled at last sync"
                      : "Disabled at last sync"
                    : "Not reported"}
                </small>
              </div>
              <input
                type="checkbox"
                checked={!!p[key]}
                onChange={(e) => pSet({ ...p, [key]: e.target.checked })}
              />
            </label>
          ))}
          <div className="form-grid">
            <label>
              Activity retention (days)
              <input
                type="number"
                min="1"
                max="90"
                required
                value={p.retentionDays}
                onChange={(e) => pSet({ ...p, retentionDays: +e.target.value })}
              />
            </label>
            <label>
              Location history (days)
              <input
                type="number"
                min="1"
                max="90"
                required
                value={p.locationHistoryDays}
                onChange={(e) =>
                  pSet({ ...p, locationHistoryDays: +e.target.value })
                }
              />
            </label>
          </div>
          <label className="toggle-row">
            <b>Family zone geofence</b>
            <input
              type="checkbox"
              checked={!!p.geofence}
              onChange={(e) =>
                pSet({
                  ...p,
                  geofence: e.target.checked
                    ? { latitude: 0, longitude: 0, radius: 500 }
                    : undefined,
                })
              }
            />
          </label>
          {p.geofence && (
            <div className="form-grid">
              {[
                ["latitude", -90, 90],
                ["longitude", -180, 180],
                ["radius", 100, 100000],
              ].map(([key, min, max]) => (
                <label key={key}>
                  {key === "radius" ? "Radius (meters)" : key}
                  <input
                    type="number"
                    min={min}
                    max={max}
                    step="any"
                    required
                    value={p.geofence[key]}
                    onChange={(e) =>
                      pSet({
                        ...p,
                        geofence: { ...p.geofence, [key]: +e.target.value },
                      })
                    }
                  />
                </label>
              ))}
            </div>
          )}
          <p className="muted">
            Geofence alerts are evaluated when a recent location reaches the
            server. They can be delayed while offline or when location accuracy
            is poor. Preferences reach the child on the next sync.
          </p>
          <button disabled={busy}>
            {busy ? "Saving…" : "Save preferences"}
            <Check size={16} />
          </button>
          {saved && (
            <span role="status" className="success">
              Preferences saved.
            </span>
          )}
        </form>
      )}
      {settings && (
        <section className="panel spaced">
          <h2>Delete account & activity</h2>
          <p>
            Disable the only parent account and permanently delete paired device
            data. Re-enabling requires an operator-managed account reset.
          </p>
          {!deleting ? (
            <button className="danger" onClick={() => deletingSet(true)}>
              Delete account…
            </button>
          ) : (
            <div className="confirmation">
              <p>Confirm permanent account deletion?</p>
              <button
                className="danger"
                onClick={async () => {
                  try {
                    await api.delete("/api/account");
                    location.reload();
                  } catch (e) {
                    errorSet(message(e));
                  }
                }}
              >
                Permanently delete
              </button>
              <button className="secondary" onClick={() => deletingSet(false)}>
                Cancel
              </button>
            </div>
          )}
        </section>
      )}
    </>
  );
}
function Alerts({ refresh }) {
  const [rows, setRows] = useState([]),
    [error, errorSet] = useState("");
  const load = useCallback(
    () =>
      api
        .get("/api/alerts")
        .then((r) => setRows(r.data))
        .catch((e) => errorSet(message(e))),
    [],
  );
  useEffect(() => {
    load();
  }, [load]);
  return (
    <>
      <PageHead
        title="The things worth knowing."
        detail="Rate-limited alerts from permitted device activity."
      />
      <ErrorBox error={error} />
      <section className="panel">
        {rows.length ? (
          rows.map((a) => (
            <div className="event" key={a.id}>
              <span className="event-icon">
                <Bell size={18} />
              </span>
              <div>
                <b>{a.message}</b>
                <p>
                  {a.kind.replaceAll("_", " ")} · {date(a.occurredAt)}
                </p>
              </div>
              {!a.read && (
                <button
                  className="secondary"
                  onClick={async () => {
                    try {
                      await api.post(`/api/alerts/${a.id}/read`);
                      load();
                      refresh();
                    } catch (e) {
                      errorSet(message(e));
                    }
                  }}
                >
                  Mark read
                </button>
              )}
            </div>
          ))
        ) : (
          <Empty title="You’re all caught up" />
        )}
      </section>
    </>
  );
}
function Reports({ data }) {
  const [error, errorSet] = useState(""),
    [busy, busySet] = useState(false);
  return (
    <>
      <PageHead
        title="A clearer picture over time."
        detail="Download the latest retained events for your private review."
      />
      <section className="panel">
        <h2>Family activity export</h2>
        <p>
          Exports include up to 10,000 retained records per category. Files
          contain sensitive family metadata; store them privately.
        </p>
        <ErrorBox error={error} />
        <button
          disabled={!data?.device || busy}
          onClick={async () => {
            busySet(true);
            try {
              const categories = [
                "apps",
                "usage",
                "notifications",
                "calls",
                "permissions",
                "location",
              ];
              const results = await Promise.all(
                categories.map(async (c) => [
                  c,
                  (await api.get("/api/events/" + c)).data,
                ]),
              );
              const url = URL.createObjectURL(
                new Blob(
                  [
                    JSON.stringify(
                      {
                        exportedAt: new Date().toISOString(),
                        ...Object.fromEntries(results),
                      },
                      null,
                      2,
                    ),
                  ],
                  { type: "application/json" },
                ),
              );
              const a = document.createElement("a");
              a.href = url;
              a.download = `familyguard-${localDay()}.json`;
              a.click();
              setTimeout(() => URL.revokeObjectURL(url), 1000);
            } catch (e) {
              errorSet(message(e));
            } finally {
              busySet(false);
            }
          }}
        >
          <Download size={16} />
          {busy ? "Preparing report…" : "Download private report"}
        </button>
      </section>
    </>
  );
}
export { App, Login, Dashboard, Device, Activity, LocationPage, Preferences };
const mount = document.getElementById("root");
if (mount)
  createRoot(mount).render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  );

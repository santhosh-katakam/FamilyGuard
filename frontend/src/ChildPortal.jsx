import React, { useEffect, useRef, useState } from "react";
import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import {
  AppWindow,
  ArrowLeft,
  ArrowUpRight,
  Bell,
  Check,
  ChevronRight,
  Clock,
  LayoutDashboard,
  Link,
  LockKeyhole,
  MapPin,
  Monitor,
  Pause,
  Phone,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Wifi,
  WifiOff,
} from "lucide-react";
import "./child-portal.css";

const storageKey = "familyguard.child-interface.v1";
const permissions = [
  {
    key: "apps",
    title: "Application changes",
    icon: AppWindow,
    description: "Supported app installations, updates and removals.",
    access: "Android package visibility limits which applications are visible.",
  },
  {
    key: "usage",
    title: "Application usage",
    icon: Clock,
    description: "Time spent in supported foreground applications.",
    access:
      "Usage Access must be granted in Android Settings. Usage totals are approximate.",
  },
  {
    key: "notifications",
    title: "Notification events",
    icon: Bell,
    description: "Selected application names, event counts and timestamps.",
    access:
      "Notification Access must be granted in Android Settings. Android can hide notifications; access does not provide full chat history.",
  },
  {
    key: "calls",
    title: "Call activity",
    icon: Phone,
    description: "Supported cellular call states and their timestamps.",
    access:
      "Android Phone permission is required. Call audio, phone numbers and VoIP conversations are excluded.",
  },
  {
    key: "location",
    title: "Location sharing",
    icon: MapPin,
    description: "Your opted-in location, accuracy and update time.",
    access:
      "Android Location permission and a visible foreground notification are required. Location sharing is started separately on the phone.",
  },
];
const notificationApps = ["WhatsApp", "Instagram", "Snapchat", "Telegram"];
const emptyChoices = () =>
  Object.fromEntries(permissions.map(({ key }) => [key, false]));
function readChoices() {
  try {
    const saved = JSON.parse(window.localStorage.getItem(storageKey));
    return {
      sharing: Object.fromEntries(
        permissions.map(({ key }) => [key, saved?.sharing?.[key] === true]),
      ),
      apps: notificationApps.filter((app) => saved?.apps?.includes(app)),
    };
  } catch {
    return { sharing: emptyChoices(), apps: [] };
  }
}

function Confirmation({ kind, onCancel, onConfirm }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  }, []);
  const reset = kind === "reset";
  return (
    <dialog
      ref={ref}
      className="child-dialog"
      aria-labelledby="child-dialog-title"
      onCancel={(event) => {
        event.preventDefault();
        onCancel();
      }}
    >
      <ShieldCheck size={30} />
      <h2 id="child-dialog-title">
        {reset ? "Reset browser choices?" : "Pause monitoring?"}
      </h2>
      <p>
        {reset
          ? "This clears the sharing choices saved by this browser test. Your parent account and Android pairing stay unchanged."
          : "On Android, pausing stops collection and clears pending activity. This browser test checks the confirmation flow and sends no command to a phone."}
      </p>
      <div className="child-actions">
        <button className="secondary" onClick={onCancel} autoFocus>
          Cancel
        </button>
        <button className="danger" onClick={onConfirm}>
          {reset ? "Reset browser choices" : "Confirm pause test"}
        </button>
      </div>
    </dialog>
  );
}

export function ChildPortal() {
  const [saved] = useState(readChoices);
  const [choices, setChoices] = useState(saved.sharing);
  const [apps, setApps] = useState(saved.apps);
  const [online, setOnline] = useState(window.navigator.onLine);
  const [confirmation, setConfirmation] = useState(null);
  const [feedback, setFeedback] = useState("");
  useEffect(() => {
    const update = () => setOnline(window.navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  function save() {
    try {
      window.localStorage.setItem(
        storageKey,
        JSON.stringify({ sharing: choices, apps }),
      );
      setFeedback(
        "Choices saved in this browser for interface testing. Android permissions are unchanged.",
      );
    } catch {
      setFeedback(
        "Browser storage is unavailable. Your choices remain on this page until it closes.",
      );
    }
  }
  function confirm() {
    if (confirmation === "pause") {
      setFeedback(
        "Pause confirmation checked. Android monitoring is unavailable in this browser; no phone command was sent.",
      );
    } else {
      setChoices(emptyChoices());
      setApps([]);
      try {
        window.localStorage.removeItem(storageKey);
        setFeedback(
          "Browser choices reset. Your parent account and device pairing are unchanged.",
        );
      } catch {
        setFeedback(
          "Choices reset on this page. Browser storage could not be cleared.",
        );
      }
    }
    setConfirmation(null);
  }
  const count = Object.values(choices).filter(Boolean).length;
  return (
    <main className="child-portal">
      <div className="child-topbar">
        <div className="brand">
          <span>
            <ShieldCheck size={25} />
          </span>
          FamilyGuard<i>●</i>
        </div>
        <NavLink to="/" className="child-parent-link">
          <ArrowLeft size={16} />
          Return to parent sign in
        </NavLink>
      </div>
      <div className="child-test-banner">
        <Monitor size={21} />
        <div>
          <strong>Browser interface test</strong>
          <p>
            Explore the child screens here. Sharing choices stay in this
            browser; phone pairing and Android permissions are completed in the
            native app.
          </p>
        </div>
      </div>
      <div className="child-title">
        <div>
          <span className="eyebrow">CHILD SPACE</span>
          <h1>Your space. Your choices.</h1>
          <p>
            See what is shared, review your permissions, and stay in control.
          </p>
        </div>
        <span className="child-browser-status">
          {online ? <Wifi size={16} /> : <WifiOff size={16} />}Browser network:{" "}
          {online ? "Online" : "Offline"}
        </span>
      </div>
      <nav className="child-tabs" aria-label="Child portal">
        {[
          ["", "Overview", LayoutDashboard],
          ["pairing", "Pairing", Link],
          ["permissions", "Permissions", LockKeyhole],
          ["status", "Monitoring", Smartphone],
        ].map(([path, label, Icon]) => (
          <NavLink key={path} to={`/child${path ? "/" + path : ""}`} end>
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
      </nav>
      <div
        role="status"
        aria-live="polite"
        className={feedback ? "child-feedback" : ""}
      >
        {feedback}
      </div>
      <Routes>
        <Route path="/child" element={<Overview count={count} />} />
        <Route path="/child/pairing" element={<PairingForm />} />
        <Route
          path="/child/permissions"
          element={
            <section className="panel child-section">
              <span className="eyebrow">YOUR SHARING CHOICES</span>
              <h2>Choose what you would like to share</h2>
              <p>
                Try each control below. Selecting a choice here does not grant
                an Android permission or start collection.
              </p>
              <div className="child-permission-list">
                {permissions.map(
                  ({ key, title, icon: Icon, description, access }) => (
                    <article className="child-permission" key={key}>
                      <div className="child-permission-icon">
                        <Icon size={22} />
                      </div>
                      <div className="child-permission-copy">
                        <label className="child-choice">
                          <span>{title}</span>
                          <input
                            type="checkbox"
                            checked={choices[key]}
                            onChange={(event) => {
                              setChoices({
                                ...choices,
                                [key]: event.target.checked,
                              });
                              setFeedback("");
                            }}
                          />
                        </label>
                        <p>{description}</p>
                        <small>Android access: unavailable in browser</small>
                        <details>
                          <summary>How this permission works</summary>
                          <p>{access}</p>
                        </details>
                        {key === "notifications" && (
                          <fieldset
                            className="child-app-picker"
                            disabled={!choices.notifications}
                          >
                            <legend>Applications to include</legend>
                            {notificationApps.map((app) => (
                              <label key={app}>
                                <input
                                  type="checkbox"
                                  checked={apps.includes(app)}
                                  onChange={(event) => {
                                    setApps(
                                      event.target.checked
                                        ? [...apps, app]
                                        : apps.filter((value) => value !== app),
                                    );
                                    setFeedback("");
                                  }}
                                />
                                {app}
                              </label>
                            ))}
                          </fieldset>
                        )}
                      </div>
                    </article>
                  ),
                )}
              </div>
              <div className="child-actions">
                <button onClick={save}>
                  <Check size={17} />
                  Save browser choices
                </button>
                <span>Applies to this interface test only.</span>
              </div>
            </section>
          }
        />
        <Route
          path="/child/status"
          element={
            <div className="child-grid">
              <section className="panel child-section">
                <span className="eyebrow">DEVICE STATUS</span>
                <h2>Monitoring & synchronization</h2>
                <dl className="child-status-list">
                  <div>
                    <dt>Android device</dt>
                    <dd>Not connected</dd>
                  </div>
                  <div>
                    <dt>Monitoring</dt>
                    <dd>Unavailable in browser</dd>
                  </div>
                  <div>
                    <dt>Last device synchronization</dt>
                    <dd>No device connected</dd>
                  </div>
                  <div>
                    <dt>Pending device events</dt>
                    <dd>Not available</dd>
                  </div>
                  <div>
                    <dt>Browser network</dt>
                    <dd>{online ? "Online" : "Offline"}</dd>
                  </div>
                </dl>
                <details className="child-sync-details">
                  <summary>How offline synchronization works</summary>
                  <p>
                    The Android app encrypts permitted events in its local
                    queue. WorkManager retries when internet returns. A remote
                    dashboard receives updates after a successful sync; offline
                    activity is not live.
                  </p>
                </details>
              </section>
              <section className="panel child-section">
                <span className="eyebrow">YOU STAY IN CONTROL</span>
                <h2>Try the controls</h2>
                <p>
                  Review the pause confirmation or start again with your browser
                  choices.
                </p>
                <div className="child-control-actions">
                  <button
                    className="secondary"
                    onClick={() => {
                      setFeedback("");
                      setConfirmation("pause");
                    }}
                  >
                    <Pause size={17} />
                    Test pause confirmation
                  </button>
                  <button
                    className="secondary"
                    onClick={() => {
                      setFeedback("");
                      setConfirmation("reset");
                    }}
                  >
                    <RefreshCw size={17} />
                    Reset browser choices
                  </button>
                </div>
                <div className="child-privacy-note">
                  <LockKeyhole size={22} />
                  <p>
                    FamilyGuard shares permitted event metadata. Private
                    conversations, call audio and passwords are excluded from
                    collection.
                  </p>
                </div>
              </section>
            </div>
          }
        />
        <Route path="*" element={<Navigate to="/child" replace />} />
      </Routes>
      <div className="child-footer">
        <ShieldCheck size={16} />
        <span>
          Visible supervision. Clear choices. Android activity requires the
          native app.
        </span>
      </div>
      {confirmation && (
        <Confirmation
          kind={confirmation}
          onCancel={() => setConfirmation(null)}
          onConfirm={confirm}
        />
      )}
    </main>
  );
}

function Overview({ count }) {
  return (
    <>
      <section className="child-welcome">
        <div>
          <span className="eyebrow">LET'S TAKE A LOOK TOGETHER</span>
          <h2>Understand your digital space.</h2>
          <p>
            You can explore every child screen without installing an APK. Start
            with the supervision notice, then try pairing and sharing controls.
          </p>
          <NavLink to="/child/pairing" className="button">
            Try the pairing screen
            <ArrowUpRight size={17} />
          </NavLink>
        </div>
        <div className="child-welcome-icon" aria-hidden="true">
          <ShieldCheck size={90} strokeWidth={1.2} />
        </div>
      </section>
      <div className="child-summary">
        <article className="panel">
          <Smartphone size={23} />
          <span>Android device</span>
          <strong>Not connected</strong>
          <p>Browser interface test</p>
        </article>
        <article className="panel">
          <LockKeyhole size={23} />
          <span>Sharing choices selected</span>
          <strong>
            {count} of {permissions.length}
          </strong>
          <p>Choices for this browser test</p>
        </article>
        <article className="panel">
          <ShieldCheck size={23} />
          <span>Android collection</span>
          <strong>Unavailable here</strong>
          <p>No device activity is collected</p>
        </article>
      </div>
      <div className="child-grid">
        <section className="panel child-section">
          <span className="eyebrow">SUPERVISION NOTICE</span>
          <h2>What your parent can see</h2>
          <p>
            On a paired Android device, your parent can receive the activity you
            enable: supported app changes, usage, selected notification events,
            cellular call states and opted-in location. Permission access and
            supervision status remain visible on the phone.
          </p>
          <p>
            You can pause collection. Offline events are encrypted locally and
            synchronize when a connection returns.
          </p>
          <NavLink to="/child/permissions" className="child-card-link">
            Explore sharing choices
            <ChevronRight size={17} />
          </NavLink>
        </section>
        <section className="panel child-section">
          <span className="eyebrow">PRIVACY COMES FIRST</span>
          <h2>Know what stays private</h2>
          <ul className="child-privacy-list">
            <li>
              <Check size={17} />
              Private notification contents and chat history
            </li>
            <li>
              <Check size={17} />
              Call audio and phone numbers
            </li>
            <li>
              <Check size={17} />
              Account passwords and private conversations
            </li>
          </ul>
          <p>
            Notification access can miss or hide events. It cannot provide every
            incoming message or a full conversation archive.
          </p>
        </section>
      </div>
    </>
  );
}

function PairingForm() {
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [consent, setConsent] = useState(false);
  const [errors, setErrors] = useState([]);
  const [ready, setReady] = useState(false);
  function check(event) {
    event.preventDefault();
    const problems = [];
    if (!name.trim() || name.trim().length > 80)
      problems.push("Enter a device name between 1 and 80 characters.");
    if (!/^[A-Z0-9_-]{12}$/.test(code))
      problems.push(
        "Enter the 12-character pairing code using letters, numbers, hyphens or underscores.",
      );
    if (!consent)
      problems.push(
        "Read and acknowledge the supervision notice before continuing.",
      );
    setErrors(problems);
    setReady(problems.length === 0);
  }
  return (
    <div className="child-grid child-pairing-grid">
      <section className="panel child-section">
        <span className="eyebrow">ONE PARENT. ONE ANDROID DEVICE.</span>
        <h2>Pairing setup</h2>
        <p>
          Try the same setup fields used by Child Mode. This browser checks the
          form only; it does not verify or consume a real pairing code.
        </p>
        <form
          className="child-pairing-form"
          noValidate
          onSubmit={check}
          onChange={() => {
            setErrors([]);
            setReady(false);
          }}
        >
          <label>
            Device name
            <input
              autoComplete="off"
              maxLength={80}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="e.g. My Android phone"
            />
          </label>
          <label>
            Parent pairing code
            <input
              className="child-code-input"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={64}
              value={code}
              onChange={(event) =>
                setCode(event.target.value.replace(/\s/g, "").toUpperCase())
              }
              placeholder="12-character code"
              aria-describedby="child-pairing-help"
            />
          </label>
          <p id="child-pairing-help" className="child-field-help">
            A parent generates a one-time code in Device Management. Real codes
            expire after five minutes.
          </p>
          <label className="child-consent">
            <input
              type="checkbox"
              checked={consent}
              onChange={(event) => setConsent(event.target.checked)}
            />
            <span>
              I have read the supervision notice and understand the sharing
              choices.
            </span>
          </label>
          {errors.length > 0 && (
            <div className="error" role="alert">
              <ul>
                {errors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </div>
          )}
          {ready && (
            <div className="child-form-success" role="status">
              <Check size={20} />
              <span>
                Setup form checked. No device was paired or pairing code
                consumed. Complete actual pairing in the Android app.
              </span>
            </div>
          )}
          <button type="submit">
            Check pairing form
            <ArrowUpRight size={17} />
          </button>
        </form>
      </section>
      <section className="panel child-section">
        <span className="eyebrow">READ TOGETHER</span>
        <h2>Before you connect</h2>
        <p>
          Your parent receives only permitted activity metadata. Android asks
          for each system permission separately, and location sharing requires
          its own opt-in.
        </p>
        <p>
          Monitoring stays visible and can be paused. FamilyGuard does not
          record calls, read notification text, or collect account passwords.
        </p>
        <div className="child-privacy-note">
          <ShieldCheck size={24} />
          <p>
            This acknowledgement is part of the browser screen test. Consent is
            requested again when pairing a real Android device.
          </p>
        </div>
        <NavLink to="/child/permissions" className="child-card-link">
          Next: explore permissions
          <ChevronRight size={17} />
        </NavLink>
      </section>
    </div>
  );
}

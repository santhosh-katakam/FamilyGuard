import React, { useCallback, useEffect, useState } from "react";
import { Bell, RefreshCw, Smartphone } from "lucide-react";
import { api, message } from "./api";

export function ParentReceiverSettings() {
  const [receiver, setReceiver] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const load = useCallback(async () => {
    setBusy(true);
    try {
      setReceiver((await api.get("/api/parent-receiver")).data);
      setError("");
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  return (
    <section className="panel spaced">
      <div className="panel-heading">
        <div>
          <h2>
            <Bell size={18} /> Parent phone alerts
          </h2>
          <p>One registered parent phone receives approved activity alerts.</p>
        </div>
        <button
          className="secondary"
          disabled={busy}
          onClick={load}
          aria-label="Refresh parent receiver"
        >
          <RefreshCw size={16} />
        </button>
      </div>
      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      {!receiver && !error && (
        <p role="status">Checking parent phone registration…</p>
      )}
      {receiver && (
        <>
          <div className="health-row">
            <Smartphone size={18} />
            <span>Parent phone</span>
            <b>
              {receiver.registered ? receiver.deviceName : "Not registered"}
            </b>
          </div>
          {receiver.registered && (
            <>
              <div className="health-row">
                <span>Last receiver catch-up</span>
                <b>
                  {receiver.lastSeen
                    ? new Date(receiver.lastSeen).toLocaleString()
                    : "Not available"}
                </b>
              </div>
              <div className="health-row">
                <span>Receiver credential expires</span>
                <b>{new Date(receiver.expiresAt).toLocaleString()}</b>
              </div>
            </>
          )}
          <p className="muted">
            Open the Parent Android app and sign in to register this phone.
            Allow notifications, then tap Start live alerts. Choose categories
            and quiet hours on that phone. Its visible connection notification
            provides a Stop control.
          </p>
          <div className="notice">
            Alerts can arrive while the dashboard is closed and the parent
            receiver is connected. Android background restrictions, a stopped
            receiver, lost internet or an unavailable server can delay delivery.
            Reconnecting catches up retained approved events.
          </div>
          {receiver.registered &&
            (!confirm ? (
              <button className="danger" onClick={() => setConfirm(true)}>
                Revoke parent phone…
              </button>
            ) : (
              <div className="confirmation">
                <p>
                  Revoke this phone’s receiver credential and disconnect its
                  alert connection? The parent account and child pairing will
                  stay active.
                </p>
                <button
                  className="danger"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await api.delete("/api/parent-receiver");
                      setConfirm(false);
                      await load();
                    } catch (failure) {
                      setError(message(failure));
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  Confirm revocation
                </button>
                <button
                  className="secondary"
                  disabled={busy}
                  onClick={() => setConfirm(false)}
                >
                  Cancel
                </button>
              </div>
            ))}
        </>
      )}
    </section>
  );
}

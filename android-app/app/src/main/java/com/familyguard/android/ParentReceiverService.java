package com.familyguard.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** User-started, visible best-effort receiver. Never boot-started or used to bypass Doze. */
public class ParentReceiverService extends Service {
  static volatile boolean running;
  final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
  final OkHttpClient socketClient =
      Api.CLIENT
          .newBuilder()
          .pingInterval(25, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .callTimeout(0, TimeUnit.MILLISECONDS)
          .build();
  volatile boolean stopped;
  volatile WebSocket socket;
  ScheduledFuture<?> retry;
  int failures;
  ConnectivityManager network;
  final ConnectivityManager.NetworkCallback connectivity =
      new ConnectivityManager.NetworkCallback() {
        public void onAvailable(Network n) {
          submit(
              () -> {
                if (socket == null) connect();
                else synchronize();
              });
        }

        public void onLost(Network n) {
          submit(
              () -> {
                if (!Collectors.connected(ParentReceiverService.this))
                  disconnect("Offline; waiting to reconnect");
              });
        }
      };

  public void onCreate() {
    super.onCreate();
    ParentNotifications.channels(this);
  }

  // Shared source is analyzed even in the child flavor, where this component is removed.
  // Parent/universal manifests declare specialUse, its permission and the purpose subtype.
  @android.annotation.SuppressLint("ForegroundServiceType")
  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && "STOP".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (BuildConfig.APP_ROLE.equals("child") || !ParentNotifications.visible(this)) {
      ParentStore.status(this, "Stopped: allow visible notifications before starting live alerts.");
      stopSelf();
      return START_NOT_STICKY;
    }
    try {
      if (ParentStore.token(this) == null) {
        stopSelf();
        return START_NOT_STICKY;
      }
      Notification notice =
          ParentNotifications.connection(this, "Connecting to the safety server…");
      if (Build.VERSION.SDK_INT >= 34)
        startForeground(
            ParentNotifications.CONNECTION_ID,
            notice,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
      else startForeground(ParentNotifications.CONNECTION_ID, notice);
    } catch (RuntimeException error) {
      ParentStore.status(
          this, "Android could not start the visible receiver. Open FamilyGuard to retry.");
      stopSelf();
      return START_NOT_STICKY;
    }
    if (!running) {
      running = true;
      network = getSystemService(ConnectivityManager.class);
      network.registerDefaultNetworkCallback(connectivity);
      submit(this::connect);
      io.scheduleWithFixedDelay(
          () -> {
            if (!stopped) {
              if (socket == null) connect();
              else synchronize();
            }
          },
          60,
          60,
          TimeUnit.SECONDS);
    }
    return START_NOT_STICKY;
  }

  void submit(Runnable task) {
    if (!stopped)
      try {
        io.execute(
            () -> {
              if (!stopped) task.run();
            });
      } catch (RejectedExecutionException ignored) {
      }
  }

  void status(String text) {
    ParentStore.status(this, text);
    if (!stopped && ParentNotifications.visible(this))
      try {
        getSystemService(NotificationManager.class)
            .notify(ParentNotifications.CONNECTION_ID, ParentNotifications.connection(this, text));
      } catch (SecurityException ignored) {
        stopSelf();
      }
  }

  boolean allowed() {
    if (stopped) return false;
    if (!ParentNotifications.visible(this)) {
      ParentStore.status(
          this, "Stopped: notification permission or the connection channel was disabled.");
      stopSelf();
      return false;
    }
    return true;
  }

  void connect() {
    if (!allowed() || socket != null) return;
    if (retry != null) {
      retry.cancel(false);
      retry = null;
    }
    if (!Collectors.connected(this)) {
      later("Offline; waiting for internet");
      return;
    }
    try {
      String token = ParentStore.token(this);
      if (token == null) {
        stopSelf();
        return;
      }
      var response = Api.post("/api/parent-device/ticket", token, new JSONObject());
      if (unauthorized(response.code())) return;
      if (response.code() != 200) {
        later("Server unavailable; retrying with backoff");
        return;
      }
      if (!allowed()) return;
      String ticket = response.body().getString("ticket");
      String address = BuildConfig.API_URL.replaceFirst("^https:", "wss:") + "/ws?ticket=" + ticket;
      Request request =
          new Request.Builder().url(address).header("Origin", BuildConfig.WEB_URL).build();
      socket =
          socketClient.newWebSocket(
              request,
              new WebSocketListener() {
                public void onOpen(WebSocket ws, Response r) {
                  submit(
                      () -> {
                        if (socket != ws) return;
                        failures = 0;
                        status(
                            "Connected; live alerts are available while this connection stays"
                                + " active");
                        synchronize();
                      });
                }

                public void onMessage(WebSocket ws, String message) {
                  submit(
                      () -> {
                        if (ws != socket || message.length() > 1024) return;
                        try {
                          if (new JSONObject(message).optString("type").equals("refresh"))
                            synchronize();
                        } catch (JSONException ignored) {
                        }
                      });
                }

                public void onFailure(WebSocket ws, Throwable error, Response r) {
                  submit(
                      () -> {
                        if (ws == socket) {
                          socket = null;
                          later("Connection interrupted; reconnecting");
                        }
                      });
                }

                public void onClosing(WebSocket ws, int code, String reason) {
                  ws.close(code, null);
                }

                public void onClosed(WebSocket ws, int code, String reason) {
                  submit(
                      () -> {
                        if (ws == socket) {
                          socket = null;
                          later("Connection closed; reconnecting");
                        }
                      });
                }
              });
    } catch (Exception ignored) {
      later("Could not reach the server; retrying");
    }
  }

  void later(String text) {
    if (!allowed()) return;
    status(text);
    if (retry != null && !retry.isDone()) return;
    long delay = Math.min(60, 1L << Math.min(failures++, 6));
    retry = io.schedule(this::connect, delay, TimeUnit.SECONDS);
  }

  void disconnect(String text) {
    WebSocket old = socket;
    socket = null;
    if (old != null) old.cancel();
    later(text);
  }

  boolean unauthorized(int code) {
    if (code != 401 && code != 403) return false;
    ParentStore.clear(this);
    ParentInboxDb.get(this).events().clear();
    ParentNotifications.clearEvents(this);
    ParentStore.status(this, "Parent phone registration expired or was revoked. Sign in again.");
    stopSelf();
    return true;
  }

  void synchronize() {
    if (!allowed()) return;
    try {
      String credential = ParentStore.token(this);
      if (credential == null) {
        stopSelf();
        return;
      }
      var dao = ParentInboxDb.get(this).events();
      dao.expire(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
      if (!notifyPending()) return;
      for (int page = 0; page < 20 && allowed(); page++) {
        var response = Api.request("GET", "/api/parent-device/events", credential, null);
        if (unauthorized(response.code())) return;
        if (response.code() != 200) {
          disconnect("Delivery catch-up delayed; retrying");
          return;
        }
        if (!allowed() || !credential.equals(ParentStore.token(this))) return;
        JSONArray events = response.body().getJSONArray("events");
        // Parse the entire batch before storing or acknowledging any of it.
        java.util.List<ParentInboxEvent> rows = new java.util.ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
          JSONObject safe = ParentNotifications.metadata(events.getJSONObject(i));
          var row = new ParentInboxEvent();
          row.id = safe.getString("id");
          row.sequence = safe.getLong("sequence");
          row.receivedAt = System.currentTimeMillis();
          row.encrypted = SecureStore.encrypt(safe.toString());
          rows.add(row);
        }
        ParentInboxDb.get(this)
            .runInTransaction(
                () -> {
                  for (var row : rows) dao.insert(row);
                });
        if (!notifyPending()) return;
        long next = response.body().getLong("nextCursor");
        var ack =
            Api.post("/api/parent-device/ack", credential, new JSONObject().put("cursor", next));
        if (unauthorized(ack.code())) return;
        if (ack.code() != 204) {
          disconnect("Acknowledgment delayed; retrying safely");
          return;
        }
        dao.trim();
        ParentStore.preferences(this)
            .edit()
            .putLong("last-contact", System.currentTimeMillis())
            .apply();
        if (!response.body().getBoolean("hasMore")) break;
      }
    } catch (Exception ignored) {
      if (!stopped) disconnect("Catch-up interrupted; events will be retried");
    }
  }

  boolean notifyPending() throws Exception {
    var dao = ParentInboxDb.get(this).events();
    for (var page = dao.pending(); !page.isEmpty(); page = dao.pending()) {
      for (var row : page) {
        if (!allowed()) return false;
        JSONObject event = new JSONObject(SecureStore.decrypt(row.encrypted));
        if (!ParentNotifications.post(this, row.id, event)) {
          stopSelf();
          return false;
        }
        // Stable Android notification tags make crash/replay display idempotent.
        dao.notified(row.id);
      }
    }
    return true;
  }

  public void onDestroy() {
    stopped = true;
    running = false;
    if (network != null)
      try {
        network.unregisterNetworkCallback(connectivity);
      } catch (RuntimeException ignored) {
      }
    WebSocket old = socket;
    socket = null;
    if (old != null) old.cancel();
    io.shutdownNow();
    stopForeground(STOP_FOREGROUND_REMOVE);
    if (ParentStore.preferences(this).contains("credential"))
      ParentStore.status(this, "Stopped. Open FamilyGuard and tap Start live alerts to reconnect.");
    super.onDestroy();
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}

package com.familyguard.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

public class ChildActivity extends Activity {
  TextView status;
  Handler handler = new Handler(Looper.getMainLooper());
  final Runnable update =
      new Runnable() {
        public void run() {
          refreshStatus();
          handler.postDelayed(this, 5000);
        }
      };

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    render();
  }

  protected void onResume() {
    super.onResume();
    if (SecureStore.token(this) != null) {
      SyncWorker.schedule(this);
      Events.IO.execute(() -> Collectors.collect(this));
    }
    handler.post(update);
  }

  protected void onPause() {
    handler.removeCallbacks(update);
    super.onPause();
  }

  void render() {
    Ui.page(this, "Your FamilyGuard", "Visible supervision. You stay in control.");
    if (SecureStore.token(this) == null) {
      pairing();
      return;
    }
    status = Ui.text(this, "Checking connection…");
    Ui.text(
        this,
        "Your parent can see enabled event metadata, supported app usage, visible applications, and"
            + " your opted-in location. FamilyGuard never records calls or reads private"
            + " notification content.");
    var preferences = getSharedPreferences("consent", 0);
    for (String key : List.of("apps", "usage", "notifications", "calls")) {
      String label =
          switch (key) {
            case "apps" -> "Share visible application changes";
            case "usage" -> "Share reported application usage";
            case "notifications" -> "Share selected notification event counts";
            default -> "Share supported cellular call states";
          };
      Ui.check(
          this,
          label,
          preferences.getBoolean(key, false),
          (b, on) -> {
            preferences.edit().putBoolean(key, on).commit();
            if (!on) Events.IO.execute(() -> QueueDb.get(this).events().clearCategory(key));
            Collectors.permission(
                this,
                key,
                on
                    && switch (key) {
                      case "usage" -> Consent.usage(this);
                      case "notifications" -> Consent.notifications(this);
                      case "calls" -> Consent.granted(this, Manifest.permission.READ_PHONE_STATE);
                      default -> true;
                    });
          });
    }
    Ui.button(
        this,
        "Open Android Usage Access settings",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    Ui.button(
        this,
        "Open Android Notification Access settings",
        () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    Ui.button(
        this,
        "Request cellular call-state permission",
        () -> requestPermissions(new String[] {Manifest.permission.READ_PHONE_STATE}, 11));
    Ui.text(
        this,
        "Notification access does not guarantee every incoming notification or any full chat"
            + " history. Android may hide sensitive notifications. Select apps below; only names"
            + " and event timestamps are shared.");
    Set<String> known =
        new TreeSet<>(
            Set.of(
                "com.whatsapp",
                "com.instagram.android",
                "com.snapchat.android",
                "org.telegram.messenger"));
    Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (var info : getPackageManager().queryIntentActivities(launcher, 0))
      known.add(info.activityInfo.packageName);
    Ui.button(
        this,
        "Choose notification applications",
        () -> {
          String[] packages = known.toArray(new String[0]);
          String[] labels = new String[packages.length];
          boolean[] enabled = new boolean[packages.length];
          Set<String> selected = new HashSet<>(preferences.getStringSet("selectedApps", Set.of()));
          for (int i = 0; i < packages.length; i++) {
            labels[i] = Collectors.label(this, packages[i]) + " (" + packages[i] + ")";
            enabled[i] = selected.contains(packages[i]);
          }
          new AlertDialog.Builder(this)
              .setTitle("Notification event applications")
              .setMultiChoiceItems(
                  labels,
                  enabled,
                  (d, index, on) -> {
                    if (on) selected.add(packages[index]);
                    else selected.remove(packages[index]);
                  })
              .setNegativeButton("Cancel", null)
              .setPositiveButton(
                  "Save",
                  (d, w) -> preferences.edit().putStringSet("selectedApps", selected).commit())
              .show();
        });
    Ui.button(
        this,
        "Enable monitoring",
        () -> {
          if (!Consent.noticeVisible(this)) {
            if (Build.VERSION.SDK_INT >= 33
                && !Consent.granted(this, Manifest.permission.POST_NOTIFICATIONS))
              requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 13);
            else
              startActivity(
                  new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                      .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            toast("Allow visible supervision notifications, then tap Enable monitoring again.");
            return;
          }
          preferences.edit().putBoolean("monitoring", true).commit();
          getSharedPreferences("apps", 0).edit().clear().apply();
          getSharedPreferences("permissions", 0).edit().clear().apply();
          if (Build.VERSION.SDK_INT >= 33
              && !Consent.granted(this, Manifest.permission.POST_NOTIFICATIONS))
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 13);
          MonitorNotice.show(this);
          SyncWorker.schedule(this);
          refreshStatus();
        });
    Ui.button(
        this,
        "Pause monitoring & clear pending activity",
        () ->
            Ui.confirm(
                this,
                "Pause collection and clear pending activity? Your parent will receive a"
                    + " monitoring-disabled status when online.",
                () -> {
                  Consent.pause(this);
                  refreshStatus();
                }));
    Ui.text(
        this,
        "Location sharing is separate. Start it here after granting permission. It shows a"
            + " persistent notification and can be stopped at any time. It does not automatically"
            + " restart after a reboot or force stop.");
    Ui.button(
        this,
        "Start location sharing",
        () -> {
          if (!Consent.active(this)) {
            toast("Enable monitoring first.");
            return;
          }
          if (!Consent.location(this)) {
            requestPermissions(
                new String[] {
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                },
                12);
            toast("After choosing permission, tap Start location sharing again.");
            return;
          }
          if (Build.VERSION.SDK_INT >= 33
              && !Consent.granted(this, Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 13);
            toast("Allow the visible notification, then tap Start again.");
            return;
          }
          preferences.edit().putBoolean("location", true).commit();
          try {
            startForegroundService(new Intent(this, LocationService.class));
            Collectors.permission(this, "location", true);
          } catch (RuntimeException e) {
            preferences.edit().putBoolean("location", false).commit();
            toast("Android could not start location sharing. Check system settings.");
          }
        });
    Ui.button(
        this,
        "Stop location sharing",
        () -> {
          preferences.edit().putBoolean("location", false).commit();
          stopService(new Intent(this, LocationService.class));
          Events.IO.execute(() -> QueueDb.get(this).events().clearCategory("location"));
          Collectors.permission(this, "location", false);
        });
    Ui.button(
        this,
        "Synchronize now",
        () -> {
          SyncWorker.scheduleNow(this);
          toast("Sync requested. Android runs it when a network is available.");
        });
    Ui.button(
        this,
        "Clear local pending activity",
        () ->
            Ui.confirm(
                this,
                "Delete all unsent events? Already synchronized server records are managed by the"
                    + " parent’s privacy settings.",
                () -> Events.IO.execute(() -> QueueDb.get(this).events().clear())));
    Ui.button(
        this,
        "Remove pairing on this phone",
        () ->
            Ui.confirm(
                this,
                "Stop monitoring and remove local credentials and queued data? The parent must"
                    + " revoke the old device before a new pairing.",
                () ->
                    Events.IO.execute(
                        () -> {
                          Consent.reset(this);
                          runOnUiThread(this::render);
                        })));
    refreshStatus();
  }

  void pairing() {
    Ui.text(
        this,
        "SUPERVISION NOTICE\n"
            + "Your Parent Admin can receive only the activity you explicitly enable. Collection"
            + " remains visible and can be paused here. Notification contents, phone numbers, call"
            + " audio, passwords and private chat histories are never collected. Offline events are"
            + " encrypted locally and sent when internet returns. Read this together before"
            + " pairing.");
    EditText code = Ui.input(this, "12-character parent pairing code");
    code.setFilters(
        new android.text.InputFilter[] {
          new android.text.InputFilter.AllCaps(), new android.text.InputFilter.LengthFilter(12)
        });
    EditText name = Ui.input(this, "Device name (e.g. My Android phone)");
    name.setText(Build.MANUFACTURER + " " + Build.MODEL);
    CheckBox consent =
        Ui.check(
            this,
            "I understand and accept visible family supervision on this device.",
            false,
            (b, on) -> {});
    Ui.button(
        this,
        "Pair this device",
        () -> {
          if (!consent.isChecked()
              || code.getText().length() != 12
              || name.getText().toString().isBlank()) {
            toast("Read the notice, accept, and enter the code and device name.");
            return;
          }
          String codeText = code.getText().toString(), deviceName = name.getText().toString();
          Events.IO.execute(
              () -> {
                try {
                  var r =
                      Api.post(
                          "/api/pairing/complete",
                          null,
                          Events.data("code", codeText, "deviceName", deviceName, "consent", true));
                  if (r.code() != 200)
                    throw new IllegalStateException(
                        r.body()
                            .optString(
                                "message", "Pairing failed. Check the code and server URL."));
                  SecureStore.token(this, r.body().getString("deviceToken"));
                  getSharedPreferences("consent", 0).edit().clear().commit();
                  SyncWorker.schedule(this);
                  runOnUiThread(this::render);
                } catch (Exception e) {
                  runOnUiThread(
                      () ->
                          toast(
                              e.getMessage() == null
                                  ? "Could not reach the server."
                                  : e.getMessage()));
                }
              });
        });
    Ui.text(
        this,
        "API server: "
            + BuildConfig.API_URL
            + "\n"
            + "Both devices need an internet connection for pairing. A browser page alone cannot"
            + " monitor this device.");
  }

  void refreshStatus() {
    if (status == null || SecureStore.token(this) == null) return;
    Events.IO.execute(
        () -> {
          int queued = QueueDb.get(this).events().count();
          var state = getSharedPreferences("state", 0);
          String text =
              (Consent.active(this) ? "MONITORING ENABLED" : "MONITORING PAUSED")
                  + "\n"
                  + (Collectors.connected(this)
                      ? "Internet connected"
                      : "Offline — local queue only")
                  + "\nPending encrypted events: "
                  + queued
                  + "\nLast sync: "
                  + (state.getLong("lastSync", 0) == 0
                      ? "Never"
                      : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                          .format(new Date(state.getLong("lastSync", 0))))
                  + "\nUsage access: "
                  + Consent.usage(this)
                  + " · Notification access: "
                  + Consent.notifications(this)
                  + "\nLocation permission: "
                  + Consent.location(this)
                  + " · Call state permission: "
                  + Consent.granted(this, Manifest.permission.READ_PHONE_STATE)
                  + "\n"
                  + state.getString("error", "");
          runOnUiThread(
              () -> {
                if (status != null) status.setText(text);
              });
        });
  }

  void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }
}

package com.familyguard.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.time.LocalTime;
import java.util.concurrent.*;
import org.json.JSONObject;

public class ParentActivity extends Activity {
  final ExecutorService io = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  TextView state;
  final Runnable refresh =
      new Runnable() {
        public void run() {
          update();
          handler.postDelayed(this, 3000);
        }
      };

  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    ParentNotifications.channels(this);
    render();
  }

  protected void onResume() {
    super.onResume();
    handler.post(refresh);
  }

  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  protected void onDestroy() {
    handler.removeCallbacks(refresh);
    io.shutdown();
    super.onDestroy();
  }

  void render() {
    Ui.page(
        this,
        "FamilyGuard Parent",
        "Your one parent phone. Visible, permission-based safety alerts.");
    Ui.text(
        this,
        "Live alerts use your own Spring Boot server. Android can delay or stop background"
            + " networking. A visible connection notification stays on while the receiver runs;"
            + " force stop, reboot, Doze, lost internet and sleeping Render services can interrupt"
            + " delivery.");
    String token;
    try {
      token = ParentStore.token(this);
    } catch (RuntimeException error) {
      token = null;
    }
    if (token == null) {
      registration();
      return;
    }
    state = Ui.text(this, "Checking receiver status…");
    Ui.button(this, "Allow Android notifications", this::requestNotifications);
    Ui.button(
        this,
        "Start live alerts",
        () -> {
          if (!ParentNotifications.visible(this)) {
            requestNotifications();
            toast(
                "Allow notifications and the Live alert connection channel, then tap Start again.");
            return;
          }
          try {
            startForegroundService(new Intent(this, ParentReceiverService.class));
          } catch (RuntimeException error) {
            toast("Android blocked service startup. Keep this screen open and retry.");
          }
          update();
        });
    Ui.button(
        this,
        "Stop live alerts",
        () -> {
          stopService(new Intent(this, ParentReceiverService.class));
          update();
        });
    Ui.button(
        this,
        "Open parent web dashboard",
        () -> startActivity(new Intent(this, AdminActivity.class)));
    var p = ParentStore.preferences(this);
    Ui.text(
        this, "Notification categories (child sharing permissions must also allow the activity):");
    for (String category : new String[] {"apps", "notifications", "calls"}) {
      String label =
          switch (category) {
            case "apps" -> "Installed / uninstalled applications";
            case "notifications" -> "Selected app notification event counts";
            default -> "Supported incoming cellular call activity";
          };
      Ui.check(
          this,
          label,
          p.getBoolean("notify-" + category, !category.equals("notifications")),
          (view, checked) -> p.edit().putBoolean("notify-" + category, checked).commit());
    }
    Ui.check(
        this,
        "Enable quiet hours in this phone's time zone",
        p.getBoolean("quiet", false),
        (view, enabled) -> p.edit().putBoolean("quiet", enabled).commit());
    EditText from = Ui.input(this, "Quiet hours start (HH:mm)");
    from.setText(p.getString("quiet-start", "22:00"));
    EditText until = Ui.input(this, "Quiet hours end (HH:mm)");
    until.setText(p.getString("quiet-end", "07:00"));
    Ui.button(
        this,
        "Save quiet hours",
        () -> {
          try {
            String start = LocalTime.parse(from.getText().toString()).toString();
            String end = LocalTime.parse(until.getText().toString()).toString();
            p.edit().putString("quiet-start", start).putString("quiet-end", end).commit();
            toast("Quiet hours saved. Equal start/end times disable the quiet window.");
          } catch (RuntimeException error) {
            toast("Use valid 24-hour times, such as 22:00 and 07:00.");
          }
        });
    Ui.text(
        this,
        "Quiet or disabled categories are retained on the dashboard and consumed without a phone"
            + " alert. Notification events contain application, type and timestamp only. No private"
            + " message bodies, senders, SMS content or call audio are forwarded.");
    Ui.button(
        this,
        "Remove this parent phone",
        () ->
            Ui.confirm(
                this,
                "Revoke this receiver and clear its local alert cache? The parent account and child"
                    + " pairing stay unchanged.",
                () -> {
                  stopService(new Intent(this, ParentReceiverService.class));
                  io.execute(
                      () -> {
                        try {
                          var response =
                              Api.request(
                                  "DELETE",
                                  "/api/parent-device/registration",
                                  ParentStore.token(this),
                                  null);
                          if (response.code() != 204
                              && response.code() != 401
                              && response.code() != 403) throw new IllegalStateException();
                          ParentStore.clear(this);
                          ParentInboxDb.get(this).events().clear();
                          ParentNotifications.clearEvents(this);
                          runOnUiThread(this::render);
                        } catch (Exception error) {
                          runOnUiThread(
                              () ->
                                  toast(
                                      "Could not revoke on the server. Retry when connected, or"
                                          + " revoke the phone in web Settings."));
                        }
                      });
                }));
    update();
  }

  void registration() {
    state = null;
    Ui.text(
        this,
        "Sign in with the existing Parent Admin account to register this phone for alerts. Only one"
            + " parent receiver can be registered. This does not create another parent account.");
    EditText email = Ui.input(this, "Parent Admin email");
    email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    EditText password = Ui.input(this, "Parent Admin password");
    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditText name = Ui.input(this, "Parent phone name");
    name.setText(Build.MANUFACTURER + " " + Build.MODEL);
    CheckBox replace =
        Ui.check(
            this,
            "Replace the previously registered parent phone and revoke its receiver access",
            false,
            (button, checked) -> {});
    Ui.text(
        this,
        "Registration starts alert delivery from now. Replacement revokes the old phone; dashboard"
            + " history is preserved. The receiver credential expires after 30 days and requires"
            + " signing in again.");
    Button submit = Ui.button(this, "Sign in and register this phone", () -> {});
    submit.setOnClickListener(
        view -> {
          String address = email.getText().toString().trim(),
              secret = password.getText().toString(),
              label = name.getText().toString().trim();
          if (address.isBlank() || secret.isBlank() || label.isBlank() || label.length() > 80) {
            toast("Enter the parent email, password and a phone name up to 80 characters.");
            return;
          }
          boolean replacing = replace.isChecked();
          Runnable register =
              () -> {
                submit.setEnabled(false);
                password.setText("");
                io.execute(
                    () -> {
                      String error;
                      try {
                        var response =
                            Api.post(
                                "/api/parent-device/register",
                                null,
                                new JSONObject()
                                    .put("email", address)
                                    .put("password", secret)
                                    .put("deviceName", label)
                                    .put("replace", replacing));
                        if (response.code() == 200) {
                          ParentStore.save(
                              this,
                              response.body().getString("receiverToken"),
                              response.body().getString("expiresAt"),
                              label);
                          runOnUiThread(this::render);
                          return;
                        }
                        error =
                            switch (response.code()) {
                              case 401 -> "Invalid parent credentials.";
                              case 409 ->
                                  "A parent phone is already registered. Confirm replacement to"
                                      + " change it.";
                              case 404 ->
                                  "Deploy the updated Spring Boot backend before registering this"
                                      + " receiver.";
                              case 429 -> "Too many sign-in attempts. Wait a minute and retry.";
                              default ->
                                  "The server could not register this phone. Check the backend and"
                                      + " retry.";
                            };
                      } catch (Exception failure) {
                        error =
                            "Could not reach the secure server. Check internet and Render"
                                + " availability.";
                      }
                      String message = error;
                      runOnUiThread(
                          () -> {
                            submit.setEnabled(true);
                            toast(message);
                          });
                    });
              };
          if (replacing)
            Ui.confirm(
                this,
                "Revoke the old parent receiver and start delivery on this phone from now?",
                register);
          else register.run();
        });
    Ui.button(
        this,
        "Open parent web dashboard",
        () -> startActivity(new Intent(this, AdminActivity.class)));
  }

  void update() {
    if (state == null) return;
    var p = ParentStore.preferences(this);
    if (!p.contains("credential")) {
      render();
      return;
    }
    long contact = p.getLong("last-contact", 0);
    state.setText(
        "Receiver: "
            + (ParentReceiverService.running ? p.getString("status", "Connecting…") : "Stopped")
            + "\nNotification permission: "
            + (ParentNotifications.visible(this) ? "Available" : "Disabled")
            + "\nLast parent catch-up: "
            + (contact == 0 ? "Never" : new java.util.Date(contact).toString())
            + "\nCredential expires: "
            + p.getString("expires", "Not registered"));
  }

  void requestNotifications() {
    if (Build.VERSION.SDK_INT >= 33
        && !Consent.granted(this, Manifest.permission.POST_NOTIFICATIONS))
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 31);
    else
      startActivity(
          new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
  }

  void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}

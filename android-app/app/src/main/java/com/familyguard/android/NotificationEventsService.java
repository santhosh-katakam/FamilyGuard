package com.familyguard.android;

import android.app.Notification;
import android.service.notification.*;
import java.util.Set;

public class NotificationEventsService extends NotificationListenerService {
  public void onNotificationPosted(StatusBarNotification sbn) {
    if (!Consent.allowed(this, "notifications")) return;
    String pkg = sbn.getPackageName();
    Set<String> selected =
        getSharedPreferences("consent", 0).getStringSet("selectedApps", Set.of());
    if (!selected.contains(pkg)
        || pkg.equals(getPackageName())
        || (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
    // Deliberately never read Notification.extras, text, people, titles or actions.
    Events.add(
        this,
        "notifications",
        "NOTIFICATION",
        pkg,
        Events.data("count", 1),
        "notification:" + sbn.getKey() + ":" + sbn.getPostTime());
  }

  public void onListenerConnected() {
    Collectors.permission(
        this,
        "notifications",
        Consent.allowed(this, "notifications") && Consent.notifications(this));
  }

  public void onListenerDisconnected() {
    Collectors.permission(this, "notifications", false);
  }
}

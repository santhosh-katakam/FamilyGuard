package com.familyguard.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.time.*;
import java.util.*;
import org.json.JSONObject;

final class ParentNotifications {
  static final String CONNECTION = "parent-live-connection";
  static final int CONNECTION_ID = 51;

  static void channels(Context c) {
    NotificationManager m = c.getSystemService(NotificationManager.class);
    m.createNotificationChannel(
        new NotificationChannel(
            CONNECTION, "Live alert connection", NotificationManager.IMPORTANCE_LOW));
    for (String[] channel :
        new String[][] {
          {"apps", "Application changes"},
          {"notifications", "Selected notification activity"},
          {"calls", "Incoming call activity"}
        })
      m.createNotificationChannel(
          new NotificationChannel(
              "parent-" + channel[0], channel[1], NotificationManager.IMPORTANCE_DEFAULT));
  }

  static boolean visible(Context c) {
    var channel = c.getSystemService(NotificationManager.class).getNotificationChannel(CONNECTION);
    return NotificationManagerCompat.from(c).areNotificationsEnabled()
        && (Build.VERSION.SDK_INT < 33
            || Consent.granted(c, Manifest.permission.POST_NOTIFICATIONS))
        && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
  }

  static PendingIntent open(Context c) {
    return PendingIntent.getActivity(
        c,
        51,
        new Intent(c, ParentActivity.class),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  static Notification connection(Context c, String status) {
    PendingIntent stop =
        PendingIntent.getService(
            c,
            52,
            new Intent(c, ParentReceiverService.class).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new NotificationCompat.Builder(c, CONNECTION)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle("FamilyGuard live alert receiver")
        .setContentText(status)
        .setContentIntent(open(c))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(0, "Stop live alerts", stop)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build();
  }

  // Reconstruct an allowlisted metadata object; never display arbitrary server text.
  static JSONObject metadata(JSONObject input) throws Exception {
    String category = input.getString("category"), kind = input.getString("kind");
    boolean valid =
        category.equals("apps") && Set.of("INSTALLED", "REMOVED").contains(kind)
            || category.equals("notifications") && kind.equals("NOTIFICATION")
            || category.equals("calls") && kind.equals("INCOMING_CALL");
    if (!valid || input.getLong("sequence") < 1)
      throw new IllegalArgumentException("Invalid alert metadata");
    String id = UUID.fromString(input.getString("id")).toString();
    String pkg = category.equals("calls") ? "" : input.getString("packageName");
    if (!category.equals("calls") && !pkg.matches("[A-Za-z0-9_.]{1,255}"))
      throw new IllegalArgumentException("Invalid application");
    String occurred = Instant.parse(input.getString("occurredAt")).toString();
    return new JSONObject()
        .put("id", id)
        .put("sequence", input.getLong("sequence"))
        .put("category", category)
        .put("kind", kind)
        .put("packageName", pkg)
        .put("occurredAt", occurred);
  }

  static boolean post(Context c, String id, JSONObject event) throws Exception {
    if (!visible(c)) return false;
    String category = event.getString("category");
    var p = ParentStore.preferences(c);
    if (!p.getBoolean("notify-" + category, !category.equals("notifications"))
        || QuietHours.contains(
            p.getBoolean("quiet", false),
            p.getString("quiet-start", "22:00"),
            p.getString("quiet-end", "07:00"),
            LocalTime.now())) return true;
    NotificationChannel channel =
        c.getSystemService(NotificationManager.class).getNotificationChannel("parent-" + category);
    if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)
      return true;
    String title =
        switch (event.getString("kind")) {
          case "INSTALLED" -> "Application installed";
          case "REMOVED" -> "Application uninstalled";
          case "INCOMING_CALL" -> "Incoming cellular call activity";
          default -> "Selected application notification activity";
        };
    String pkg = event.getString("packageName");
    String source =
        switch (pkg) {
          case "com.whatsapp" -> "WhatsApp";
          case "com.instagram.android" -> "Instagram";
          case "com.snapchat.android" -> "Snapchat";
          case "org.telegram.messenger" -> "Telegram";
          default -> pkg;
        };
    String timestamp =
        java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(event.getString("occurredAt")));
    String text = (source.isEmpty() ? "Call-state event" : source) + " · " + timestamp;
    Notification publicVersion =
        new NotificationCompat.Builder(c, "parent-" + category)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("FamilyGuard safety activity")
            .setContentText("Open FamilyGuard to review.")
            .build();
    Notification notification =
        new NotificationCompat.Builder(c, "parent-" + category)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(c))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build();
    try {
      c.getSystemService(NotificationManager.class).notify("parent-event:" + id, 1, notification);
      return true;
    } catch (SecurityException e) {
      return false;
    }
  }

  static void clearEvents(Context c) {
    NotificationManager m = c.getSystemService(NotificationManager.class);
    for (var n : m.getActiveNotifications())
      if (n.getTag() != null && n.getTag().startsWith("parent-event:"))
        m.cancel(n.getTag(), n.getId());
  }
}

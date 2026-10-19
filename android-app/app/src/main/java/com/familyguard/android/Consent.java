package com.familyguard.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Process;
import androidx.core.app.NotificationManagerCompat;

final class Consent {
  static boolean noticeVisible(Context c) {
    NotificationChannel channel =
        c.getSystemService(NotificationManager.class).getNotificationChannel("supervision");
    return NotificationManagerCompat.from(c).areNotificationsEnabled()
        && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
  }

  static boolean active(Context c) {
    return c.getSharedPreferences("consent", 0).getBoolean("monitoring", false)
        && SecureStore.token(c) != null
        && noticeVisible(c);
  }

  static boolean allowed(Context c, String key) {
    return active(c)
        && c.getSharedPreferences("consent", 0).getBoolean(key, false)
        && c.getSharedPreferences("remote", 0).getBoolean(key, true);
  }

  static boolean granted(Context c, String permission) {
    return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean usage(Context c) {
    AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
    return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
        == AppOpsManager.MODE_ALLOWED;
  }

  static boolean notifications(Context c) {
    return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
  }

  static boolean location(Context c) {
    return granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
        || granted(c, Manifest.permission.ACCESS_FINE_LOCATION);
  }

  static void pause(Context c) {
    MonitorNotice.hide(c);
    c.getSharedPreferences("consent", 0)
        .edit()
        .putBoolean("monitoring", false)
        .putBoolean("location", false)
        .commit();
    c.stopService(new Intent(c, LocationService.class));
    Events.IO.execute(() -> QueueDb.get(c).events().clear());
    Events.add(
        c,
        "permissions",
        "PERMISSION",
        null,
        Events.data("permission", "monitoring", "enabled", false),
        null);
    Events.add(
        c,
        "status",
        "STATUS",
        null,
        Events.data(
            "battery",
            Collectors.battery(c),
            "monitoring",
            false,
            "connected",
            Collectors.connected(c)),
        null);
  }

  static void reset(Context c) {
    MonitorNotice.hide(c);
    c.getSharedPreferences("consent", 0).edit().clear().commit();
    c.getSharedPreferences("apps", 0).edit().clear().commit();
    c.getSharedPreferences("permissions", 0).edit().clear().commit();
    c.getSharedPreferences("remote", 0).edit().clear().commit();
    SecureStore.clear(c);
    c.stopService(new Intent(c, LocationService.class));
    QueueDb.get(c).events().clear();
  }
}

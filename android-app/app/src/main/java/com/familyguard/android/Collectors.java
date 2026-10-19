package com.familyguard.android;

import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import java.time.*;
import java.util.*;

final class Collectors {
  static int battery(Context c) {
    Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return i == null
        ? 0
        : Math.max(
            0,
            Math.min(
                100,
                i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    * 100
                    / Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))));
  }

  static boolean connected(Context c) {
    ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
    return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }

  static void collect(Context c) {
    if (SecureStore.token(c) == null) return;
    if (Consent.active(c)) MonitorNotice.show(c);
    else MonitorNotice.hide(c);
    Events.add(
        c,
        "status",
        "STATUS",
        null,
        Events.data(
            "battery", battery(c), "monitoring", Consent.active(c), "connected", connected(c)),
        null);
    permission(c, "monitoring", Consent.active(c));
    permission(c, "usage", Consent.allowed(c, "usage") && Consent.usage(c));
    permission(c, "notifications", Consent.allowed(c, "notifications") && Consent.notifications(c));
    permission(c, "location", Consent.allowed(c, "location") && Consent.location(c));
    permission(
        c,
        "calls",
        Consent.allowed(c, "calls")
            && Consent.granted(c, android.Manifest.permission.READ_PHONE_STATE));
    permission(c, "apps", Consent.allowed(c, "apps"));
    if (Consent.allowed(c, "apps")) apps(c);
    if (Consent.allowed(c, "usage") && Consent.usage(c)) usage(c);
  }

  static void permission(Context c, String name, boolean enabled) {
    var state = c.getSharedPreferences("permissions", 0);
    boolean changed = !state.contains(name) || state.getBoolean(name, false) != enabled;
    state.edit().putBoolean(name, enabled).commit();
    Events.add(
        c,
        "permissions",
        "PERMISSION",
        null,
        Events.data("permission", name, "enabled", enabled),
        changed ? null : "permission:" + LocalDate.now() + ":" + name + ":" + enabled);
    if (!enabled && !name.equals("monitoring"))
      Events.IO.execute(() -> QueueDb.get(c).events().clearCategory(name));
  }

  static void apps(Context c) {
    PackageManager pm = c.getPackageManager();
    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    Set<String> current = new HashSet<>();
    for (ResolveInfo info : pm.queryIntentActivities(intent, 0))
      current.add(info.activityInfo.packageName);
    var state = c.getSharedPreferences("apps", 0);
    Set<String> old = new HashSet<>(state.getStringSet("known", Set.of()));
    for (String pkg : current) {
      if (!old.contains(pkg))
        Events.add(
            c,
            "apps",
            state.contains("known") ? "INSTALLED" : "SNAPSHOT",
            pkg,
            Events.data("label", label(c, pkg)),
            null);
      else
        Events.add(
            c,
            "apps",
            "SNAPSHOT",
            pkg,
            Events.data("label", label(c, pkg)),
            "inventory:" + LocalDate.now() + ":" + pkg);
    }
    for (String pkg : old)
      if (!current.contains(pkg))
        Events.add(c, "apps", "REMOVED", pkg, Events.data("label", pkg), null);
    state.edit().putStringSet("known", current).commit();
  }

  static String label(Context c, String pkg) {
    try {
      return c.getPackageManager()
          .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
          .toString();
    } catch (Exception e) {
      return pkg;
    }
  }

  static void usage(Context c) {
    UsageStatsManager manager = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
    LocalDate today = LocalDate.now();
    for (int days = 1; days >= 0; days--) {
      LocalDate day = today.minusDays(days);
      long start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
          end =
              Math.min(
                  System.currentTimeMillis(),
                  day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
      for (var entry : manager.queryAndAggregateUsageStats(start, end).entrySet()) {
        long duration = Math.min(86400000, entry.getValue().getTotalTimeInForeground());
        if (duration > 0)
          Events.add(
              c,
              "usage",
              "DAILY_USAGE",
              entry.getKey(),
              Events.data("day", day.toString(), "durationMs", duration),
              "usage:" + day + ":" + entry.getKey() + ":" + duration);
      }
    }
  }
}

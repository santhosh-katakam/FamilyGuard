package com.familyguard.android;

import android.content.*;

public class PackageReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    if (i == null || i.getAction() == null) return;
    if ((Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())
            || Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()))
        && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
    if (!Consent.allowed(c, "apps")) return;
    PendingResult pending = goAsync();
    Events.IO.execute(
        () -> {
          try {
            if (Intent.ACTION_PACKAGE_REPLACED.equals(i.getAction()) && i.getData() != null) {
              String pkg = i.getData().getSchemeSpecificPart();
              try {
                long version = c.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime;
                Events.add(
                    c,
                    "apps",
                    "UPDATED",
                    pkg,
                    Events.data("label", Collectors.label(c, pkg)),
                    "updated:" + pkg + ":" + version);
              } catch (Exception ignored) {
              }
            }
            Collectors.apps(c);
          } finally {
            pending.finish();
          }
        });
  }
}

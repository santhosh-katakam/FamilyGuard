package com.familyguard.android;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;

final class MonitorNotice {
  static void show(Context c) {
    NotificationManager manager = c.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "supervision", "Visible family supervision", NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            c,
            10,
            new Intent(c, ChildActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    try {
      manager.notify(
          43,
          new NotificationCompat.Builder(c, "supervision")
              .setSmallIcon(R.drawable.ic_shield)
              .setContentTitle("FamilyGuard supervision is enabled")
              .setContentText(
                  "Approved activity is shared with your parent. Tap to review permissions or"
                      + " pause.")
              .setContentIntent(open)
              .setOngoing(true)
              .build());
    } catch (SecurityException ignored) {
    }
  }

  static void hide(Context c) {
    c.getSystemService(NotificationManager.class).cancel(43);
  }
}

package com.familyguard.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class LocationService extends Service implements LocationListener {
  LocationManager manager;

  public void onCreate() {
    super.onCreate();
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "location", "Visible location sharing", NotificationManager.IMPORTANCE_LOW));
  }

  public int onStartCommand(Intent i, int flags, int startId) {
    if (i != null && "STOP".equals(i.getAction())) {
      getSharedPreferences("consent", 0).edit().putBoolean("location", false).commit();
      Collectors.permission(this, "location", false);
      Events.IO.execute(() -> QueueDb.get(this).events().clearCategory("location"));
      stopSelf();
      return START_NOT_STICKY;
    }
    if (!Consent.allowed(this, "location") || !Consent.location(this)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    Intent open = new Intent(this, ChildActivity.class);
    PendingIntent content =
        PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, LocationService.class).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification =
        new NotificationCompat.Builder(this, "location")
            .setSmallIcon(com.familyguard.android.R.drawable.ic_shield)
            .setContentTitle("FamilyGuard location sharing is on")
            .setContentText("Your location is shared with your parent. Tap to review or stop.")
            .setContentIntent(content)
            .setOngoing(true)
            .addAction(0, "Stop sharing", stop)
            .build();
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    else startForeground(42, notification);
    manager = (LocationManager) getSystemService(LOCATION_SERVICE);
    try {
      if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
          && Consent.granted(this, android.Manifest.permission.ACCESS_FINE_LOCATION))
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 50, this);
      if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 50, this);
    } catch (SecurityException e) {
      Collectors.permission(this, "location", false);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  public void onLocationChanged(Location location) {
    if (!Consent.allowed(this, "location") || !Consent.location(this)) {
      stopSelf();
      return;
    }
    if (System.currentTimeMillis() - location.getTime() > 120000 || !location.hasAccuracy()) return;
    Events.add(
        this,
        "location",
        "LOCATION",
        null,
        Events.data(
            "latitude",
            location.getLatitude(),
            "longitude",
            location.getLongitude(),
            "accuracy",
            location.getAccuracy()),
        null);
  }

  public void onProviderDisabled(String provider) {
    Collectors.permission(this, "location", false);
  }

  public void onProviderEnabled(String provider) {}

  public void onStatusChanged(String provider, int status, Bundle extras) {}

  public void onDestroy() {
    if (manager != null) manager.removeUpdates(this);
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  public IBinder onBind(Intent i) {
    return null;
  }
}

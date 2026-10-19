package com.familyguard.android;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;

public class FamilyGuardApp extends Application {
  public void onCreate() {
    super.onCreate();
    if (BuildConfig.APP_ROLE.equals("parent")) return;
    // Package-added/replaced manifest broadcasts are restricted on modern Android.
    // This observer works while our process is alive; periodic inventory reconciles later.
    IntentFilter packages = new IntentFilter();
    packages.addAction(Intent.ACTION_PACKAGE_ADDED);
    packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
    packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
    packages.addDataScheme("package");
    ContextCompat.registerReceiver(
        this, new PackageReceiver(), packages, ContextCompat.RECEIVER_NOT_EXPORTED);
  }
}

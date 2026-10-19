package com.familyguard.android;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoleManifestTest {
  @Test
  public void enabledServicesHaveTheirCorrectForegroundTypes() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PackageManager pm = c.getPackageManager();
    if (!BuildConfig.APP_ROLE.equals("parent")) {
      ServiceInfo location = pm.getServiceInfo(new ComponentName(c, LocationService.class), 0);
      if (Build.VERSION.SDK_INT >= 29)
        assertTrue(
            (location.getForegroundServiceType() & ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                != 0);
    } else {
      try {
        pm.getServiceInfo(new ComponentName(c, LocationService.class), 0);
        fail("Parent APK must not register child location service");
      } catch (PackageManager.NameNotFoundException expected) {
      }
    }
    if (!BuildConfig.APP_ROLE.equals("child")) {
      ServiceInfo receiver =
          pm.getServiceInfo(new ComponentName(c, ParentReceiverService.class), 0);
      assertFalse(receiver.exported);
      if (Build.VERSION.SDK_INT >= 34)
        assertTrue(
            (receiver.getForegroundServiceType() & ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                != 0);
    } else {
      try {
        pm.getServiceInfo(new ComponentName(c, ParentReceiverService.class), 0);
        fail("Child APK must not register parent receiver");
      } catch (PackageManager.NameNotFoundException expected) {
      }
    }
  }
}

package com.familyguard.android;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ParentInboxTest {
  ParentInboxDb db;

  @Before
  public void open() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    db = Room.inMemoryDatabaseBuilder(c, ParentInboxDb.class).build();
  }

  @After
  public void close() {
    db.close();
  }

  @Test
  public void encryptedInboxDeduplicatesReplayedEventsBeforeAcknowledgment() {
    var row = new ParentInboxEvent();
    row.id = "same-event";
    row.sequence = 12;
    row.receivedAt = 1000;
    row.encrypted = SecureStore.encrypt("{\"packageName\":\"com.whatsapp\"}");
    assertFalse(row.encrypted.contains("whatsapp"));
    db.events().insert(row);
    db.events().insert(row);
    assertEquals(1, db.events().count());
    assertEquals(1, db.events().pending().size());
    db.events().notified(row.id);
    db.events().insert(row);
    assertEquals(0, db.events().pending().size());
    db.events().expire(1001);
    assertEquals(0, db.events().count());
  }

  @Test
  public void notificationAllowlistDiscardsBodiesSendersAndCallNumbers() throws Exception {
    JSONObject raw =
        new JSONObject()
            .put("id", java.util.UUID.randomUUID().toString())
            .put("sequence", 1)
            .put("category", "notifications")
            .put("kind", "NOTIFICATION")
            .put("packageName", "com.whatsapp")
            .put("occurredAt", java.time.Instant.now().toString())
            .put("body", "PRIVATE BODY")
            .put("sender", "PRIVATE SENDER")
            .put("phoneNumber", "PRIVATE NUMBER");
    JSONObject safe = ParentNotifications.metadata(raw);
    assertFalse(safe.toString().contains("PRIVATE"));
    assertEquals(6, safe.length());
    raw.put("category", "calls").put("kind", "INCOMING_CALL").put("packageName", "PRIVATE NUMBER");
    assertEquals("", ParentNotifications.metadata(raw).getString("packageName"));
  }

  @Test
  public void parentFlavorDoesNotRequestChildMonitoringPermissions() throws Exception {
    if (!BuildConfig.APP_ROLE.equals("parent")) return;
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    var info =
        c.getPackageManager()
            .getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
    var requested = java.util.Set.of(info.requestedPermissions);
    assertFalse(requested.contains(android.Manifest.permission.READ_PHONE_STATE));
    assertFalse(requested.contains(android.Manifest.permission.PACKAGE_USAGE_STATS));
    assertFalse(requested.contains(android.Manifest.permission.ACCESS_FINE_LOCATION));
    assertFalse(requested.contains(android.Manifest.permission.RECORD_AUDIO));
  }
}

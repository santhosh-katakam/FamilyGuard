package com.familyguard.android;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocalQueueTest {
  QueueDb db;
  Context context;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    db = Room.inMemoryDatabaseBuilder(context, QueueDb.class).build();
  }

  @After
  public void close() {
    db.close();
  }

  @Test
  public void duplicatesAreIgnoredAndOnlyAcknowledgedRowsRemoved() {
    QueuedEvent a = event("a", "notifications"), b = event("b", "location");
    db.events().insert(a);
    db.events().insert(a);
    db.events().insert(b);
    assertEquals(2, db.events().count());
    db.events().acknowledge(List.of("a"));
    assertEquals("b", db.events().batch().get(0).id);
    db.events().clearCategory("location");
    assertEquals(0, db.events().count());
  }

  @Test
  public void payloadCiphertextUsesRandomIvAndRoundTrips() {
    String a = SecureStore.encrypt("{\"latitude\":12.34}"),
        b = SecureStore.encrypt("{\"latitude\":12.34}");
    assertNotEquals(a, b);
    assertFalse(a.contains("latitude"));
    assertEquals("{\"latitude\":12.34}", SecureStore.decrypt(a));
    QueuedEvent event = event("secure", "location");
    event.encrypted = a;
    db.events().insert(event);
    assertEquals(a, db.events().batch().get(0).encrypted);
  }

  @Test
  public void retentionRemovesOnlyExpiredRows() {
    QueuedEvent old = event("old", "usage"), recent = event("recent", "usage");
    old.createdAt = 1;
    recent.createdAt = 1000;
    db.events().insert(old);
    db.events().insert(recent);
    db.events().expire(500);
    assertEquals(1, db.events().count());
    assertEquals("recent", db.events().batch().get(0).id);
  }

  QueuedEvent event(String id, String category) {
    QueuedEvent e = new QueuedEvent();
    e.id = id;
    e.category = category;
    e.createdAt = System.currentTimeMillis();
    e.encrypted = SecureStore.encrypt("{}");
    return e;
  }
}

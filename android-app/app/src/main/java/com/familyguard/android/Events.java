package com.familyguard.android;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.*;

final class Events {
  static final ExecutorService IO = Executors.newSingleThreadExecutor();

  static void add(
      Context context, String category, String kind, String pkg, JSONObject data, String stableId) {
    Context c = context.getApplicationContext();
    IO.execute(
        () -> {
          try {
            if (SecureStore.token(c) == null) return;
            if (!category.equals("permissions")
                && !category.equals("status")
                && !Consent.allowed(c, category)) return;
            JSONObject e =
                new JSONObject()
                    .put(
                        "id",
                        stableId == null
                            ? UUID.randomUUID().toString()
                            : UUID.nameUUIDFromBytes(stableId.getBytes(StandardCharsets.UTF_8))
                                .toString())
                    .put("category", category)
                    .put("kind", kind)
                    .put("occurredAt", Instant.now().toString())
                    .put("data", data);
            if (pkg != null) e.put("packageName", pkg);
            QueuedEvent row = new QueuedEvent();
            row.id = e.getString("id");
            row.category = category;
            row.createdAt = System.currentTimeMillis();
            row.encrypted = SecureStore.encrypt(e.toString());
            QueueDb.get(c).events().insert(row);
            QueueDb.get(c).events().trim();
            SyncWorker.scheduleNow(c);
          } catch (Exception ex) {
            c.getSharedPreferences("state", 0)
                .edit()
                .putString("error", "Could not store event securely. Open FamilyGuard.")
                .apply();
          }
        });
  }

  static JSONObject data(Object... pairs) {
    JSONObject o = new JSONObject();
    try {
      for (int i = 0; i < pairs.length; i += 2) o.put((String) pairs[i], pairs[i + 1]);
      return o;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }
}

package com.familyguard.android;

import android.content.*;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class SyncWorker extends Worker {
  public SyncWorker(@NonNull Context c, @NonNull WorkerParameters p) {
    super(c, p);
  }

  static Constraints network() {
    return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  }

  static void schedule(Context c) {
    CollectWorker.schedule(c);
    WorkManager.getInstance(c)
        .enqueueUniquePeriodicWork(
            "familyguard-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(network())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build());
    scheduleNow(c);
  }

  static void scheduleNow(Context c) {
    WorkManager.getInstance(c)
        .enqueueUniqueWork(
            "familyguard-sync",
            ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(network())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build());
  }

  @NonNull
  public Result doWork() {
    Context c = getApplicationContext();
    try {
      String token = SecureStore.token(c);
      if (token == null) return Result.success();
      Collectors.collect(c);
      Events.IO.submit(() -> {}).get(30, TimeUnit.SECONDS);
      QueueDao dao = QueueDb.get(c).events();
      long days = c.getSharedPreferences("remote", 0).getInt("retentionDays", 7);
      dao.expire(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Math.min(days, 7)));
      dao.trim();
      for (int page = 0; page < 20; page++) {
        List<QueuedEvent> batch = dao.batch();
        JSONArray events = new JSONArray();
        for (QueuedEvent event : batch)
          events.put(new JSONObject(SecureStore.decrypt(event.encrypted)));
        Api.Reply response =
            Api.post("/api/device/sync", token, new JSONObject().put("events", events));
        if (response.code() == 401 || response.code() == 403) {
          Consent.reset(c);
          c.getSharedPreferences("state", 0)
              .edit()
              .putString(
                  "error", "Pairing was revoked. Local data cleared. Pair again with your parent.")
              .apply();
          return Result.failure();
        }
        if (response.code() == 429 || response.code() >= 500) return Result.retry();
        if (response.code() != 200) {
          c.getSharedPreferences("state", 0)
              .edit()
              .putString(
                  "error",
                  "Server rejected queued activity ("
                      + response.code()
                      + "). Open FamilyGuard to review or clear the queue.")
              .apply();
          return Result.failure();
        }
        JSONArray ack = response.body().getJSONArray("acknowledged");
        Set<String> sent = new HashSet<>();
        for (var e : batch) sent.add(e.id);
        List<String> confirmed = new ArrayList<>();
        for (int i = 0; i < ack.length(); i++) {
          String id = ack.getString(i);
          if (sent.contains(id)) confirmed.add(id);
        }
        dao.acknowledge(confirmed);
        JSONObject prefs = response.body().getJSONObject("preferences");
        var edit = c.getSharedPreferences("remote", 0).edit();
        for (String key : List.of("apps", "usage", "notifications", "calls", "location")) {
          boolean enabled = prefs.optBoolean(key, true);
          edit.putBoolean(key, enabled);
          if (!enabled) dao.clearCategory(key);
        }
        edit.putInt("retentionDays", prefs.optInt("retentionDays", 7)).commit();
        if (!prefs.optBoolean("location", true))
          c.stopService(new Intent(c, LocationService.class));
        c.getSharedPreferences("state", 0)
            .edit()
            .putLong("lastSync", System.currentTimeMillis())
            .remove("error")
            .apply();
        if (batch.size() < 200) return Result.success();
        if (confirmed.isEmpty()) return Result.retry();
      }
      return Result.retry();
    } catch (Exception e) {
      c.getSharedPreferences("state", 0)
          .edit()
          .putString("error", "Waiting to synchronize. Check internet and server availability.")
          .apply();
      return Result.retry();
    }
  }
}

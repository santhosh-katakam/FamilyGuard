package com.familyguard.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class CollectWorker extends Worker {
  public CollectWorker(@NonNull Context c, @NonNull WorkerParameters p) {
    super(c, p);
  }

  @NonNull
  public Result doWork() {
    try {
      Collectors.collect(getApplicationContext());
      Events.IO.submit(() -> {}).get(30, TimeUnit.SECONDS);
      return Result.success();
    } catch (Exception e) {
      return Result.retry();
    }
  }

  static void schedule(Context c) {
    WorkManager.getInstance(c)
        .enqueueUniquePeriodicWork(
            "familyguard-local-collection",
            ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(CollectWorker.class, 15, TimeUnit.MINUTES).build());
  }
}

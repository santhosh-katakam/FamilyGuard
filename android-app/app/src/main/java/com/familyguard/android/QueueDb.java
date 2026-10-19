package com.familyguard.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.*;
import java.util.List;

@Entity(
    tableName = "events",
    indices = {@Index("createdAt")})
class QueuedEvent {
  @PrimaryKey @NonNull public String id = "";
  public String category;
  public long createdAt;
  public String encrypted;
}

@Dao
interface QueueDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  void insert(QueuedEvent event);

  @Query("SELECT * FROM events ORDER BY createdAt LIMIT 200")
  List<QueuedEvent> batch();

  @Query("DELETE FROM events WHERE id IN (:ids)")
  void acknowledge(List<String> ids);

  @Query("DELETE FROM events")
  void clear();

  @Query("DELETE FROM events WHERE category=:category")
  void clearCategory(String category);

  @Query("DELETE FROM events WHERE createdAt < :cutoff")
  void expire(long cutoff);

  @Query(
      "DELETE FROM events WHERE id IN (SELECT id FROM events ORDER BY createdAt DESC LIMIT -1"
          + " OFFSET 20000)")
  void trim();

  @Query("SELECT COUNT(*) FROM events")
  int count();
}

@Database(
    entities = {QueuedEvent.class},
    version = 1,
    exportSchema = false)
public abstract class QueueDb extends RoomDatabase {
  public abstract QueueDao events();

  private static volatile QueueDb instance;

  static QueueDb get(Context c) {
    if (instance == null)
      synchronized (QueueDb.class) {
        if (instance == null)
          instance =
              Room.databaseBuilder(
                      c.getApplicationContext(), QueueDb.class, "familyguard-events.db")
                  .build();
      }
    return instance;
  }
}

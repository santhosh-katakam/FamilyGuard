package com.familyguard.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.*;
import java.util.List;

@Entity(
    tableName = "parent_inbox",
    indices = {@Index(value = "sequence", unique = true), @Index("receivedAt")})
class ParentInboxEvent {
  @PrimaryKey @NonNull public String id = "";
  public long sequence;
  public long receivedAt;
  public String encrypted;
  public boolean notified;
}

@Dao
interface ParentInboxDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  long insert(ParentInboxEvent event);

  @Query("SELECT * FROM parent_inbox WHERE notified=0 ORDER BY sequence LIMIT 100")
  List<ParentInboxEvent> pending();

  @Query("UPDATE parent_inbox SET notified=1 WHERE id=:id")
  void notified(String id);

  @Query("DELETE FROM parent_inbox WHERE receivedAt < :before")
  void expire(long before);

  @Query(
      "DELETE FROM parent_inbox WHERE id IN (SELECT id FROM parent_inbox ORDER BY sequence DESC"
          + " LIMIT -1 OFFSET 2000)")
  void trim();

  @Query("DELETE FROM parent_inbox")
  void clear();

  @Query("SELECT COUNT(*) FROM parent_inbox")
  int count();
}

@Database(
    entities = {ParentInboxEvent.class},
    version = 1,
    exportSchema = false)
public abstract class ParentInboxDb extends RoomDatabase {
  public abstract ParentInboxDao events();

  private static volatile ParentInboxDb instance;

  static ParentInboxDb get(Context c) {
    if (instance == null)
      synchronized (ParentInboxDb.class) {
        if (instance == null)
          instance =
              Room.databaseBuilder(
                      c.getApplicationContext(), ParentInboxDb.class, "familyguard-parent-inbox.db")
                  .build();
      }
    return instance;
  }
}

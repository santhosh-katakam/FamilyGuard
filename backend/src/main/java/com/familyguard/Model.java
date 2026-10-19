package com.familyguard;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_users")
class AdminUser {
  @Id public Integer id = 1;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  public String refreshHash;
  public Instant refreshExpires;
  public long sessionVersion;
  public boolean enabled = true;
}

@Entity
@Table(name = "child_devices")
class ChildDevice {
  @Id public UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true)
  public boolean singleton = true;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, unique = true)
  public String tokenHash;

  public Instant lastSeen;
  public Integer battery;
  public boolean offlineAlerted;
  public boolean monitoring;
  public Instant createdAt = Instant.now();
}

@Entity
@Table(name = "device_pairings")
class DevicePairing {
  @Id public Integer id = 1;
  public String codeHash;
  public Instant expiresAt;
}

@MappedSuperclass
abstract class EventBase {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID deviceId;

  @Column(nullable = false)
  public Instant occurredAt;

  @Column(nullable = false)
  public Instant receivedAt = Instant.now();

  @Column(nullable = false, length = 32)
  public String kind;

  @Column(length = 255)
  public String packageName;

  @Convert(converter = EncryptedText.class)
  @Column(nullable = false, columnDefinition = "text")
  public String payload;
}

@Entity
@Table(name = "privacy_preferences")
class PrivacyPreferences {
  @Id public Integer id = 1;

  @Convert(converter = EncryptedText.class)
  @Column(nullable = false, columnDefinition = "text")
  public String value =
      "{\"retentionDays\":30,\"locationHistoryDays\":7,\"notifications\":true,\"apps\":true,\"usage\":true,\"calls\":true,\"location\":true,\"alertNotifications\":true}";
}

@Entity
@Table(name = "security_audit_logs")
class SecurityAuditLog {
  @Id public UUID id = UUID.randomUUID();
  public Instant occurredAt = Instant.now();

  @Column(nullable = false)
  public String action;
}

@Entity
@Table(name = "sync_queue")
class SyncReceipt {
  @Id public UUID id;
  public UUID deviceId;
  public Instant receivedAt = Instant.now();
}

@Entity
@Table(name = "alerts")
class Alert {
  @Id public UUID id = UUID.randomUUID();
  public UUID deviceId;
  public Instant occurredAt = Instant.now();
  public String kind;
  public String message;
  public boolean read;
}

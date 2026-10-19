package com.familyguard;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "parent_receivers")
class ParentReceiver {
  @Id public Integer id = 1;
  public Integer adminId = 1;

  @Column(nullable = false, length = 80)
  public String name;

  @Column(nullable = false, unique = true, length = 64)
  public String tokenHash;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant lastSeen;
  public long cursor;
  public long offeredCursor;
}

@Entity
@Table(name = "parent_delivery_events")
class ParentDeliveryEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long sequence;

  @Column(nullable = false, unique = true)
  public UUID eventId;

  @Column(nullable = false)
  public UUID deviceId;

  @Column(nullable = false, length = 32)
  public String category;

  @Column(nullable = false, length = 32)
  public String kind;

  @Column(length = 255)
  public String packageName;

  @Column(nullable = false)
  public Instant occurredAt;

  @Column(nullable = false)
  public Instant receivedAt = Instant.now();
}

interface ParentReceiverRepository extends JpaRepository<ParentReceiver, Integer> {
  Optional<ParentReceiver> findByTokenHash(String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ParentReceiver r where r.id=1")
  Optional<ParentReceiver> locked();
}

interface ParentDeliveryRepository extends JpaRepository<ParentDeliveryEvent, Long> {
  List<ParentDeliveryEvent> findTop100BySequenceGreaterThanOrderBySequenceAsc(long cursor);

  @Query("select coalesce(max(e.sequence),0) from ParentDeliveryEvent e")
  long maximum();

  void deleteBySequenceLessThanEqual(long cursor);

  void deleteByReceivedAtBefore(Instant cutoff);

  @Modifying
  @Query(
      value =
          "DELETE FROM parent_delivery_events WHERE sequence < (SELECT min(sequence) FROM (SELECT"
              + " sequence FROM parent_delivery_events ORDER BY sequence DESC LIMIT 20000) kept)",
      nativeQuery = true)
  int trim();
}

@Service
class ParentDelivery {
  final AdminRepository admins;
  final ParentReceiverRepository receivers;
  final ParentDeliveryRepository deliveries;
  final PrivacyRepository privacy;
  final DeviceRepository devices;
  final AuditRepository audits;
  final BCryptPasswordEncoder passwords;
  final ObjectMapper json;
  final ApplicationEventPublisher publisher;

  ParentDelivery(
      AdminRepository a,
      ParentReceiverRepository r,
      ParentDeliveryRepository d,
      PrivacyRepository p,
      DeviceRepository child,
      AuditRepository audit,
      BCryptPasswordEncoder pw,
      ObjectMapper mapper,
      ApplicationEventPublisher pub) {
    admins = a;
    receivers = r;
    deliveries = d;
    privacy = p;
    devices = child;
    audits = audit;
    passwords = pw;
    json = mapper;
    publisher = pub;
  }

  public Optional<ParentReceiver> authenticate(String hash) {
    if (!admins.findById(1).map(a -> a.enabled).orElse(false)) return Optional.empty();
    return receivers.findByTokenHash(hash).filter(r -> r.expiresAt.isAfter(Instant.now()));
  }

  ParentReceiver locked(String hash) {
    var r =
        receivers.locked().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (!r.tokenHash.equals(hash)
        || !r.expiresAt.isAfter(Instant.now())
        || !admins.findById(1).map(a -> a.enabled).orElse(false))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return r;
  }

  @Transactional
  public Map<String, Object> register(String email, String password, String name, boolean replace) {
    var a = admins.locked().orElseThrow();
    if (!a.enabled
        || !a.email.equalsIgnoreCase(email)
        || !passwords.matches(password, a.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    var existing = receivers.findById(1);
    if (existing.isPresent() && !replace)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "One parent phone is already registered. Confirm replacement to revoke its access.");
    String token = Tokens.random();
    var r = existing.orElseGet(ParentReceiver::new);
    r.name = name.trim();
    r.tokenHash = Tokens.hash(token);
    r.expiresAt = Instant.now().plus(Duration.ofDays(30));
    r.lastSeen = Instant.now();
    r.cursor = deliveries.maximum();
    r.offeredCursor = r.cursor;
    receivers.save(r);
    deliveries.deleteBySequenceLessThanEqual(r.cursor);
    audit(existing.isPresent() ? "PARENT_RECEIVER_REPLACED" : "PARENT_RECEIVER_REGISTERED");
    publisher.publishEvent(new DataChanged());
    return Map.of("receiverToken", token, "expiresAt", r.expiresAt, "deviceName", r.name);
  }

  // Called only for a sanitized, privacy-approved event, in its ingestion transaction.
  public void enqueue(EventBase event, String category, JsonNode safe) {
    String kind =
        switch (category) {
          case "apps" -> Set.of("INSTALLED", "REMOVED").contains(event.kind) ? event.kind : null;
          case "notifications" ->
              enabledCategories().contains("notifications") ? "NOTIFICATION" : null;
          case "calls" -> safe.path("state").asText().equals("RINGING") ? "INCOMING_CALL" : null;
          default -> null;
        };
    if (kind == null || event.occurredAt.isBefore(Instant.now().minus(Duration.ofDays(7)))) return;
    var delivery = new ParentDeliveryEvent();
    delivery.eventId = event.id;
    delivery.deviceId = event.deviceId;
    delivery.category = category;
    delivery.kind = kind;
    delivery.packageName = category.equals("calls") ? null : event.packageName;
    delivery.occurredAt = event.occurredAt;
    deliveries.save(delivery);
  }

  Set<String> enabledCategories() {
    try {
      JsonNode p = json.readTree(privacy.findById(1).orElseThrow().value);
      var allowed = new HashSet<String>();
      for (String category : List.of("apps", "calls", "notifications"))
        if (p.path(category).asBoolean(true)
            && (!category.equals("notifications") || p.path("alertNotifications").asBoolean(true)))
          allowed.add(category);
      return allowed;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Map<String, Object> pending(String hash) {
    var r = locked(hash);
    var page = deliveries.findTop100BySequenceGreaterThanOrderBySequenceAsc(r.cursor);
    Set<String> allowed = enabledCategories();
    Instant cutoff;
    try {
      int days =
          json.readTree(privacy.findById(1).orElseThrow().value).path("retentionDays").asInt(30);
      cutoff = Instant.now().minus(Duration.ofDays(Math.min(days, 7)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    Set<UUID> paired = new HashSet<>(devices.findAll().stream().map(d -> d.id).toList());
    var rows = new ArrayList<Map<String, Object>>();
    long next = r.cursor;
    for (var event : page) {
      next = event.sequence;
      if (!allowed.contains(event.category)
          || !paired.contains(event.deviceId)
          || event.receivedAt.isBefore(cutoff)
          || event.occurredAt.isBefore(cutoff)) continue;
      rows.add(
          Map.of(
              "sequence",
              event.sequence,
              "id",
              event.eventId,
              "category",
              event.category,
              "kind",
              event.kind,
              "packageName",
              Objects.requireNonNullElse(event.packageName, ""),
              "occurredAt",
              event.occurredAt,
              "receivedAt",
              event.receivedAt));
    }
    r.offeredCursor = Math.max(r.offeredCursor, next);
    r.lastSeen = Instant.now();
    receivers.save(r);
    return Map.of("events", rows, "nextCursor", next, "hasMore", page.size() == 100);
  }

  @Transactional
  public void acknowledge(String hash, long cursor) {
    var r = locked(hash);
    if (cursor > r.offeredCursor)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cursor was not offered to this receiver");
    if (cursor <= r.cursor) return;
    r.cursor = cursor;
    receivers.save(r);
    deliveries.deleteBySequenceLessThanEqual(cursor);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> status() {
    return receivers
        .findById(1)
        .<Map<String, Object>>map(
            r ->
                Map.of(
                    "registered",
                    true,
                    "deviceName",
                    r.name,
                    "expiresAt",
                    r.expiresAt,
                    "lastSeen",
                    r.lastSeen == null ? "" : r.lastSeen))
        .orElseGet(() -> Map.of("registered", false));
  }

  @Transactional
  public void revoke() {
    admins.locked().orElseThrow();
    receivers.deleteAllInBatch();
    audit("PARENT_RECEIVER_REVOKED");
    publisher.publishEvent(new DataChanged());
  }

  @Transactional
  public void revokeSelf(String hash) {
    locked(hash);
    receivers.deleteAllInBatch();
    audit("PARENT_RECEIVER_REMOVED_BY_PHONE");
    publisher.publishEvent(new DataChanged());
  }

  public void clearEvents() {
    deliveries.deleteAllInBatch();
  }

  public void eraseReceiver() {
    receivers.deleteAllInBatch();
  }

  public void trimEvents() {
    deliveries.trim();
  }

  public void retainEvents(Instant cutoff) {
    deliveries.deleteByReceivedAtBefore(cutoff);
  }

  void audit(String action) {
    var log = new SecurityAuditLog();
    log.action = action;
    audits.save(log);
  }
}

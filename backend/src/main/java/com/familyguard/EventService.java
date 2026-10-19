package com.familyguard;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class EventService {
  final Map<String, JpaRepository<? extends EventBase, UUID>> repos = new LinkedHashMap<>();
  final DeviceRepository devices;
  final ReceiptRepository receipts;
  final AlertRepository alerts;
  final PrivacyRepository privacy;
  final ObjectMapper json;
  final ApplicationEventPublisher publisher;
  final ParentDelivery delivery;

  EventService(
      DeviceRepository d,
      ReceiptRepository r,
      AlertRepository a,
      PrivacyRepository p,
      ObjectMapper j,
      ApplicationEventPublisher pub,
      PermissionStatusRepository ps,
      InstalledAppEventRepository ia,
      AppUsageLogRepository au,
      NotificationEventRepository ne,
      CallEventRepository ce,
      LocationHistoryRepository lh,
      DeviceStatusRepository ds,
      ParentDelivery parentDelivery) {
    devices = d;
    receipts = r;
    alerts = a;
    privacy = p;
    json = j;
    publisher = pub;
    delivery = parentDelivery;
    repos.put("permissions", ps);
    repos.put("apps", ia);
    repos.put("usage", au);
    repos.put("notifications", ne);
    repos.put("calls", ce);
    repos.put("location", lh);
    repos.put("status", ds);
  }

  record Input(
      @NotNull UUID id,
      @NotBlank String category,
      @NotBlank @Size(max = 32) String kind,
      @NotNull Instant occurredAt,
      @Size(max = 255) String packageName,
      @NotNull JsonNode data) {}

  Map<String, Object> prefs() {
    try {
      return json.readValue(
          privacy.findById(1).orElseThrow().value,
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  DeviceRepository deviceRepository() {
    return devices;
  }

  AlertRepository alertRepository() {
    return alerts;
  }

  ReceiptRepository receiptRepository() {
    return receipts;
  }

  Map<String, JpaRepository<? extends EventBase, UUID>> repositories() {
    return repos;
  }

  void changed() {
    publisher.publishEvent(new DataChanged());
  }

  void resetPrivacy() {
    privacy.save(new PrivacyPreferences());
  }

  boolean enabled(String category) {
    return !Boolean.FALSE.equals(prefs().get(category));
  }

  @Transactional
  Map<String, Object> ingest(UUID deviceId, List<Input> batch) {
    if (batch.size() > 200) throw new IllegalArgumentException("At most 200 events");
    var device =
        devices.locked().stream()
            .filter(d -> d.id.equals(deviceId))
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pairing revoked"));
    var acknowledged = new ArrayList<UUID>();
    for (var input : batch) {
      if (!repos.containsKey(input.category())
          || input.occurredAt().isAfter(Instant.now().plusSeconds(300)))
        throw new IllegalArgumentException("Invalid event category or time");
      if (receipts.existsById(input.id())) {
        acknowledged.add(input.id());
        continue;
      }
      JsonNode safe = sanitize(input);
      var receipt = new SyncReceipt();
      receipt.id = input.id();
      receipt.deviceId = deviceId;
      receipts.save(receipt);
      acknowledged.add(input.id());
      if (!enabled(input.category())
          || input
              .occurredAt()
              .isBefore(
                  Instant.now()
                      .minus(
                          Duration.ofDays(
                              ((Number) prefs().getOrDefault("retentionDays", 30)).intValue()))))
        continue;
      EventBase event =
          switch (input.category()) {
            case "permissions" -> new PermissionStatus();
            case "apps" -> new InstalledAppEvent();
            case "usage" -> new AppUsageLog();
            case "notifications" -> new NotificationEvent();
            case "calls" -> new CallEvent();
            case "location" -> new LocationHistory();
            default -> new DeviceStatus();
          };
      event.id = input.id();
      event.deviceId = deviceId;
      event.kind = input.kind();
      event.packageName = input.packageName();
      event.occurredAt = input.occurredAt();
      event.payload = safe.toString();
      save(input.category(), event);
      delivery.enqueue(event, input.category(), safe);
      boolean recent = input.occurredAt().isAfter(Instant.now().minusSeconds(3600));
      if (input.category().equals("status")) {
        if (safe.has("battery")) device.battery = safe.get("battery").asInt();
        device.monitoring = safe.path("monitoring").asBoolean();
        if (device.battery != null && device.battery <= 15 && recent)
          alert(deviceId, "BATTERY_LOW", "Device battery is low");
      }
      if (recent) {
        switch (input.category()) {
          case "apps" -> {
            if (!input.kind().equals("SNAPSHOT"))
              alert(
                  deviceId,
                  "APP_CHANGE",
                  "Application "
                      + input.kind().toLowerCase(Locale.ROOT)
                      + ": "
                      + Objects.requireNonNullElse(input.packageName(), "unknown"));
          }
          case "permissions" -> {
            if (!safe.path("enabled").asBoolean())
              alert(
                  deviceId,
                  "PERMISSION_REVOKED",
                  "Permission disabled: " + safe.path("permission").asText());
          }
          case "notifications" -> {
            if (enabled("alertNotifications"))
              alert(deviceId, "NOTIFICATION", "Selected application notification event");
          }
          case "location" -> geofence(deviceId, safe);
          case "calls" -> {
            if (safe.path("state").asText().equals("RINGING"))
              alert(deviceId, "INCOMING_CALL", "Supported incoming cellular call event");
          }
        }
      }
    }
    if (device.offlineAlerted) alert(deviceId, "DEVICE_ONLINE", "Device synchronized again");
    device.offlineAlerted = false;
    device.lastSeen = Instant.now();
    devices.save(device);
    delivery.trimEvents();
    publisher.publishEvent(new DataChanged());
    return Map.of(
        "acknowledged", acknowledged, "serverTime", Instant.now(), "preferences", prefs());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  void save(String category, EventBase e) {
    ((JpaRepository) repos.get(category)).save(e);
  }

  JsonNode sanitize(Input in) {
    var result = json.createObjectNode();
    var d = in.data();
    switch (in.category()) {
      case "status" -> {
        int battery = d.path("battery").asInt(-1);
        if (battery < 0 || battery > 100) throw new IllegalArgumentException();
        result.put("battery", battery);
        result.put("monitoring", d.path("monitoring").asBoolean());
        result.put("connected", d.path("connected").asBoolean());
      }
      case "permissions" -> {
        String name = d.path("permission").asText();
        if (!Set.of("usage", "notifications", "location", "calls", "apps", "monitoring")
            .contains(name)) throw new IllegalArgumentException();
        result.put("permission", name);
        result.put("enabled", d.path("enabled").asBoolean());
      }
      case "apps" -> {
        if (!Set.of("INSTALLED", "REMOVED", "UPDATED", "SNAPSHOT").contains(in.kind()))
          throw new IllegalArgumentException();
        result.put(
            "label",
            d.path("label")
                .asText("")
                .substring(0, Math.min(100, d.path("label").asText("").length())));
      }
      case "usage" -> {
        long ms = d.path("durationMs").asLong(-1);
        if (ms < 0 || ms > 86400000) throw new IllegalArgumentException();
        LocalDate.parse(d.path("day").asText());
        result.put("durationMs", ms);
        result.put("day", d.path("day").asText());
      }
      case "notifications" -> {
        result.put("count", 1);
      }
      case "calls" -> {
        String state = d.path("state").asText();
        if (!Set.of("RINGING", "OFFHOOK", "IDLE").contains(state))
          throw new IllegalArgumentException();
        result.put("state", state);
      }
      case "location" -> {
        double lat = d.path("latitude").asDouble(999),
            lon = d.path("longitude").asDouble(999),
            acc = d.path("accuracy").asDouble(-1);
        if (!Double.isFinite(lat)
            || !Double.isFinite(lon)
            || lat < -90
            || lat > 90
            || lon < -180
            || lon > 180
            || !Double.isFinite(acc)
            || acc < 0
            || acc > 100000) throw new IllegalArgumentException();
        result.put("latitude", lat);
        result.put("longitude", lon);
        result.put("accuracy", acc);
      }
    }
    if (Set.of("apps", "usage", "notifications").contains(in.category())
        && (in.packageName() == null || !in.packageName().matches("[A-Za-z0-9_.]{1,255}")))
      throw new IllegalArgumentException();
    return result;
  }

  List<EventBase> list(String category) {
    var r = repos.get(category);
    if (r == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown activity category");
    return switch (category) {
      case "permissions" ->
          new ArrayList<>(((PermissionStatusRepository) r).findTop10000ByOrderByOccurredAtDesc());
      case "apps" ->
          new ArrayList<>(((InstalledAppEventRepository) r).findTop10000ByOrderByOccurredAtDesc());
      case "usage" ->
          new ArrayList<>(((AppUsageLogRepository) r).findTop10000ByOrderByOccurredAtDesc());
      case "notifications" ->
          new ArrayList<>(((NotificationEventRepository) r).findTop10000ByOrderByOccurredAtDesc());
      case "calls" ->
          new ArrayList<>(((CallEventRepository) r).findTop10000ByOrderByOccurredAtDesc());
      case "location" ->
          new ArrayList<>(((LocationHistoryRepository) r).findTop10000ByOrderByOccurredAtDesc());
      default ->
          new ArrayList<>(((DeviceStatusRepository) r).findTop10000ByOrderByOccurredAtDesc());
    };
  }

  Map<String, Object> view(EventBase e) {
    try {
      return Map.of(
          "id",
          e.id,
          "category",
          e.getClass().getSimpleName(),
          "kind",
          e.kind,
          "occurredAt",
          e.occurredAt,
          "packageName",
          Objects.requireNonNullElse(e.packageName, ""),
          "data",
          json.readTree(e.payload));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  void alert(UUID id, String kind, String message) {
    if (alerts.existsByKindAndOccurredAtAfter(kind, Instant.now().minusSeconds(300))) return;
    var a = new Alert();
    a.deviceId = id;
    a.kind = kind;
    a.message = message.length() > 250 ? message.substring(0, 250) : message;
    alerts.save(a);
  }

  void geofence(UUID device, JsonNode location) {
    var p = prefs();
    if (!(p.get("geofence") instanceof Map<?, ?> g)) return;
    double lat = ((Number) g.get("latitude")).doubleValue(),
        lon = ((Number) g.get("longitude")).doubleValue(),
        radius = ((Number) g.get("radius")).doubleValue();
    double a = Math.toRadians(location.get("latitude").asDouble() - lat),
        b = Math.toRadians(location.get("longitude").asDouble() - lon);
    double h =
        Math.pow(Math.sin(a / 2), 2)
            + Math.cos(Math.toRadians(lat))
                * Math.cos(Math.toRadians(location.get("latitude").asDouble()))
                * Math.pow(Math.sin(b / 2), 2);
    double distance = 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
    if (location.get("accuracy").asDouble() > radius) return;
    boolean inside = distance <= radius;
    Object previous = p.get("geofenceInside");
    if (previous instanceof Boolean old && old != inside)
      alert(
          device,
          inside ? "GEOFENCE_ENTER" : "GEOFENCE_EXIT",
          inside ? "Device entered the family zone" : "Device left the family zone");
    p.put("geofenceInside", inside);
    storePrefs(p);
  }

  void storePrefs(Map<String, Object> p) {
    try {
      var entity = privacy.findById(1).orElseThrow();
      entity.value = json.writeValueAsString(p);
      privacy.save(entity);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public void deleteDeviceData() {
    delivery.clearEvents();
    for (var repo : repos.values()) repo.deleteAllInBatch();
    alerts.deleteAllInBatch();
    receipts.deleteAllInBatch();
    devices.deleteAllInBatch();
    var p = prefs();
    p.remove("geofenceInside");
    storePrefs(p);
    publisher.publishEvent(new DataChanged());
  }

  public void eraseParentReceiver() {
    delivery.eraseReceiver();
  }

  public void retainParentDeliveries(Instant cutoff) {
    delivery.retainEvents(cutoff);
  }
}

package com.familyguard;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class DashboardController {
  final EventService events;
  final DeviceRepository devices;
  final AlertRepository alerts;
  final AuditRepository audits;
  final long offlineMinutes;

  DashboardController(
      EventService e,
      DeviceRepository d,
      AlertRepository a,
      AuditRepository au,
      @Value("${app.offline-minutes}") long off) {
    events = e;
    devices = d;
    alerts = a;
    audits = au;
    offlineMinutes = off;
  }

  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  Map<String, Object> dashboard(@RequestParam(required = false) Instant dayStart) {
    var out = new LinkedHashMap<String, Object>();
    var device = devices.findAll().stream().findFirst().orElse(null);
    if (device == null) {
      out.put("device", null);
      return out;
    }
    out.put(
        "device",
        Map.of(
            "id",
            device.id,
            "name",
            device.name,
            "status",
            device.lastSeen != null
                    && device.lastSeen.isAfter(Instant.now().minusSeconds(offlineMinutes * 60))
                ? "recently_seen"
                : "offline",
            "lastSeen",
            device.lastSeen == null ? "" : device.lastSeen,
            "battery",
            device.battery == null ? -1 : device.battery,
            "monitoring",
            device.monitoring));
    var latestApps = new LinkedHashMap<String, EventBase>();
    events.list("apps").forEach(e -> latestApps.putIfAbsent(e.packageName, e));
    out.put(
        "installedAppsCount",
        latestApps.values().stream().filter(e -> !e.kind.equals("REMOVED")).count());
    out.put("alerts", alerts.findTop200ByOrderByOccurredAtDesc());
    out.put(
        "alertsToday",
        alerts.countByOccurredAtGreaterThanEqual(
            dayStart == null
                ? LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
                : dayStart));
    out.put(
        "lastLocation",
        events.list("location").stream().findFirst().map(events::view).orElse(null));
    var permissions = new LinkedHashMap<String, Object>();
    events
        .list("permissions")
        .forEach(
            e -> {
              var v = events.view(e);
              var data = (com.fasterxml.jackson.databind.JsonNode) v.get("data");
              permissions.putIfAbsent(
                  data.path("permission").asText(), data.path("enabled").asBoolean());
            });
    out.put("permissions", permissions);
    out.put("usage", events.list("usage").stream().map(events::view).toList());
    var recent = new ArrayList<EventBase>();
    for (String c : events.repositories().keySet()) recent.addAll(events.list(c));
    recent.sort(Comparator.comparing((EventBase e) -> e.occurredAt).reversed());
    out.put("recent", recent.stream().limit(20).map(events::view).toList());
    return out;
  }

  @GetMapping("/events/{category}")
  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      @PathVariable String category,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "") String search) {
    return events.list(category).stream()
        .filter(e -> from == null || !e.occurredAt.isBefore(from))
        .filter(e -> to == null || !e.occurredAt.isAfter(to))
        .filter(
            e ->
                search.isBlank()
                    || Objects.requireNonNullElse(e.packageName, "")
                        .toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT)))
        .map(events::view)
        .toList();
  }

  @GetMapping("/alerts")
  List<Alert> alerts() {
    return alerts.findTop200ByOrderByOccurredAtDesc();
  }

  @PostMapping("/alerts/{id}/read")
  @Transactional
  void read(@PathVariable UUID id) {
    alerts
        .findById(id)
        .ifPresent(
            a -> {
              a.read = true;
              alerts.save(a);
            });
  }

  @GetMapping("/preferences")
  Map<String, Object> preferences() {
    return events.prefs();
  }

  @PutMapping("/preferences")
  @Transactional
  Map<String, Object> preferences(@RequestBody Map<String, Object> input) {
    var p = new LinkedHashMap<String, Object>();
    for (String key :
        List.of("notifications", "apps", "usage", "calls", "location", "alertNotifications")) {
      if (!(input.get(key) instanceof Boolean)) throw new IllegalArgumentException();
      p.put(key, input.get(key));
    }
    for (String key : List.of("retentionDays", "locationHistoryDays")) {
      if (!(input.get(key) instanceof Number n)
          || n.doubleValue() != n.intValue()
          || n.intValue() < 1
          || n.intValue() > 90) throw new IllegalArgumentException();
      p.put(key, input.get(key));
    }
    if (input.get("geofence") instanceof Map<?, ?> g) {
      for (String key : List.of("latitude", "longitude", "radius"))
        if (!(g.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue()))
          throw new IllegalArgumentException();
      double lat = ((Number) g.get("latitude")).doubleValue(),
          lon = ((Number) g.get("longitude")).doubleValue(),
          radius = ((Number) g.get("radius")).doubleValue();
      if (Math.abs(lat) > 90 || Math.abs(lon) > 180 || radius < 100 || radius > 100000)
        throw new IllegalArgumentException();
      p.put("geofence", Map.of("latitude", lat, "longitude", lon, "radius", radius));
    }
    events.storePrefs(p);
    var a = new SecurityAuditLog();
    a.action = "PRIVACY_PREFERENCES_UPDATED";
    audits.save(a);
    return p;
  }
}

package com.familyguard;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:fg;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "app.admin-email=parent@example.test",
      "app.admin-password=testing-parent-password",
      "app.jwt-secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
      "app.encryption-key=ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=",
      "app.secure-cookies=false",
      "app.origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowsTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DeviceRepository devices;
  @Autowired PairingRepository pairings;
  @Autowired NotificationEventRepository notifications;
  @Autowired ReceiptRepository receipts;
  @Autowired EncryptedText crypto;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  String parent() throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"parent@example.test\",\"password\":\"testing-parent-password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("accessToken").asText();
  }

  String code(String parent) throws Exception {
    return json.readTree(
            mvc.perform(post("/api/pairing/code").header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("code")
        .asText();
  }

  String pair(String code) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/pairing/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "code", code, "deviceName", "Child phone", "consent", true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("deviceToken")
        .asText();
  }

  @Test
  void authenticationAndRoleIsolation() throws Exception {
    mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"parent@example.test\",\"password\":\"incorrect\"}"))
        .andExpect(status().isUnauthorized());
    String p = parent(), d = pair(code(p));
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + d))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + p)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void singleUsePairingDeduplicationAndRevocation() throws Exception {
    String p = parent(), c = code(p), d = pair(c);
    mvc.perform(post("/api/pairing/code").header("Authorization", "Bearer " + p))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/pairing/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("code", c, "deviceName", "Second phone", "consent", true))))
        .andExpect(status().isBadRequest());
    UUID eventId = UUID.randomUUID();
    String batch =
        json.writeValueAsString(
            Map.of(
                "events",
                List.of(
                    Map.of(
                        "id",
                        eventId,
                        "category",
                        "notifications",
                        "kind",
                        "NOTIFICATION",
                        "packageName",
                        "com.whatsapp",
                        "occurredAt",
                        Instant.now().toString(),
                        "data",
                        Map.of("count", 1, "text", "MUST NOT BE STORED")))));
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/device/sync")
                  .header("Authorization", "Bearer " + d)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(batch))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.acknowledged[0]").value(eventId.toString()));
    assertEquals(1, notifications.count());
    assertEquals(1, receipts.count());
    assertFalse(notifications.findAll().getFirst().payload.contains("MUST NOT"));
    String stored = jdbc.queryForObject("select payload from notification_events", String.class);
    assertFalse(stored.contains("count"));
    assertTrue(stored.contains(":"));
    mvc.perform(delete("/api/pairing").header("Authorization", "Bearer " + p))
        .andExpect(status().isOk());
    assertEquals(0, devices.count());
    assertEquals(0, notifications.count());
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + d)
                .contentType(MediaType.APPLICATION_JSON)
                .content(batch))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredCodesAndConsentRequired() throws Exception {
    String p = parent(), c = code(p);
    var entity = pairings.findById(1).orElseThrow();
    entity.expiresAt = Instant.now().minusSeconds(1);
    pairings.save(entity);
    mvc.perform(
            post("/api/pairing/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("code", c, "deviceName", "phone", "consent", true))))
        .andExpect(status().isBadRequest());
    c = code(p);
    mvc.perform(
            post("/api/pairing/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("code", c, "deviceName", "phone", "consent", false))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refreshRotatesAndRejectsUntrustedOrigins() throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"parent@example.test\",\"password\":\"testing-parent-password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String cookie = r.getHeader("Set-Cookie").split(";")[0].substring("fg_refresh=".length());
    var refreshCookie = new jakarta.servlet.http.Cookie("fg_refresh", cookie);
    mvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/refresh")
                .header("Origin", "http://localhost:5173")
                .cookie(refreshCookie))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/refresh")
                .header("Origin", "http://localhost:5173")
                .cookie(refreshCookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedEncryptionDetectsTampering() {
    String a = crypto.convertToDatabaseColumn("sensitive"),
        b = crypto.convertToDatabaseColumn("sensitive");
    assertNotEquals(a, b);
    assertFalse(a.contains("sensitive"));
    assertEquals("sensitive", crypto.convertToEntityAttribute(a));
    String[] parts = a.split(":");
    byte[] bytes = Base64.getDecoder().decode(parts[1]);
    bytes[0] ^= 1;
    assertThrows(
        IllegalStateException.class,
        () ->
            crypto.convertToEntityAttribute(
                parts[0] + ":" + Base64.getEncoder().encodeToString(bytes)));
  }

  @Test
  void privacyDenialsAcknowledgeWithoutStoringAndRejectInvalidLocations() throws Exception {
    String p = parent(), d = pair(code(p));
    Map<String, Object> prefs = new LinkedHashMap<>();
    for (String key :
        List.of("apps", "usage", "notifications", "calls", "location", "alertNotifications"))
      prefs.put(key, false);
    prefs.put("retentionDays", 7);
    prefs.put("locationHistoryDays", 1);
    mvc.perform(
            put("/api/preferences")
                .header("Authorization", "Bearer " + p)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(prefs)))
        .andExpect(status().isOk());
    String event =
        json.writeValueAsString(
            Map.of(
                "events",
                List.of(
                    Map.of(
                        "id",
                        UUID.randomUUID(),
                        "category",
                        "notifications",
                        "kind",
                        "NOTIFICATION",
                        "packageName",
                        "com.whatsapp",
                        "occurredAt",
                        Instant.now().toString(),
                        "data",
                        Map.of("count", 1)))));
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + d)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledged.length()").value(1));
    assertEquals(0, notifications.count());
    assertEquals(1, receipts.count());
    String invalid =
        json.writeValueAsString(
            Map.of(
                "events",
                List.of(
                    Map.of(
                        "id",
                        UUID.randomUUID(),
                        "category",
                        "location",
                        "kind",
                        "LOCATION",
                        "occurredAt",
                        Instant.now().toString(),
                        "data",
                        Map.of("latitude", 999, "longitude", 10, "accuracy", 5)))));
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + d)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalid))
        .andExpect(status().isBadRequest());
    assertEquals(1, receipts.count());
  }

  @Test
  void deletingAccountRemovesIdentityAndCannotRecreateIt() throws Exception {
    String p = parent();
    pair(code(p));
    mvc.perform(delete("/api/account").header("Authorization", "Bearer " + p))
        .andExpect(status().isOk());
    assertEquals(0, devices.count());
    assertEquals(
        "deleted@familyguard.invalid",
        jdbc.queryForObject("select email from admin_users", String.class));
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + p))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"parent@example.test\",\"password\":\"testing-parent-password\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Autowired Maintenance maintenance;
  @Autowired AlertRepository alertRepository;

  @Test
  void geofenceAlertsOnlyOnAccurateReportedTransitions() throws Exception {
    String p = parent(), d = pair(code(p));
    Map<String, Object> prefs = new LinkedHashMap<>();
    for (String key :
        List.of("apps", "usage", "notifications", "calls", "location", "alertNotifications"))
      prefs.put(key, true);
    prefs.put("retentionDays", 30);
    prefs.put("locationHistoryDays", 7);
    prefs.put("geofence", Map.of("latitude", 12.0, "longitude", 77.0, "radius", 500));
    mvc.perform(
            put("/api/preferences")
                .header("Authorization", "Bearer " + p)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(prefs)))
        .andExpect(status().isOk());
    for (double[] point :
        List.of(
            new double[] {12, 77, 10}, new double[] {13, 77, 1000}, new double[] {13, 77, 10})) {
      String event =
          json.writeValueAsString(
              Map.of(
                  "events",
                  List.of(
                      Map.of(
                          "id",
                          UUID.randomUUID(),
                          "category",
                          "location",
                          "kind",
                          "LOCATION",
                          "occurredAt",
                          Instant.now().toString(),
                          "data",
                          Map.of(
                              "latitude",
                              point[0],
                              "longitude",
                              point[1],
                              "accuracy",
                              point[2])))));
      mvc.perform(
              post("/api/device/sync")
                  .header("Authorization", "Bearer " + d)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(event))
          .andExpect(status().isOk());
    }
    assertEquals(
        1, alertRepository.findAll().stream().filter(a -> a.kind.equals("GEOFENCE_EXIT")).count());
    assertEquals(
        0, alertRepository.findAll().stream().filter(a -> a.kind.equals("GEOFENCE_ENTER")).count());
    assertFalse(
        jdbc.queryForObject("select value from privacy_preferences", String.class)
            .contains("latitude"));
  }

  @Test
  void dashboardAndOfflineRecoveryUseActualPersistedData() throws Exception {
    String p = parent();
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + p))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.device").doesNotExist());
    String d = pair(code(p));
    String event =
        json.writeValueAsString(
            Map.of(
                "events",
                List.of(
                    Map.of(
                        "id",
                        UUID.randomUUID(),
                        "category",
                        "status",
                        "kind",
                        "STATUS",
                        "occurredAt",
                        Instant.now().toString(),
                        "data",
                        Map.of("battery", 12, "monitoring", true, "connected", true)))));
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + d)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event))
        .andExpect(status().isOk());
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + p))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.device.battery").value(12))
        .andExpect(jsonPath("$.device.status").value("recently_seen"))
        .andExpect(jsonPath("$.recent.length()").value(1));
    var child = devices.findAll().getFirst();
    child.lastSeen = Instant.now().minusSeconds(4000);
    devices.save(child);
    maintenance.offline();
    mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + p))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.device.status").value("offline"));
    assertTrue(devices.findAll().getFirst().offlineAlerted);
    mvc.perform(
            post("/api/device/sync")
                .header("Authorization", "Bearer " + d)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[]}"))
        .andExpect(status().isOk());
    assertFalse(devices.findAll().getFirst().offlineAlerted);
    maintenance.retention();
  }
}

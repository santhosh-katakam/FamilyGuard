package com.familyguard;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:fgsocket;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.admin-email=parent@example.test",
      "app.admin-password=testing-parent-password",
      "app.jwt-secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
      "app.encryption-key=ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=",
      "app.secure-cookies=false",
      "app.origins=http://localhost:5173",
      "debug=false"
    })
class ParentSocketTest {
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  HttpResponse<String> request(String method, String path, String token, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json");
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return client.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  JsonNode post(String path, String token, Object body) throws Exception {
    var response = request("POST", path, token, body);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  @Test
  void realSocketRequiresOneUseTicketAndClosesWhenReceiverIsRevoked() throws Exception {
    Map<String, String> credentials =
        Map.of("email", "parent@example.test", "password", "testing-parent-password");
    String parent = post("/api/auth/login", null, credentials).get("accessToken").asText();
    String code = post("/api/pairing/code", parent, Map.of()).get("code").asText();
    String child =
        post(
                "/api/pairing/complete",
                null,
                Map.of("code", code, "deviceName", "Test child", "consent", true))
            .get("deviceToken")
            .asText();
    String receiver =
        post(
                "/api/parent-device/register",
                null,
                Map.of(
                    "email",
                    credentials.get("email"),
                    "password",
                    credentials.get("password"),
                    "deviceName",
                    "Test parent"))
            .get("receiverToken")
            .asText();
    String ticket = post("/api/parent-device/ticket", receiver, Map.of()).get("ticket").asText();
    var message = new CompletableFuture<String>();
    var closed = new CompletableFuture<Integer>();
    WebSocket.Listener listener =
        new WebSocket.Listener() {
          final StringBuilder text = new StringBuilder();

          public void onOpen(WebSocket ws) {
            ws.request(1);
          }

          public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
              message.complete(text.toString());
              text.setLength(0);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
          }

          public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            closed.complete(code);
            return CompletableFuture.completedFuture(null);
          }

          public void onError(WebSocket ws, Throwable failure) {
            message.completeExceptionally(failure);
            closed.completeExceptionally(failure);
          }
        };
    URI socketUrl = URI.create("ws://localhost:" + port + "/ws?ticket=" + ticket);
    WebSocket socket =
        client
            .newWebSocketBuilder()
            .header("Origin", "http://localhost:5173")
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(socketUrl, listener)
            .get(10, TimeUnit.SECONDS);
    try {
      assertThrows(
          ExecutionException.class,
          () ->
              client
                  .newWebSocketBuilder()
                  .header("Origin", "http://localhost:5173")
                  .buildAsync(socketUrl, new WebSocket.Listener() {})
                  .get(10, TimeUnit.SECONDS));
      // Web login rotation affects only web credentials, not the enrolled native receiver.
      parent = post("/api/auth/login", null, credentials).get("accessToken").asText();
      post(
          "/api/device/sync",
          child,
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
                      Map.of("text", "PRIVATE", "count", 1)))));
      assertEquals(
          "refresh", json.readTree(message.get(10, TimeUnit.SECONDS)).get("type").asText());
      var pending = request("GET", "/api/parent-device/events", receiver, null);
      assertEquals(200, pending.statusCode());
      assertFalse(pending.body().contains("PRIVATE"));
      assertEquals(1, json.readTree(pending.body()).get("events").size());
      assertEquals(204, request("DELETE", "/api/parent-receiver", parent, null).statusCode());
      assertEquals(1008, closed.get(10, TimeUnit.SECONDS));
      assertEquals(401, request("GET", "/api/parent-device/events", receiver, null).statusCode());
    } finally {
      socket.abort();
    }
  }
}

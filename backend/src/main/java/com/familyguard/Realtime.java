package com.familyguard;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.transaction.event.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

record DataChanged() {}

@Configuration
@EnableWebSocket
class Realtime extends TextWebSocketHandler implements WebSocketConfigurer {
  record Ticket(long expiry, long version) {}

  final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
  final Map<WebSocketSession, Long> sessions = new ConcurrentHashMap<>();
  final AdminRepository admins;
  final String[] origins;

  Realtime(AdminRepository a, @Value("${app.origins}") String o) {
    admins = a;
    origins = o.split(",");
  }

  String ticket() {
    long now = System.currentTimeMillis();
    tickets.entrySet().removeIf(e -> e.getValue().expiry() < now);
    String t = Tokens.random();
    tickets.put(t, new Ticket(now + 30000, admins.findById(1).orElseThrow().sessionVersion));
    return t;
  }

  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(this, "/ws")
        .setAllowedOrigins(origins)
        .addInterceptors(
            new HandshakeInterceptor() {
              public boolean beforeHandshake(
                  ServerHttpRequest q,
                  ServerHttpResponse r,
                  WebSocketHandler h,
                  Map<String, Object> a) {
                String ticket =
                    UriComponentsBuilder.fromUri(q.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("ticket");
                Ticket grant = ticket == null ? null : tickets.remove(ticket);
                if (grant == null || grant.expiry() < System.currentTimeMillis()) return false;
                var parent = admins.findById(1).orElse(null);
                if (parent == null || !parent.enabled || parent.sessionVersion != grant.version())
                  return false;
                a.put("version", parent.sessionVersion);
                return true;
              }

              public void afterHandshake(
                  ServerHttpRequest q, ServerHttpResponse r, WebSocketHandler h, Exception e) {}
            });
  }

  public void afterConnectionEstablished(WebSocketSession s) {
    sessions.put(s, (Long) s.getAttributes().get("version"));
  }

  public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
    sessions.remove(s);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void changed(DataChanged event) {
    var parent = admins.findById(1).orElse(null);
    sessions.forEach(
        (s, v) -> {
          try {
            if (parent == null || !parent.enabled || v != parent.sessionVersion) {
              s.close(CloseStatus.POLICY_VIOLATION);
              sessions.remove(s);
            } else
              synchronized (s) {
                s.sendMessage(new TextMessage("{\"type\":\"refresh\"}"));
              }
          } catch (Exception e) {
            sessions.remove(s);
          }
        });
  }
}

@RestController
class RealtimeController {
  final Realtime realtime;

  RealtimeController(Realtime r) {
    realtime = r;
  }

  @PostMapping("/api/realtime/ticket")
  Map<String, String> ticket() {
    return Map.of("ticket", realtime.ticket());
  }
}

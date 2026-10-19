package com.familyguard;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.Scheduled;
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
  record Ticket(long expiry, long version, String receiverHash) {}

  final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
  final Map<WebSocketSession, Ticket> sessions = new ConcurrentHashMap<>();
  final AdminRepository admins;
  final ParentDelivery delivery;
  final String[] origins;

  Realtime(AdminRepository a, ParentDelivery pd, @Value("${app.origins}") String o) {
    admins = a;
    delivery = pd;
    origins = o.split(",");
  }

  String ticket() {
    return issue(null);
  }

  String receiverTicket(String hash) {
    if (delivery.authenticate(hash).isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return issue(hash);
  }

  String issue(String receiverHash) {
    long now = System.currentTimeMillis();
    tickets.entrySet().removeIf(e -> e.getValue().expiry() < now);
    String t = Tokens.random();
    if (tickets.size() >= 1000)
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS);
    tickets.put(
        t, new Ticket(now + 30000, admins.findById(1).orElseThrow().sessionVersion, receiverHash));
    return t;
  }

  Ticket consume(String ticket) {
    Ticket grant = ticket == null ? null : tickets.remove(ticket);
    return grant != null && grant.expiry() >= System.currentTimeMillis() && authorized(grant)
        ? grant
        : null;
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
                Ticket grant = consume(ticket);
                if (grant == null) {
                  r.setStatusCode(HttpStatus.FORBIDDEN);
                  return false;
                }
                a.put("grant", grant);
                return true;
              }

              public void afterHandshake(
                  ServerHttpRequest q, ServerHttpResponse r, WebSocketHandler h, Exception e) {}
            });
  }

  public void afterConnectionEstablished(WebSocketSession s) {
    sessions.put(s, (Ticket) s.getAttributes().get("grant"));
  }

  public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
    sessions.remove(s);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void changed(DataChanged event) {
    sessions.forEach(
        (s, v) -> {
          try {
            if (!authorized(v)) {
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

  boolean authorized(Ticket grant) {
    if (grant.receiverHash() != null)
      return delivery.authenticate(grant.receiverHash()).isPresent();
    return admins
        .findById(1)
        .filter(a -> a.enabled && a.sessionVersion == grant.version())
        .isPresent();
  }

  @Scheduled(fixedDelay = 30000)
  public void pruneRevoked() {
    sessions.forEach(
        (session, grant) -> {
          if (!authorized(grant)) {
            try {
              session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            sessions.remove(session);
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

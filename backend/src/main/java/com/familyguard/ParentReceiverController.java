package com.familyguard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class ParentReceiverController {
  final ParentDelivery delivery;
  final Realtime realtime;

  ParentReceiverController(ParentDelivery d, Realtime r) {
    delivery = d;
    realtime = r;
  }

  record Registration(
      @NotBlank @Email String email,
      @NotBlank @Size(max = 200) String password,
      @NotBlank @Size(max = 80) String deviceName,
      boolean replace) {}

  record Acknowledgment(@PositiveOrZero long cursor) {}

  @PostMapping("/api/parent-device/register")
  Map<String, Object> register(@Valid @RequestBody Registration input) {
    return delivery.register(input.email(), input.password(), input.deviceName(), input.replace());
  }

  @GetMapping("/api/parent-device/events")
  Map<String, Object> events(Authentication auth) {
    return delivery.pending(auth.getName());
  }

  @PostMapping("/api/parent-device/ack")
  ResponseEntity<Void> acknowledge(Authentication auth, @Valid @RequestBody Acknowledgment ack) {
    delivery.acknowledge(auth.getName(), ack.cursor());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/parent-device/ticket")
  Map<String, String> ticket(Authentication auth) {
    return Map.of("ticket", realtime.receiverTicket(auth.getName()));
  }

  @DeleteMapping("/api/parent-device/registration")
  ResponseEntity<Void> remove(Authentication auth) {
    delivery.revokeSelf(auth.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/parent-receiver")
  Map<String, Object> status() {
    return delivery.status();
  }

  @DeleteMapping("/api/parent-receiver")
  ResponseEntity<Void> revoke() {
    delivery.revoke();
    return ResponseEntity.noContent().build();
  }
}

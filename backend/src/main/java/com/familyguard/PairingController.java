package com.familyguard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class PairingController {
  final AdminRepository admins;
  final DeviceRepository devices;
  final PairingRepository pairings;
  final AuditRepository audit;
  final EventService events;

  PairingController(
      AdminRepository a,
      DeviceRepository d,
      PairingRepository p,
      AuditRepository au,
      EventService e) {
    admins = a;
    devices = d;
    pairings = p;
    audit = au;
    events = e;
  }

  @PostMapping("/pairing/code")
  @Transactional
  Map<String, Object> code() {
    admins.locked().orElseThrow();
    if (devices.count() != 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Revoke the current device before pairing another");
    String code = Tokens.random().substring(0, 12).toUpperCase(Locale.ROOT);
    var p = new DevicePairing();
    p.codeHash = Tokens.hash(code);
    p.expiresAt = Instant.now().plusSeconds(300);
    pairings.save(p);
    log("PAIRING_CODE_CREATED");
    return Map.of("code", code, "expiresAt", p.expiresAt);
  }

  record Pair(
      @NotBlank @Size(min = 12, max = 12) String code,
      @NotBlank @Size(max = 80) String deviceName,
      @AssertTrue boolean consent) {}

  @PostMapping("/pairing/complete")
  @Transactional
  Map<String, Object> complete(@Valid @RequestBody Pair input) {
    admins
        .locked()
        .filter(a -> a.enabled)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Account disabled"));
    var p =
        pairings
            .findById(1)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalid or expired"));
    if (devices.count() != 0
        || p.expiresAt.isBefore(Instant.now())
        || !p.codeHash.equals(Tokens.hash(input.code().toUpperCase(Locale.ROOT))))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Code invalid, expired, or already used");
    var d = new ChildDevice();
    String token = Tokens.random();
    d.tokenHash = Tokens.hash(token);
    d.name = input.deviceName();
    devices.save(d);
    pairings.delete(p);
    log("CHILD_PAIRED_WITH_NOTICE_ACCEPTED");
    return Map.of("deviceToken", token, "deviceId", d.id, "deviceName", d.name);
  }

  @DeleteMapping("/pairing")
  @Transactional
  void revoke() {
    admins.locked().orElseThrow();
    events.deleteDeviceData();
    pairings.deleteAll();
    log("PAIRING_REVOKED_AND_DATA_DELETED");
  }

  @DeleteMapping("/account")
  @Transactional
  void deleteAccount() {
    var a = admins.locked().orElseThrow();
    events.deleteDeviceData();
    pairings.deleteAll();
    a.enabled = false;
    events.eraseParentReceiver();
    a.email = "deleted@familyguard.invalid";
    a.passwordHash = Tokens.hash(Tokens.random());
    a.refreshHash = null;
    a.refreshExpires = null;
    a.sessionVersion++;
    admins.save(a);
    events.resetPrivacy();
    log("ACCOUNT_DISABLED_AND_DATA_DELETED");
  }

  void log(String action) {
    var entry = new SecurityAuditLog();
    entry.action = action;
    audit.save(entry);
  }
}

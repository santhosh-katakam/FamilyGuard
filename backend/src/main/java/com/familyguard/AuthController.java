package com.familyguard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Component
class Bootstrap implements ApplicationRunner {
  final AdminRepository admins;
  final PrivacyRepository privacy;
  final BCryptPasswordEncoder encoder;
  final String email, password;

  Bootstrap(
      AdminRepository a,
      PrivacyRepository p,
      BCryptPasswordEncoder e,
      @Value("${app.admin-email}") String mail,
      @Value("${app.admin-password}") String pass) {
    admins = a;
    privacy = p;
    encoder = e;
    email = mail;
    password = pass;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (admins.count() == 0) {
      if (password.length() < 14)
        throw new IllegalArgumentException("ADMIN_PASSWORD must have at least 14 characters");
      var a = new AdminUser();
      a.email = email.toLowerCase(Locale.ROOT);
      a.passwordHash = encoder.encode(password);
      admins.save(a);
    }
    if (privacy.count() == 0) privacy.save(new PrivacyPreferences());
  }
}

@RestController
@RequestMapping("/api/auth")
class AuthController {
  final AdminRepository admins;
  final BCryptPasswordEncoder encoder;
  final Tokens tokens;
  final AuditRepository audits;
  final boolean secure;

  AuthController(
      AdminRepository a,
      BCryptPasswordEncoder e,
      Tokens t,
      AuditRepository audit,
      @Value("${app.secure-cookies}") boolean s) {
    admins = a;
    encoder = e;
    tokens = t;
    audits = audit;
    secure = s;
  }

  record Login(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {}

  @PostMapping("/login")
  @Transactional
  ResponseEntity<?> login(@Valid @RequestBody Login input) {
    var a = admins.locked().orElseThrow();
    if (!a.enabled
        || !a.email.equalsIgnoreCase(input.email())
        || !encoder.matches(input.password(), a.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    a.sessionVersion++;
    audit("LOGIN");
    return issue(a);
  }

  @PostMapping("/refresh")
  @Transactional
  ResponseEntity<?> refresh(@CookieValue(name = "fg_refresh", defaultValue = "") String token) {
    var a = admins.locked().orElseThrow();
    if (!a.enabled
        || a.refreshHash == null
        || !a.refreshHash.equals(Tokens.hash(token))
        || a.refreshExpires.isBefore(Instant.now()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
    return issue(a);
  }

  @PostMapping("/logout")
  @Transactional
  ResponseEntity<?> logout(@CookieValue(name = "fg_refresh", defaultValue = "") String token) {
    admins
        .locked()
        .filter(a -> a.refreshHash != null && a.refreshHash.equals(Tokens.hash(token)))
        .ifPresent(
            a -> {
              a.refreshHash = null;
              a.sessionVersion++;
            });
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
        .build();
  }

  ResponseEntity<?> issue(AdminUser a) {
    String refresh = Tokens.random();
    a.refreshHash = Tokens.hash(refresh);
    a.refreshExpires = Instant.now().plus(Duration.ofDays(7));
    admins.save(a);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie(refresh, 604800).toString())
        .body(Map.of("accessToken", tokens.access(a.sessionVersion), "expiresIn", 900));
  }

  ResponseCookie cookie(String value, long age) {
    return ResponseCookie.from("fg_refresh", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite(secure ? "None" : "Lax")
        .path("/api/auth")
        .maxAge(age)
        .build();
  }

  void audit(String action) {
    var a = new SecurityAuditLog();
    a.action = action;
    audits.save(a);
  }
}

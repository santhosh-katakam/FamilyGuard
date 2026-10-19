package com.familyguard;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class Tokens {
  private final SecretKey key;

  Tokens(@Value("${app.jwt-secret}") String secret) {
    byte[] bytes = Base64.getDecoder().decode(secret);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT_SECRET requires 32 random bytes, base64 encoded");
    key = Keys.hmacShaKeyFor(bytes);
  }

  String access(long version) {
    return Jwts.builder()
        .subject("parent")
        .issuer("familyguard")
        .claim("version", version)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusSeconds(900)))
        .signWith(key)
        .compact();
  }

  long verify(String token) {
    return ((Number)
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer("familyguard")
                .requireSubject("parent")
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("version"))
        .longValue();
  }

  static String random() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

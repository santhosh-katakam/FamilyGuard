package com.familyguard;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptedText implements AttributeConverter<String, String> {
  private final SecretKeySpec key;

  public EncryptedText(@Value("${app.encryption-key}") String encoded) {
    byte[] bytes = Base64.getDecoder().decode(encoded);
    if (bytes.length != 32)
      throw new IllegalArgumentException("DATA_ENCRYPTION_KEY must be base64 of 32 random bytes");
    key = new SecretKeySpec(bytes, "AES");
  }

  public String convertToDatabaseColumn(String value) {
    if (value == null) return null;
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      return Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String convertToEntityAttribute(String value) {
    if (value == null) return null;
    try {
      String[] parts = value.split(":");
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      return new String(c.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }
}

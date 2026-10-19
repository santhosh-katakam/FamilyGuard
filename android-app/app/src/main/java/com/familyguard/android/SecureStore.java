package com.familyguard.android;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStore {
  static final String ALIAS = "familyguard.local.v1";

  static synchronized javax.crypto.SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(ALIAS)) {
      KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setRandomizedEncryptionRequired(true)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
  }

  static String encrypt(String text) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key());
      return Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
          + ":"
          + Base64.encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    } catch (Exception e) {
      throw new IllegalStateException("Secure storage unavailable", e);
    }
  }

  static String decrypt(String text) {
    try {
      String[] parts = text.split(":");
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
      return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Secure storage key unavailable", e);
    }
  }

  static void token(Context c, String value) {
    c.getSharedPreferences("secure", 0).edit().putString("token", encrypt(value)).apply();
  }

  static String token(Context c) {
    String s = c.getSharedPreferences("secure", 0).getString("token", null);
    return s == null ? null : decrypt(s);
  }

  static void clear(Context c) {
    c.getSharedPreferences("secure", 0).edit().clear().commit();
  }
}

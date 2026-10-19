package com.familyguard.android;

import android.content.Context;
import android.content.SharedPreferences;

final class ParentStore {
  static SharedPreferences preferences(Context c) {
    return c.getSharedPreferences("parent-receiver", 0);
  }

  static String token(Context c) {
    String encrypted = preferences(c).getString("credential", null);
    return encrypted == null ? null : SecureStore.decrypt(encrypted);
  }

  static void save(Context c, String token, String expires, String name) {
    preferences(c)
        .edit()
        .putString("credential", SecureStore.encrypt(token))
        .putString("expires", expires)
        .putString("name", name)
        .commit();
  }

  static void clear(Context c) {
    preferences(c).edit().clear().commit();
  }

  static void status(Context c, String status) {
    preferences(c).edit().putString("status", status).apply();
  }
}

package com.familyguard.android;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

final class QuietHours {
  static boolean contains(boolean enabled, String start, String end, LocalTime now) {
    if (!enabled) return false;
    try {
      LocalTime from = LocalTime.parse(start), until = LocalTime.parse(end);
      if (from.equals(until)) return false;
      return from.isBefore(until)
          ? !now.isBefore(from) && now.isBefore(until)
          : !now.isBefore(from) || now.isBefore(until);
    } catch (DateTimeParseException | NullPointerException e) {
      return false;
    }
  }
}

package com.familyguard.android;

import static org.junit.Assert.*;

import java.time.LocalTime;
import org.junit.Test;

public class QuietHoursTest {
  @Test
  public void midnightWindowUsesInclusiveStartAndExclusiveEnd() {
    assertFalse(QuietHours.contains(true, "22:00", "07:00", LocalTime.of(21, 59)));
    assertTrue(QuietHours.contains(true, "22:00", "07:00", LocalTime.of(22, 0)));
    assertTrue(QuietHours.contains(true, "22:00", "07:00", LocalTime.MIDNIGHT));
    assertTrue(QuietHours.contains(true, "22:00", "07:00", LocalTime.of(6, 59)));
    assertFalse(QuietHours.contains(true, "22:00", "07:00", LocalTime.of(7, 0)));
  }

  @Test
  public void disabledEqualAndInvalidWindowsDoNotSuppress() {
    assertFalse(QuietHours.contains(false, "22:00", "07:00", LocalTime.MIDNIGHT));
    assertFalse(QuietHours.contains(true, "07:00", "07:00", LocalTime.of(7, 0)));
    assertFalse(QuietHours.contains(true, "broken", "07:00", LocalTime.MIDNIGHT));
  }

  @Test
  public void daytimeWindowDoesNotQuietTheRestOfTheDay() {
    assertTrue(QuietHours.contains(true, "09:00", "17:00", LocalTime.NOON));
    assertFalse(QuietHours.contains(true, "09:00", "17:00", LocalTime.of(20, 0)));
  }
}

package com.familyguard;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class Maintenance {
  final EventService events;
  final AuditRepository audits;
  final long offline;

  Maintenance(EventService e, AuditRepository a, @Value("${app.offline-minutes}") long o) {
    events = e;
    audits = a;
    offline = o;
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void offline() {
    for (var d : events.deviceRepository().locked())
      if (d.lastSeen != null
          && d.lastSeen.isBefore(Instant.now().minusSeconds(offline * 60))
          && !d.offlineAlerted) {
        d.offlineAlerted = true;
        events.deviceRepository().save(d);
        events.alert(d.id, "DEVICE_OFFLINE", "No synchronization within the expected window");
        events.changed();
      }
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
  @Transactional
  public void retention() {
    var p = events.prefs();
    Instant before =
        Instant.now()
            .minus(Duration.ofDays(((Number) p.getOrDefault("retentionDays", 30)).intValue()));
    for (var r : events.repositories().values()) {
      if (r instanceof PermissionStatusRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof InstalledAppEventRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof AppUsageLogRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof NotificationEventRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof CallEventRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof DeviceStatusRepository x) x.deleteByOccurredAtBefore(before);
      else if (r instanceof LocationHistoryRepository x)
        x.deleteByOccurredAtBefore(
            Instant.now()
                .minus(
                    Duration.ofDays(
                        Math.min(
                            ((Number) p.getOrDefault("locationHistoryDays", 7)).intValue(),
                            ((Number) p.getOrDefault("retentionDays", 30)).intValue()))));
    }
    events.alertRepository().deleteByOccurredAtBefore(before);
    events.retainParentDeliveries(
        before.isAfter(Instant.now().minus(Duration.ofDays(7)))
            ? before
            : Instant.now().minus(Duration.ofDays(7)));
    events.receiptRepository().deleteByReceivedAtBefore(Instant.now().minus(Duration.ofDays(91)));
    audits.deleteByOccurredAtBefore(Instant.now().minus(Duration.ofDays(90)));
  }
}

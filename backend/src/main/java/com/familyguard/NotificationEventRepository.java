package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {
  List<NotificationEvent> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

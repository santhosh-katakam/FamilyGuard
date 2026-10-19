package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppUsageLogRepository extends JpaRepository<AppUsageLog, UUID> {
  List<AppUsageLog> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

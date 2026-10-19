package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstalledAppEventRepository extends JpaRepository<InstalledAppEvent, UUID> {
  List<InstalledAppEvent> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface LocationHistoryRepository extends JpaRepository<LocationHistory, UUID> {
  List<LocationHistory> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeviceStatusRepository extends JpaRepository<DeviceStatus, UUID> {
  List<DeviceStatus> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

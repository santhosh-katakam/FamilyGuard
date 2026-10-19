package com.familyguard;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionStatusRepository extends JpaRepository<PermissionStatus, UUID> {
  List<PermissionStatus> findTop10000ByOrderByOccurredAtDesc();

  void deleteByOccurredAtBefore(Instant before);
}

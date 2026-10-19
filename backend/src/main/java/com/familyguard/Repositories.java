package com.familyguard;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface AdminRepository extends JpaRepository<AdminUser, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AdminUser a where a.id=1")
  Optional<AdminUser> locked();
}

interface DeviceRepository extends JpaRepository<ChildDevice, UUID> {
  Optional<ChildDevice> findByTokenHash(String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from ChildDevice d")
  List<ChildDevice> locked();
}

interface PairingRepository extends JpaRepository<DevicePairing, Integer> {}

interface PrivacyRepository extends JpaRepository<PrivacyPreferences, Integer> {}

interface AuditRepository extends JpaRepository<SecurityAuditLog, UUID> {
  void deleteByOccurredAtBefore(Instant before);
}

interface ReceiptRepository extends JpaRepository<SyncReceipt, UUID> {
  void deleteByReceivedAtBefore(Instant before);
}

interface AlertRepository extends JpaRepository<Alert, UUID> {
  long countByOccurredAtGreaterThanEqual(Instant after);

  List<Alert> findTop200ByOrderByOccurredAtDesc();

  boolean existsByKindAndOccurredAtAfter(String kind, Instant after);

  void deleteByOccurredAtBefore(Instant before);
}

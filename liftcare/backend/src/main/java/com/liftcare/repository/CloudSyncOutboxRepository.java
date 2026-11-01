package com.liftcare.repository;

import com.liftcare.model.CloudSyncOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudSyncOutboxRepository extends JpaRepository<CloudSyncOutbox, Long> {
    Optional<CloudSyncOutbox> findByEventId(String eventId);
    Optional<CloudSyncOutbox> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<CloudSyncOutbox> findByBuildingCodeAndStatusOrderByCreatedAtAsc(String buildingCode, String status);
    List<CloudSyncOutbox> findByStatusOrderByCreatedAtAsc(String status);
    long countByBuildingCodeAndStatus(String buildingCode, String status);
    long countByStatus(String status);
}

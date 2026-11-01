package com.liftcare.repository;

import com.liftcare.model.CloudSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudSyncEventRepository extends JpaRepository<CloudSyncEvent, Long> {
    Optional<CloudSyncEvent> findByEventId(String eventId);
    Optional<CloudSyncEvent> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<CloudSyncEvent> findByBuildingCodeOrderByReceivedAtDesc(String buildingCode);
    long countByBuildingCode(String buildingCode);
}

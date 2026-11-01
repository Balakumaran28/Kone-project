package com.liftcare.repository;

import com.liftcare.model.CloudSyncConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudSyncConflictRepository extends JpaRepository<CloudSyncConflict, Long> {
    Optional<CloudSyncConflict> findByConflictId(String conflictId);
    List<CloudSyncConflict> findByBuildingCodeOrderByCreatedAtDesc(String buildingCode);
    List<CloudSyncConflict> findByResolutionActionOrderByCreatedAtDesc(String resolutionAction);
    List<CloudSyncConflict> findAllByOrderByCreatedAtDesc();
    long countByResolutionAction(String resolutionAction);
}

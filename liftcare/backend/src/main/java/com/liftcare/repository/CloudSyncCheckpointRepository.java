package com.liftcare.repository;

import com.liftcare.model.CloudSyncCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CloudSyncCheckpointRepository extends JpaRepository<CloudSyncCheckpoint, Long> {
    Optional<CloudSyncCheckpoint> findByBuildingCode(String buildingCode);
}

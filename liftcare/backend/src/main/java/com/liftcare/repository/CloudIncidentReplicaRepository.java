package com.liftcare.repository;

import com.liftcare.model.CloudIncidentReplica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CloudIncidentReplicaRepository extends JpaRepository<CloudIncidentReplica, Long> {
    Optional<CloudIncidentReplica> findByTicketNumber(String ticketNumber);
    List<CloudIncidentReplica> findByBuildingCodeOrderByReportedAtDesc(String buildingCode);
    List<CloudIncidentReplica> findAllByOrderByReportedAtDesc();
    List<CloudIncidentReplica> findByBuildingCodeAndStatusIn(String buildingCode, Collection<String> statuses);
    List<CloudIncidentReplica> findByStatusIn(Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);
    long countBySeverity(String severity);
    long countByEntrapmentReportedTrue();
    long countByResponseSlaBreachedTrue();
    long countByResolutionSlaBreachedTrue();
    long countByEscalationLevelGreaterThan(int level);

    long countByBuildingCodeAndStatusIn(String buildingCode, Collection<String> statuses);
    long countByBuildingCodeAndSeverity(String buildingCode, String severity);
    long countByBuildingCodeAndEntrapmentReportedTrue(String buildingCode);
}

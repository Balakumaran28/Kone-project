package com.liftcare.repository;

import com.liftcare.model.Incident;
import com.liftcare.model.IncidentStatus;
import com.liftcare.model.SeverityLevel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Override
    @EntityGraph(attributePaths = {"building", "elevator", "slaPolicy", "issueCategory", "assignedTo"})
    Optional<Incident> findById(Long id);

    Optional<Incident> findByTicketNumber(String ticketNumber);

    boolean existsByTicketNumber(String ticketNumber);

    List<Incident> findByBuildingIdAndStatus(Long buildingId, IncidentStatus status);

    List<Incident> findByElevatorId(Long elevatorId);

    List<Incident> findByBuildingId(Long buildingId);

    List<Incident> findByBuildingIdOrderByReportedAtDesc(Long buildingId);

    List<Incident> findByElevatorIdOrderByReportedAtDesc(Long elevatorId);

    List<Incident> findByReportedByIdOrderByReportedAtDesc(Long reportedById);

    List<Incident> findByAssignedToIdOrderByReportedAtDesc(Long assignedToId);

    List<Incident> findByBuildingIdAndReportedByIdOrderByReportedAtDesc(Long buildingId, Long reportedById);

    List<Incident> findByElevatorIdAndReportedByIdOrderByReportedAtDesc(Long elevatorId, Long reportedById);

    List<Incident> findAllByOrderByReportedAtDesc();

    List<Incident> findByStatus(IncidentStatus status);

    long countByStatusIn(Collection<IncidentStatus> statuses);

    long countByStatusInAndSeverity(Collection<IncidentStatus> statuses, SeverityLevel severity);

    long countByStatus(IncidentStatus status);

    long countByStatusInAndEntrapmentReportedTrue(Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusIn(Long buildingId, Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusInAndSeverity(Long buildingId, Collection<IncidentStatus> statuses, SeverityLevel severity);

    long countByBuildingIdAndStatus(Long buildingId, IncidentStatus status);

    long countByBuildingIdAndStatusInAndEntrapmentReportedTrue(Long buildingId, Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInAndEntrapmentReportedTrueOrderByReportedAtAsc(Collection<IncidentStatus> statuses);

    List<Incident> findByBuildingIdAndStatusInAndEntrapmentReportedTrueOrderByReportedAtAsc(Long buildingId, Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInAndSeverityOrderByReportedAtAsc(Collection<IncidentStatus> statuses, SeverityLevel severity);

    List<Incident> findByBuildingIdAndStatusInAndSeverityOrderByReportedAtAsc(Long buildingId, Collection<IncidentStatus> statuses, SeverityLevel severity);

    List<Incident> findByStatusInOrderByReportedAtAsc(Collection<IncidentStatus> statuses);

    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"building", "elevator", "slaPolicy", "issueCategory", "assignedTo"})
    @Query("SELECT i FROM Incident i WHERE i.status IN :statuses")
    List<Incident> findActiveIncidentsWithAssociations(@Param("statuses") Collection<IncidentStatus> statuses);

    long countByStatusInAndResponseSlaBreachedTrue(Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusInAndResponseSlaBreachedTrue(Long buildingId, Collection<IncidentStatus> statuses);

    long countByStatusInAndResolutionSlaBreachedTrue(Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusInAndResolutionSlaBreachedTrue(Long buildingId, Collection<IncidentStatus> statuses);

    long countByStatusInAndEscalationLevel(Collection<IncidentStatus> statuses, int escalationLevel);

    long countByBuildingIdAndStatusInAndEscalationLevel(Long buildingId, Collection<IncidentStatus> statuses, int escalationLevel);

    long countByStatusInAndEscalationLevelGreaterThan(Collection<IncidentStatus> statuses, int level);

    long countByBuildingIdAndStatusInAndEscalationLevelGreaterThan(Long buildingId, Collection<IncidentStatus> statuses, int level);

    long countByStatusInAndSlaPolicyIsNull(Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusInAndSlaPolicyIsNull(Long buildingId, Collection<IncidentStatus> statuses);

    long countByStatusInAndSlaWarningSentTrue(Collection<IncidentStatus> statuses);

    long countByBuildingIdAndStatusInAndSlaWarningSentTrue(Long buildingId, Collection<IncidentStatus> statuses);
}

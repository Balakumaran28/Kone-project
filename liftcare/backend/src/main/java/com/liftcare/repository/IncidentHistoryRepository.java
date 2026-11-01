package com.liftcare.repository;

import com.liftcare.model.IncidentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentHistoryRepository extends JpaRepository<IncidentHistory, Long>, JpaSpecificationExecutor<IncidentHistory> {

    List<IncidentHistory> findByIncidentIdOrderByChangedAtAsc(Long incidentId);

    List<IncidentHistory> findByIncidentIdOrderByChangedAtDesc(Long incidentId);

    Page<IncidentHistory> findByIncidentId(Long incidentId, Pageable pageable);
}

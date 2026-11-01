package com.liftcare.repository;

import com.liftcare.model.Building;
import com.liftcare.model.Elevator;
import com.liftcare.model.ElevatorOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElevatorRepository extends JpaRepository<Elevator, Long> {

    Optional<Elevator> findByBuildingAndElevatorCode(Building building, String elevatorCode);

    Optional<Elevator> findByBuildingIdAndElevatorCode(Long buildingId, String elevatorCode);

    boolean existsByBuildingIdAndElevatorCode(Long buildingId, String elevatorCode);

    boolean existsByBuildingIdAndElevatorCodeAndIdNot(Long buildingId, String elevatorCode, Long id);

    List<Elevator> findByBuildingId(Long buildingId);

    List<Elevator> findByBuildingIdAndOperationalStatus(Long buildingId, ElevatorOperationalStatus status);

    long countByBuildingId(Long buildingId);

    long countByBuildingIdAndOperationalStatus(Long buildingId, ElevatorOperationalStatus status);

    long countByOperationalStatus(ElevatorOperationalStatus status);

    List<Elevator> findByOperationalStatus(ElevatorOperationalStatus status);
}

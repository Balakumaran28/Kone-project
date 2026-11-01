package com.liftcare.repository;

import com.liftcare.model.CloudRegisteredBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudRegisteredBuildingRepository extends JpaRepository<CloudRegisteredBuilding, Long> {
    Optional<CloudRegisteredBuilding> findByBuildingCode(String buildingCode);
    boolean existsByBuildingCode(String buildingCode);
    List<CloudRegisteredBuilding> findByStatus(String status);
}

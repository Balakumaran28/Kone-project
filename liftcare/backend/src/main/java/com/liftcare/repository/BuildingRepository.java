package com.liftcare.repository;

import com.liftcare.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByBuildingCode(String buildingCode);

    boolean existsByBuildingCode(String buildingCode);

    boolean existsByBuildingCodeAndIdNot(String buildingCode, Long id);

    Optional<Building> findByLocalNodeId(String localNodeId);

    List<Building> findByActiveTrue();

    long countByActiveTrue();
}

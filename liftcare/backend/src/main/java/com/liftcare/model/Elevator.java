package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single elevator shaft unit inside a building complex.
 * The elevatorCode is unique within its containing building.
 */
@Entity
@Table(name = "elevators",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_elevator_building_code", columnNames = {"building_id", "elevator_code"})
        },
        indexes = {
                @Index(name = "idx_elevators_building", columnList = "building_id"),
                @Index(name = "idx_elevators_status", columnList = "operational_status")
        }
)
public class Elevator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Elevator code is required")
    @Size(max = 50, message = "Elevator code cannot exceed 50 characters")
    @Column(name = "elevator_code", nullable = false, length = 50)
    private String elevatorCode;

    @NotNull(message = "Building relationship is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Size(max = 255, message = "Location description cannot exceed 255 characters")
    @Column(name = "location_description", length = 255)
    private String locationDescription;

    @Size(max = 100, message = "Manufacturer cannot exceed 100 characters")
    @Column(length = 100)
    private String manufacturer;

    @Size(max = 100, message = "Model cannot exceed 100 characters")
    @Column(length = 100)
    private String model;

    @Size(max = 150, message = "Service provider cannot exceed 150 characters")
    @Column(name = "service_provider", length = 150)
    private String serviceProvider;

    @Positive(message = "Total floors must be a positive integer")
    @Column(name = "total_floors")
    private Integer totalFloors;

    @NotNull(message = "Operational status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 30)
    private ElevatorOperationalStatus operationalStatus = ElevatorOperationalStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Elevator() {
    }

    public Elevator(String elevatorCode, Building building, String locationDescription, ElevatorOperationalStatus operationalStatus) {
        this.elevatorCode = elevatorCode;
        this.building = building;
        this.locationDescription = locationDescription;
        this.operationalStatus = operationalStatus != null ? operationalStatus : ElevatorOperationalStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.operationalStatus == null) {
            this.operationalStatus = ElevatorOperationalStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getElevatorCode() {
        return elevatorCode;
    }

    public void setElevatorCode(String elevatorCode) {
        this.elevatorCode = elevatorCode;
    }

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getServiceProvider() {
        return serviceProvider;
    }

    public void setServiceProvider(String serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    public Integer getTotalFloors() {
        return totalFloors;
    }

    public void setTotalFloors(Integer totalFloors) {
        this.totalFloors = totalFloors;
    }

    public ElevatorOperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(ElevatorOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Elevator elevator)) return false;
        return Objects.equals(id, elevator.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Elevator{" +
                "id=" + id +
                ", elevatorCode='" + elevatorCode + '\'' +
                ", buildingId=" + (building != null ? building.getId() : null) +
                ", operationalStatus=" + operationalStatus +
                ", totalFloors=" + totalFloors +
                '}';
    }
}

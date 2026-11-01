package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a physical building complex monitored by an on-premise LiftCare local node.
 */
@Entity
@Table(name = "buildings", indexes = {
        @Index(name = "idx_buildings_code", columnList = "building_code", unique = true),
        @Index(name = "idx_buildings_node", columnList = "local_node_id")
})
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Building code is required")
    @Size(max = 50, message = "Building code cannot exceed 50 characters")
    @Column(name = "building_code", nullable = false, unique = true, length = 50)
    private String buildingCode;

    @NotBlank(message = "Building name is required")
    @Size(max = 150, message = "Building name cannot exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address cannot exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String address;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Column(length = 100)
    private String city;

    @Size(max = 100, message = "Local node ID cannot exceed 100 characters")
    @Column(name = "local_node_id", length = 100)
    private String localNodeId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Building() {
    }

    public Building(String buildingCode, String name, String address, String city, String localNodeId) {
        this.buildingCode = buildingCode;
        this.name = name;
        this.address = address;
        this.city = city;
        this.localNodeId = localNodeId;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
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

    public String getBuildingCode() {
        return buildingCode;
    }

    public void setBuildingCode(String buildingCode) {
        this.buildingCode = buildingCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getLocalNodeId() {
        return localNodeId;
    }

    public void setLocalNodeId(String localNodeId) {
        this.localNodeId = localNodeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
        if (!(o instanceof Building building)) return false;
        return Objects.equals(id, building.id) || (buildingCode != null && Objects.equals(buildingCode, building.buildingCode));
    }

    @Override
    public int hashCode() {
        return buildingCode != null ? buildingCode.hashCode() : Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Building{" +
                "id=" + id +
                ", buildingCode='" + buildingCode + '\'' +
                ", name='" + name + '\'' +
                ", city='" + city + '\'' +
                ", localNodeId='" + localNodeId + '\'' +
                ", active=" + active +
                '}';
    }
}

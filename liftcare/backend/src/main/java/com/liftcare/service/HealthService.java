package com.liftcare.service;

import com.liftcare.dto.DatabaseStatus;
import com.liftcare.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DataSource dataSource;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/liftcare_db}")
    private String configuredDbUrl;

    @Value("${liftcare.environment:development}")
    private String environment;

    @Value("${liftcare.node-id:building-local-node-01}")
    private String nodeId;

    public HealthService(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthResponse getSystemHealth() {
        DatabaseStatus dbStatus = checkDatabaseConnectivity();

        Map<String, String> capabilities = new LinkedHashMap<>();
        capabilities.put("offlineResilience", "ENABLED - Operates on local building network");
        capabilities.put("module1Status", "Complete (Setup, Architecture, Health Check, CORS, Error Model)");
        capabilities.put("module2Status", "Complete (JPA Entities, Relationships, Repositories, Flyway Migrations)");
        capabilities.put("module3Status", "Complete (JWT Authentication, BCrypt Password Storage, Role-Based Access Control)");
        capabilities.put("module4Status", "Complete (Building and Elevator Management, Scoped Uniqueness, Operational Status)");
        capabilities.put("module5Status", "Complete (Incident Reporting, Local Triage, Severity Rules, Elevator Safety Transitions)");
        capabilities.put("module6Status", "Complete (Control-Room Dashboard, Real-Time Fleet & Incident Visibility, Offline Metrics)");
        capabilities.put("module7Status", "Complete (Local Building Server, Containerized Deployment, LAN Resilience & Health Telemetry)");
        capabilities.put("module8Status", "Complete (Incident History & Immutable Audit Trail, Actor Types, Multi-Dimensional Filtering)");
        capabilities.put("module9Status", "Complete (Offline Action Queue, Server Deduplication & Durable Notification Outbox)");
        capabilities.put("module10Status", "Complete (SLA Policies, Dynamic Deadlines, Background Monitoring & Automatic Escalation Engine)");
        capabilities.put("nextModule", "Module 11 (Technician Mobile Workflow & Shaft Diagnostic Checklist)");

        return new HealthResponse(
                "UP",
                "LiftCare Local Node Backend",
                "1.0.0-SNAPSHOT",
                "Module 1 through 10 - Local Server, Incident Lifecycle, Audit Trail, Offline Outbox & SLA Escalation Engine",
                environment,
                nodeId,
                Instant.now(),
                dbStatus,
                capabilities
        );
    }

    private String sanitizeDbUrl(String rawUrl) {
        if (rawUrl == null) return "unknown";
        // Mask any password in user:password@host format
        return rawUrl.replaceAll("(?i)(//[^:@]+):[^@]+@", "$1:****@");
    }

    private DatabaseStatus checkDatabaseConnectivity() {
        String safeUrl = sanitizeDbUrl(configuredDbUrl);
        if (dataSource == null) {
            return DatabaseStatus.disconnected(
                    safeUrl,
                    "No DataSource configured in the current Spring application context."
            );
        }

        try (Connection conn = dataSource.getConnection()) {
            if (conn != null && conn.isValid(2)) {
                DatabaseMetaData meta = conn.getMetaData();
                return DatabaseStatus.connected(
                        meta.getDatabaseProductName(),
                        meta.getDatabaseProductVersion(),
                        safeUrl
                );
            } else {
                return DatabaseStatus.disconnected(
                        safeUrl,
                        "Database connection probe failed validation."
                );
            }
        } catch (Exception ex) {
            log.debug("Database connection check failed: {}", ex.getMessage());
            return DatabaseStatus.disconnected(
                    safeUrl,
                    "Local PostgreSQL database is unreachable (" + ex.getClass().getSimpleName() + "). Ensure PostgreSQL container or service is active on the local building server."
            );
        }
    }
}

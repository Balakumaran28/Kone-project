package com.liftcare.service;

import com.liftcare.dto.IncidentSlaDetailResponse;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.*;
import com.liftcare.repository.IncidentHistoryRepository;
import com.liftcare.repository.IncidentRepository;
import com.liftcare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlaEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEvaluationService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository incidentHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationOutboxService notificationOutboxService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public SlaEvaluationService(
            IncidentRepository incidentRepository,
            IncidentHistoryRepository incidentHistoryRepository,
            UserRepository userRepository,
            NotificationOutboxService notificationOutboxService,
            @Autowired(required = false) Clock clock,
            @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.incidentRepository = incidentRepository;
        this.incidentHistoryRepository = incidentHistoryRepository;
        this.userRepository = userRepository;
        this.notificationOutboxService = notificationOutboxService;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    /**
     * Periodic scheduled evaluation of all eligible active incidents against their response and resolution SLAs.
     * Evaluates each incident within its own isolated transaction so a failure on one incident
     * never blocks or rolls back SLA processing for subsequent incidents.
     */
    public int evaluateSlaPoliciesAndEscalate() {
        Instant now = clock.instant();
        List<Incident> openIncidents = incidentRepository.findActiveIncidentsWithAssociations(DashboardService.ACTIVE_STATUSES);

        int evaluatedCount = 0;
        for (Incident incident : openIncidents) {
            try {
                if (transactionTemplate != null) {
                    transactionTemplate.executeWithoutResult(status -> evaluateIncident(incident, now));
                } else {
                    evaluateIncident(incident, now);
                }
                evaluatedCount++;
            } catch (Exception ex) {
                log.error("Failed to evaluate SLA for incident id={} ticket='{}': {}",
                        incident.getId(), incident.getTicketNumber(), ex.getMessage());
            }
        }

        return evaluatedCount;
    }

    /**
     * Evaluates a single incident against its SLA deadlines, triggering warnings, breaches, and escalations.
     */
    @Transactional
    public void evaluateIncident(Incident incident, Instant now) {
        if (incident == null) return;

        // Re-attach or reload with associations if detached
        if (incident.getId() != null) {
            incident = incidentRepository.findById(incident.getId()).orElse(incident);
        }

        // 1. Missing policy inspection
        if (incident.getSlaPolicy() == null && incident.getResponseDeadline() == null) {
            // Already flagged as POLICY_MISSING
            return;
        }

        boolean updated = false;

        // 2. Pre-deadline Warning Check (Approaching Response or Resolution Deadline)
        if (incident.getSlaWarningThresholdMinutes() != null && incident.getSlaWarningThresholdMinutes() > 0 && !incident.isSlaWarningSent()) {
            boolean responseApproaching = false;
            if (incident.getAcknowledgedAt() == null && incident.getResponseDeadline() != null) {
                Instant respWarnTime = incident.getResponseDeadline().minus(Duration.ofMinutes(incident.getSlaWarningThresholdMinutes()));
                if (!now.isBefore(respWarnTime) && now.isBefore(incident.getResponseDeadline())) {
                    responseApproaching = true;
                }
            }

            boolean resolutionApproaching = false;
            if (incident.getResolvedAt() == null && incident.getResolutionDeadline() != null) {
                Instant resolWarnTime = incident.getResolutionDeadline().minus(Duration.ofMinutes(incident.getSlaWarningThresholdMinutes()));
                if (!now.isBefore(resolWarnTime) && now.isBefore(incident.getResolutionDeadline())) {
                    if (incident.getAcknowledgedAt() != null) {
                        resolutionApproaching = true;
                    }
                }
            }

            if (responseApproaching || resolutionApproaching) {
                incident.setSlaWarningSent(true);
                updated = true;

                String warningType = responseApproaching ? "Response deadline" : "Resolution deadline";
                String warnScope = responseApproaching ? "RESPONSE" : "RESOLUTION";
                long minutesLeft = responseApproaching
                        ? Math.max(0, Duration.between(now, incident.getResponseDeadline()).toMinutes())
                        : Math.max(0, Duration.between(now, incident.getResolutionDeadline()).toMinutes());

                String remarks = String.format("%s approaching: approximately %d minutes remaining.", warningType, minutesLeft);
                IncidentHistory warningHistory = new IncidentHistory(
                        incident,
                        "SLA_WARNING_TRIGGERED",
                        "ON_TRACK",
                        "APPROACHING_DEADLINE",
                        null,
                        ActorType.SYSTEM,
                        remarks
                );
                incidentHistoryRepository.save(warningHistory);

                if (notificationOutboxService != null) {
                    notificationOutboxService.queueSlaWarning(incident, warnScope, remarks);
                }
                log.info("Triggered SLA warning for ticket='{}' ({})", incident.getTicketNumber(), remarks);
            }
        }

        // 3. Response SLA Evaluation and Tier 1 / Tier 2 Escalation
        if (incident.getAcknowledgedAt() == null && incident.getResponseDeadline() != null && now.isAfter(incident.getResponseDeadline())) {
            if (!incident.isResponseSlaBreached()) {
                incident.setResponseSlaBreached(true);
                updated = true;

                IncidentHistory breachHistory = new IncidentHistory(
                        incident,
                        "RESPONSE_SLA_BREACHED",
                        "REPORTED",
                        "BREACHED",
                        null,
                        ActorType.SYSTEM,
                        String.format("Response SLA breached: Target was %d minutes. Incident remains unacknowledged.",
                                incident.getSlaResponseTargetMinutes() != null ? incident.getSlaResponseTargetMinutes() : 0)
                );
                incidentHistoryRepository.save(breachHistory);
                log.warn("Response SLA breached for ticket='{}'", incident.getTicketNumber());
            }

            // Escalation Tier 1: Notify on-duty technician / operational staff
            if (incident.getEscalationLevel() < 1) {
                UserRole targetRole = (incident.getSlaPolicy() != null && incident.getSlaPolicy().getTier1Role() != null)
                        ? incident.getSlaPolicy().getTier1Role()
                        : UserRole.TECHNICIAN;

                List<User> recipients = resolveRecipients(incident, targetRole);
                int oldLevel = incident.getEscalationLevel();
                incident.setEscalationLevel(1);
                incident.setLastEscalatedAt(now);
                updated = true;

                if (!recipients.isEmpty()) {
                    String remarks = String.format("Escalated to Tier 1 (%s) due to unacknowledged response SLA breach.", targetRole);
                    IncidentHistory escHistory = new IncidentHistory(
                            incident,
                            "INCIDENT_ESCALATED",
                            String.valueOf(oldLevel),
                            "1",
                            null,
                            ActorType.SYSTEM,
                            remarks
                    );
                    incidentHistoryRepository.save(escHistory);

                    if (notificationOutboxService != null) {
                        notificationOutboxService.queueIncidentEscalated(incident, 1, remarks, recipients);
                    }
                    log.info("Incident ticket='{}' escalated to Tier 1 ({})", incident.getTicketNumber(), targetRole);
                } else {
                    String failRemarks = String.format("No active user found with role %s for Tier 1 escalation.", targetRole);
                    IncidentHistory failHistory = new IncidentHistory(
                            incident,
                            "ESCALATION_DELIVERY_FAILED",
                            String.valueOf(oldLevel),
                            "1",
                            null,
                            ActorType.SYSTEM,
                            failRemarks
                    );
                    incidentHistoryRepository.save(failHistory);

                    if (notificationOutboxService != null) {
                        notificationOutboxService.queueEscalationFailure(incident, 1, failRemarks);
                    }
                    log.warn("Tier 1 escalation recipient missing for ticket='{}'", incident.getTicketNumber());
                }
            }
            // Escalation Tier 2: Persistent unacknowledged breach past second threshold
            else if (incident.getEscalationLevel() == 1) {
                int respTarget = incident.getSlaResponseTargetMinutes() != null ? incident.getSlaResponseTargetMinutes() : 30;
                Instant tier2Threshold = incident.getResponseDeadline().plus(Duration.ofMinutes(Math.max(10, respTarget)));

                if (now.isAfter(tier2Threshold)) {
                    UserRole targetRole = (incident.getSlaPolicy() != null && incident.getSlaPolicy().getTier2Role() != null)
                            ? incident.getSlaPolicy().getTier2Role()
                            : UserRole.BUILDING_MANAGER;

                    List<User> recipients = resolveRecipients(incident, targetRole);
                    int oldLevel = incident.getEscalationLevel();
                    incident.setEscalationLevel(2);
                    incident.setLastEscalatedAt(now);
                    updated = true;

                    if (!recipients.isEmpty()) {
                        String remarks = String.format("Escalated to Tier 2 (%s) due to prolonged unacknowledged response breach.", targetRole);
                        IncidentHistory escHistory = new IncidentHistory(
                                incident,
                                "INCIDENT_ESCALATED",
                                String.valueOf(oldLevel),
                                "2",
                                null,
                                ActorType.SYSTEM,
                                remarks
                        );
                        incidentHistoryRepository.save(escHistory);

                        if (notificationOutboxService != null) {
                            notificationOutboxService.queueIncidentEscalated(incident, 2, remarks, recipients);
                        }
                        log.info("Incident ticket='{}' escalated to Tier 2 ({})", incident.getTicketNumber(), targetRole);
                    } else {
                        String failRemarks = String.format("No active user found with role %s for Tier 2 escalation.", targetRole);
                        IncidentHistory failHistory = new IncidentHistory(
                                incident,
                                "ESCALATION_DELIVERY_FAILED",
                                String.valueOf(oldLevel),
                                "2",
                                null,
                                ActorType.SYSTEM,
                                failRemarks
                        );
                        incidentHistoryRepository.save(failHistory);

                        if (notificationOutboxService != null) {
                            notificationOutboxService.queueEscalationFailure(incident, 2, failRemarks);
                        }
                    }
                }
            }
        }

        // 4. Resolution SLA Evaluation and Tier 3 Escalation
        if (incident.getResolvedAt() == null && incident.getResolutionDeadline() != null && now.isAfter(incident.getResolutionDeadline())) {
            if (!incident.isResolutionSlaBreached()) {
                incident.setResolutionSlaBreached(true);
                updated = true;

                IncidentHistory breachHistory = new IncidentHistory(
                        incident,
                        "RESOLUTION_SLA_BREACHED",
                        incident.getStatus().name(),
                        "BREACHED",
                        null,
                        ActorType.SYSTEM,
                        String.format("Resolution SLA breached: Target was %d minutes. Incident remains unresolved.",
                                incident.getSlaResolutionTargetMinutes() != null ? incident.getSlaResolutionTargetMinutes() : 0)
                );
                incidentHistoryRepository.save(breachHistory);
                log.warn("Resolution SLA breached for ticket='{}'", incident.getTicketNumber());
            }

            // Escalation Tier 3: Executive alert to Company Supervisor / Administrator
            if (incident.getEscalationLevel() < 3) {
                UserRole targetRole = (incident.getSlaPolicy() != null && incident.getSlaPolicy().getTier3Role() != null)
                        ? incident.getSlaPolicy().getTier3Role()
                        : UserRole.COMPANY_SUPERVISOR;

                List<User> recipients = resolveRecipients(incident, targetRole);
                if (recipients.isEmpty()) {
                    recipients = userRepository.findByRoleInAndActiveTrue(List.of(UserRole.ADMINISTRATOR));
                }

                int oldLevel = incident.getEscalationLevel();
                incident.setEscalationLevel(3);
                incident.setLastEscalatedAt(now);
                updated = true;

                if (!recipients.isEmpty()) {
                    String remarks = String.format("Escalated to Tier 3 (%s) due to resolution SLA breach.", targetRole);
                    IncidentHistory escHistory = new IncidentHistory(
                            incident,
                            "INCIDENT_ESCALATED",
                            String.valueOf(oldLevel),
                            "3",
                            null,
                            ActorType.SYSTEM,
                            remarks
                    );
                    incidentHistoryRepository.save(escHistory);

                    if (notificationOutboxService != null) {
                        notificationOutboxService.queueIncidentEscalated(incident, 3, remarks, recipients);
                    }
                    log.info("Incident ticket='{}' escalated to Tier 3 ({})", incident.getTicketNumber(), targetRole);
                } else {
                    String failRemarks = String.format("No active user found with role %s for Tier 3 escalation.", targetRole);
                    IncidentHistory failHistory = new IncidentHistory(
                            incident,
                            "ESCALATION_DELIVERY_FAILED",
                            String.valueOf(oldLevel),
                            "3",
                            null,
                            ActorType.SYSTEM,
                            failRemarks
                    );
                    incidentHistoryRepository.save(failHistory);

                    if (notificationOutboxService != null) {
                        notificationOutboxService.queueEscalationFailure(incident, 3, failRemarks);
                    }
                }
            }
        }

        if (updated) {
            incidentRepository.save(incident);
        }
    }

    /**
     * Resolves active recipients for an escalation tier based on target role.
     */
    private List<User> resolveRecipients(Incident incident, UserRole role) {
        List<User> users = new ArrayList<>();
        if (role == UserRole.TECHNICIAN && incident.getAssignedTo() != null && incident.getAssignedTo().isActive()) {
            users.add(incident.getAssignedTo());
            return users;
        }

        return userRepository.findByRoleInAndActiveTrue(List.of(role));
    }

    /**
     * Retrieves detailed SLA compliance telemetry for a specific incident.
     */
    @Transactional(readOnly = true)
    public IncidentSlaDetailResponse getIncidentSlaDetail(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
        return IncidentSlaDetailResponse.from(incident, clock.instant());
    }
}

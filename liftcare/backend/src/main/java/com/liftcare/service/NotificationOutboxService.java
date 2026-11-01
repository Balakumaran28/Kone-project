package com.liftcare.service;

import com.liftcare.dto.NotificationOutboxSummaryResponse;
import com.liftcare.dto.NotificationResponse;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.*;
import com.liftcare.repository.NotificationOutboxRepository;
import com.liftcare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final UserRepository userRepository;

    public NotificationOutboxService(
            NotificationOutboxRepository notificationOutboxRepository,
            UserRepository userRepository) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.userRepository = userRepository;
    }

    /**
     * Enqueues durable notification outbox records when an incident is created.
     */
    @Transactional
    public void queueIncidentCreated(Incident incident) {
        if (incident == null) return;

        // 1. Notify Building Managers & Security Staff on the local building server node
        List<User> staff = userRepository.findByRoleInAndActiveTrue(
                List.of(UserRole.BUILDING_MANAGER, UserRole.SECURITY_STAFF, UserRole.ADMINISTRATOR)
        );

        for (User recipient : staff) {
            String dedupKey = String.format("INC-CREATE-%d-%d", incident.getId(), recipient.getId());
            if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                String subject = String.format("New Incident Reported: %s (%s)", incident.getTicketNumber(), incident.getSeverity());
                String body = String.format("Elevator %s in %s reported: %s",
                        incident.getElevator().getElevatorCode(),
                        incident.getBuilding().getName(),
                        incident.getDescription());

                NotificationOutbox outbox = new NotificationOutbox(
                        incident,
                        recipient,
                        recipient.getRole().name(),
                        recipient.getEmail(),
                        NotificationType.IN_APP,
                        "INCIDENT_CREATED",
                        subject,
                        body,
                        dedupKey
                );
                // In-app notifications are delivered immediately to the local database queue
                outbox.setDeliveryState(NotificationDeliveryState.SENT);
                outbox.setDeliveredAt(Instant.now());
                notificationOutboxRepository.save(outbox);
            }
        }

        // 2. Critical passenger entrapment safety alert
        if (incident.isEntrapmentReported() || incident.getSeverity() == SeverityLevel.CRITICAL) {
            for (User recipient : staff) {
                String dedupKey = String.format("INC-ENTRAPMENT-%d-%d", incident.getId(), recipient.getId());
                if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                    String subject = String.format("EMERGENCY ENTRAPMENT: %s in %s", incident.getTicketNumber(), incident.getElevator().getElevatorCode());
                    String body = String.format("Urgent safety alert: Passenger entrapment reported at %s (%s). Dispatch emergency response.",
                            incident.getElevator().getElevatorCode(),
                            incident.getBuilding().getName());

                    NotificationOutbox outbox = new NotificationOutbox(
                            incident,
                            recipient,
                            recipient.getRole().name(),
                            recipient.getEmail(),
                            NotificationType.SAFETY_ALERT,
                            "CRITICAL_ENTRAPMENT",
                            subject,
                            body,
                            dedupKey
                    );
                    outbox.setDeliveryState(NotificationDeliveryState.SENT);
                    outbox.setDeliveredAt(Instant.now());
                    notificationOutboxRepository.save(outbox);
                }
            }
        }
    }

    /**
     * Enqueues durable notification outbox record when a technician is dispatched.
     */
    @Transactional
    public void queueIncidentAssigned(Incident incident, User technician) {
        if (incident == null || technician == null) return;

        String dedupKey = String.format("INC-ASSIGN-%d-%d", incident.getId(), technician.getId());
        if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
            String subject = String.format("Dispatch Assignment: Incident %s", incident.getTicketNumber());
            String body = String.format("You have been assigned to service elevator %s at %s. Severity: %s.",
                    incident.getElevator().getElevatorCode(),
                    incident.getBuilding().getName(),
                    incident.getSeverity());

            NotificationOutbox outbox = new NotificationOutbox(
                    incident,
                    technician,
                    technician.getRole().name(),
                    technician.getEmail(),
                    NotificationType.DISPATCH_NOTICE,
                    "INCIDENT_ASSIGNED",
                    subject,
                    body,
                    dedupKey
            );
            outbox.setDeliveryState(NotificationDeliveryState.SENT);
            outbox.setDeliveredAt(Instant.now());
            notificationOutboxRepository.save(outbox);
            log.info("Enqueued dispatch notification for technician id={} on ticket={}", technician.getId(), incident.getTicketNumber());
        }
    }

    /**
     * Enqueues notification outbox record when incident status changes (e.g. RESOLVED or CLOSED).
     */
    @Transactional
    public void queueIncidentStatusTransition(Incident incident, IncidentStatus oldStatus, IncidentStatus newStatus) {
        if (incident == null || newStatus == null) return;

        // If RESOLVED or CLOSED, notify reporting resident
        if ((newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) && incident.getReportedBy() != null) {
            User resident = incident.getReportedBy();
            String dedupKey = String.format("INC-STATUS-%d-%s-%d", incident.getId(), newStatus.name(), resident.getId());
            if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                String subject = String.format("Incident %s has been %s", incident.getTicketNumber(), newStatus.name());
                String body = String.format("Your reported issue with elevator %s has been %s.",
                        incident.getElevator().getElevatorCode(),
                        newStatus.name().toLowerCase());

                NotificationOutbox outbox = new NotificationOutbox(
                        incident,
                        resident,
                        resident.getRole().name(),
                        resident.getEmail(),
                        NotificationType.STATUS_UPDATE,
                        "INCIDENT_" + newStatus.name(),
                        subject,
                        body,
                        dedupKey
                );
                outbox.setDeliveryState(NotificationDeliveryState.SENT);
                outbox.setDeliveredAt(Instant.now());
                notificationOutboxRepository.save(outbox);
            }
        }
    }

    /**
     * Retrieves in-app notifications for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(User currentUser) {
        if (currentUser == null) return Collections.emptyList();
        return notificationOutboxRepository.findByRecipientUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * Enqueues durable notification outbox record when an SLA deadline is approaching.
     */
    @Transactional
    public void queueSlaWarning(Incident incident, String warningDetails) {
        queueSlaWarning(incident, "RESPONSE", warningDetails);
    }

    /**
     * Enqueues durable notification outbox records with explicit warning scope (RESPONSE vs RESOLUTION).
     */
    @Transactional
    public void queueSlaWarning(Incident incident, String warningScope, String warningDetails) {
        if (incident == null) return;

        List<User> recipients = new ArrayList<>();
        if (incident.getAssignedTo() != null && incident.getAssignedTo().isActive()) {
            recipients.add(incident.getAssignedTo());
        }
        recipients.addAll(userRepository.findByRoleInAndActiveTrue(
                List.of(UserRole.BUILDING_MANAGER, UserRole.SECURITY_STAFF)
        ));

        String scopeKey = warningScope != null ? warningScope.toUpperCase() : "RESPONSE";
        for (User recipient : recipients) {
            String dedupKey = String.format("SLA-WARN-%s-%d-%d", scopeKey, incident.getId(), recipient.getId());
            if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                String scopeLabel = "RESOLUTION".equals(scopeKey) ? "Resolution" : "Response";
                String subject = String.format("SLA %s Warning: Incident %s Approaching Deadline", scopeLabel, incident.getTicketNumber());
                String body = String.format("Incident %s (%s, %s): %s",
                        incident.getTicketNumber(),
                        incident.getElevator() != null ? incident.getElevator().getElevatorCode() : "Elevator",
                        incident.getSeverity(),
                        warningDetails);

                NotificationOutbox outbox = new NotificationOutbox(
                        incident,
                        recipient,
                        recipient.getRole().name(),
                        recipient.getEmail(),
                        NotificationType.IN_APP,
                        "SLA_WARNING_TRIGGERED",
                        subject,
                        body,
                        dedupKey
                );
                outbox.setDeliveryState(NotificationDeliveryState.SENT);
                outbox.setDeliveredAt(Instant.now());
                notificationOutboxRepository.save(outbox);
            }
        }
    }

    /**
     * Enqueues durable notification outbox records when an incident is escalated.
     */
    @Transactional
    public void queueIncidentEscalated(Incident incident, int tier, String reason, List<User> recipients) {
        if (incident == null || recipients == null || recipients.isEmpty()) return;

        NotificationType type = (tier >= 3 || incident.isEntrapmentReported())
                ? NotificationType.SAFETY_ALERT
                : NotificationType.DISPATCH_NOTICE;

        for (User recipient : recipients) {
            String dedupKey = String.format("SLA-ESC-%d-TIER-%d-%d", incident.getId(), tier, recipient.getId());
            if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                String subject = String.format("🚨 TIER %d ESCALATION: Incident %s", tier, incident.getTicketNumber());
                String body = String.format("Elevator %s in %s has escalated to Tier %d. Reason: %s",
                        incident.getElevator() != null ? incident.getElevator().getElevatorCode() : "Unknown Elevator",
                        incident.getBuilding() != null ? incident.getBuilding().getName() : "Building",
                        tier,
                        reason);

                NotificationOutbox outbox = new NotificationOutbox(
                        incident,
                        recipient,
                        recipient.getRole().name(),
                        recipient.getEmail(),
                        type,
                        "INCIDENT_ESCALATED",
                        subject,
                        body,
                        dedupKey
                );
                outbox.setDeliveryState(NotificationDeliveryState.SENT);
                outbox.setDeliveredAt(Instant.now());
                notificationOutboxRepository.save(outbox);
            }
        }
    }

    /**
     * Enqueues durable notification outbox record when an escalation could not find active recipients.
     */
    @Transactional
    public void queueEscalationFailure(Incident incident, int tier, String reason) {
        if (incident == null) return;

        List<User> admins = userRepository.findByRoleInAndActiveTrue(
                List.of(UserRole.ADMINISTRATOR, UserRole.BUILDING_MANAGER)
        );

        for (User admin : admins) {
            String dedupKey = String.format("SLA-ESC-FAIL-%d-TIER-%d-%d", incident.getId(), tier, admin.getId());
            if (!notificationOutboxRepository.existsByDeduplicationKey(dedupKey)) {
                String subject = String.format("⚠️ Escalation Dispatch Warning: Tier %d on %s", tier, incident.getTicketNumber());
                String body = String.format("Escalation to Tier %d for ticket %s could not dispatch to target role: %s. Administrative intervention required.",
                        tier, incident.getTicketNumber(), reason);

                NotificationOutbox outbox = new NotificationOutbox(
                        incident,
                        admin,
                        admin.getRole().name(),
                        admin.getEmail(),
                        NotificationType.SAFETY_ALERT,
                        "ESCALATION_DELIVERY_FAILED",
                        subject,
                        body,
                        dedupKey
                );
                outbox.setDeliveryState(NotificationDeliveryState.SENT);
                outbox.setDeliveredAt(Instant.now());
                notificationOutboxRepository.save(outbox);
            }
        }
    }

    /**
     * Marks an in-app notification as read.
     */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, User currentUser) {
        NotificationOutbox notification = notificationOutboxRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (notification.getRecipientUser() != null && !notification.getRecipientUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied. You can only mark your own notifications as read.");
        }

        notification.setReadAt(Instant.now());
        NotificationOutbox saved = notificationOutboxRepository.save(notification);
        return NotificationResponse.from(saved);
    }

    /**
     * Retrieves outbox summary counts for operational staff inspection.
     */
    @Transactional(readOnly = true)
    public NotificationOutboxSummaryResponse getOutboxSummary() {
        long pending = notificationOutboxRepository.countByDeliveryState(NotificationDeliveryState.PENDING);
        long processing = notificationOutboxRepository.countByDeliveryState(NotificationDeliveryState.PROCESSING);
        long sent = notificationOutboxRepository.countByDeliveryState(NotificationDeliveryState.SENT);
        long retryWait = notificationOutboxRepository.countByDeliveryState(NotificationDeliveryState.RETRY_WAIT);
        long failed = notificationOutboxRepository.countByDeliveryState(NotificationDeliveryState.FAILED);
        long total = notificationOutboxRepository.count();

        Map<String, Long> map = new LinkedHashMap<>();
        map.put("PENDING", pending);
        map.put("PROCESSING", processing);
        map.put("SENT", sent);
        map.put("RETRY_WAIT", retryWait);
        map.put("FAILED", failed);

        return new NotificationOutboxSummaryResponse(
                total, pending, processing, sent, retryWait, failed, map
        );
    }

    /**
     * Retrieves paginated outbox entries for operational management.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getOutboxPage(NotificationDeliveryState state, Pageable pageable) {
        Page<NotificationOutbox> page;
        if (state != null) {
            page = notificationOutboxRepository.findByDeliveryState(state, pageable);
        } else {
            page = notificationOutboxRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(NotificationResponse::from);
    }
}

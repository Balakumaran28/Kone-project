package com.liftcare.dto;

import com.liftcare.model.NotificationOutbox;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String notificationUid,
        Long incidentId,
        String incidentTicket,
        Long recipientUserId,
        String recipientRole,
        String notificationType,
        String eventType,
        String subject,
        String messageBody,
        String deliveryState,
        int attemptCount,
        boolean read,
        Instant deliveredAt,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(NotificationOutbox o) {
        return new NotificationResponse(
                o.getId(),
                o.getNotificationUid(),
                o.getIncident() != null ? o.getIncident().getId() : null,
                o.getIncident() != null ? o.getIncident().getTicketNumber() : null,
                o.getRecipientUser() != null ? o.getRecipientUser().getId() : null,
                o.getRecipientRole(),
                o.getNotificationType().name(),
                o.getEventType(),
                o.getSubject(),
                o.getMessageBody(),
                o.getDeliveryState().name(),
                o.getAttemptCount(),
                o.getReadAt() != null,
                o.getDeliveredAt(),
                o.getReadAt(),
                o.getCreatedAt()
        );
    }
}

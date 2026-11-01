package com.liftcare.repository;

import com.liftcare.model.NotificationDeliveryState;
import com.liftcare.model.NotificationOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByNotificationUid(String notificationUid);

    boolean existsByDeduplicationKey(String deduplicationKey);

    List<NotificationOutbox> findByIncidentId(Long incidentId);

    List<NotificationOutbox> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    List<NotificationOutbox> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientUserId);

    Page<NotificationOutbox> findByDeliveryState(NotificationDeliveryState deliveryState, Pageable pageable);

    Page<NotificationOutbox> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByDeliveryState(NotificationDeliveryState deliveryState);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);
}

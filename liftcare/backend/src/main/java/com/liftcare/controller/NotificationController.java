package com.liftcare.controller;

import com.liftcare.dto.NotificationOutboxSummaryResponse;
import com.liftcare.dto.NotificationResponse;
import com.liftcare.model.NotificationDeliveryState;
import com.liftcare.security.CustomUserDetails;
import com.liftcare.service.NotificationOutboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationOutboxService notificationOutboxService;

    public NotificationController(NotificationOutboxService notificationOutboxService) {
        this.notificationOutboxService = notificationOutboxService;
    }

    /**
     * Retrieve in-app notifications for the currently authenticated user.
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<NotificationResponse> list = notificationOutboxService.getMyNotifications(userDetails.getUser());
        return ResponseEntity.ok(list);
    }

    /**
     * Mark an in-app notification as read.
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NotificationResponse updated = notificationOutboxService.markAsRead(id, userDetails.getUser());
        return ResponseEntity.ok(updated);
    }

    /**
     * Operational staff inspection endpoint for durable notification outbox summary counts.
     */
    @GetMapping("/outbox")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<NotificationOutboxSummaryResponse> getOutboxSummary() {
        NotificationOutboxSummaryResponse summary = notificationOutboxService.getOutboxSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Operational staff inspection endpoint for paginated notification outbox queue entries.
     */
    @GetMapping("/outbox/items")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<Page<NotificationResponse>> getOutboxItems(
            @RequestParam(required = false) NotificationDeliveryState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponse> items = notificationOutboxService.getOutboxPage(state, pageable);
        return ResponseEntity.ok(items);
    }
}

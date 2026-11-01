package com.liftcare.service;

import com.liftcare.dto.CloudSyncBatchRequest;
import com.liftcare.dto.CloudSyncBatchResponse;
import com.liftcare.dto.LocalSyncStatusResponse;
import com.liftcare.dto.SyncEventItemDto;
import com.liftcare.model.CloudSyncCheckpoint;
import com.liftcare.model.CloudSyncOutbox;
import com.liftcare.repository.CloudSyncCheckpointRepository;
import com.liftcare.repository.CloudSyncOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LocalCloudSyncClientService {

    private static final Logger log = LoggerFactory.getLogger(LocalCloudSyncClientService.class);

    private final CloudSyncOutboxRepository outboxRepository;
    private final CloudSyncCheckpointRepository checkpointRepository;
    private final CloudSyncIngestionService ingestionService;

    // Simulation toggle to test offline cloud behavior without breaking server
    private final AtomicBoolean simulatedCloudOffline = new AtomicBoolean(false);

    @Value("${liftcare.building.code:BLD-A}")
    private String defaultBuildingCode;

    public LocalCloudSyncClientService(CloudSyncOutboxRepository outboxRepository,
                                       CloudSyncCheckpointRepository checkpointRepository,
                                       CloudSyncIngestionService ingestionService) {
        this.outboxRepository = outboxRepository;
        this.checkpointRepository = checkpointRepository;
        this.ingestionService = ingestionService;
    }

    public void setSimulatedCloudOffline(boolean offline) {
        this.simulatedCloudOffline.set(offline);
        log.info("Simulated cloud connectivity set to: {}", offline ? "OFFLINE" : "ONLINE");
    }

    public boolean isSimulatedCloudOffline() {
        return this.simulatedCloudOffline.get();
    }

    @Transactional(readOnly = true)
    public LocalSyncStatusResponse getLocalSyncStatus(String buildingCode) {
        String code = resolveCode(buildingCode);
        CloudSyncCheckpoint checkpoint = checkpointRepository.findByBuildingCode(code)
                .orElseGet(() -> new CloudSyncCheckpoint(code));

        long pending = outboxRepository.countByBuildingCodeAndStatus(code, "PENDING");
        long failed = outboxRepository.countByBuildingCodeAndStatus(code, "FAILED");

        String status = simulatedCloudOffline.get() ? "OFFLINE" : checkpoint.getCloudConnectionStatus();

        return new LocalSyncStatusResponse(
                code,
                status,
                checkpoint.getLastSuccessfulSyncAt(),
                checkpoint.getLastSyncAttemptAt(),
                (int) pending,
                (int) failed,
                checkpoint.getLastSyncError(),
                true // Local building operations are ALWAYS available
        );
    }

    @Transactional
    public CloudSyncBatchResponse triggerLocalSync(String buildingCode) {
        String code = resolveCode(buildingCode);
        CloudSyncCheckpoint checkpoint = checkpointRepository.findByBuildingCode(code)
                .orElseGet(() -> new CloudSyncCheckpoint(code));

        checkpoint.setLastSyncAttemptAt(Instant.now());

        // 1. Check if simulated or real cloud is unreachable
        if (simulatedCloudOffline.get()) {
            checkpoint.setCloudConnectionStatus("OFFLINE");
            checkpoint.setLastSyncError("Cloud coordination server unreachable (network timeout / simulated offline)");
            checkpointRepository.save(checkpoint);
            log.warn("Local sync aborted for building='{}': Cloud is offline.", code);
            return new CloudSyncBatchResponse(
                    UUID.randomUUID().toString(),
                    code,
                    0, 0, 0, 0, 0,
                    "OFFLINE",
                    List.of(), List.of(),
                    Instant.now()
            );
        }

        // 2. Fetch pending and retry-eligible failed outbox records
        List<CloudSyncOutbox> pending = outboxRepository.findByBuildingCodeAndStatusOrderByCreatedAtAsc(code, "PENDING");
        List<CloudSyncOutbox> retryableFailed = outboxRepository.findByBuildingCodeAndStatusOrderByCreatedAtAsc(code, "FAILED")
                .stream()
                .filter(item -> item.getRetryCount() < 5)
                .toList();

        List<CloudSyncOutbox> toSync = new java.util.ArrayList<>(pending);
        toSync.addAll(retryableFailed);
        toSync.sort(java.util.Comparator.comparing(CloudSyncOutbox::getCreatedAt));

        if (toSync.isEmpty()) {
            checkpoint.setCloudConnectionStatus("ONLINE");
            checkpoint.setPendingEventsCount(0);
            checkpointRepository.save(checkpoint);
            return new CloudSyncBatchResponse(
                    UUID.randomUUID().toString(),
                    code,
                    0, 0, 0, 0, 0,
                    "SUCCESS",
                    List.of(), List.of(),
                    Instant.now()
            );
        }

        // 3. Mark items as SYNCING
        for (CloudSyncOutbox item : toSync) {
            item.setStatus("SYNCING");
            item.setLastAttemptAt(Instant.now());
            item.setRetryCount(item.getRetryCount() + 1);
        }
        outboxRepository.saveAll(toSync);

        // 4. Build batch request
        List<SyncEventItemDto> eventDtos = toSync.stream()
                .map(item -> new SyncEventItemDto(
                        item.getEventId(),
                        item.getEventType(),
                        item.getAggregateType(),
                        item.getAggregateId(),
                        item.getPayload(),
                        item.getIdempotencyKey()
                ))
                .toList();

        String batchId = UUID.randomUUID().toString();
        CloudSyncBatchRequest batchRequest = new CloudSyncBatchRequest(code, batchId, eventDtos, Instant.now());

        try {
            // Ingest into cloud service
            CloudSyncBatchResponse response = ingestionService.ingestBatch(code, batchRequest);

            // 5. Update local outbox items on success
            Instant now = Instant.now();
            for (CloudSyncOutbox item : toSync) {
                if (response.processedEventIds().contains(item.getEventId())) {
                    item.setStatus("SYNCED");
                    item.setSyncedAt(now);
                    item.setErrorMessage(null);
                } else if (response.failedEventIds().contains(item.getEventId())) {
                    item.setStatus("FAILED");
                    item.setErrorMessage("Cloud failed to process event");
                }
            }
            outboxRepository.saveAll(toSync);

            checkpoint.setCloudConnectionStatus("ONLINE");
            checkpoint.setLastSuccessfulSyncAt(now);
            checkpoint.setLastSyncError(null);
            checkpoint.setPendingEventsCount((int) outboxRepository.countByBuildingCodeAndStatus(code, "PENDING"));
            checkpoint.setFailedEventsCount((int) outboxRepository.countByBuildingCodeAndStatus(code, "FAILED"));
            checkpointRepository.save(checkpoint);

            log.info("Local sync completed for building='{}' batchId='{}': processed={}, duplicates={}, conflicts={}, failed={}",
                    code, batchId, response.processedCount(), response.duplicateCount(),
                    response.conflictCount(), response.rejectedCount());

            return response;
        } catch (Exception ex) {
            log.error("Cloud synchronization failed for building='{}': {}", code, ex.getMessage());
            for (CloudSyncOutbox item : toSync) {
                item.setStatus("FAILED");
                item.setErrorMessage(ex.getMessage());
            }
            outboxRepository.saveAll(toSync);

            checkpoint.setCloudConnectionStatus("ERROR");
            checkpoint.setLastSyncError(ex.getMessage());
            checkpoint.setPendingEventsCount((int) outboxRepository.countByBuildingCodeAndStatus(code, "PENDING"));
            checkpoint.setFailedEventsCount((int) outboxRepository.countByBuildingCodeAndStatus(code, "FAILED"));
            checkpointRepository.save(checkpoint);

            return new CloudSyncBatchResponse(
                    batchId,
                    code,
                    toSync.size(),
                    0, 0, 0, toSync.size(),
                    "FAILED",
                    List.of(),
                    toSync.stream().map(CloudSyncOutbox::getEventId).toList(),
                    Instant.now()
            );
        }
    }

    private String resolveCode(String code) {
        return (code != null && !code.isBlank()) ? code.trim().toUpperCase() : defaultBuildingCode.toUpperCase();
    }
}

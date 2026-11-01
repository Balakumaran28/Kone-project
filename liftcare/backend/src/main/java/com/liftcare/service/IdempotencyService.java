package com.liftcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.model.IdempotencyRecord;
import com.liftcare.model.User;
import com.liftcare.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    public String computePayloadHash(Object payload) {
        if (payload == null) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty hash
        }
        try {
            String json;
            if (payload instanceof String str) {
                json = str.trim();
            } else {
                json = objectMapper.writeValueAsString(payload);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        } catch (Exception e) {
            log.warn("Could not serialize payload for idempotency hashing: {}", e.getMessage());
            return "hash-fallback-" + payload.hashCode();
        }
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findExistingRecord(User user, String idempotencyKey, String currentPayloadHash) {
        if (user == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyRecord> recordOpt = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(
                user.getId(), idempotencyKey.trim()
        );

        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if (!record.getRequestHash().equals(currentPayloadHash)) {
                throw new IllegalArgumentException(
                        "Idempotency key '" + idempotencyKey + "' has already been used with a different request payload. Reusing keys with different payloads is prohibited."
                );
            }
            return Optional.of(record);
        }

        return Optional.empty();
    }

    @Transactional
    public IdempotencyRecord saveSuccessRecord(String idempotencyKey, User user, String endpoint, String requestHash, int status, Object responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || user == null) {
            return null;
        }

        String bodyJson;
        try {
            if (responseBody instanceof String str) {
                bodyJson = str;
            } else {
                bodyJson = objectMapper.writeValueAsString(responseBody);
            }
        } catch (Exception e) {
            bodyJson = "{}";
        }

        IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey.trim(),
                user,
                endpoint,
                requestHash,
                status,
                bodyJson
        );

        IdempotencyRecord saved = idempotencyRecordRepository.save(record);
        log.info("Persisted idempotency key='{}' for user id={} on endpoint '{}'", idempotencyKey, user.getId(), endpoint);
        return saved;
    }
}

package com.nazila.ordermgmt.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Produces a stable fingerprint of a request payload so that a replayed
 * {@code Idempotency-Key} can be distinguished from the same key being
 * reused for a genuinely different request (which is a client bug, not a
 * safe-to-replay retry).
 */
@Component
public class IdempotencyKeyHasher {

    private final ObjectMapper objectMapper;

    public IdempotencyKeyHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(Object payload) {
        try {
            byte[] canonicalJson = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonicalJson);
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash request payload for idempotency check", e);
        }
    }
}

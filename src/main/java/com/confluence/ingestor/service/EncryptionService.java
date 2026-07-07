package com.confluence.ingestor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Optional AES-GCM encryption for PAT at rest when {@code CONFLUENCE_INGESTOR_ENCRYPTION_KEY} is set
 * (32-byte key, Base64-encoded).
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ENV_KEY = "CONFLUENCE_INGESTOR_ENCRYPTION_KEY";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final Optional<SecretKey> secretKey;

    public EncryptionService() {
        this.secretKey = loadKeyFromEnvironment();
        if (this.secretKey.isEmpty()) {
            log.info(
                    "Encryption key not configured ({}). PAT will be kept in memory only and not persisted to disk.",
                    ENV_KEY);
        }
    }

    public boolean isEnabled() {
        return secretKey.isPresent();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        SecretKey key = secretKey.orElseThrow(() -> new IllegalStateException("Encryption is not configured"));
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        SecretKey key = secretKey.orElseThrow(() -> new IllegalStateException("Encryption is not configured"));
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    private static Optional<SecretKey> loadKeyFromEnvironment() {
        String encoded = System.getenv(ENV_KEY);
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        byte[] keyBytes = Base64.getDecoder().decode(encoded.strip());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(ENV_KEY + " must decode to exactly 32 bytes (AES-256)");
        }
        return Optional.of(new SecretKeySpec(keyBytes, "AES"));
    }
}

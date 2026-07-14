package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class AttachmentChecksumService {

    public String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

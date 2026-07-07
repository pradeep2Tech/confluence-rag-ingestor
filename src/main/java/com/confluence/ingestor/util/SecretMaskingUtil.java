package com.confluence.ingestor.util;

/**
 * Masks secrets for API responses — never expose full PAT values.
 */
public final class SecretMaskingUtil {

    private SecretMaskingUtil() {
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (secret.length() <= 4) {
            return "****";
        }
        return "****" + secret.substring(secret.length() - 4);
    }

    public static boolean isMaskedValue(String value) {
        return value != null && value.startsWith("****");
    }
}

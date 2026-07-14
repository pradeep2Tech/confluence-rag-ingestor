package com.confluence.ingestor.attachment;

public enum DetectionMethod {
    EXTENSION,
    MIME_TYPE,
    MAGIC_BYTES,
    EMBEDDED_METADATA,
    FORMAT_PARSER,
    VISION_MODEL,
    CACHED,
    UNKNOWN
}

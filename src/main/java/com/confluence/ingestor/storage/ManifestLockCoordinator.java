package com.confluence.ingestor.storage;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-parent manifest file locking — separated from repository I/O (Phase 11b).
 */
@Component
public class ManifestLockCoordinator {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockFor(String parentPageId) {
        return locks.computeIfAbsent(parentPageId, ignored -> new ReentrantLock());
    }
}

package com.expensetracker.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembatas percobaan akses (in-memory) untuk mencegah brute force PIN.
 * Per IP: 5 kegagalan / 10 menit -> blok 15 menit.
 * Global: 100 kegagalan / 10 menit -> tunda semua percobaan 5 menit.
 */
@Component
public class AccessAttemptLimiter {

    private static final int MAX_FAILURES_PER_IP = 5;
    private static final long FAILURE_WINDOW_MS = 10 * 60_000L;
    private static final long IP_BLOCK_MS = 15 * 60_000L;
    private static final int MAX_GLOBAL_FAILURES = 100;
    private static final long GLOBAL_BLOCK_MS = 5 * 60_000L;
    private static final int MAX_TRACKED_IPS = 10_000;

    private static final class Attempt {
        long windowStart;
        int failures;
        long blockedUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private long globalWindowStart;
    private int globalFailures;
    private long globalBlockedUntil;

    public synchronized boolean isBlocked(String key) {
        long now = System.currentTimeMillis();
        if (globalBlockedUntil > now) {
            return true;
        }
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (attempt.blockedUntil > now) {
            return true;
        }
        if (now - attempt.windowStart > FAILURE_WINDOW_MS) {
            attempts.remove(key);
        }
        return false;
    }

    public synchronized long retryAfterSeconds(String key) {
        long now = System.currentTimeMillis();
        long until = Math.max(globalBlockedUntil, 0);
        Attempt attempt = attempts.get(key);
        if (attempt != null) {
            until = Math.max(until, attempt.blockedUntil);
        }
        long remaining = until - now;
        return remaining > 0 ? Math.max(1, (remaining + 999) / 1000) : 0;
    }

    public synchronized void recordFailure(String key) {
        long now = System.currentTimeMillis();
        prune(now);

        if (now - globalWindowStart > FAILURE_WINDOW_MS) {
            globalWindowStart = now;
            globalFailures = 0;
        }
        globalFailures++;
        if (globalFailures >= MAX_GLOBAL_FAILURES) {
            globalBlockedUntil = now + GLOBAL_BLOCK_MS;
            globalFailures = 0;
            globalWindowStart = now;
        }

        Attempt attempt = attempts.computeIfAbsent(key, ignored -> new Attempt());
        if (now - attempt.windowStart > FAILURE_WINDOW_MS) {
            attempt.windowStart = now;
            attempt.failures = 0;
        }
        attempt.failures++;
        if (attempt.failures >= MAX_FAILURES_PER_IP) {
            attempt.blockedUntil = now + IP_BLOCK_MS;
            attempt.failures = 0;
            attempt.windowStart = now;
        }
    }

    public synchronized void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void prune(long now) {
        if (attempts.size() <= MAX_TRACKED_IPS) {
            return;
        }
        // Buang entri yang sudah tidak aktif lebih dulu.
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            return attempt.blockedUntil < now && now - attempt.windowStart > FAILURE_WINDOW_MS;
        });
        // Batas keras: jangan biarkan key dari klien menumbuhkan map tanpa batas.
        if (attempts.size() > MAX_TRACKED_IPS) {
            attempts.clear();
        }
    }
}

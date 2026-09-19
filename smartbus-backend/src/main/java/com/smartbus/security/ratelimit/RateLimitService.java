package com.smartbus.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC-11: Server-side in-memory rate limiting service.
 * Thread-safe sliding window implementation with automatic memory eviction.
 * Protects login, GPS telemetry ingestion, and analytics CSV exports.
 */
@Service
@Slf4j
public class RateLimitService {

    private static final int MAX_BUCKETS = 50_000;

    @Value("${app.rate-limit.login.max-attempts:10}")
    private int loginMaxAttempts;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private long loginWindowSeconds;

    @Value("${app.rate-limit.gps.max-requests:60}")
    private int gpsMaxRequests;

    @Value("${app.rate-limit.gps.window-seconds:60}")
    private long gpsWindowSeconds;

    @Value("${app.rate-limit.export.max-requests:10}")
    private int exportMaxRequests;

    @Value("${app.rate-limit.export.window-seconds:60}")
    private long exportWindowSeconds;

    private final Map<String, SlidingWindowBucket> buckets = new ConcurrentHashMap<>();

    private static class SlidingWindowBucket {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private long lastAccessTime = System.currentTimeMillis();

        synchronized RateLimitResult consume(int maxRequests, long windowSeconds) {
            long now = System.currentTimeMillis();
            lastAccessTime = now;
            long windowStart = now - (windowSeconds * 1000L);

            // Evict expired timestamps
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                return RateLimitResult.allow();
            } else {
                Long oldest = timestamps.peekFirst();
                long expiry = (oldest != null ? oldest : now) + (windowSeconds * 1000L);
                long retryAfterMs = Math.max(1000L, expiry - now);
                long retryAfterSec = (long) Math.ceil(retryAfterMs / 1000.0);
                return RateLimitResult.block(retryAfterSec);
            }
        }

        synchronized boolean isStale(long thresholdMs) {
            return (System.currentTimeMillis() - lastAccessTime) > thresholdMs;
        }
    }

    public RateLimitResult tryConsume(String key, int maxRequests, long windowSeconds) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allow();
        }

        // Memory protection: if map grows excessively, evict stale buckets immediately
        if (buckets.size() > MAX_BUCKETS) {
            evictStaleBuckets(Math.max(windowSeconds * 1000L, 60_000L));
        }

        SlidingWindowBucket bucket = buckets.computeIfAbsent(key, k -> new SlidingWindowBucket());
        return bucket.consume(maxRequests, windowSeconds);
    }

    /**
     * Check rate limit for login attempts.
     * Keyed on client IP and sanitized email to prevent single-IP brute-forcing and account spraying.
     */
    public RateLimitResult checkLoginLimit(String clientIp, String email) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        String ipKey = "login:ip:" + safeIp;
        RateLimitResult ipResult = tryConsume(ipKey, loginMaxAttempts, loginWindowSeconds);
        if (!ipResult.isAllowed()) {
            return ipResult;
        }

        if (email != null && !email.isBlank()) {
            String safeEmail = email.trim().toLowerCase();
            String emailKey = "login:acc:" + safeIp + ":" + safeEmail;
            return tryConsume(emailKey, loginMaxAttempts, loginWindowSeconds);
        }

        return ipResult;
    }

    /**
     * Check rate limit for GPS telemetry ingestion.
     * Keyed by authenticated driver or trip identifier.
     */
    public RateLimitResult checkGpsLimit(String driverOrTripKey) {
        String key = "gps:" + driverOrTripKey;
        return tryConsume(key, gpsMaxRequests, gpsWindowSeconds);
    }

    /**
     * Check rate limit for analytics CSV exports.
     * Keyed by authenticated administrator user ID.
     */
    public RateLimitResult checkExportLimit(String adminKey) {
        String key = "export:" + adminKey;
        return tryConsume(key, exportMaxRequests, exportWindowSeconds);
    }

    /**
     * Check rate limit for forgot-password requests (keyed by IP and email).
     */
    public RateLimitResult checkForgotPasswordLimit(String clientIp, String email) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        String ipKey = "forgot_pwd:ip:" + safeIp;
        RateLimitResult ipResult = tryConsume(ipKey, 5, 60); // 5 per minute per IP
        if (!ipResult.isAllowed()) {
            return ipResult;
        }

        if (email != null && !email.isBlank()) {
            String emailKey = "forgot_pwd:email:" + safeIp + ":" + email.trim().toLowerCase();
            return tryConsume(emailKey, 3, 60); // 3 per minute per email
        }

        return ipResult;
    }

    /**
     * Check rate limit for password reset submissions (keyed by IP).
     */
    public RateLimitResult checkResetPasswordLimit(String clientIp) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        return tryConsume("reset_pwd:" + safeIp, 10, 60); // 10 attempts per minute
    }

    /**
     * Check rate limit for Google OAuth login submissions (keyed by IP).
     */
    public RateLimitResult checkGoogleAuthLimit(String clientIp) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        return tryConsume("google_auth:" + safeIp, loginMaxAttempts, loginWindowSeconds);
    }

    /**
     * Check rate limit for college admin self-registration submissions (keyed by IP).
     * Limit: 5 requests per minute per IP to prevent registration flooding.
     */
    public RateLimitResult checkAdminRegistrationLimit(String clientIp) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        return tryConsume("admin_reg:ip:" + safeIp, 5, 60);
    }

    /**
     * Scheduled cleanup of stale buckets every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredBuckets() {
        evictStaleBuckets(120_000L); // remove buckets inactive for > 2 minutes
    }

    private void evictStaleBuckets(long thresholdMs) {
        int initialSize = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().isStale(thresholdMs));
        int removed = initialSize - buckets.size();
        if (removed > 0) {
            log.debug("Evicted {} stale rate limit buckets. Current active buckets: {}", removed, buckets.size());
        }
    }

    /**
     * Clear all rate limiting state (useful for tests).
     */
    public void clear() {
        buckets.clear();
    }

    public int getActiveBucketCount() {
        return buckets.size();
    }
}

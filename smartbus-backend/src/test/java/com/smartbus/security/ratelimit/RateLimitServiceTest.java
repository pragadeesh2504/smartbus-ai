package com.smartbus.security.ratelimit;

import com.smartbus.domain.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
        ReflectionTestUtils.setField(rateLimitService, "loginMaxAttempts", 3);
        ReflectionTestUtils.setField(rateLimitService, "loginWindowSeconds", 2L);
        ReflectionTestUtils.setField(rateLimitService, "gpsMaxRequests", 5);
        ReflectionTestUtils.setField(rateLimitService, "gpsWindowSeconds", 2L);
        ReflectionTestUtils.setField(rateLimitService, "exportMaxRequests", 2);
        ReflectionTestUtils.setField(rateLimitService, "exportWindowSeconds", 2L);
        rateLimitService.clear();
    }

    @Test
    void testLoginLimit_AllowsUnderLimitAndBlocksOverLimit() {
        String ip = "192.168.1.100";
        String email = "test@smartbus.ai";

        // 3 allowed
        assertTrue(rateLimitService.checkLoginLimit(ip, email).isAllowed());
        assertTrue(rateLimitService.checkLoginLimit(ip, email).isAllowed());
        assertTrue(rateLimitService.checkLoginLimit(ip, email).isAllowed());

        // 4th blocked
        RateLimitResult blocked = rateLimitService.checkLoginLimit(ip, email);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfterSeconds() >= 1);
    }

    @Test
    void testLoginLimit_DifferentIpsAreIndependentlyLimited() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";
        String email = "shared@smartbus.ai";

        // Consume all for ip1
        assertTrue(rateLimitService.checkLoginLimit(ip1, email).isAllowed());
        assertTrue(rateLimitService.checkLoginLimit(ip1, email).isAllowed());
        assertTrue(rateLimitService.checkLoginLimit(ip1, email).isAllowed());
        assertFalse(rateLimitService.checkLoginLimit(ip1, email).isAllowed());

        // ip2 should still be allowed
        assertTrue(rateLimitService.checkLoginLimit(ip2, email).isAllowed());
    }

    @Test
    void testGpsLimit_AllowsUnderLimitAndBlocksOverLimit() {
        String driverTripKey = "driver-1:trip-101";

        for (int i = 0; i < 5; i++) {
            RateLimitResult result = rateLimitService.checkGpsLimit(driverTripKey);
            assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
        }

        RateLimitResult blocked = rateLimitService.checkGpsLimit(driverTripKey);
        assertFalse(blocked.isAllowed(), "6th request should be blocked");
        assertTrue(blocked.getRetryAfterSeconds() >= 1);
    }

    @Test
    void testGpsLimit_DifferentDriversDoNotShareLimit() {
        String key1 = "driver-1:trip-101";
        String key2 = "driver-2:trip-102";

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.checkGpsLimit(key1).isAllowed());
        }
        assertFalse(rateLimitService.checkGpsLimit(key1).isAllowed());

        // key2 still allowed
        assertTrue(rateLimitService.checkGpsLimit(key2).isAllowed());
    }

    @Test
    void testExportLimit_AllowsUnderLimitAndBlocksOverLimit() {
        String adminKey = "admin-user-uuid";

        assertTrue(rateLimitService.checkExportLimit(adminKey).isAllowed());
        assertTrue(rateLimitService.checkExportLimit(adminKey).isAllowed());

        RateLimitResult blocked = rateLimitService.checkExportLimit(adminKey);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfterSeconds() >= 1);
    }

    @Test
    void testRateLimitMemorySafety_EvictsStaleBuckets() throws InterruptedException {
        rateLimitService.checkLoginLimit("stale-ip-1", "stale@test.com");
        rateLimitService.checkGpsLimit("stale-trip-1");
        assertTrue(rateLimitService.getActiveBucketCount() >= 2);

        // Sleep briefly to let timestamps age, then call cleanup
        Thread.sleep(50);
        ReflectionTestUtils.invokeMethod(rateLimitService, "evictStaleBuckets", 10L);

        assertEquals(0, rateLimitService.getActiveBucketCount(), "Stale buckets should be evicted");
    }

    @Test
    void testRateLimitExceededException_StoresRetryAfter() {
        RateLimitExceededException ex = new RateLimitExceededException("Limit exceeded", 15);
        assertEquals(15, ex.getRetryAfterSeconds());
        assertEquals("Limit exceeded", ex.getMessage());
    }
}

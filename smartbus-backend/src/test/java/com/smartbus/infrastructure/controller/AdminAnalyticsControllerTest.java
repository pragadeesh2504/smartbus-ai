package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AnalyticsService;
import com.smartbus.domain.exception.RateLimitExceededException;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.security.UserPrincipal;
import com.smartbus.security.ratelimit.RateLimitResult;
import com.smartbus.security.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminAnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AdminAnalyticsController adminAnalyticsController;

    private UserPrincipal adminPrincipal;
    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@smartbus.ai")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        adminPrincipal = new UserPrincipal(adminUser);
    }

    @Test
    void testExportCsv_SuccessUnderRateLimit() {
        when(rateLimitService.checkExportLimit(anyString())).thenReturn(RateLimitResult.allow());
        when(analyticsService.generateCsvExport("routes", null, null)).thenReturn("Route,Trips\nRoute 1,10");

        ResponseEntity<byte[]> response = adminAnalyticsController.exportCsv("routes", null, null, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(new String(response.getBody()).contains("Route 1,10"));
        verify(analyticsService, times(1)).generateCsvExport("routes", null, null);
    }

    @Test
    void testExportCsv_BlockedWhenRateLimitExceeded() {
        when(rateLimitService.checkExportLimit(anyString())).thenReturn(RateLimitResult.block(45));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> adminAnalyticsController.exportCsv("routes", null, null, adminPrincipal)
        );

        assertEquals(45, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("Too many export requests"));
        verify(analyticsService, never()).generateCsvExport(anyString(), anyString(), anyString());
    }
}

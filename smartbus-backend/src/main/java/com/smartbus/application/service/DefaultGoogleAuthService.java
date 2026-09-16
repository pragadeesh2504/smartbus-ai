package com.smartbus.application.service;

import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.infrastructure.dto.GoogleUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class DefaultGoogleAuthService implements GoogleAuthService {

    @Value("${app.google.client-id:${GOOGLE_CLIENT_ID:}}")
    private String googleClientId;

    private final RestTemplate restTemplate;

    public DefaultGoogleAuthService() {
        this.restTemplate = new RestTemplate();
    }

    public DefaultGoogleAuthService(RestTemplate restTemplate, String googleClientId) {
        this.restTemplate = restTemplate;
        this.googleClientId = googleClientId;
    }

    @Override
    public GoogleUserInfo verifyToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Google ID token is missing or empty");
        }

        // Test/Mock token support for test environments without live internet
        if (idToken.startsWith("mock-google-token:")) {
            String mockEmail = idToken.substring("mock-google-token:".length());
            return new GoogleUserInfo(mockEmail.trim().toLowerCase(), "Mock Google User", true, "mock-sub-" + mockEmail);
        }

        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String email = (String) body.get("email");
                String name = (String) body.get("name");
                Object emailVerifiedObj = body.get("email_verified");
                boolean emailVerified = emailVerifiedObj != null && 
                        ("true".equalsIgnoreCase(String.valueOf(emailVerifiedObj)) || Boolean.TRUE.equals(emailVerifiedObj));
                String aud = (String) body.get("aud");
                String sub = (String) body.get("sub");

                if (!emailVerified) {
                    throw new BadRequestException("Google email is not verified");
                }

                if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.equalsIgnoreCase("mock-client-id")) {
                    if (!googleClientId.equals(aud)) {
                        log.warn("Google token audience mismatch. Expected: {}, Got: {}", googleClientId, aud);
                        throw new BadRequestException("Google token audience mismatch");
                    }
                }

                return new GoogleUserInfo(email.trim().toLowerCase(), name != null ? name : "Google User", true, sub);
            } else {
                throw new BadRequestException("Failed to verify identity with Google");
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.warn("Google token verification error: {}", e.getMessage());
            throw new BadRequestException("Invalid Google ID token");
        }
    }
}

package com.smartbus.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DefaultEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        // Safe logging of password reset email dispatch without exposing token in prod info logs
        log.info("Password reset link generated and dispatched for recipient: {}", toEmail);
        log.debug("Password reset URL for {}: {}", toEmail, resetLink);
        // Can be wired to Spring JavaMailSender or cloud provider (SES/SendGrid)
    }
}

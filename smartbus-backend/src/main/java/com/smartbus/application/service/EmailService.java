package com.smartbus.application.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}

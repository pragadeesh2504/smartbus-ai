package com.smartbus.infrastructure.dto;

public record GoogleUserInfo(
        String email,
        String name,
        boolean emailVerified,
        String sub
) {}

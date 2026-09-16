package com.smartbus.application.service;

import com.smartbus.infrastructure.dto.GoogleUserInfo;

public interface GoogleAuthService {
    GoogleUserInfo verifyToken(String idToken);
}

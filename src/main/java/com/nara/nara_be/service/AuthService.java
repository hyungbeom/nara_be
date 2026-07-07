package com.nara.nara_be.service;

import com.nara.nara_be.dto.LoginRequest;
import com.nara.nara_be.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse getCurrentUser(String userId);

    void logout();
}

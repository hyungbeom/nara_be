package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.LoginRequest;
import com.nara.nara_be.dto.LoginResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        return ApiResponse.success(authService.getCurrentUser(userDetails.getUsername()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        authService.logout();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success("logout", null);
    }
}

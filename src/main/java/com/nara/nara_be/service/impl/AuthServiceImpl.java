package com.nara.nara_be.service.impl;

import com.nara.nara_be.dao.UserDao;
import com.nara.nara_be.domain.User;
import com.nara.nara_be.dto.LoginRequest;
import com.nara.nara_be.dto.LoginResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDao userDao;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    request.getUserId(),
                    request.getPassword()
            );
            var authentication = authenticationManager.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BadCredentialsException e) {
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        User user = userDao.findByUserId(request.getUserId());
        if (user == null) {
            throw new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.UNAUTHORIZED);
        }

        return LoginResponse.from(user);
    }

    @Override
    public LoginResponse getCurrentUser(String userId) {
        User user = userDao.findByUserId(userId);
        if (user == null) {
            throw new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.UNAUTHORIZED);
        }
        return LoginResponse.from(user);
    }

    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }
}

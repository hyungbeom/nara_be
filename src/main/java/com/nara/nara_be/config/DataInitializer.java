package com.nara.nara_be.config;

import com.nara.nara_be.dao.UserDao;
import com.nara.nara_be.domain.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    void init() {
        if (!userDao.existsByUserId("progist")) {
            userDao.insert(User.builder()
                    .userId("progist")
                    .password(passwordEncoder.encode("progist21c"))
                    .userName("Progist")
                    .useYn("Y")
                    .build());
        }
    }
}

package com.nara.nara_be.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private Long userSeq;
    private String userId;
    private String password;
    private String userName;
    private String useYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public User(String userId, String password, String userName, String useYn) {
        this.userId = userId;
        this.password = password;
        this.userName = userName;
        this.useYn = useYn;
    }

    public boolean isActive() {
        return "Y".equalsIgnoreCase(useYn);
    }
}

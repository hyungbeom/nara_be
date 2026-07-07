package com.nara.nara_be.dto;

import com.nara.nara_be.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private Long userSeq;
    private String userId;
    private String userName;

    public static LoginResponse from(User user) {
        return LoginResponse.builder()
                .userSeq(user.getUserSeq())
                .userId(user.getUserId())
                .userName(user.getUserName())
                .build();
    }
}

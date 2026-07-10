package com.gabojago.member.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사용자 계정 상태. */
@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성"),
    LOCKED("잠금"),
    DELETED("탈퇴"),
    PENDING("대기");

    private final String description;
}

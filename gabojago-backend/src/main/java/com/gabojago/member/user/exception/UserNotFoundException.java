package com.gabojago.member.user.exception;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super(ErrorCode.USER_NOT_FOUND, "존재하지 않는 사용자입니다. userId=" + userId);
    }
}

package com.gabojago.member.activity.exception;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;

public class BucketItemNotFoundException extends BusinessException {

    public BucketItemNotFoundException(Long bucketItemId) {
        super(ErrorCode.NOT_FOUND, "존재하지 않는 버킷 항목입니다. id=" + bucketItemId);
    }
}

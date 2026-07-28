package com.gabojago.tourism.data.exception;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;

public class CongestionBatchNotFoundException extends BusinessException {

    public CongestionBatchNotFoundException(Long batchId) {
        super(ErrorCode.NOT_FOUND, "존재하지 않는 혼잡도 배치입니다. batchId=" + batchId);
    }
}

package com.gabojago.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.info("BusinessException: {}", e.getErrorCode(), e);
        return build(e.getErrorCode(), e.getDetail());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        log.info("ResponseStatusException: {}", e.getStatusCode(), e);
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        ErrorCode errorCode = switch (status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status) {
            case BAD_REQUEST -> ErrorCode.INVALID_REQUEST;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case CONFLICT -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
        return build(errorCode, e.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.info("MethodArgumentNotValidException", e);
        return build(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadableException(HttpMessageNotReadableException e) {
        log.info("HttpMessageNotReadableException", e);
        return build(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.info("MissingServletRequestParameterException: {}", e.getParameterName(), e);
        return build(ErrorCode.MISSING_PARAMETER, e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.info("MethodArgumentTypeMismatchException: {}", e.getName(), e);
        return build(ErrorCode.INVALID_PARAMETER, e.getName());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        log.info("ConstraintViolationException", e);
        return build(ErrorCode.INVALID_PARAMETER);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.info("IllegalArgumentException", e);
        return build(ErrorCode.INVALID_PARAMETER, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        log.info("IllegalStateException", e);
        return build(ErrorCode.INVALID_STATE, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled Exception", e);
        return build(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String detail) {
        ErrorResponse body = new ErrorResponse(errorCode, detail);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode) {
        return build(errorCode, null);
    }
}

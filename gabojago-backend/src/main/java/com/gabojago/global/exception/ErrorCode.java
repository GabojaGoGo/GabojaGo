package com.gabojago.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // ===== Request =====
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다."),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 값이 누락되었습니다."),
  INVALID_STATE(HttpStatus.CONFLICT, "요청을 처리할 수 없는 상태입니다."),

  // ===== Authentication =====
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
  AUTH_SESSION_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그인 세션을 저장할 수 없습니다."),

  // ===== User =====
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
  USER_INACTIVE(HttpStatus.FORBIDDEN, "비활성 사용자입니다."),

  // ===== OAuth =====
  OAUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 인증 정보가 유효하지 않습니다."),
  OAUTH_USERINFO_FAILED(HttpStatus.BAD_GATEWAY, "소셜 사용자 정보 조회에 실패했습니다."),

  // ===== Place =====
  PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 장소입니다."),
  INVALID_PLACE_ATTRIBUTE(HttpStatus.BAD_REQUEST, "장소 속성 값이 올바르지 않습니다."),

  // ===== Common =====
  ROUTING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이동 경로를 계산할 수 없습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 리소스를 찾을 수 없습니다."),
  CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String message;
}

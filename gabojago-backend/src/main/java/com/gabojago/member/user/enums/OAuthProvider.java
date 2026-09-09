package com.gabojago.member.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 소셜 로그인 제공자. */
@Getter
@RequiredArgsConstructor
public enum OAuthProvider {
  KAKAO("카카오"),
  NAVER("네이버"),
  GOOGLE("구글"),
  APPLE("애플");

  private final String description;
}

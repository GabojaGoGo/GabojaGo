package com.gabojago.member.auth.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.jwt.JwtUtils;
import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.google.GoogleOAuthProviderClient;
import com.gabojago.global.security.oauth2.kakao.KakaoOAuthProviderClient;
import com.gabojago.global.security.oauth2.naver.NaverOAuthProviderClient;
import com.gabojago.member.auth.dto.response.OAuthLoginResponse;
import com.gabojago.member.user.domain.SocialAccount;
import com.gabojago.member.user.domain.User;
import com.gabojago.member.user.enums.OAuthProvider;
import com.gabojago.member.user.enums.UserStatus;
import com.gabojago.member.user.repository.SocialAccountRepository;
import com.gabojago.member.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    private SocialAccountRepository socialAccountRepository;
    private UserRepository userRepository;
    private JwtUtils jwtUtils;
    private RefreshTokenService refreshTokenService;
    private PlatformTransactionManager transactionManager;
    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        socialAccountRepository = mock(SocialAccountRepository.class);
        userRepository = mock(UserRepository.class);
        jwtUtils = mock(JwtUtils.class);
        refreshTokenService = mock(RefreshTokenService.class);
        transactionManager = mock(PlatformTransactionManager.class);

        service = new OAuthLoginService(
                mock(KakaoOAuthProviderClient.class),
                mock(NaverOAuthProviderClient.class),
                mock(GoogleOAuthProviderClient.class),
                socialAccountRepository,
                userRepository,
                jwtUtils,
                refreshTokenService,
                transactionManager);

        // 기본 stub: 조회는 비어있고, 저장은 인자를 그대로 반환(신규 User엔 id 부여)
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailAndStatus(any(), any()))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.findByUser_Id(anyLong()))
                .thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (ReflectionTestUtils.getField(u, "id") == null) {
                ReflectionTestUtils.setField(u, "id", 100L);
            }
            return u;
        });
        when(socialAccountRepository.save(any(SocialAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtils.generateAccessToken(anyString())).thenReturn("access-token");
        when(refreshTokenService.issue(anyLong())).thenReturn("refresh-token");
    }

    private OAuth2UserInfo info(OAuthProvider provider, String providerUserId, String email) {
        return new OAuth2UserInfo(provider, providerUserId, email, "여행자");
    }

    private User activeUser(long id, String email) {
        User user = User.create("여행자", email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("완전 신규: 같은 소셜계정·이메일 모두 없으면 User+SocialAccount 생성")
    void createsNewUserWhenNothingExists() {
        OAuthLoginResponse res = service.processLogin(info(OAuthProvider.KAKAO, "k1", "new@x.com"));

        assertThat(res.isNewUser()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("재로그인: 동일 (provider, providerUserId)면 기존 유저로 처리하고 이메일 검사를 건너뛴다")
    void reusesExistingSocialAccount() {
        User user = activeUser(1L, "a@x.com");
        SocialAccount social = SocialAccount.create(user, OAuthProvider.KAKAO, "k1");
        when(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "k1"))
                .thenReturn(Optional.of(social));

        OAuthLoginResponse res = service.processLogin(info(OAuthProvider.KAKAO, "k1", "a@x.com"));

        assertThat(res.isNewUser()).isFalse();
        assertThat(res.userId()).isEqualTo("1");
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
        verify(userRepository, never()).findFirstByEmailAndStatus(any(), any());
    }

    @Test
    @DisplayName("차단: 같은 이메일의 활성 유저가 다른 provider로 가입돼 있으면 409 + 기존 provider 반환")
    void blocksWhenEmailBoundToAnotherProvider() {
        User existing = activeUser(1L, "a@x.com");
        when(userRepository.findFirstByEmailAndStatus("a@x.com", UserStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(socialAccountRepository.findByUser_Id(1L))
                .thenReturn(List.of(SocialAccount.create(existing, OAuthProvider.KAKAO, "k1")));

        assertThatThrownBy(() -> service.processLogin(info(OAuthProvider.GOOGLE, "g1", "a@x.com")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
                    assertThat(be.getDetail()).isEqualTo("KAKAO");
                });

        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("정규화: 대소문자·공백이 달라도 같은 이메일로 보고 차단한다")
    void normalizesEmailBeforeConflictCheck() {
        User existing = activeUser(1L, "a@x.com");
        when(userRepository.findFirstByEmailAndStatus("a@x.com", UserStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(socialAccountRepository.findByUser_Id(1L))
                .thenReturn(List.of(SocialAccount.create(existing, OAuthProvider.NAVER, "n1")));

        assertThatThrownBy(() -> service.processLogin(info(OAuthProvider.GOOGLE, "g1", "  A@X.COM ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getDetail())
                .isEqualTo("NAVER");

        // 조회 키가 정규화된 값이어야 한다
        verify(userRepository).findFirstByEmailAndStatus("a@x.com", UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("탈퇴 재가입: 비활성 유저의 같은 소셜 재로그인이면 기존 연결을 정리하고 신규 생성")
    void cleansUpInactiveSocialAccountAndCreatesNew() {
        User deleted = activeUser(1L, "a@x.com");
        deleted.softDelete();
        SocialAccount social = SocialAccount.create(deleted, OAuthProvider.KAKAO, "k1");
        when(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "k1"))
                .thenReturn(Optional.of(social));

        OAuthLoginResponse res = service.processLogin(info(OAuthProvider.KAKAO, "k1", "a@x.com"));

        assertThat(res.isNewUser()).isTrue();
        verify(socialAccountRepository).delete(social);
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("이메일 없음: null이면 충돌 검사를 건너뛰고 신규 생성")
    void skipsConflictCheckWhenEmailNull() {
        OAuthLoginResponse res = service.processLogin(info(OAuthProvider.KAKAO, "k1", null));

        assertThat(res.isNewUser()).isTrue();
        verify(userRepository, never()).findFirstByEmailAndStatus(any(), any());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 공백: 빈 문자열도 정규화 후 null로 보고 충돌 검사를 건너뛴다")
    void skipsConflictCheckWhenEmailBlank() {
        OAuthLoginResponse res = service.processLogin(info(OAuthProvider.KAKAO, "k1", "   "));

        assertThat(res.isNewUser()).isTrue();
        verify(userRepository, never()).findFirstByEmailAndStatus(any(), any());
    }

    @Nested
    @DisplayName("동시 가입 race")
    class ConcurrentSignup {

        @Test
        @DisplayName("첫 처리가 unique 위반이면 새 트랜잭션에서 한 번 재시도한다")
        void retriesOnceOnDataIntegrityViolation() {
            // TransactionTemplate이 콜백을 실제로 실행하도록 트랜잭션 매니저 stub
            when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            OAuthLoginService spied = spy(service);

            OAuthLoginResponse ok = new OAuthLoginResponse("a", "r", "1", "여행자", false);
            doThrow(new DataIntegrityViolationException("dup"))
                    .doReturn(ok)
                    .when(spied).processLogin(any());

            OAuthLoginResponse res = spied.login(info(OAuthProvider.KAKAO, "k1", "a@x.com"));

            assertThat(res).isSameAs(ok);
            verify(spied, times(2)).processLogin(any());
        }
    }
}

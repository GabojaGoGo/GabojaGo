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
import com.gabojago.member.user.repository.SocialAccountRepository;
import com.gabojago.member.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class OAuthLoginService {

    private final KakaoOAuthProviderClient kakaoClient;
    private final NaverOAuthProviderClient naverClient;
    private final GoogleOAuthProviderClient googleClient;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;

    public OAuthLoginService(KakaoOAuthProviderClient kakaoClient,
                             NaverOAuthProviderClient naverClient,
                             GoogleOAuthProviderClient googleClient,
                             SocialAccountRepository socialAccountRepository,
                             UserRepository userRepository,
                             JwtUtils jwtUtils,
                             RefreshTokenService refreshTokenService,
                             PlatformTransactionManager transactionManager) {
        this.kakaoClient = kakaoClient;
        this.naverClient = naverClient;
        this.googleClient = googleClient;
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** OAuth SDK 로그인: provider userinfo 검증(네트워크)은 트랜잭션 밖에서, 유저 처리만 트랜잭션 안에서 수행 */
    public OAuthLoginResponse login(OAuthProvider provider, String accessToken) {
        OAuth2UserInfo userInfo = getUserInfo(provider, accessToken);
        return login(userInfo);
    }

    public OAuthLoginResponse login(OAuth2UserInfo userInfo) {
        try {
            return transactionTemplate.execute(status -> processLogin(userInfo));
        } catch (DataIntegrityViolationException e) {
            // 같은 소셜 계정으로 동시에 첫 로그인이 겹쳐 (provider, providerUserId) unique 제약에 걸린 경우.
            // 패배한 트랜잭션은 롤백됐으므로, 승자가 만든 계정을 새 트랜잭션에서 다시 조회해 기존 유저로 처리한다.
            log.warn("동시 가입 충돌 감지, 재시도: provider={}, providerUserId={}",
                    userInfo.provider(), userInfo.providerUserId());
            return transactionTemplate.execute(status -> processLogin(userInfo));
        }
    }

    OAuthLoginResponse processLogin(OAuth2UserInfo userInfo) {
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(userInfo.provider(), userInfo.providerUserId())
                .orElse(null);

        // 탈퇴(비활성) 유저가 같은 소셜로 재로그인 → 기존 연결을 정리하고 신규 가입으로 취급한다.
        if (socialAccount != null && !socialAccount.getUser().isActive()) {
            log.info("탈퇴 사용자 소셜 연결 정리: socialAccountId={}, userId={}",
                    socialAccount.getId(), socialAccount.getUser().getId());
            socialAccountRepository.delete(socialAccount);
            socialAccount = null;
        }

        if (socialAccount != null) {
            User user = socialAccount.getUser();
            user.recordLogin();
            socialAccount.recordLogin();
            log.info("기존 사용자 로그인: userId={}", user.getId());
            return issueTokens(user, false);
        }

        User user = User.create(userInfo.nickname(), userInfo.email());
        user.recordLogin();
        userRepository.save(user);

        socialAccount = SocialAccount.create(user, userInfo.provider(), userInfo.providerUserId());
        socialAccountRepository.save(socialAccount);
        socialAccount.recordLogin();
        log.info("신규 사용자 생성: userId={}", user.getId());
        return issueTokens(user, true);
    }

    private OAuthLoginResponse issueTokens(User user, boolean isNewUser) {
        String userId = String.valueOf(user.getId());
        String accessToken = jwtUtils.generateAccessToken(userId);
        try {
            String refreshToken = refreshTokenService.issue(user.getId());
            return new OAuthLoginResponse(accessToken, refreshToken, userId, user.getNickname(), isNewUser);
        } catch (RedisConnectionFailureException e) {
            // Redis는 refresh token의 단일 저장소다. 로그인 성공처럼 보이는 JWT만 반환하면
            // 앱 재시작·갱신 때 즉시 깨지므로, 명확한 503으로 전체 로그인 요청을 실패시킨다.
            log.error("로그인 세션 저장소 연결 실패: userId={}", user.getId(), e);
            throw new BusinessException(ErrorCode.AUTH_SESSION_STORE_UNAVAILABLE, null, e);
        }
    }

    public OAuth2UserInfo getUserInfo(OAuthProvider provider, String accessToken) {
        return switch (provider) {
            case KAKAO -> kakaoClient.getUserInfo(accessToken);
            case NAVER -> naverClient.getUserInfo(accessToken);
            case GOOGLE -> googleClient.getUserInfo(accessToken);
        };
    }

    public void unlink(OAuthProvider provider, String providerUserId) {
        switch (provider) {
            case KAKAO -> kakaoClient.unlink(providerUserId);
            case NAVER -> naverClient.unlink(providerUserId);
            case GOOGLE -> googleClient.unlink(providerUserId);
        }
    }
}

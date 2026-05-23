package com.tripmate.backend.user.service;

import com.tripmate.backend.crypto.FieldEncryptionService;
import com.tripmate.backend.social.domain.SocialAccountRepository;
import com.tripmate.backend.user.domain.*;
import com.tripmate.backend.user.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final FieldEncryptionService encryptionService;

    @Transactional(readOnly = true)
    public MeResponse getMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        String nickname = profile != null && profile.getNicknameEnc() != null
                ? encryptionService.decrypt(profile.getNicknameEnc())
                : null;

        String provider = socialAccountRepository.findAll().stream()
                .filter(sa -> sa.getUserId().equals(userId))
                .map(sa -> sa.getProvider())
                .findFirst()
                .orElse("UNKNOWN");

        return new MeResponse(user.getId(), nickname, provider,
                user.getCreatedAt(), user.getLastLoginAt());
    }

    @Transactional
    public void updateNickname(String userId, String nickname) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 없음"));
        profile.updateNickname(encryptionService.encrypt(nickname));
    }
}

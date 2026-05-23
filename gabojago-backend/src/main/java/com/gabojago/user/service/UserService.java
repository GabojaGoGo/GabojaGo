package com.gabojago.user.service;

import com.gabojago.user.domain.*;
import com.gabojago.user.dto.response.MeResponse;
import com.gabojago.user.repository.SocialAccountRepository;
import com.gabojago.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional(readOnly = true)
    public MeResponse getMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        String provider = socialAccountRepository.findByUser_Id(userId).stream()
                .map(SocialAccount::getProvider)
                .map(Enum::name)
                .findFirst()
                .orElse("UNKNOWN");

        return new MeResponse(user.getId(), user.getNickname(), provider,
                user.getCreatedAt(), user.getLastLoginAt());
    }

    @Transactional
    public void updateNickname(String userId, String nickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        user.updateNickname(nickname);
    }
}

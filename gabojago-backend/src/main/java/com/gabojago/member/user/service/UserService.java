package com.gabojago.member.user.service;

import com.gabojago.member.user.domain.*;
import com.gabojago.member.user.dto.response.MeResponse;
import com.gabojago.member.user.exception.UserNotFoundException;
import com.gabojago.member.user.repository.SocialAccountRepository;
import com.gabojago.member.user.repository.UserRepository;
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
        Long userPk = parseUserId(userId);
        User user = userRepository.findById(userPk)
                .orElseThrow(() -> new UserNotFoundException(userPk));

        String provider = socialAccountRepository.findByUser_Id(userPk).stream()
                .map(SocialAccount::getProvider)
                .map(Enum::name)
                .findFirst()
                .orElse("UNKNOWN");

        return new MeResponse(String.valueOf(user.getId()), user.getNickname(), provider,
                user.getCreatedAt(), user.getLastLoginAt());
    }

    @Transactional
    public void updateNickname(String userId, String nickname) {
        Long userPk = parseUserId(userId);
        User user = userRepository.findById(userPk)
                .orElseThrow(() -> new UserNotFoundException(userPk));
        user.updateNickname(nickname);
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID", e);
        }
    }
}

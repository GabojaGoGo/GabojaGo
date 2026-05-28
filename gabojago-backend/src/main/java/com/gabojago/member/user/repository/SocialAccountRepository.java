package com.gabojago.member.user.repository;

import com.gabojago.member.user.domain.SocialAccount;
import com.gabojago.member.user.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<SocialAccount> findByUser_Id(Long userId);
}

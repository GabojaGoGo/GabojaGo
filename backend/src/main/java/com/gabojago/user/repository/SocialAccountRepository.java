package com.gabojago.user.repository;

import com.gabojago.user.domain.SocialAccount;
import com.gabojago.user.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<SocialAccount> findByUser_Id(String userId);
}

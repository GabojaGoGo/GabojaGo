package com.gabojago.member.activity.repository;

import com.gabojago.member.activity.domain.UserBucketItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserBucketItemRepository extends JpaRepository<UserBucketItem, Long> {
    List<UserBucketItem> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}

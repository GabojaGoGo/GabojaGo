package com.gabojago.userdata.repository;

import com.gabojago.userdata.domain.UserBucketItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserBucketItemRepository extends JpaRepository<UserBucketItem, Long> {
    List<UserBucketItem> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByIdAndUserId(Long id, String userId);
}

package com.gabojago.member.activity.repository;

import com.gabojago.member.activity.domain.UserFootprint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFootprintRepository extends JpaRepository<UserFootprint, Long> {
    List<UserFootprint> findByUserIdOrderByVisitedAtDesc(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
